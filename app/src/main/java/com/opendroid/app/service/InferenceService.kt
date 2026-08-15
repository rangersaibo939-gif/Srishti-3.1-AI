package com.opendroid.app.service

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.opendroid.app.IInferenceCallback
import com.opendroid.app.IInferenceService
import com.opendroid.app.core.inference.LlamaCppAdapter
import com.opendroid.app.core.inference.NativeLlamaCppAdapter
import com.opendroid.app.core.inference.UnimplementedLlamaCppAdapter
import com.opendroid.app.core.logging.RedactedLogger
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Isolated Android Service running in process "com.opendroid.app:inference"
 *
 * Hosts the native llama.cpp execution engine inside a dedicated OS subprocess
 * to protect the main UI and safety watchdogs from native OOM or memory faults.
 */
class InferenceService : Service() {

    private val isCancelled = AtomicBoolean(false)
    private lateinit var nativeAdapter: LlamaCppAdapter
    private var activeModelPath: String? = null

    companion object {
        private const val TAG = "InferenceService"
        private const val MIN_REQUIRED_RAM_BYTES = 400L * 1024L * 1024L // 400 MB minimum RAM threshold
    }

    override fun onCreate() {
        super.onCreate()
        RedactedLogger.i(TAG, "InferenceService started in dedicated process: com.opendroid.app:inference (PID: ${android.os.Process.myPid()})")

        // Initialize real NativeLlamaCppAdapter
        nativeAdapter = try {
            val adapter = NativeLlamaCppAdapter()
            if (adapter.isNativeLibraryLoaded()) {
                RedactedLogger.i(TAG, "NativeLlamaCppAdapter initialized with libopendroid_llama.so")
                adapter
            } else {
                RedactedLogger.w(TAG, "Native library not loaded, using fallback adapter")
                UnimplementedLlamaCppAdapter()
            }
        } catch (e: Throwable) {
            RedactedLogger.e(TAG, "Error initializing NativeLlamaCppAdapter: ${e.message}")
            UnimplementedLlamaCppAdapter()
        }
    }

    /**
     * Inspects system RAM availability using ActivityManager.MemoryInfo
     */
    private fun checkSystemMemorySafety(): Pair<Boolean, Long> {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val availBytes = memoryInfo.availMem
        val isSafe = availBytes >= MIN_REQUIRED_RAM_BYTES && !memoryInfo.lowMemory
        return Pair(isSafe, availBytes)
    }

    /**
     * Validates model file and safely loads it into the native llama.cpp context
     */
    private fun loadModelSafely(modelPath: String, contextSize: Int = 2048, threads: Int = 4): JSONObject {
        val result = JSONObject()
        val file = File(modelPath)

        if (!file.exists()) {
            result.put("success", false)
            result.put("error", "FILE_NOT_FOUND")
            result.put("message", "Model file does not exist at path: $modelPath")
            return result
        }

        if (!file.canRead()) {
            result.put("success", false)
            result.put("error", "FILE_NOT_READABLE")
            result.put("message", "Model file is not readable at path: $modelPath")
            return result
        }

        val (isRamSafe, availRam) = checkSystemMemorySafety()
        if (!isRamSafe) {
            result.put("success", false)
            result.put("error", "INSUFFICIENT_MEMORY")
            result.put("message", "Available memory ($availRam bytes) is below safe threshold ($MIN_REQUIRED_RAM_BYTES bytes)")
            return result
        }

        val loaded = nativeAdapter.loadModel(modelPath, contextSize, threads)
        if (loaded) {
            activeModelPath = modelPath
            result.put("success", true)
            result.put("modelPath", modelPath)
            result.put("contextSize", contextSize)
        } else {
            result.put("success", false)
            result.put("error", "NATIVE_LOAD_FAILED")
            result.put("message", "llama_load_model_from_file returned null.")
        }
        return result
    }

