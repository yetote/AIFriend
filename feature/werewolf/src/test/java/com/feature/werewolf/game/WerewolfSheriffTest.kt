package com.feature.werewolf.game

import com.feature.werewolf.game.WerewolfRole.HUNTER
import com.feature.werewolf.game.WerewolfRole.SEER
import com.feature.werewolf.game.WerewolfRole.VILLAGER
import com.feature.werewolf.game.WerewolfRole.WEREWOLF
import com.feature.werewolf.game.WerewolfRole.WITCH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 警长规则。
 *
 * 座位与身份（9 人局）：1/2/3 狼 · 4 预言家 · 5 女巫 · 6 猎人 · 7/8/9 平民。
 *
 * 这些用例验三件事：谁能上警 / 谁能投票、1.5 票到底改不改得动结果、警徽怎么交出去。
 * 竞选流程本身（谁先说话、遗言排在哪）由 `WerewolfDayFlowTest` 管。
 */
class WerewolfSheriffTest {

    private val roles = listOf(WEREWOLF, WEREWOLF, WEREWOLF, SEER, WITCH, HUNTER, VILLAGER, VILLAGER, VILLAGER)

    private val config = WerewolfConfig(
        composition = roles.groupingBy { it }.eachCount(),
        hasSheriff = true,
        maxVoteRounds = 2,
    )

    private fun roster(size: Int) = List(size) { RosterEntry("p${it + 1}", "${it + 1}号") }

    private fun game(
        roles: List<WerewolfRole> = this.roles,
        config: WerewolfConfig = this.config,
    ): WerewolfGame = WerewolfGame.withRoles(config, roster(roles.size), roles)
        .let { if (it.phase == Phase.NIGHT_WOLF_CHAT) it.beginWolfVote() else it }

    /**
     * 直接停在上警阶段。
     *
     * 选举规则的用例不关心「第一夜怎么打」，走一整夜只会把断言拖进遗言和猎人开枪里。
     * 「天亮之后是不是真的先进竞选」由第一条用例单独验。
     */
    private fun atSignup(): WerewolfGame = game().copy(phase = Phase.DAY_SHERIFF_SIGNUP)

    /** 把某个人做成出局状态，用于构造「警长已死」的局面。 */
    private fun WerewolfGame.killed(seat: Int): WerewolfGame = copy(
        players = players.map {
            if (it.seat == seat) {
                it.copy(alive = false, deathCause = DeathCause.WOLF_KILL, deathDay = day)
            } else {
                it
            }
        },
    )

    // ------------------------------------------------------------------
    // 流程位置
    // ------------------------------------------------------------------

    @Test
    fun `开警长的板子第一天先竞选，死者的遗言排在竞选之后`() {
        var mode = game()
        mode = mode.wolfVote(mapOf(1 to 7, 2 to 7, 3 to 7))
        mode = mode.seerCheck(1)
        mode = mode.witchAct(heal = false)

        assertEquals(Phase.DAY_SHERIFF_SIGNUP, mode.phase)
        // 遗言已经排好队，但必须等竞选结束才轮到死者说话——
        // 先遗言等于让死人的指控直接影响警长投票，那张票还不用负责
        assertEquals(listOf(7), mode.pendingLastWords)
    }

    @Test
    fun `不竞选警长的板子不会出现竞选阶段`() {
        val plain = WerewolfConfig(composition = roles.groupingBy { it }.eachCount())
        var mode = game(config = plain)
        mode = mode.wolfVote(mapOf(1 to 7, 2 to 7, 3 to 7))
        mode = mode.seerCheck(1)
        mode = mode.witchAct(heal = false)

        assertEquals(Phase.DAY_LAST_WORDS, mode.phase)
    }

    // ------------------------------------------------------------------
    // 上警与投票
    // ------------------------------------------------------------------

    @Test
    fun `无人上警就没有警长，也不会卡在竞选`() {
        val mode = atSignup().signUpSheriff(emptySet())

        assertNull(mode.sheriffSeat)
        assertTrue(mode.sheriffElectionDone)
        assertTrue(mode.history.any { it is GameEvent.SheriffVacant })
        assertEquals(Phase.DAY_DISCUSSION, mode.phase)
    }

