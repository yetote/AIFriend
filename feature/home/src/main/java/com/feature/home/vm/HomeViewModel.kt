package com.feature.home.vm

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.core.common.BaseViewModel
import com.core.repository.storage.AvatarStore
import com.core.repository.storage.FriendRepository
import com.core.repository.storage.LegacyMigration
import com.core.repository.storage.ProfileRepository
import com.core.repository.storage.RoomRepository
import com.core.repository.storage.StoredRoomMessage
import com.feature.agent.data.toFriend
import com.feature.home.contract.HomeScreen
import com.feature.home.contract.HomeUiState
import com.feature.room.data.toRoom
import com.feature.room.data.toStored
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * 首页只负责：好友/房间列表、统计、搜索、删除、置顶、用户头像。
 * 1:1 会话在 AgentViewModel，聊天室在 RoomViewModel。
 */
class HomeViewModel(application: Application) : BaseViewModel(application) {

    private val friendRepository = FriendRepository(application)
    private val roomRepository = RoomRepository(application)
    private val profileRepository = ProfileRepository(application)
    private val avatarStore = AvatarStore(application)

    private val seqCounter = AtomicLong(0)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { LegacyMigration.migrateIfNeeded(application) }
            val storedRooms = runCatching { roomRepository.loadRooms() }.getOrDefault(emptyList())
            val maxMessageSeq = runCatching { roomRepository.maxMessageSeq() }.getOrDefault(0L)
            val maxJoinSeq = storedRooms.flatMap { it.memberJoinSeq.values }.maxOrNull() ?: 0L
            seqCounter.set(maxOf(maxMessageSeq, maxJoinSeq))
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val storedFriends = runCatching { friendRepository.load() }.getOrDefault(emptyList())
            val storedRooms = runCatching { roomRepository.loadRooms() }.getOrDefault(emptyList())
            val userAvatar = runCatching { profileRepository.loadAvatarPath() }.getOrNull()
            _uiState.update { state ->
                state.copy(
                    friends = storedFriends.map { it.toFriend() },
                    rooms = storedRooms.map { it.toRoom() },
                    userAvatarPath = userAvatar ?: state.userAvatarPath
                )
            }
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

    fun back() {
        _uiState.update { it.copy(screen = HomeScreen.List) }
    }

    // ---------------- 删除 / 置顶 ----------------

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