    private val binder = object : IInferenceService.Stub() {
        override fun inferAsync(requestJson: String?, callback: IInferenceCallback?) {
            isCancelled.set(false)

            if (requestJson == null) {
                callback?.onError(400, "{\"error\": \"INVALID_REQUEST\", \"message\": \"requestJson is null\"}")
                return
            }

            try {
                val req = JSONObject(requestJson)
                val action = req.optString("action", "infer")

                if (action == "load_model") {
                    val modelPath = req.getString("modelPath")
                    val ctxSize = req.optInt("contextSize", 2048)
                    val threads = req.optInt("numThreads", 4)
                    val loadRes = loadModelSafely(modelPath, ctxSize, threads)
                    if (loadRes.getBoolean("success")) {
                        callback?.onComplete(loadRes.toString())
                    } else {
                        callback?.onError(500, loadRes.toString())
                    }
                    return
                }

                val prompt = req.optString("prompt", "")
                val grammar = req.optString("gbnfGrammar", "")
                val maxTokens = req.optInt("maxTokens", 256)

                if (!nativeAdapter.isNativeLibraryLoaded()) {
                    callback?.onError(501, "{\"error\": \"NATIVE_LIBRARY_UNAVAILABLE\", \"message\": \"libopendroid_llama.so is not available on host.\"}")
                    return
                }

                if (!nativeAdapter.isModelLoaded()) {
                    // Try auto-loading model if modelPath supplied in request
                    val modelPath = req.optString("modelPath", "")
                    if (modelPath.isNotEmpty()) {
                        val loadRes = loadModelSafely(modelPath)
                        if (!loadRes.getBoolean("success")) {
                            callback?.onError(500, loadRes.toString())
                            return
                        }
                    } else {
                        callback?.onError(400, "{\"error\": \"NO_MODEL_LOADED\", \"message\": \"No GGUF model loaded in :inference process.\"}")
                        return
                    }
                }

                if (isCancelled.get()) {
                    callback?.onError(499, "{\"error\": \"CANCELLED\", \"message\": \"Execution cancelled before start.\"}")
                    return
                }

                val response = nativeAdapter.runInferenceConstrained(
                    prompt = prompt,
                    gbnfGrammar = grammar,
                    maxTokens = maxTokens,
                    onToken = { token ->
                        try {
                            callback?.onToken(token)
                        } catch (e: Exception) {
                            RedactedLogger.w(TAG, "Failed to dispatch onToken IPC: ${e.message}")
                        }
                    }
                )
                callback?.onComplete(response)
            } catch (e: Throwable) {
                RedactedLogger.e(TAG, "Inference execution failed: ${e.message}")
                callback?.onError(500, "{\"error\": \"EXECUTION_FAILED\", \"message\": \"${e.message}\"}")
            }
        }

        override fun inferSync(requestJson: String?): String {
            if (requestJson == null) {
                return "{\"error\": \"INVALID_REQUEST\"}"
            }
            return try {
                val req = JSONObject(requestJson)
                val prompt = req.optString("prompt", "")
                val grammar = req.optString("gbnfGrammar", "")
                val maxTokens = req.optInt("maxTokens", 256)

                if (!nativeAdapter.isNativeLibraryLoaded() || !nativeAdapter.isModelLoaded()) {
                    return "{\"error\": \"ENGINE_NOT_READY\"}"
                }
                nativeAdapter.runInferenceConstrained(prompt, grammar, maxTokens, null)
            } catch (e: Throwable) {
                "{\"error\": \"SYNC_INFERENCE_FAILED\", \"message\": \"${e.message}\"}"
            }
        }

        override fun cancelInference() {
            RedactedLogger.w(TAG, "cancelInference IPC signal received. Halting native llama.cpp execution.")
            isCancelled.set(true)
            nativeAdapter.cancelCurrentExecution()
        }

        override fun getServiceStatus(): String {
            val (isRamSafe, availRam) = checkSystemMemorySafety()
            val status = JSONObject()
            status.put("process", ":inference")
            status.put("pid", android.os.Process.myPid())
            status.put("nativeLibraryLoaded", nativeAdapter.isNativeLibraryLoaded())
            status.put("modelLoaded", nativeAdapter.isModelLoaded())
            status.put("activeModelPath", activeModelPath ?: "none")
            status.put("availableRamBytes", availRam)
            status.put("isRamSafe", isRamSafe)
            status.put("llamaCppRevision", "b3600")
            return status.toString()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        RedactedLogger.i(TAG, "Client bound to IInferenceService")
        return binder
    }

    override fun onDestroy() {
        RedactedLogger.i(TAG, "InferenceService destroying. Unloading native model context.")
        nativeAdapter.unloadModel()
        super.onDestroy()
    }
}
