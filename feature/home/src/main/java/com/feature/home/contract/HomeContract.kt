package com.feature.home.contract

import com.feature.agent.model.Friend
import com.feature.room.model.Room

/** 单 Activity 内的页面状态，导航由 HomeRoute 持有。 */
sealed interface HomeScreen {
    data object List : HomeScreen
    data object Create : HomeScreen
    data class Chat(val friendId: String) : HomeScreen
    data class EditFriend(val friendId: String) : HomeScreen
    data object RoomCreate : HomeScreen
    data class RoomChat(val roomId: String) : HomeScreen

    /** 模型与密钥设置，从首页右上角齿轮进入。 */
    data object Settings : HomeScreen
}

data class HomeUiState(
    val friends: List<Friend> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val userAvatarPath: String? = null,
    val search: String = "",
    val screen: HomeScreen = HomeScreen.List
) {
    val visibleFriends: List<Friend>
        get() = if (search.isBlank()) {
            friends
        } else {
            friends.filter { friend ->
                friend.name.contains(search, ignoreCase = true) ||
                    friend.role.contains(search, ignoreCase = true) ||
                    friend.company.contains(search, ignoreCase = true) ||
                    friend.brand.contains(search, ignoreCase = true)
            }
        }

    val messageCount: Int get() = friends.sumOf { it.messages.size }
    val activeCount: Int get() = friends.count { it.messages.isNotEmpty() }
    val mostActive: String
        get() = friends.maxByOrNull { it.messages.size }
            ?.takeIf { it.messages.isNotEmpty() }
            ?.name ?: "-"

    /** 置顶优先，其余按创建时间升序。 */
    val sortedRooms: List<Room>
        get() = rooms.sortedWith(
            compareByDescending<Room> { it.pinned }.thenBy { it.createdAt }
        )

    /** 首页搜索同时匹配好友和聊天室名。 */
    val visibleRooms: List<Room>
        get() = if (search.isBlank()) {
            sortedRooms
        } else {
            sortedRooms.filter { it.name.contains(search, ignoreCase = true) }
        }
}
