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
    fun `每个档位给出两套秒数`() {
        // 决策和发言分开：只出一个 JSON 和「边说边想」本来就不是同一个量级
        assertEquals(30_000L, WerewolfTurnLimit.FAST.decisionMs)
        assertEquals(75_000L, WerewolfTurnLimit.FAST.speechMs)
        assertEquals(60_000L, WerewolfTurnLimit.STANDARD.decisionMs)
        assertEquals(150_000L, WerewolfTurnLimit.STANDARD.speechMs)
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
        assertEquals(75_000L, pacing.limitMs(TurnKind.SPEECH))
    }
}
