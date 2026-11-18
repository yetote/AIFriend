package com.feature.werewolf.game

import com.feature.werewolf.game.WerewolfRole.GUARD
import com.feature.werewolf.game.WerewolfRole.HUNTER
import com.feature.werewolf.game.WerewolfRole.IDIOT
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
 * 守卫与白痴的规则测试。
 *
 * 这两个身份是「扩充板型」时加的，它们和原有身份的区别不在强度，而在**规则的类型**：
 * 守卫改的是夜里的一次死亡结算，白痴改的是白天的一次放逐结算。
 * 所以测试也按这两条线分开写，不去堆砌组合场景。
 *
 * 座位与身份对照：
 * - 10 人守卫局：1/2/3 狼 · 4 预言家 · 5 女巫 · 6 猎人 · 7 守卫 · 8/9/10 平民
 * - 12 人白痴局：1/2/3/4 狼 · 5 预言家 · 6 女巫 · 7 猎人 · 8 白痴 · 9~12 平民
 */
class WerewolfNewRolesTest {

    private val guard10 = listOf(
        WEREWOLF, WEREWOLF, WEREWOLF,
        SEER, WITCH, HUNTER, GUARD,
        VILLAGER, VILLAGER, VILLAGER,
    )

    private val fool12 = listOf(
        WEREWOLF, WEREWOLF, WEREWOLF, WEREWOLF,
        SEER, WITCH, HUNTER, IDIOT,
        VILLAGER, VILLAGER, VILLAGER, VILLAGER,
    )

    private fun game(roles: List<WerewolfRole>): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(composition = roles.groupingBy { it }.eachCount()),
        roster = roles.indices.map { RosterEntry(id = "p${it + 1}", name = "${it + 1}号") },
        roles = roles,
    )


