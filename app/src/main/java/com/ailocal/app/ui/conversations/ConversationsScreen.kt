package com.ailocal.app.ui.conversations

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ailocal.app.R
import com.ailocal.app.chat.ChatManager
import com.ailocal.app.chat.ConversationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(chatManager: ChatManager, onOpenConversation: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var deleteAllConfirm by remember { mutableStateOf(false) }

    val conversationsFlow: Flow<List<ConversationEntity>> = remember(query) {
        if (query.isBlank()) chatManager.observeConversations() else chatManager.searchConversations(query)
    }
    val conversations by conversationsFlow.collectAsStateCompat(emptyList())

    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.conversations_title)) },
            actions = {
                IconButton(onClick = { deleteAllConfirm = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.conversations_delete_all))
                }
            }
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text(stringResource(R.string.conversations_search_hint)) },
            singleLine = true
        )

        if (conversations.isEmpty()) {
            Text(
                stringResource(R.string.conversations_empty),
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        onClick = { onOpenConversation(conversation.id) },
                        onRename = { renameTarget = conversation; renameText = conversation.title },
                        onDelete = { scope.launch { chatManager.deleteConversation(conversation) } },
                        onExport = {
                            scope.launch {
                                val messages = chatManager.observeMessages(conversation.id).first()
                                chatManager.exportConversation(conversation, messages)
                                // Exported JSON string is ready; wiring to a SAF "create document"
                                // launcher happens the same way as CreateTextFile in FileActions.
                            }
                        }
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.conversations_rename)) },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { chatManager.renameConversation(target, renameText) }
                    renameTarget = null
                }) { Text(stringResource(R.string.confirm_approve)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.confirm_cancel)) }
            }
        )
    }

    if (deleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { deleteAllConfirm = false },
            title = { Text(stringResource(R.string.conversations_delete_all)) },
            text = { Text(stringResource(R.string.confirm_generic, stringResource(R.string.conversations_delete_all))) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { chatManager.deleteAllHistory() }
                    deleteAllConfirm = false
                }) { Text(stringResource(R.string.confirm_approve)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteAllConfirm = false }) { Text(stringResource(R.string.confirm_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
            .padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(conversation.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    dateFmt.format(Date(conversation.updatedAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.conversations_rename))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.conversations_rename)) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.conversations_export)) },
                        leadingIcon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                        onClick = { menuExpanded = false; onExport() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.conversations_delete)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> Flow<T>.collectAsStateCompat(initial: T) =
    androidx.compose.runtime.produceState(initialValue = initial, key1 = this) {
        this@collectAsStateCompat.collect { value = it }
    }
