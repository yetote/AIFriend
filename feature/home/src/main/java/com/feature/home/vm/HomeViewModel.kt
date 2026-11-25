package com.feature.home.vm

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.core.ai.factory.AIFactory
import com.core.ai.model.Friend
import com.core.ai.model.ModelPriceRefresher
import com.core.ai.model.toFriend
import com.core.ai.probe.ApiCheckState
import com.core.ai.probe.FriendProbeStore
import com.core.common.BaseViewModel
import com.core.repository.storage.AIProviderSettings
import com.core.repository.storage.AvatarStore
import com.core.repository.storage.FriendRepository
import com.core.repository.storage.LegacyMigration
import com.core.repository.storage.ProfileRepository
import com.core.repository.storage.RoomRepository
import com.core.repository.storage.StoredRoomMessage
import com.feature.home.contract.HomeScreen
import com.feature.home.contract.HomeUiState
import com.feature.room.data.toRoom
import com.feature.room.data.toStored
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * 首页只负责：好友/房间列表、统计、搜索、探活、删除、置顶、用户头像。
 * 1:1 会话在 AgentViewModel，聊天室在 RoomViewModel。
 */
class HomeViewModel(application: Application) : BaseViewModel(application) {

    private val friendRepository = FriendRepository(application)
    private val roomRepository = RoomRepository(application)
    private val profileRepository = ProfileRepository(application)
    private val providerSettings = AIProviderSettings(application)
    private val avatarStore = AvatarStore(application)
    private val aiFactory = AIFactory()

    private val priceRefresher = ModelPriceRefresher(application)

    private val seqCounter = AtomicLong(0)

