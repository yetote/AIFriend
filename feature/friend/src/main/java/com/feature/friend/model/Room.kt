package com.feature.friend.model

/**
 * 聊天室：把多个好友拉进一个房间，用户每发一条消息，房间内所有 AI 各自回答。
 */
data class Room(
    val id: String,
    val name: String,
    val memberIds: List<String>,
    val createdAt: Long,
    /**
     * 成员加入时的消息 seq（0 表示房间创建时就已加入）。
     * 新加入的 AI 只看得到这个位置之后的内容，之前的用户历史不会同步给它。
     */
    val memberJoinSeq: Map<String, Long> = emptyMap()
)

sealed interface RoomSender {
    data object User : RoomSender

    /** 系统提示，例如「你邀请 A 加入了聊天室」，只用于展示，不进入任何 AI 的上下文。 */
    data object System : RoomSender

    data class Ai(val friendId: String) : RoomSender
}

/**
 * 房间消息。每个 AI 的上下文 = 房间内所有 User 消息 + 它自己的 Ai 消息，
 * 不包含其他 AI 的回复（方案 B）。
 */
data class RoomMessage(
    val id: String,
    val roomId: String,
    val sender: RoomSender,
    val content: String,
    val createdAt: Long,
    val seq: Long
)
