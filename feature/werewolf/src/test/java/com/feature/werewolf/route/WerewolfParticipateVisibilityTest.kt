package com.feature.werewolf.route

import com.feature.werewolf.engine.WerewolfLive
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfProgress
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 参与局（人类在桌上）的夜里可见性。
 *
 * 夜里「谁在动」本身就是身份。参与局里只要有一个座位的名字冒出来，
 * 那张牌就等于被翻开了——所以夜里除了本人，谁都不该在界面上留下痕迹。
 */
class WerewolfParticipateVisibilityTest {

    /** 1、2 号是狼，3、4、5 号是好人。 */
    private fun game(phase: Phase): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(
            composition = mapOf(
                WerewolfRole.WEREWOLF to 2,
                WerewolfRole.VILLAGER to 3,
            ),
        ),
        roster = (1..5).map { RosterEntry(id = "p$it", name = "玩家$it") },
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
        ),
    ).copy(phase = phase)

    private fun state(phase: Phase, live: WerewolfLive) = WerewolfUiState(
        table = WerewolfTable(game(phase)),
        live = live,
        running = true,
    )

    @Test
    fun `狼人下刀时 参与局看不到狼人的气泡`() {
        // 夜里下刀是并发阶段：「当前是谁」本来就不存在，
        // 但正在下刀的那几个人就是全部狼人——气泡一冒出来，狼就全亮了
        val live = state(
            Phase.NIGHT_WOLF,
            WerewolfLive(seat = 1, phase = Phase.NIGHT_WOLF, reasoning = "刀 3 号比较稳"),
        ).toLive(humanSeat = 2, turn = null, avatars = emptyMap())

        assertNull("参与局的夜里不该出现别人的气泡", live.streaming)
    }

    @Test
    fun `观战时夜里照样看得到气泡`() {
        val live = state(
            Phase.NIGHT_WOLF,
            WerewolfLive(seat = 1, phase = Phase.NIGHT_WOLF, reasoning = "刀 3 号比较稳"),
        ).toLive(humanSeat = null, turn = null, avatars = emptyMap())

        assertNotNull("观战是上帝视角，夜里也要看得见", live.streaming)
    }

    @Test
    fun `自己是狼时 狼人商议看得见`() {
        // 人类自己是 1 号狼：夜聊是他和 2 号狼的正事，必须看得见
        val live = state(
            Phase.NIGHT_WOLF_CHAT,
            WerewolfLive(seat = 2, phase = Phase.NIGHT_WOLF_CHAT, text = "刀 4 号吧"),
        ).toLive(humanSeat = 1, turn = null, avatars = emptyMap())

        assertNotNull("自己是狼时狼人商议要看得见", live.streaming)
    }

    @Test
    fun `自己是好人时 狼人商议看不见`() {
        val live = state(
            Phase.NIGHT_WOLF_CHAT,
            WerewolfLive(seat = 1, phase = Phase.NIGHT_WOLF_CHAT, text = "刀 4 号吧"),
        ).toLive(humanSeat = 3, turn = null, avatars = emptyMap())

        assertNull("好人看不见狼人密谈", live.streaming)
    }

    @Test
    fun `参与局里界面拿不到谁是狼`() {
        val live = state(
            Phase.DAY_DISCUSSION,
            WerewolfLive(seat = 1, phase = Phase.DAY_DISCUSSION, text = "我跳预言家"),
        ).toLive(humanSeat = 3, turn = null, avatars = emptyMap())

        assertTrue(
            "参与中的局里不该把真实阵营发到界面：${live.seats.map { it.seat to it.wolf }}",
            live.seats.none { it.wolf },
        )
        assertTrue("身份名同样不该给", live.seats.all { it.roleLabel == null })
    }

    @Test
    fun `观战时界面拿得到谁是狼`() {
        val live = state(
            Phase.DAY_DISCUSSION,
            WerewolfLive(seat = 1, phase = Phase.DAY_DISCUSSION, text = "我跳预言家"),
        ).toLive(humanSeat = null, turn = null, avatars = emptyMap())

        assertEquals(2, live.seats.count { it.wolf })
        assertEquals(2, live.seats.count { it.roleLabel == "狼人" })
    }

    /** 夜里这五个阶段，任何一个都不该在参与局的界面上留下痕迹。 */
    @Test
    fun `夜里所有阶段都不亮座位也不出气泡`() {
        val nights = listOf(
            Phase.NIGHT_GUARD,
            Phase.NIGHT_WOLF_CHAT,
            Phase.NIGHT_WOLF,
            Phase.NIGHT_SEER,
            Phase.NIGHT_WITCH,
        )
        for (phase in nights) {
            val live = state(
                phase,
                WerewolfLive(seat = 1, phase = phase, text = "夜里的话", reasoning = "夜里在想"),
            ).toLive(humanSeat = 3, turn = null, avatars = emptyMap())

            assertNull("$phase 不该出气泡", live.streaming)
            assertTrue(
                "$phase 不该亮任何座位：${live.seats.filter { it.active }.map { it.seat }}",
                live.seats.none { it.active },
            )
        }
    }

    /** 白天是公开的：发言、投票的时候该看到谁在动。 */
    @Test
    fun `白天发言时该看到说话的人`() {
        val live = WerewolfUiState(
            table = WerewolfTable(game(Phase.DAY_DISCUSSION)),
            live = WerewolfLive(seat = 2, phase = Phase.DAY_DISCUSSION, text = "我跳预言家"),
            // 座位高亮来自引擎进度：引擎此刻在等谁
            progress = WerewolfProgress(
                day = 1,
                phase = Phase.DAY_DISCUSSION,
                step = 1,
                total = 5,
                seat = 2,
            ),
            running = true,
        ).toLive(humanSeat = 3, turn = null, avatars = emptyMap())

        assertNotNull("白天发言要有气泡", live.streaming)
        assertTrue("说话的人要亮起来", live.seats.first { it.seat == 2 }.active)
    }
}
