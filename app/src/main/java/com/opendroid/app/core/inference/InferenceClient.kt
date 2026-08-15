package com.opendroid.app.core.inference

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.opendroid.app.IInferenceCallback
import com.opendroid.app.IInferenceService
import com.opendroid.app.core.logging.RedactedLogger
import com.opendroid.app.service.InferenceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * IPC Client connecting AgentCore (main process) to InferenceService (:inference process)
 * Handles ServiceConnection, DeathRecipient crash watchdog, streaming callback dispatch,
 * and IPC cancellation.
 */
class InferenceClient(private val context: Context) {

    private var inferenceService: IInferenceService? = null
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val deathRecipient = IBinder.DeathRecipient {
        RedactedLogger.e(TAG, "FATAL: :inference process died (SIGSEGV/OOM/Crash). Watchdog triggered.")
        inferenceService = null
        _isConnected.value = false
        // Automatically attempt to rebind on death
        bind()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            RedactedLogger.i(TAG, "Connected to :inference process over Binder IPC.")
            inferenceService = IInferenceService.Stub.asInterface(service)
            try {
                service?.linkToDeath(deathRecipient, 0)
            } catch (e: Exception) {
                RedactedLogger.e(TAG, "Failed to link death recipient", e)
            }
            _isConnected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            RedactedLogger.w(TAG, "Disconnected from :inference process.")
            inferenceService = null
            _isConnected.value = false
        }
    }

    fun bind() {
        if (_isConnected.value) return
        val intent = Intent(context, InferenceService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        if (_isConnected.value) {
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                RedactedLogger.w(TAG, "Error unbinding inference service", e)
            }
            _isConnected.value = false
            inferenceService = null
        }
    }

    suspend fun inferAsync(
        prompt: String,
        gbnfGrammar: String = "",
        maxTokens: Int = 512,
        modelPath: String? = null,
        onToken: ((String) -> Unit)? = null
    ): Result<String> {
        val service = inferenceService
            ?: return Result.failure(IllegalStateException("InferenceService IPC is not connected."))

        val deferred = CompletableDeferred<Result<String>>()

        val reqJson = JSONObject().apply {
            put("action", "infer")
            put("prompt", prompt)
            put("gbnfGrammar", gbnfGrammar)
            put("maxTokens", maxTokens)
            if (!modelPath.isNullOrEmpty()) {
                put("modelPath", modelPath)
            }
        }.toString()

        val callback = object : IInferenceCallback.Stub() {
            override fun onToken(token: String?) {
                if (token != null) {
                    onToken?.invoke(token)
                }
            }

            override fun onComplete(responseJson: String?) {
                deferred.complete(Result.success(responseJson ?: ""))
            }

            override fun onError(errorCode: Int, errorMessage: String?) {
                val msg = errorMessage ?: "Unknown IPC error"
                deferred.complete(Result.failure(RuntimeException("Inference error [$errorCode]: $msg")))
            }
        }

        try {
            service.inferAsync(reqJson, callback)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return deferred.await()
    }

    fun cancelInflightInference() {
        try {
            inferenceService?.cancelInference()
            RedactedLogger.i(TAG, "Sent cancelInference IPC signal to :inference process.")
        } catch (e: Exception) {
            RedactedLogger.e(TAG, "Failed to send cancel signal over IPC", e)
        }
    }

    fun getServiceStatus(): String {
        return try {
            inferenceService?.serviceStatus ?: "{\"status\": \"DISCONNECTED\"}"
        } catch (e: Exception) {
            "{\"status\": \"ERROR\", \"message\": \"${e.message}\"}"
        }
    }

    companion object {
        private const val TAG = "InferenceClient"
    }
}
