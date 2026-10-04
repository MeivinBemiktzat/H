package com.ailocal.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ailocal.app.R
import com.ailocal.app.settings.LlmSettings
import com.ailocal.app.settings.SettingsRepository
import com.ailocal.app.settings.TtsSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SettingsRepository(context) }

    var llm by remember { mutableStateOf(LlmSettings()) }
    var tts by remember { mutableStateOf(TtsSettings()) }

    LaunchedEffect(Unit) {
        repository.llmSettings.collect { llm = it }
    }
    LaunchedEffect(Unit) {
        repository.ttsSettings.collect { tts = it }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.settings_title)) })

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.settings_llm_section), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LabeledSlider(
                label = "${stringResource(R.string.settings_temperature)}: ${"%.2f".format(llm.temperature)}",
                value = llm.temperature, range = 0f..2f,
                onChange = { v -> scope.launch { repository.updateLlmSettings { it.copy(temperature = v) } } }
            )
            LabeledSlider(
                label = "${stringResource(R.string.settings_top_p)}: ${"%.2f".format(llm.topP)}",
                value = llm.topP, range = 0f..1f,
                onChange = { v -> scope.launch { repository.updateLlmSettings { it.copy(topP = v) } } }
            )
            LabeledSlider(
                label = "${stringResource(R.string.settings_top_k)}: ${llm.topK}",
                value = llm.topK.toFloat(), range = 1f..100f,
                onChange = { v -> scope.launch { repository.updateLlmSettings { it.copy(topK = v.toInt()) } } }
            )
            LabeledSlider(
                label = "${stringResource(R.string.settings_max_tokens)}: ${llm.maxTokens}",
                value = llm.maxTokens.toFloat(), range = 32f..1024f,
                onChange = { v -> scope.launch { repository.updateLlmSettings { it.copy(maxTokens = v.toInt()) } } }
            )
            LabeledSlider(
                label = "${stringResource(R.string.settings_context_size)}: ${llm.contextSize}",
                value = llm.contextSize.toFloat(), range = 256f..4096f,
                onChange = { v -> scope.launch { repository.updateLlmSettings { it.copy(contextSize = v.toInt()) } } }
            )
            LabeledSlider(
                label = "${stringResource(R.string.settings_threads)}: ${llm.threads}",
                value = llm.threads.toFloat(), range = 1f..8f,
                onChange = { v -> scope.launch { repository.updateLlmSettings { it.copy(threads = v.toInt()) } } }
            )
            LabeledSlider(
                label = "${stringResource(R.string.settings_batch_size)}: ${llm.batchSize}",
                value = llm.batchSize.toFloat(), range = 32f..512f,
                onChange = { v -> scope.launch { repository.updateLlmSettings { it.copy(batchSize = v.toInt()) } } }
            )

            OutlinedTextField(
                value = if (llm.seed == -1) "" else llm.seed.toString(),
                onValueChange = { text ->
                    val parsed = text.toIntOrNull() ?: -1
                    scope.launch { repository.updateLlmSettings { it.copy(seed = parsed) } }
                },
                label = { Text(stringResource(R.string.settings_seed)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = llm.systemPrompt,
                onValueChange = { text ->
                    scope.launch { repository.updateLlmSettings { it.copy(systemPrompt = text) } }
                },
                label = { Text(stringResource(R.string.settings_system_prompt)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                minLines = 3
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Text(stringResource(R.string.settings_tts_section), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.settings_tts_enabled))
                Switch(
                    checked = tts.enabled,
                    onCheckedChange = { checked ->
                        scope.launch { repository.updateTtsSettings { it.copy(enabled = checked) } }
                    }
                )
            }
            LabeledSlider(
                label = "${stringResource(R.string.settings_tts_rate)}: ${"%.2f".format(tts.rate)}",
                value = tts.rate, range = 0.5f..2f,
                onChange = { v -> scope.launch { repository.updateTtsSettings { it.copy(rate = v) } } }
            )
            LabeledSlider(
                label = "${stringResource(R.string.settings_tts_pitch)}: ${"%.2f".format(tts.pitch)}",
                value = tts.pitch, range = 0.5f..2f,
                onChange = { v -> scope.launch { repository.updateTtsSettings { it.copy(pitch = v) } } }
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Button(onClick = { scope.launch { repository.resetToDefaults() } }) {
                Text(stringResource(R.string.settings_reset_defaults))
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
