package com.feature.werewolf.engine

import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.SpeechDirection
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.prompt.WerewolfPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 每轮记录板的测试。
 *
 * 判据是**两头都不能错**：
 * - 该给的没给 → 模型把还没开口的人当成已经表过态，或者不知道自己排在第几个；
 * - 不该给的给了 → 狼队商议的顺序漏给好人，等于夜里提前泄底。
 */
class RoundBoardTest {

    private val roles = listOf(
        WerewolfRole.WEREWOLF,
        WerewolfRole.WEREWOLF,
        WerewolfRole.SEER,
        WerewolfRole.WITCH,
        WerewolfRole.HUNTER,
        WerewolfRole.VILLAGER,
        WerewolfRole.VILLAGER,
        WerewolfRole.VILLAGER,
    )

    private fun roster(size: Int) = List(size) { RosterEntry("p${it + 1}", "${it + 1}号") }

    private fun game(): WerewolfGame = WerewolfGame.withRoles(WerewolfConfig(), roster(8), roles)

    /** 停在白天讨论，2 号是警长，已按警左定序。 */
    private fun atDiscussion(): WerewolfGame =
        game().copy(phase = Phase.DAY_DISCUSSION, sheriffSeat = 2)
            .setSpeechDirection(SpeechDirection.SHERIFF_LEFT)

    @Test
    fun `白天讨论给出顺序，并分清谁说过谁还没说`() {
        val game = atDiscussion().markSpoken(3).markSpoken(4)

        val text = RoundBoard.of(game, seat = 6)

        assertTrue("发言顺序要原样给出来：$text", text.contains("发言顺序：3-4-5-6-7-8-1-2"))
        assertTrue("已发言名单：$text", text.contains("已经发过言：3-4"))
        assertTrue("未发言名单：$text", text.contains("还没发言：5-6-7-8-1-2"))
        assertTrue("要知道自己排第几：$text", text.contains("你是 6 号"))
        assertTrue("要知道自己排第几：$text", text.contains("本轮第 4 个发言"))
    }

    @Test
    fun `顺序用连字符排开，和用户给的样子一致`() {
        val text = RoundBoard.of(atDiscussion(), seat = 1)

        assertTrue("顺序是 3-4-5-6-7-8-1-2 这样的连排：$text", text.contains("3-4-5-6-7-8-1-2"))
        assertTrue("不要写成带空格的列表：$text", !text.contains("3, 4, 5"))
    }

    @Test
    fun `一个人都还没说时，名单要说人话`() {
        val text = RoundBoard.of(atDiscussion(), seat = 3)

        assertTrue(text.contains("已经发过言：还没有人发言"))
        assertTrue(text.contains("还没发言：3-4-5-6-7-8-1-2"))
    }

    @Test
    fun `警长还没选方向时不输出，顺序此时还不存在`() {
        // 2 号是警长但还没定序：此时 discussionOrder 只是默认值，不是真实顺序，
        // 写进提示词等于骗模型。
        val game = game().copy(phase = Phase.DAY_DISCUSSION, sheriffSeat = 2)

        assertTrue(game.speechDirectionPending)
        assertEquals("", RoundBoard.of(game, seat = 6))
    }

    @Test
    fun `狼队商议的顺序只有狼人看得到`() {
        val game = game().copy(phase = Phase.NIGHT_WOLF_CHAT, nightHead = 2)

        val wolf = RoundBoard.of(game, seat = 1)
        assertTrue("狼人要看得到顺序：$wolf", wolf.contains("发言顺序：2-1"))
        assertEquals("好人拿不到狼队的顺序", "", RoundBoard.of(game, seat = 3))
    }

    @Test
    fun `投票这类没有发言顺序的阶段不输出`() {
        assertEquals("", RoundBoard.of(game().copy(phase = Phase.DAY_VOTE), seat = 3))
        assertEquals("", RoundBoard.of(game().copy(phase = Phase.NIGHT_SEER), seat = 3))
        assertEquals("", RoundBoard.of(game().copy(phase = Phase.NIGHT_WITCH), seat = 4))
    }

    @Test
    fun `竞选发言按候选人名单，遗言按待遗言名单`() {
        val sheriff = game().copy(phase = Phase.DAY_SHERIFF_SPEECH, sheriffCandidates = listOf(3, 5, 7))
        assertTrue(RoundBoard.of(sheriff, seat = 5).contains("发言顺序：3-5-7"))

        val last = game().copy(phase = Phase.DAY_LAST_WORDS, pendingLastWords = listOf(4, 6))
        assertTrue(RoundBoard.of(last, seat = 6).contains("发言顺序：4-6"))
    }

    @Test
    fun `平票重谈时上一轮的发言名单作废`() {
        val game = game().copy(phase = Phase.NIGHT_WOLF_CHAT, nightHead = 2).markSpoken(2)
        assertEquals(listOf(2), game.currentRoundSpokenSeats())

        // 狼刀平票重谈：轮次 +1，作用域 key 变了，上一轮「已经说过」不再作数
        val reopened = game.copy(wolfVoteRound = 2)
        assertEquals(emptyList<Int>(), reopened.currentRoundSpokenSeats())
        assertTrue(RoundBoard.of(reopened, seat = 1).contains("已经发过言：还没有人发言"))
    }

    @Test
    fun `这块板子进到 system prompt 里`() {
        val game = atDiscussion().markSpoken(3)
        val board = RoundBoard.of(game, seat = 6)

        val prompt = WerewolfPrompt.systemPrompt(game, seat = 6, roundBoard = board)

        assertTrue(prompt.contains("【每轮记录】"))
        assertTrue(prompt.contains("发言顺序：3-4-5-6-7-8-1-2"))
        assertTrue(prompt.contains("已经发过言：3"))
        // 不给就一点都不能有：复盘、测试的提示词必须还是原样
        assertTrue(!WerewolfPrompt.systemPrompt(game, seat = 6).contains("【每轮记录】"))
    }
}
