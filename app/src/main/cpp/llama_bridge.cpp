// JNI bridge between com.ailocal.app.llm.LlamaBridge (Kotlin) and llama.cpp.
//
// Design goals, matching the product requirements:
//  - Generic across GGUF model families - we never assume a specific
//    architecture; everything we report back comes from what llama.cpp
//    itself detects from the GGUF metadata.
//  - Never crash the process on a bad/unsupported model - all llama.cpp
//    calls are guarded and failures are surfaced as a null handle / JSON
//    error field rather than letting an exception or abort cross the JNI
//    boundary.
//  - Only one model resident at a time is enforced by the Kotlin layer;
//    this file simply does not do anything surprising with global state
//    beyond what's needed for cooperative cancellation.

#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <string>
#include <sstream>
#include <vector>
#include <mutex>

#include "llama.h"

#define LOG_TAG "AiLocalLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct EngineHandle {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    std::atomic<bool> cancelRequested{false};
    int contextSize = 0;
};

std::mutex g_initMutex;
bool g_backendInitialized = false;

void ensureBackendInitialized() {
    std::lock_guard<std::mutex> lock(g_initMutex);
    if (!g_backendInitialized) {
        llama_backend_init();
        g_backendInitialized = true;
    }
}

std::string jstringToStd(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

// Rough heuristic label from parameter count - purely cosmetic for the UI,
// never used to decide engine behavior.
std::string paramCountLabel(int64_t nParams) {
    if (nParams <= 0) return "unknown";
    double billions = nParams / 1e9;
    std::ostringstream oss;
    if (billions >= 1.0) {
        oss.precision(2);
        oss << std::fixed << billions << "B";
    } else {
        double millions = nParams / 1e6;
        oss.precision(0);
        oss << std::fixed << millions << "M";
    }
    return oss.str();
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_ailocal_app_llm_LlamaBridge_nativeLoadModel(
    JNIEnv* env, jobject /*thiz*/,
    jstring jModelPath, jint jContextSize, jint jThreads, jint jBatchSize, jint jSeed) {

    ensureBackendInitialized();

    std::string modelPath = jstringToStd(env, jModelPath);
    LOGI("Loading model: %s", modelPath.c_str());

    llama_model_params modelParams = llama_model_default_params();
    // CPU-only inference: safest default across the wide range of Android
    // GPUs/driver states. Keeps behavior predictable on low-end test devices.
    modelParams.n_gpu_layers = 0;

    llama_model* model = llama_load_model_from_file(modelPath.c_str(), modelParams);
    if (model == nullptr) {
        LOGE("llama_load_model_from_file failed for %s", modelPath.c_str());
        return 0;
    }

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = static_cast<uint32_t>(jContextSize > 0 ? jContextSize : 2048);
    ctxParams.n_threads = jThreads > 0 ? jThreads : 4;
    ctxParams.n_threads_batch = ctxParams.n_threads;
    ctxParams.n_batch = static_cast<uint32_t>(jBatchSize > 0 ? jBatchSize : 512);
    ctxParams.seed = static_cast<uint32_t>(jSeed);

    llama_context* ctx = llama_new_context_with_model(model, ctxParams);
    if (ctx == nullptr) {
        LOGE("llama_new_context_with_model failed");
        llama_free_model(model);
        return 0;
    }

    auto* handle = new EngineHandle();
    handle->model = model;
    handle->ctx = ctx;
    handle->contextSize = static_cast<int>(ctxParams.n_ctx);

    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT void JNICALL
Java_com_ailocal_app_llm_LlamaBridge_nativeUnloadModel(JNIEnv* /*env*/, jobject /*thiz*/, jlong jHandle) {
    if (jHandle == 0) return;
    auto* handle = reinterpret_cast<EngineHandle*>(jHandle);
    if (handle->ctx) llama_free(handle->ctx);
    if (handle->model) llama_free_model(handle->model);
    delete handle;
}

JNIEXPORT jstring JNICALL
Java_com_ailocal_app_llm_LlamaBridge_nativeGetModelInfo(JNIEnv* env, jobject /*thiz*/, jlong jHandle) {
    if (jHandle == 0) return env->NewStringUTF("{\"error\":\"no model loaded\"}");
    auto* handle = reinterpret_cast<EngineHandle*>(jHandle);

    char archBuf[128] = {0};
    llama_model_meta_val_str(handle->model, "general.architecture", archBuf, sizeof(archBuf));
    std::string architecture = archBuf[0] != '\0' ? std::string(archBuf) : "unknown";

    int64_t nParams = static_cast<int64_t>(llama_model_n_params(handle->model));
    int32_t vocabSize = llama_n_vocab(handle->model);

    char quantBuf[64] = {0};
    const char* quantDesc = llama_model_quant_desc(handle->model);
    std::string quantization = quantDesc != nullptr ? std::string(quantDesc) : "unknown";

    std::ostringstream json;
    json << "{"
         << "\"architecture\":\"" << architecture << "\","
         << "\"param_count_label\":\"" << paramCountLabel(nParams) << "\","
         << "\"context_length\":" << handle->contextSize << ","
         << "\"vocab_size\":" << vocabSize << ","
         << "\"quantization\":\"" << quantization << "\""
         << "}";

    return env->NewStringUTF(json.str().c_str());
}

JNIEXPORT jint JNICALL
Java_com_ailocal_app_llm_LlamaBridge_nativeGenerate(
    JNIEnv* env, jobject /*thiz*/, jlong jHandle,
    jstring jPrompt, jint jMaxTokens, jfloat jTemperature, jfloat jTopP, jint jTopK,
    jobject jCallback) {

    if (jHandle == 0) return 0;
    auto* handle = reinterpret_cast<EngineHandle*>(jHandle);
    handle->cancelRequested.store(false);

    std::string prompt = jstringToStd(env, jPrompt);

    // Resolve the TokenCallback.onToken(String) -> boolean method once.
    jclass callbackClass = env->GetObjectClass(jCallback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)Z");

    // Tokenize prompt
    std::vector<llama_token> tokens(prompt.size() + 16);
    int nTokens = llama_tokenize(
        handle->model, prompt.c_str(), static_cast<int32_t>(prompt.size()),
        tokens.data(), static_cast<int32_t>(tokens.size()), true, true
    );
    if (nTokens < 0) {
        tokens.resize(-nTokens);
        nTokens = llama_tokenize(
            handle->model, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), true, true
        );
    }
    tokens.resize(nTokens);

    llama_batch batch = llama_batch_init(static_cast<int32_t>(tokens.size()), 0, 1);
    for (int i = 0; i < static_cast<int>(tokens.size()); i++) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == static_cast<int>(tokens.size()) - 1);
    }
    batch.n_tokens = static_cast<int32_t>(tokens.size());

    if (llama_decode(handle->ctx, batch) != 0) {
        LOGE("Initial prompt decode failed");
        llama_batch_free(batch);
        return 0;
    }

    int generated = 0;
    int nCur = static_cast<int32_t>(tokens.size());
    int maxTokens = jMaxTokens > 0 ? jMaxTokens : 256;

    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(jTopK > 0 ? jTopK : 40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(jTopP > 0 ? jTopP : 0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(jTemperature > 0 ? jTemperature : 0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(static_cast<uint32_t>(LLAMA_DEFAULT_SEED)));

    while (generated < maxTokens) {
        if (handle->cancelRequested.load()) {
            LOGI("Generation cancelled by caller");
            break;
        }

        llama_token newToken = llama_sampler_sample(sampler, handle->ctx, -1);

        if (llama_token_is_eog(handle->model, newToken)) {
            break;
        }

        char pieceBuf[256];
        int pieceLen = llama_token_to_piece(handle->model, newToken, pieceBuf, sizeof(pieceBuf), 0, true);
        std::string piece = pieceLen > 0 ? std::string(pieceBuf, pieceLen) : "";

        jstring jPiece = env->NewStringUTF(piece.c_str());
        jboolean shouldContinue = env->CallBooleanMethod(jCallback, onTokenMethod, jPiece);
        env->DeleteLocalRef(jPiece);

        generated++;

        if (!shouldContinue) {
            break;
        }

        // Feed the new token back in for the next step.
        batch.n_tokens = 1;
        batch.token[0] = newToken;
        batch.pos[0] = nCur;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;

        nCur++;

        if (llama_decode(handle->ctx, batch) != 0) {
            LOGE("Decode step failed at position %d", nCur);
            break;
        }
    }

    llama_sampler_free(sampler);
    llama_batch_free(batch);

    return generated;
}

JNIEXPORT void JNICALL
Java_com_ailocal_app_llm_LlamaBridge_nativeCancelGeneration(JNIEnv* /*env*/, jobject /*thiz*/, jlong jHandle) {
    if (jHandle == 0) return;
    auto* handle = reinterpret_cast<EngineHandle*>(jHandle);
    handle->cancelRequested.store(true);
}

JNIEXPORT jlong JNICALL
Java_com_ailocal_app_llm_LlamaBridge_nativeEstimateMemoryUsageBytes(JNIEnv* /*env*/, jobject /*thiz*/, jlong jHandle) {
    if (jHandle == 0) return 0;
    auto* handle = reinterpret_cast<EngineHandle*>(jHandle);
    // llama.cpp does not expose a single authoritative "current usage" call
    // across versions, so we report the state size (KV cache + bookkeeping)
    // plus the model's own mapped size as a reasonable estimate rather than
    // inventing a number.
    size_t stateSize = llama_state_get_size(handle->ctx);
    size_t modelSize = llama_model_size(handle->model);
    return static_cast<jlong>(stateSize + modelSize);
}

} // extern "C"
