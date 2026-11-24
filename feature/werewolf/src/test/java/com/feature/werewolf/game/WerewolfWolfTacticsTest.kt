package com.feature.werewolf.game

import com.feature.werewolf.game.WerewolfRole.HUNTER
import com.feature.werewolf.game.WerewolfRole.SEER
import com.feature.werewolf.game.WerewolfRole.VILLAGER
import com.feature.werewolf.game.WerewolfRole.WEREWOLF
import com.feature.werewolf.game.WerewolfRole.WEREWOLF_KING
import com.feature.werewolf.game.WerewolfRole.WITCH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 狼队的花板子在引擎这一层成立不成立。
 *
 * 0.0.61 之前引擎有两道护栏：狼不能刀队友、不能刀自己，而且必须指一个好人。
 * 结果整局狼只有「刀好人」一条路，打法被压成一套悍跳。这三条现在都放开了，
 * 但放开是有代价的——自刀会让狼自己死、空刀会让女巫这一夜什么都用不上，
 * 这些后果必须由引擎算对，不能只靠提示词嘴硬。
 *
 * 座位（9 人）：1/2/3 狼 · 4 预言家 · 5 女巫 · 6 猎人 · 7/8/9 平民。
 */
class WerewolfWolfTacticsTest {

    private val standard9 = listOf(
        WEREWOLF, WEREWOLF, WEREWOLF, SEER, WITCH, HUNTER, VILLAGER, VILLAGER, VILLAGER
    )

    private fun game(roles: List<WerewolfRole>): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(composition = roles.groupingBy { it }.eachCount()),
        roster = roles.indices.map { RosterEntry(id = "p${it + 1}", name = "${it + 1}号") },
        roles = roles,
    ).let { if (it.phase == Phase.NIGHT_WOLF_CHAT) it.beginWolfVote() else it }

    /** 过掉预言家和女巫，把这一夜结算完。 */
    private fun settle(game: WerewolfGame, check: Int = 7): WerewolfGame =
        game.seerCheck(check).witchActSettled(heal = false)

    @Test
    fun `狼可以刀队友`() {
        val dawn = settle(game(standard9).wolfVote(mapOf(1 to 2, 2 to 2, 3 to 2)))

        assertEquals("2 号是狼，但这一刀照样落下", DeathCause.WOLF_KILL, dawn.playerAt(2).deathCause)
        assertFalse("2 号出局", dawn.playerAt(2).alive)
    }

    @Test
    fun `狼可以自刀`() {
        val dawn = settle(game(standard9).wolfVote(mapOf(1 to 1, 2 to 1, 3 to 1)))

        assertEquals("自刀也是刀，死因照常是狼刀", DeathCause.WOLF_KILL, dawn.playerAt(1).deathCause)
        assertFalse(dawn.playerAt(1).alive)
    }

    @Test
    fun `狼王自刀拿不到枪`() {
        val roles = listOf(
            WEREWOLF_KING, WEREWOLF, WEREWOLF, SEER, WITCH, HUNTER, VILLAGER, VILLAGER, VILLAGER
        )
        val dawn = settle(game(roles).wolfVote(mapOf(1 to 1, 2 to 1, 3 to 1)))

        assertFalse("狼王已经出局", dawn.playerAt(1).alive)
        // 他那把枪只属于「被投票放逐」这一刻，夜里被刀没有开枪权
        assertTrue("不该停在开枪阶段：${dawn.phase}", dawn.phase != Phase.HUNTER_SHOOT)
    }

    @Test
    fun `全员空刀就是平安夜`() {
        val dawn = settle(game(standard9).wolfVote(mapOf(1 to null, 2 to null, 3 to null)))

        assertNull("没有刀口", dawn.tonightVictim)
        assertTrue("没有人死", dawn.players.all { it.alive })
        assertNull("夜间记录里也没有刀口", dawn.nights.single().wolfTarget)
    }

    @Test
    fun `空刀时女巫没有可救的人`() {
        val night = game(standard9).wolfVote(mapOf(1 to null, 2 to null, 3 to null))
        // 空刀之后女巫仍要行动一次，但「解药」对她这一刻不成立——
        // 决策层靠 witchCanHeal 判断能不能摆出「救人」这个选项
        assertFalse(night.witchCanHeal)
    }

    @Test
    fun `多数狼主张空刀就当夜空刀`() {
        // 三只狼里两只主张空刀，一只想刀 5 号：多数决的结果是不刀人
        val dawn = settle(game(standard9).wolfVote(mapOf(1 to null, 2 to null, 3 to 5)))

        assertNull("跟票的是多数", dawn.tonightVictim)
        assertTrue(dawn.players.all { it.alive })
    }

    @Test
    fun `一只狼想空刀不影响定刀`() {
        val dawn = settle(game(standard9).wolfVote(mapOf(1 to 5, 2 to 5, 3 to null)))

        assertEquals("两票对一票，刀口还是 5 号", 5, dawn.tonightVictim)
        assertFalse(dawn.playerAt(5).alive)
    }

    @Test
    fun `空刀和刀人平票时回到商议`() {
        // 两只狼意见一人一半：不猜，直接加商一轮
        val night = game(listOf(WEREWOLF, WEREWOLF, SEER, WITCH, VILLAGER, VILLAGER))
            .wolfVote(mapOf(1 to null, 2 to 4))

        assertEquals("还在下刀阶段", Phase.NIGHT_WOLF, night.phase)
        assertEquals("进入第二轮", 2, night.wolfVoteRound)
    }
}
