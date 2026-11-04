package com.feature.room.contract

import com.feature.agent.model.Friend
import com.feature.room.model.Room
import com.feature.room.model.RoomMessage

data class RoomUiState(
    val rooms: List<Room> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val roomMessages: List<RoomMessage> = emptyList(),
    val userAvatarPath: String? = null,
    val activeRoomId: String? = null,
    val roomInput: String = "",
    val roomTargetIds: Set<String> = emptySet(),
    val roomPending: Set<String> = emptySet(),
    val roomErrors: Map<String, String> = emptyMap(),
    val roomHasMore: Set<String> = emptySet(),
    val roomLoadingMore: Set<String> = emptySet(),
    val roomDraftName: String = "",
    val roomDraftPrompt: String = "",
    val roomDraftMemberIds: Set<String> = emptySet(),
    val loaded: Boolean = false
) {
    val activeRoom: Room?
        get() = activeRoomId?.let { id -> rooms.firstOrNull { it.id == id } }

    val activeRoomMessages: List<RoomMessage>
        get() = activeRoom?.let { room ->
            roomMessages.filter { it.roomId == room.id }
                .sortedWith(compareBy({ it.createdAt }, { it.seq }))
        }.orEmpty()
}

sealed interface RoomScreen {
    data object Create : RoomScreen
    data class Chat(val roomId: String) : RoomScreen
    data class Settings(val roomId: String) : RoomScreen
}
