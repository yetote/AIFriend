package com.feature.friend.data

import com.core.ai.model.ChatMessage
import com.core.repository.storage.StoredRoom
import com.core.repository.storage.StoredRoomMessage
import com.feature.friend.model.Room
import com.feature.friend.model.RoomMessage
import com.feature.friend.model.RoomSender

private const val SENDER_USER = "user"
private const val SENDER_AI = "ai"

fun Room.toStored(): StoredRoom = StoredRoom(
    id = id,
    name = name,
    memberIds = memberIds,
    createdAt = createdAt
)

fun StoredRoom.toRoom(): Room = Room(
    id = id,
    name = name,
    memberIds = memberIds,
    createdAt = createdAt
)

fun RoomMessage.toStored(): StoredRoomMessage = StoredRoomMessage(
    id = id,
    roomId = roomId,
    senderType = if (sender == RoomSender.User) SENDER_USER else SENDER_AI,
    senderId = (sender as? RoomSender.Ai)?.friendId,
    content = content,
    createdAt = createdAt,
    seq = seq
)

fun StoredRoomMessage.toRoomMessage(): RoomMessage = RoomMessage(
    id = id,
    roomId = roomId,
    sender = if (senderType == SENDER_USER) {
        RoomSender.User
    } else {
        RoomSender.Ai(senderId.orEmpty())
    },
    content = content,
    createdAt = createdAt,
    seq = seq
)

/**
 * 方案 B：某个 AI 在房间里的上下文 = 所有用户消息 + 它自己的回复，按时间排序。
 * 其他 AI 的回复被过滤掉，避免互相污染。
 */
fun buildRoomContext(messages: List<RoomMessage>, friendId: String): List<ChatMessage> =
    messages.asSequence()
        .sortedWith(compareBy({ it.createdAt }, { it.seq }))
        .filter { message ->
            message.sender == RoomSender.User ||
                (message.sender as? RoomSender.Ai)?.friendId == friendId
        }
        .map { message ->
            if (message.sender == RoomSender.User) {
                ChatMessage.user(message.content)
            } else {
                ChatMessage.assistant(message.content)
            }
        }
        .toList()
