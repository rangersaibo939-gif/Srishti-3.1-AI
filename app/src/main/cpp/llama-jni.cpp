#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include <android/log.h>

#include "llama.h"
#include "grammar-parser.h"

#define TAG "OpenDroid-LLamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::mutex g_mutex;
static struct llama_model * g_model = nullptr;
static struct llama_context * g_ctx = nullptr;
static std::atomic<bool> g_backend_initialized{false};
static std::atomic<bool> g_cancel_requested{false};

static void ensure_backend_init() {
    if (!g_backend_initialized.load()) {
        llama_backend_init();
        g_backend_initialized.store(true);
        LOGI("llama_backend_init() completed successfully.");
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_opendroid_app_core_inference_NativeLlamaCppAdapter_isNativeLibraryLoadedNative(
    JNIEnv * /* env */,
    jobject /* this */) {
    LOGI("Native library libopendroid_llama.so is loaded and operational.");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_opendroid_app_core_inference_NativeLlamaCppAdapter_isModelLoadedNative(
    JNIEnv * /* env */,
    jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_opendroid_app_core_inference_NativeLlamaCppAdapter_loadModelNative(
    JNIEnv *env,
    jobject /* this */,
    jstring model_path,
    jint context_size,
    jint num_threads) {
    std::lock_guard<std::mutex> lock(g_mutex);

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("loadModelNative requested for path: %s, ctx: %d, threads: %d", path, (int)context_size, (int)num_threads);

    ensure_backend_init();

    // Clean up existing model if one is loaded
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_free_model(g_model);
        g_model = nullptr;
    }

    struct llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;
    model_params.use_mlock = false;

    g_model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (g_model == nullptr) {
        LOGE("Failed to load GGUF model from path.");
        return JNI_FALSE;
    }

    struct llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (context_size > 0) ? (uint32_t)context_size : 2048;
    uint32_t threads = (num_threads > 0) ? (uint32_t)num_threads : 4;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;

    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (g_ctx == nullptr) {
        LOGE("Failed to create llama_context for model.");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    g_cancel_requested.store(false);
    LOGI("Model and Context loaded successfully. n_ctx=%u, threads=%u", ctx_params.n_ctx, threads);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_opendroid_app_core_inference_NativeLlamaCppAdapter_unloadModelNative(
    JNIEnv * /* env */,
    jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    LOGI("unloadModelNative requested.");
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    LOGI("Model and Context successfully unloaded.");
}

JNIEXPORT jstring JNICALL
Java_com_opendroid_app_core_inference_NativeLlamaCppAdapter_runInferenceConstrainedNative(
    JNIEnv *env,
    jobject /* this */,
    jstring prompt,
    jstring gbnf_grammar,
    jint max_tokens,
    jobject on_token_callback) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model == nullptr || g_ctx == nullptr) {
        LOGE("runInferenceConstrainedNative failed: No model currently loaded.");
        return env->NewStringUTF("{\"error\": \"NO_MODEL_LOADED\"}");
    }

    const char *prompt_raw = env->GetStringUTFChars(prompt, nullptr);
    const char *grammar_raw = (gbnf_grammar != nullptr) ? env->GetStringUTFChars(gbnf_grammar, nullptr) : "";
    std::string prompt_str(prompt_raw);
    std::string grammar_str(grammar_raw);

    env->ReleaseStringUTFChars(prompt, prompt_raw);
    if (gbnf_grammar != nullptr) {
        env->ReleaseStringUTFChars(gbnf_grammar, grammar_raw);
    }

    // Prepare token callback method lookup if Kotlin lambda provided
    jclass callback_class = nullptr;
    jmethodID callback_invoke_mid = nullptr;
    if (on_token_callback != nullptr) {
        callback_class = env->GetObjectClass(on_token_callback);
        if (callback_class != nullptr) {
            callback_invoke_mid = env->GetMethodID(callback_class, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");
        }
    }

    g_cancel_requested.store(false);

    // 1. Tokenize prompt
    int n_tokens_max = (int)prompt_str.length() + 128;
    std::vector<llama_token> tokens(n_tokens_max);
    int n_tokens = llama_tokenize(g_model, prompt_str.c_str(), (int32_t)prompt_str.length(), tokens.data(), n_tokens_max, true, false);
    if (n_tokens < 0) {
        n_tokens_max = -n_tokens;
        tokens.resize(n_tokens_max);
        n_tokens = llama_tokenize(g_model, prompt_str.c_str(), (int32_t)prompt_str.length(), tokens.data(), n_tokens_max, true, false);
    }
    tokens.resize(n_tokens);

    if (tokens.empty()) {
        LOGE("Tokenization resulted in 0 tokens.");
        return env->NewStringUTF("{\"error\": \"EMPTY_PROMPT_TOKENS\"}");
    }

    // 2. Feed prompt batch
    struct llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == n_tokens - 1) ? 1 : 0;
    }
    batch.n_tokens = n_tokens;

    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("llama_decode failed on prompt ingestion.");
        llama_batch_free(batch);
        return env->NewStringUTF("{\"error\": \"DECODE_PROMPT_FAILED\"}");
    }
    llama_batch_free(batch);

    // 3. Initialize GBNF Grammar Parser if grammar is specified
    struct llama_grammar * grammar = nullptr;
    if (!grammar_str.empty()) {
        try {
            auto parsed_state = grammar_parser::parse(grammar_str.c_str());
            if (!parsed_state.rules.empty()) {
                auto c_rules = parsed_state.c_rules();
                uint32_t root_id = 0;
                if (parsed_state.symbol_ids.find("root") != parsed_state.symbol_ids.end()) {
                    root_id = parsed_state.symbol_ids.at("root");
                }
                grammar = llama_grammar_init(c_rules.data(), c_rules.size(), root_id);
                if (grammar != nullptr) {
                    LOGI("GBNF grammar successfully compiled and initialized with root id: %u", root_id);
                }
            }
        } catch (const std::exception & ex) {
            LOGE("GBNF grammar parse exception: %s", ex.what());
        }
    }

    const int n_vocab = llama_n_vocab(g_model);
    int max_gen = (max_tokens > 0) ? (int)max_tokens : 512;
    int n_cur = n_tokens;
    std::string response_text;

    // 4. Autoregressive token generation loop
    while (n_cur < n_tokens + max_gen) {
        if (g_cancel_requested.load()) {
            LOGW("Inference loop cancelled by cancellation signal.");
            break;
        }

        const float * logits = llama_get_logits_ith(g_ctx, -1);
        std::vector<llama_token_data> candidates;
        candidates.reserve(n_vocab);
        for (llama_token token_id = 0; token_id < n_vocab; token_id++) {
            candidates.emplace_back(llama_token_data{token_id, logits[token_id], 0.0f});
        }
        llama_token_data_array candidates_p = { candidates.data(), candidates.size(), false };

        if (grammar != nullptr) {
            llama_grammar_sample(grammar, g_ctx, &candidates_p);
        }

        llama_token new_token_id = llama_sample_token_greedy(g_ctx, &candidates_p);

        if (llama_token_is_eog(g_model, new_token_id)) {
            LOGI("EOG token reached at step %d", n_cur - n_tokens);
            break;
        }

        if (grammar != nullptr) {
            llama_grammar_accept_token(grammar, g_ctx, new_token_id);
        }

        char piece_buf[128];
        int n_chars = llama_token_to_piece(g_model, new_token_id, piece_buf, sizeof(piece_buf), 0, false);
        if (n_chars > 0) {
            response_text.append(piece_buf, n_chars);

            // Stream token to Kotlin callback if provided
            if (on_token_callback != nullptr && callback_invoke_mid != nullptr) {
                jstring piece_jstr = env->NewStringUTF(std::string(piece_buf, n_chars).c_str());
                env->CallObjectMethod(on_token_callback, callback_invoke_mid, piece_jstr);
                env->DeleteLocalRef(piece_jstr);
            }
        }

        struct llama_batch next_batch = llama_batch_init(1, 0, 1);
        next_batch.token[0] = new_token_id;
        next_batch.pos[0] = n_cur;
        next_batch.n_seq_id[0] = 1;
        next_batch.seq_id[0][0] = 0;
        next_batch.logits[0] = 1;
        next_batch.n_tokens = 1;

        n_cur++;
        int decode_res = llama_decode(g_ctx, next_batch);
        llama_batch_free(next_batch);

        if (decode_res != 0) {
            LOGE("llama_decode step failed with code: %d", decode_res);
            break;
        }
    }

    if (grammar != nullptr) {
        llama_grammar_free(grammar);
    }

    return env->NewStringUTF(response_text.c_str());
}

JNIEXPORT void JNICALL
Java_com_opendroid_app_core_inference_NativeLlamaCppAdapter_cancelExecutionNative(
    JNIEnv * /* env */,
    jobject /* this */) {
    LOGI("cancelExecutionNative called - setting cancel flag.");
    g_cancel_requested.store(true);
}

} // extern "C"
