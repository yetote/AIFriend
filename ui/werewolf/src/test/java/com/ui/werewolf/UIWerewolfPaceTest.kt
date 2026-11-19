package com.ui.werewolf

import com.ui.werewolf.data.UIWerewolfPromptStyle
import com.ui.werewolf.data.UIWerewolfTurnLimit
import com.ui.werewolf.data.formatSpent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 思考时限在界面这一侧的两个约定。
 *
 * 这里守的都是「读起来对不对」：秒数写错不会让牌局出错，但会让用户误判
 * 「这个模型是不是卡死了」，从而去改一个本来不该改的档位。
 */
class UIWerewolfPaceTest {

    @Test
    fun `秒数按人话写，不到一分钟不带分`() {
        assertEquals("没走过就是破折号", "—", formatSpent(0L))
        assertEquals("不到一秒", "不到 1s", formatSpent(400L))
        assertEquals("整秒", "12s", formatSpent(12_000L))
        // 四舍五入到秒：60 秒要进位成 1 分，而不是显示「60s」
        assertEquals("刚好一分钟", "1 分", formatSpent(60_000L))
        assertEquals("一分零五秒", "1 分 05 秒", formatSpent(65_000L))
        assertTrue("三位数秒也要能读", formatSpent(125_000L).startsWith("2 分"))
    }

    @Test
    fun `四档的名称和顺序与引擎一致`() {
        // route 层靠 name 做一一映射，两边名字对不上会静默回到「标准」，
        // 表现是「我明明选了宽松，下一局还是 1 分钟」
        assertEquals(
            listOf("FAST", "STANDARD", "RELAXED", "UNLIMITED"),
            UIWerewolfTurnLimit.entries.map { it.name },
        )
    }

    @Test
    fun `不限档要说清代价，别的档要说清超时之后发生什么`() {
        // 「不限」是一个陷阱选项：用户以为只是「宽松一点」，
        // 实际可能是某个座位卡十几分钟。文案必须点明这一点。
        assertTrue(UIWerewolfTurnLimit.UNLIMITED.consequence.contains("十几分钟"))
        UIWerewolfTurnLimit.entries
            .filter { it != UIWerewolfTurnLimit.UNLIMITED }
            .forEach { limit ->
                assertTrue(
                    "${limit.label} 要说清超时后果",
                    limit.consequence.contains("兜底") && limit.consequence.contains("深度思考"),
                )
            }
    }

    @Test
    fun `提示词风格两档的名称和顺序与引擎一致`() {
        // route 靠 name 映射，名字对不上会静默回到引导式——用户选了自由式却没生效。
        assertEquals(
            listOf("GUIDED", "FREE"),
            UIWerewolfPromptStyle.entries.map { it.name },
        )
        assertTrue("自由式要说清是给强模型的", UIWerewolfPromptStyle.FREE.hint.contains("强推理"))
    }
}
