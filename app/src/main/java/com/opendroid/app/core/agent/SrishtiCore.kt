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
import com.opendroid.app.core.voice.VoiceEngine
import com.opendroid.app.data.database.MessageRole
import com.opendroid.app.data.database.OpenDroidDatabase
import com.opendroid.app.data.database.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class SrishtiTurnResult {
    data class ConversationalResponse(val text: String, val thought: String?) : SrishtiTurnResult()
    data class ToolExecuted(val outcome: ToolRoutingOutcome, val summary: String) : SrishtiTurnResult()
    data class Failed(val error: String) : SrishtiTurnResult()
}

/**
 * SrishtiCore 3.0: Central Autonomous Orchestrator
 * Coordinates PersonalityEngine, MemoryEngine, KnowledgeEngine, SessionManager,
 * AI Providers (Gemini, Local Llama, Deterministic), ToolRouter, and Voice/Avatar outputs.
 */
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

    fun setActiveProvider(type: AIProviderType) {
        _activeProviderType.value = type
    }

    /**
     * Executes an end-to-end companion turn
     */
    suspend fun processTurn(
        userPrompt: String,
        imageFrame: VisualFrame? = null,
        onConfirmationNeeded: (suspend (reason: String) -> Boolean)? = null
    ): SrishtiTurnResult = withContext(Dispatchers.Default) {
        if (EmergencyStopManager.isStopRequested()) {
            return@withContext SrishtiTurnResult.Failed("Emergency stop is currently active.")
        }

        _isBusy.value = true
        val taskId = UUID.randomUUID().toString()

        try {
            // 1. Log User turn to Session & Room
            database.taskDao().insertTask(TaskEntity(id = taskId, originalPrompt = userPrompt, status = TaskStatus.ANALYZING))
            sessionManager.logTurn(role = MessageRole.USER, content = userPrompt)
            memoryEngine.addShortTermTurn("User", userPrompt)

            // 2. Query Knowledge & Relevant Memories
            val deviceContext = knowledgeEngine.formatContextString()
            val recalledMemories = memoryEngine.retrieveRelevantContext(userPrompt)

            // 3. Synthesize dynamic persona system prompt
            val systemPrompt = personalityEngine.buildSystemPersonaPrompt(
                userContext = deviceContext,
                recalledMemories = recalledMemories
            )

            // 4. Select AI Provider with fallback
            val provider = selectProvider()

            // 5. Generate Decision
            val decisionResult = provider.generateDecision(
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageFrame = imageFrame
            )

            if (decisionResult.isFailure) {
                // Fallback to Deterministic offline provider
                RedactedLogger.w(TAG, "Primary provider failed, attempting offline deterministic fallback")
                val fallbackDecision = deterministicProvider.generateDecision(systemPrompt, userPrompt, imageFrame).getOrThrow()
                return@withContext handleDecision(taskId, fallbackDecision, onConfirmationNeeded)
            }

            val decision = decisionResult.getOrThrow()
            return@withContext handleDecision(taskId, decision, onConfirmationNeeded)

        } catch (e: Exception) {
            RedactedLogger.e(TAG, "Turn execution error: ${e.message}")
            database.taskDao().updateTaskStatus(taskId, TaskStatus.FAILED, e.message)
            SrishtiTurnResult.Failed(e.localizedMessage ?: "Unknown execution error")
        } finally {
            _isBusy.value = false
        }
    }

    private suspend fun handleDecision(
        taskId: String,
        decision: StructuredModelDecision,
        onConfirmationNeeded: (suspend (reason: String) -> Boolean)?
    ): SrishtiTurnResult {
        when (decision) {
            is StructuredModelDecision.DirectResponse -> {
                database.taskDao().updateTaskStatus(taskId, TaskStatus.COMPLETED)
                sessionManager.logTurn(
                    role = MessageRole.SRISHTI,
                    content = decision.content,
                    thought = decision.thought,
                    mood = personalityEngine.currentMood.value.name
                )
                memoryEngine.addShortTermTurn("Srishti", decision.content)
                return SrishtiTurnResult.ConversationalResponse(decision.content, decision.thought)
            }

            is StructuredModelDecision.ToolCall -> {
                database.taskDao().updateTaskStatus(taskId, TaskStatus.EXECUTING)
                val call = decision.call

                val outcome = toolRouter.routeAndExecute(
                    taskId = taskId,
                    stepIndex = 0,
                    toolName = call.name,
                    arguments = call.arguments,
                    onConfirmationNeeded = onConfirmationNeeded
                )

                val summary = if (outcome.success) {
                    "Executed ${call.name} successfully."
                } else {
                    "Action ${call.name} could not be completed: ${outcome.errorMessage}"
                }

                database.taskDao().updateTaskStatus(
                    taskId,
                    if (outcome.success) TaskStatus.COMPLETED else TaskStatus.FAILED,
                    outcome.errorMessage
                )

                sessionManager.logTurn(
                    role = MessageRole.TOOL,
                    content = summary,
                    thought = decision.thought,
                    toolCallJson = call.name,
                    toolResultJson = outcome.result?.data?.toString()
                )

                return SrishtiTurnResult.ToolExecuted(outcome, summary)
            }
        }
    }

    private fun selectProvider(): AIProvider {
        val selected = _activeProviderType.value
        return when (selected) {
            AIProviderType.GEMINI_CLOUD -> if (geminiProvider.isAvailable) geminiProvider else deterministicProvider
            AIProviderType.LOCAL_LLAMA -> if (localLlamaProvider.isAvailable) localLlamaProvider else deterministicProvider
            AIProviderType.OFFLINE_DETERMINISTIC -> deterministicProvider
        }
    }

    fun triggerEmergencyStop() {
        EmergencyStopManager.triggerEmergencyStop("User tapped Emergency Stop")
        scope.launch {
            sessionManager.logTurn(
                role = MessageRole.SYSTEM,
                content = "EMERGENCY STOP TRIGGERED: All actions and inference halted immediately."
            )
        }
    }

    fun resetEmergencyStop() {
        EmergencyStopManager.reset()
    }

    companion object {
        private const val TAG = "SrishtiCore"
    }
}
