package com.opendroid.app.core.agent

import android.content.Context
import com.opendroid.app.core.ai.AIProvider
import com.opendroid.app.core.ai.AIProviderType
import com.opendroid.app.core.ai.DeterministicProvider
import com.opendroid.app.core.ai.GeminiProvider
import com.opendroid.app.core.ai.LocalLlamaProvider
import com.opendroid.app.core.avatar.AvatarEngine
import com.opendroid.app.core.domain.StructuredModelDecision
import com.opendroid.app.core.domain.TaskStatus
import com.opendroid.app.core.inference.InferenceClient
import com.opendroid.app.core.knowledge.KnowledgeEngine
import com.opendroid.app.core.logging.RedactedLogger
import com.opendroid.app.core.memory.MemoryEngine
import com.opendroid.app.core.personality.PersonalityEngine
import com.opendroid.app.core.session.SessionManager
import com.opendroid.app.core.task.EmergencyStopManager
import com.opendroid.app.core.tools.ToolRouter
import com.opendroid.app.core.tools.ToolRoutingOutcome
import com.opendroid.app.core.vision.VisualFrame
import com.opendroid.app.data.database.MessageRole
import com.opendroid.app.data.database.OpenDroidDatabase
import com.opendroid.app.data.database.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class SrishtiTurnResult {
    data class ConversationalResponse(val text: String, val thought: String?) : SrishtiTurnResult()
    data class ToolExecuted(val outcome: ToolRoutingOutcome, val summary: String) : SrishtiTurnResult()
    data class Failed(val error: String) : SrishtiTurnResult()
}

/** Central Srishti orchestrator: memory + knowledge + personality + inference + safe tools. */
class SrishtiCore(
    val context: Context,
    val database: OpenDroidDatabase,
    val scope: CoroutineScope,
    val apiKeyProvider: () -> String?
) {
    val personalityEngine = PersonalityEngine()
    val memoryEngine = MemoryEngine(database.memoryDao())
    val knowledgeEngine = KnowledgeEngine(context)
    val sessionManager = SessionManager(database.conversationDao())
    val avatarEngine = AvatarEngine()
    val toolRouter = ToolRouter(context, database.taskDao())
    val inferenceClient = InferenceClient(context)
    val geminiProvider = GeminiProvider(apiKeyProvider)
    val localLlamaProvider = LocalLlamaProvider(inferenceClient)
    val deterministicProvider = DeterministicProvider()
    private val _activeProviderType = MutableStateFlow(AIProviderType.GEMINI_CLOUD)
    val activeProviderType: StateFlow<AIProviderType> = _activeProviderType.asStateFlow()
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun setActiveProvider(type: AIProviderType) { _activeProviderType.value = type }

    suspend fun processTurn(
        userPrompt: String,
        imageFrame: VisualFrame? = null,
        onConfirmationNeeded: (suspend (reason: String) -> Boolean)? = null
    ): SrishtiTurnResult = withContext(Dispatchers.Default) {
        if (EmergencyStopManager.isStopRequested()) return@withContext SrishtiTurnResult.Failed("Emergency stop is currently active.")
        _isBusy.value = true
        val taskId = "srishti_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        try {
            database.taskDao().insertTask(TaskEntity(taskId, userPrompt, TaskStatus.ANALYZING, _activeProviderType.value.name, selectedModel()))
            sessionManager.logTurn(MessageRole.USER, userPrompt)
            memoryEngine.addShortTermTurn("User", userPrompt)
            val systemPrompt = personalityEngine.buildSystemPersonaPrompt(knowledgeEngine.formatContextString(), memoryEngine.retrieveRelevantContext(userPrompt))
            val provider = selectProvider()
            val decision = provider.generateDecision(systemPrompt, userPrompt, imageFrame).getOrElse {
                RedactedLogger.w(TAG, "Primary provider failed: ${it.message}; using deterministic fallback")
                deterministicProvider.generateDecision(systemPrompt, userPrompt, imageFrame).getOrThrow()
            }
            handleDecision(taskId, decision, onConfirmationNeeded)
        } catch (e: Exception) {
            RedactedLogger.e(TAG, "Turn execution error: ${e.message}")
            database.taskDao().updateTaskStatus(taskId, TaskStatus.FAILED, e.message)
            SrishtiTurnResult.Failed(e.localizedMessage ?: "Unknown execution error")
        } finally { _isBusy.value = false }
    }

    private suspend fun handleDecision(taskId: String, decision: StructuredModelDecision, onConfirmationNeeded: (suspend (reason: String) -> Boolean)?): SrishtiTurnResult = when (decision) {
        is StructuredModelDecision.DirectResponse -> {
            database.taskDao().updateTaskStatus(taskId, TaskStatus.COMPLETED, finalResponse = decision.response)
            sessionManager.logTurn(MessageRole.SRISHTI, decision.response, decision.thought, personalityEngine.currentMood.value.name)
            memoryEngine.addShortTermTurn("Srishti", decision.response)
            SrishtiTurnResult.ConversationalResponse(decision.response, decision.thought)
        }
        is StructuredModelDecision.ToolCall -> {
            database.taskDao().updateTaskStatus(taskId, TaskStatus.EXECUTING)
            val call = decision.toolCall
            val outcome = toolRouter.routeAndExecute(taskId, 0, call.name, call.arguments, onConfirmationNeeded)
            val summary = if (outcome.success) "Executed ${call.name} successfully." else "Action ${call.name} could not be completed: ${outcome.errorMessage}"
            database.taskDao().updateTaskStatus(taskId, if (outcome.success) TaskStatus.COMPLETED else TaskStatus.FAILED, outcome.errorMessage, summary)
            sessionManager.logTurn(MessageRole.TOOL, summary, decision.thought, toolCallJson = call.name, toolResultJson = outcome.result?.output?.toString())
            SrishtiTurnResult.ToolExecuted(outcome, summary)
        }
        is StructuredModelDecision.BlockedOrInvalid -> {
            database.taskDao().updateTaskStatus(taskId, TaskStatus.FAILED, decision.reason)
            SrishtiTurnResult.Failed(decision.reason)
        }
    }

    private fun selectProvider(): AIProvider = when (_activeProviderType.value) {
        AIProviderType.GEMINI_CLOUD -> if (geminiProvider.isAvailable) geminiProvider else localLlamaProvider.takeIf { it.isAvailable } ?: deterministicProvider
        AIProviderType.LOCAL_LLAMA -> if (localLlamaProvider.isAvailable) localLlamaProvider else deterministicProvider
        AIProviderType.OFFLINE_DETERMINISTIC -> deterministicProvider
    }

    private fun selectedModel() = when (_activeProviderType.value) {
        AIProviderType.GEMINI_CLOUD -> "Gemini"
        AIProviderType.LOCAL_LLAMA -> "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        AIProviderType.OFFLINE_DETERMINISTIC -> "deterministic"
    }

    fun triggerEmergencyStop() { EmergencyStopManager.triggerEmergencyStop("User tapped Emergency Stop") }
    fun resetEmergencyStop() { EmergencyStopManager.reset() }
    companion object { private const val TAG = "SrishtiCore" }
}