    @Test
    fun `只有一个人上警时自动当选，不用投票`() {
        val mode = atSignup().signUpSheriff(setOf(4))

        assertEquals(4, mode.sheriffSeat)
        assertEquals(Phase.DAY_DISCUSSION, mode.phase)
        val event = mode.history.filterIsInstance<GameEvent.SheriffElected>().single()
        assertEquals(0, event.votes)
    }

    @Test
    fun `上过警的人不能投警长票`() {
        val mode = atSignup().signUpSheriff(setOf(1, 4))

        assertEquals(Phase.DAY_SHERIFF_SPEECH, mode.phase)
        assertEquals(listOf(1, 4), mode.sheriffCandidates)
        assertEquals(listOf(2, 3, 5, 6, 7, 8, 9), mode.sheriffVoters)
    }

    @Test
    fun `警下投票选出警长，票数记在播报里`() {
        val voting = atSignup().signUpSheriff(setOf(1, 4)).beginSheriffVote()

        // 少一个人投票也要拦：警下是「全部」投票，不是「想投的人」投票
        assertThrows(IllegalArgumentException::class.java) {
            voting.castSheriffVotes(mapOf(2 to 4))
        }

        val elected = voting.castSheriffVotes(
            mapOf(2 to 4, 3 to 4, 5 to 4, 6 to 1, 7 to 1, 8 to null, 9 to null),
        )
        assertEquals(4, elected.sheriffSeat)
        assertEquals(Phase.DAY_DISCUSSION, elected.phase)
        assertEquals(3, elected.history.filterIsInstance<GameEvent.SheriffElected>().single().votes)
    }

    @Test
    fun `警长投票平票先 PK，再平票就警徽流失`() {
        val voting = atSignup().signUpSheriff(setOf(1, 4)).beginSheriffVote()

        // 7 个警下人：3 票给 4、3 票给 1、1 人弃票
        val tie = voting.castSheriffVotes(
            mapOf(2 to 4, 3 to 4, 5 to 4, 6 to 1, 7 to 1, 8 to 1, 9 to null),
        )
        assertEquals(Phase.DAY_SHERIFF_SPEECH, tie.phase)
        assertEquals(2, tie.sheriffRound)
        assertEquals(listOf(1, 4), tie.sheriffCandidates)
        assertTrue(tie.history.any { it is GameEvent.SheriffPk })

        // PK 轮：台上(1、4)不投，其余存活都能投。这局只有 1、4 上过警，
        // 所以新口径的 PK 投票人和旧口径的警下名单恰好一样。
        val again = tie.beginSheriffVote()
        assertEquals(listOf(2, 3, 5, 6, 7, 8, 9), again.sheriffVoteVoters)

        val still = again.castSheriffVotes(
            mapOf(2 to 4, 3 to 4, 5 to 4, 6 to 1, 7 to 1, 8 to 1, 9 to null),
        )
        assertNull(still.sheriffSeat)
        assertEquals(Phase.DAY_DISCUSSION, still.phase)
        assertTrue(still.history.any { it is GameEvent.SheriffVacant })
    }

    @Test
    fun `警长PK时台上不投，上过警但没进PK的人拿回投票权`() {
        // 三人上警(1、4、7)：首轮只有警下 6 人投，1 和 4 各 3 票平票进 PK。
        // 7 号上过警、没进 PK——按新规则他 PK 轮可以投，旧规则会把他排除。
        val voting = atSignup().signUpSheriff(setOf(1, 4, 7)).beginSheriffVote()
        assertEquals("首轮只有警下能投", listOf(2, 3, 5, 6, 8, 9), voting.sheriffVoteVoters)

        val tie = voting.castSheriffVotes(
            mapOf(2 to 1, 3 to 1, 5 to 4, 6 to 4, 8 to null, 9 to null),
        )
        assertEquals(2, tie.sheriffRound)
        assertEquals(listOf(1, 4), tie.sheriffCandidates)

        val pk = tie.beginSheriffVote()
        assertEquals(
            "PK 轮台上(1、4)不投，其余存活全可投，含上过警的 7 号",
            listOf(2, 3, 5, 6, 7, 8, 9),
            pk.sheriffVoteVoters,
        )
        // 带上 7 号这一票，7 号投给 4 决定警徽归属
        val elected = pk.castSheriffVotes(
            mapOf(2 to 1, 3 to 1, 5 to 4, 6 to 4, 7 to 4, 8 to null, 9 to null),
        )
        assertEquals("7 号的回归票让 4 号以 3:2 当选", 4, elected.sheriffSeat)
    }

