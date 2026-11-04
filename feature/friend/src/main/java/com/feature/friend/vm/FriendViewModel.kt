package com.feature.friend.vm

import androidx.lifecycle.viewModelScope
import com.core.ai.entity.AIDeepseek
import com.core.ai.entity.IAI
import com.core.ai.factory.AIFactory
import com.core.ai.model.ChatMessage
import com.core.common.BaseViewModel
import com.feature.friend.contract.DEFAULT_MODEL
import com.feature.friend.contract.FriendScreen
import com.feature.friend.contract.FriendUiState
import com.feature.friend.model.Friend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class FriendViewModel : BaseViewModel() {

    /** 当前只开放 DeepSeek，其它厂商实现还没迁移到多轮对话协议。 */
    private val ai: IAI = AIFactory().createAI("Deepseek") ?: AIDeepseek()

    private val _uiState = MutableStateFlow(FriendUiState())
    val uiState: StateFlow<FriendUiState> = _uiState.asStateFlow()

    init {
        loadModels()
    }

    fun loadModels() = viewModelScope.launch {
        _uiState.update { it.copy(isLoadingModels = true) }
        ai.requestModel()
        val available = ai.modelSet.toList().ifEmpty { FALLBACK_MODELS }
        _uiState.update { state ->
            state.copy(
                isLoadingModels = false,
                models = available,
                selectedModel = state.selectedModel.takeIf { it in available } ?: available.first()
            )
        }
    }

    fun selectModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun updateSearch(text: String) {
        _uiState.update { it.copy(search = text) }
    }

    fun openCreate() {
        _uiState.update { it.copy(screen = FriendScreen.Create, error = null) }
    }

    fun openChat(friendId: String) {
        _uiState.update {
            it.copy(screen = FriendScreen.Chat(friendId), error = null, input = "")
        }
    }

    fun back() {
        _uiState.update { it.copy(screen = FriendScreen.List, error = null, input = "") }
    }

    fun createFriend(name: String, role: String, prompt: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return

        val friend = Friend(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            role = role.trim(),
            prompt = prompt.trim(),
            brand = ai.brandName,
            model = _uiState.value.selectedModel.ifBlank { DEFAULT_MODEL }
        )
        _uiState.update {
            it.copy(
                friends = it.friends + friend,
                screen = FriendScreen.Chat(friend.id),
                error = null,
                input = ""
            )
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    fun send() = viewModelScope.launch {
        val state = _uiState.value
        val friend = state.activeFriend ?: return@launch
        val text = state.input.trim()
        if (text.isEmpty() || state.isSending) return@launch

        val history = friend.messages + ChatMessage.user(text)
        updateMessages(friend.id, history)
        _uiState.update { it.copy(input = "", isSending = true, error = null) }
        requestReply(friend.id, friend, history)
    }

    fun retry() = viewModelScope.launch {
        val state = _uiState.value
        val friend = state.activeFriend ?: return@launch
        if (state.isSending || friend.messages.isEmpty()) return@launch

        _uiState.update { it.copy(isSending = true, error = null) }
        requestReply(friend.id, friend, friend.messages)
    }

    private suspend fun requestReply(friendId: String, friend: Friend, history: List<ChatMessage>) {
        try {
            val reply = ai.chat(
                messages = history,
                model = friend.model,
                systemPrompt = friend.systemPrompt
            )
            appendMessage(friendId, ChatMessage.assistant(reply))
        } catch (t: Throwable) {
            _uiState.update { it.copy(error = t.readableMessage()) }
        } finally {
            _uiState.update { it.copy(isSending = false) }
        }
    }

    private fun updateMessages(friendId: String, messages: List<ChatMessage>) {
        _uiState.update { state ->
            state.copy(friends = state.friends.map { friend ->
                if (friend.id == friendId) friend.copy(messages = messages) else friend
            })
        }
    }

    private fun appendMessage(friendId: String, message: ChatMessage) {
        _uiState.update { state ->
            state.copy(friends = state.friends.map { friend ->
                if (friend.id == friendId) {
                    friend.copy(messages = friend.messages + message)
                } else {
                    friend
                }
            })
        }
    }

    private fun Throwable.readableMessage(): String {
        var message = ""
        handleError { message = it }
        return message.ifBlank { "发生未知错误，请重试" }
    }

    private companion object {
        val FALLBACK_MODELS = listOf(DEFAULT_MODEL, "deepseek-v4-pro")
    }
}
