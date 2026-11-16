package com.feature.werewolf.game

import com.feature.werewolf.game.WerewolfRole.HUNTER
import com.feature.werewolf.game.WerewolfRole.SEER
import com.feature.werewolf.game.WerewolfRole.VILLAGER
import com.feature.werewolf.game.WerewolfRole.WEREWOLF
import com.feature.werewolf.game.WerewolfRole.WITCH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 狼人杀规则测试。
 *
 * 座位与身份对照（9 人局）：1/2/3 狼 · 4 预言家 · 5 女巫 · 6 猎人 · 7/8/9 平民。
 * 6 人测试局是 1/2 狼 · 3 预言家 · 4 女巫 · 5/6 平民。
 *
 * 通用辅助：[game] 用固定身份开局，[playNight] 打完一整夜，[skipDay] 弃票过一天。
 */
class WerewolfGameTest {

    private val standard9 = listOf(WEREWOLF, WEREWOLF, WEREWOLF, SEER, WITCH, HUNTER, VILLAGER, VILLAGER, VILLAGER)

    /** 6 人：两个神职，杀一个不够屠边，适合测投票流程。 */
    private val sixWithWitch = listOf(WEREWOLF, WEREWOLF, SEER, WITCH, VILLAGER, VILLAGER)

    private fun game(
        roles: List<WerewolfRole>,
        config: WerewolfConfig = WerewolfConfig(composition = roles.groupingBy { it }.eachCount()),
    ): WerewolfGame = WerewolfGame.withRoles(
        config = config,
        roster = roles.indices.map { RosterEntry(id = "p${it + 1}", name = "${it + 1}号") },
        roles = roles,
    )

    /**
     * 打完一整夜。狼人全体刀 [victim]，预言家验 [checkTarget]，女巫按参数用药。
     * 返回时天已亮（可能停在猎人开枪阶段）。
     */
    private fun playNight(
        game: WerewolfGame,
        victim: Int,
        checkTarget: Int = 1,
        heal: Boolean = false,
        poison: Int? = null,
    ): WerewolfGame {
        val wolfSeats = game.aliveWolves().map { it.seat }
        var next = game.wolfVote(wolfSeats.associateWith { victim })
        if (next.phase == Phase.NIGHT_SEER) next = next.seerCheck(checkTarget)
        if (next.phase == Phase.NIGHT_WITCH) next = next.witchAct(heal = heal, poisonTarget = poison)
        return next
    }

    /** 走完一个白天：全员弃票，无人出局，进入下一夜。 */
    private fun skipDay(game: WerewolfGame): WerewolfGame {
        val voting = game.beginVote()
        return voting.castVotes(voting.aliveSeats.associateWith { null })
    }

    // ------------------------------------------------------------------
    // 发牌
    // ------------------------------------------------------------------

    @Test
    fun `deal 按配置分配身份且座位从 1 连续`() {
        val roster = List(8) { RosterEntry("id$it", "${it + 1}号") }
        val dealt = WerewolfGame.deal(WerewolfConfig(), roster, Random(42))

        assertEquals((1..8).toList(), dealt.players.map { it.seat })
        assertEquals(2, dealt.players.count { it.role == WEREWOLF })
        assertEquals(1, dealt.players.count { it.role == SEER })
        assertEquals(3, dealt.players.count { it.role == VILLAGER })
        assertEquals(Phase.NIGHT_WOLF, dealt.phase)
        assertEquals(1, dealt.day)
        assertTrue(dealt.players.all { it.alive })
    }

    @Test
    fun `deal 座位数与配置不符时拒绝开局`() {
        val roster = List(7) { RosterEntry("id$it", "${it + 1}号") }
        assertThrows(IllegalArgumentException::class.java) {
            WerewolfGame.deal(WerewolfConfig(), roster, Random(1))
        }
    }

    @Test
    fun `withRoles 身份构成与配置不符时拒绝开局`() {
        assertThrows(IllegalArgumentException::class.java) {
            WerewolfGame.withRoles(
                config = WerewolfConfig(),
                roster = List(8) { RosterEntry("id$it", "${it + 1}号") },
                roles = List(8) { WEREWOLF },
            )
        }
    }

