package com.feature.werewolf.route

import com.feature.werewolf.engine.BallotCard
import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.Ballot
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.ui.werewolf.data.UIWerewolfLineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 唱票卡从领域消息到界面数据的映射。
 *
 * 判据有三条：公开可见、票型一张不漏、半点票的精度不丢。
 * 这三条任何一条错了，牌桌上那张卡就会变成一句没用的话。
 */
class BallotCardMappingTest {

    @Test
    fun `唱票消息映射成带票型的公开卡片`() {
        val message = WerewolfMessage(
            day = 2,
            phase = Phase.DAY_VOTE,
            seat = null,
            kind = MessageKind.BALLOT,
            text = "第 2 天 投票：1→3、2→3、4→弃票",
            ballot = BallotCard(
                ballots = listOf(Ballot(1, 3), Ballot(2, 3), Ballot(4, null)),
                tally = mapOf(3 to 2.5),
            ),
        )
        val game = WerewolfGame.deal(WerewolfConfig(), (1..8).map { RosterEntry("p$it", "玩家$it") })
        val state = WerewolfUiState(table = WerewolfTable(game = game, transcript = listOf(message)))

        // 参与局的普通视角（humanSeat=3）也该看到：票型是公开信息
        val live = state.toLive(humanSeat = 3, turn = null, avatars = emptyMap())
        val line = live.lines.single()

        assertEquals(UIWerewolfLineKind.BALLOT, line.kind)
        assertEquals("放逐投票", line.ballot!!.title)
        assertEquals(3, line.ballot!!.totalVotes)
        assertEquals(1, line.ballot!!.abstained)
        assertEquals(2.5, line.ballot!!.tally.single().votes, 0.0)
        assertNotNull("弃票那张在卡里", line.ballot!!.ballots.single { it.voterSeat == 4 })
    }

    @Test
    fun `警长投票的唱票卡标题是警长投票`() {
        val message = WerewolfMessage(
            day = 1,
            phase = Phase.DAY_SHERIFF_VOTE,
            seat = null,
            kind = MessageKind.BALLOT,
            text = "第 1 天 警长投票：1→3",
            ballot = BallotCard(ballots = listOf(Ballot(1, 3)), tally = mapOf(3 to 1.0)),
        )
        val game = WerewolfGame.deal(WerewolfConfig(), (1..8).map { RosterEntry("p$it", "玩家$it") })
        val state = WerewolfUiState(table = WerewolfTable(game = game, transcript = listOf(message)))

        val live = state.toLive(humanSeat = null, turn = null, avatars = emptyMap())
        assertEquals("警长投票", live.lines.single().ballot!!.title)
        assertEquals(1.0, live.lines.single().ballot!!.tally.single().votes, 0.0)
    }
}
