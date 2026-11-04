package com.feature.room.data
import com.feature.room.model.Room
import com.feature.room.model.RoomMessage
import com.feature.room.model.RoomSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMapperTest {

    @Test
    fun `room prompt is prepended to friend prompt`() {
        assertEquals("群规\n\n人格", buildRoomSystemPrompt(" 群规 ", "人格"))
    }

    @Test
    fun `blank room prompt keeps friend prompt`() {
        assertEquals("人格", buildRoomSystemPrompt("", "人格"))
        assertEquals("人格", buildRoomSystemPrompt("   ", "人格"))
    }

    @Test
    fun `each ai sees user and its own replies only`() {
        val messages = listOf(
            RoomMessage("1", "r", RoomSender.User, "大家好", 1L, 1L),
            RoomMessage("2", "r", RoomSender.Ai("a"), "A 的回答", 2L, 2L),
            RoomMessage("3", "r", RoomSender.Ai("b"), "B 的回答", 3L, 3L),
            RoomMessage("4", "r", RoomSender.System, "C 加入了聊天室", 4L, 4L)
        )

        val context = buildRoomContext(messages, friendId = "a")

        assertEquals(listOf("user", "assistant", "user"), context.map { it.role })
        assertEquals(listOf("大家好", "A 的回答", "（聊天室通知）C 加入了聊天室"), context.map { it.content })
    }

    @Test
    fun `discussion window limits other members replies`() {
        val messages = listOf(
            RoomMessage("1", "r", RoomSender.User, "第一轮", 1L, 1L),
            RoomMessage("2", "r", RoomSender.Ai("a"), "A1", 2L, 2L),
            RoomMessage("3", "r", RoomSender.Ai("b"), "B1", 3L, 3L),
            RoomMessage("4", "r", RoomSender.User, "第二轮", 4L, 4L),
            RoomMessage("5", "r", RoomSender.Ai("b"), "B2", 5L, 5L),
            RoomMessage("6", "r", RoomSender.Ai("a"), "A2", 6L, 6L)
        )
        val names = mapOf("a" to "A", "b" to "B")

        val window1 = buildRoomContext(
            messages = messages,
            friendId = "a",
            otherWindowRounds = 1,
            friendNames = names
        )
        assertEquals(
            listOf("第一轮", "A1", "第二轮", "（B 的发言）B2", "A2"),
            window1.map { it.content }
        )

        val window2 = buildRoomContext(
            messages = messages,
            friendId = "a",
            otherWindowRounds = 2,
            friendNames = names
        )
        assertEquals(
            listOf("第一轮", "A1", "（B 的发言）B1", "第二轮", "（B 的发言）B2", "A2"),
            window2.map { it.content }
        )
    }

    @Test
    fun `markdown export includes header senders and system notices`() {
        val room = Room(
            id = "r",
            name = "评审团",
            memberIds = listOf("a"),
            createdAt = 0L
        )
        val messages = listOf(
            RoomMessage("1", "r", RoomSender.User, "大家好", 1L, 1L),
            RoomMessage("2", "r", RoomSender.Ai("a"), "我的结论", 2L, 2L),
            RoomMessage("3", "r", RoomSender.System, "B 加入了聊天室", 3L, 3L)
        )

        val markdown = buildRoomMarkdown(room, messages, mapOf("a" to "A"))

        assertTrue(markdown.startsWith("# 评审团"))
        assertTrue(markdown.contains("**我**"))
        assertTrue(markdown.contains("**A**"))
        assertTrue(markdown.contains("我的结论"))
        assertTrue(markdown.contains("> "))
    }
}
