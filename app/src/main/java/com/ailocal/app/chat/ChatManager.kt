package com.ailocal.app.chat

import android.content.Context
import android.net.Uri
import com.ailocal.app.AiLocalApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long,
    val toolActionId: String? = null,
    val toolResultSummary: String? = null
)

@Serializable
data class ExportedConversation(
    val title: String,
    val createdAt: Long,
    val messages: List<ChatMessage>
)

class ChatManager(private val context: Context) {

    private val db = (context.applicationContext as AiLocalApplication).database
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun observeConversations(): Flow<List<ConversationEntity>> = conversationDao.observeAll()

    fun searchConversations(query: String): Flow<List<ConversationEntity>> = conversationDao.search(query)

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> =
        messageDao.observeForConversation(conversationId)

    suspend fun createConversation(initialTitle: String): Long {
        val now = System.currentTimeMillis()
        return conversationDao.insert(ConversationEntity(title = initialTitle, createdAt = now, updatedAt = now))
    }

    suspend fun renameConversation(conversation: ConversationEntity, newTitle: String) {
        conversationDao.update(conversation.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(conversation: ConversationEntity) {
        messageDao.deleteForConversation(conversation.id)
        conversationDao.delete(conversation)
    }

    suspend fun deleteAllHistory() {
        messageDao.deleteAll()
        conversationDao.deleteAll()
    }

    suspend fun appendMessage(
        conversationId: Long,
        role: String,
        content: String,
        toolActionId: String? = null,
        toolResultSummary: String? = null
    ) {
        val now = System.currentTimeMillis()
        messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                timestamp = now,
                toolActionId = toolActionId,
                toolResultSummary = toolResultSummary
            )
        )
        conversationDao.getById(conversationId)?.let {
            conversationDao.update(it.copy(updatedAt = now))
        }
    }

    suspend fun exportConversation(conversation: ConversationEntity, messages: List<MessageEntity>): String {
        val exported = ExportedConversation(
            title = conversation.title,
            createdAt = conversation.createdAt,
            messages = messages.map {
                ChatMessage(it.role, it.content, it.timestamp, it.toolActionId, it.toolResultSummary)
            }
        )
        return json.encodeToString(exported)
    }

    suspend fun importConversation(jsonString: String): Long {
        val exported = json.decodeFromString<ExportedConversation>(jsonString)
        val id = createConversation(exported.title)
        exported.messages.forEach {
            appendMessage(id, it.role, it.content, it.toolActionId, it.toolResultSummary)
        }
        return id
    }

    fun readTextFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        }
    }
}
