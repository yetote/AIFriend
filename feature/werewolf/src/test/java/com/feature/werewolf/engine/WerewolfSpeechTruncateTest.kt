package com.feature.werewolf.engine

import com.feature.werewolf.game.Faction
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.route.finishReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真机对局复盘里挖出来的几个问题，锁在这里。
 *
 * 这些都不是「理论上不对」，而是在真实牌桌上出现过：
 * 发言被拦腰截断、警长死了结束语还说他在场。
 */
class WerewolfSpeechTruncateTest {

    private val director = WerewolfDirector(emptyMap())

    @Test
    fun `没超长就原样返回`() {
        assertEquals("短句不动", "短句不动", director.truncateAtSentence("短句不动", 200))
    }

    @Test
    fun `超长了停在最后一个句号，不切半句`() {
        // 真实案例：一句发言被 200 字硬切，切出「我认我」这种半句话
        val long = "第一句说完了。第二句也说完了。第三句还没说完就被切断"
        val cut = director.truncateAtSentence(long, 20)

        assertTrue("不能出现半句：$cut", cut.endsWith("。"))
        assertTrue("不该把整段都留下：$cut", cut.length < long.length)
        assertEquals("第一句说完了。第二句也说完了。", cut)
    }

    @Test
    fun `整段没有句号时加省略号，不留半句`() {
        val cut = director.truncateAtSentence("没有任何句号的一段很长的话一直在说下去说到超过上限", 20)

        assertTrue("要有省略号表示后面被截了：$cut", cut.endsWith("…"))
    }

    @Test
    fun `上限为 0 表示不限，一字不裁`() {
        // 「不限」档传 chars = 0。0 在这里要是「放行」而不是「全裁光」：
        // 写反了，整局所有发言都只剩一个省略号
        val text = "这是一段完全不裁的长发言。第一句。第二句。"
        assertEquals(text, director.truncateAtSentence(text, 0))
        assertEquals(text, director.truncateAtSentence(text, -1))
    }

    @Test
    fun `警长中途没了，结束语不能说他在场`() {
        // 真机案例：警长第 2 天被猎人开枪带走、警徽作废，
        // 结束语却还写着「警长在场也最多主持一轮 PK」
        val game = WerewolfGame.withRoles(
            config = WerewolfConfig(
                composition = mapOf(WerewolfRole.WEREWOLF to 4, WerewolfRole.VILLAGER to 4),
            ),
            roster = (1..8).map { RosterEntry(id = "p$it", name = "玩家$it") },
            // 4 狼 4 好：正好绑票（票权相等），才走「为什么投不出去」那段
            roles = listOf(
                WerewolfRole.WEREWOLF,
                WerewolfRole.WEREWOLF,
                WerewolfRole.WEREWOLF,
                WerewolfRole.WEREWOLF,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
            ),
        ).copy(phase = Phase.FINISHED, sheriffSeat = null)

        val reason = finishReason(game, Faction.WEREWOLF)

        assertFalse("警长已经不在了：$reason", reason.contains("警长在场"))
        assertTrue(reason.contains("没有警长就没有 PK"))
    }
}
