package com.feature.friend.vm

import androidx.lifecycle.viewModelScope
import com.core.ai.entity.IAI
import com.core.ai.factory.AIFactory
import com.core.ai.model.ChatMessage
import com.core.common.BaseViewModel
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

    /** 每家厂商一个实例，第一次用到时创建，之后复用（模型列表也缓存在实例里）。 */
    private val ais = mutableMapOf<String, IAI>()

    /** 厂商 -> 模型列表，切换厂商时避免重复请求。 */
    private val modelCache = mutableMapOf<String, List<String>>()

    private val _uiState = MutableStateFlow(FriendUiState())
    val uiState: StateFlow<FriendUiState> = _uiState.asStateFlow()

    init {
        loadModelsFor(_uiState.value.selectedBrand)
    }

    fun selectBrand(brand: String) {
        val state = _uiState.value
        if (brand == state.selectedBrand) return
        val provider = state.providers.firstOrNull { it.brand == brand } ?: return

        val cached = modelCache[brand]
        _uiState.update {
            it.copy(
                selectedBrand = brand,
                models = cached.orEmpty(),
                selectedModel = provider.defaultModel,
                isLoadingModels = cached.isNullOrEmpty(),
                error = null
            )
        }
        if (cached.isNullOrEmpty()) {
            loadModelsFor(brand)
        }
    }

    fun selectModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun setThinkingEnabled(enabled: Boolean) {
        _uiState.update { it.copy(thinkingEnabled = enabled) }
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

        val state = _uiState.value
        val provider = state.selectedProvider
        val friend = Friend(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            role = role.trim(),
            prompt = prompt.trim(),
            brand = state.selectedBrand,
            company = provider?.company.orEmpty(),
            model = state.selectedModel.ifBlank { provider?.defaultModel.orEmpty() },
            thinkingEnabled = state.thinkingEnabled
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

    private fun loadModelsFor(brand: String) = viewModelScope.launch {
        val provider = _uiState.value.providers.firstOrNull { it.brand == brand } ?: return@launch
        val ai = aiFor(brand) ?: run {
            _uiState.update { it.copy(isLoadingModels = false) }
            return@launch
        }

        _uiState.update { it.copy(isLoadingModels = true) }
        ai.requestModel()

        val available = ai.modelSet.toList().ifEmpty { provider.fallbackModels }
        modelCache[brand] = available

        _uiState.update { state ->
            if (state.selectedBrand != brand) {
                state
            } else {
                state.copy(
                    isLoadingModels = false,
                    models = available,
                    selectedModel = state.selectedModel
                        .takeIf { it in available }
                        ?: provider.defaultModel.takeIf { it in available }
                        ?: available.first()
                )
            }
        }
    }

    private suspend fun requestReply(
        friendId: String,
        friend: Friend,
        history: List<ChatMessage>
    ) {
        try {
            val ai = aiFor(friend.brand)
                ?: throw IllegalStateException("暂不支持的厂商：${friend.brand}")
            val reply = ai.chat(
                messages = history,
                model = friend.model,
                systemPrompt = friend.systemPrompt,
                enableThinking = friend.thinkingEnabled
            )
            appendMessage(friendId, ChatMessage.assistant(reply))
        } catch (t: Throwable) {
            _uiState.update { it.copy(error = t.readableMessage()) }
        } finally {
            _uiState.update { it.copy(isSending = false) }
        }
    }

    private fun aiFor(brand: String): IAI? {
        ais[brand]?.let { return it }
        val created = AIFactory().createAI(brand) ?: return null
        ais[brand] = created
        return created
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
}
