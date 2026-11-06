package com.ui.room.data

import com.ui.common.UIGroupMember

/** 首页聊天室卡片 */
data class UIRoomData(
    val id: String,
    val name: String,
    val memberCount: Int,
    val pinned: Boolean = false,
    val avatarPath: String? = null,
    /**
     * 成员的头像信息，用来拼群头像（取前四个）。
     * 只放画图需要的字段，不把 Friends 整个传进 UI 层。
     */
    val memberAvatars: List<UIGroupMember> = emptyList()
)

/** 房间成员（创建页 / 成员弹窗 / 消息署名共用） */
data class UIRoomMember(
    val id: String,
    val name: String,
    val avatarPath: String? = null,
    val role: String = "",
    val model: String = ""
)

/** 房间消息的展示模型 */
data class UIRoomMessage(
    val id: String,
    val isUser: Boolean,
    val isSystem: Boolean = false,
    val senderId: String? = null,
    val senderName: String? = null,
    val avatarPath: String? = null,
    val content: String,
    val createdAt: Long = 0L
)
