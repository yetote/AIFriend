package com.feature.werewolf.engine

import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「这句发言值不值得问狼要不要插爆」的筛选规则。
 *
 * 这里锁的是**成本口径**：只有点名 + 表态词才问。多问一次只是多一次请求，
 * 漏掉一次却会让插爆时灵时不灵——所以判据刻意偏松，但「没点名」必须坚决不问。
 *
 * 座位与身份（6 人局）：1/2 狼 · 3 预言家 · 4 女巫 · 5/6 平民。
 */
class ExplodeWatchTest {

    private val roles = listOf(
        WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF,
        WerewolfRole.SEER, WerewolfRole.WITCH,
        WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
    )

    private val game: WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(composition = roles.groupingBy { it }.eachCount()),
        roster = List(roles.size) { RosterEntry("p${it + 1}", "玩家${it + 1}") },
        roles = roles,
    ).let { if (it.phase != Phase.DAY_DISCUSSION) it.copy(phase = Phase.DAY_DISCUSSION) else it }

    @Test
    fun `点名加表态词才问`() {
        assertTrue(ExplodeWatch.shouldAsk(game, 1, "我投 1 号。"))
        assertTrue(ExplodeWatch.shouldAsk(game, 1, "3号查杀 1号，今天必出他。"))
    }

    @Test
    fun `被查杀只是指控，不触发插爆`() {
        // 「1 号是狼」「1 号被我查杀」说的是结论，还没到收票那一步。
        // 这时候问一次，模型很容易答「爆」——等于被点一下就自己把命交出去，
        // 好人连辩都不用听。真正该掐断的是「今天票 1 号」。
        assertFalse(ExplodeWatch.shouldAsk(game, 1, "3 号查杀的是 1 号。"))
        assertFalse(ExplodeWatch.shouldAsk(game, 1, "1 号是狼，我建议大家都注意他。"))
        assertFalse(ExplodeWatch.shouldAsk(game, 1, "我踩 1 号，他这轮说得太虚。"))
    }

    @Test
    fun `只被夸一句不打扰`() {
        assertFalse("没有威胁动作", ExplodeWatch.shouldAsk(game, 1, "1 号看起来挺真诚的。"))
    }

    @Test
    fun `没点名就不问，哪怕全场都在喊出人`() {
        assertFalse(ExplodeWatch.shouldAsk(game, 1, "今天必须出一个人。"))
        assertFalse("「2 个人没说话」不是在点 2 号", ExplodeWatch.shouldAsk(game, 1, "今天有 2 个人没说话。"))
    }

    @Test
    fun `点了队友也要问`() {
        // 2 号和 1 号是队友：有人要投 1 号，2 号也有理由爆
        assertTrue(ExplodeWatch.shouldAsk(game, 2, "我建议今天就出 1 号。"))
    }

    @Test
    fun `喊名字也算点名`() {
        assertTrue(ExplodeWatch.shouldAsk(game, 1, "玩家1 这轮发言很虚，我先投他。"))
    }

    @Test
    fun `11号和12号不会误触发1号和2号的插爆检查`() {
        assertFalse("11 号不能误中 1 号", ExplodeWatch.shouldAsk(game, 1, "今天必投 11 号，先出 11号。"))
        assertFalse("12 号不能误中 2 号", ExplodeWatch.shouldAsk(game, 2, "我归票 12 号，大家跟我出 12号。"))
    }

    @Test
    fun `插爆上下文压缩包含已起跳与先前发言立场且标记队友`() {
        val table = WerewolfTable(
            game = game,
            transcript = listOf(
                WerewolfMessage(1, Phase.DAY_DISCUSSION, 3, MessageKind.SPEECH, "我是预言家，昨夜验了 5 号金水。"),
                WerewolfMessage(1, Phase.DAY_DISCUSSION, 1, MessageKind.SPEECH, "我先听后置位怎么归票，暂压 3 号。"),
                WerewolfMessage(1, Phase.DAY_DISCUSSION, 4, MessageKind.SPEECH, "先投 3 号；1 号若改口我再跟 1 号。"),
            ),
        )
        val context = ExplodeWatch.briefContext(table, wolfSeat = 2)
        assertTrue("应包含跳预言家信息", context.contains("3号跳预言家"))
        assertTrue("应标出队友发言", context.contains("1号(队友)"))
        assertFalse("末尾待判断的 4 号正文不重复写进先前摘要", context.contains("4号:"))
        assertTrue("总长需控制在轻量预算内", context.length <= 260)
    }
}
