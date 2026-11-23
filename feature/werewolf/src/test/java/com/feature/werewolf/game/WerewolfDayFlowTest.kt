package com.feature.werewolf.game

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

/**
 * 白天的流程规则：发言顺序、遗言、自爆。
 *
 * 座位与身份（6 人局）：1/2 狼 · 3 预言家 · 4 女巫 · 5/6 平民。
 * 6 人局的好处是「投错一个就接近屠边」，胜负判定会把流程问题暴露得很快。
 */
class WerewolfDayFlowTest {

    private val roles = listOf(WEREWOLF, WEREWOLF, SEER, WITCH, VILLAGER, VILLAGER)

    private fun roster(size: Int) = List(size) { RosterEntry("p${it + 1}", "${it + 1}号") }

    private fun game(): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(composition = roles.groupingBy { it }.eachCount()),
        roster = roster(roles.size),
        roles = roles,
    ).let { if (it.phase == Phase.NIGHT_WOLF_CHAT) it.beginWolfVote() else it }

    private fun day(): WerewolfGame = game().copy(day = 1, phase = Phase.DAY_DISCUSSION)

    // ------------------------------------------------------------------
    // 发言顺序
    // ------------------------------------------------------------------

    @Test
    fun `有警长时从他下位起，警长最后归票`() {
        val mode = day().copy(sheriffSeat = 3)

        // 从 4 号开始绕一圈，警长 3 号排最后：他先说等于把自己的判断摊给所有人抄
        assertEquals(listOf(4, 5, 6, 1, 2, 3), mode.discussionOrder)
    }

    @Test
    fun `没有警长时从昨夜死者下位起`() {
        val mode = day().copy(history = listOf(GameEvent.Dawn(1, listOf(5))))

        assertEquals(listOf(6, 1, 2, 3, 4, 5), mode.discussionOrder)
    }

    @Test
    fun `平安夜从 1 号起，死人不会留在发言顺序里`() {
        val mode = day().copy(players = day().players.map { if (it.seat == 1) it.copy(alive = false) else it })

        assertEquals(listOf(2, 3, 4, 5, 6), mode.discussionOrder)
    }

    // ------------------------------------------------------------------
    // 遗言
    // ------------------------------------------------------------------

    @Test
    fun `被放逐的人留遗言，说完才进黑夜`() {
        val start = day()
        val voted = start.beginVote().castVotes(start.aliveSeats.associateWith { 5 })

        assertEquals(Phase.DAY_LAST_WORDS, voted.phase)
        assertEquals(listOf(5), voted.pendingLastWords)
        assertEquals("放逐之后的下一步是黑夜", Phase.NIGHT_WOLF, voted.afterLastWordsPhase)

        val next = voted.finishLastWords()
        assertEquals(2, next.day)
        assertEquals(Phase.NIGHT_WOLF_CHAT, next.phase)
        assertTrue(next.pendingLastWords.isEmpty())
    }

    @Test
    fun `第二夜起夜里死的人不再留遗言`() {
        // 直接推到第 2 夜下刀：这条规则和「第一夜」绑定，得让 day = 2 才验得出来
        var mode = game().copy(day = 2, phase = Phase.NIGHT_WOLF)
        mode = mode.wolfVote(mode.aliveWolves().associate { it.seat to 5 })
        mode = mode.seerCheck(1)
        mode = mode.witchAct(heal = false)

        assertFalse(mode.playerAt(5).alive)
        assertTrue("第二夜起夜里死的人没有遗言", mode.pendingLastWords.isEmpty())
        assertEquals(Phase.DAY_DISCUSSION, mode.phase)
    }

    @Test
    fun `遗言阶段不接受投票，也不接受再开一次遗言`() {
        val dying = day().copy(
            phase = Phase.DAY_LAST_WORDS,
            pendingLastWords = listOf(5),
            players = day().players.map { if (it.seat == 5) it.copy(alive = false) else it },
        )

        assertThrows(IllegalArgumentException::class.java) { dying.beginVote() }
    }

    // ------------------------------------------------------------------
    // 自爆
    // ------------------------------------------------------------------

    @Test
    fun `狼人自爆当天直接结束，不产生任何投票`() {
        val exploded = day().wolfExplode(1)

        assertFalse(exploded.playerAt(1).alive)
        assertEquals(DeathCause.SELF_EXPLODE, exploded.playerAt(1).deathCause)
        assertTrue(exploded.history.any { it is GameEvent.WolfExploded })
        assertFalse(
            "自爆这一天不该有放逐记录",
            exploded.history.any { it is GameEvent.Exiled || it is GameEvent.NoExile },
        )
        assertEquals(2, exploded.day)
        assertEquals(Phase.NIGHT_WOLF_CHAT, exploded.phase)
    }

    @Test
    fun `好人在白天不能自爆，狼人在夜里也不能`() {
        val daytime = day()

        // 3 号是预言家
        assertThrows(IllegalArgumentException::class.java) { daytime.wolfExplode(3) }
        // 夜里不处理自爆：那时没有「白天被打断」这回事
        assertThrows(IllegalArgumentException::class.java) { game().wolfExplode(1) }
        // 已经出局的人也不能再爆一次
        val dead = daytime.copy(players = daytime.players.map { if (it.seat == 1) it.copy(alive = false) else it })
        assertThrows(IllegalArgumentException::class.java) { dead.wolfExplode(1) }
    }

    @Test
    fun `警长自爆仍要留遗言，把警徽交出去`() {
        val explodingSheriff = day().copy(sheriffSeat = 1)
        val exploded = explodingSheriff.wolfExplode(1)

        assertEquals(Phase.DAY_LAST_WORDS, exploded.phase)
        assertTrue("他是警长，遗言里得交代警徽", exploded.canTransferBadge(1))

        val next = exploded.transferBadge(1, 6).finishLastWords()
        assertEquals(6, next.sheriffSeat)
        assertEquals("自爆也是直接进黑夜", Phase.NIGHT_WOLF_CHAT, next.phase)
    }

    @Test
    fun `竞选发言时自爆也要先把昨夜死讯公布出来`() {
        // 有警长的局，第 1 天死讯压在 pendingDawn 里，等竞选结束才公布。
        // 自爆把竞选直接炸停，公布时机也一起没了——必须在这里补上。
        val base = game()
        val campaigning = base.copy(
            config = WerewolfConfig(composition = roles.groupingBy { it }.eachCount(), hasSheriff = true),
            day = 1,
            phase = Phase.DAY_SHERIFF_SPEECH,
            sheriffElectionDone = false,
            sheriffCandidates = listOf(1, 4),
            sheriffRunners = listOf(1, 4),
            pendingDawn = PendingDawn(day = 1, deaths = listOf(5)),
            pendingLastWords = listOf(5),
            players = base.players.map {
                if (it.seat == 5) it.copy(alive = false, deathCause = DeathCause.WOLF_KILL) else it
            },
        )

        val exploded = campaigning.wolfExplode(1)

        assertTrue(
            "自爆中止竞选后，死讯再没有别的时机可以公布",
            exploded.history.any { it is GameEvent.Dawn && 5 in it.deadSeats },
        )
        assertNull("死讯公布完就该清空，否则下一夜结算会把它覆盖掉", exploded.pendingDawn)
        assertEquals("首夜死者还要留遗言", Phase.DAY_LAST_WORDS, exploded.phase)
        assertTrue(
            "先说自爆、再说昨夜倒牌，顺序反了像在自爆之前就公布过",
            exploded.history.indexOfFirst { it is GameEvent.WolfExploded } <
                exploded.history.indexOfFirst { it is GameEvent.Dawn },
        )
    }

    @Test
    fun `普通狼人自爆不留遗言`() {
        val exploded = day().wolfExplode(1)

        assertNull(exploded.sheriffSeat)
        assertTrue(exploded.pendingLastWords.isEmpty())
    }
}
