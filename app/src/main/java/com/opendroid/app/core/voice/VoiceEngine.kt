package com.opendroid.app.core.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.opendroid.app.core.logging.RedactedLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    INTERRUPTED,
    ERROR,
    OFFLINE
}

interface VoiceListener {
    fun onSpeechRecognized(text: String, isFinal: Boolean)
    fun onVoiceStateChanged(state: VoiceState)
    fun onAudioRmsChanged(rmsDb: Float)
    fun onError(errorMessage: String)
}

/**
 * First-Class Voice Engine for Srishti 3.0
 * Manages native Android SpeechRecognizer (ASR) & TextToSpeech (TTS) pipelines
 * with full support for true continuous conversation, interruption/barge-in, 
 * audio waveform metrics, and speaker routing.
 */
class VoiceEngine(
    private val context: Context,
    private val scope: CoroutineScope
) : TextToSpeech.OnInitListener {

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _isContinuousMode = MutableStateFlow(false)
    val isContinuousMode: StateFlow<Boolean> = _isContinuousMode.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var listener: VoiceListener? = null

    private var retryJob: Job? = null
    private var isDestroyed = false

    private var speechPitch = 1.05f
    private var speechRate = 1.0f

    init {
        initTts()
    }

    fun setVoiceListener(listener: VoiceListener) {
        this.listener = listener
    }

    fun setContinuousMode(enabled: Boolean) {
        _isContinuousMode.value = enabled
        if (!enabled) {
            retryJob?.cancel()
            stopListening()
            stopSpeaking()
            updateState(VoiceState.IDLE)
        } else {
            startListening()
        }
    }

    fun toggleContinuousMode(): Boolean {
        val newState = !_isContinuousMode.value
        setContinuousMode(newState)
        return newState
    }

    fun setVoiceParameters(pitch: Float, rate: Float) {
        this.speechPitch = pitch
        this.speechRate = rate
        textToSpeech?.setPitch(pitch)
        textToSpeech?.setSpeechRate(rate)
    }

    private fun initTts() {
        try {
            textToSpeech = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            RedactedLogger.e(TAG, "Failed to instantiate TextToSpeech: ${e.message}")
            updateState(VoiceState.OFFLINE)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let { tts ->
                val result = tts.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    RedactedLogger.w(TAG, "TTS Language US not supported, trying default")
                    tts.setLanguage(Locale.getDefault())
                }

                // Route to media/main phone speaker stream
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts.setAudioAttributes(audioAttributes)
                }

                isTtsInitialized = true
                tts.setPitch(speechPitch)
                tts.setSpeechRate(speechRate)

                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        RedactedLogger.i(TAG, "[TTS_START] Utterance begun: $utteranceId")
                        updateState(VoiceState.SPEAKING)
                    }

                    override fun onDone(utteranceId: String?) {
                        RedactedLogger.i(TAG, "[TTS_DONE] Utterance completed: $utteranceId")
                        updateState(VoiceState.IDLE)
                        if (_isContinuousMode.value && !isDestroyed) {
                            scope.launch(Dispatchers.Main) {
                                delay(280) // Conversational turn transition pause
                                if (_isContinuousMode.value && _voiceState.value != VoiceState.SPEAKING) {
                                    RedactedLogger.i(TAG, "[CONTINUOUS_RELISTEN] Automatically restarting SpeechRecognizer")
                                    startListening()
                                }
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        RedactedLogger.e(TAG, "[TTS_ERROR] Utterance error for: $utteranceId")
                        updateState(VoiceState.ERROR)
                        if (_isContinuousMode.value && !isDestroyed) {
                            scope.launch(Dispatchers.Main) {
                                delay(500)
                                if (_isContinuousMode.value) {
                                    RedactedLogger.i(TAG, "[CONTINUOUS_RELISTEN] Recovering from TTS error to listen")
                                    startListening()
                                }
                            }
                        }
                    }
                })
            }
        } else {
            RedactedLogger.e(TAG, "TTS Initialization failed: $status")
            updateState(VoiceState.OFFLINE)
        }
    }

    fun startListening() {
        if (isDestroyed) return
        retryJob?.cancel()

        scope.launch(Dispatchers.Main) {
            try {
                // If Srishti is speaking, stop speaking when user initiates listening
                stopSpeaking()

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    RedactedLogger.e(TAG, "Speech recognition not available on device")
                    updateState(VoiceState.ERROR)
                    listener?.onError("Speech recognition not available on device")
                    return@launch
                }

                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createRecognitionListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    // Set speech detection silence thresholds
                    putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 1500L)
                    putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 1000L)
                }

                speechRecognizer?.startListening(intent)
                updateState(VoiceState.LISTENING)
            } catch (e: Exception) {
                RedactedLogger.e(TAG, "Error starting speech recognition: ${e.message}")
                handleRecoverableError()
            }
        }
    }

    fun stopListening() {
        retryJob?.cancel()
        scope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.stopListening()
                if (_voiceState.value == VoiceState.LISTENING) {
                    updateState(VoiceState.IDLE)
                }
            } catch (e: Exception) {
                RedactedLogger.e(TAG, "Error stopping speech recognition: ${e.message}")
            }
        }
    }

    fun speak(text: String, utteranceId: String = "srishti_${System.currentTimeMillis()}") {
        if (isDestroyed) return
        retryJob?.cancel()

        if (!isTtsInitialized || textToSpeech == null) {
            RedactedLogger.w(TAG, "TTS not ready yet - buffering speech")
            // If continuous mode is active, don't stall
            if (_isContinuousMode.value) {
                scope.launch(Dispatchers.Main) {
                    delay(1000)
                    if (_isContinuousMode.value) startListening()
                }
            }
            return
        }

        stopListening()
        updateState(VoiceState.SPEAKING)

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stopSpeaking() {
        if (textToSpeech?.isSpeaking == true) {
            textToSpeech?.stop()
            updateState(VoiceState.INTERRUPTED)
        }
    }

    /**
     * Interruption / Barge-in trigger:
     * User began speaking while Srishti was delivering audio.
     */
    fun onUserBargeIn() {
        if (_voiceState.value == VoiceState.SPEAKING || textToSpeech?.isSpeaking == true) {
            RedactedLogger.i(TAG, "Barge-in detected: stopping TTS output")
            stopSpeaking()
            updateState(VoiceState.LISTENING)
        }
    }

    fun interruptAll() {
        retryJob?.cancel()
        stopSpeaking()
        stopListening()
        updateState(VoiceState.INTERRUPTED)
    }

    fun setThinking() {
        stopSpeaking()
        updateState(VoiceState.THINKING)
    }

    fun setIdle() {
        updateState(VoiceState.IDLE)
    }

    private fun updateState(newState: VoiceState) {
        _voiceState.value = newState
        listener?.onVoiceStateChanged(newState)
    }

    private fun handleRecoverableError() {
        if (_isContinuousMode.value && !isDestroyed) {
            retryJob?.cancel()
            retryJob = scope.launch(Dispatchers.Main) {
                updateState(VoiceState.IDLE)
                delay(350)
                if (_isContinuousMode.value && _voiceState.value != VoiceState.SPEAKING) {
                    startListening()
                }
            }
        } else {
            updateState(VoiceState.IDLE)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateState(VoiceState.LISTENING)
            }

            override fun onBeginningOfSpeech() {
                // Interruption check
                onUserBargeIn()
            }

            override fun onRmsChanged(rmsdB: Float) {
                _rmsLevel.value = rmsdB
                listener?.onAudioRmsChanged(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                updateState(VoiceState.THINKING)
            }

            override fun onError(error: Int) {
                RedactedLogger.w(TAG, "Speech recognition error code: $error")
                _rmsLevel.value = 0f

                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // User was silent: in continuous mode, recover seamlessly
                        handleRecoverableError()
                    }
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_BUSY -> {
                        // Session busy: recover after slight delay
                        handleRecoverableError()
                    }
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        listener?.onError("Network issue with speech recognizer")
                        handleRecoverableError()
                    }
                    else -> {
                        updateState(VoiceState.ERROR)
                        listener?.onError("Voice recognizer error ($error)")
                        handleRecoverableError()
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                _rmsLevel.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""

                if (text.isNotBlank()) {
                    RedactedLogger.i(TAG, "[ASR_FINAL_TEXT] Captured speech: $text")
                    updateState(VoiceState.THINKING)
                    listener?.onSpeechRecognized(text, isFinal = true)
                } else {
                    handleRecoverableError()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                if (text.isNotBlank()) {
                    onUserBargeIn()
                    listener?.onSpeechRecognized(text, isFinal = false)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun release() {
        isDestroyed = true
        retryJob?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsInitialized = false
    }

    companion object {
        private const val TAG = "SrishtiVoiceEngine"
    }
}
