package com.feature.werewolf.engine.persistence

import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.game.Ballot
import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.Faction
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.SeerCheck
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPlayer
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WitchStock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 存档格式的测试。
 *
 * 判据是**整局对象完全相等**，不是抽查几个字段。断点续玩的 bug 几乎都是
 * 「大部分字段回来了，但某一个漏了」，然后在续上的那一刻表现成一局怪游戏——
 * 比如死了的人还能投票、狼人忘了队友。抽查字段恰好抓不到这类问题。
 */
class WerewolfSnapshotTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry("p$it", "玩家$it") }

    /** 一局攒满各种状态的局面：死人、选票、查验、药品消耗、猎人待开枪、胜负已分。 */
    private fun richTable(): WerewolfTable {
        val players = listOf(
            WerewolfPlayer(1, "p1", "玩家1", WerewolfRole.WEREWOLF, alive = true),
            WerewolfPlayer(2, "p2", "玩家2", WerewolfRole.WEREWOLF, alive = false, deathCause = DeathCause.EXILE, deathDay = 2),
            WerewolfPlayer(3, "p3", "玩家3", WerewolfRole.SEER, alive = false, deathCause = DeathCause.WOLF_KILL, deathDay = 2),
            WerewolfPlayer(4, "p4", "玩家4", WerewolfRole.WITCH, alive = true),
            WerewolfPlayer(5, "p5", "玩家5", WerewolfRole.HUNTER, alive = false, deathCause = DeathCause.WITCH_POISON, deathDay = 1),
            WerewolfPlayer(6, "p6", "玩家6", WerewolfRole.VILLAGER, alive = true),
            WerewolfPlayer(7, "p7", "玩家7", WerewolfRole.VILLAGER, alive = false, deathCause = DeathCause.HUNTER_SHOOT, deathDay = 1),
            WerewolfPlayer(8, "p8", "玩家8", WerewolfRole.VILLAGER, alive = true),
        )

        val game = WerewolfGame(
            config = WerewolfConfig(
                composition = WerewolfConfig.Standard8,
                winRule = com.feature.werewolf.game.WinRule.SLAY_ALL,
                maxVoteRounds = 3,
                witchCanSelfHealOnFirstNight = false,
            ),
            players = players,
            day = 3,
            phase = Phase.HUNTER_SHOOT,
            witch = WitchStock(antidoteAvailable = false, poisonAvailable = true),
            wolfVotes = mapOf(1 to 6),
            wolfVoteRound = 2,
            tonightVictim = 6,
            seerChecks = listOf(
                SeerCheck(day = 1, targetSeat = 2, isWerewolf = true),
                SeerCheck(day = 2, targetSeat = 6, isWerewolf = false),
            ),
            voteRound = 2,
            voteCandidates = listOf(1, 4, 6),
            ballots = listOf(
                Ballot(voterSeat = 1, targetSeat = 4),
                Ballot(voterSeat = 4, targetSeat = null),
            ),
            pendingHunterSeat = 5,
            afterHunterPhase = Phase.DAY_DISCUSSION,
            history = listOf(
                GameEvent.Dawn(day = 1, deadSeats = listOf(5, 7)),
                GameEvent.Exiled(day = 2, seat = 2, voteCount = 5),
                GameEvent.NoExile(day = 2),
                GameEvent.HunterShot(day = 3, hunterSeat = 5, targetSeat = 7),
                GameEvent.GameOver(day = 3, winner = Faction.WEREWOLF),
            ),
            winner = Faction.WEREWOLF,
        )

        val transcript = listOf(
            WerewolfMessage(
                day = 1, phase = Phase.NIGHT_WOLF, seat = 1, kind = MessageKind.WOLF_CHAT,
                text = "刀 5 号吧。", audience = setOf(1, 2),
            ),
            WerewolfMessage(
                day = 1, phase = Phase.NIGHT_SEER, seat = 3, kind = MessageKind.NIGHT_ACT,
                text = "查验 2 号。", audience = setOf(3),
            ),
            WerewolfMessage(
                day = 1, phase = Phase.DAY_DISCUSSION, seat = 4, kind = MessageKind.SPEECH,
                text = "我觉得 2 号有问题。",
            ),
            WerewolfMessage(
                day = 1, phase = Phase.DAY_VOTE, seat = 6, kind = MessageKind.VOTE,
                text = "我投 2 号。",
            ),
            WerewolfMessage(
                day = 1, phase = Phase.DAY_VOTE, seat = null, kind = MessageKind.DIVINE,
                text = "第1天 投票：2 号被放逐（5 票）",
            ),
        )
        return WerewolfTable(game = game, transcript = transcript)
    }

    // ------------------------------------------------------------------

    @Test
    fun `整局存档再读出，和存档前一模一样`() {
        val original = richTable()

        val text = WerewolfSnapshot.encode(original)
        val restored = WerewolfSnapshot.decode(text)

        assertNotNull("应该能读回来", restored)
        assertEquals("整局必须完全相等", original, restored)
    }

    @Test
    fun `空的一局也能原样读回`() {
        val table = WerewolfTable(WerewolfGame.deal(WerewolfConfig(), roster(8)))
        val restored = WerewolfSnapshot.decode(WerewolfSnapshot.encode(table))
        assertEquals(table, restored)
    }

    @Test
    fun `私有可见性跟着存档一起保留`() {
        val restored = WerewolfSnapshot.decode(WerewolfSnapshot.encode(richTable()))!!

        // 狼人夜聊的 audience 必须还在，否则续上之后夜聊会变成公开消息
        val wolfChat = restored.transcript.first { it.kind == MessageKind.WOLF_CHAT }
        assertEquals(setOf(1, 2), wolfChat.audience)
        assertTrue(wolfChat.visibleTo(1))
        assertTrue("好人依旧看不到", !wolfChat.visibleTo(4))

        val public = restored.transcript.first { it.kind == MessageKind.SPEECH }
        assertNull("公开发言不该带 audience", public.audience)
    }

    @Test
    fun `枚举存的是名字不是序号`() {
        val text = WerewolfSnapshot.encode(richTable())
        // 存序号的话，枚举重排一次所有老存档就全错了
        assertTrue(text.contains("\"WEREWOLF\""))
        assertTrue(text.contains("\"NIGHT_WOLF\""))
        assertTrue(text.contains("\"HUNTER_SHOT\""))
    }

    @Test
    fun `未知字段不影响读取`() {
        val text = WerewolfSnapshot.encode(richTable())
        // 模拟未来版本多写了一个字段，旧版本读到时应该忽略而不是失败
        val withExtra = text.dropLast(1) + ",\"futureField\":123}"
        assertNotNull(WerewolfSnapshot.decode(withExtra))
    }

    @Test
    fun `版本不匹配当作没有存档`() {
        val text = WerewolfSnapshot.encode(richTable())
            .replaceFirst("\"version\":${WerewolfSnapshot.VERSION}", "\"version\":999")
        assertNull("不认识的版本不该硬读", WerewolfSnapshot.decode(text))
    }

    @Test
    fun `损坏的存档返回 null 而不是抛异常`() {
        assertNull(WerewolfSnapshot.decode(""))
        assertNull(WerewolfSnapshot.decode("不是 JSON"))
        assertNull(WerewolfSnapshot.decode("{}"))
    }

    @Test
    fun `未知事件类型会被判为坏档`() {
        val text = WerewolfSnapshot.encode(richTable())
            .replace("\"HUNTER_SHOT\"", "\"SOMETHING_NEW\"")
        assertNull(WerewolfSnapshot.decode(text))
    }
}
