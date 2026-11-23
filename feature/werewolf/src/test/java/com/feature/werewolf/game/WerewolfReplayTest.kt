package com.feature.werewolf.game

import com.feature.werewolf.game.WerewolfRole.SEER
import com.feature.werewolf.game.WerewolfRole.VILLAGER
import com.feature.werewolf.game.WerewolfRole.WEREWOLF
import com.feature.werewolf.game.WerewolfRole.WITCH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复盘数据（[WerewolfGame.nights] / [WerewolfGame.voteRounds]）的测试。
 *
 * 这两个字段**不参与任何规则判定**，但它们是复盘页唯一的依据，错了不会被别的测试抓到：
 * 规则照常跑完，只是结束后摊开一看，票型少了一天、或者刀口记成了空刀。
 * 所以这里逐条钉住「什么时候写一条、写进去是什么」。
 *
 * 6 人测试局：1/2 狼 · 3 预言家 · 4 女巫 · 5/6 平民。
 */
class WerewolfReplayTest {

    private val six = listOf(WEREWOLF, WEREWOLF, SEER, WITCH, VILLAGER, VILLAGER)

    private fun game(
        roles: List<WerewolfRole> = six,
        maxVoteRounds: Int = 1,
    ): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(
            composition = roles.groupingBy { it }.eachCount(),
            maxVoteRounds = maxVoteRounds,
        ),
        roster = roles.indices.map { RosterEntry(id = "p${it + 1}", name = "${it + 1}号") },
        roles = roles,
    ).atWolfVote()


