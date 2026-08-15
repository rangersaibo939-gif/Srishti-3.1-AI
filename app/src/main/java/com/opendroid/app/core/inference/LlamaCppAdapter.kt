package com.opendroid.app.core.inference

import com.opendroid.app.core.logging.RedactedLogger

/**
 * Interface and Status boundary for native llama.cpp arm64 JNI integration.
 *
 * Calls direct C++ APIs in libopendroid_llama.so (llama.cpp revision b3600).
 */
interface LlamaCppAdapter {
    fun isNativeLibraryLoaded(): Boolean
    fun isModelLoaded(): Boolean
    fun loadModel(modelPath: String, contextSize: Int = 2048, numThreads: Int = 4): Boolean
    fun unloadModel()
    fun runInferenceConstrained(
        prompt: String,
        gbnfGrammar: String = "",
        maxTokens: Int = 512,
        onToken: ((String) -> Unit)? = null
    ): String
    fun cancelCurrentExecution()
}

class NativeLlamaCppAdapter : LlamaCppAdapter {
    companion object {
        private const val TAG = "NativeLlamaCppAdapter"
        private var isLoaded = false

        init {
            try {
                System.loadLibrary("opendroid_llama")
                isLoaded = true
                RedactedLogger.i(TAG, "Successfully loaded native library: libopendroid_llama.so")
            } catch (e: UnsatisfiedLinkError) {
                isLoaded = false
                RedactedLogger.e(TAG, "Could not load libopendroid_llama.so (expected if running on host without arm64 NDK build): ${e.message}")
            }
        }
    }

    override fun isNativeLibraryLoaded(): Boolean = isLoaded && isNativeLibraryLoadedNative()

    override fun isModelLoaded(): Boolean {
        if (!isLoaded) return false
        return isModelLoadedNative()
    }

    override fun loadModel(modelPath: String, contextSize: Int, numThreads: Int): Boolean {
        if (!isLoaded) {
            RedactedLogger.w(TAG, "Cannot load model: Native library not linked.")
            return false
        }
        return loadModelNative(modelPath, contextSize, numThreads)
    }

    override fun unloadModel() {
        if (isLoaded) {
            unloadModelNative()
        }
    }

    override fun runInferenceConstrained(
        prompt: String,
        gbnfGrammar: String,
        maxTokens: Int,
        onToken: ((String) -> Unit)?
    ): String {
        if (!isLoaded) {
            throw UnsupportedOperationException("Native llama.cpp JNI engine is not loaded on this architecture.")
        }
        return runInferenceConstrainedNative(prompt, gbnfGrammar, maxTokens, onToken)
    }

    override fun cancelCurrentExecution() {
        if (isLoaded) {
            cancelExecutionNative()
        }
    }

    private external fun isNativeLibraryLoadedNative(): Boolean
    private external fun isModelLoadedNative(): Boolean
    private external fun loadModelNative(modelPath: String, contextSize: Int, numThreads: Int): Boolean
    private external fun unloadModelNative()
    private external fun runInferenceConstrainedNative(
        prompt: String,
        gbnfGrammar: String,
        maxTokens: Int,
        onTokenCallback: Any?
    ): String
    private external fun cancelExecutionNative()
}

class UnimplementedLlamaCppAdapter : LlamaCppAdapter {
    companion object {
        private const val TAG = "LlamaCppAdapter"
        const val STATUS = "FALLBACK_ADAPTER"
        const val TARGET_MODEL = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf / SmolLM2-135M-Instruct-Q4_K_M.gguf"
    }

    override fun isNativeLibraryLoaded(): Boolean = false
    override fun isModelLoaded(): Boolean = false

    override fun loadModel(modelPath: String, contextSize: Int, numThreads: Int): Boolean {
        RedactedLogger.w(TAG, "LlamaCppAdapter fallback: Native libllama.so not available in this test environment.")
        return false
    }

    override fun unloadModel() {}

    override fun runInferenceConstrained(
        prompt: String,
        gbnfGrammar: String,
        maxTokens: Int,
        onToken: ((String) -> Unit)?
    ): String {
        throw UnsupportedOperationException("Native llama.cpp JNI engine is not available.")
    }

    override fun cancelCurrentExecution() {}
}
