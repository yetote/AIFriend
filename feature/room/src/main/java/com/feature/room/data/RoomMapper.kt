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
 * 房间设定用来约定「你们在一个群里、各自发言」这类共同规则。
 */
fun buildRoomSystemPrompt(roomPrompt: String, friendPrompt: String): String {
    val room = roomPrompt.trim()
    return if (room.isBlank()) friendPrompt else "$room\n\n$friendPrompt"
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
    seq = seq
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
    seq = seq
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
    if (otherWindowRounds == null) {
        return sorted.asSequence()
            .filter { it.seq >= joinedSeq }
            .filter { message -> isVisible(message, friendId) }
            .map { message -> toChatMessage(message, friendId, friendNames) }
            .toList()
    }

    // 按用户消息划分轮次，其他人的发言只保留最近 N 轮
    val roundOf = HashMap<Long, Int>(sorted.size)
    var round = 0
    sorted.forEach { message ->
        if (message.sender == RoomSender.User) round++
        roundOf[message.seq] = round
    }
    val firstRound = (round - otherWindowRounds + 1).coerceAtLeast(0)

    return sorted.asSequence()
        .filter { it.seq >= joinedSeq }
        .filter { message ->
            isVisible(message, friendId) ||
                (roundOf[message.seq] ?: 0) >= firstRound
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