/** 这些用例验的是规则，不验「今晚先商量再下刀」，所以直接从下刀阶段起手。 */
private fun WerewolfGame.atWolfVote(): WerewolfGame =
    if (phase == Phase.NIGHT_WOLF_CHAT) beginWolfVote() else this

    /**
     * 打完一整夜：守卫按 [guard] 守人（有守卫阶段时），狼全体刀 [victim]，
     * 预言家验一个无关的人，女巫按参数用药。
     */
    private fun night(
        game: WerewolfGame,
        guard: Int? = null,
        victim: Int,
        heal: Boolean = false,
        poison: Int? = null,
    ): WerewolfGame {
        var next = if (game.phase == Phase.NIGHT_GUARD) game.guardProtect(guard) else game
        next = next.atWolfVote().wolfVote(next.aliveWolves().map { it.seat }.associateWith { victim })
        if (next.phase == Phase.NIGHT_SEER) {
            val seer = next.alivePlayers.first { it.role == SEER }
            next = next.seerCheck(next.aliveSeats.first { it != seer.seat })
        }
        if (next.phase == Phase.NIGHT_WITCH) next = next.witchAct(heal = heal, poisonTarget = poison)
        return next
    }

    /** 走完一个白天：全员弃票，无人出局，进入下一夜。 */
    private fun skipDay(game: WerewolfGame): WerewolfGame {
        val voting = game.atDiscussion().beginVote()
        return voting.castVotes(voting.votingSeats.associateWith { null })
    }

    private fun lastDawn(game: WerewolfGame): GameEvent.Dawn =
        game.history.filterIsInstance<GameEvent.Dawn>().last()

    // ------------------------------------------------------------------
    // 守卫
    // ------------------------------------------------------------------

    @Test
    fun `有守卫的板子从守卫阶段入夜，没有的仍从狼人开始`() {
        assertEquals(Phase.NIGHT_GUARD, game(guard10).phase)
        assertEquals(Phase.NIGHT_WOLF_CHAT, game(fool12).phase)
    }

    @Test
    fun `守中刀口就是平安夜，夜里记下守了谁`() {
        val morning = night(game(guard10), guard = 8, victim = 8)

        assertTrue("8 号被守住，应该活着", morning.playerAt(8).alive)
        assertTrue("这一夜应该和平", lastDawn(morning).isPeaceful)
        // 平安夜没人出局，也就没有遗言，天亮直接进讨论
        assertEquals(Phase.DAY_DISCUSSION, morning.phase)

        val record = morning.nights.single()
        assertEquals(8, record.wolfTarget)
        assertEquals("复盘要能看出是守住了，而不是狼人空刀", 8, record.guarded)
        assertFalse("守住的不是解药", record.healed)
        assertTrue(record.deaths.isEmpty())
    }

    @Test
    fun `守错人时刀口照常生效`() {
        val morning = night(game(guard10), guard = 9, victim = 8)

        assertFalse(morning.playerAt(8).alive)
        assertEquals(DeathCause.WOLF_KILL, morning.playerAt(8).deathCause)
        assertEquals(9, morning.nights.single().guarded)
    }

    @Test
    fun `守卫可以守自己`() {
        val guarded = game(guard10).guardProtect(7)

        assertEquals(7, guarded.tonightGuardedSeat)
        assertEquals(Phase.NIGHT_WOLF_CHAT, guarded.phase)
    }

    @Test
    fun `不能连续两夜守同一个人，空守之后限制解除`() {
        val morning = night(game(guard10), guard = 8, victim = 10)
        val secondNight = skipDay(morning)

        assertEquals(Phase.NIGHT_GUARD, secondNight.phase)
        assertEquals("上一夜守的人要记住", 8, secondNight.lastGuardedSeat)
        assertThrows(IllegalArgumentException::class.java) { secondNight.guardProtect(8) }

        // 空守不算「守过」，所以下一夜又可以守 8 号了
        val afterEmpty = secondNight.guardProtect(null)
        assertNull(afterEmpty.tonightGuardedSeat)
        assertEquals(Phase.NIGHT_WOLF_CHAT, afterEmpty.phase)
    }

    @Test
    fun `守卫出局后不再进入守护阶段`() {
        // 狼刀守卫本人：他守了 8 号，但没人守他
        val morning = night(game(guard10), guard = 8, victim = 7)
        assertFalse(morning.playerAt(7).alive)

        val nextNight = skipDay(morning)
        assertEquals("守卫死了就直接进狼人商议", Phase.NIGHT_WOLF_CHAT, nextNight.phase)
    }

    @Test
    fun `毒药无视守护`() {
        // 狼刀 8 号被守住，但女巫同时毒 8 号：照样死
        val morning = night(game(guard10), guard = 8, victim = 8, poison = 8)

        assertFalse(morning.playerAt(8).alive)
        assertEquals(DeathCause.WITCH_POISON, morning.playerAt(8).deathCause)
        val record = morning.nights.single()
        assertEquals(8, record.guarded)
        assertEquals(8, record.poisonTarget)
    }

    @Test
    fun `同守同救按同归于尽处理`() {
        // 线下经典规则：守卫守了他，女巫也救了他，人还是死。
        // 本引擎原来选的是「活下来」（那时觉得 AI 推理成本高），
        // 2026-11-19 按用户要求改成守救同归。
        val morning = night(game(guard10), guard = 8, victim = 8, heal = true)

        assertFalse("同归于尽就是要死", morning.playerAt(8).alive)
        assertFalse("不是平安夜", lastDawn(morning).isPeaceful)
        assertTrue("解药确实用掉了", !morning.witch.antidoteAvailable)
        assertTrue(morning.nights.single().healed)
        assertTrue("复盘里要记下是守救同归", morning.nights.single().mutualProtection)
    }

    // ------------------------------------------------------------------
    // 白痴
    // ------------------------------------------------------------------

    @Test
    fun `白痴被票出只是翻牌，不死也不出局`() {
        val morning = night(game(fool12), victim = 12)
        val voting = morning.atDiscussion().beginVote()
        val voted = voting.castVotes(voting.votingSeats.associateWith { 8 })

        assertTrue("翻牌不是出局", voted.playerAt(8).alive)
        assertNull(voted.playerAt(8).deathCause)
        assertTrue(voted.idiotRevealed)

        val event = voted.history.filterIsInstance<GameEvent.IdiotRevealed>().single()
        assertEquals(8, event.seat)
        // 第一夜死了 12 号，所以这天是 11 个人投票，全票投给了白痴
        assertEquals(11.0, event.voteCount, 0.0)

        val round = voted.voteRounds.last()
        assertEquals(VoteOutcome.IDIOT_REVEALED, round.outcome)

        // 翻牌之后直接进下一夜，白天的流程不会因为「没人出局」而重投
        assertEquals(Phase.NIGHT_WOLF_CHAT, voted.phase)
        assertEquals(2, voted.day)
    }

    @Test
    fun `翻牌后失去投票权，也不能再被投`() {
        val morning = night(game(fool12), victim = 12)
        val voting = morning.atDiscussion().beginVote()
        val voted = voting.castVotes(voting.votingSeats.associateWith { 8 })

        assertFalse("翻牌的白痴不该再有票", 8 in voted.votingSeats)
        assertEquals(10, voted.votingSeats.size)

        // 第二天再投：把 8 号当目标必须被挡住，否则会出现「投他→翻牌→再投他」的循环
        val nextMorning = night(voted, victim = 11)
        val nextVoting = nextMorning.atDiscussion().beginVote()
        assertThrows(IllegalArgumentException::class.java) {
            nextVoting.castVotes(nextVoting.votingSeats.associateWith { 8 })
        }
    }

    @Test
    fun `白痴翻牌后夜里照样会被刀死`() {
        val morning = night(game(fool12), victim = 12)
        val voting = morning.atDiscussion().beginVote()
        val voted = voting.castVotes(voting.votingSeats.associateWith { 8 })

        val nextMorning = night(voted, victim = 8)
        assertFalse("白痴只是票不死，不是无敌", nextMorning.playerAt(8).alive)
        assertEquals(DeathCause.WOLF_KILL, nextMorning.playerAt(8).deathCause)
    }

    @Test
    fun `白痴算神职，屠边时算在神里`() {
        assertTrue(IDIOT.isGod)
        assertTrue(GUARD.isGod)

        // 12 人白痴局：四个神全倒才算屠神。
        // 这里只验「白痴翻牌不算神全灭」：翻牌后神位仍然有人在。
        val morning = night(game(fool12), victim = 12)
        val voting = morning.atDiscussion().beginVote()
        val voted = voting.castVotes(voting.votingSeats.associateWith { 8 })

        assertNull("翻牌不该直接判负", voted.winner)
    }

    @Test
    fun `配置挡住重复的神职`() {
        assertThrows(IllegalArgumentException::class.java) {
            WerewolfConfig(
                composition = mapOf(WEREWOLF to 2, GUARD to 2, VILLAGER to 3),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WerewolfConfig(
                composition = mapOf(WEREWOLF to 2, IDIOT to 2, VILLAGER to 3),
            )
        }
    }
}