    @Test
    fun `全员上警时没人有资格投票，本局就没有警长`() {
        val everyone = atSignup().aliveSeats.toSet()
        val mode = atSignup().signUpSheriff(everyone)

        assertEquals(Phase.DAY_SHERIFF_SPEECH, mode.phase)
        assertTrue(mode.sheriffVoters.isEmpty())

        val after = mode.beginSheriffVote().castSheriffVotes(emptyMap())
        assertNull(after.sheriffSeat)
        assertTrue(after.history.any { it is GameEvent.SheriffVacant })
    }

    // ------------------------------------------------------------------
    // 退水
    // ------------------------------------------------------------------

    @Test
    fun `退水的人不再被投票，首轮也没有警长票`() {
        // 三个人上警，7 号退水：还剩两个人，所以照常投票
        val stage = atSignup().signUpSheriff(setOf(1, 4, 7)).beginSheriffWithdraw()
        assertEquals(Phase.DAY_SHERIFF_WITHDRAW, stage.phase)

        val after = stage.withdrawSheriff(setOf(7))

        assertEquals(Phase.DAY_SHERIFF_VOTE, after.phase)
        assertEquals("7 号退出竞选，不再是候选", listOf(1, 4), after.sheriffCandidates)
        // 退水不等于把票拿回来：7 号上过警，首轮仍然不投
        assertEquals(listOf(2, 3, 5, 6, 8, 9), after.sheriffVoteVoters)
        assertEquals(
            "退水要记进公开事件",
            listOf(7),
            after.history.filterIsInstance<GameEvent.SheriffWithdrawn>().single().seats,
        )

        // 票投给退水的人要拦下来：他已经不在候选里了
        assertThrows(IllegalArgumentException::class.java) {
            after.castSheriffVotes(
                mapOf(2 to 7, 3 to 1, 5 to 1, 6 to 1, 8 to 4, 9 to 4),
            )
        }
    }

    @Test
    fun `退水的人首轮不投，平票PK后拿回投票权`() {
        // 三人上警(1、4、7)：7 号退水，首轮只剩警下 6 人投，1 和 4 各 3 票平票。
        // 首轮 7 号没票（上过警），但 PK 是「台上不投、台外都能投」——他在台外，有票。
        val after = atSignup().signUpSheriff(setOf(1, 4, 7)).beginSheriffWithdraw()
            .withdrawSheriff(setOf(7))
        assertEquals("退水后首轮仍没有票", listOf(2, 3, 5, 6, 8, 9), after.sheriffVoteVoters)

        val tie = after.castSheriffVotes(
            mapOf(2 to 1, 3 to 1, 5 to 1, 6 to 4, 8 to 4, 9 to 4),
        )
        assertEquals(2, tie.sheriffRound)
        assertEquals(listOf(1, 4), tie.sheriffCandidates)

        val pk = tie.beginSheriffVote()
        assertEquals(
            "PK 轮台上(1、4)不投，退水的 7 号在台外，这一轮有票",
            listOf(2, 3, 5, 6, 7, 8, 9),
            pk.sheriffVoteVoters,
        )
        val elected = pk.castSheriffVotes(
            mapOf(2 to 1, 3 to 1, 5 to 1, 6 to 4, 7 to 4, 8 to 4, 9 to 4),
        )
        assertEquals("7 号这一票让 4 号以 4:3 当选", 4, elected.sheriffSeat)
    }

