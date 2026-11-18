package com.feature.werewolf.game

import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.persistence.WerewolfSnapshot
import com.feature.werewolf.game.WerewolfRole.BEAR
import com.feature.werewolf.game.WerewolfRole.HUNTER
import com.feature.werewolf.game.WerewolfRole.SEER
import com.feature.werewolf.game.WerewolfRole.VILLAGER
import com.feature.werewolf.game.WerewolfRole.WEREWOLF
import com.feature.werewolf.game.WerewolfRole.WEREWOLF_KING
import com.feature.werewolf.game.WerewolfRole.WITCH
import com.feature.werewolf.prompt.WerewolfPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 狼王与熊的规则测试。
 *
 * 这两个身份分别补了原来空缺的两种玩法：
 * - 狼王把「狼队已经全灭」从终局变成「还要等最后一枪」，胜负判定必须排在枪响之后；
 * - 熊不行动，只在天亮时给全场一条公开线索，考验的是座位的环形邻居和存活状态。
 */
class WerewolfKingBearTest {

    private fun game(roles: List<WerewolfRole>): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(composition = roles.groupingBy { it }.eachCount()),
        roster = roles.indices.map { RosterEntry(id = "p${it + 1}", name = "${it + 1}号") },
        roles = roles,
    )

    private fun WerewolfGame.atWolfVote(): WerewolfGame =
        if (phase == Phase.NIGHT_WOLF_CHAT) beginWolfVote() else this

    /** 走完一夜：狼人下刀，预言家随便验一个，女巫按参数用药。 */
    private fun night(game: WerewolfGame, victim: Int, poison: Int? = null): WerewolfGame {
        var next = game.atWolfVote()
        next = next.wolfVote(nextAliveWolves(next).associateWith { victim })
        if (next.phase == Phase.NIGHT_SEER) {
            val seer = next.alivePlayers.first { it.role == SEER }
            next = next.seerCheck(next.aliveSeats.first { it != seer.seat })
        }
        if (next.phase == Phase.NIGHT_WITCH) {
            next = next.witchAct(heal = false, poisonTarget = poison)
        }
        return next
    }

    private fun nextAliveWolves(game: WerewolfGame): List<Int> =
        game.aliveWolves().map { it.seat }

    private fun kill(game: WerewolfGame, vararg seats: Int): WerewolfGame = game.copy(
        players = game.players.map { player ->
            if (player.seat in seats) {
                player.copy(alive = false, deathCause = DeathCause.EXILE, deathDay = game.day)
            } else {
                player
            }
        }
    )

    // ------------------------------------------------------------------
    // 板子与阵营
    // ------------------------------------------------------------------

    @Test
    fun `新板子的身份构成合法且狼王计入狼人数`() {
        val king10 = WerewolfConfig(composition = WerewolfConfig.King10)
        assertEquals(10, king10.playerCount)
        assertEquals(3, king10.composition.filterKeys { it.isWerewolf }.values.sum())
        assertEquals(1, king10.composition[WEREWOLF_KING])
        assertTrue(king10.has(BEAR) == false)

        val bear12 = WerewolfConfig(composition = WerewolfConfig.Bear12)
        assertEquals(12, bear12.playerCount)
        assertTrue(bear12.has(BEAR))
        assertTrue(BEAR.isGod)
    }

    @Test
    fun `狼王在夜晚和投票中都按狼人处理`() {
        val roles = listOf(WEREWOLF_KING, WEREWOLF, SEER, WITCH, HUNTER, BEAR, VILLAGER, VILLAGER)
        val game = game(roles)

        assertEquals(listOf(1, 2), game.aliveWolves().map { it.seat })
        assertTrue(game.isWerewolf(1))
    }

    // ------------------------------------------------------------------
    // 狼王
    // ------------------------------------------------------------------

    @Test
    fun `狼王被票出后可以开枪，播报里写明是狼王`() {
        val roles = listOf(WEREWOLF_KING, WEREWOLF, SEER, WITCH, HUNTER, BEAR, VILLAGER, VILLAGER)
        val morning = night(game(roles), victim = 8)
        val voting = morning.atDiscussion().beginVote()
        val exiled = voting.castVotes(voting.votingSeats.associateWith { 1 })

        assertEquals(Phase.DAY_LAST_WORDS, exiled.phase)
        assertEquals(1, exiled.pendingHunterSeat)
        assertEquals(Phase.HUNTER_SHOOT, exiled.afterLastWordsPhase)

        val shot = exiled.finishLastWords().hunterShoot(3)
        val event = shot.history.filterIsInstance<GameEvent.HunterShot>().single()
        assertEquals("狼王", event.shooterLabel)
        assertEquals(1, event.hunterSeat)
        assertEquals(3, event.targetSeat)
        assertEquals(DeathCause.HUNTER_SHOOT, shot.playerAt(3).deathCause)
    }

    @Test
    fun `狼王最后一枪带走最后一神时狼人翻盘`() {
        // 只剩狼王 1 号和女巫 3 号：1、2 号身份是狼王 / 预言家，4 号平民和 5 号熊都已出局。
        val roles = listOf(WEREWOLF_KING, SEER, WITCH, VILLAGER, BEAR)
        val board = kill(game(roles), 2, 4, 5).copy(phase = Phase.DAY_DISCUSSION)
        val voting = board.beginVote()
        val exiled = voting.castVotes(voting.votingSeats.associateWith { 1 })

        // 狼队已经全灭，但仍然不能在这里终局——狼王还有一枪
        assertEquals(Phase.DAY_LAST_WORDS, exiled.phase)
        val shot = exiled.finishLastWords().hunterShoot(3)

        assertTrue(shot.isFinished)
        assertEquals(Faction.WEREWOLF, shot.winner)
        assertTrue(shot.history.any { it is GameEvent.GameOver })
    }

    @Test
    fun `狼王被毒死时不开枪`() {
        val roles = listOf(WEREWOLF_KING, SEER, WITCH, VILLAGER, BEAR, VILLAGER)
        // 让狼人刀 6 号、女巫毒狼王：毒覆盖刀，狼王不会开枪
        val morning = night(game(roles), victim = 6, poison = 1)

        assertEquals(DeathCause.WITCH_POISON, morning.playerAt(1).deathCause)
        assertFalse(morning.history.any { it is GameEvent.HunterShot })
        assertFalse(morning.phase == Phase.HUNTER_SHOOT)
    }

    // ------------------------------------------------------------------
    // 熊
    // ------------------------------------------------------------------

    @Test
    fun `天亮时熊的环形邻居有狼就咆哮`() {
        // 熊坐 1 号，狼坐 6 号：座位号绕圈后这两人是邻居
        val roles = listOf(BEAR, SEER, WITCH, VILLAGER, VILLAGER, WEREWOLF)
        val morning = night(game(roles), victim = 4)

        val roar = morning.history.filterIsInstance<GameEvent.BearRoar>().single()
        assertEquals(1, roar.seat)
        assertTrue(WerewolfPrompt.describe(roar).contains("咆哮"))
    }

    @Test
    fun `熊死了或者邻居死了都不再咆哮`() {
        val roles = listOf(BEAR, SEER, WITCH, VILLAGER, VILLAGER, WEREWOLF)

        val bearDead = night(game(roles), victim = 1)
        assertFalse(bearDead.history.any { it is GameEvent.BearRoar })

        // 狼刀 4 号，女巫毒死 6 号狼：熊活着但左边邻居已经死了
        val neighborDead = night(game(roles), victim = 4, poison = 6)
        assertFalse(neighborDead.history.any { it is GameEvent.BearRoar })
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Test
    fun `新事件和狼王称呼能原样存读档`() {
        val roles = listOf(WEREWOLF_KING, SEER, WITCH, VILLAGER)
        val history = listOf(
            GameEvent.BearRoar(day = 1, seat = 4),
            GameEvent.HunterShot(day = 1, hunterSeat = 1, targetSeat = 2, shooterLabel = "狼王"),
        )
        val table = WerewolfTable(game(roles).copy(history = history))

        val restored = WerewolfSnapshot.decode(WerewolfSnapshot.encode(table))
        assertNotNull(restored)
        val shot = restored!!.game.history.filterIsInstance<GameEvent.HunterShot>().single()
        assertEquals("狼王", shot.shooterLabel)
        assertEquals(4, restored.game.history.filterIsInstance<GameEvent.BearRoar>().single().seat)
    }
}