    // ------------------------------------------------------------------
    // 夜晚
    // ------------------------------------------------------------------

    @Test
    fun `狼人多数决刀人，天亮只公布座位不公布身份`() {
        var mode = game(standard9)
        // 3 狼里 2 票投 7 号，1 票投 8 号 → 7 号出局
        mode = mode.wolfVote(mapOf(1 to 7, 2 to 7, 3 to 8))
        assertEquals(Phase.NIGHT_SEER, mode.phase)
        assertEquals(7, mode.tonightVictim)

        mode = mode.seerCheck(1)
        mode = mode.witchAct(heal = false)

        assertEquals(Phase.DAY_DISCUSSION, mode.phase)
        val dawn = mode.history.filterIsInstance<GameEvent.Dawn>().single()
        assertEquals(listOf(7), dawn.deadSeats)
        assertFalse(mode.playerAt(7).alive)
        assertEquals(DeathCause.WOLF_KILL, mode.playerAt(7).deathCause)
    }

    @Test
    fun `狼人平票加商一次，再次平票则空刀`() {
        var mode = game(standard9)
        mode = mode.wolfVote(mapOf(1 to 7, 2 to 8, 3 to 9))
        assertEquals(Phase.NIGHT_WOLF, mode.phase)
        assertEquals(2, mode.wolfVoteRound)

        mode = mode.wolfVote(mapOf(1 to 7, 2 to 8, 3 to 9))
        assertNull(mode.tonightVictim)
        assertEquals(Phase.NIGHT_SEER, mode.phase)

        mode = mode.seerCheck(1)
        mode = mode.witchAct(heal = false)
        assertTrue(mode.history.filterIsInstance<GameEvent.Dawn>().single().isPeaceful)
    }

    @Test
    fun `空刀夜女巫没有解药可用`() {
        var mode = game(standard9)
        mode = mode.wolfVote(mapOf(1 to 7, 2 to 8, 3 to 9))
        mode = mode.wolfVote(mapOf(1 to 7, 2 to 8, 3 to 9))
        mode = mode.seerCheck(1)

        assertFalse(mode.witchCanHeal)
        assertThrows(IllegalStateException::class.java) { mode.witchAct(heal = true) }
    }

    @Test
    fun `预言家查验结果只进私有记录，不进公开历史`() {
        var mode = game(standard9)
        mode = mode.wolfVote(mapOf(1 to 7, 2 to 7, 3 to 7))
        mode = mode.seerCheck(2)

        val check = mode.seerChecks.single()
        assertEquals(2, check.targetSeat)
        assertTrue(check.isWerewolf)

        mode = mode.witchAct(heal = false)
        assertEquals(1, mode.history.size)
        assertTrue(mode.history.all { it is GameEvent.Dawn })
    }

    @Test
    fun `预言家不能查验自己`() {
        var mode = game(standard9)
        mode = mode.wolfVote(mapOf(1 to 7, 2 to 7, 3 to 7))
        // 4 号是预言家
        assertThrows(IllegalArgumentException::class.java) { mode.seerCheck(4) }
    }

    @Test
    fun `女巫解药救人得到平安夜且解药用尽`() {
        var mode = game(standard9)
        mode = mode.wolfVote(mapOf(1 to 7, 2 to 7, 3 to 7))
        mode = mode.seerCheck(1)
        mode = mode.witchAct(heal = true)

        assertTrue(mode.playerAt(7).alive)
        assertFalse(mode.witch.antidoteAvailable)
        assertTrue(mode.witch.poisonAvailable)
        assertTrue(mode.history.filterIsInstance<GameEvent.Dawn>().single().isPeaceful)
    }

    @Test
    fun `女巫毒药可与狼刀叠加，一夜双死`() {
        var mode = game(standard9)
        mode = mode.wolfVote(mapOf(1 to 7, 2 to 7, 3 to 7))
        mode = mode.seerCheck(1)
        mode = mode.witchAct(heal = false, poisonTarget = 8)

        assertEquals(DeathCause.WOLF_KILL, mode.playerAt(7).deathCause)
        assertEquals(DeathCause.WITCH_POISON, mode.playerAt(8).deathCause)
        assertFalse(mode.witch.poisonAvailable)
        assertEquals(listOf(7, 8), mode.history.filterIsInstance<GameEvent.Dawn>().single().deadSeats)
    }

