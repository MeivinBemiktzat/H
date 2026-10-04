package com.ailocal.app.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Uses Android's built-in SpeechRecognizer with EXTRA_PREFER_OFFLINE set,
 * so recognition runs on-device when the OS has an offline language pack
 * installed (Settings > System > Languages > On-device speech recognition
 * on most OEM builds). No audio is sent anywhere by this app itself - it
 * only invokes the system recognizer, which performs the recognition
 * according to the device's own on-device/offline configuration.
 *
 * If the device truly has no offline model for Hebrew, recognition will
 * fail at the OS level; [SttResult.Error] surfaces that clearly rather
 * than silently falling back to a network path, since this app adds none.
 */
class AndroidSttEngine(private val context: Context) : SttEngine {

    override val name: String = "android_on_device"

    private var recognizer: SpeechRecognizer? = null

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun startListening(onResult: (SttResult) -> Unit) {
        if (!isAvailable()) {
            onResult(SttResult.Error("Speech recognition is not available on this device"))
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission not granted"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_SERVER ->
                            "Online recognition unavailable; install an offline language pack in system settings for fully offline use"
                        else -> "Speech recognition error ($error)"
                    }
                    onResult(SttResult.Error(message))
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull().orEmpty()
                    onResult(SttResult.Final(text))
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (!text.isNullOrBlank()) onResult(SttResult.Partial(text))
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("he", "IL").toString())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        recognizer?.startListening(intent)
    }

    override fun stopListening() {
        recognizer?.stopListening()
    }

    override fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
