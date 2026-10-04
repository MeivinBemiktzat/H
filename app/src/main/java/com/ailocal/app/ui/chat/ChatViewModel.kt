package com.ailocal.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ailocal.app.chat.ChatManager
import com.ailocal.app.chat.MessageEntity
import com.ailocal.app.gguf.ModelManager
import com.ailocal.app.llm.EngineState
import com.ailocal.app.settings.LlmSettings
import com.ailocal.app.settings.SettingsRepository
import com.ailocal.app.stt.AndroidSttEngine
import com.ailocal.app.stt.SttResult
import com.ailocal.app.tools.ToolCall
import com.ailocal.app.tools.ToolCallParser
import com.ailocal.app.tools.ToolExecutor
import com.ailocal.app.tools.ToolResult
import com.ailocal.app.tts.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversationId: Long? = null,
    val isGenerating: Boolean = false,
    val isTranscribing: Boolean = false,
    val pendingConfirmation: ToolCall? = null,
    val pendingConfirmationLabel: String = "",
    val errorMessage: String? = null,
    val streamingText: String = ""
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val chatManager = ChatManager(context)
    val modelManager = ModelManager(context)
    private val settingsRepository = SettingsRepository(context)
    private val toolExecutor = ToolExecutor(context)
    private val sttEngine = AndroidSttEngine(context)
    private val ttsManager = TtsManager(context)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    val engineState: StateFlow<EngineState> = modelManager.engineState

    init {
        ttsManager.initialize {}
    }

    fun messagesFor(conversationId: Long) = chatManager.observeMessages(conversationId)

    fun startNewConversation(title: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = chatManager.createConversation(title)
            _uiState.value = _uiState.value.copy(conversationId = id)
            onCreated(id)
        }
    }

    fun selectConversation(id: Long) {
        _uiState.value = _uiState.value.copy(conversationId = id)
    }

    fun sendUserMessage(conversationId: Long, text: String) {
        if (text.isBlank()) return
        if (!modelManager.isModelLoaded()) {
            _uiState.value = _uiState.value.copy(errorMessage = "no_model")
            return
        }

        viewModelScope.launch {
            chatManager.appendMessage(conversationId, "user", text)
            generateReply(conversationId)
        }
    }

    private suspend fun generateReply(conversationId: Long) {
        _uiState.value = _uiState.value.copy(isGenerating = true, streamingText = "")

        val llmSettings = settingsRepository.llmSettings.first()
        val history = chatManager.observeMessages(conversationId).first()
        val prompt = buildPrompt(history, llmSettings)

        val builder = StringBuilder()
        try {
            modelManager.engine.generate(
                prompt = prompt,
                maxTokens = llmSettings.maxTokens,
                temperature = llmSettings.temperature,
                topP = llmSettings.topP,
                topK = llmSettings.topK
            ).collect { piece ->
                builder.append(piece)
                _uiState.value = _uiState.value.copy(streamingText = builder.toString())
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isGenerating = false, errorMessage = e.message)
            return
        }

        val fullText = builder.toString()
        val toolCall = ToolCallParser.extractToolCall(fullText)
        val displayText = if (toolCall != null) ToolCallParser.stripToolCallJson(fullText) else fullText

        if (displayText.isNotBlank()) {
            chatManager.appendMessage(conversationId, "assistant", displayText)
        }

        _uiState.value = _uiState.value.copy(isGenerating = false, streamingText = "")

        if (toolCall != null) {
            handleToolCall(conversationId, toolCall)
        } else if (displayText.isNotBlank()) {
            maybeSpeak(displayText)
        }
    }

    private suspend fun handleToolCall(conversationId: Long, call: ToolCall) {
        if (toolExecutor.requiresConfirmation(call)) {
            _uiState.value = _uiState.value.copy(
                pendingConfirmation = call,
                pendingConfirmationLabel = call.action
            )
        } else {
            runToolCall(conversationId, call)
        }
    }

    fun confirmPendingAction(conversationId: Long) {
        val call = _uiState.value.pendingConfirmation ?: return
        _uiState.value = _uiState.value.copy(pendingConfirmation = null, pendingConfirmationLabel = "")
        viewModelScope.launch { runToolCall(conversationId, call) }
    }

    fun cancelPendingAction() {
        _uiState.value = _uiState.value.copy(pendingConfirmation = null, pendingConfirmationLabel = "")
    }

    private suspend fun runToolCall(conversationId: Long, call: ToolCall) {
        val result = toolExecutor.execute(call)
        val summary = when (result) {
            is ToolResult.Success -> result.message
            is ToolResult.Failure -> "Failed: ${result.message}"
            is ToolResult.NotSupported -> result.reason
            is ToolResult.NeedsConfirmation -> "Needs confirmation"
        }
        chatManager.appendMessage(
            conversationId, "tool", summary,
            toolActionId = call.action, toolResultSummary = summary
        )
        maybeSpeak(summary)
    }

    private suspend fun maybeSpeak(text: String) {
        val ttsSettings = settingsRepository.ttsSettings.first()
        if (ttsSettings.enabled && ttsManager.hasHebrewVoice()) {
            try {
                ttsManager.speak(text).first()
            } catch (_: Exception) { /* non-fatal */ }
        }
    }

    fun startVoiceInput(conversationId: Long) {
        _uiState.value = _uiState.value.copy(isTranscribing = true)
        sttEngine.startListening { result ->
            when (result) {
                is SttResult.Final -> {
                    _uiState.value = _uiState.value.copy(isTranscribing = false)
                    if (result.text.isNotBlank()) sendUserMessage(conversationId, result.text)
                }
                is SttResult.Error -> {
                    _uiState.value = _uiState.value.copy(isTranscribing = false, errorMessage = result.message)
                }
                is SttResult.Partial -> { /* could surface live partials in UI if desired */ }
            }
        }
    }

    fun stopVoiceInput() {
        sttEngine.stopListening()
        _uiState.value = _uiState.value.copy(isTranscribing = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Builds the prompt using ChatML, the format Qwen2.5-Instruct models
     * are trained on (<|im_start|>role ... <|im_end|>). This is the
     * default template since Qwen2.5-0.5B-Instruct is the spec'd model,
     * but it's a plain string builder, not a hardcoded dependency on any
     * one model - a user loading a differently-templated GGUF can adjust
     * the System Prompt field; the surrounding ChatML wrapper is harmless
     * for most instruct-tuned models since it just looks like extra text
     * to a model that doesn't recognize the special tokens.
     */
    private fun buildPrompt(history: List<MessageEntity>, settings: LlmSettings): String {
        val systemPrompt = ToolCallParser.buildSystemPrompt(settings.systemPrompt)
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n").append(systemPrompt).append("<|im_end|>\n")
        history.takeLast(20).forEach { msg ->
            when (msg.role) {
                "user" -> sb.append("<|im_start|>user\n").append(msg.content).append("<|im_end|>\n")
                "assistant" -> sb.append("<|im_start|>assistant\n").append(msg.content).append("<|im_end|>\n")
                "tool" -> sb.append("<|im_start|>user\n[tool result] ").append(msg.content).append("<|im_end|>\n")
            }
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        sttEngine.destroy()
        ttsManager.shutdown()
        modelManager.unloadModel()
    }
}