    @Test
    fun `女巫同一夜不能同时用解药和毒药`() {
        var mode = game(standard9)
        mode = mode.wolfVote(mapOf(1 to 7, 2 to 7, 3 to 7))
        mode = mode.seerCheck(1)

        assertThrows(IllegalArgumentException::class.java) {
            mode.witchAct(heal = true, poisonTarget = 8)
        }
    }

    @Test
    fun `女巫第一夜可以自救`() {
        var mode = game(standard9)
        mode = mode.wolfVote(mapOf(1 to 5, 2 to 5, 3 to 5))
        mode = mode.seerCheck(1)
        mode = mode.witchAct(heal = true)

        assertTrue(mode.playerAt(5).alive)
        assertTrue(mode.history.filterIsInstance<GameEvent.Dawn>().single().isPeaceful)
    }

    @Test
    fun `女巫第二夜不能自救`() {
        var mode = game(standard9)
        mode = playNight(mode, victim = 7)
        mode = skipDay(mode)
        assertEquals(2, mode.day)

        mode = mode.wolfVote(mapOf(1 to 5, 2 to 5, 3 to 5))
        mode = mode.seerCheck(1)

        val night2 = mode
        assertThrows(IllegalArgumentException::class.java) { night2.witchAct(heal = true) }
    }

    @Test
    fun `解药只能用一次`() {
        var mode = game(standard9)
        mode = playNight(mode, victim = 7, heal = true)
        assertFalse(mode.witch.antidoteAvailable)

        mode = skipDay(mode)
        mode = mode.wolfVote(mapOf(1 to 6, 2 to 6, 3 to 6))
        mode = mode.seerCheck(1)

        val night2 = mode
        assertThrows(IllegalArgumentException::class.java) { night2.witchAct(heal = true) }
    }

    // ------------------------------------------------------------------
    // 猎人
    // ------------------------------------------------------------------

    @Test
    fun `猎人被狼刀可以开枪，之后进入白天讨论`() {
        var mode = game(standard9)
        mode = playNight(mode, victim = 6) // 6 号猎人

        assertEquals(Phase.HUNTER_SHOOT, mode.phase)
        assertEquals(6, mode.pendingHunterSeat)

        mode = mode.hunterShoot(1)
        assertFalse(mode.playerAt(1).alive)
        assertEquals(DeathCause.HUNTER_SHOOT, mode.playerAt(1).deathCause)
        assertEquals(Phase.DAY_DISCUSSION, mode.phase)
        assertEquals(1, mode.day)
    }

    @Test
    fun `猎人被女巫毒死不能开枪`() {
        val mode = playNight(game(standard9), victim = 7, poison = 6)

        assertEquals(Phase.DAY_DISCUSSION, mode.phase)
        assertNull(mode.pendingHunterSeat)
        assertEquals(DeathCause.WITCH_POISON, mode.playerAt(6).deathCause)
    }

    @Test
    fun `猎人被票出可以开枪，之后直接进入下一夜`() {
        var mode = game(standard9)
        mode = playNight(mode, victim = 7)
        mode = mode.beginVote()
        mode = mode.castVotes(mode.aliveSeats.associateWith { 6 })

        assertEquals(Phase.HUNTER_SHOOT, mode.phase)
        assertEquals(Phase.NIGHT_WOLF, mode.afterHunterPhase)

        mode = mode.hunterShoot(null)
        assertEquals(Phase.NIGHT_WOLF, mode.phase)
        assertEquals(2, mode.day)
    }

    // ------------------------------------------------------------------
    // 胜负
    // ------------------------------------------------------------------

    @Test
    fun `狼人全部出局则好人获胜`() {
        var mode = game(listOf(WEREWOLF, SEER, VILLAGER, VILLAGER))
        mode = playNight(mode, victim = 3) // 3 号平民
        assertEquals(Phase.DAY_DISCUSSION, mode.phase)

        mode = mode.beginVote()
        mode = mode.castVotes(mapOf(1 to 1, 2 to 1, 4 to 1))

        assertEquals(Faction.GOOD, mode.winner)
        assertEquals(Phase.FINISHED, mode.phase)
        assertTrue(mode.history.last() is GameEvent.GameOver)
    }

