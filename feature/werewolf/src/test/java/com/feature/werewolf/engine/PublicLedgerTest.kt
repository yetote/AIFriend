package com.feature.werewolf.engine

import com.feature.werewolf.game.Ballot
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公开台账的测试。
 *
 * 判据是**两个方向都不能错**：
 * - 该摘的没摘到 → 模型第 3 天开始缺材料，或者漏掉「我是真预言家」「验到 3 号是狼」；
 * - 不该摘的摘进来（转述别人的验人、跨分号硬拼、非预言家讨论金水）→ 台账制造假证据污染全桌记忆。
 */
class PublicLedgerTest {

    private fun table(
        vararg messages: WerewolfMessage,
        history: List<GameEvent> = emptyList(),
    ): WerewolfTable {
        val roster = (1..12).map { RosterEntry("p$it", "玩家$it") }
        val game = WerewolfGame.deal(WerewolfBoards.configOf(WerewolfBoards.GUARD_12), roster)
            .copy(history = history)
        return WerewolfTable(game, messages.toList())
    }

    private fun speech(
        seat: Int,
        text: String,
        day: Int = 1,
        phase: Phase = Phase.DAY_DISCUSSION,
        audience: Set<Int>? = null,
    ) = WerewolfMessage(
        day = day,
        phase = phase,
        seat = seat,
        kind = MessageKind.SPEECH,
        text = text,
        audience = audience,
    )

    private fun ballot(day: Int, vararg votes: Pair<Int, Int?>) = WerewolfMessage(
        day = day,
        phase = Phase.DAY_VOTE,
        seat = null,
        kind = MessageKind.BALLOT,
        text = "唱票",
        ballot = BallotCard(votes.map { Ballot(it.first, it.second) }),
    )

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
    fun `兼容我是真预言家与验到X号是狼，绝不跨分号或转述伪造验人`() {
        // 完整复现 12 人真实对局里的所有台账事故：
        // 1. 2 号说「我是真预言家，首夜验的4号是好人」曾被漏抓；
        // 2. 6 号说「验到3号是狼」曾被漏抓，随后「补验4号的心路；轮到自己被报查杀」被错拼成「6号验4号狼」；
        // 3. 5 号、7 号、2 号转述别人验人曾全部被错记成自己验的；
        // 4. 1 号退水并声明诈身份后必须标明已退水/撤回。
        val text = PublicLedger.of(
            table(
                speech(1, "我是预言家，昨晚验的2号金水。", phase = Phase.DAY_SHERIFF_SPEECH),
                speech(2, "我是真预言家，首夜验的4号是好人。", phase = Phase.DAY_SHERIFF_SPEECH),
                speech(4, "我是预言家，昨天验的11号，好人，2号给我发金水，我反水立警。", phase = Phase.DAY_SHERIFF_SPEECH),
                speech(6, "我是预言家，首夜按前置位顺位摸，验到3号是狼。", phase = Phase.DAY_SHERIFF_SPEECH),
                speech(1, "我退水。", phase = Phase.DAY_SHERIFF_WITHDRAW),
                speech(5, "配上 6 号跳预言家验 3 号是查杀，我这一票投 3 号。"),
                speech(1, "你2号自己跳预言家，首夜报4号是金水，结果4号反水。我这一票投3号。"),
                speech(6, "3号警上亲口说2号“只报结果我记一笔”，要2号下轮补验4号的心路；轮到自己被报查杀，他就反咬。我这一票投3号。"),
                speech(7, "4号连票向都说不圆，我凭什么信他验11号那张金水。", day = 2),
                speech(2, "刚才他还改口说自己验了8号是好人，这套逻辑前后完全接不上。", day = 2),
                speech(1, "2号别往我身上扣预言家帽子，我警上跳预就是诈身份试悍跳的，早就退水了。", day = 2),
                history = listOf(GameEvent.SheriffWithdrawn(day = 1, seats = listOf(1))),
            )
        )

        // 四个真声称全都在
        assertTrue("2 号的「我是真预言家」必须记入：$text", text.contains("2 号自称预言家"))
        assertTrue("预言家有 4 个人声称：$text", text.contains("预言家有 4 个人声称"))
        assertTrue("1 号要标注已退水/诈身份：$text", text.contains("1 号自称预言家（第 1 天，已退水/自称诈身份）"))

        // 真实验人全都在
        assertTrue("1 号验 2 好人：$text", text.contains("1 号称第 1 天验 2 号是好人"))
        assertTrue("2 号验 4 好人：$text", text.contains("2 号称第 1 天验 4 号是好人"))
        assertTrue("4 号验 11 好人：$text", text.contains("4 号称第 1 天验 11 号是好人"))
        assertTrue("6 号验到 3 号是狼必须抓到：$text", text.contains("6 号称第 1 天验 3 号是狼人"))

        // 所有转述与跨分号假证全部消灭
        assertFalse("6 号绝不能被伪造验 4 号狼：$text", text.contains("6 号称第 1 天验 4 号"))
        assertFalse("5 号没跳预，不能记成验 3 号：$text", text.contains("5 号称"))
        assertFalse("7 号没跳预，不能记成验 11 号：$text", text.contains("7 号称"))
        assertFalse("2 号转述别人验 8 号，不能记成 2 号验 8 号：$text", text.contains("2 号称第 2 天验 8 号"))
        assertFalse("1 号转述 2 号报 4 号金水，不能记成 1 号验 4 号：$text", text.contains("1 号称第 1 天验 4 号"))
    }

