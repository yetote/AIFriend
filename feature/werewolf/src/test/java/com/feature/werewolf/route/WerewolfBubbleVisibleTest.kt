package com.feature.werewolf.route

import com.feature.werewolf.engine.WerewolfLive
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.engine.WerewolfTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 思考气泡的可见性解析：座位覆盖值优先于全局开关。
 *
 * 长按气泡 = 把那个座位的覆盖值写成 false——哪怕全局开着（观战）也不给；
 * 对局设置面板里的单独开关可以反向放行——哪怕全局关着（参与局）也给。
 */
class WerewolfBubbleVisibleTest {

    /** 1 狼；2、3、4 好人。 */
    private fun game(): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(
            composition = mapOf(
                WerewolfRole.WEREWOLF to 1,
                WerewolfRole.VILLAGER to 3,
            ),
        ),
        roster = (1..4).map { RosterEntry(id = "p$it", name = "玩家$it") },
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
        ),
    )

    private fun state(): WerewolfUiState = WerewolfUiState(
        table = WerewolfTable(game().copy(phase = Phase.DAY_DISCUSSION)),
        live = WerewolfLive(seat = 1, phase = Phase.DAY_DISCUSSION, reasoning = "3 号话不对劲"),
        running = true,
    )

    @Test
    fun `长按藏掉的座位 全局开着也不显示思考`() {
        val live = state().toLive(
            humanSeat = null, // 观战：全局恒开
            turn = null,
            avatars = emptyMap(),
            showThinking = true,
            bubbleVisible = mapOf(1 to false),
        )

        assertEquals("", live.streaming!!.reasoning)
    }

    @Test
    fun `单独放行的座位 全局关着也显示思考`() {
        val live = state().toLive(
            humanSeat = 2, // 参与局：默认不给
            turn = null,
            avatars = emptyMap(),
            showThinking = false,
            bubbleVisible = mapOf(1 to true),
        )

        assertTrue(live.streaming!!.reasoning.contains("3 号"))
    }

    @Test
    fun `没有覆盖值时跟全局`() {
        val spectating = state().toLive(humanSeat = null, turn = null, avatars = emptyMap())
        assertTrue(spectating.streaming!!.reasoning.isNotBlank())

        val playing = state().toLive(humanSeat = 2, turn = null, avatars = emptyMap())
        assertEquals("", playing.streaming!!.reasoning)
    }
}
