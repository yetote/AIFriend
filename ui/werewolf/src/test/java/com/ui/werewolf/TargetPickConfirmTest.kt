package com.ui.werewolf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 选目标弹窗两次点击确认的状态转移测试。
 */
class TargetPickConfirmTest {

    @Test
    fun `第一次点击只选中，换点别人切换选中，第二次点击同一座位才确认`() {
        assertEquals(TargetTapOutcome.Selected(3), resolveTargetTap(currentSelected = null, tappedSeat = 3))
        assertEquals(TargetTapOutcome.Selected(7), resolveTargetTap(currentSelected = 3, tappedSeat = 7))
        assertEquals(TargetTapOutcome.Confirmed(7), resolveTargetTap(currentSelected = 7, tappedSeat = 7))
    }
}
