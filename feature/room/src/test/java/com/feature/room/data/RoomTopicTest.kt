package com.feature.room.data

import com.feature.room.model.RoomMessage
import com.feature.room.model.RoomSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomTopicTest {

    private fun message(
        seq: Long,
        sender: RoomSender,
        content: String,
        targetIds: Set<String>? = null
    ) = RoomMessage(
        id = "m$seq",
        roomId = "room-1",
        sender = sender,
        content = content,
        createdAt = seq,
        seq = seq,
        targetIds = targetIds
    )

    @Test
    fun `anchor picks the latest user message`() {
        val messages = listOf(
            message(1, RoomSender.User, "第一个话题"),
            message(2, RoomSender.Ai("a"), "接话"),
            message(3, RoomSender.User, "第二个话题")
        )

        assertEquals("第二个话题", RoomTopic.anchor(messages))
    }

    @Test
    fun `anchor ignores ai replies`() {
        val messages = listOf(
            message(1, RoomSender.User, "用户的需求"),
            message(2, RoomSender.Ai("a"), "AI 说了很多新东西"),
            message(3, RoomSender.Ai("b"), "另一个 AI 又扯了别的")
        )

        assertEquals("用户的需求", RoomTopic.anchor(messages))
    }

    @Test
    fun `anchor is null when the user never spoke`() {
        val messages = listOf(
            message(1, RoomSender.System, "欢迎"),
            message(2, RoomSender.Ai("a"), "大家好")
        )

        assertNull(RoomTopic.anchor(messages))
    }

    @Test
    fun `anchor skips messages the member cannot see`() {
        val messages = listOf(
            message(1, RoomSender.User, "只问 A 的问题", targetIds = setOf("a")),
            message(2, RoomSender.User, "公开的问题")
        )

        // A 能看到两轮，取最近的公开那条；B 看不到第一条，也只能取到公开那条
        assertEquals("公开的问题", RoomTopic.anchor(messages, friendId = "a"))
        assertEquals("公开的问题", RoomTopic.anchor(messages, friendId = "b"))
    }

    @Test
    fun `anchor respects the targeted round for its own member`() {
        val messages = listOf(
            message(1, RoomSender.User, "只问 A 的细节", targetIds = setOf("a"))
        )

        assertEquals("只问 A 的细节", RoomTopic.anchor(messages, friendId = "a"))
        assertNull(RoomTopic.anchor(messages, friendId = "b"))
    }

    @Test
    fun `anchorOrRoomPrompt falls back to the room prompt`() {
        val messages = listOf(message(1, RoomSender.Ai("a"), "大家好"))

        assertEquals("你是评委团", RoomTopic.anchorOrRoomPrompt(messages, "你是评委团"))
        assertNull(RoomTopic.anchorOrRoomPrompt(messages, "   "))
    }

    @Test
    fun `truncate keeps long topics short`() {
        val long = "很长的需求".repeat(80)

        val truncated = RoomTopic.truncate(long)

        assertTrue(truncated.length <= RoomTopic.ANCHOR_MAX_CHARS + 1)
        assertTrue(truncated.endsWith("…"))
    }

    @Test
    fun `truncate collapses whitespace`() {
        assertEquals("换行 和 空格", RoomTopic.truncate("换行\n和   空格"))
    }

    @Test
    fun `hint tells the speaker to stay on topic`() {
        val hint = RoomTopic.hint("要不要换工作", speakerName = "小谋")

        assertTrue(hint.contains("要不要换工作"))
        assertTrue(hint.contains("小谋"))
        assertTrue(hint.contains("不要另起新话题"))
    }

    @Test
    fun `hint works without a speaker name`() {
        val hint = RoomTopic.hint("要不要换工作")

        assertTrue(hint.contains("要不要换工作"))
        assertTrue(hint.contains("不要另起新话题"))
    }

    @Test
    fun `closing hint asks for a conclusion instead of more discussion`() {
        val hint = RoomTopic.hint("要不要换工作", speakerName = "小谋", closing = true)

        assertTrue(hint.contains("最后一轮"))
        assertTrue(hint.contains("结论"))
        assertTrue(hint.contains("【结束】"))
        // 收口时不该再要求「补充」，否则模型会继续往外扩
        assertTrue(hint.contains("不要再提出新的问题"))
    }

    @Test
    fun `closing hint still carries the topic`() {
        val hint = RoomTopic.hint("要不要换工作", closing = true)

        assertTrue(hint.contains("要不要换工作"))
    }

    @Test
    fun `non closing hint does not ask for the end marker`() {
        val hint = RoomTopic.hint("要不要换工作", speakerName = "小谋")

        assertFalse(hint.contains("【结束】"))
        assertFalse(hint.contains("最后一轮"))
    }
}
