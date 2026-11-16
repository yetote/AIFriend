package com.feature.werewolf.game

import com.feature.werewolf.game.WerewolfRole.HUNTER
import com.feature.werewolf.game.WerewolfRole.SEER
import com.feature.werewolf.game.WerewolfRole.VILLAGER
import com.feature.werewolf.game.WerewolfRole.WEREWOLF
import com.feature.werewolf.game.WerewolfRole.WITCH
import com.feature.werewolf.prompt.WerewolfPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 同一夜多人、多因致死的边界。
 *
 * 一件事要同时出现在三个地方：**公开播报**（`GameEvent.Dawn`）、**复盘记录**
 * （`NightRecord.deaths`）、**玩家状态**（`WerewolfPlayer.deathCause`）。
 * 三处各算一遍，只要有一处口径不同，就会出现「播报说 6 号出局、底牌区看他还活着」
 * 这类自相矛盾的复盘。这个文件把这些口径钉在一起。
 *
 * 座位对照（9 人局）：1/2/3 狼 · 4 预言家 · 5 女巫 · 6 猎人 · 7/8/9 平民。
 */
class WerewolfDeathCollisionTest {

    private val standard9 =
        listOf(WEREWOLF, WEREWOLF, WEREWOLF, SEER, WITCH, HUNTER, VILLAGER, VILLAGER, VILLAGER)

    private fun game(roles: List<WerewolfRole> = standard9): WerewolfGame =
        WerewolfGame.withRoles(
            config = WerewolfConfig(composition = roles.groupingBy { it }.eachCount()),
            roster = roles.indices.map { RosterEntry(id = "p${it + 1}", name = "${it + 1}号") },
            roles = roles,
        ).let { if (it.phase == Phase.NIGHT_WOLF_CHAT) it.beginWolfVote() else it }

    /** 打完一夜：狼刀 [victim]，预言家验 1 号（固定），女巫按参数用药。 */
    private fun playNight(
        victim: Int,
        poison: Int? = null,
        heal: Boolean = false,
    ): WerewolfGame {
        var next = game().wolfVote(game().aliveWolves().associate { it.seat to victim })
        if (next.phase == Phase.NIGHT_SEER) next = next.seerCheck(1)
        if (next.phase == Phase.NIGHT_WITCH) {
            next = next.witchAct(heal = heal, poisonTarget = poison)
        }
        return next
    }

    @Test
    fun `毒落在刀口上时，天亮播报只算一次死亡`() {
        // 3 狼刀 6 号，女巫又毒 6 号：同一个人被两个来源指向，播报不能写成「6 号、6 号」
        val mode = playNight(victim = 6, poison = 6)

        val dawn = mode.history.filterIsInstance<GameEvent.Dawn>().single()
        assertEquals(listOf(6), dawn.deadSeats)
        assertEquals(
            "第1天 天亮：6 号出局",
            WerewolfPrompt.describe(dawn),
        )
    }

    @Test
    fun `毒落在刀口上时，复盘记录也只算一次`() {
        val mode = playNight(victim = 6, poison = 6)

        assertEquals(listOf(6), mode.nights.single().deaths)
        // 刀口和毒口都要如实记下来，否则复盘看不出「女巫毒了自己人」这种操作
        assertEquals(6, mode.nights.single().wolfTarget)
        assertEquals(6, mode.nights.single().poisonTarget)
    }

    @Test
    fun `被毒覆盖的猎人不开枪，与死因判定保持一致`() {
        // 死因按「毒」记（毒既不能被守卫挡，也不能被解药救），而规则是「被毒不能开枪」。
        // 若判开枪时还按刀口算，就会出现「记为毒死、却照样开枪」的矛盾
        val mode = playNight(victim = 6, poison = 6)

        assertEquals(DeathCause.WITCH_POISON, mode.playerAt(6).deathCause)
        assertNull(mode.pendingHunterSeat)
        assertEquals(Phase.DAY_LAST_WORDS, mode.phase)
    }

    @Test
    fun `毒不是刀口时两人都出局，顺序稳定`() {
        val mode = playNight(victim = 7, poison = 9)

        assertEquals(listOf(7, 9), mode.history.filterIsInstance<GameEvent.Dawn>().single().deadSeats)
        assertEquals(listOf(7, 9), mode.nights.single().deaths)
        assertEquals(DeathCause.WOLF_KILL, mode.playerAt(7).deathCause)
        assertEquals(DeathCause.WITCH_POISON, mode.playerAt(9).deathCause)
    }
}
