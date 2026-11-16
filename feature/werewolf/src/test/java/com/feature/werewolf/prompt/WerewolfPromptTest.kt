package com.feature.werewolf.prompt

import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.SeerCheck
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 入场提示词的测试。
 *
 * 重点不是「文案好不好」，而是**谁能看到什么**：
 * 私有信息一旦漏进别人的 system prompt，这条泄漏在游戏里是不可逆的——
 * AI 会直接说出「我知道 5 号是狼」，而那本该是它不该知道的事。
 */
class WerewolfPromptTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry(id = "p$it", name = "玩家$it") }

    /** 标准 8 人局：1、2 狼；3 预言家；4 女巫；5 猎人；6、7、8 平民。 */
    private fun standardGame(): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(),
        roster = roster(8),
        roles = listOf(
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

    // ------------------------------------------------------------------
    // 身份
    // ------------------------------------------------------------------

    @Test
    fun `身份和座位出现在提示词里`() {
        val game = standardGame()
        val prompt = WerewolfPrompt.systemPrompt(game, seat = 3)
        assertTrue(prompt.contains("你是 3 号玩家「玩家3」"))
        assertTrue(prompt.contains("预言家"))
    }

    @Test
    fun `人设放在最前面，且不会重复身份段`() {
        val prompt = WerewolfPrompt.systemPrompt(standardGame(), seat = 6, persona = "你说话很冲，喜欢抬杠。")
        assertTrue(prompt.startsWith("你说话很冲"))
        assertTrue(prompt.contains("平民"))
    }

    // ------------------------------------------------------------------
    // 私有信息隔离
    // ------------------------------------------------------------------

    @Test
    fun `狼队友只出现在狼人的提示词里`() {
        val game = standardGame()
        val wolf = WerewolfPrompt.systemPrompt(game, seat = 1)
        assertTrue(wolf.contains("你的狼队友"))
        assertTrue(wolf.contains("2 号"))

        // 好人不能看到任何「队友」信息
        listOf(3, 4, 5, 6).forEach { seat ->
            val good = WerewolfPrompt.systemPrompt(game, seat)
            assertFalse("$seat 号（好人）不该看到狼队友信息", good.contains("狼队友"))
            assertFalse("$seat 号（好人）不该知道 1 号是狼", good.contains("1 号是狼"))
        }
    }

    @Test
    fun `查验记录只给预言家`() {
        val game = standardGame().copy(
            seerChecks = listOf(
                SeerCheck(day = 1, targetSeat = 2, isWerewolf = true),
                SeerCheck(day = 2, targetSeat = 6, isWerewolf = false),
            )
        )

        val seer = WerewolfPrompt.systemPrompt(game, seat = 3)
        assertTrue(seer.contains("查验 2 号 → 狼人"))
        assertTrue(seer.contains("查验 6 号 → 好人"))
        assertTrue(seer.contains("还没查验过的存活玩家"))

        listOf(1, 4, 5, 6, 7).forEach { seat ->
            val other = WerewolfPrompt.systemPrompt(game, seat)
            assertFalse("$seat 号不该看到查验记录", other.contains("查验 2 号"))
        }
    }

    @Test
    fun `女巫的药品和被刀目标只给女巫`() {
        val game = standardGame().copy(phase = Phase.NIGHT_WITCH, tonightVictim = 7)

        val witch = WerewolfPrompt.systemPrompt(game, seat = 4)
        assertTrue(witch.contains("今晚被狼人袭击的是 7 号"))
        assertTrue(witch.contains("解药"))
        assertTrue(witch.contains("毒药"))

        listOf(1, 2, 3, 5, 6).forEach { seat ->
            val other = WerewolfPrompt.systemPrompt(game, seat)
            assertFalse("$seat 号不该知道今晚谁被刀", other.contains("今晚被狼人袭击的是"))
            assertFalse("$seat 号不该知道药品余量", other.contains("解药："))
        }
    }

    @Test
    fun `猎人知道自己的开枪规则，别人不知道他是猎人`() {
        val game = standardGame()
        val hunter = WerewolfPrompt.systemPrompt(game, seat = 5)
        assertTrue(hunter.contains("你是猎人"))

        // 1 号是狼，它的提示词里不该出现「5 号是猎人」这种信息
        val wolf = WerewolfPrompt.systemPrompt(game, seat = 1)
        assertFalse(wolf.contains("5 号是猎人"))
    }

    // ------------------------------------------------------------------
    // 公开播报
    // ------------------------------------------------------------------

    @Test
    fun `播报只公布座位号，不公布死者身份`() {
        val game = standardGame().copy(
            history = listOf(GameEvent.Dawn(day = 1, deadSeats = listOf(5)))
        )
        // 5 号是猎人，但播报里不能出现「猎人」
        val text = WerewolfPrompt.describe(game.history.single())
        assertTrue(text.contains("5 号"))
        assertFalse(text.contains("猎人"))

        val viewer = WerewolfPrompt.systemPrompt(game, seat = 6)
        assertFalse(viewer.contains("5 号是猎人"))
    }

    @Test
    fun `各种播报文案都读得通`() {
        assertTrue(WerewolfPrompt.describe(GameEvent.Dawn(1, emptyList())).contains("平安夜"))
        assertTrue(WerewolfPrompt.describe(GameEvent.Exiled(2, 4, 5.0)).contains("4 号被放逐"))
        assertTrue(WerewolfPrompt.describe(GameEvent.NoExile(2)).contains("无人被放逐"))
        assertTrue(WerewolfPrompt.describe(GameEvent.HunterShot(2, 5, null)).contains("没有开枪"))
        assertTrue(WerewolfPrompt.describe(GameEvent.HunterShot(2, 5, 7)).contains("带走"))
    }

    // ------------------------------------------------------------------
    // 任务
    // ------------------------------------------------------------------

    @Test
    fun `任务随阶段变化，且带出可选目标`() {
        val game = standardGame()

        val chatTask = WerewolfPrompt.task(game.copy(phase = Phase.NIGHT_WOLF_CHAT), seat = 1)
        assertTrue(chatTask.contains("商量"))
        // 商议阶段只要一句话，不能要 JSON——那会把「说」和「定」又揉回一步
        assertTrue(chatTask.contains("不要输出 JSON"))
        assertFalse(chatTask.contains("1 号、"))
        assertFalse(chatTask.contains("2 号、"))
        assertTrue(chatTask.contains("3 号"))

        val wolfTask = WerewolfPrompt.task(game.copy(phase = Phase.NIGHT_WOLF), seat = 1)
        assertTrue(wolfTask.contains("定刀"))
        // 可选目标里不能出现自己或队友
        assertFalse(wolfTask.contains("1 号、"))
        assertFalse(wolfTask.contains("2 号、"))
        assertTrue(wolfTask.contains("3 号"))

        val seerTask = WerewolfPrompt.task(game.copy(phase = Phase.NIGHT_SEER), seat = 3)
        assertTrue(seerTask.contains("查验"))
        assertFalse("预言家不能验自己", seerTask.contains("可选目标：3 号"))

        val discussTask = WerewolfPrompt.task(game.copy(phase = Phase.DAY_DISCUSSION), seat = 6)
        assertTrue(discussTask.contains("轮到你发言"))

        val voteTask = WerewolfPrompt.task(game.copy(phase = Phase.DAY_VOTE), seat = 6)
        assertTrue(voteTask.contains("投票"))

        val hunterTask = WerewolfPrompt.task(game.copy(phase = Phase.HUNTER_SHOOT), seat = 5)
        assertTrue(hunterTask.contains("可以开枪"))
    }

    @Test
    fun `平票加商时会告诉狼人上一轮的提议`() {
        val game = standardGame().copy(
            phase = Phase.NIGHT_WOLF,
            wolfVoteRound = 2,
            wolfVotes = mapOf(1 to 6, 2 to 7),
        )
        val prompt = WerewolfPrompt.systemPrompt(game, seat = 1)
        assertTrue(prompt.contains("上一轮你们没能统一意见"))
        assertTrue(prompt.contains("1 号想刀 6 号"))
        assertTrue(prompt.contains("2 号想刀 7 号"))
    }

    // ------------------------------------------------------------------
    // 长历史
    // ------------------------------------------------------------------

    /**
     * 打到第四天：第一天和第二天压成一行，最近两天还是逐条。
     *
     * 判据分两边：折叠的那天**细节形状**没了（没有「· 第1天 天亮」这种逐条行），
     * 但**事实**一个不少（死的是谁、谁被放逐、几票）。
     */
    @Test
    fun `旧天折叠成一行，最近两天仍然逐条`() {
        val history = listOf(
            GameEvent.Dawn(day = 1, deadSeats = listOf(8)),
            GameEvent.Exiled(day = 1, seat = 4, voteCount = 5.0),
            GameEvent.Dawn(day = 2, deadSeats = emptyList()),
            GameEvent.NoExile(day = 2),
            GameEvent.Dawn(day = 3, deadSeats = listOf(6)),
            GameEvent.Exiled(day = 3, seat = 2, voteCount = 4.0),
            GameEvent.Dawn(day = 4, deadSeats = emptyList()),
        )
        val situation = WerewolfPrompt.publicSituation(standardGame().copy(day = 4, history = history))

        assertTrue("第一天应该被压成一行", situation.contains("第1天：夜里 8 号出局；4 号被放逐（5 票）"))
        assertTrue("第二天也只有一行", situation.contains("第2天：夜里平安；无人被放逐"))
        assertFalse("折叠之后不该再有第一天的逐条播报", situation.contains("· 第1天 天亮"))
        assertFalse("折叠之后不该再有第一天的逐条投票", situation.contains("· 第1天 投票"))

        assertTrue("第三天要逐条保留", situation.contains("· 第3天 天亮：6 号出局"))
        assertTrue("第四天也要逐条保留", situation.contains("· 第4天 天亮：平安夜"))
    }

    @Test
    fun `只打到第二天时一条都不折叠`() {
        val history = listOf(
            GameEvent.Dawn(day = 1, deadSeats = listOf(8)),
            GameEvent.NoExile(day = 1),
            GameEvent.Dawn(day = 2, deadSeats = emptyList()),
        )
        val situation = WerewolfPrompt.publicSituation(standardGame().copy(day = 2, history = history))

        assertTrue(situation.contains("· 第1天 天亮：8 号出局"))
        assertFalse("两天都在展开窗口内，不该出现折叠行", situation.contains("第1天：夜里"))
    }

    @Test
    fun `折叠后的能看出警长、自爆和开枪`() {
        val history = listOf(
            GameEvent.SheriffElected(day = 1, seat = 2, votes = 4),
            GameEvent.WolfExploded(day = 1, seat = 7),
            GameEvent.SheriffBadgePassed(day = 2, fromSeat = 2, toSeat = 6),
            GameEvent.HunterShot(day = 2, hunterSeat = 5, targetSeat = 3),
            GameEvent.Dawn(day = 3, deadSeats = emptyList()),
            GameEvent.Dawn(day = 4, deadSeats = emptyList()),
        )
        val situation = WerewolfPrompt.publicSituation(standardGame().copy(day = 4, history = history))

        assertTrue(situation.contains("2 号当选警长（4 票）"))
        assertTrue(situation.contains("7 号狼人自爆"))
        assertTrue(situation.contains("警徽交给 6 号"))
        assertTrue(situation.contains("猎人 5 号开枪带走 3 号"))
    }

    @Test
    fun `历史很长时身份信息仍然完整`() {
        // 模拟打到第 30 天：历史几十条，隐私字段还在。
        // 这正是「按 token 预算裁剪把身份裁掉」那个坑的对照组——
        // system prompt 不参与裁剪，所以身份永远不会丢。
        val manyEvents = (1..30).flatMap { day ->
            listOf(
                GameEvent.Dawn(day, emptyList()),
                GameEvent.NoExile(day),
            )
        }
        val game = standardGame().copy(day = 31, history = manyEvents)

        val prompt = WerewolfPrompt.systemPrompt(game, seat = 1)
        assertTrue(prompt.contains("你是 1 号玩家"))
        assertTrue(prompt.contains("身份是狼人"))
        assertTrue(prompt.contains("你的狼队友"))
        assertTrue(prompt.length > 1000)
    }
}
