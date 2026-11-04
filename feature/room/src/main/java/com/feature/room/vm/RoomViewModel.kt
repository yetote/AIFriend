package com.feature.room.vm

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
import com.feature.agent.data.toFriend
import com.feature.agent.data.trimToBudget
import com.feature.agent.model.Friend
import com.feature.room.contract.RoomUiState
import com.feature.room.data.buildRoomContext
import com.feature.room.data.buildRoomMarkdown
import com.feature.room.data.buildRoomSystemPrompt
import com.feature.room.data.toRoom
import com.feature.room.data.toRoomMessage
import com.feature.room.data.toStored
import com.feature.room.model.MAX_ROOM_MEMBERS
import com.feature.room.model.Room
import com.feature.room.model.RoomMessage
import com.feature.room.model.RoomSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class RoomViewModel(application: Application) : BaseViewModel(application) {

    private val friendRepository = FriendRepository(application)
    private val roomRepository = RoomRepository(application)
    private val profileRepository = ProfileRepository(application)
    private val avatarStore = AvatarStore(application)

    private val ais = mutableMapOf<String, IAI>()
    private val roomLocks = mutableMapOf<String, Mutex>()
    private val roomJobs = ConcurrentHashMap<String, Job>()
    private val seqCounter = AtomicLong(0)

    private val _uiState = MutableStateFlow(RoomUiState())
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { LegacyMigration.migrateIfNeeded(application) }
            loadFriends()
            val storedRooms = runCatching { roomRepository.loadRooms() }.getOrDefault(emptyList())
            val maxMessageSeq = runCatching { roomRepository.maxMessageSeq() }.getOrDefault(0L)
            val maxJoinSeq = storedRooms.flatMap { it.memberJoinSeq.values }.maxOrNull() ?: 0L
            seqCounter.set(maxOf(maxMessageSeq, maxJoinSeq))
            _uiState.update {
                it.copy(rooms = storedRooms.map { room -> room.toRoom() }, loaded = true)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadFriends()
            val storedRooms = runCatching { roomRepository.loadRooms() }.getOrDefault(emptyList())
            _uiState.update { it.copy(rooms = storedRooms.map { room -> room.toRoom() }) }
        }
    }

    private suspend fun loadFriends() {
        val storedFriends = runCatching { friendRepository.load() }.getOrDefault(emptyList())
        val avatar = runCatching { profileRepository.loadAvatarPath() }.getOrNull()
        _uiState.update { state ->
            state.copy(
                friends = storedFriends.map { it.toFriend() },
                userAvatarPath = avatar ?: state.userAvatarPath
            )
        }
    }

    // ---------------- 创建房间 ----------------

    fun openRoomCreate() {
        _uiState.update {
            it.copy(
                roomDraftName = "",
                roomDraftPrompt = "",
                roomDraftMemberIds = emptySet()
            )
        }
    }

    fun updateRoomDraftName(name: String) {
        _uiState.update { it.copy(roomDraftName = name) }
    }

    fun updateRoomDraftPrompt(prompt: String) {
        _uiState.update { it.copy(roomDraftPrompt = prompt) }
    }

    fun toggleRoomDraftMember(friendId: String) {
        _uiState.update { state ->
            val selected = state.roomDraftMemberIds
            val next = if (friendId in selected) {
                selected - friendId
            } else {
                if (selected.size >= MAX_ROOM_MEMBERS) return@update state
                selected + friendId
            }
            state.copy(roomDraftMemberIds = next)
        }
    }

    fun createRoom(): Room? {
        val state = _uiState.value
        val members = state.roomDraftMemberIds.toList()
        if (members.size !in 2..MAX_ROOM_MEMBERS) return null

        val memberNames = members.mapNotNull { id -> state.friends.firstOrNull { it.id == id }?.name }
        val name = state.roomDraftName.trim()
            .ifBlank { "${memberNames.joinToString("、")} 的聊天室" }

        val room = Room(
            id = UUID.randomUUID().toString(),
            name = name,
            memberIds = members,
            createdAt = System.currentTimeMillis(),
            prompt = state.roomDraftPrompt.trim(),
            memberJoinSeq = members.associateWith { 0L }
        )
        _uiState.update {
            it.copy(
                rooms = it.rooms + room,
                activeRoomId = room.id,
                roomDraftName = "",
                roomDraftPrompt = "",
                roomDraftMemberIds = emptySet(),
                roomInput = "",
                roomTargetIds = members.toSet(),
                roomPending = emptySet(),
                roomErrors = emptyMap()
            )
        }
        persistRoom(room.id)
        appendSystemMessage(
            roomId = room.id,
            text = "${memberNames.joinToString("、")} 加入了聊天室"
        )
        return room
    }

    // ---------------- 打开房间 / 分页 ----------------

    fun openRoom(roomId: String) {
        val room = _uiState.value.rooms.firstOrNull { it.id == roomId }
        _uiState.update {
            it.copy(
                activeRoomId = roomId,
                roomInput = "",
                roomTargetIds = room?.memberIds?.toSet().orEmpty(),
                roomPending = emptySet(),
                roomErrors = emptyMap()
            )
        }
        viewModelScope.launch {
            loadFriends()
            loadRecentRoomMessages(roomId)
        }
    }

    private suspend fun loadRecentRoomMessages(roomId: String) {
        val page = runCatching { roomRepository.loadRecentMessages(roomId, MESSAGE_PAGE_SIZE) }
            .getOrDefault(emptyList())
            .map { it.toRoomMessage() }
        val total = runCatching { roomRepository.countMessages(roomId) }.getOrDefault(page.size)
        _uiState.update { state ->
            val merged = (state.roomMessages + page)
                .associateBy { it.id }
                .values
                .sortedWith(compareBy({ it.createdAt }, { it.seq }))
            state.copy(
                roomMessages = merged,
                roomHasMore = if (total > merged.count { it.roomId == roomId }) {
                    state.roomHasMore + roomId
                } else {
                    state.roomHasMore - roomId
                }
            )
        }
    }

    fun loadOlderRoomMessages(roomId: String) {
        val state = _uiState.value
        if (roomId in state.roomLoadingMore || roomId !in state.roomHasMore) return
        val oldest = state.roomMessages.filter { it.roomId == roomId }
            .minWithOrNull(compareBy({ it.createdAt }, { it.seq })) ?: return

        _uiState.update { it.copy(roomLoadingMore = it.roomLoadingMore + roomId) }
        viewModelScope.launch {
            val page = runCatching {
                roomRepository.loadMessagesBefore(
                    roomId = roomId,
                    beforeCreatedAt = oldest.createdAt,
                    beforeSeq = oldest.seq,
                    limit = MESSAGE_PAGE_SIZE
                )
            }.getOrDefault(emptyList()).map { it.toRoomMessage() }

            _uiState.update { current ->
                val merged = (current.roomMessages + page)
                    .associateBy { it.id }
                    .values
                    .sortedWith(compareBy({ it.createdAt }, { it.seq }))
                current.copy(
                    roomMessages = merged,
                    roomHasMore = if (page.size >= MESSAGE_PAGE_SIZE) {
                        current.roomHasMore
                    } else {
                        current.roomHasMore - roomId
                    },
                    roomLoadingMore = current.roomLoadingMore - roomId
                )
            }
        }
    }

    // ---------------- 发送与生成 ----------------

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
        val targets = state.roomTargetIds.intersect(room.memberIds.toSet())
            .ifEmpty { room.memberIds.toSet() }
        val ordered = room.memberIds.filter { it in targets }
        if (room.discussionEnabled && room.sequentialSpeaking) {
            var previous: Job? = null
            ordered.forEachIndexed { index, friendId ->
                previous = enqueueRoomReply(
                    roomId = room.id,
                    friendId = friendId,
                    waitFor = previous,
                    roundIndex = index,
                    roundSize = ordered.size
                )
            }
        } else {
            ordered.forEach { friendId -> enqueueRoomReply(room.id, friendId) }
        }
    }

    fun retryRoomReply(friendId: String) {
        val room = _uiState.value.activeRoom ?: return
        _uiState.update { it.copy(roomErrors = it.roomErrors - friendId) }
        enqueueRoomReply(room.id, friendId)
    }

    fun toggleRoomTarget(friendId: String) {
        val state = _uiState.value
        val room = state.activeRoom ?: return
        if (friendId !in room.memberIds) return
        val current = state.roomTargetIds.ifEmpty { room.memberIds.toSet() }
        val next = if (friendId in current) {
            if (current.size <= 1) return
            current - friendId
        } else {
            current + friendId
        }
        _uiState.update { it.copy(roomTargetIds = next) }
    }

    fun selectAllRoomTargets() {
        val room = _uiState.value.activeRoom ?: return
        _uiState.update { it.copy(roomTargetIds = room.memberIds.toSet()) }
    }

    fun stopRoomReply(friendId: String) {
        val room = _uiState.value.activeRoom ?: return
        roomJobs.remove(roomKey(room.id, friendId))?.cancel()
        _uiState.update {
            it.copy(
                roomPending = it.roomPending - friendId,
                roomErrors = it.roomErrors - friendId
            )
        }
    }

    fun stopAllRoomReplies() {
        val room = _uiState.value.activeRoom ?: return
        room.memberIds.forEach { friendId ->
            roomJobs.remove(roomKey(room.id, friendId))?.cancel()
        }
        _uiState.update { it.copy(roomPending = emptySet(), roomErrors = emptyMap()) }
    }

    // ---------------- 设置 / 成员 / 消息管理 ----------------

    fun saveRoomSettings(
        roomId: String,
        name: String,
        prompt: String,
        discussionEnabled: Boolean,
        discussionWindow: Int,
        sequentialSpeaking: Boolean
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        if (_uiState.value.rooms.none { it.id == roomId }) return
        _uiState.update { state ->
            state.copy(
                rooms = state.rooms.map { room ->
                    if (room.id == roomId) {
                        room.copy(
                            name = trimmedName,
                            prompt = prompt.trim(),
                            discussionEnabled = discussionEnabled,
                            discussionWindow = discussionWindow.coerceIn(1, 5),
                            sequentialSpeaking = sequentialSpeaking
                        )
                    } else {
                        room
                    }
                }
            )
        }
        persistRoom(roomId)
    }

    fun setRoomAvatar(roomId: String, bytes: ByteArray) {
        viewModelScope.launch {
            val path = avatarStore.save(bytes, roomId) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    rooms = state.rooms.map { room ->
                        if (room.id == roomId) room.copy(avatarPath = path) else room
                    }
                )
            }
            persistRoom(roomId)
        }
    }

    fun exportRoomMarkdown(roomId: String, onReady: (String) -> Unit) {
        val room = _uiState.value.rooms.firstOrNull { it.id == roomId } ?: return
        viewModelScope.launch {
            val messages = runCatching { roomRepository.loadAllMessages(roomId) }
                .getOrDefault(emptyList())
                .map { it.toRoomMessage() }
            val names = _uiState.value.friends.associate { it.id to it.name }
            onReady(buildRoomMarkdown(room, messages, names))
        }
    }

    fun clearRoomMessages(roomId: String) {
        val state = _uiState.value
        val room = state.rooms.firstOrNull { it.id == roomId } ?: return
        cancelRoomJobs(roomId)
        val resetRoom = room.copy(memberJoinSeq = room.memberIds.associateWith { 0L })
        _uiState.update { current ->
            current.copy(
                rooms = current.rooms.map { if (it.id == roomId) resetRoom else it },
                roomMessages = current.roomMessages.filterNot { it.roomId == roomId },
                roomPending = emptySet(),
                roomErrors = emptyMap(),
                roomInput = "",
                roomHasMore = current.roomHasMore - roomId,
                roomLoadingMore = current.roomLoadingMore - roomId
            )
        }
        viewModelScope.launch {
            runCatching { roomRepository.clearMessages(resetRoom.toStored()) }
        }
    }

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
                    roomErrors = it.roomErrors - friendId,
                    roomPending = it.roomPending - friendId,
                    roomTargetIds = memberIds.toSet()
                )
            }
            roomJobs.remove(roomKey(room.id, friendId))?.cancel()
            persistRoom(room.id)
            appendSystemMessage(room.id, "$friendName 退出了聊天室")
        } else {
            if (room.memberIds.size >= MAX_ROOM_MEMBERS) return
            val joinSeq = seqCounter.incrementAndGet()
            val memberIds = room.memberIds + friendId
            _uiState.update {
                it.copy(
                    rooms = it.rooms.map { current ->
                        if (current.id == room.id) {
                            current.copy(
                                memberIds = memberIds,
                                memberJoinSeq = current.memberJoinSeq + (friendId to joinSeq)
                            )
                        } else {
                            current
                        }
                    },
                    roomErrors = it.roomErrors - friendId,
                    roomTargetIds = memberIds.toSet()
                )
            }
            persistRoom(room.id)
            appendSystemMessage(room.id, "$friendName 加入了聊天室", joinSeq)
            enqueueRoomGreeting(room.id, friendId)
        }
    }

    // ---------------- 请求 ----------------

    private fun enqueueRoomGreeting(roomId: String, friendId: String) {
        val lock = roomLocks.getOrPut(roomKey(roomId, friendId)) { Mutex() }
        launchRoomRequest(roomId, friendId) {
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
                        systemPrompt = buildRoomSystemPrompt(room.prompt, friend.systemPrompt),
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
                } catch (t: CancellationException) {
                    throw t
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

    private fun enqueueRoomReply(
        roomId: String,
        friendId: String,
        waitFor: Job? = null,
        roundIndex: Int? = null,
        roundSize: Int? = null
    ): Job {
        val lock = roomLocks.getOrPut(roomKey(roomId, friendId)) { Mutex() }
        return launchRoomRequest(roomId, friendId) {
            waitFor?.join()
            lock.withLock {
                val state = _uiState.value
                val room = state.rooms.firstOrNull { it.id == roomId } ?: return@withLock
                if (friendId !in room.memberIds) return@withLock
                val friend = state.friends.firstOrNull { it.id == friendId } ?: return@withLock

                val discussion = room.discussionEnabled
                val maxTokens = if (discussion) 4000 else 6000
                var context = buildRoomContext(
                    messages = state.roomMessages.filter { it.roomId == roomId },
                    friendId = friendId,
                    joinedSeq = room.memberJoinSeq[friendId] ?: 0L,
                    otherWindowRounds = if (discussion) room.discussionWindow else null,
                    friendNames = state.friends.associate { it.id to it.name }
                )
                if (roundIndex != null) {
                    context = context + ChatMessage.user(
                        "（本轮你是第 ${roundIndex + 1} 位发言，前面已经有 $roundIndex 位成员发言。" +
                            "请基于前面的内容补充新的观点；不要复读别人或自己之前的句式、数字和结论。）"
                    )
                }
                context = context.trimToBudget(maxTokens)
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
                        systemPrompt = buildRoomSystemPrompt(room.prompt, friend.systemPrompt),
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
                } catch (t: CancellationException) {
                    throw t
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

    private fun isRoomActive(roomId: String): Boolean = _uiState.value.activeRoomId == roomId

    private fun isFriendActiveInRoom(roomId: String, friendId: String): Boolean {
        val state = _uiState.value
        val stillMember = state.rooms.firstOrNull { it.id == roomId }
            ?.memberIds?.contains(friendId) == true
        return stillMember && state.friends.any { it.id == friendId }
    }

    private fun aiFor(brand: String): IAI? {
        ais[brand]?.let { return it }
        val created = AIFactory().createAI(brand) ?: return null
        ais[brand] = created
        return created
    }

    private fun roomKey(roomId: String, friendId: String): String = "$roomId:$friendId"

    private fun launchRoomRequest(
        roomId: String,
        friendId: String,
        block: suspend () -> Unit
    ): Job {
        val key = roomKey(roomId, friendId)
        roomJobs.remove(key)?.cancel()
        val job = viewModelScope.launch { block() }
        roomJobs[key] = job
        job.invokeOnCompletion { roomJobs.remove(key, job) }
        return job
    }

    private fun cancelRoomJobs(roomId: String) {
        roomJobs.keys
            .filter { it.startsWith("$roomId:") }
            .forEach { roomJobs.remove(it)?.cancel() }
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
        const val MESSAGE_PAGE_SIZE = 50
    }
}