    @Test
    fun `全员退水就没有警长`() {
        val after = atSignup().signUpSheriff(setOf(1, 4)).beginSheriffWithdraw()
            .withdrawSheriff(setOf(1, 4))

        assertNull(after.sheriffSeat)
        assertTrue(after.sheriffElectionDone)
        assertEquals(Phase.DAY_DISCUSSION, after.phase)
        assertTrue(
            "要写明是因为全员退水",
            after.history.filterIsInstance<GameEvent.SheriffVacant>().single().reason.contains("退水"),
        )
    }

    @Test
    fun `只剩一个人不退时直接当选，不用投票`() {
        val after = atSignup().signUpSheriff(setOf(1, 4)).beginSheriffWithdraw()
            .withdrawSheriff(setOf(1))

        assertEquals(4, after.sheriffSeat)
        assertEquals(Phase.DAY_DISCUSSION, after.phase)
        val elected = after.history.filterIsInstance<GameEvent.SheriffElected>().single()
        assertEquals(0, elected.votes)
        assertNotNull("要说明是别人退水了，而不是只有他上警", elected.note)
    }

    @Test
    fun `只能退自己的警，PK 轮不再退水`() {
        val stage = atSignup().signUpSheriff(setOf(1, 4)).beginSheriffWithdraw()
        assertThrows(IllegalArgumentException::class.java) {
            stage.withdrawSheriff(setOf(7))
        }

        // PK 轮发言结束后直接投票：那轮的候选人是投票选出来的，再退水等于让票白投
        val tie = stage.withdrawSheriff(emptySet()).castSheriffVotes(
            mapOf(2 to 4, 3 to 4, 5 to 4, 6 to 1, 7 to 1, 8 to 1, 9 to null),
        )
        assertEquals(Phase.DAY_SHERIFF_SPEECH, tie.phase)
        assertEquals(Phase.DAY_SHERIFF_VOTE, tie.beginSheriffWithdraw().phase)
    }

    // ------------------------------------------------------------------
    // 1.5 票
    // ------------------------------------------------------------------

    /**
     * 6 人局（1/2 狼 · 3 预 · 4 女 · 5/6 民），把警徽直接挂在 1 号身上。
     *
     * 用同一张票型跑两遍：有警徽时 3 号 2.5 票出局，没警徽时是 2:2 平票。
     * 这就是 1.5 票唯一的意义——它只在差半票的时候改变结果，正好是最需要它的时候。
     */
    @Test
    fun `警长的一票算一点五票，能改变放逐结果`() {
        val small = listOf(WEREWOLF, WEREWOLF, SEER, WITCH, VILLAGER, VILLAGER)
        val cfg = WerewolfConfig(
            composition = small.groupingBy { it }.eachCount(),
            hasSheriff = true,
            maxVoteRounds = 2,
        )
        fun board(sheriff: Int?) = WerewolfGame.withRoles(cfg, roster(6), small)
            .copy(day = 2, phase = Phase.DAY_DISCUSSION, sheriffSeat = sheriff)

        // 人头票：3 号 2 票、5 号 2 票、2 号 2 票 —— 平票
        // 加上警长的 1.5 票之后：3 号 2.5 票，独占最高票
        val ballots = mapOf(1 to 3, 2 to 3, 3 to 5, 4 to 5, 5 to 2, 6 to 2)

        val withBadge = board(sheriff = 1).beginVote().castVotes(ballots)
        assertEquals(3, withBadge.voteRounds.single().exiledSeat)
        assertEquals(2.5, withBadge.voteRounds.single().exileVotes, 0.0)

        val noBadge = board(sheriff = null).beginVote().castVotes(ballots)
        assertTrue("同一张票型没有警徽就是平票", noBadge.voteRounds.single().outcome == VoteOutcome.TIE)
    }

    // ------------------------------------------------------------------
    // 警徽移交
    // ------------------------------------------------------------------

    private fun deadSheriff(): WerewolfGame {
        val elected = atSignup().signUpSheriff(setOf(4))
        return elected.killed(4).copy(
            day = 2,
            phase = Phase.DAY_LAST_WORDS,
            pendingLastWords = listOf(4),
            afterLastWordsPhase = Phase.NIGHT_WOLF,
        )
    }