    @Test
    fun `票型对账要标出话和票不一致的人，并支持今天出X写法`() {
        val text = PublicLedger.of(
            table(
                speech(1, "我这一票投 3 号。"),
                speech(2, "我今天投 3 号。"),
                speech(4, "今天出2，明天出4后天出6。今天出2。"),
                ballot(1, 1 to 3, 2 to 5, 3 to null, 4 to 8),
            )
        )
        assertTrue("说到做到", text.contains("1 号说投 3 号，实际投了 3 号"))
        assertTrue("说一套投一套", text.contains("2 号说投 3 号，实际投了 5 号  ← 话和票对不上"))
        assertTrue("弃票也是事实", text.contains("3 号投票前没说票向，实际弃票"))
        assertTrue("「今天出2」算票向承诺且不把「明天出4」误当今天：$text", text.contains("4 号说投 2 号，实际投了 8 号  ← 话和票对不上"))
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
    fun `假设句和引号里的转述不算声称身份`() {
        val text = PublicLedger.of(
            table(
                speech(4, "如果我是预言家，我第一天就会验 1 号。今天先听大家的。"),
                speech(5, "刚才 2 号喊「我是真预言家」，我觉得站不住。"),
            )
        )
        assertFalse("打比方不能被记成对跳", text.contains("4 号自称预言家"))
        assertFalse("引用别人原话不能记成本人声称", text.contains("5 号自称预言家"))
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

    @Test
    fun `预言家警徽流按最新一版记入，多子句连续报验人不漏抓，非预言家与转述忽略`() {
        val text = PublicLedger.of(
            table(
                speech(2, "我是真预言家，第一夜验了4号好人，警徽流留7号、9号。", day = 1, phase = Phase.DAY_SHERIFF_SPEECH),
                speech(5, "2号警徽流留7号、9号太水了，我不信他。", day = 1),
                speech(2, "昨晚验了7号是狼人，2号逻辑没毛病，我警徽流改留9号、11号。", day = 2),
                speech(6, "我是预言家，第一夜验了3号查杀，昨晚验了8号金水，我警徽流先验10号顺验12号。", day = 2),
            )
        )
        assertTrue("2 号第 1 夜验 4 好人：$text", text.contains("2 号称第 1 天验 4 号是好人"))
        assertTrue("2 号第 2 夜验 7 狼人：$text", text.contains("2 号称第 2 天验 7 号是狼人"))
        assertTrue("6 号同句两验全抓到：$text", text.contains("6 号称第 2 天验 3 号是狼人") && text.contains("6 号称第 2 天验 8 号是好人"))
        assertTrue("2 号警徽流更新为最新版 9→11：$text", text.contains("2 号（第 2 天）留 9号→11号"))
        assertFalse("2 号旧警徽流应被新流覆盖：$text", text.contains("7号→9号"))
        assertTrue("6 号警徽流记入：$text", text.contains("6 号（第 2 天）留 10号→12号"))
        assertFalse("5 号不是预言家，转述警徽流不入账：$text", text.contains("5 号（"))
    }

    @Test
    fun `女巫起跳者报出的银水与毒口入账，假设与计划与非女巫转述绝不污染`() {
        val text = PublicLedger.of(
            table(
                speech(4, "我不藏了，我是女巫，第一夜救了5号，昨晚毒了8号。", day = 2),
                speech(7, "4号说他昨晚救了5号、毒了8号，我觉得他在编。", day = 2),
                speech(9, "我跳女巫，第一夜没救3号，今晚打算毒10号，如果今晚不平安我就毒11号。", day = 2),
            )
        )
        assertTrue("4 号救 5 号入账：$text", text.contains("4 号称第 2 天救了 5 号"))
        assertTrue("4 号毒 8 号入账：$text", text.contains("4 号称第 2 天毒了 8 号"))
        assertFalse("7 号没跳女巫，转述不入账：$text", text.contains("7 号称"))
        assertFalse("9 号否定句「没救3号」不入账：$text", text.contains("9 号称第 2 天救了 3 号"))
        assertFalse("9 号未来计划「今晚打算毒10号」不入账：$text", text.contains("9 号称第 2 天毒了 10 号"))
        assertFalse("9 号假设句「如果…就毒11号」不入账：$text", text.contains("9 号称第 2 天毒了 11 号"))
    }
}