    private var probeJob: Job? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { LegacyMigration.migrateIfNeeded(application) }
            withContext(Dispatchers.IO) { runCatching { providerSettings.load() } }
            val storedRooms = runCatching { roomRepository.loadRooms() }.getOrDefault(emptyList())
            val maxMessageSeq = runCatching { roomRepository.maxMessageSeq() }.getOrDefault(0L)
            val maxJoinSeq = storedRooms.flatMap { it.memberJoinSeq.values }.maxOrNull() ?: 0L
            seqCounter.set(maxOf(maxMessageSeq, maxJoinSeq))
            refresh()
            observeFriends()
        }
        observeProbeStore()
        refreshPricesDaily()
    }

    /**
     * 好友列表跟着数据库走，而不是靠各条返回路径记得调 refresh()。
     *
     * 换头像、编辑资料、发消息都会写库，写完这里就会收到新列表，
     * 首页不再需要「退出时手动同步」这种容易漏掉的约定。
     */
    private fun observeFriends() {
        viewModelScope.launch {
            friendRepository.observe().collect { stored ->
                val list = stored.map { it.toFriend() }
                _uiState.update { state ->
                    state.copy(
                        friends = list,
                        apiChecks = FriendProbeStore.resolveForFriends(list, webSearch = false),
                    )
                }
            }
        }
    }

    /** 订阅全局探活中心：无论是首页探活还是狼人杀开局探活，结果实时同步到好友列表。 */
    private fun observeProbeStore() {
        viewModelScope.launch {
            FriendProbeStore.checks.collect { snapshot ->
                _uiState.update { state ->
                    state.copy(
                        apiChecks = FriendProbeStore.resolveForFriends(
                            friends = state.friends,
                            webSearch = false,
                            snapshot = snapshot,
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            FriendProbeStore.progress.collect { progress ->
                _uiState.update { it.copy(probeProgress = progress) }
            }
        }
    }

    /**
     * 每天首次打开时更新一次聚合平台的模型价格。
     *
     * 放在首页而不是设置页：首页是必然经过的入口，设置页可能一辈子不进，
     * 那样价格就永远是内置快照里那份过期的数。
     *
     * 不阻塞启动 —— 好友列表先出来，价格在后台抓完再悄悄生效。
     * 失败了也不提示：这不是用户主动发起的操作，安静地用上次的价即可。
     */
    private fun refreshPricesDaily() {
        viewModelScope.launch {
            runCatching { priceRefresher.refreshIfNeeded() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val storedFriends = runCatching { friendRepository.load() }.getOrDefault(emptyList())
            val storedRooms = runCatching { roomRepository.loadRooms() }.getOrDefault(emptyList())
            val userAvatar = runCatching { profileRepository.loadAvatarPath() }.getOrNull()
            val friends = storedFriends.map { it.toFriend() }
            _uiState.update { state ->
                state.copy(
                    friends = friends,
                    rooms = storedRooms.map { it.toRoom() },
                    userAvatarPath = userAvatar ?: state.userAvatarPath,
                    apiChecks = FriendProbeStore.resolveForFriends(friends, webSearch = false),
                )
            }
        }
    }

    // ---------------- 好友探活 ----------------

    /**
     * 一键探活全部好友（按「厂商 + 模型」自动去重，实时逐条点亮）。
     *
     * [force] 默认为 true：用户主动点「探活」时强制重测一遍，避免刚换完 Key 或网络恢复后点不动。
     * 正在探活时重复点击直接忽略，取消走 [cancelProbe]。
     */
    fun probeAllFriends(force: Boolean = true) {
        if (probeJob?.isActive == true) return
        val targets = _uiState.value.friends
        if (targets.isEmpty()) return
        launchProbe(targets, force = force)
    }

    /** 只重测当前标红（不可用）的好友，不重复消耗已连通模型的额度。 */
    fun probeFailedFriends() {
        if (probeJob?.isActive == true) return
        val state = _uiState.value
        val failed = state.friends.filter {
            state.apiChecks[it.id]?.state == ApiCheckState.FAILED
        }
        if (failed.isEmpty()) return
        launchProbe(failed, force = true)
    }

    /** 单独探活某一位好友（与其同厂商同模型的其他好友会同步更新结论）。 */
    fun probeFriend(friendId: String) {
        if (probeJob?.isActive == true) return
        val friend = _uiState.value.friends.firstOrNull { it.id == friendId } ?: return
        launchProbe(listOf(friend), force = true)
    }

    /** 停止当前进行中的批量探活，已跑出的绿/红结果保留。 */
    fun cancelProbe() {
        probeJob?.cancel()
        probeJob = null
    }

    private fun launchProbe(targets: List<Friend>, force: Boolean) {
        probeJob = viewModelScope.launch {
            FriendProbeStore.probe(
                friends = targets,
                force = force,
                webSearch = false,
                loadCredentials = {
                    withContext(Dispatchers.IO) { runCatching { providerSettings.load() } }
                },
                aiFor = { brand -> aiFactory.createAI(brand) },
            )
        }
    }

    fun updateSearch(text: String) {
        _uiState.update { it.copy(search = text) }
    }

    fun setUserAvatar(bytes: ByteArray) {
        viewModelScope.launch {
            val path = avatarStore.save(bytes, USER_AVATAR_ID) ?: return@launch
            runCatching { profileRepository.saveAvatarPath(path) }
            _uiState.update { it.copy(userAvatarPath = path) }
        }
    }

    // ---------------- 导航 ----------------

    fun openCreate() {
        _uiState.update { it.copy(screen = HomeScreen.Create) }
    }

    fun openChat(friendId: String) {
        _uiState.update { it.copy(screen = HomeScreen.Chat(friendId)) }
    }

    fun startEditFriend(friendId: String) {
        if (_uiState.value.friends.none { it.id == friendId }) return
        _uiState.update { it.copy(screen = HomeScreen.EditFriend(friendId)) }
    }

    fun openRoomCreate() {
        _uiState.update { it.copy(screen = HomeScreen.RoomCreate) }
    }

    fun openRoom(roomId: String) {
        _uiState.update { it.copy(screen = HomeScreen.RoomChat(roomId)) }
    }

    fun openWerewolf() {
        _uiState.update { it.copy(screen = HomeScreen.Werewolf) }
    }

    fun openSettings() {
        _uiState.update { it.copy(screen = HomeScreen.Settings) }
    }

    fun back() {
        _uiState.update { it.copy(screen = HomeScreen.List) }
    }

    // ---------------- 删除 / 置顶 ----------------

    fun deleteFriend(friendId: String) {
        val state = _uiState.value
        val friend = state.friends.firstOrNull { it.id == friendId } ?: return
        val affectedRooms = state.rooms.filter { friendId in it.memberIds }

        _uiState.update { current ->
            val remaining = current.friends.filterNot { it.id == friendId }
            current.copy(
                friends = remaining,
                apiChecks = FriendProbeStore.resolveForFriends(remaining, webSearch = false),
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
                screen = if ((current.screen as? HomeScreen.Chat)?.friendId == friendId) {
                    HomeScreen.List
                } else {
                    current.screen
                }
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

    fun deleteRoom(roomId: String) {
        val room = _uiState.value.rooms.firstOrNull { it.id == roomId } ?: return
        _uiState.update { current ->
            current.copy(
                rooms = current.rooms.filterNot { it.id == room.id },
                screen = if ((current.screen as? HomeScreen.RoomChat)?.roomId == room.id) {
                    HomeScreen.List
                } else {
                    current.screen
                }
            )
        }
        viewModelScope.launch { runCatching { roomRepository.delete(room.id) } }
        viewModelScope.launch { avatarStore.delete(room.id) }
    }

    fun toggleRoomPin(roomId: String) {
        val room = _uiState.value.rooms.firstOrNull { it.id == roomId } ?: return
        _uiState.update { state ->
            state.copy(
                rooms = state.rooms.map {
                    if (it.id == roomId) it.copy(pinned = !it.pinned) else it
                }
            )
        }
        val updated = _uiState.value.rooms.firstOrNull { it.id == roomId } ?: return
        viewModelScope.launch { runCatching { roomRepository.upsert(updated.toStored()) } }
    }

    private fun appendSystemMessage(roomId: String, text: String) {
        val message = StoredRoomMessage(
            id = UUID.randomUUID().toString(),
            roomId = roomId,
            senderType = "system",
            content = text,
            createdAt = System.currentTimeMillis(),
            seq = seqCounter.incrementAndGet()
        )
        viewModelScope.launch { runCatching { roomRepository.appendMessage(message) } }
    }

    private companion object {
        const val USER_AVATAR_ID = "user"
    }
}
