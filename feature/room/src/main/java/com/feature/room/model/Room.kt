package com.feature.room.model

/** 单个房间最多容纳的 AI 数量，避免一次提问触发过多请求。 */
const val MAX_ROOM_MEMBERS = 6

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
    /** 开启后每个 AI 能看到其他成员最近 [discussionWindow] 轮的发言。 */
    val discussionEnabled: Boolean = false,
    /** 其他人的发言只保留最近几轮（1..5）。 */
    val discussionWindow: Int = 1,
    /** 讨论模式下是否按成员顺序依次发言（后说的能看到本轮前面的回复）。 */
    val sequentialSpeaking: Boolean = false,
    /** 是否置顶；置顶房间排在列表前面。 */
    val pinned: Boolean = false,
    /** 房间头像的本地文件路径。 */
    val avatarPath: String? = null,
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
    val seq: Long,
    /**
     * 这条用户消息 @ 的成员 ID。
     * null 表示全员可见；非空表示这一轮只对这些成员可见。
     */
    val targetIds: Set<String>? = null
)