    @Test
    fun `警长在遗言里把警徽交给活人，警徽跟着进下一夜`() {
        val passed = deadSheriff().transferBadge(4, 8)

        assertEquals(8, passed.sheriffSeat)
        assertTrue(passed.history.any { it is GameEvent.SheriffBadgePassed })

        val next = passed.finishLastWords()
        assertEquals("警徽不能随着他一起消失", 8, next.sheriffSeat)
        assertEquals(Phase.NIGHT_WOLF_CHAT, next.phase)
    }

    @Test
    fun `警长没交代就出局，警徽作废`() {
        val next = deadSheriff().finishLastWords()

        assertNull(next.sheriffSeat)
        // 「有过警长，现在没了」和「从来没选出来」是两件事，复盘要分得清
        val lost = next.history.filterIsInstance<GameEvent.SheriffBadgePassed>().single()
        assertNull(lost.toSeat)
        assertEquals(4, lost.fromSeat)
    }

    @Test
    fun `只有警长本人的遗言才能移交警徽`() {
        val dying = deadSheriff()

        assertThrows(IllegalArgumentException::class.java) { dying.transferBadge(1, 8) }
        // 也不能交给已经出局的自己
        assertThrows(IllegalArgumentException::class.java) { dying.transferBadge(4, 4) }
    }

    @Test
    fun `不在遗言阶段不能移交警徽`() {
        val elected = atSignup().signUpSheriff(setOf(4))
        assertThrows(IllegalArgumentException::class.java) {
            elected.transferBadge(4, 8)
        }
    }

    // ------------------------------------------------------------------
    // 被猎人带走
    // ------------------------------------------------------------------

    /**
     * 停在第 2 天的开枪阶段：6 号猎人要开枪，7 号是现任警长。
     *
     * 用 copy 直接搭局面而不是打一整夜——这里要验的是「枪杀与警徽」两条规则的交叉，
     * 夜里谁被刀、谁被毒跟结论无关。
     */
    private fun atHunterShoot(): WerewolfGame = atSignup().signUpSheriff(setOf(7)).copy(
        day = 2,
        phase = Phase.HUNTER_SHOOT,
        pendingHunterSeat = 6,
        afterHunterPhase = Phase.NIGHT_WOLF,
    )

    @Test
    fun `被枪带走的警长没有遗言，但警徽照样能交`() {
        val shot = atHunterShoot().hunterShoot(7)

        // 进队列不是为了说话，是为了「只交徽」这一个动作
        assertEquals(Phase.DAY_LAST_WORDS, shot.phase)
        assertEquals(listOf(7), shot.pendingLastWords)
        assertFalse(shot.playerAt(7).alive)
        assertTrue(shot.canTransferBadge(7))

        val next = shot.transferBadge(7, 8).finishLastWords()
        assertEquals("被枪带走不等于连徽一起没了", 8, next.sheriffSeat)
        assertEquals(Phase.NIGHT_WOLF_CHAT, next.phase)
    }

    @Test
    fun `被枪带走的警长也能撕掉警徽`() {
        val torn = atHunterShoot().hunterShoot(7).transferBadge(7, null)

        assertNull(torn.sheriffSeat)
    }

    @Test
    fun `天亮被枪带走的警长，交完徽才回白天`() {
        val dawn = atHunterShoot().copy(afterHunterPhase = Phase.DAY_DISCUSSION)
        val shot = dawn.hunterShoot(7)

        assertEquals(Phase.DAY_LAST_WORDS, shot.phase)
        assertEquals(Phase.DAY_DISCUSSION, shot.afterLastWordsPhase)

        val next = shot.transferBadge(7, 8).finishLastWords()
        assertEquals(Phase.DAY_DISCUSSION, next.phase)
        assertEquals(8, next.sheriffSeat)
    }

    @Test
    fun `被枪打死的普通人没有遗言`() {
        val shot = atHunterShoot().hunterShoot(3)

        assertFalse(shot.playerAt(3).alive)
        assertTrue("枪杀者不留遗言", shot.pendingLastWords.isEmpty())
        assertEquals(Phase.NIGHT_WOLF_CHAT, shot.phase)
    }
}
