package com.feature.room.data
import com.core.ai.model.ChatMessage
import com.feature.room.model.Room
import com.feature.room.model.RoomMessage
import com.feature.room.model.RoomSender
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomContextTest {

    private val names = mapOf("a" to "A", "b" to "B", "c" to "C")

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
    fun `discussion window keeps only recent rounds of other members`() {
        val messages = listOf(
            message("1", RoomSender.User, "第一轮", 1, 1),
            message("2", RoomSender.Ai("b"), "B 在第一轮", 2, 2),
            message("3", RoomSender.User, "第二轮", 3, 3),
            message("4", RoomSender.Ai("b"), "B 在第二轮", 4, 4),
            message("5", RoomSender.User, "第三轮", 5, 5),
            message("6", RoomSender.Ai("b"), "B 在第三轮", 6, 6)
        )

        // 窗口 1：用户消息始终保留，另外只保留最近一轮别人的发言
        val context = buildRoomContext(
            messages = messages,
            friendId = "a",
            otherWindowRounds = 1,
            friendNames = names
        )

        assertEquals(
            listOf(
                ChatMessage.user("第一轮"),
                ChatMessage.user("第二轮"),
                ChatMessage.user("第三轮"),
                ChatMessage.user("（B 的发言）B 在第三轮")
            ),
            context
        )
    }

    @Test
    fun `burst as rounds makes every other reply its own round`() {
        // 同一条用户消息之后连续 4 条 AI 发言：不开启 burstAsRounds 时窗口形同虚设
        val messages = listOf(
            message("1", RoomSender.User, "开个话题", 1, 1),
            message("2", RoomSender.Ai("b"), "B1", 2, 2),
            message("3", RoomSender.Ai("c"), "C1", 3, 3),
            message("4", RoomSender.Ai("b"), "B2", 4, 4),
            message("5", RoomSender.Ai("c"), "C2", 5, 5)
        )

        val withoutBurst = buildRoomContext(
            messages = messages,
            friendId = "a",
            otherWindowRounds = 1,
            friendNames = names
        )
        assertEquals(
            listOf(
                ChatMessage.user("开个话题"),
                ChatMessage.user("（B 的发言）B1"),
                ChatMessage.user("（C 的发言）C1"),
                ChatMessage.user("（B 的发言）B2"),
                ChatMessage.user("（C 的发言）C2")
            ),
            withoutBurst
        )

        val withBurst = buildRoomContext(
            messages = messages,
            friendId = "a",
            otherWindowRounds = 1,
            friendNames = names,
            burstAsRounds = true
        )
        assertEquals(
            listOf(
                ChatMessage.user("开个话题"),
                ChatMessage.user("（C 的发言）C2")
            ),
            withBurst
        )
    }

    @Test
    fun `burst as rounds keeps own replies out of the round boundaries`() {
        val messages = listOf(
            message("1", RoomSender.User, "开个话题", 1, 1),
            message("2", RoomSender.Ai("a"), "A 自己说的", 2, 2),
            message("3", RoomSender.Ai("b"), "B 说的", 3, 3)
        )

        val context = buildRoomContext(
            messages = messages,
            friendId = "a",
            otherWindowRounds = 1,
            friendNames = names,
            burstAsRounds = true
        )

        assertEquals(
            listOf(
                ChatMessage.user("开个话题"),
                ChatMessage.assistant("A 自己说的"),
                ChatMessage.user("（B 的发言）B 说的")
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
