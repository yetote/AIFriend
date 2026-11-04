package com.feature.friend.model

/**
 * 聊天室：把多个好友拉进一个房间，用户发消息后由选中的成员各自回答（默认全员）。
 */
data class Room(
    val id: String,
    val name: String,
    val memberIds: List<String>,
    val createdAt: Long,
    /**
     * 房间级设定，注入到每位成员的 system prompt 前面。
     * 例如「你们是一个评审团，各自给结论，不要互相重复」。
     */
    val prompt: String = "",
    /**
     * 成员加入时的消息 seq（0 表示房间创建时就已加入）。
     * 新加入的 AI 只看得到这个位置之后的内容，之前的用户历史不会同步给它。
     */
    val memberJoinSeq: Map<String, Long> = emptyMap()
)

sealed interface RoomSender {
    data object User : RoomSender

    /** 系统提示，例如「你邀请 A 加入了聊天室」；会以「（聊天室通知）」的形式同步给房间内的 AI。 */
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
