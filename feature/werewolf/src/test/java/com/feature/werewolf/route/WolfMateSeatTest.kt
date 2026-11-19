package com.feature.werewolf.route

import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.ui.werewolf.data.UIWerewolfLive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 牌桌上的「狼队友」标记。
 *
 * 这条信息不是剧透：线下入夜一睁眼，狼队第一件事就是认队友。不标出来，
 * 人类玩家只能靠夜聊猜，白天连「不能投自己人」都做不到。
 * 反过来，好人局和观战绝不能出现这个标记——那是真的泄漏。
 */
class WolfMateSeatTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry(id = "p$it", name = "玩家$it") }

    /** 1、2、3 狼；4 预言家；5 女巫；6 猎人；7、8、9 平民。 */
    private fun standard9(): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(composition = WerewolfConfig.Standard9),
        roster = roster(9),
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.SEER,
            WerewolfRole.WITCH,
            WerewolfRole.HUNTER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
        ),
    )

    private fun ui(humanSeat: Int?): UIWerewolfLive = WerewolfUiState(
        table = WerewolfTable(standard9().copy(phase = Phase.DAY_DISCUSSION)),
        running = true,
    ).toLive(humanSeat = humanSeat, turn = null, avatars = emptyMap())

    private fun UIWerewolfLive.mates(): Set<Int> =
        seats.filter { it.isWolfMate }.map { it.seat }.toSet()

    @Test
    fun `人类是狼时，队友全部标出来`() {
        val ui = ui(humanSeat = 1)

        assertEquals(setOf(2, 3), ui.mates())
        assertFalse("自己不是自己的队友", ui.seats.first { it.seat == 1 }.isWolfMate)
    }

    @Test
    fun `每个座位看到的队友都是对的`() {
        assertEquals(setOf(1, 3), ui(humanSeat = 2).mates())
        assertEquals(setOf(1, 2), ui(humanSeat = 3).mates())
    }

    @Test
    fun `好人看不到任何狼队友标记`() {
        listOf(4, 5, 6, 7).forEach { seat ->
            assertTrue("$seat 号是好人，不能看到队友标记", ui(humanSeat = seat).mates().isEmpty())
        }
    }

    @Test
    fun `观战不需要队友标记，身份本来就全公开`() {
        val ui = ui(humanSeat = null)

        assertTrue(ui.mates().isEmpty())
        assertEquals("狼人", ui.seats.first { it.seat == 1 }.roleLabel)
    }
}
