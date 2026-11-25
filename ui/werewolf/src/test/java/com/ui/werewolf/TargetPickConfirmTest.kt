package com.ui.werewolf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 选目标弹窗交互与自适应网格/语义配色测试。
 */
class TargetPickConfirmTest {

    @Test
    fun `第一次点击只选中，换点别人切换选中，第二次点击同一座位才确认`() {
        assertEquals(TargetTapOutcome.Selected(3), resolveTargetTap(currentSelected = null, tappedSeat = 3))
        assertEquals(TargetTapOutcome.Selected(7), resolveTargetTap(currentSelected = 3, tappedSeat = 7))
        assertEquals(TargetTapOutcome.Confirmed(7), resolveTargetTap(currentSelected = 7, tappedSeat = 7))
    }

    @Test
    fun `不同人数的牌桌自适应对称列数`() {
        assertEquals(2, targetGridColumns(4))
        assertEquals(3, targetGridColumns(6))
        assertEquals(4, targetGridColumns(8))
        assertEquals(3, targetGridColumns(9))
        assertEquals(4, targetGridColumns(10))
        assertEquals(4, targetGridColumns(12))
    }

    @Test
    fun `行动标题映射到对应的语义标签`() {
        assertEquals("毒药", resolveTargetActionTheme("毒谁", night = true).badge)
        assertEquals("查验", resolveTargetActionTheme("今晚查验谁", night = true).badge)
        assertEquals("守护", resolveTargetActionTheme("今晚守护谁", night = true).badge)
        assertEquals("警徽", resolveTargetActionTheme("警长投票", night = false).badge)
        assertEquals("目标", resolveTargetActionTheme("今晚袭击谁", night = true).badge)
        assertEquals("目标", resolveTargetActionTheme("开枪带走谁", night = false).badge)
        assertEquals("投票", resolveTargetActionTheme("投出谁", night = false).badge)
    }
}
