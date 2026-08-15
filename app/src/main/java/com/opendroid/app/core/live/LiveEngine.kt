package com.opendroid.app.core.live

import com.opendroid.app.core.logging.RedactedLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

enum class LiveSessionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    STREAMING,
    RECONNECTING,
    FAILED
}

interface LiveSessionListener {
    fun onStateChanged(state: LiveSessionState)
    fun onTextChunkReceived(chunk: String)
    fun onAudioDataReceived(pcmData: ByteArray)
    fun onError(error: String)
}

/**
 * Gemini Live Engine for Srishti 3.0
 * Manages low-latency bidirectional real-time audio & text streams over WebSocket
 * with exponential backoff auto-reconnect and graceful fallback to offline/text modes.
 */
class LiveEngine(
    private val scope: CoroutineScope,
    private val apiKeyProvider: () -> String?
) {

    private val _state = MutableStateFlow(LiveSessionState.DISCONNECTED)
    val state: StateFlow<LiveSessionState> = _state.asStateFlow()

    private var webSocket: WebSocket? = null
    private var listener: LiveSessionListener? = null
    private var reconnectJob: Job? = null
    private var retryCount = 0
    private val maxRetries = 3

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    fun setListener(listener: LiveSessionListener) {
        this.listener = listener
    }

    fun connect(endpointUrl: String = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent") {
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            RedactedLogger.w(TAG, "No API key configured for Gemini Live, fallback active")
            _state.value = LiveSessionState.DISCONNECTED
            listener?.onError("Gemini API key not configured")
            return
        }

        reconnectJob?.cancel()
        _state.value = LiveSessionState.CONNECTING
        listener?.onStateChanged(LiveSessionState.CONNECTING)

        val fullUrl = "$endpointUrl?key=$apiKey"
        val request = Request.Builder().url(fullUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                RedactedLogger.i(TAG, "Gemini Live WebSocket opened successfully")
                retryCount = 0
                _state.value = LiveSessionState.CONNECTED
                listener?.onStateChanged(LiveSessionState.CONNECTED)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                _state.value = LiveSessionState.STREAMING
                listener?.onTextChunkReceived(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                RedactedLogger.i(TAG, "Live WebSocket closing: $code / $reason")
                _state.value = LiveSessionState.DISCONNECTED
                listener?.onStateChanged(LiveSessionState.DISCONNECTED)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                RedactedLogger.e(TAG, "Live WebSocket failure: ${t.message}")
                _state.value = LiveSessionState.FAILED
                listener?.onStateChanged(LiveSessionState.FAILED)
                listener?.onError("Real-time stream error: ${t.localizedMessage}")
                attemptReconnect()
            }
        })
    }

    fun sendText(message: String) {
        if (_state.value == LiveSessionState.CONNECTED || _state.value == LiveSessionState.STREAMING) {
            val payload = """{"realtime_input":{"media_chunks":[{"mime_type":"text/plain","data":"$message"}]}}"""
            webSocket?.send(payload)
        }
    }

    fun sendAudioChunk(pcmData: ByteArray) {
        if (_state.value == LiveSessionState.CONNECTED || _state.value == LiveSessionState.STREAMING) {
            val base64Audio = android.util.Base64.encodeToString(pcmData, android.util.Base64.NO_WRAP)
            val payload = """{"realtime_input":{"media_chunks":[{"mime_type":"audio/pcm;rate=16000","data":"$base64Audio"}]}}"""
            webSocket?.send(payload)
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _state.value = LiveSessionState.DISCONNECTED
        listener?.onStateChanged(LiveSessionState.DISCONNECTED)
    }

    private fun attemptReconnect() {
        if (retryCount < maxRetries) {
            retryCount++
            _state.value = LiveSessionState.RECONNECTING
            listener?.onStateChanged(LiveSessionState.RECONNECTING)
            reconnectJob = scope.launch(Dispatchers.IO) {
                val backoffMs = (1000L * (1 shl retryCount)).coerceAtMost(8000L)
                delay(backoffMs)
                connect()
            }
        } else {
            RedactedLogger.w(TAG, "Max LiveEngine reconnection attempts reached. Falling back to REST/Local.")
            _state.value = LiveSessionState.FAILED
            listener?.onStateChanged(LiveSessionState.FAILED)
        }
    }

    companion object {
        private const val TAG = "SrishtiLiveEngine"
    }
}