/** 这些用例验的是规则，不验「今晚先商量再下刀」，所以直接从下刀阶段起手。 */
private fun WerewolfGame.atWolfVote(): WerewolfGame =
    if (phase == Phase.NIGHT_WOLF_CHAT) beginWolfVote() else this

    /** 打完一整夜：狼全体刀 [victim]，预言家验 [checkTarget]，女巫按参数用药。 */
    private fun playNight(
        game: WerewolfGame,
        victim: Int,
        checkTarget: Int = 1,
        heal: Boolean = false,
        poison: Int? = null,
    ): WerewolfGame {
        var next = game.atWolfVote().wolfVote(game.aliveWolves().associate { it.seat to victim })
        if (next.phase == Phase.NIGHT_SEER) next = next.seerCheck(checkTarget)
        if (next.phase == Phase.NIGHT_WITCH) next = next.witchAct(heal = heal, poisonTarget = poison)
        return next
    }

    /** 白天全员弃票，进入下一夜。 */
    private fun skipDay(game: WerewolfGame): WerewolfGame {
        val voting = game.atDiscussion().beginVote()
        return voting.castVotes(voting.aliveSeats.associateWith { null })
    }

    // ------------------------------------------------------------------
    // 夜间
    // ------------------------------------------------------------------

    @Test
    fun `每一夜留一条记录，天数与刀口都对得上`() {
        val night1 = playNight(game(), victim = 5)
        val night2 = playNight(skipDay(night1), victim = 3)

        assertEquals(listOf(1, 2), night2.nights.map { it.day })
        assertEquals(5, night2.nights[0].wolfTarget)
        assertEquals(listOf(5), night2.nights[0].deaths)
        assertEquals(3, night2.nights[1].wolfTarget)
    }

    @Test
    fun `刀口被救时记录 healed，且当晚无人死亡`() {
        val dawn = playNight(game(), victim = 5, heal = true)
        val record = dawn.nights.single()

        assertEquals("刀口要记下来，否则平安夜看起来像狼人空刀", 5, record.wolfTarget)
        assertTrue("用了解药", record.healed)
        assertTrue("被救的人不该出现在死者里", record.deaths.isEmpty())
    }

    @Test
    fun `毒杀单独记，和刀口分开`() {
        val dawn = playNight(game(), victim = 5, poison = 6)
        val record = dawn.nights.single()

        assertEquals(5, record.wolfTarget)
        assertEquals(6, record.poisonTarget)
        assertEquals(listOf(5, 6), record.deaths)
        assertEquals("这夜没救人", false, record.healed)
    }

    @Test
    fun `狼人意见不合空刀，记录里写清今夜没刀人`() {
        // 两个狼各投一个、两轮都僵住 → 引擎判定空刀
        var next = game().wolfVote(mapOf(1 to 5, 2 to 6))
        next = next.wolfVote(mapOf(1 to 5, 2 to 6))
        assertEquals(Phase.NIGHT_SEER, next.phase)

        next = next.seerCheck(1).witchAct(heal = false, poisonTarget = null)
        val record = next.nights.single()

        assertNull("空刀的刀口必须是 null，不能记成 0 或上一个刀口", record.wolfTarget)
        assertTrue(record.deaths.isEmpty())
        assertTrue(record.healed.not())
    }

    // ------------------------------------------------------------------
    // 投票
    // ------------------------------------------------------------------

    @Test
    fun `平票加投记成两轮，每轮只装自己这一轮的票`() {
        // 这条验的是「加投时票型怎么记」，得先有加投额度（默认板子平票即结束）
        // 6 号第一夜被刀，天亮后剩 1/2/3/4/5
        val morning = playNight(game(maxVoteRounds = 2), victim = 6)
        val first = morning.atDiscussion().beginVote()

        // 1 号 2 票、2 号 2 票 → 平票，进入加投
        val tie = first.castVotes(mapOf(1 to 2, 2 to 1, 3 to 2, 4 to 1, 5 to null))
        assertEquals(VoteOutcome.TIE, tie.voteRounds.single().outcome)
        assertEquals("平票还没出局人", null, tie.voteRounds.single().exiledSeat)
        assertEquals(5, tie.voteRounds.single().ballots.size)

        // 加投：PK 台上的 1/2 不投，只有台外的 3/4/5 投，仍只能投 1/2。
        // 3→2、4→1、5→2 → 2 号 2 票出局
        assertEquals("台上 1/2 没有票", listOf(3, 4, 5), tie.exileVoters)
        val second = tie.castVotes(mapOf(3 to 2, 4 to 1, 5 to 2))
        assertEquals(listOf(1, 2), second.voteRounds.map { it.round })
        assertEquals(VoteOutcome.EXILED, second.voteRounds[1].outcome)
        assertEquals(2, second.voteRounds[1].exiledSeat)
        assertEquals(2.0, second.voteRounds[1].exileVotes, 0.0)
        assertEquals("加投轮只有台外 3 人的票", 3, second.voteRounds[1].ballots.size)
    }

    @Test
    fun `全员弃票记成无人出局`() {
        val morning = playNight(game(), victim = 6)
        val voting = morning.atDiscussion().beginVote()
        val voted = voting.castVotes(voting.aliveSeats.associateWith { null })

        val round = voted.voteRounds.single()
        assertEquals(VoteOutcome.NO_EXILE, round.outcome)
        assertNull(round.exiledSeat)
        assertEquals(0.0, round.exileVotes, 0.0)
    }

    @Test
    fun `投票历史跨天累积，进下一夜不会被清掉`() {
        val morning = playNight(game(), victim = 6)
        // 放逐 1 号（狼）而不是平民：票掉平民会立刻屠边结束，就走不到第二夜了
        val voted = morning.atDiscussion().beginVote().castVotes(mapOf(1 to 5, 2 to 1, 3 to 1, 4 to 1, 5 to 1))
        assertEquals(1, voted.voteRounds.single().exiledSeat)

        // 放逐之后还有遗言，说完才真正翻夜
        val next = playNight(voted.atDiscussion(), victim = 3, checkTarget = 2)
        assertEquals("第一天的票型必须还在", 1, next.voteRounds.count { it.day == 1 })
        assertEquals(2, next.nights.size)
    }
}
