package com.feature.werewolf.engine

import com.feature.werewolf.game.WerewolfTurnLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一局的节奏控制。
 *
 * 这里最容易错的两点都不会抛异常：
 * 1. **超时之后思考没真的关掉** —— 那个座位下一步照样慢，用户以为是设置没生效；
 * 2. **关掉之后再也开不回来** —— 面板上的开关点了没反应。
 */
class WerewolfPacingTest {

    @Test
    fun `每个档位给思考秒数，发言段有宽松的安全上限`() {
        // 发言段的上限是安全网：把思考写进正文的模型会让看门狗误以为已在发言，
        // 发言段不限的话思考就没了上限（实测夜聊能想二十分钟）
        assertEquals(30_000L, WerewolfTurnLimit.FAST.decisionMs)
        assertEquals(60_000L, WerewolfTurnLimit.FAST.speechMs)
        assertEquals(60_000L, WerewolfTurnLimit.STANDARD.decisionMs)
        assertEquals(90_000L, WerewolfTurnLimit.STANDARD.speechMs)
        assertEquals(150_000L, WerewolfTurnLimit.RELAXED.speechMs)
        // 0 是「不设限」而不是「立刻超时」——写反了整局会瞬间全走兜底
        assertEquals(0L, WerewolfTurnLimit.UNLIMITED.decisionMs)
        assertEquals(0L, WerewolfTurnLimit.UNLIMITED.speechMs)
    }

    @Test
    fun `超时关掉的座位，这一局剩下的步骤都不再开思考`() {
        val pacing = WerewolfPacing()
        assertTrue("一开始按角色设定走", pacing.thinkingAllowed(seat = 3, roleDefault = true))

        pacing.disableThinking(3)
        assertFalse("关掉之后不能再开", pacing.thinkingAllowed(seat = 3, roleDefault = true))
        assertEquals(setOf(3), pacing.thinkingOff.value)
        // 别人不受影响：一处超时不该让全场都不思考了
        assertTrue(pacing.thinkingAllowed(seat = 4, roleDefault = true))
    }

    @Test
    fun `手动打开只是恢复角色设定，角色本来没开的还是不开`() {
        val pacing = WerewolfPacing(thinkingOff = setOf(3))
        pacing.setThinking(3, on = true)
        assertTrue("角色本来开着，关掉之后可以点回来", pacing.thinkingAllowed(3, roleDefault = true))
        assertFalse("角色本来关着，点了也不该开", pacing.thinkingAllowed(4, roleDefault = false))
        assertEquals(emptySet<Int>(), pacing.thinkingOff.value)
    }

    @Test
    fun `账本分别记最近一次、累计、步数与超时次数`() {
        val pacing = WerewolfPacing()
        pacing.record(seat = 3, elapsedMs = 1_000L, kind = TurnKind.DECISION, timedOut = false)
        pacing.record(seat = 3, elapsedMs = 3_000L, kind = TurnKind.DECISION, timedOut = true)

        val timing = pacing.timings.value.getValue(3)
        assertEquals("最近一次", 3_000L, timing.lastMs)
        assertEquals("累计", 4_000L, timing.totalMs)
        assertEquals("步数", 2, timing.turns)
        assertEquals("平均", 2_000L, timing.averageMs)
        // 超时次数单独记：超时那一次的耗时也进了累计，靠耗时反推不出来
        assertEquals("超时次数", 1, timing.timeouts)
    }

    @Test
    fun `开新局清账，续档只接回档位与名单`() {
        val pacing = WerewolfPacing(limit = WerewolfTurnLimit.FAST, thinkingOff = setOf(2))
        pacing.record(seat = 2, elapsedMs = 5_000L, kind = TurnKind.SPEECH, timedOut = true)

        pacing.reset(limit = WerewolfTurnLimit.RELAXED)
        assertEquals(WerewolfTurnLimit.RELAXED, pacing.currentLimit)
        assertEquals("新局一个都不能关", emptySet<Int>(), pacing.thinkingOff.value)
        assertEquals("新局账本从零开始", emptyMap<Int, SeatTiming>(), pacing.timings.value)

        pacing.restore(limit = WerewolfTurnLimit.UNLIMITED, thinkingOff = setOf(5))
        assertEquals(WerewolfTurnLimit.UNLIMITED, pacing.currentLimit)
        assertEquals(setOf(5), pacing.thinkingOff.value)
        assertEquals("续档不动账本（它本来就没落档）", emptyMap<Int, SeatTiming>(), pacing.timings.value)
    }

