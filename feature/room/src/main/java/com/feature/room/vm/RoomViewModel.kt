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
import com.feature.room.data.AutoChatPolicy
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
import kotlin.random.Random

class RoomViewModel(application: Application) : BaseViewModel(application) {

    private val friendRepository = FriendRepository(application)
    private val roomRepository = RoomRepository(application)
    private val profileRepository = ProfileRepository(application)
    private val avatarStore = AvatarStore(application)

    private val ais = mutableMapOf<String, IAI>()
    private val roomLocks = mutableMapOf<String, Mutex>()
    private val roomJobs = ConcurrentHashMap<String, Job>()
    private val autoChatJobs = ConcurrentHashMap<String, Job>()

    /**
     * 每个房间当前自由对话的「代次」。
     * 取消旧循环后重新开始时，旧 job 的 finally 会晚一步跑到，
     * 靠这个代次拦住它，避免它把新循环的状态清掉。
     */
    private val autoChatGeneration = ConcurrentHashMap<String, Long>()
    private val autoChatGenerationSeq = AtomicLong(0)
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
                roomMentionIds = emptySet(),
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
        // 离开原来的房间就不要再让那个房间继续自动接话
        autoChatJobs.keys.filter { it != roomId }.forEach { cancelAutoChat(it) }
        _uiState.update {
            it.copy(
                activeRoomId = roomId,
                roomInput = "",
                roomMentionIds = emptySet(),
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

        // 用户又说了一句：先打断正在进行的自由对话，避免两条线并行互相污染上下文
        cancelAutoChat(room.id)

        val targets = state.roomMentionIds.intersect(room.memberIds.toSet())
        // 只选部分成员时，这一轮只对这些成员可见；全员或未选时按全员处理
        val visibility = if (targets.isEmpty() || targets.size >= room.memberIds.size) {
            null
        } else {
            targets
        }
        val replyTargets = targets.ifEmpty { room.memberIds.toSet() }

        appendRoomMessage(
            RoomMessage(
                id = UUID.randomUUID().toString(),
                roomId = room.id,
                sender = RoomSender.User,
                content = text,
                createdAt = System.currentTimeMillis(),
                seq = seqCounter.incrementAndGet(),
                targetIds = visibility
            )
        )
        _uiState.update {
            it.copy(roomInput = "", roomMentionIds = emptySet(), roomErrors = emptyMap())
        }
        // 发言顺序由客户端随机决定，且一律串行：
        // 这样后一位真的能看到前一位说了什么，而不是各自答各自的。
        // 只有在讨论模式（成员能看到别人的发言）下才告诉模型「你是第几位」，否则它看不到前文，
        // 被告知「前面已有 N 位发言」只会诱发凭空附和。
        val positionHint = room.discussionEnabled
        val ordered = AutoChatPolicy.planSpeakers(room.memberIds.filter { it in replyTargets })
        var previous: Job? = null
        ordered.forEachIndexed { index, friendId ->
            previous = enqueueRoomReply(
                roomId = room.id,
                friendId = friendId,
                waitFor = previous,
                roundIndex = if (positionHint) index else null,
                // 首批是一条接一条上屏的，也要有一位一位说话的节奏
                gapBefore = index > 0
            )
        }

        // 首批都答完之后，如果开了自由对话就让成员接着聊；
        // 首位发言人要排除刚说完的那位，避免同一个人连着说两句。
        if (room.autoChatEnabled && room.discussionEnabled) {
            startAutoChat(
                roomId = room.id,
                waitFor = previous,
                lastSpeakerId = ordered.lastOrNull(),
                // 用户只 @ 了部分成员时，后续接话也只在这几个人之间进行
                candidates = ordered
            )
        }
    }

    fun retryRoomReply(friendId: String) {
        val room = _uiState.value.activeRoom ?: return
        _uiState.update { it.copy(roomErrors = it.roomErrors - friendId) }
        enqueueRoomReply(room.id, friendId)
    }

    /** 记录选择器选中的 @ 成员 ID，输入框里的 @名字 由 UI 负责插入。 */
    fun selectMentions(friendIds: Set<String>) {
        val room = _uiState.value.activeRoom ?: return
        _uiState.update {
            it.copy(roomMentionIds = friendIds.intersect(room.memberIds.toSet()))
        }
    }

    fun stopRoomReply(friendId: String) {
        val room = _uiState.value.activeRoom ?: return
        // 自由对话期间点单人的「停止」等于结束这一轮接话，否则它会继续换下一个人说
        if (autoChatJobs.containsKey(room.id)) {
            cancelAutoChat(room.id)
        }
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
        // 先掐掉自由对话的循环 job：只 cancel 当前请求的话，循环还会接着选下一位
        cancelAutoChat(room.id)
        room.memberIds.forEach { friendId ->
            roomJobs.remove(roomKey(room.id, friendId))?.cancel()
        }
        _uiState.update { it.copy(roomPending = emptySet(), roomErrors = emptyMap()) }
    }

    /** 用户手动结束当前房间的自由对话。 */
    fun stopAutoChat(roomId: String) {
        cancelAutoChat(roomId)
    }

    // ---------------- 自由对话 ----------------

    /**
     * 首批回复都结束后，按「一次接话 = 一轮」继续让成员互聊。
     * 依赖讨论模式：没有讨论模式时成员看不到别人的发言，接话只会各说各话。
     */
    private fun startAutoChat(
        roomId: String,
        waitFor: Job?,
        lastSpeakerId: String? = null,
        candidates: List<String> = emptyList()
    ) {
        val room = _uiState.value.rooms.firstOrNull { it.id == roomId } ?: return
        if (!room.autoChatEnabled || !room.discussionEnabled) return
        val total = AutoChatPolicy.sanitizeTurns(room.autoChatTurns)
        val pool = candidates.ifEmpty { room.memberIds }
        if (!AutoChatPolicy.shouldContinue(0, total, pool.size)) return

        cancelAutoChat(roomId)
        val generation = autoChatGenerationSeq.incrementAndGet()
        autoChatGeneration[roomId] = generation
        val job = viewModelScope.launch {
            _uiState.update {
                it.copy(autoChatRoomId = roomId, autoChatCompleted = 0, autoChatTotal = total)
            }
            try {
                waitFor?.join()
                var lastSpeaker: String? = lastSpeakerId
                var lastReply: String? = null
                var completed = 0
                var failures = 0
                while (isActive) {
                    val current = _uiState.value.rooms.firstOrNull { it.id == roomId }
                        ?: break
                    // 成员可能中途被移除，候选按当前房间成员过滤一遍
                    val speakers = pool.filter { it in current.memberIds }.ifEmpty {
                        current.memberIds
                    }
                    if (!AutoChatPolicy.shouldContinue(
                            completedTurns = completed,
                            maxTurns = total,
                            speakerCount = speakers.size,
                            lastReply = lastReply
                        )
                    ) {
                        break
                    }
                    val names = _uiState.value.friends
                        .filter { it.id in speakers }
                        .associate { it.id to it.name }
                    val mentioned = lastReply
                        ?.let { AutoChatPolicy.findMentionedMember(it, names) }
                    val next = AutoChatPolicy.pickNextSpeaker(
                        candidates = speakers,
                        lastSpeakerId = lastSpeaker,
                        mentionedFriendId = mentioned
                    ) ?: break

                    lastSpeaker = next
                    // 连着秒回看起来太假：每接一次话之前都停一下，
                    // 停顿取随机值，免得节奏像机器一样整齐。
                    delay(
                        AUTO_CHAT_TURN_GAP_MIN_MS +
                            Random.nextLong(AUTO_CHAT_TURN_GAP_SPREAD_MS)
                    )
                    val replied = runRoomReply(
                        roomId = roomId,
                        friendId = next,
                        isBurstTurn = true
                    )
                    lastReply = replied
                    if (replied == null) {
                        // 偶发失败（超时、限流）换下一位继续；连续失败才整体停下。
                        // 失败的这一次不算接话次数，否则次数会被报错吃光。
                        failures++
                        if (failures >= AUTO_CHAT_MAX_FAILURES) break
                        delay(AUTO_CHAT_RETRY_GAP_MS)
                    } else {
                        failures = 0
                        completed++
                        _uiState.update { it.copy(autoChatCompleted = completed) }
                    }
                }
            } finally {
                // 已被新的循环接管时不要再清状态，否则刚打开的新循环会被旧 job 抹掉
                if (autoChatGeneration[roomId] == generation) {
                    _uiState.update {
                        if (it.autoChatRoomId == roomId) {
                            it.copy(
                                autoChatRoomId = null,
                                autoChatCompleted = 0,
                                autoChatTotal = 0
                            )
                        } else {
                            it
                        }
                    }
                }
            }
        }
        autoChatJobs[roomId] = job
        job.invokeOnCompletion { autoChatJobs.remove(roomId, job) }
    }

    private fun cancelAutoChat(roomId: String) {
        // 换一代，让被取消的循环在 finally 里认不出自己，不会回头清状态
        autoChatGeneration[roomId] = autoChatGenerationSeq.incrementAndGet()
        autoChatJobs.remove(roomId)?.cancel()
        _uiState.update {
            if (it.autoChatRoomId == roomId) {
                it.copy(autoChatRoomId = null, autoChatCompleted = 0, autoChatTotal = 0)
            } else {
                it
            }
        }
    }

    // ---------------- 设置 / 成员 / 消息管理 ----------------

    fun saveRoomSettings(
        roomId: String,
        name: String,
        prompt: String,
        discussionEnabled: Boolean,
        discussionWindow: Int,
        autoChatEnabled: Boolean,
        autoChatTurns: Int
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        if (_uiState.value.rooms.none { it.id == roomId }) return
        // 自由对话依赖讨论模式：讨论模式关闭时必须一起关掉，不留下关不掉的开关
        val autoChat = autoChatEnabled && discussionEnabled
        _uiState.update { state ->
            state.copy(
                rooms = state.rooms.map { room ->
                    if (room.id == roomId) {
                        room.copy(
                            name = trimmedName,
                            prompt = prompt.trim(),
                            discussionEnabled = discussionEnabled,
                            discussionWindow = discussionWindow.coerceIn(1, 5),
                            autoChatEnabled = autoChat,
                            autoChatTurns = AutoChatPolicy.sanitizeTurns(autoChatTurns)
                        )
                    } else {
                        room
                    }
                }
            )
        }
        if (!autoChat) cancelAutoChat(roomId)
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
        cancelAutoChat(roomId)
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
                    roomMentionIds = it.roomMentionIds - friendId
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
                    roomMentionIds = it.roomMentionIds
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
        gapBefore: Boolean = false
    ): Job = launchRoomRequest(roomId, friendId) {
        if (waitFor != null) {
            waitFor.join()
            // 串行发言时后一位也要「先读一下前一位说的」，否则上一条刚上屏下一条就顶上来，像机器人刷屏
            if (gapBefore) {
                delay(
                    AUTO_CHAT_TURN_GAP_MIN_MS +
                        Random.nextLong(AUTO_CHAT_TURN_GAP_SPREAD_MS)
                )
            }
        }
        runRoomReply(roomId, friendId, roundIndex = roundIndex)
    }

    /**
     * 真正发起一次房间请求并落库，返回模型回复（失败返回 null）。
     * 供「首批回复」和「自由对话」共用，保证两条路径的上下文与落库行为一致。
     */
    private suspend fun runRoomReply(
        roomId: String,
        friendId: String,
        roundIndex: Int? = null,
        isBurstTurn: Boolean = false
    ): String? {
        val lock = roomLocks.getOrPut(roomKey(roomId, friendId)) { Mutex() }
        return lock.withLock {
            val state = _uiState.value
            val room = state.rooms.firstOrNull { it.id == roomId } ?: return@withLock null
            if (friendId !in room.memberIds) return@withLock null
            val friend = state.friends.firstOrNull { it.id == friendId } ?: return@withLock null

            val discussion = room.discussionEnabled
            val maxTokens = when {
                isBurstTurn -> AUTO_CHAT_MAX_TOKENS
                discussion -> DISCUSSION_MAX_TOKENS
                else -> NORMAL_MAX_TOKENS
            }
            var context = buildRoomContext(
                messages = state.roomMessages.filter { it.roomId == roomId },
                friendId = friendId,
                joinedSeq = room.memberJoinSeq[friendId] ?: 0L,
                otherWindowRounds = if (discussion) room.discussionWindow else null,
                friendNames = room.memberIds.mapNotNull { id ->
                    state.friends.firstOrNull { it.id == id }?.let { id to it.name }
                }.toMap(),
                // 自由对话里每一条别人的发言都算一轮，窗口 N 才有意义
                burstAsRounds = isBurstTurn
            )
            if (roundIndex != null) {
                context = context + ChatMessage.user(
                    "（本轮你是第 ${roundIndex + 1} 位发言，前面已经有 $roundIndex 位成员发言。" +
                        "请基于前面的内容补充新的观点；不要复读别人或自己之前的句式、数字和结论。）"
                )
            }
            context = context.trimToBudget(maxTokens)
            if (context.isEmpty()) return@withLock null

            if (isRoomActive(roomId)) {
                _uiState.update { it.copy(roomPending = it.roomPending + friendId) }
            }
            try {
                val ai = aiFor(friend.brand)
                    ?: throw IllegalStateException("暂不支持的厂商：${friend.brand}")
                val reply = ai.chat(
                    messages = context,
                    model = friend.model,
                    systemPrompt = buildRoomSystemPrompt(
                        room.prompt,
                        friend.systemPrompt,
                        autoChat = isBurstTurn
                    ),
                    enableThinking = friend.thinkingEnabled
                )
                // 自由对话的收尾标记只用于判断「聊完了」，不该显示在气泡里
                val content = if (isBurstTurn) {
                    AutoChatPolicy.stripEndMarker(reply)
                } else {
                    reply
                }
                if (content.isNotBlank() && isFriendActiveInRoom(roomId, friendId)) {
                    appendRoomMessage(
                        RoomMessage(
                            id = UUID.randomUUID().toString(),
                            roomId = roomId,
                            sender = RoomSender.Ai(friendId),
                            content = content,
                            createdAt = System.currentTimeMillis(),
                            seq = seqCounter.incrementAndGet()
                        )
                    )
                }
                if (isRoomActive(roomId)) {
                    _uiState.update { it.copy(roomErrors = it.roomErrors - friendId) }
                }
                reply
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (isRoomActive(roomId)) {
                    _uiState.update {
                        it.copy(roomErrors = it.roomErrors + (friendId to t.readableMessage()))
                    }
                }
                null
            } finally {
                if (isRoomActive(roomId)) {
                    _uiState.update { it.copy(roomPending = it.roomPending - friendId) }
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

        /** 普通聊天室上下文预算。 */
        const val NORMAL_MAX_TOKENS = 6000

        /** 讨论模式上下文预算。 */
        const val DISCUSSION_MAX_TOKENS = 4000

        /** 自由对话每一轮只保留很近的上下文，避免接话越聊越长把 token 撑爆。 */
        const val AUTO_CHAT_MAX_TOKENS = 2000

        /** 某位成员失败后，等一小会儿再换下一位，避免连续打满接口。 */
        const val AUTO_CHAT_RETRY_GAP_MS = 600L

        /** 自由对话每次接话前的停顿下限，让它看起来像在打字而不是秒回。 */
        const val AUTO_CHAT_TURN_GAP_MIN_MS = 1200L

        /** 停顿的随机区间，实际停顿是下限加这段随机值。 */
        const val AUTO_CHAT_TURN_GAP_SPREAD_MS = 1800L

        /** 连续失败多少次就放弃整场自由对话。 */
        const val AUTO_CHAT_MAX_FAILURES = 2
    }
}
