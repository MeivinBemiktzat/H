package com.ailocal.app.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class ModelInfo(
    val architecture: String,
    val paramCountLabel: String,
    val contextLength: Int,
    val vocabSize: Int,
    val quantization: String
)

data class GenerationStats(
    val tokensPerSecond: Double,
    val loadTimeMs: Long,
    val generationTimeMs: Long,
    val estimatedMemoryBytes: Long
)

sealed class EngineState {
    object Idle : EngineState()
    object Loading : EngineState()
    data class Loaded(val info: ModelInfo, val loadTimeMs: Long) : EngineState()
    data class Error(val message: String, val outOfMemory: Boolean = false) : EngineState()
}

/**
 * Owns the lifetime of a single loaded GGUF model. Only one model is ever
 * resident in memory at a time (per the performance requirements) - callers
 * must [unload] before [load]ing a different file.
 */
class LlamaEngine {

    private var handle: Long = 0L
    var state: EngineState = EngineState.Idle
        private set

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(
        modelPath: String,
        contextSize: Int,
        threads: Int,
        batchSize: Int,
        seed: Int
    ): EngineState = withContext(Dispatchers.Default) {
        val file = File(modelPath)
        if (!file.exists() || !file.canRead()) {
            val err = EngineState.Error("Model file not found or not readable")
            state = err
            return@withContext err
        }

        state = EngineState.Loading
        val start = System.currentTimeMillis()

        val newHandle = try {
            LlamaBridge.nativeLoadModel(modelPath, contextSize, threads, batchSize, seed)
        } catch (oom: OutOfMemoryError) {
            val err = EngineState.Error("Out of memory while loading model", outOfMemory = true)
            state = err
            return@withContext err
        } catch (e: Exception) {
            val err = EngineState.Error(e.message ?: "Unknown load error")
            state = err
            return@withContext err
        }

        if (newHandle == 0L) {
            val err = EngineState.Error("Engine could not load this GGUF file - it may be unsupported or corrupted")
            state = err
            return@withContext err
        }

        handle = newHandle
        val loadTime = System.currentTimeMillis() - start

        val info = try {
            parseModelInfo(LlamaBridge.nativeGetModelInfo(newHandle))
        } catch (e: Exception) {
            ModelInfo("unknown", "unknown", contextSize, 0, "unknown")
        }

        val loaded = EngineState.Loaded(info, loadTime)
        state = loaded
        loaded
    }

    fun unload() {
        if (handle != 0L) {
            LlamaBridge.nativeUnloadModel(handle)
            handle = 0L
        }
        state = EngineState.Idle
    }

    fun isLoaded(): Boolean = handle != 0L

    fun estimatedMemoryUsageBytes(): Long =
        if (handle != 0L) LlamaBridge.nativeEstimateMemoryUsageBytes(handle) else 0L

    /**
     * Streams generated token pieces as a Flow. Collecting coroutine
     * cancellation propagates down to [LlamaBridge.nativeCancelGeneration].
     */
    fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int
    ): Flow<String> = callbackFlow {
        if (handle == 0L) {
            close(IllegalStateException("No model loaded"))
            return@callbackFlow
        }

        val callback = LlamaBridge.TokenCallback { piece ->
            val sendResult = trySend(piece)
            sendResult.isSuccess
        }

        val job = CoroutineScope(Dispatchers.Default).launch {
            try {
                LlamaBridge.nativeGenerate(handle, prompt, maxTokens, temperature, topP, topK, callback)
            } catch (e: CancellationException) {
                LlamaBridge.nativeCancelGeneration(handle)
            } catch (e: Exception) {
                close(e)
            } finally {
                close()
            }
        }

        awaitClose {
            job.cancel()
            if (handle != 0L) LlamaBridge.nativeCancelGeneration(handle)
        }
    }

    private fun parseModelInfo(jsonString: String): ModelInfo {
        val obj = json.parseToJsonElement(jsonString).jsonObject
        return ModelInfo(
            architecture = obj["architecture"]?.jsonPrimitive?.content ?: "unknown",
            paramCountLabel = obj["param_count_label"]?.jsonPrimitive?.content ?: "unknown",
            contextLength = obj["context_length"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            vocabSize = obj["vocab_size"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            quantization = obj["quantization"]?.jsonPrimitive?.content ?: "unknown"
        )
    }
}

private fun kotlinx.coroutines.CoroutineScope.launch(
    block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit
) = kotlinx.coroutines.launch(block = block)
