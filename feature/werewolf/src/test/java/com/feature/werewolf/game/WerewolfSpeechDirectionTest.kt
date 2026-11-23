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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 警长选发言顺序：警左 / 警右 / 死左 / 死右。
 *
 * 座位与身份（9 人局）：1/2/3 狼 · 4 预言家 · 5 女巫 · 6 猎人 · 7/8/9 平民。
 * 这些用例验五件事：四种顺序都对、警左 / 警右里警长垫底（归票位）、
 * 平安夜不给死左死右、「每天选一次」的幂等与校验、快照往返后方向不丢。
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
    fun `没选方向时默认警左，从警长下一位起警长垫底`() {
        val game = atDiscussion(sheriff = 2)

        assertTrue(game.speechDirectionPending)
        assertEquals(SpeechDirection.SHERIFF_LEFT, game.speechDirection)
        assertEquals(listOf(3, 4, 5, 6, 7, 8, 9, 1, 2), game.discussionOrder)
    }

    @Test
    fun `警右从警长上一位倒着绕，警长仍然垫底`() {
        val game = atDiscussion(sheriff = 2).setSpeechDirection(SpeechDirection.SHERIFF_RIGHT)

        assertEquals(SpeechDirection.SHERIFF_RIGHT, game.speechDirection)
        // 2 号的右手边是 1 号，倒着数到 3 号收尾，警长 2 号最后归票
        assertEquals(listOf(1, 9, 8, 7, 6, 5, 4, 3, 2), game.discussionOrder)
    }

    @Test
    fun `死左从昨夜死者的下一位起，警长不再垫底`() {
        val game = atDiscussion(sheriff = 2).withDead(5).setSpeechDirection(SpeechDirection.DEATH_LEFT)

        // 5 号倒牌，从 6 号起绕一圈；警长 2 号排在中段
        assertEquals(listOf(6, 7, 8, 9, 1, 2, 3, 4), game.discussionOrder)
    }

    @Test
    fun `死右从昨夜死者的上一位倒着绕`() {
        val game = atDiscussion(sheriff = 2).withDead(5).setSpeechDirection(SpeechDirection.DEATH_RIGHT)

        assertEquals(listOf(4, 3, 2, 1, 9, 8, 7, 6), game.discussionOrder)
    }

    @Test
    fun `多人倒牌时死左死右按座位号最小的那个算`() {
        val game = atDiscussion(sheriff = 2).withDead(3, 7)

        assertEquals(listOf(4, 5, 6, 8, 9, 1, 2), game.discussionOrderFor(SpeechDirection.DEATH_LEFT))
        assertEquals(listOf(2, 1, 9, 8, 6, 5, 4), game.discussionOrderFor(SpeechDirection.DEATH_RIGHT))
    }

    @Test
    fun `平安夜不能选死左死右`() {
        val game = atDiscussion(sheriff = 2)

        assertThrows(IllegalArgumentException::class.java) {
            game.setSpeechDirection(SpeechDirection.DEATH_LEFT)
        }
        // 可选列表里也不该出现这两项
        assertEquals(
            listOf(SpeechDirection.SHERIFF_LEFT, SpeechDirection.SHERIFF_RIGHT),
            SpeechDirection.options(hasDawnDeath = false),
        )
    }

    @Test
    fun `选完方向当天不再询问`() {
        val game = atDiscussion().setSpeechDirection(SpeechDirection.SHERIFF_RIGHT)

        assertFalse(game.speechDirectionPending)
    }

    @Test
    fun `没有警长的局面不选方向，起点和顺序由系统随机`() {
        val game = atDiscussion(sheriff = 2).copy(sheriffSeat = null)

        assertFalse(game.speechDirectionPending)
        val order = game.discussionOrder
        // 不写死从谁起：随机起点 + 随机方向，但一定绕满一圈、每人一次
        assertEquals((1..9).toList(), order.sorted())
        // 同一局面反复取要一致：续档、复盘、拼上下文都得对齐
        assertEquals(order, game.discussionOrder)
    }

    @Test
    fun `没活着的警长也不能选`() {
        val dead = atDiscussion(sheriff = 2).killed(2)

        assertThrows(IllegalArgumentException::class.java) {
            dead.setSpeechDirection(SpeechDirection.SHERIFF_RIGHT)
        }
    }

    @Test
    fun `一天只能选一次`() {
        val game = atDiscussion().setSpeechDirection(SpeechDirection.SHERIFF_LEFT)

        assertThrows(IllegalArgumentException::class.java) {
            game.setSpeechDirection(SpeechDirection.SHERIFF_RIGHT)
        }
    }

    @Test
    fun `选方向是公开事件，写进了历史`() {
        val game = atDiscussion().setSpeechDirection(SpeechDirection.SHERIFF_RIGHT)

        val event = game.history.filterIsInstance<GameEvent.SpeechDirectionChosen>().single()
        assertEquals(2, event.seat)
        assertEquals(SpeechDirection.SHERIFF_RIGHT, event.direction)
    }

    @Test
    fun `第二天的方向重新询问，昨天的选择不管用`() {
        val day1 = atDiscussion().setSpeechDirection(SpeechDirection.SHERIFF_RIGHT)
        val day2 = day1.copy(day = 2, phase = Phase.DAY_DISCUSSION)

        assertTrue(day2.speechDirectionPending)
        // 方向只认当天的选择：跨了天还没选，就回落到警左
        assertEquals(SpeechDirection.SHERIFF_LEFT, day2.speechDirection)
    }

    @Test
    fun `快照往返后方向不丢`() {
        val game = atDiscussion(sheriff = 2).setSpeechDirection(SpeechDirection.SHERIFF_RIGHT)
        val restored = WerewolfTable(game = game).toSnapshot().toTable().game

        assertEquals(SpeechDirection.SHERIFF_RIGHT, restored.speechDirection)
        assertFalse(restored.speechDirectionPending)
        assertEquals(listOf(1, 9, 8, 7, 6, 5, 4, 3, 2), restored.discussionOrder)
    }

    @Test
    fun `旧档没有方向事件时回落警左`() {
        // 模拟旧快照：没有 SPEECH_DIRECTION 事件，decode 后行为不变
        val plain = atDiscussion(sheriff = 2)
        val restored = WerewolfTable(game = plain).toSnapshot().toTable().game

        assertEquals(SpeechDirection.SHERIFF_LEFT, restored.speechDirection)
        assertTrue(restored.speechDirectionPending)
    }

    @Test
    fun `旧档的顺时针逆时针读成警左警右`() {
        // v22 之前事件里存的是枚举名，老存档续档不能直接崩
        assertEquals(SpeechDirection.SHERIFF_LEFT, SpeechDirection.ofLabel("CLOCKWISE"))
        assertEquals(SpeechDirection.SHERIFF_RIGHT, SpeechDirection.ofLabel("COUNTER_CLOCKWISE"))
        assertEquals(SpeechDirection.DEATH_LEFT, SpeechDirection.ofLabel("死左"))
        assertNull(SpeechDirection.ofLabel("斜着来"))
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

    /** 造一个「今天天亮有人倒牌」的局面：补 Dawn 事件，并把那几个人标成出局。 */
    private fun WerewolfGame.withDead(vararg seats: Int): WerewolfGame = copy(
        history = history + GameEvent.Dawn(day, seats.toList()),
        players = players.map {
            if (it.seat in seats) {
                it.copy(alive = false, deathCause = DeathCause.WOLF_KILL, deathDay = day)
            } else {
                it
            }
        },
    )
}
