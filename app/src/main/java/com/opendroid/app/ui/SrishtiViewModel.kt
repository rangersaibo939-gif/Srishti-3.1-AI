package com.opendroid.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opendroid.app.OpenDroidApplication
import com.opendroid.app.core.agent.SrishtiCore
import com.opendroid.app.core.agent.SrishtiTurnResult
import com.opendroid.app.core.ai.AIProviderType
import com.opendroid.app.core.avatar.AvatarVisualState
import com.opendroid.app.core.domain.ToolCall
import com.opendroid.app.core.export.ProjectExporter
import com.opendroid.app.core.memory.MemoryEngine
import com.opendroid.app.core.personality.PersonalityProfile
import com.opendroid.app.core.personality.SrishtiMood
import com.opendroid.app.core.security.KeystoreSecretProvider
import com.opendroid.app.core.upgrade.SystemHealthReport
import com.opendroid.app.core.upgrade.UpgradeEngine
import com.opendroid.app.core.voice.VoiceEngine
import com.opendroid.app.core.voice.VoiceListener
import com.opendroid.app.core.voice.VoiceState
import com.opendroid.app.data.database.ConversationEntity
import com.opendroid.app.data.database.MemoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCancellableCoroutine

data class PendingConfirmation(
    val toolCall: ToolCall,
    val reason: String,
    val onDecision: (Boolean) -> Unit
)

/**
 * Main ViewModel for Srishti 3.0
 * Exposes reactive StateFlows for Compose UI, connects VoiceEngine, SrishtiCore, MemoryEngine, and AvatarEngine.
 */
class SrishtiViewModel(application: Application) : AndroidViewModel(application), VoiceListener {

    private val app = application as OpenDroidApplication
    val srishtiCore: SrishtiCore = SrishtiCore(
        context = application,
        database = app.database,
        scope = viewModelScope,
        apiKeyProvider = { KeystoreSecretProvider.getString("gemini_api_key", null) }
    )

    val voiceEngine = VoiceEngine(application, viewModelScope)
    val upgradeEngine = UpgradeEngine(application)
    val projectExporter = ProjectExporter(application)

    // Reactive StateFlows for Compose UI
    val avatarState: StateFlow<AvatarVisualState> = srishtiCore.avatarEngine.avatarState
    val currentMood: StateFlow<SrishtiMood> = srishtiCore.personalityEngine.currentMood
    val personalityProfile: StateFlow<PersonalityProfile> = srishtiCore.personalityEngine.profile
    val activeProviderType: StateFlow<AIProviderType> = srishtiCore.activeProviderType
    val isBusy: StateFlow<Boolean> = srishtiCore.isBusy
    val voiceState: StateFlow<VoiceState> = voiceEngine.voiceState
    val rmsLevel: StateFlow<Float> = voiceEngine.rmsLevel
    val isContinuousMode: StateFlow<Boolean> = voiceEngine.isContinuousMode
    val exportProgress: StateFlow<ProjectExporter.ExportProgress> = projectExporter.progress

