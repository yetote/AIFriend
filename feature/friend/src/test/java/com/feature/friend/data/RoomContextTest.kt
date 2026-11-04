package com.feature.friend.data

import com.core.ai.model.ChatMessage
import com.feature.friend.model.Room
import com.feature.friend.model.RoomMessage
import com.feature.friend.model.RoomSender
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomContextTest {

    private fun message(
        id: String,
        sender: RoomSender,
        content: String,
        createdAt: Long,
        seq: Long
    ) = RoomMessage(
        id = id,
        roomId = "room-1",
        sender = sender,
        content = content,
        createdAt = createdAt,
        seq = seq
    )

    @Test
    fun `context keeps user messages and only own replies`() {
        val messages = listOf(
            message("1", RoomSender.User, "大家好", 1, 1),
            message("2", RoomSender.Ai("a"), "A 的回答", 2, 2),
            message("3", RoomSender.Ai("b"), "B 的回答", 3, 3),
            message("4", RoomSender.User, "继续", 4, 4)
        )

        val context = buildRoomContext(messages, friendId = "a")

        assertEquals(
            listOf(
                ChatMessage.user("大家好"),
                ChatMessage.assistant("A 的回答"),
                ChatMessage.user("继续")
            ),
            context
        )
    }

    @Test
    fun `context is sorted by time even when input is unordered`() {
        val messages = listOf(
            message("2", RoomSender.User, "第二条", 2, 2),
            message("1", RoomSender.User, "第一条", 1, 1)
        )

        val context = buildRoomContext(messages, friendId = "a")

        assertEquals(listOf("第一条", "第二条"), context.map { it.content })
    }

    @Test
    fun `system messages become room notifications in context`() {
        val system = message("s1", RoomSender.System, "A 加入了聊天室", 1, 1)
        val user = message("1", RoomSender.User, "大家好", 2, 2)

        assertEquals(system, system.toStored().toRoomMessage())
        assertEquals(
            listOf(
                ChatMessage.user("（聊天室通知）A 加入了聊天室"),
                ChatMessage.user("大家好")
            ),
            buildRoomContext(listOf(system, user), "a")
        )
    }

    @Test
    fun `new member only sees messages after joining`() {
        val messages = listOf(
            message("1", RoomSender.User, "旧消息", 1, 1),
            message("2", RoomSender.Ai("b"), "B 的回答", 2, 2),
            message("3", RoomSender.System, "A 加入了聊天室", 3, 3),
            message("4", RoomSender.User, "新消息", 4, 4)
        )

        val context = buildRoomContext(messages, friendId = "a", joinedSeq = 3)

        assertEquals(
            listOf(
                ChatMessage.user("（聊天室通知）A 加入了聊天室"),
                ChatMessage.user("新消息")
            ),
            context
        )
    }

    @Test
    fun `room and message mapping round trip`() {
        val room = Room(
            id = "r1",
            name = "评审团",
            memberIds = listOf("a", "b"),
            createdAt = 100L,
            memberJoinSeq = mapOf("a" to 0L, "b" to 7L)
        )
        assertEquals(room, room.toStored().toRoom())

        val message = message("m1", RoomSender.Ai("a"), "hi", 5, 1)
        assertEquals(message, message.toStored().toRoomMessage())
    }
}
