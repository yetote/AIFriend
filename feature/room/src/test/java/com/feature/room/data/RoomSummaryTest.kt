package com.feature.room.data

import com.feature.room.model.RoomMessage
import com.feature.room.model.RoomSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSummaryTest {

    private fun msg(
        seq: Long,
        sender: RoomSender,
        content: String,
        targetIds: Set<String>? = null,
    ) = RoomMessage(
        id = "m-$seq",
        roomId = "r1",
        sender = sender,
        content = content,
        createdAt = seq * 100L,
        seq = seq,
        targetIds = targetIds,
    )

    @Test
    fun `消息总数不足活跃窗口与触发阈值时不触发自动摘要`() {
        val messages = (1L..10L).map { i ->
            msg(i, if (i % 2L == 1L) RoomSender.User else RoomSender.Ai("a"), "短句 $i")
        }
        // 10 条里留最近 8 条，只剩 2 条较早消息，低于 MIN_BATCH_MESSAGES(6) 且 token 很少
        assertNull(RoomSummary.pickBatchToSummarize(messages, summaryUntilSeq = 0L, force = false))
        // 手动强制更新只要 >=2 条即可触发
        val forced = RoomSummary.pickBatchToSummarize(messages, summaryUntilSeq = 0L, force = true)
        assertNotNull(forced)
        assertEquals(2, forced!!.messages.size)
        assertEquals(2L, forced.throughSeq)
    }

    @Test
    fun `较早公开消息达到阈值时按序挑出且保留最近活跃窗口`() {
        val messages = (1L..16L).map { i ->
            msg(i, if (i % 2L == 1L) RoomSender.User else RoomSender.Ai("a"), "讨论内容 $i")
        }
        val batch = RoomSummary.pickBatchToSummarize(messages, summaryUntilSeq = 0L)
        assertNotNull(batch)
        // 16 - 8 = 8 条进入待摘要批次，throughSeq = 8
        assertEquals(8, batch!!.messages.size)
        assertEquals(8L, batch.throughSeq)

        // 吸收到 seq=8 之后，再调一次应返回 null
        assertNull(RoomSummary.pickBatchToSummarize(messages, summaryUntilSeq = 8L))
    }

    @Test
    fun `定向 @ 私有轮次绝不进入共享纪要`() {
        val messages = buildList {
            add(msg(1, RoomSender.User, "公开讨论1"))
            add(msg(2, RoomSender.Ai("a"), "公开回复1"))
            add(msg(3, RoomSender.User, "只问 A 的私密问题", targetIds = setOf("a")))
            add(msg(4, RoomSender.Ai("a"), "A 的私密回答"))
            for (seq in 5L..16L) {
                add(msg(seq, if (seq % 2L == 1L) RoomSender.User else RoomSender.Ai("b"), "公开 $seq"))
            }
        }
        val batch = RoomSummary.pickBatchToSummarize(messages, summaryUntilSeq = 0L)
        assertNotNull(batch)
        assertTrue(
            "私密轮次不能进入共享纪要：${batch!!.messages.map { it.content }}",
            batch.messages.none { it.content.contains("私密") },
        )
    }

    @Test
    fun `共享纪要注入 system prompt 且 buildRoomContext 跳过已摘要公开消息`() {
        val prompt = buildRoomSystemPrompt(
            roomPrompt = "评审团",
            friendPrompt = "产品经理",
            sharedSummary = "- 已确认采用方案 A",
        )
        assertTrue(prompt.contains("【聊天室共享纪要（较早对话摘要）】"))
        assertTrue(prompt.contains("已确认采用方案 A"))

        val messages = listOf(
            msg(1, RoomSender.User, "很早的公开需求"),
            msg(2, RoomSender.Ai("a"), "很早的公开回复"),
            msg(3, RoomSender.User, "私密 @ A", targetIds = setOf("a")),
            msg(4, RoomSender.Ai("a"), "A 的私密答复"),
            msg(5, RoomSender.User, "最新公开问题"),
            msg(6, RoomSender.Ai("a"), "最新公开答复"),
        )
        val ctx = buildRoomContext(
            messages = messages,
            friendId = "a",
            summaryUntilSeq = 4L,
        )
        val texts = ctx.map { it.content }
        assertFalse("已摘要的公开消息应跳过", texts.contains("很早的公开需求"))
        assertFalse("已摘要的公开回复应跳过", texts.contains("很早的公开回复"))
        assertTrue("定向私密消息未进纪要，仍应保留", texts.contains("私密 @ A"))
        assertTrue("摘要水位之后的新消息照常保留", texts.contains("最新公开问题"))
        assertTrue(texts.contains("最新公开答复"))
    }

    @Test
    fun `纪要提示词包含已有纪要并截断过长输出`() {
        val batch = RoomSummary.Batch(
            messages = listOf(msg(1, RoomSender.User, "我们确定下周发布")),
            throughSeq = 1L,
        )
        val prompt = RoomSummary.buildSummaryPrompt(
            roomName = "发版群",
            roomPrompt = "协调发版节奏",
            existingSummary = "- 目标版本 1.0",
            batch = batch,
            friendNames = mapOf("a" to "小谋"),
        )
        assertTrue(prompt.contains("【已有纪要】"))
        assertTrue(prompt.contains("目标版本 1.0"))
        assertTrue(prompt.contains("下周发布"))

        val longText = "a".repeat(RoomSummary.MAX_SUMMARY_CHARS + 50)
        val sanitized = RoomSummary.sanitize("```markdown\n$longText\n```")
        assertEquals(RoomSummary.MAX_SUMMARY_CHARS, sanitized.length)
        assertTrue(sanitized.endsWith("…"))
    }
}
