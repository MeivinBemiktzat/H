package com.ailocal.app.ui.models

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ailocal.app.R
import com.ailocal.app.gguf.ModelManager
import com.ailocal.app.llm.EngineState
import com.ailocal.app.settings.SettingsRepository
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(modelManager: ModelManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository(context) }

    val selectedModel by modelManager.selectedModel.collectAsStateWithLifecycle()
    val engineState by modelManager.engineState.collectAsStateWithLifecycle()

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            modelManager.onModelPicked(uri)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.models_title)) })

        Column(Modifier.padding(16.dp)) {
            if (selectedModel == null) {
                Text(
                    stringResource(R.string.models_empty_state),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
            } else {
                val model = selectedModel!!
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${stringResource(R.string.models_size)}: ${humanReadableSize(model.sizeBytes)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "${stringResource(R.string.models_location)}: ${model.localPath}",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2
                        )

                        when (val state = engineState) {
                            is EngineState.Loaded -> {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${stringResource(R.string.models_family)}: ${state.info.architecture} (${state.info.paramCountLabel}, ${state.info.quantization})",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${stringResource(R.string.models_context_length)}: ${state.info.contextLength}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            is EngineState.Error -> {
                                Spacer(Modifier.height(8.dp))
                                val msg = if (state.outOfMemory) stringResource(R.string.oom_error)
                                    else stringResource(R.string.models_load_failed, state.message)
                                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                            else -> {}
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val isLoading = engineState is EngineState.Loading
                            val isLoaded = engineState is EngineState.Loaded

                            Button(
                                onClick = {
                                    scope.launch {
                                        val llmSettings = settingsRepository.llmSettings.first()
                                        modelManager.loadSelectedModel(
                                            contextSize = llmSettings.contextSize,
                                            threads = llmSettings.threads,
                                            batchSize = llmSettings.batchSize,
                                            seed = llmSettings.seed
                                        )
                                    }
                                },
                                enabled = !isLoading && !isLoaded
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.models_load))
                                }
                            }

                            OutlinedButton(
                                onClick = { modelManager.unloadModel() },
                                enabled = isLoaded
                            ) {
                                Text(stringResource(R.string.models_unload))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Button(onClick = {
                pickerLauncher.launch(arrayOf("*/*"))
            }) {
                Text(stringResource(R.string.models_pick_file))
            }
        }
    }
}

private fun humanReadableSize(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) {
        String.format(Locale.getDefault(), "%.2f GB", gb)
    } else {
        val mb = bytes / (1024.0 * 1024.0)
        String.format(Locale.getDefault(), "%.1f MB", mb)
    }
}