    @Test
    fun `屠边规则下杀光神职即狼人获胜`() {
        val roles = listOf(WEREWOLF, SEER, VILLAGER, VILLAGER, VILLAGER)
        var mode = game(roles).wolfVote(mapOf(1 to 2)) // 刀预言家
        mode = mode.seerCheck(1) // 预言家临死前仍可查验

        assertEquals(Faction.WEREWOLF, mode.winner)
        assertEquals(Phase.FINISHED, mode.phase)
    }

    @Test
    fun `屠城规则下神职全灭但人数占优则继续`() {
        val roles = listOf(WEREWOLF, SEER, VILLAGER, VILLAGER, VILLAGER)
        val config = WerewolfConfig(
            composition = roles.groupingBy { it }.eachCount(),
            winRule = WinRule.SLAY_ALL,
        )
        var mode = game(roles, config).wolfVote(mapOf(1 to 2))
        mode = mode.seerCheck(1)

        assertNull(mode.winner)
        assertEquals(Phase.DAY_DISCUSSION, mode.phase)
    }

    @Test
    fun `游戏结束后拒绝继续行动`() {
        var mode = game(listOf(WEREWOLF, SEER, VILLAGER, VILLAGER))
        mode = playNight(mode, victim = 3)
        mode = mode.beginVote().castVotes(mapOf(1 to 1, 2 to 1, 4 to 1))
        assertEquals(Phase.FINISHED, mode.phase)

        val finished = mode
        assertThrows(IllegalArgumentException::class.java) { finished.beginVote() }
        assertThrows(IllegalArgumentException::class.java) { finished.wolfVote(mapOf(1 to 2)) }
    }

    // ------------------------------------------------------------------
    // 投票
    // ------------------------------------------------------------------

    @Test
    fun `平票进入加投且候选人收窄，再平票则无人出局`() {
        var mode = game(sixWithWitch)
        mode = playNight(mode, victim = 5) // 存活 1/2/3/4/6
        mode = mode.beginVote()

        // 3 号 2 票、1 号 2 票 → 平票，候选人收窄到 1 和 3
        mode = mode.castVotes(mapOf(1 to 3, 2 to 3, 3 to 1, 4 to 1, 6 to 4))
        assertEquals(Phase.DAY_VOTE, mode.phase)
        assertEquals(2, mode.voteRound)
        assertEquals(listOf(1, 3), mode.voteCandidates)

        // 加投阶段投候选人之外必须被拒
        assertThrows(IllegalArgumentException::class.java) {
            mode.castVotes(mapOf(1 to 3, 2 to 4, 3 to 1, 4 to 1, 6 to 4))
        }

        // 再次平票，额度用尽 → 无人出局
        mode = mode.castVotes(mapOf(1 to 3, 2 to null, 3 to 1, 4 to null, 6 to null))
        assertTrue(mode.history.any { it is GameEvent.NoExile })
        assertEquals(2, mode.day)
        assertEquals(Phase.NIGHT_WOLF, mode.phase)
    }

    @Test
    fun `全员弃票则无人出局`() {
        var mode = game(standard9)
        mode = playNight(mode, victim = 7)
        mode = mode.beginVote()

        val voters = mode.aliveSeats
        mode = mode.castVotes(voters.associateWith { null })

        assertTrue(mode.history.any { it is GameEvent.NoExile })
        assertEquals(2, mode.day)
    }

    // ------------------------------------------------------------------
    // 非法行动
    // ------------------------------------------------------------------

    @Test
    fun `狼人投票必须全员表态`() {
        val mode = game(standard9)
        assertThrows(IllegalArgumentException::class.java) { mode.wolfVote(mapOf(1 to 7)) }
    }

    @Test
    fun `阶段不符的行动被拒绝`() {
        val mode = game(standard9)
        // 开局是狼人阶段，不能直接验人或投票
        assertThrows(IllegalArgumentException::class.java) { mode.seerCheck(1) }
        assertThrows(IllegalArgumentException::class.java) { mode.beginVote() }
        assertThrows(IllegalArgumentException::class.java) { mode.hunterShoot(1) }
    }
}
