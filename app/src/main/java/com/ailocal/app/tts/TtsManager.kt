package com.ailocal.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import java.util.UUID

sealed class TtsState {
    object NotReady : TtsState()
    object Ready : TtsState()
    object Speaking : TtsState()
    data class Unavailable(val reason: String) : TtsState()
}

/**
 * Wraps Android's built-in, fully offline TextToSpeech engine. No cloud
 * TTS service is used anywhere. If the device has no Hebrew voice
 * installed, [hasHebrewVoice] reports that clearly so the UI can show the
 * message the spec requires instead of silently falling back.
 */
class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    var state: TtsState = TtsState.NotReady
        private set

    fun initialize(onReady: (TtsState) -> Unit) {
        tts = TextToSpeech(context) { status ->
            state = if (status == TextToSpeech.SUCCESS) {
                TtsState.Ready
            } else {
                TtsState.Unavailable("Text-to-Speech engine failed to initialize")
            }
            onReady(state)
        }
    }

    fun hasHebrewVoice(): Boolean {
        val engine = tts ?: return false
        val hebrew = Locale("he", "IL")
        return try {
            engine.voices?.any { voice ->
                voice.locale.language == "he" || voice.locale.language == hebrew.language
            } ?: (engine.isLanguageAvailable(hebrew) >= TextToSpeech.LANG_AVAILABLE)
        } catch (e: Exception) {
            false
        }
    }

    fun availableVoices(): List<Voice> = tts?.voices?.toList() ?: emptyList()

    fun setVoice(voice: Voice) {
        tts?.voice = voice
    }

    fun setRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    /** Speaks [text] and emits completion as a single Flow event, for easy coroutine use. */
    fun speak(text: String): Flow<Unit> = callbackFlow {
        val engine = tts
        if (engine == null) {
            close(IllegalStateException("TTS not initialized"))
            return@callbackFlow
        }

        val utteranceId = UUID.randomUUID().toString()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { state = TtsState.Speaking }
            override fun onDone(utteranceId: String?) {
                state = TtsState.Ready
                trySend(Unit)
                close()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                state = TtsState.Ready
                close(RuntimeException("TTS playback error"))
            }
        })

        // Prefer Hebrew locale if a Hebrew voice is installed; otherwise
        // fall back to the device default rather than failing outright.
        if (hasHebrewVoice()) {
            engine.language = Locale("he", "IL")
        }

        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            close(RuntimeException("Could not start speech"))
        }

        awaitClose { }
    }

    fun stop() {
        tts?.stop()
        state = TtsState.Ready
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        state = TtsState.NotReady
    }
}
