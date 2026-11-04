package com.feature.friend.model

/**
 * 聊天室：把多个好友拉进一个房间，用户每发一条消息，房间内所有 AI 各自回答。
 */
data class Room(
    val id: String,
    val name: String,
    val memberIds: List<String>,
    val createdAt: Long
)

sealed interface RoomSender {
    data object User : RoomSender
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
