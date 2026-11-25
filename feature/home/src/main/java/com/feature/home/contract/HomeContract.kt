package com.feature.home.contract

import com.core.ai.model.Friend
import com.core.ai.probe.ApiCheck
import com.core.ai.probe.ApiCheckState
import com.core.ai.probe.ProbeProgress
import com.feature.room.model.Room

/** 单 Activity 内的页面状态，导航由 HomeRoute 持有。 */
sealed interface HomeScreen {
    data object List : HomeScreen
    data object Create : HomeScreen
    data class Chat(val friendId: String) : HomeScreen
    data class EditFriend(val friendId: String) : HomeScreen
    data object RoomCreate : HomeScreen
    data class RoomChat(val roomId: String) : HomeScreen

    /** 狼人杀：开局配置与对局，自己持有一套页面状态，不参与这里的导航。 */
    data object Werewolf : HomeScreen

    /** 模型与密钥设置，从首页右上角齿轮进入。 */
    data object Settings : HomeScreen
}

data class HomeUiState(
    val friends: List<Friend> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val userAvatarPath: String? = null,
    val search: String = "",
    val screen: HomeScreen = HomeScreen.List,
    /** 好友 id → 普通对话连通性探活结果，与狼人杀开局页共用进程内缓存。 */
    val apiChecks: Map<String, ApiCheck> = emptyMap(),
    /** 当前一轮批量探活进度（按「厂商 + 模型」去重后的任务数）。 */
    val probeProgress: ProbeProgress = ProbeProgress(),
) {
    val visibleFriends: List<Friend>
        get() = if (search.isBlank()) {
            friends
        } else {
            friends.filter { friend ->
                friend.name.contains(search, ignoreCase = true) ||
                    friend.role.contains(search, ignoreCase = true) ||
                    friend.company.contains(search, ignoreCase = true) ||
                    friend.brand.contains(search, ignoreCase = true) ||
                    friend.model.contains(search, ignoreCase = true)
            }
        }

    val messageCount: Int get() = friends.sumOf { it.messages.size }
    val activeCount: Int get() = friends.count { it.messages.isNotEmpty() }
    val mostActive: String
        get() = friends.maxByOrNull { it.messages.size }
            ?.takeIf { it.messages.isNotEmpty() }
            ?.name ?: "-"

    val aliveCount: Int
        get() = friends.count { apiChecks[it.id]?.state == ApiCheckState.OK }

    val failedCount: Int
        get() = friends.count { apiChecks[it.id]?.state == ApiCheckState.FAILED }

    val probedCount: Int
        get() = friends.count {
            val state = apiChecks[it.id]?.state
            state == ApiCheckState.OK || state == ApiCheckState.FAILED
        }

    val isProbing: Boolean
        get() = probeProgress.active || apiChecks.values.any { it.state == ApiCheckState.CHECKING }

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
