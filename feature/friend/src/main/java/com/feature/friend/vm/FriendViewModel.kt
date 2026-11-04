package com.feature.friend.vm

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
import com.core.repository.storage.RoomRepository
import com.core.repository.storage.StoredMessage
import com.feature.friend.contract.FriendScreen
import com.feature.friend.contract.FriendUiState
import com.feature.friend.data.buildRoomContext
import com.feature.friend.data.toFriend
import com.feature.friend.data.toRoom
import com.feature.friend.data.toRoomMessage
import com.feature.friend.data.toStored
import com.feature.friend.data.trimToBudget
import com.feature.friend.model.Friend
import com.feature.friend.model.Room
import com.feature.friend.model.RoomMessage
import com.feature.friend.model.RoomSender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class FriendViewModel(application: Application) : BaseViewModel(application) {

    private val friendRepository = FriendRepository(application)
    private val roomRepository = RoomRepository(application)
    private val profileRepository = ProfileRepository(application)
    private val avatarStore = AvatarStore(application)

    /** 每家厂商一个实例，第一次用到时创建，之后复用（模型列表也缓存在实例里）。 */
    private val ais = mutableMapOf<String, IAI>()

    /** 厂商 -> 模型列表，切换厂商时避免重复请求。 */
    private val modelCache = mutableMapOf<String, List<String>>()

    /** 每个「房间 + AI」一把锁：用户连发消息时，保证同一个 AI 的请求串行，上下文不乱。 */
    private val roomLocks = mutableMapOf<String, Mutex>()

    /** 消息顺序号，解决同一毫秒内多条消息的稳定排序。 */
    private val seqCounter = AtomicLong(0)

    private val _uiState = MutableStateFlow(FriendUiState())
    val uiState: StateFlow<FriendUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 旧版 DataStore 数据一次性迁移到 Room
            runCatching { LegacyMigration.migrateIfNeeded(application) }

            val storedFriends = runCatching { friendRepository.load() }.getOrDefault(emptyList())
            val storedRooms = runCatching { roomRepository.loadRooms() }.getOrDefault(emptyList())
            val storedMessages = runCatching { roomRepository.loadMessages() }.getOrDefault(emptyList())
            val userAvatar = runCatching { profileRepository.loadAvatarPath() }.getOrNull()

            _uiState.update { state ->
                state.copy(
                    userAvatarPath = userAvatar ?: state.userAvatarPath,
                    friends = if (state.friends.isEmpty()) {
                        storedFriends.map { it.toFriend() }
                    } else {
                        state.friends
                    },
                    rooms = if (state.rooms.isEmpty()) {
                        storedRooms.map { it.toRoom() }
                    } else {
                        state.rooms
                    },
                    roomMessages = if (state.roomMessages.isEmpty()) {
                        storedMessages.map { it.toRoomMessage() }
                    } else {
                        state.roomMessages
                    }
                )
            }
            loadModelsFor(_uiState.value.selectedBrand)
        }
    }

    // ---------------- 好友 ----------------

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

    fun startEditFriend(friendId: String) {
        if (_uiState.value.friends.none { it.id == friendId }) return
        _uiState.update { it.copy(screen = FriendScreen.EditFriend(friendId), error = null) }
    }

    /**
     * 保存好友资料的编辑：昵称、角色、设定、头像、思考开关。
     * 会话历史、绑定的厂商和模型保持不变。
     */
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
                screen = FriendScreen.List,
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
        _uiState.update {
            it.copy(
                screen = FriendScreen.List,
                error = null,
                input = "",
                roomInput = "",
                roomPending = emptySet(),
                roomErrors = emptyMap()
            )
        }
    }

    fun createFriend(
        name: String,
        role: String,
        prompt: String,
        avatarBytes: ByteArray? = null,
        thinkingEnabled: Boolean = false
    ) {
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
            thinkingEnabled = thinkingEnabled
        )
        _uiState.update {
            it.copy(
                friends = it.friends + friend,
                screen = FriendScreen.Chat(friend.id),
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

    /**
     * 删除智能体：
     * 1. 从好友列表移除（本地会话历史随 Friend 一起删除，之后不会再发给模型）；
     * 2. 从所有聊天室移除成员关系；
     * 3. 删除它在聊天室里发过的消息，避免残留上下文和内容；
     * 4. 删除头像文件。
     */
    fun deleteFriend(friendId: String) {
        val state = _uiState.value
        val friend = state.friends.firstOrNull { it.id == friendId } ?: return
        val affectedRooms = state.rooms.filter { friendId in it.memberIds }

        _uiState.update { current ->
            current.copy(
                friends = current.friends.filterNot { it.id == friendId },
                rooms = current.rooms.map { room ->
                    if (friendId in room.memberIds) {
                        room.copy(
                            memberIds = room.memberIds - friendId,
                            memberJoinSeq = room.memberJoinSeq - friendId
                        )
                    } else {
                        room
                    }
                },
                roomMessages = current.roomMessages.filterNot { message ->
                    (message.sender as? RoomSender.Ai)?.friendId == friendId
                },
                screen = if ((current.screen as? FriendScreen.Chat)?.friendId == friendId) {
                    FriendScreen.List
                } else {
                    current.screen
                },
                input = "",
                roomPending = current.roomPending - friendId,
                roomErrors = current.roomErrors - friendId
            )
        }

        viewModelScope.launch {
            runCatching { friendRepository.delete(friendId) }
            runCatching { roomRepository.removeMemberEverywhere(friendId) }
            runCatching { roomRepository.deleteMessagesBySender(friendId) }
        }
        viewModelScope.launch { avatarStore.delete(friendId) }

        affectedRooms.forEach { room ->
            appendSystemMessage(room.id, "${friend.name} 已被删除并移出聊天室")
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
            // 请求期间好友可能已被删除，避免删除后又冒出回复
            if (_uiState.value.friends.any { it.id == friendId }) {
                appendMessage(friendId, ChatMessage.assistant(reply))
            }
        } catch (t: Throwable) {
            _uiState.update { it.copy(error = t.readableMessage()) }
        } finally {
            _uiState.update { it.copy(isSending = false) }
        }
    }

    // ---------------- 聊天室 ----------------

    fun openRoomCreate() {
        _uiState.update {
            it.copy(
                screen = FriendScreen.RoomCreate,
                roomDraftName = "",
                roomDraftMemberIds = emptySet(),
                error = null
            )
        }
    }

    fun updateRoomDraftName(name: String) {
        _uiState.update { it.copy(roomDraftName = name) }
    }

    fun toggleRoomDraftMember(friendId: String) {
        _uiState.update { state ->
            val selected = state.roomDraftMemberIds
            state.copy(
                roomDraftMemberIds = if (friendId in selected) selected - friendId else selected + friendId
            )
        }
    }

    fun createRoom() {
        val state = _uiState.value
        val members = state.roomDraftMemberIds.toList()
        if (members.size < 2) return

        val memberNames = members.mapNotNull { id -> state.friends.firstOrNull { it.id == id }?.name }
        val name = state.roomDraftName.trim()
            .ifBlank { "${memberNames.joinToString("、")} 的聊天室" }

        val room = Room(
            id = UUID.randomUUID().toString(),
            name = name,
            memberIds = members,
            createdAt = System.currentTimeMillis(),
            memberJoinSeq = members.associateWith { 0L }
        )
        _uiState.update {
            it.copy(
                rooms = it.rooms + room,
                screen = FriendScreen.RoomChat(room.id),
                roomDraftName = "",
                roomDraftMemberIds = emptySet(),
                roomInput = "",
                roomPending = emptySet(),
                roomErrors = emptyMap()
            )
        }
        persistRoom(room.id)

        appendSystemMessage(
            roomId = room.id,
            text = "${memberNames.joinToString("、")} 加入了聊天室"
        )
    }

    fun openRoom(roomId: String) {
        _uiState.update {
            it.copy(
                screen = FriendScreen.RoomChat(roomId),
                roomInput = "",
                roomPending = emptySet(),
                roomErrors = emptyMap(),
                error = null
            )
        }
    }

    /** 删除整个聊天室及其消息。 */
    fun deleteRoom(roomId: String) {
        val room = _uiState.value.rooms.firstOrNull { it.id == roomId } ?: return
        _uiState.update { current ->
            current.copy(
                rooms = current.rooms.filterNot { it.id == room.id },
                roomMessages = current.roomMessages.filterNot { it.roomId == room.id },
                screen = if ((current.screen as? FriendScreen.RoomChat)?.roomId == room.id) {
                    FriendScreen.List
                } else {
                    current.screen
                },
                roomInput = "",
                roomPending = emptySet(),
                roomErrors = emptyMap()
            )
        }
        viewModelScope.launch {
            runCatching { roomRepository.delete(room.id) }
        }
    }

    fun updateRoomInput(text: String) {
        _uiState.update { it.copy(roomInput = text) }
    }

    fun sendRoomMessage() = viewModelScope.launch {
        val state = _uiState.value
        val room = state.activeRoom ?: return@launch
        val text = state.roomInput.trim()
        if (text.isEmpty()) return@launch

        appendRoomMessage(
            RoomMessage(
                id = UUID.randomUUID().toString(),
                roomId = room.id,
                sender = RoomSender.User,
                content = text,
                createdAt = System.currentTimeMillis(),
                seq = seqCounter.incrementAndGet()
            )
        )
        _uiState.update { it.copy(roomInput = "", roomErrors = emptyMap()) }
        room.memberIds.forEach { friendId -> enqueueRoomReply(room.id, friendId) }
    }

    fun retryRoomReply(friendId: String) {
        val room = _uiState.value.activeRoom ?: return
        _uiState.update { it.copy(roomErrors = it.roomErrors - friendId) }
        enqueueRoomReply(room.id, friendId)
    }

    /**
     * 房间内增删成员。至少保留一位，避免房间变成空壳。
     * 新成员会记录加入位置：只看得到加入之后的内容，并自动打招呼。
     */
    fun toggleRoomMember(friendId: String) {
        val state = _uiState.value
        val room = state.activeRoom ?: return
        val friendName = state.friends.firstOrNull { it.id == friendId }?.name ?: "好友"

        if (friendId in room.memberIds) {
            val memberIds = room.memberIds - friendId
            if (memberIds.isEmpty()) return
            _uiState.update {
                it.copy(
                    rooms = it.rooms.map { current ->
                        if (current.id == room.id) {
                            current.copy(
                                memberIds = memberIds,
                                memberJoinSeq = current.memberJoinSeq - friendId
                            )
                        } else {
                            current
                        }
                    },
                    roomErrors = it.roomErrors - friendId
                )
            }
            persistRoom(room.id)
            appendSystemMessage(room.id, "$friendName 退出了聊天室")
        } else {
            val joinSeq = seqCounter.incrementAndGet()
            _uiState.update {
                it.copy(
                    rooms = it.rooms.map { current ->
                        if (current.id == room.id) {
                            current.copy(
                                memberIds = current.memberIds + friendId,
                                memberJoinSeq = current.memberJoinSeq + (friendId to joinSeq)
                            )
                        } else {
                            current
                        }
                    },
                    roomErrors = it.roomErrors - friendId
                )
            }
            persistRoom(room.id)
            appendSystemMessage(room.id, "$friendName 加入了聊天室", joinSeq)
            enqueueRoomGreeting(room.id, friendId)
        }
    }

    /**
     * 新加入的 AI 不带上任何用户历史，只让它打个招呼。
     */
    private fun enqueueRoomGreeting(roomId: String, friendId: String) {
        val lock = roomLocks.getOrPut("$roomId:$friendId") { Mutex() }
        viewModelScope.launch {
            lock.withLock {
                val state = _uiState.value
                val room = state.rooms.firstOrNull { it.id == roomId } ?: return@withLock
                if (friendId !in room.memberIds) return@withLock
                val friend = state.friends.firstOrNull { it.id == friendId } ?: return@withLock

                if (isRoomActive(roomId)) {
                    _uiState.update { it.copy(roomPending = it.roomPending + friendId) }
                }
                try {
                    val ai = aiFor(friend.brand)
                        ?: throw IllegalStateException("暂不支持的厂商：${friend.brand}")
                    val greeting = ai.chat(
                        messages = listOf(
                            ChatMessage.user(
                                "你刚加入这个聊天室。请用一句简短的话向大家打个招呼，" +
                                    "介绍你的名字和角色，不要提及其他内容。"
                            )
                        ),
                        model = friend.model,
                        systemPrompt = friend.systemPrompt,
                        enableThinking = friend.thinkingEnabled
                    )
                    if (isFriendActiveInRoom(roomId, friendId)) {
                        appendRoomMessage(
                            RoomMessage(
                                id = UUID.randomUUID().toString(),
                                roomId = roomId,
                                sender = RoomSender.Ai(friendId),
                                content = greeting,
                                createdAt = System.currentTimeMillis(),
                                seq = seqCounter.incrementAndGet()
                            )
                        )
                    }
                    if (isRoomActive(roomId)) {
                        _uiState.update { it.copy(roomErrors = it.roomErrors - friendId) }
                    }
                } catch (t: Throwable) {
                    if (isRoomActive(roomId)) {
                        _uiState.update {
                            it.copy(roomErrors = it.roomErrors + (friendId to t.readableMessage()))
                        }
                    }
                } finally {
                    if (isRoomActive(roomId)) {
                        _uiState.update { it.copy(roomPending = it.roomPending - friendId) }
                    }
                }
            }
        }
    }

    private fun enqueueRoomReply(roomId: String, friendId: String) {
        val lock = roomLocks.getOrPut("$roomId:$friendId") { Mutex() }
        viewModelScope.launch {
            lock.withLock {
                val state = _uiState.value
                val room = state.rooms.firstOrNull { it.id == roomId } ?: return@withLock
                if (friendId !in room.memberIds) return@withLock
                val friend = state.friends.firstOrNull { it.id == friendId } ?: return@withLock

                val context = buildRoomContext(
                    messages = state.roomMessages.filter { it.roomId == roomId },
                    friendId = friendId,
                    joinedSeq = room.memberJoinSeq[friendId] ?: 0L
                ).trimToBudget()
                if (context.isEmpty()) return@withLock

                if (isRoomActive(roomId)) {
                    _uiState.update { it.copy(roomPending = it.roomPending + friendId) }
                }
                try {
                    val ai = aiFor(friend.brand)
                        ?: throw IllegalStateException("暂不支持的厂商：${friend.brand}")
                    val reply = ai.chat(
                        messages = context,
                        model = friend.model,
                        systemPrompt = friend.systemPrompt,
                        enableThinking = friend.thinkingEnabled
                    )
                    if (isFriendActiveInRoom(roomId, friendId)) {
                        appendRoomMessage(
                            RoomMessage(
                                id = UUID.randomUUID().toString(),
                                roomId = roomId,
                                sender = RoomSender.Ai(friendId),
                                content = reply,
                                createdAt = System.currentTimeMillis(),
                                seq = seqCounter.incrementAndGet()
                            )
                        )
                    }
                    if (isRoomActive(roomId)) {
                        _uiState.update { it.copy(roomErrors = it.roomErrors - friendId) }
                    }
                } catch (t: Throwable) {
                    if (isRoomActive(roomId)) {
                        _uiState.update {
                            it.copy(roomErrors = it.roomErrors + (friendId to t.readableMessage()))
                        }
                    }
                } finally {
                    if (isRoomActive(roomId)) {
                        _uiState.update { it.copy(roomPending = it.roomPending - friendId) }
                    }
                }
            }
        }
    }

    private fun appendRoomMessage(message: RoomMessage) {
        _uiState.update { state ->
            state.copy(
                roomMessages = (state.roomMessages + message)
                    .sortedWith(compareBy({ it.createdAt }, { it.seq }))
            )
        }
        viewModelScope.launch {
            runCatching { roomRepository.appendMessage(message.toStored()) }
        }
    }

    private fun appendSystemMessage(
        roomId: String,
        text: String,
        seq: Long = seqCounter.incrementAndGet()
    ) {
        appendRoomMessage(
            RoomMessage(
                id = UUID.randomUUID().toString(),
                roomId = roomId,
                sender = RoomSender.System,
                content = text,
                createdAt = System.currentTimeMillis(),
                seq = seq
            )
        )
    }

    private fun isRoomActive(roomId: String): Boolean = _uiState.value.activeRoom?.id == roomId

    /** 回复返回时，好友可能已被删除或移出房间，此时丢弃这次结果。 */
    private fun isFriendActiveInRoom(roomId: String, friendId: String): Boolean {
        val state = _uiState.value
        val stillMember = state.rooms.firstOrNull { it.id == roomId }
            ?.memberIds?.contains(friendId) == true
        return stillMember && state.friends.any { it.id == friendId }
    }

    // ---------------- 公共 ----------------

    private fun aiFor(brand: String): IAI? {
        ais[brand]?.let { return it }
        val created = AIFactory().createAI(brand) ?: return null
        ais[brand] = created
        return created
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
        viewModelScope.launch {
            runCatching {
                friendRepository.appendMessage(
                    friendId = friendId,
                    message = StoredMessage(role = message.role, content = message.content)
                )
            }
        }
    }

    private fun persistRoom(roomId: String) {
        val room = _uiState.value.rooms.firstOrNull { it.id == roomId } ?: return
        viewModelScope.launch {
            runCatching { roomRepository.upsert(room.toStored()) }
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
