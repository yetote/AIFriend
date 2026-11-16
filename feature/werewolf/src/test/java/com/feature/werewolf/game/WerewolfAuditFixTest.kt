package com.feature.werewolf.game

import com.feature.werewolf.game.WerewolfRole.HUNTER
import com.feature.werewolf.game.WerewolfRole.SEER
import com.feature.werewolf.game.WerewolfRole.VILLAGER
import com.feature.werewolf.game.WerewolfRole.WEREWOLF
import com.feature.werewolf.game.WerewolfRole.WITCH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 审查回归：警长 1.5 票与猎人开枪、遗言的交叉处。
 *
 * 这几条都是先写出复现场景、确认失败、再改引擎的——留在这里防止以后
 * 「顺手简化」把胜负判定挪回开枪之前，或者把自爆当作可以清空遗言队列。
 */
class WerewolfAuditFixTest {

    private fun roster(size: Int) = List(size) { RosterEntry("p${it + 1}", "${it + 1}号") }

    // 统一用屠城：这些用例只关心绑票和流程，屠边会先一步判负把断言搅浑
    // （6 人局里最后两个平民一死，屠边就成立，根本走不到绑票那条分支）
    private fun configOf(roles: List<WerewolfRole>, hasSheriff: Boolean = true) = WerewolfConfig(
        composition = roles.groupingBy { it }.eachCount(),
        winRule = WinRule.SLAY_ALL,
        hasSheriff = hasSheriff,
        maxVoteRounds = 2,
    )

    /** 第二夜开局，且 6 号已在第一夜出局——用来快速凑出人数临界局面。 */
    private fun atSecondNight(
        roles: List<WerewolfRole>,
        sheriff: Int? = null,
        hasSheriff: Boolean = true,
    ): WerewolfGame {
        val start = WerewolfGame.withRoles(configOf(roles, hasSheriff), roster(roles.size), roles)
        val trimmed = start.copy(
            day = 2,
            sheriffSeat = sheriff,
            players = start.players.map {
                if (it.seat == 6) it.copy(alive = false, deathCause = DeathCause.EXILE, deathDay = 1) else it
            },
        )
        return trimmed.beginWolfVote()
    }

    // ------------------------------------------------------------------
    // 绑票要按票权算，不是人头
    // ------------------------------------------------------------------

    @Test
    fun `二比二但有存活警长时不算绑票，好人还能用那一票半`() {
        // 2 狼 2 好（3 预言家、4 女巫），3 号是警长：
        // 狼 4 个半点 vs 好人 5 个半点，好人能把狼投出去。
        // 同一局面拿掉警徽就是 4:4，照旧判狼胜（另一条用例覆盖）。
        val roles = listOf(WEREWOLF, WEREWOLF, SEER, WITCH, VILLAGER, VILLAGER)
        var mode = atSecondNight(roles, sheriff = 3)
        mode = mode.wolfVote(mapOf(1 to 5, 2 to 5)).seerCheck(1).witchAct(heal = false)

        assertNull("警长的 1.5 票让绑票不成立", mode.winner)
        assertEquals(Phase.DAY_DISCUSSION, mode.phase)
    }

    @Test
    fun `二比二且警徽已作废时仍然绑票获胜`() {
        val roles = listOf(WEREWOLF, WEREWOLF, SEER, WITCH, VILLAGER, VILLAGER)
        var mode = atSecondNight(roles, sheriff = null)
        mode = mode.wolfVote(mapOf(1 to 5, 2 to 5)).seerCheck(1).witchAct(heal = false)

        assertEquals(Faction.WEREWOLF, mode.winner)
        assertEquals(Phase.FINISHED, mode.phase)
    }

    // ------------------------------------------------------------------
    // 猎人的枪要排在胜负判定之前
    // ------------------------------------------------------------------

    @Test
    fun `被狼刀的猎人即使已经绑票也照样开枪`() {
        // 2 狼 2 好（3 预言家、4 女巫）时刀死 5 号猎人，原本会在开枪前直接判狼胜。
        val roles = listOf(WEREWOLF, WEREWOLF, SEER, WITCH, HUNTER, VILLAGER)
        var mode = atSecondNight(roles)
        mode = mode.wolfVote(mapOf(1 to 5, 2 to 5)).seerCheck(1).witchAct(heal = false)

        assertEquals(Phase.HUNTER_SHOOT, mode.phase)
        assertEquals(5, mode.pendingHunterSeat)
        assertEquals(DeathCause.WOLF_KILL, mode.playerAt(5).deathCause)

        // 一枪带走 1 号狼：剩 1 狼 2 好，绑票被打破，白天照常进行
        val after = mode.hunterShoot(1)
        assertNull(after.winner)
        assertEquals(Phase.DAY_DISCUSSION, after.phase)
    }

