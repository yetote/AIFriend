package com.feature.werewolf.route

import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.engine.syncPublicEvents
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.replay.ReplayTimeline
import com.ui.werewolf.data.UIWerewolfLineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheriffSignupUiMappingTest {

    private fun initialGame(): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(
            composition = mapOf(
                WerewolfRole.WEREWOLF to 2,
                WerewolfRole.SEER to 1,
                WerewolfRole.VILLAGER to 3,
            ),
            hasSheriff = true,
        ),
        roster = (1..6).map { RosterEntry(id = "p$it", name = "玩家$it") },
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.SEER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
        ),
    ).copy(phase = Phase.DAY_SHERIFF_SIGNUP)

    @Test
    fun `上警完成后警上候选人挂起警上标记，退水立刻摘除，当选后由警徽接棒`() {
        val before = initialGame()
        val afterSignup = before.signUpSheriff(setOf(1, 3, 5))
        val tableSignup = WerewolfTable(afterSignup).syncPublicEvents(before)

        val liveSignup = WerewolfUiState(table = tableSignup, running = true).toLive(
            humanSeat = 3,
            turn = null,
            avatars = emptyMap(),
        )
        assertEquals(
            "1、3、5 号应挂起警上标记",
            setOf(1, 3, 5),
            liveSignup.seats.filter { it.sheriffCandidate }.map { it.seat }.toSet(),
        )
        assertTrue("尚未选出警长前不应有人挂警徽", liveSignup.seats.none { it.sheriff })

        // 5 号退水后，立刻摘除 5 号的警上标记
        val beforeWithdraw = afterSignup.beginSheriffWithdraw()
        val afterWithdraw = beforeWithdraw.withdrawSheriff(setOf(5))
        val liveWithdraw = WerewolfUiState(table = WerewolfTable(afterWithdraw), running = true).toLive(
            humanSeat = 3,
            turn = null,
            avatars = emptyMap(),
        )
        assertEquals(
            "退水后只剩 1、3 号挂警上标记",
            setOf(1, 3),
            liveWithdraw.seats.filter { it.sheriffCandidate }.map { it.seat }.toSet(),
        )

        // 3 号当选警长后，所有警上标记清空，仅 3 号挂警徽
        val afterElect = afterWithdraw.castSheriffVotes(mapOf(2 to 3, 4 to 3, 6 to 1))
        val liveElected = WerewolfUiState(table = WerewolfTable(afterElect), running = true).toLive(
            humanSeat = 3,
            turn = null,
            avatars = emptyMap(),
        )
        assertTrue("选出警长后不再显示警上标记", liveElected.seats.none { it.sheriffCandidate })
        assertEquals(
            "3 号当选警长",
            setOf(3),
            liveElected.seats.filter { it.sheriff }.map { it.seat }.toSet(),
        )
    }

    @Test
    fun `上警播报映射为结构化上警名单气泡卡，同时包含警上与警下座位`() {
        val before = initialGame()
        val afterSignup = before.signUpSheriff(setOf(1, 3, 5))
        val table = WerewolfTable(afterSignup).syncPublicEvents(before)

        val live = WerewolfUiState(table = table, running = true).toLive(
            humanSeat = 3,
            turn = null,
            avatars = emptyMap(),
        )

        val rosterLine = live.lines.firstOrNull { it.kind == UIWerewolfLineKind.SHERIFF_ROSTER }
        assertNotNull("上警播报应渲染为结构化上警名单卡", rosterLine)
        val roster = rosterLine!!.sheriffRoster!!
        assertEquals(listOf(1, 3, 5), roster.runners.map { it.seat })
        assertEquals(listOf(2, 4, 6), roster.offPodium.map { it.seat })
        assertTrue("本人 3 号应标为 isSelf", roster.runners.first { it.seat == 3 }.isSelf)
        assertFalse("其他座位不应标为 isSelf", roster.runners.first { it.seat == 1 }.isSelf)
    }

    @Test
    fun `回放时间线按帧推进警上标记并在当选后清空`() {
        val g0 = initialGame()
        val g1 = g0.signUpSheriff(setOf(1, 3, 5))
        var table = WerewolfTable(g1).syncPublicEvents(g0)

        // 模拟一条竞选发言
        table = table.append(
            WerewolfMessage(day = 1, phase = Phase.DAY_SHERIFF_SPEECH, seat = 1, kind = MessageKind.SPEECH, text = "我发金水")
        )
        val g2 = g1.beginSheriffWithdraw()
        val g3 = g2.withdrawSheriff(setOf(5))
        table = table.copy(game = g3).syncPublicEvents(g2)

        val g4 = g3.castSheriffVotes(mapOf(2 to 3, 4 to 3, 6 to 1))
        table = table.copy(game = g4).syncPublicEvents(g3)

        val frames = ReplayTimeline.build(table, humanSeat = null, godView = true)
        val signupFrame = frames.first { it.phase == Phase.DAY_SHERIFF_SIGNUP }
        assertEquals(setOf(1, 3, 5), signupFrame.sheriffCandidates)

        val withdrawFrame = frames.first { it.phase == Phase.DAY_SHERIFF_WITHDRAW }
        assertEquals(setOf(1, 3), withdrawFrame.sheriffCandidates)

        val electedFrame = frames.last()
        assertEquals(3, electedFrame.sheriffSeat)
        assertTrue("当选帧警上标记应清空", electedFrame.sheriffCandidates.isEmpty())
    }

    @Test
    fun `无历史事件兜底解析播报文案时不误吃天数数字`() {
        val game = initialGame().copy(day = 2)
        val message = WerewolfMessage(
            day = 2,
            phase = Phase.DAY_SHERIFF_SIGNUP,
            seat = null,
            kind = MessageKind.DIVINE,
            text = "第2天：上警的是 4、6 号",
        )
        val roster = buildSheriffRoster(message, game, humanSeat = 4)
        assertNotNull(roster)
        assertEquals(listOf(4, 6), roster!!.runners.map { it.seat })
        assertEquals(listOf(1, 2, 3, 5), roster.offPodium.map { it.seat })
    }
}
