package com.feature.werewolf.engine

import com.feature.werewolf.game.Ballot
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公开台账的测试。
 *
 * 判据是**两个方向都不能错**：
 * - 该摘的没摘到 → 模型第 3 天开始又没材料，等于没做；
 * - 不该摘的摘进来（私有消息、否定句里的座位号）→ 台账变成误导，
 *   比没有更糟，因为它看起来像事实。
 */
class PublicLedgerTest {

    private fun table(vararg messages: WerewolfMessage): WerewolfTable {
        val roster = (1..8).map { RosterEntry("p$it", "玩家$it") }
        return WerewolfTable(WerewolfGame.deal(WerewolfConfig(), roster), messages.toList())
    }

    private fun speech(seat: Int, text: String, day: Int = 1, audience: Set<Int>? = null) =
        WerewolfMessage(
            day = day, phase = Phase.DAY_DISCUSSION, seat = seat,
            kind = MessageKind.SPEECH, text = text, audience = audience,
        )

    private fun ballot(day: Int, vararg votes: Pair<Int, Int?>) = WerewolfMessage(
        day = day, phase = Phase.DAY_VOTE, seat = null, kind = MessageKind.BALLOT, text = "唱票",
        ballot = BallotCard(ballotList(votes)),
    )

    private fun ballotList(votes: Array<out Pair<Int, Int?>>) = votes.map { Ballot(it.first, it.second) }

    @Test
    fun `摘出对跳与验人，并点明同一身份有几个人声称`() {
        val text = PublicLedger.of(
            table(
                speech(3, "我是预言家，第 1 夜验了 7 号，是好人。"),
                speech(5, "我跳预言家。我验了 2 号，结果是狼人。"),
            )
        )
        assertTrue("3 号的声称要记下来", text.contains("3 号自称预言家"))
        assertTrue("5 号的声称要记下来", text.contains("5 号自称预言家"))
        assertTrue("对跳要数出来", text.contains("预言家有 2 个人声称"))
        assertTrue("验人要记结果", text.contains("3 号称第 1 天验 7 号是好人"))
        assertTrue("查杀要记结果", text.contains("5 号称第 1 天验 2 号是狼人"))
    }

    @Test
    fun `票型对账要标出话和票不一致的人`() {
        val text = PublicLedger.of(
            table(
                speech(1, "我这一票投 3 号。"),
                speech(2, "我今天投 3 号。"),
                ballot(1, 1 to 3, 2 to 5, 3 to null),
            )
        )
        assertTrue("说到做到", text.contains("1 号说投 3 号，实际投了 3 号"))
        assertTrue("说一套投一套", text.contains("2 号说投 3 号，实际投了 5 号  ← 话和票对不上"))
        assertTrue("弃票也是事实", text.contains("3 号投票前没说票向，实际弃票"))
    }

    @Test
    fun `票向取当天最后一句，否定句不算自己的票`() {
        val text = PublicLedger.of(
            table(
                speech(4, "别投 3 号，他是我认的好人。我这一票投 6 号。"),
                ballot(1, 4 to 6),
            )
        )
        assertTrue("取最后一句的票向", text.contains("4 号说投 6 号，实际投了 6 号"))
        assertFalse("「别投 3 号」不是他的票", text.contains("4 号说投 3 号"))
    }

    @Test
    fun `只摘公开发言，私有消息不进台账`() {
        val text = PublicLedger.of(
            table(
                speech(3, "我是预言家，验了 7 号是好人。", audience = setOf(3)),
                speech(8, "我跳女巫。"),
            )
        )
        assertFalse("私有发言不能泄漏", text.contains("3 号自称预言家"))
        assertTrue("公开的照常记", text.contains("8 号自称女巫"))
    }

    @Test
    fun `假设句不算声称身份`() {
        val text = PublicLedger.of(
            table(speech(4, "如果我是预言家，我第一天就会验 1 号。今天先听大家的。"))
        )
        assertFalse("打比方不能被记成对跳", text.contains("4 号自称预言家"))
    }

    @Test
    fun `没有可摘的内容时不注入`() {
        assertEquals("", PublicLedger.of(table()))
        assertEquals("", PublicLedger.of(table(speech(6, "今天天气不错，大家随便聊聊。"))))
    }

    @Test
    fun `熊身份声明与警长票型都会记入公开台账`() {
        val sheriffBallot = WerewolfMessage(
            day = 1,
            phase = Phase.DAY_SHERIFF_VOTE,
            seat = null,
            kind = MessageKind.BALLOT,
            text = "第 1 天 警长投票：2→4、3→4、5→弃票",
            ballot = BallotCard(listOf(Ballot(2, 4), Ballot(3, 4), Ballot(5, null))),
        )
        val text = PublicLedger.of(
            table(
                speech(6, "我是熊，今天早上咆哮了。"),
                sheriffBallot,
            )
        )
        assertTrue("熊声明要入账", text.contains("6 号自称熊"))
        assertTrue("警长票型要入账", text.contains("第 1 天警长票型：2→4、3→4、5→弃票"))
        assertFalse("警长票不做言行矛盾误报", text.contains("话和票对不上"))
    }
}
