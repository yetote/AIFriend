package com.feature.room.data

import com.feature.room.model.RoomMessage
import com.feature.room.model.RoomSender
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoChatPolicyTest {

    private val names = mapOf("a" to "阿岚", "b" to "小北", "c" to "陈默")

    @Test
    fun `planSpeakers keeps every member exactly once`() {
        val members = listOf("a", "b", "c", "d")

        val order = AutoChatPolicy.planSpeakers(members, Random(7))

        assertEquals(members.toSet(), order.toSet())
        assertEquals(members.size, order.size)
    }

    @Test
    fun `planSpeakers can produce an order different from member order`() {
        val members = (1..6).map { "m$it" }
        val orders = (1..20).map { seed ->
            AutoChatPolicy.planSpeakers(members, Random(seed))
        }

        assertTrue(
            "随机排序应该至少有一次不同于成员原始顺序",
            orders.any { it != members }
        )
        orders.forEach { assertEquals(members.toSet(), it.toSet()) }
    }

    @Test
    fun `planSpeakers handles single and empty members`() {
        assertEquals(listOf("a"), AutoChatPolicy.planSpeakers(listOf("a")))
        assertEquals(emptyList<String>(), AutoChatPolicy.planSpeakers(emptyList()))
    }

    @Test
    fun `pickNextSpeaker uses mentioned member`() {
        val next = AutoChatPolicy.pickNextSpeaker(
            candidates = listOf("a", "b", "c"),
            lastSpeakerId = "a",
            mentionedFriendId = "c",
            random = Random(1)
        )

        assertEquals("c", next)
    }

    @Test
    fun `pickNextSpeaker never repeats the last speaker`() {
        repeat(30) { seed ->
            val next = AutoChatPolicy.pickNextSpeaker(
                candidates = listOf("a", "b", "c"),
                lastSpeakerId = "b",
                random = Random(seed)
            )
            assertTrue("不能连续由同一个人发言，实际=$next", next != "b")
        }
    }

    @Test
    fun `pickNextSpeaker ignores mention of the last speaker`() {
        // 自己 @ 自己不算交接，应回落到其他人
        val next = AutoChatPolicy.pickNextSpeaker(
            candidates = listOf("a", "b"),
            lastSpeakerId = "a",
            mentionedFriendId = "a",
            random = Random(3)
        )

        assertEquals("b", next)
    }

    @Test
    fun `pickNextSpeaker falls back to the only member`() {
        assertEquals(
            "a",
            AutoChatPolicy.pickNextSpeaker(listOf("a"), lastSpeakerId = "a")
        )
        assertNull(AutoChatPolicy.pickNextSpeaker(emptyList(), lastSpeakerId = null))
    }

    @Test
    fun `findMentionedMember reads the trailing mention`() {
        val reply = "我觉得阿岚说得对，不过还要看成本。小北 你怎么看？@小北"

        assertEquals("b", AutoChatPolicy.findMentionedMember(reply, names))
    }

    @Test
    fun `findMentionedMember prefers the longest name`() {
        val overlapping = mapOf("x" to "小北", "y" to "小北极")

        assertEquals("y", AutoChatPolicy.findMentionedMember("@小北极 你来", overlapping))
    }

    @Test
    fun `findMentionedMember returns null when nobody is mentioned`() {
        assertNull(AutoChatPolicy.findMentionedMember("没有提到任何人", names))
        assertNull(AutoChatPolicy.findMentionedMember("@不存在的人", names))
        assertNull(AutoChatPolicy.findMentionedMember("", names))
        assertNull(AutoChatPolicy.findMentionedMember("@阿岚", emptyMap()))
    }

    @Test
    fun `findMentionedMember ignores an at sign inside a word`() {
        // 邮箱里的 @ 不应被当成交接
        assertNull(AutoChatPolicy.findMentionedMember("发我邮箱 me@a.com", names))
    }

    @Test
    fun `ending reply stops the loop`() {
        assertTrue(AutoChatPolicy.isEndingReply("那就这样定了吧 [END]"))

        assertFalse(
            AutoChatPolicy.shouldContinue(
                completedTurns = 1,
                maxTurns = 6,
                speakerCount = 3,
                lastReply = "结论已定 [END]"
            )
        )
    }

    @Test
    fun `stripEndMarker removes the marker from the bubble text`() {
        assertEquals("那就这样定了吧", AutoChatPolicy.stripEndMarker("那就这样定了吧【结束】"))
        assertEquals("聊完了", AutoChatPolicy.stripEndMarker("聊完了 [END]"))
        assertEquals("没什么可删的", AutoChatPolicy.stripEndMarker("没什么可删的"))
        assertEquals("", AutoChatPolicy.stripEndMarker("【结束】"))
    }

    @Test
    fun `shouldContinue stops at the configured turn limit`() {
        assertTrue(
            AutoChatPolicy.shouldContinue(completedTurns = 5, maxTurns = 6, speakerCount = 3)
        )
        assertFalse(
            AutoChatPolicy.shouldContinue(completedTurns = 6, maxTurns = 6, speakerCount = 3)
        )
    }

    @Test
    fun `shouldContinue needs at least two members`() {
        assertFalse(
            AutoChatPolicy.shouldContinue(completedTurns = 0, maxTurns = 6, speakerCount = 1)
        )
    }

    @Test
    fun `sanitizeTurns clamps into range`() {
        assertEquals(AutoChatPolicy.TURN_RANGE.first, AutoChatPolicy.sanitizeTurns(0))
        assertEquals(AutoChatPolicy.TURN_RANGE.last, AutoChatPolicy.sanitizeTurns(999))
        assertEquals(8, AutoChatPolicy.sanitizeTurns(8))
    }
}