    val messages: StateFlow<List<ConversationEntity>> = srishtiCore.sessionManager.getActiveMessagesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val memories: StateFlow<List<MemoryEntity>> = srishtiCore.memoryEngine.getAllMemoriesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _healthReport = MutableStateFlow(upgradeEngine.performDiagnostics())
    val healthReport: StateFlow<SystemHealthReport> = _healthReport.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        voiceEngine.setVoiceListener(this)
    }

    fun executeUserPrompt(prompt: String) {
        if (prompt.isBlank()) return
        voiceEngine.stopSpeaking()
        voiceEngine.setThinking()
        RedactedLogger.i("SrishtiViewModel", "[AI_REQUEST] Submitting prompt to SrishtiCore: $prompt")

        viewModelScope.launch {
            val result = srishtiCore.processTurn(
                userPrompt = prompt,
                onConfirmationNeeded = { reason ->
                    suspendCancellableCoroutine { cont ->
                        _pendingConfirmation.value = PendingConfirmation(
                            toolCall = ToolCall("pending_action", emptyMap()),
                            reason = reason,
                            onDecision = { approved ->
                                _pendingConfirmation.value = null
                                cont.resume(approved)
                            }
                        )
                    }
                }
            )

            when (result) {
                is SrishtiTurnResult.ConversationalResponse -> {
                    RedactedLogger.i("SrishtiViewModel", "[AI_RESPONSE] Received conversational text: ${result.text}")
                    voiceEngine.speak(result.text)
                }
                is SrishtiTurnResult.ToolExecuted -> {
                    RedactedLogger.i("SrishtiViewModel", "[AI_RESPONSE] Tool execution summary: ${result.summary}")
                    voiceEngine.speak(result.summary)
                }
                is SrishtiTurnResult.Failed -> {
                    RedactedLogger.e("SrishtiViewModel", "[AI_RESPONSE] Turn failed: ${result.error}")
                    _errorMessage.value = result.error
                    voiceEngine.speak("I encountered an issue: ${result.error}")
                }
            }
        }
    }

    fun startVoiceInput() {
        voiceEngine.startListening()
    }

    fun stopVoiceInput() {
        voiceEngine.stopListening()
    }

    fun toggleContinuousVoice(): Boolean {
        return voiceEngine.toggleContinuousMode()
    }

    fun setContinuousMode(enabled: Boolean) {
        voiceEngine.setContinuousMode(enabled)
    }

    fun triggerEmergencyStop() {
        voiceEngine.interruptAll()
        srishtiCore.triggerEmergencyStop()
        _pendingConfirmation.value = null
    }

    fun resetEmergencyStop() {
        srishtiCore.resetEmergencyStop()
    }

    fun setMood(mood: SrishtiMood) {
        srishtiCore.personalityEngine.updateMood(mood)
        srishtiCore.avatarEngine.updateMood(mood)
        // Adaptive voice tuning per mood
        when (mood) {
            SrishtiMood.WARM -> voiceEngine.setVoiceParameters(pitch = 1.05f, rate = 1.0f)
            SrishtiMood.PLAYFUL -> voiceEngine.setVoiceParameters(pitch = 1.15f, rate = 1.08f)
            SrishtiMood.FOCUSED -> voiceEngine.setVoiceParameters(pitch = 0.98f, rate = 1.02f)
            SrishtiMood.EMPATHETIC -> voiceEngine.setVoiceParameters(pitch = 1.02f, rate = 0.94f)
            SrishtiMood.CURIOUS -> voiceEngine.setVoiceParameters(pitch = 1.12f, rate = 1.04f)
            SrishtiMood.PROTECTIVE -> voiceEngine.setVoiceParameters(pitch = 0.95f, rate = 0.98f)
        }
    }

    fun setAIProvider(providerType: AIProviderType) {
        srishtiCore.setActiveProvider(providerType)
    }

    fun saveApiKey(apiKey: String) {
        KeystoreSecretProvider.putString("gemini_api_key", apiKey.trim())
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            srishtiCore.memoryEngine.deleteMemory(id)
        }
    }

    fun clearSession() {
        viewModelScope.launch {
            srishtiCore.sessionManager.clearCurrentSession()
        }
    }

    fun refreshHealthReport() {
        _healthReport.value = upgradeEngine.performDiagnostics()
    }

    fun exportProjectZip(onComplete: (File?) -> Unit = {}) {
        viewModelScope.launch {
            val file = projectExporter.exportProjectZip()
            onComplete(file)
        }
    }

    fun getShareIntent(file: File) = projectExporter.createShareIntent(file)

    suspend fun saveZipToStream(file: File, outputStream: OutputStream) {
        projectExporter.saveToUri(file, outputStream)
    }

    // VoiceListener callbacks
    override fun onSpeechRecognized(text: String, isFinal: Boolean) {
        if (isFinal && text.isNotBlank()) {
            executeUserPrompt(text)
        }
    }

    override fun onVoiceStateChanged(state: VoiceState) {
        srishtiCore.avatarEngine.updateVoiceAndAudio(state, voiceEngine.rmsLevel.value)
    }

    override fun onAudioRmsChanged(rmsDb: Float) {
        srishtiCore.avatarEngine.updateVoiceAndAudio(voiceEngine.voiceState.value, rmsDb)
    }

    override fun onError(errorMessage: String) {
        _errorMessage.value = errorMessage
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
    }
}
