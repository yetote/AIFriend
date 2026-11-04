package com.feature.friend.data

import com.core.ai.model.ChatMessage
import com.core.repository.storage.StoredRoom
import com.core.repository.storage.StoredRoomMessage
import com.feature.friend.model.Room
import com.feature.friend.model.RoomMessage
import com.feature.friend.model.RoomSender

private const val SENDER_USER = "user"
private const val SENDER_AI = "ai"
private const val SENDER_SYSTEM = "system"

fun Room.toStored(): StoredRoom = StoredRoom(
    id = id,
    name = name,
    memberIds = memberIds,
    createdAt = createdAt,
    prompt = prompt,
    memberJoinSeq = memberJoinSeq
)

fun StoredRoom.toRoom(): Room = Room(
    id = id,
    name = name,
    memberIds = memberIds,
    createdAt = createdAt,
    prompt = prompt,
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
 * 某个 AI 在房间里的上下文（方案 B + 加入位置过滤）：
 * 1. 只取加入位置之后的消息，新成员看不到加入前的用户历史；
 * 2. 保留所有用户消息、系统通知，以及它自己的回复，过滤其他 AI 的回复；
 * 3. 系统通知以 user 角色注入并加「（聊天室通知）」前缀，兼容所有厂商。
 */
fun buildRoomContext(
    messages: List<RoomMessage>,
    friendId: String,
    joinedSeq: Long = 0L
): List<ChatMessage> =
    messages.asSequence()
        .sortedWith(compareBy({ it.createdAt }, { it.seq }))
        .filter { it.seq >= joinedSeq }
        .filter { message ->
            message.sender == RoomSender.User ||
                message.sender == RoomSender.System ||
                (message.sender as? RoomSender.Ai)?.friendId == friendId
        }
        .map { message ->
            when (message.sender) {
                RoomSender.User -> ChatMessage.user(message.content)
                RoomSender.System -> ChatMessage.user("（聊天室通知）${message.content}")
                is RoomSender.Ai -> ChatMessage.assistant(message.content)
            }
        }
        .toList()
