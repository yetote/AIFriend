package com.feature.friend.contract

import com.feature.friend.model.Friend
import com.feature.friend.model.Provider
import com.feature.friend.model.Room
import com.feature.friend.model.RoomMessage

/**
 * 当前已接入对话链路的厂商。未迁移到多轮协议的厂商不列出来，避免用户创建出不能聊的好友。
 */
val DEFAULT_PROVIDERS = listOf(
    Provider(
        brand = "Deepseek",
        company = "深度求索",
        defaultModel = "deepseek-flash",
        fallbackModels = listOf("deepseek-flash", "deepseek-v4-pro")
    ),
    Provider(
        brand = "豆包",
        company = "字节跳动",
        defaultModel = "doubao-seed-evolving",
        fallbackModels = listOf("doubao-seed-evolving", "doubao-seed-2-1-pro-260915")
    ),
    Provider(
        brand = "智谱清言",
        company = "北京智谱华章",
        defaultModel = "glm-5.3-flash",
        fallbackModels = listOf("glm-5.3-flash", "glm-5.3", "glm-5.2")
    ),
    Provider(
        brand = "Kimi",
        company = "月之暗面",
        defaultModel = "kimi-k3.1-pro",
        fallbackModels = listOf("kimi-k3.1-pro", "kimi-k3", "kimi-k2.6")
    ),
    Provider(
        brand = "通义千问",
        company = "阿里巴巴",
        defaultModel = "qwen3.8-flash",
        fallbackModels = listOf("qwen3.8-flash", "qwen3.8-max", "qwen-plus")
    ),
    Provider(
        brand = "星火",
        company = "科大讯飞",
        defaultModel = "x1",
        fallbackModels = listOf("x1"),
        thinkingSwitchable = false
    ),
    Provider(
        brand = "Gemini",
        company = "Google",
        defaultModel = "gemini-3.8-flash",
        fallbackModels = listOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-flash-latest"),
        thinkingSwitchable = false
    )
)

/** 单 Activity 内的页面，先用状态机切换，后续再换 TheRouter/Navigation。 */
sealed interface FriendScreen {
    data object List : FriendScreen
    data object Create : FriendScreen
    data class Chat(val friendId: String) : FriendScreen
    data object RoomCreate : FriendScreen
    data class RoomChat(val roomId: String) : FriendScreen
}

data class FriendUiState(
    val friends: List<Friend> = emptyList(),
    val screen: FriendScreen = FriendScreen.List,
    val search: String = "",
    val input: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
    val providers: List<Provider> = DEFAULT_PROVIDERS,
    val selectedBrand: String = DEFAULT_PROVIDERS.first().brand,
    val models: List<String> = emptyList(),
    val selectedModel: String = DEFAULT_PROVIDERS.first().defaultModel,
    val isLoadingModels: Boolean = false,
    /** 默认关闭深度思考：响应更快、token 更省；GLM-5 系列只能降档。 */
    val thinkingEnabled: Boolean = false,

    // 聊天室
    val rooms: List<Room> = emptyList(),
    val roomMessages: List<RoomMessage> = emptyList(),
    val roomInput: String = "",
    /** 正在回答的 AI（friendId） */
    val roomPending: Set<String> = emptySet(),
    /** 回答失败的 AI -> 错误信息 */
    val roomErrors: Map<String, String> = emptyMap(),
    val roomDraftName: String = "",
    val roomDraftMemberIds: Set<String> = emptySet()
) {
    val activeFriend: Friend?
        get() = (screen as? FriendScreen.Chat)
            ?.let { chat -> friends.firstOrNull { it.id == chat.friendId } }

    val selectedProvider: Provider?
        get() = providers.firstOrNull { it.brand == selectedBrand }

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

    val activeRoom: Room?
        get() = (screen as? FriendScreen.RoomChat)
            ?.let { roomScreen -> rooms.firstOrNull { it.id == roomScreen.roomId } }

    val activeRoomMessages: List<RoomMessage>
        get() = activeRoom?.let { room ->
            roomMessages.filter { it.roomId == room.id }
                .sortedWith(compareBy({ it.createdAt }, { it.seq }))
        }.orEmpty()
}
