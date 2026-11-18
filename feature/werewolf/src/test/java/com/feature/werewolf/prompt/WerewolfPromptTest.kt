package com.feature.werewolf.prompt

import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.SeerCheck
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WitchStock
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
    fun `狼人被明确要求白天不许提狼队内部的事`() {
        // 线上实际出现的两句话：「顺手往队友节奏里搅屎」「昨夜四狼一致刀 4 号」。
        // 只写「不要说队友的名字」挡不住这种不点名但等于摊牌的表述。
        val wolf = WerewolfPrompt.systemPrompt(standardGame(), seat = 1)

        assertTrue(wolf.contains("狼队内部的一切只能在夜里说"))
        assertTrue(wolf.contains("不许出现「队友」"))
        assertTrue(wolf.contains("不许复述夜里说过的话"))
    }

    @Test
    fun `自爆提示要求能指出救下的是哪个座位 否则不许写标记`() {
        val day = standardGame().copy(phase = Phase.DAY_DISCUSSION)

        val wolf = WerewolfPrompt.task(day, seat = 1)
        assertTrue(wolf.contains("另一个队友"))
        assertTrue(wolf.contains("写不出这个号码就别写标记"))

        // 好人看不到这条：写给好人等于提前告诉全场「这局的狼会自爆」
        assertFalse(WerewolfPrompt.task(day, seat = 6).contains("【自爆】"))
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
    fun `女巫自己被刀且解药已尽时，提示词告诉她今晚会死`() {
        // 第 2 夜、解药用尽、狼刀落在女巫自己身上。这一夜的毒药是最后一次行动，
        // 提示词必须说出来，否则模型会答「毒药留到更关键的时候」——而它天一亮就出局了。
        val game = standardGame().copy(
            phase = Phase.NIGHT_WITCH,
            day = 2,
            tonightVictim = 4,
            witch = WitchStock(antidoteAvailable = false),
        )
        val prompt = WerewolfPrompt.systemPrompt(game, seat = 4)
        assertTrue(prompt.contains("今晚被狼人袭击的就是你自己（4 号）"))
        assertTrue(prompt.contains("天一亮你就出局了"))
        assertTrue(prompt.contains("这是你最后一次行动"))
        assertFalse("不能再说「可以救他」", prompt.contains("你可以救他"))
    }

    @Test
    fun `女巫第一夜被刀可以自救，提示词不渲染必死`() {
        val game = standardGame().copy(phase = Phase.NIGHT_WITCH, day = 1, tonightVictim = 4)
        val prompt = WerewolfPrompt.systemPrompt(game, seat = 4)
        assertTrue(prompt.contains("第一夜你还能自救"))
        assertFalse(prompt.contains("这是你最后一次行动"))
    }

    @Test
    fun `女巫没被刀时不会看到必死文案`() {
        // 上面两个用例证明「该说的说到了」，这条守住反面：没被刀时不能渲染必死
        val game = standardGame().copy(phase = Phase.NIGHT_WITCH, day = 3, tonightVictim = 7)
        val prompt = WerewolfPrompt.systemPrompt(game, seat = 4)
        assertTrue(prompt.contains("今晚被狼人袭击的是 7 号"))
        assertFalse(prompt.contains("天一亮你就出局了"))
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

    @Test
    fun `上帝播报只报事件，不解释规则`() {
        // 玩家原话：「熊咆哮了」就够了。播报会被朗读，解释规则既占屏幕又拖长语音，
        // 而规则本来就在板子说明和提示词里讲过。
        // 座位号也不能带：熊是暗身份，「11 号在咆哮」等于当众宣布谁是熊
        val roar = WerewolfPrompt.describe(GameEvent.BearRoar(day = 1, seat = 11))
        assertTrue(roar.contains("熊在咆哮"))
        assertFalse("不能点名熊的座位", roar.contains("11 号"))
        assertFalse(roar.contains("邻座"))
        assertFalse(roar.contains("狼人"))

        // 自爆的后果由流程本身表达（直接入夜），不用写成一段说明文
        val exploded = WerewolfPrompt.describe(GameEvent.WolfExploded(day = 1, seat = 8))
        assertTrue(exploded.contains("8 号狼人自爆"))
        assertFalse(exploded.contains("没有人被放逐"))
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

    // ------------------------------------------------------------------
    // 基本打法
    // ------------------------------------------------------------------

    /** 8 人局，5 号是守卫。 */
    private fun guardGame(): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(
            composition = mapOf(
                WerewolfRole.WEREWOLF to 2,
                WerewolfRole.SEER to 1,
                WerewolfRole.WITCH to 1,
                WerewolfRole.GUARD to 1,
                WerewolfRole.VILLAGER to 3,
            ),
        ),
        roster = roster(8),
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.SEER,
            WerewolfRole.WITCH,
            WerewolfRole.GUARD,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
        ),
    )

    @Test
    fun `系统提示词带基本打法 而不是只有规则`() {
        // 规则说的是「能做什么」，打法说的是「怎么判断」。缺了这一层，
        // 发言完全由人设驱动：真预言家三轮复读、好人看对跳只看谁语气硬。
        val prompt = WerewolfPrompt.systemPrompt(standardGame(), seat = 1)

        assertTrue(prompt.contains("【基本打法】"))
        assertTrue("预言家必须报验人", prompt.contains("报出前一晚验了谁"))
        assertTrue("防复读", prompt.contains("同一段判断说第二遍"))
        assertTrue("看票型而不是看气势", prompt.contains("谁发起的"))
        assertTrue("被指认先正面回答", prompt.contains("先正面回答"))
    }

    @Test
    fun `守卫白天拿到的昨夜守人 是真正昨夜的那个 而不是前夜`() {
        val game = guardGame()
        // 白天这一刻 lastGuardedSeat 还停在「前夜的前夜」——startNextNight() 是白天结束才搬家的。
        // 照它发言，守卫每轮都会报错守人对象，好人据此认定它前后矛盾。
        val day = game.copy(
            day = 3,
            phase = Phase.DAY_DISCUSSION,
            lastGuardedSeat = 7,
            tonightGuardedSeat = 4,
        )
        val brief = WerewolfPrompt.privateBriefing(day, seat = 5)
        assertTrue("白天要报昨夜守的 4 号", brief.contains("你昨夜守的是 4 号"))
        assertFalse("不能把前夜守的 7 号说成昨夜", brief.contains("你昨夜守的是 7 号"))

        // 守护阶段正好相反：那时 tonightGuardedSeat 还没产生，lastGuardedSeat 才是昨夜
        val guardPhase = day.copy(phase = Phase.NIGHT_GUARD)
        assertTrue(
            WerewolfPrompt.privateBriefing(guardPhase, seat = 5).contains("你昨夜守的是 7 号")
        )
    }
}
