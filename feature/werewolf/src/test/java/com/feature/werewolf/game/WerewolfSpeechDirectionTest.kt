package com.feature.werewolf.game

import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.persistence.toSnapshot
import com.feature.werewolf.engine.persistence.toTable
import com.feature.werewolf.game.WerewolfRole.HUNTER
import com.feature.werewolf.game.WerewolfRole.SEER
import com.feature.werewolf.game.WerewolfRole.VILLAGER
import com.feature.werewolf.game.WerewolfRole.WEREWOLF
import com.feature.werewolf.game.WerewolfRole.WITCH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 警长选发言方向。
 *
 * 座位与身份（9 人局）：1/2/3 狼 · 4 预言家 · 5 女巫 · 6 猎人 · 7/8/9 平民。
 * 这些用例验四件事：两种方向的顺序都对、警长永远垫底（归票位）、
 * 「每天选一次」的幂等与校验、快照往返后方向不丢。
 */
class WerewolfSpeechDirectionTest {

    private val roles = listOf(WEREWOLF, WEREWOLF, WEREWOLF, SEER, WITCH, HUNTER, VILLAGER, VILLAGER, VILLAGER)

    private val config = WerewolfConfig(
        composition = roles.groupingBy { it }.eachCount(),
        hasSheriff = true,
        maxVoteRounds = 2,
    )

    private fun roster(size: Int) = List(size) { RosterEntry("p${it + 1}", "${it + 1}号") }

    private fun game(): WerewolfGame = WerewolfGame.withRoles(config, roster(roles.size), roles)

    /** 直接停在白天讨论，警徽给 [sheriff]。全员存活，顺序只受方向影响。 */
    private fun atDiscussion(sheriff: Int = 2): WerewolfGame =
        game().copy(phase = Phase.DAY_DISCUSSION, sheriffSeat = sheriff)

    @Test
    fun `没选方向时默认顺时针，从警长下一位起警长垫底`() {
        val game = atDiscussion(sheriff = 2)

        assertTrue(game.speechDirectionPending)
        assertEquals(SpeechDirection.CLOCKWISE, game.speechDirection)
        assertEquals(listOf(3, 4, 5, 6, 7, 8, 9, 1, 2), game.discussionOrder)
    }

    @Test
    fun `逆时针从警长上一位倒着绕，警长仍然垫底`() {
        val game = atDiscussion(sheriff = 2).setSpeechDirection(SpeechDirection.COUNTER_CLOCKWISE)

        assertEquals(SpeechDirection.COUNTER_CLOCKWISE, game.speechDirection)
        // 2 号的逆时针下一位是 1 号，倒着数到 3 号收尾，警长 2 号最后归票
        assertEquals(listOf(1, 9, 8, 7, 6, 5, 4, 3, 2), game.discussionOrder)
    }

    @Test
    fun `选完方向当天不再询问`() {
        val game = atDiscussion().setSpeechDirection(SpeechDirection.COUNTER_CLOCKWISE)

        assertFalse(game.speechDirectionPending)
    }

    @Test
    fun `没有警长的局面不需要选方向`() {
        val game = atDiscussion(sheriff = 2).copy(sheriffSeat = null)

        assertFalse(game.speechDirectionPending)
        // 没警长的老规则不被动：从死者下位起（这里没人死，从最小座位起）
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), game.discussionOrder)
    }

    @Test
    fun `没活着的警长也不能选`() {
        val dead = atDiscussion(sheriff = 2).killed(2)

        assertThrows(IllegalArgumentException::class.java) {
            dead.setSpeechDirection(SpeechDirection.COUNTER_CLOCKWISE)
        }
    }

    @Test
    fun `一天只能选一次`() {
        val game = atDiscussion().setSpeechDirection(SpeechDirection.CLOCKWISE)

        assertThrows(IllegalArgumentException::class.java) {
            game.setSpeechDirection(SpeechDirection.COUNTER_CLOCKWISE)
        }
    }

    @Test
    fun `选方向是公开事件，写进了历史`() {
        val game = atDiscussion().setSpeechDirection(SpeechDirection.COUNTER_CLOCKWISE)

        val event = game.history.filterIsInstance<GameEvent.SpeechDirectionChosen>().single()
        assertEquals(2, event.seat)
        assertEquals(SpeechDirection.COUNTER_CLOCKWISE, event.direction)
    }

    @Test
    fun `第二天的方向重新询问，昨天的选择不管用`() {
        val day1 = atDiscussion().setSpeechDirection(SpeechDirection.COUNTER_CLOCKWISE)
        val day2 = day1.copy(day = 2, phase = Phase.DAY_DISCUSSION)

        assertTrue(day2.speechDirectionPending)
        // 方向只认当天的选择：跨了天还没选，就回落到顺时针
        assertEquals(SpeechDirection.CLOCKWISE, day2.speechDirection)
    }

    @Test
    fun `快照往返后方向不丢`() {
        val game = atDiscussion(sheriff = 2).setSpeechDirection(SpeechDirection.COUNTER_CLOCKWISE)
        val restored = WerewolfTable(game = game).toSnapshot().toTable().game

        assertEquals(SpeechDirection.COUNTER_CLOCKWISE, restored.speechDirection)
        assertFalse(restored.speechDirectionPending)
        assertEquals(listOf(1, 9, 8, 7, 6, 5, 4, 3, 2), restored.discussionOrder)
    }

    @Test
    fun `旧档没有方向事件时回落顺时针`() {
        // 模拟旧快照：没有 SPEECH_DIRECTION 事件，decode 后行为不变
        val plain = atDiscussion(sheriff = 2)
        val restored = WerewolfTable(game = plain).toSnapshot().toTable().game

        assertEquals(SpeechDirection.CLOCKWISE, restored.speechDirection)
        assertTrue(restored.speechDirectionPending)
    }

    /** 构造出局状态，用于「警长已死」的用例。 */
    private fun WerewolfGame.killed(seat: Int): WerewolfGame = copy(
        players = players.map {
            if (it.seat == seat) {
                it.copy(alive = false, deathCause = DeathCause.WOLF_KILL, deathDay = day)
            } else {
                it
            }
        },
    )
}
