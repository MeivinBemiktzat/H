package com.ailocal.app.llm

/**
 * Thin JNI boundary to the native llama.cpp-based inference engine
 * (see app/src/main/cpp). Generic by design: it loads whatever GGUF file
 * path it's given and reports back what the engine itself detects about
 * the model (architecture name, context length, vocab size) rather than
 * assuming any particular model family.
 *
 * All heavy lifting happens on a background thread from the Kotlin side
 * (see [LlamaEngine]) - these calls are blocking from native code's
 * perspective but are always invoked off the main thread.
 */
object LlamaBridge {

    init {
        System.loadLibrary("ailocal_llama")
    }

    /** Loads a GGUF model from an absolute file path. Returns a native handle, or 0 on failure. */
    external fun nativeLoadModel(
        modelPath: String,
        contextSize: Int,
        threads: Int,
        batchSize: Int,
        seed: Int
    ): Long

    /** Frees all native resources associated with a loaded model handle. */
    external fun nativeUnloadModel(handle: Long)

    /** Returns a JSON string describing what the engine detected about the model. */
    external fun nativeGetModelInfo(handle: Long): String

    /**
     * Runs generation for [prompt] and streams tokens back via [callback].
     * Returns the total number of generated tokens.
     */
    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        callback: TokenCallback
    ): Int

    external fun nativeCancelGeneration(handle: Long)

    external fun nativeEstimateMemoryUsageBytes(handle: Long): Long

    fun interface TokenCallback {
        /** Called on the native generation thread for each produced token piece. Return false to stop early. */
        fun onToken(piece: String): Boolean
    }
}
