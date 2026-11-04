package com.feature.friend.contract

import com.feature.friend.model.Friend

const val DEFAULT_MODEL = "deepseek-flash"

/** 单 Activity 内的三个页面，先用状态机切换，后续再换 TheRouter/Navigation。 */
sealed interface FriendScreen {
    data object List : FriendScreen
    data object Create : FriendScreen
    data class Chat(val friendId: String) : FriendScreen
}

data class FriendUiState(
    val friends: List<Friend> = emptyList(),
    val screen: FriendScreen = FriendScreen.List,
    val search: String = "",
    val input: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
    val models: List<String> = emptyList(),
    val selectedModel: String = DEFAULT_MODEL,
    val isLoadingModels: Boolean = false
) {
    val activeFriend: Friend?
        get() = (screen as? FriendScreen.Chat)
            ?.let { chat -> friends.firstOrNull { it.id == chat.friendId } }

    val visibleFriends: List<Friend>
        get() = if (search.isBlank()) {
            friends
        } else {
            friends.filter { friend ->
                friend.name.contains(search, ignoreCase = true) ||
                    friend.role.contains(search, ignoreCase = true) ||
                    friend.brand.contains(search, ignoreCase = true)
            }
        }

    val messageCount: Int get() = friends.sumOf { it.messages.size }
    val activeCount: Int get() = friends.count { it.messages.isNotEmpty() }
    val mostActive: String
        get() = friends.maxByOrNull { it.messages.size }
            ?.takeIf { it.messages.isNotEmpty() }
            ?.name ?: "-"
}
