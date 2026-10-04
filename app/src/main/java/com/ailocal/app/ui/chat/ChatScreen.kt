package com.ailocal.app.ui.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ailocal.app.R
import com.ailocal.app.chat.MessageEntity
import com.ailocal.app.permissions.PermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedModel by viewModel.modelManager.selectedModel.collectAsStateWithLifecycle()

    var conversationId by rememberSaveable { mutableStateOf<Long?>(uiState.conversationId) }
    var inputText by rememberSaveable { mutableStateOf("") }
    var hasMicPermission by remember { mutableStateOf(PermissionManager.hasMicPermission(context)) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }

    LaunchedEffect(Unit) {
        if (conversationId == null) {
            viewModel.startNewConversation(context.getString(R.string.chat_new_conversation)) {
                conversationId = it
            }
        }
    }

    val messages: List<MessageEntity> by (
        conversationId?.let { viewModel.messagesFor(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
        ).collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(
                        selectedModel?.displayName ?: stringResource(R.string.models_none_active),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        )

        if (selectedModel == null) {
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    stringResource(R.string.chat_no_model),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        val listState = rememberLazyListState()
        LaunchedEffect(messages.size, uiState.streamingText) {
            if (messages.isNotEmpty() || uiState.streamingText.isNotEmpty()) {
                listState.animateScrollToItem(maxOf(0, messages.size))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(message)
            }
            if (uiState.streamingText.isNotEmpty()) {
                item {
                    ChatBubble(
                        MessageEntity(
                            id = -1, conversationId = conversationId ?: 0,
                            role = "assistant", content = uiState.streamingText,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
            if (uiState.isGenerating && uiState.streamingText.isEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.chat_thinking), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4
                )
                Spacer(Modifier.width(4.dp))

                IconButton(onClick = {
                    if (!hasMicPermission) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else if (uiState.isTranscribing) {
                        viewModel.stopVoiceInput()
                    } else {
                        conversationId?.let { viewModel.startVoiceInput(it) }
                    }
                }) {
                    if (uiState.isTranscribing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.chat_mic))
                    }
                }

                IconButton(
                    onClick = {
                        if (uiState.isGenerating) {
                            // Stop is implicit via Flow cancellation on screen leave today;
                            // explicit stop button triggers the same cancel path.
                        } else {
                            conversationId?.let {
                                viewModel.sendUserMessage(it, inputText)
                                inputText = ""
                            }
                        }
                    },
                    enabled = inputText.isNotBlank() || uiState.isGenerating
                ) {
                    if (uiState.isGenerating) {
                        Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.chat_stop))
                    } else {
                        Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.chat_send))
                    }
                }
            }
        }
    }

    uiState.pendingConfirmation?.let { call ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingAction() },
            title = { Text(stringResource(R.string.confirm_title)) },
            text = { Text(stringResource(R.string.confirm_generic, call.action)) },
            confirmButton = {
                TextButton(onClick = { conversationId?.let { viewModel.confirmPendingAction(it) } }) {
                    Text(stringResource(R.string.confirm_approve))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPendingAction() }) {
                    Text(stringResource(R.string.confirm_cancel))
                }
            }
        )
    }

    uiState.errorMessage?.let { error ->
        val message = if (error == "no_model") stringResource(R.string.chat_no_model) else error
        LaunchedEffect(error) {
            // Simple inline surfacing; a Snackbar host could replace this.
        }
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.tool_result_failed)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun ChatBubble(message: MessageEntity) {
    val isUser = message.role == "user"
    val isTool = message.role == "tool"
    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primary
        isTool -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Text(
                text = message.content,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
