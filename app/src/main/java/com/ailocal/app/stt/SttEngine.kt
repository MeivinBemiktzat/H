package com.ailocal.app.stt

sealed class SttResult {
    data class Partial(val text: String) : SttResult()
    data class Final(val text: String) : SttResult()
    data class Error(val message: String) : SttResult()
}

/**
 * Common interface for on-device speech-to-text engines, so the engine
 * backing the microphone button can be swapped (today: Android's built-in
 * on-device recognizer; a sherpa-onnx or whisper.cpp backend can be added
 * later behind this same interface, matching the "choose between STT
 * engines" requirement) without touching the UI layer.
 */
interface SttEngine {
    val name: String
    fun isAvailable(): Boolean
    fun startListening(onResult: (SttResult) -> Unit)
    fun stopListening()
    fun destroy()
}
