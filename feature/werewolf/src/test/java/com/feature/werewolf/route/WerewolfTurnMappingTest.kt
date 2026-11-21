package com.feature.werewolf.route

import com.feature.werewolf.engine.HumanTurn
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WitchStock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「现在轮到我做什么」这个面板的映射。
 *
 * 面板上的每个按钮都是一次**引擎一定会拒绝**的动作就是 bug：
 * 玩家点了、界面没反应、她的操作被静默换成「什么都不做」。
 * 女巫救自己就是现成的例子——引擎只允许第一夜自救，界面却一直摆着按钮。
 */
class WerewolfTurnMappingTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry(id = "p$it", name = "玩家$it") }

    /** 1、2 狼；3 预言家；4 女巫；5、6 平民。 */
    private fun gameWithWitch(): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(
            composition = mapOf(
                WerewolfRole.WEREWOLF to 2,
                WerewolfRole.SEER to 1,
                WerewolfRole.WITCH to 1,
                WerewolfRole.VILLAGER to 2,
            ),
        ),
        roster = roster(6),
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.SEER,
            WerewolfRole.WITCH,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
        ),
    )

    private fun waiting() = HumanTurn(
        systemPrompt = "",
        messages = emptyList(),
        jsonMode = true,
    )

    private fun witchTurn(game: WerewolfGame, seat: Int) =
        buildTurn(game, humanSeat = seat, me = game.playerAt(seat), waiting = waiting())

    @Test
    fun `女巫被刀且救不了自己时，不给解药按钮`() {
        // 第 2 夜女巫自己被刀：引擎只允许第一夜自救，这个按钮点了会被拒绝，
        // 玩家的操作会被静默换成「不用药」——她还以为自己救了自己
        val game = gameWithWitch().copy(
            phase = Phase.NIGHT_WITCH,
            day = 2,
            tonightVictim = 4,
        )

        val turn = witchTurn(game, 4)

        assertFalse("不能自救就不该摆解药按钮", turn!!.canHeal)
        assertTrue("要告诉她今晚被刀的是自己", turn.hint!!.contains("救不了自己"))
    }

    @Test
    fun `女巫救别人时照常给解药按钮`() {
        val game = gameWithWitch().copy(
            phase = Phase.NIGHT_WITCH,
            day = 2,
            tonightVictim = 5,
        )

        val turn = witchTurn(game, 4)

        assertTrue("能救人就要给按钮", turn!!.canHeal)
        assertTrue(turn.healTarget!!.contains("5 号"))
    }

    @Test
    fun `第一夜女巫被刀可以自救`() {
        val game = gameWithWitch().copy(
            phase = Phase.NIGHT_WITCH,
            day = 1,
            tonightVictim = 4,
        )

        val turn = witchTurn(game, 4)

        assertTrue("第一夜能自救", turn!!.canHeal)
    }

    @Test
    fun `解药用完后女巫夜里零信息——不报刀口也不报平安夜`() {
        // 刀口信息只在解药还可用时披露。解药一空，今晚谁被刀、是不是平安夜、
        // 甚至自己被刀，都不再告诉她；白天的死讯播报是全场公开的渠道。
        val knifed = gameWithWitch().copy(
            phase = Phase.NIGHT_WITCH,
            day = 3,
            tonightVictim = 5,
            witch = WitchStock(antidoteAvailable = false, poisonAvailable = true),
        )

        val turn = witchTurn(knifed, 4)

        assertFalse("解药空了不该有解药按钮", turn!!.canHeal)
        assertFalse("不能泄露刀口座位号", turn.hint.orEmpty().contains("5 号"))
        assertFalse("不能泄露刀口本身", turn.hint.orEmpty().contains("被袭") || turn.hint.orEmpty().contains("被刀"))
        assertTrue("只解释她剩下的抉择", turn.hint!!.contains("毒药"))

        // 平安夜同样不报：「今晚没人被袭」也是刀口信息
        val quiet = gameWithWitch().copy(
            phase = Phase.NIGHT_WITCH,
            day = 3,
            tonightVictim = null,
            witch = WitchStock(antidoteAvailable = false, poisonAvailable = false),
        )
        val quietTurn = witchTurn(quiet, 4)
        assertFalse(
            "平安夜也不许报",
            quietTurn!!.hint.orEmpty().contains("没有人被狼人袭击"),
        )
    }
}
