package com.feature.agent.vm

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.core.ai.entity.IAI
import com.core.ai.factory.AIFactory
import com.core.ai.model.ChatMessage
import com.core.common.BaseViewModel
import com.core.repository.storage.AvatarStore
import com.core.repository.storage.FriendRepository
import com.core.repository.storage.LegacyMigration
import com.core.repository.storage.ProfileRepository
import com.core.repository.storage.StoredMessage
import com.feature.agent.contract.AgentUiState
import com.feature.agent.data.toFriend
import com.feature.agent.data.toStored
import com.feature.agent.data.trimToBudget
import com.feature.agent.model.Friend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class AgentViewModel(application: Application) : BaseViewModel(application) {

    private val friendRepository = FriendRepository(application)
    private val profileRepository = ProfileRepository(application)
    private val avatarStore = AvatarStore(application)

    private val ais = mutableMapOf<String, IAI>()
    private val modelCache = mutableMapOf<String, List<String>>()

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { LegacyMigration.migrateIfNeeded(application) }
            loadFriends()
            loadModelsFor(_uiState.value.selectedBrand)
        }
    }

    fun refresh() {
        viewModelScope.launch { loadFriends() }
    }

    private suspend fun loadFriends() {
        val storedFriends = runCatching { friendRepository.load() }.getOrDefault(emptyList())
        val avatar = runCatching { profileRepository.loadAvatarPath() }.getOrNull()
        _uiState.update { state ->
            state.copy(
                friends = storedFriends.map { it.toFriend() },
                userAvatarPath = avatar ?: state.userAvatarPath,
                loaded = true
            )
        }
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

    fun openChat(friendId: String) {
        _uiState.update { it.copy(activeFriendId = friendId, input = "", error = null) }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    fun createFriend(
        name: String,
        role: String,
        prompt: String,
        avatarBytes: ByteArray? = null,
        thinkingEnabled: Boolean = false
    ): Friend? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return null

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
            thinkingEnabled = thinkingEnabled
        )
        _uiState.update {
            it.copy(
                friends = it.friends + friend,
                activeFriendId = friend.id,
                error = null,
                input = ""
            )
        }
        viewModelScope.launch {
            runCatching { friendRepository.upsert(friend.toStored()) }
        }
        if (avatarBytes != null) {
            viewModelScope.launch {
                val path = avatarStore.save(avatarBytes, friend.id) ?: return@launch
                updateFriendAvatar(friend.id, path)
            }
        }
        return friend
    }

    fun saveFriendEdits(
        friendId: String,
        name: String,
        role: String,
        prompt: String,
        avatarBytes: ByteArray?,
        thinkingEnabled: Boolean
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return

        _uiState.update { state ->
            state.copy(
                friends = state.friends.map { friend ->
                    if (friend.id == friendId) {
                        friend.copy(
                            name = trimmedName,
                            role = role.trim(),
                            prompt = prompt.trim(),
                            thinkingEnabled = thinkingEnabled
                        )
                    } else {
                        friend
                    }
                },
                error = null
            )
        }

        val updated = _uiState.value.friends.firstOrNull { it.id == friendId } ?: return
        viewModelScope.launch {
            runCatching { friendRepository.upsert(updated.toStored()) }
        }
        if (avatarBytes != null) {
            viewModelScope.launch {
                val path = avatarStore.save(avatarBytes, friendId) ?: return@launch
                updateFriendAvatar(friendId, path)
            }
        }
    }

    fun setFriendAvatar(friendId: String, bytes: ByteArray) {
        viewModelScope.launch {
            val path = avatarStore.save(bytes, friendId) ?: return@launch
            updateFriendAvatar(friendId, path)
        }
    }

    fun setUserAvatar(bytes: ByteArray) {
        viewModelScope.launch {
            val path = avatarStore.save(bytes, USER_AVATAR_ID) ?: return@launch
            runCatching { profileRepository.saveAvatarPath(path) }
            _uiState.update { it.copy(userAvatarPath = path) }
        }
    }

    private fun updateFriendAvatar(friendId: String, path: String) {
        _uiState.update { state ->
            state.copy(
                friends = state.friends.map { friend ->
                    if (friend.id == friendId) friend.copy(avatarPath = path) else friend
                }
            )
        }
        viewModelScope.launch {
            runCatching { friendRepository.updateAvatar(friendId, path) }
        }
    }

    fun send() = viewModelScope.launch {
        val state = _uiState.value
        val friend = state.activeFriend ?: return@launch
        val text = state.input.trim()
        if (text.isEmpty() || state.isSending) return@launch

        val history = friend.messages + ChatMessage.user(text)
        appendMessage(friend.id, ChatMessage.user(text))
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
                messages = history.trimToBudget(),
                model = friend.model,
                systemPrompt = friend.systemPrompt,
                enableThinking = friend.thinkingEnabled
            )
            if (_uiState.value.friends.any { it.id == friendId }) {
                appendMessage(friendId, ChatMessage.assistant(reply))
            }
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

    private fun appendMessage(friendId: String, message: ChatMessage) {
        _uiState.update { state ->
            state.copy(
                friends = state.friends.map { friend ->
                    if (friend.id == friendId) {
                        friend.copy(messages = friend.messages + message)
                    } else {
                        friend
                    }
                }
            )
        }
        viewModelScope.launch {
            runCatching {
                friendRepository.appendMessage(
                    friendId = friendId,
                    message = StoredMessage(role = message.role, content = message.content)
                )
            }
        }
    }

    private fun Throwable.readableMessage(): String {
        var message = ""
        handleError { message = it }
        return message.ifBlank { "发生未知错误，请重试" }
    }

    private companion object {
        const val USER_AVATAR_ID = "user"
    }
}