    @Test
    fun `改档位只影响之后的请求`() {
        val pacing = WerewolfPacing()
        // 已经在跑的那一步用的是发出时的值，所以这里断言的是「下一次取到的数」
        assertEquals(60_000L, pacing.limitMs(TurnKind.DECISION))
        pacing.setLimit(WerewolfTurnLimit.FAST)
        assertEquals(30_000L, pacing.limitMs(TurnKind.DECISION))
        assertEquals("快速档发言安全上限", 60_000L, pacing.limitMs(TurnKind.SPEECH))
    }

    @Test
    fun `单次超时保留深度思考，连续两次超时才自动关闭`() {
        val pacing = WerewolfPacing()

        val firstDisabled = pacing.recordTimeout(seat = 3, elapsedMs = 60_000L, kind = TurnKind.DECISION)
        assertFalse("第 1 次超时不直接关思考", firstDisabled)
        assertTrue("第 1 次超时后仍允许思考", pacing.thinkingAllowed(3, roleDefault = true))
        assertEquals(1, pacing.timings.value.getValue(3).timeouts)

        val secondDisabled = pacing.recordTimeout(seat = 3, elapsedMs = 60_000L, kind = TurnKind.SPEECH)
        assertTrue("连续第 2 次超时应自动关闭思考", secondDisabled)
        assertFalse("连续 2 次超时后关闭思考", pacing.thinkingAllowed(3, roleDefault = true))
        assertEquals(2, pacing.timings.value.getValue(3).timeouts)
    }

    @Test
    fun `中间正常完成一步会清零连续超时计数`() {
        val pacing = WerewolfPacing()

        assertFalse(pacing.recordTimeout(seat = 2, elapsedMs = 60_000L, kind = TurnKind.DECISION))
        pacing.record(seat = 2, elapsedMs = 8_000L, kind = TurnKind.SPEECH, timedOut = false)
        assertFalse("连超计数已清零，再超一次仍不关思考", pacing.recordTimeout(seat = 2, elapsedMs = 60_000L, kind = TurnKind.DECISION))
        assertTrue(pacing.thinkingAllowed(2, roleDefault = true))
        assertEquals(2, pacing.timings.value.getValue(2).timeouts)
    }

    @Test
    fun `夜间连续超时只写进隐藏集合，不泄露给参与局`() {
        val pacing = WerewolfPacing()
        pacing.recordTimeout(seat = 5, elapsedMs = 60_000L, kind = TurnKind.DECISION, night = true)
        val disabled = pacing.recordTimeout(seat = 5, elapsedMs = 60_000L, kind = TurnKind.DECISION, night = true)

        assertTrue(disabled)
        assertTrue(5 in pacing.nightThinkingOff.value)
        assertFalse("参与局面板不能看到夜间超时关思考", 5 in pacing.playerThinkingOff.value)
    }

    @Test
    fun `换人重置单座账本与关思考状态，不影响其他座位`() {
        val pacing = WerewolfPacing()
        pacing.recordTimeout(seat = 3, elapsedMs = 60_000L, kind = TurnKind.DECISION)
        pacing.recordTimeout(seat = 3, elapsedMs = 60_000L, kind = TurnKind.DECISION)
        pacing.recordTimeout(seat = 4, elapsedMs = 60_000L, kind = TurnKind.DECISION)
        pacing.recordTimeout(seat = 4, elapsedMs = 60_000L, kind = TurnKind.DECISION)

        pacing.resetSeat(3)

        assertTrue("3 号恢复角色默认思考", pacing.thinkingAllowed(3, roleDefault = true))
        assertFalse("3 号计时账本清空", 3 in pacing.timings.value)
        assertFalse("4 号不受影响", pacing.thinkingAllowed(4, roleDefault = true))
        assertEquals(2, pacing.timings.value.getValue(4).timeouts)
    }
}
