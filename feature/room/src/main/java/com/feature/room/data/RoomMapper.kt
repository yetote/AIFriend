package com.feature.room.data

import com.core.ai.model.ChatMessage
import com.core.repository.storage.StoredRoom
import com.core.repository.storage.StoredRoomMessage
import com.feature.room.model.Room
import com.feature.room.model.RoomMessage
import com.feature.room.model.RoomSender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SENDER_USER = "user"
private const val SENDER_AI = "ai"
private const val SENDER_SYSTEM = "system"

fun Room.toStored(): StoredRoom = StoredRoom(
    id = id,
    name = name,
    memberIds = memberIds,
    createdAt = createdAt,
    prompt = prompt,
    discussionEnabled = discussionEnabled,
    discussionWindow = discussionWindow,
    sequentialSpeaking = sequentialSpeaking,
    pinned = pinned,
    avatarPath = avatarPath,
    memberJoinSeq = memberJoinSeq
)

fun StoredRoom.toRoom(): Room = Room(
    id = id,
    name = name,
    memberIds = memberIds,
    createdAt = createdAt,
    prompt = prompt,
    discussionEnabled = discussionEnabled,
    discussionWindow = discussionWindow,
    sequentialSpeaking = sequentialSpeaking,
    pinned = pinned,
    avatarPath = avatarPath,
    memberJoinSeq = memberJoinSeq
)

/**
 * 房间设定优先，其次才是好友自身的人格设定。
 * 末尾统一追加群聊规则，减少复读、抢话和换说法重复。
 */
private val ROOM_CHAT_RULES = """
群聊规则：
1. 你是房间成员之一，先理解前面成员已经说了什么，再补充新的信息、角度或理由。
2. 不要复读自己或别人上一轮的句式、结论和数字，也不要用换一种说法重复同一件事。
3. 如果确实没有新内容，直接表示同意，并补充一个新的理由，不要为了凑字数复述。
""".trim()

fun buildRoomSystemPrompt(roomPrompt: String, friendPrompt: String): String {
    val room = roomPrompt.trim()
    return buildString {
        if (room.isNotBlank()) {
            append(room).append("\n\n")
        }
        append(friendPrompt)
        append("\n\n")
        append(ROOM_CHAT_RULES)
    }
}

/** 把整屋消息导出成 Markdown，供系统分享使用。 */
fun buildRoomMarkdown(
    room: Room,
    messages: List<RoomMessage>,
    friendNames: Map<String, String>
): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return buildString {
        append("# ").append(room.name).append("\n\n")
        if (room.prompt.isNotBlank()) {
            append("> ").append(room.prompt.replace("\n", "\n> ")).append("\n\n")
        }
        messages.sortedWith(compareBy({ it.createdAt }, { it.seq })).forEach { message ->
            val time = formatter.format(Date(message.createdAt))
            when (val sender = message.sender) {
                RoomSender.User -> {
                    append("**我** · ").append(time).append("\n\n")
                    append(message.content).append("\n\n")
                }

                RoomSender.System -> {
                    append("> ").append(time).append(" · ").append(message.content).append("\n\n")
                }

                is RoomSender.Ai -> {
                    val name = friendNames[sender.friendId] ?: "已移除"
                    append("**").append(name).append("** · ").append(time).append("\n\n")
                    append(message.content).append("\n\n")
                }
            }
        }
    }
}

fun RoomMessage.toStored(): StoredRoomMessage = StoredRoomMessage(
    id = id,
    roomId = roomId,
    senderType = when (sender) {
        RoomSender.User -> SENDER_USER
        RoomSender.System -> SENDER_SYSTEM
        is RoomSender.Ai -> SENDER_AI
    },
    senderId = (sender as? RoomSender.Ai)?.friendId,
    content = content,
    createdAt = createdAt,
    seq = seq,
    targetIds = targetIds
)

fun StoredRoomMessage.toRoomMessage(): RoomMessage = RoomMessage(
    id = id,
    roomId = roomId,
    sender = when (senderType) {
        SENDER_USER -> RoomSender.User
        SENDER_SYSTEM -> RoomSender.System
        else -> RoomSender.Ai(senderId.orEmpty())
    },
    content = content,
    createdAt = createdAt,
    seq = seq,
    targetIds = targetIds
)

/**
 * 某个 AI 在房间里的上下文：
 * 1. 只取加入位置之后的消息；
 * 2. 默认（otherWindowRounds = null）方案 B：保留所有用户消息、系统通知和它自己的回复；
 * 3. 讨论模式（otherWindowRounds != null）：额外保留其他成员最近 N 轮的发言，
 *    以「（某成员 的发言）」的 user 消息注入，避免模型混淆身份。
 */
fun buildRoomContext(
    messages: List<RoomMessage>,
    friendId: String,
    joinedSeq: Long = 0L,
    otherWindowRounds: Int? = null,
    friendNames: Map<String, String> = emptyMap()
): List<ChatMessage> {
    val sorted = messages.sortedWith(compareBy({ it.createdAt }, { it.seq }))

    // 按用户消息划分轮次；用户消息带了 targetIds 时，这一轮只对目标成员可见
    val roundOf = HashMap<Long, Int>(sorted.size)
    val roundVisible = HashMap<Int, Boolean>()
    var round = 0
    sorted.forEach { message ->
        if (message.sender == RoomSender.User) {
            round++
            val targets = message.targetIds
            roundVisible[round] = targets == null || friendId in targets
        }
        roundOf[message.seq] = round
    }

    fun visibleToViewer(message: RoomMessage): Boolean {
        if (message.sender == RoomSender.System) return true
        return roundVisible[roundOf[message.seq] ?: 0] ?: true
    }

    if (otherWindowRounds == null) {
        return sorted.asSequence()
            .filter { it.seq >= joinedSeq }
            .filter { message -> visibleToViewer(message) && isVisible(message, friendId) }
            .map { message -> toChatMessage(message, friendId, friendNames) }
            .toList()
    }

    // 其他人的发言只保留最近 N 轮
    val firstRound = (round - otherWindowRounds + 1).coerceAtLeast(0)

    return sorted.asSequence()
        .filter { it.seq >= joinedSeq }
        .filter { message ->
            visibleToViewer(message) &&
                (isVisible(message, friendId) ||
                    (roundOf[message.seq] ?: 0) >= firstRound)
        }
        .map { message -> toChatMessage(message, friendId, friendNames) }
        .toList()
}

private fun isVisible(message: RoomMessage, friendId: String): Boolean =
    message.sender == RoomSender.User ||
        message.sender == RoomSender.System ||
        (message.sender as? RoomSender.Ai)?.friendId == friendId

private fun toChatMessage(
    message: RoomMessage,
    friendId: String,
    friendNames: Map<String, String>
): ChatMessage = when (val sender = message.sender) {
    RoomSender.User -> ChatMessage.user(message.content)
    RoomSender.System -> ChatMessage.user("（聊天室通知）${message.content}")
    is RoomSender.Ai -> if (sender.friendId == friendId) {
        ChatMessage.assistant(message.content)
    } else {
        ChatMessage.user("（${friendNames[sender.friendId] ?: "群成员"} 的发言）${message.content}")
    }
}