    @Test
    fun `猎人放弃开枪时仍要结算绑票`() {
        val roles = listOf(WEREWOLF, WEREWOLF, SEER, WITCH, HUNTER, VILLAGER)
        var mode = atSecondNight(roles)
        mode = mode.wolfVote(mapOf(1 to 5, 2 to 5)).seerCheck(1).witchAct(heal = false)

        val after = mode.hunterShoot(null)
        assertEquals(Faction.WEREWOLF, after.winner)
        assertEquals(Phase.FINISHED, after.phase)
    }

    @Test
    fun `被票出的猎人开枪同样能打断刚成立的绑票`() {
        // 2 狼 3 好，票出猎人后剩 2v2，绑票刚好成立。和天亮被刀那条是同一个时序问题：
        // 胜负判定不能抢在枪响之前，否则猎人这张牌等于被吞。
        val roles = listOf(WEREWOLF, WEREWOLF, HUNTER, SEER, VILLAGER)
        val start = WerewolfGame.withRoles(configOf(roles), roster(5), roles)
            .copy(phase = Phase.DAY_VOTE)
        val exiled = start.castVotes(start.aliveSeats.associateWith { 3 })

        assertEquals(Phase.DAY_LAST_WORDS, exiled.phase)
        assertEquals(listOf(3), exiled.pendingLastWords)

        val shooting = exiled.finishLastWords()
        assertEquals(Phase.HUNTER_SHOOT, shooting.phase)
        val after = shooting.hunterShoot(1)

        assertNull("一枪带走狼，绑票被打破", after.winner)
        assertEquals(Phase.NIGHT_WOLF_CHAT, after.phase)
    }

    @Test
    fun `警长本人开枪不会清掉自己的警徽`() {
        // 9 人局：4 号既是警长又是猎人，第二夜被刀。
        // 他开枪后到遗言之前，警徽必须还挂在他名下，否则没法交代继任者。
        val roles = listOf(WEREWOLF, WEREWOLF, WEREWOLF, HUNTER, WITCH, VILLAGER, VILLAGER, VILLAGER, VILLAGER)
        val start = WerewolfGame.withRoles(configOf(roles), roster(9), roles)
            .copy(day = 2, sheriffSeat = 4)
            .beginWolfVote()
        var mode = start.wolfVote(mapOf(1 to 4, 2 to 4, 3 to 4)).witchAct(heal = false)

        assertEquals(Phase.HUNTER_SHOOT, mode.phase)
        val after = mode.hunterShoot(1)

        assertEquals(4, after.sheriffSeat)
        assertEquals(Phase.DAY_LAST_WORDS, after.phase)
        assertTrue("还没说遗言，应该仍可移交", after.canTransferBadge(4))
        val passed = after.transferBadge(4, 5)
        assertEquals(5, passed.sheriffSeat)
    }

    // ------------------------------------------------------------------
    // 自爆的收尾
    // ------------------------------------------------------------------

    @Test
    fun `竞选发言时自爆不吞首夜遗言，也不把选举留成未完成`() {
        val roles = listOf(WEREWOLF, WEREWOLF, WEREWOLF, SEER, WITCH, HUNTER, VILLAGER, VILLAGER, VILLAGER)
        var mode = WerewolfGame.withRoles(configOf(roles), roster(9), roles).beginWolfVote()
        mode = mode.wolfVote(mapOf(1 to 7, 2 to 7, 3 to 7)).seerCheck(1).witchAct(heal = false)

        assertEquals(listOf(7), mode.pendingLastWords)
        mode = mode.signUpSheriff(setOf(1, 2))
        assertEquals(Phase.DAY_SHERIFF_SPEECH, mode.phase)

        val after = mode.wolfExplode(1)
        assertEquals("7 号的遗言不能因为别人自爆而消失", listOf(7), after.pendingLastWords)
        assertTrue("选举已中止，第二天不能再回到上警", after.sheriffElectionDone)
        assertTrue("公开历史要说明本局为什么没有警长", after.history.any { it is GameEvent.SheriffVacant })
        assertTrue(after.sheriffCandidates.isEmpty())
        assertTrue(after.sheriffRunners.isEmpty())
        assertEquals(Phase.DAY_LAST_WORDS, after.phase)

        assertEquals(Phase.NIGHT_WOLF_CHAT, after.finishLastWords().phase)
    }
}
