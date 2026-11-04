package com.ui.friend.data

/** 首页聊天室卡片 */
data class UIRoomData(
    val id: String,
    val name: String,
    val memberCount: Int
)

/** 房间成员（创建页 / 成员弹窗 / 消息署名共用） */
data class UIRoomMember(
    val id: String,
    val name: String,
    val avatarPath: String? = null
)

/** 房间消息的展示模型 */
data class UIRoomMessage(
    val id: String,
    val isUser: Boolean,
    val isSystem: Boolean = false,
    val senderId: String? = null,
    val senderName: String? = null,
    val avatarPath: String? = null,
    val content: String
)
