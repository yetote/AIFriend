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
    val roomMentionIds: Set<String> = emptySet(),
    val roomPending: Set<String> = emptySet(),
    val roomErrors: Map<String, String> = emptyMap(),
    val roomHasMore: Set<String> = emptySet(),
    val roomLoadingMore: Set<String> = emptySet(),
    /** 已经读过首屏消息的房间（区分「还没读」和「真的没有消息」）。 */
    val roomMessagesLoaded: Set<String> = emptySet(),
    val roomDraftName: String = "",
    val roomDraftPrompt: String = "",
    val roomDraftMemberIds: Set<String> = emptySet(),
    /** 正在自由对话的房间，null 表示没有在跑。 */
    val autoChatRoomId: String? = null,
    /** 自由对话已完成的接话次数。 */
    val autoChatCompleted: Int = 0,
    /** 自由对话本次的接话上限。 */
    val autoChatTotal: Int = 0,
    /** 好友列表是否已经读过一次（区分「还没读」和「真的没有成员」）。 */
    val friendsLoaded: Boolean = false,
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
