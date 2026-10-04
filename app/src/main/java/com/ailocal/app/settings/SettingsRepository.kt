package com.ailocal.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ailocal_settings")

data class LlmSettings(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxTokens: Int = 256,
    val contextSize: Int = 1024,   // conservative default for weak devices
    val threads: Int = 4,
    val batchSize: Int = 128,
    val seed: Int = -1,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
)

data class TtsSettings(
    val enabled: Boolean = true,
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voiceName: String? = null
)

data class SttSettings(
    val engine: String = "sherpa-onnx"
)

const val DEFAULT_SYSTEM_PROMPT = "אתה עוזר AI מקומי במכשיר Android. כאשר המשתמש מבקש פעולה במכשיר, השתמש ב-Tool המתאים. אל תטען שביצעת פעולה אם האפליקציה לא אישרה שהפעולה בוצעה."

/**
 * Low Context Size / thread / batch defaults here are intentionally modest
 * so the app behaves reasonably on the weaker test device described in the
 * product spec. Users can raise them in the advanced settings screen.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_P = floatPreferencesKey("top_p")
        val TOP_K = intPreferencesKey("top_k")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val CONTEXT_SIZE = intPreferencesKey("context_size")
        val THREADS = intPreferencesKey("threads")
        val BATCH_SIZE = intPreferencesKey("batch_size")
        val SEED = intPreferencesKey("seed")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")

        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_VOICE = stringPreferencesKey("tts_voice")

        val STT_ENGINE = stringPreferencesKey("stt_engine")
    }

    val llmSettings: Flow<LlmSettings> = context.dataStore.data.map { prefs ->
        LlmSettings(
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7f,
            topP = prefs[Keys.TOP_P] ?: 0.9f,
            topK = prefs[Keys.TOP_K] ?: 40,
            maxTokens = prefs[Keys.MAX_TOKENS] ?: 256,
            contextSize = prefs[Keys.CONTEXT_SIZE] ?: 1024,
            threads = prefs[Keys.THREADS] ?: 4,
            batchSize = prefs[Keys.BATCH_SIZE] ?: 128,
            seed = prefs[Keys.SEED] ?: -1,
            systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT
        )
    }

    val ttsSettings: Flow<TtsSettings> = context.dataStore.data.map { prefs ->
        TtsSettings(
            enabled = prefs[Keys.TTS_ENABLED] ?: true,
            rate = prefs[Keys.TTS_RATE] ?: 1.0f,
            pitch = prefs[Keys.TTS_PITCH] ?: 1.0f,
            voiceName = prefs[Keys.TTS_VOICE]
        )
    }

    val sttSettings: Flow<SttSettings> = context.dataStore.data.map { prefs ->
        SttSettings(engine = prefs[Keys.STT_ENGINE] ?: "sherpa-onnx")
    }

    suspend fun updateLlmSettings(update: (LlmSettings) -> LlmSettings) {
        context.dataStore.edit { prefs ->
            val current = LlmSettings(
                temperature = prefs[Keys.TEMPERATURE] ?: 0.7f,
                topP = prefs[Keys.TOP_P] ?: 0.9f,
                topK = prefs[Keys.TOP_K] ?: 40,
                maxTokens = prefs[Keys.MAX_TOKENS] ?: 256,
                contextSize = prefs[Keys.CONTEXT_SIZE] ?: 1024,
                threads = prefs[Keys.THREADS] ?: 4,
                batchSize = prefs[Keys.BATCH_SIZE] ?: 128,
                seed = prefs[Keys.SEED] ?: -1,
                systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT
            )
            val next = update(current)
            prefs[Keys.TEMPERATURE] = next.temperature
            prefs[Keys.TOP_P] = next.topP
            prefs[Keys.TOP_K] = next.topK
            prefs[Keys.MAX_TOKENS] = next.maxTokens
            prefs[Keys.CONTEXT_SIZE] = next.contextSize
            prefs[Keys.THREADS] = next.threads
            prefs[Keys.BATCH_SIZE] = next.batchSize
            prefs[Keys.SEED] = next.seed
            prefs[Keys.SYSTEM_PROMPT] = next.systemPrompt
        }
    }

    suspend fun updateTtsSettings(update: (TtsSettings) -> TtsSettings) {
        context.dataStore.edit { prefs ->
            val current = TtsSettings(
                enabled = prefs[Keys.TTS_ENABLED] ?: true,
                rate = prefs[Keys.TTS_RATE] ?: 1.0f,
                pitch = prefs[Keys.TTS_PITCH] ?: 1.0f,
                voiceName = prefs[Keys.TTS_VOICE]
            )
            val next = update(current)
            prefs[Keys.TTS_ENABLED] = next.enabled
            prefs[Keys.TTS_RATE] = next.rate
            prefs[Keys.TTS_PITCH] = next.pitch
            next.voiceName?.let { prefs[Keys.TTS_VOICE] = it }
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
    }
}
