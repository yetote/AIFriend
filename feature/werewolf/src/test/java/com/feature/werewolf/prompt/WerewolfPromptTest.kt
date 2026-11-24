package com.feature.werewolf.prompt

import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.SeerCheck
import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfSpeechLength
import com.feature.werewolf.game.NightRecord
import com.feature.werewolf.game.PendingDawn
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WerewolfTurnLimit
import com.feature.werewolf.game.WinRule
import com.feature.werewolf.decision.WerewolfDecisions
import com.feature.werewolf.game.WitchStock
import com.feature.werewolf.prompt.boards.Boards
import com.feature.werewolf.prompt.boards.BoardText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** 带守卫的 8 人局：1、2 狼；3 预言家；4 女巫；5 守卫；6 猎人；7、8 平民。 */
    private fun gameWithGuard(): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(
            composition = mapOf(
                WerewolfRole.WEREWOLF to 2,
                WerewolfRole.SEER to 1,
                WerewolfRole.WITCH to 1,
                WerewolfRole.GUARD to 1,
                WerewolfRole.VILLAGER to 2,
                WerewolfRole.HUNTER to 1,
            ),
            hasSheriff = true,
        ),
        roster = roster(8),
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.SEER,
            WerewolfRole.WITCH,
            WerewolfRole.GUARD,
            WerewolfRole.HUNTER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
        ),
    )

    @Test
    fun `狼人的打法里有盘身份和盘守卫`() {
        // 狼人整局不盘身份 = 白天说的全是「他很可疑」这种没有指向的废话，
        // 好人分不出谁在带节奏，狼队也拿不到任何可执行的信息。
        val wolf = WerewolfPrompt.systemPrompt(gameWithGuard(), seat = 1)

        assertTrue("要盘身份", wolf.contains("盘身份"))
        assertTrue("盘预言家", wolf.contains("预言家"))
        assertTrue("盘猎人", wolf.contains("猎人"))
        assertTrue("盘女巫", wolf.contains("女巫"))
        assertTrue("盘守卫", wolf.contains("盘守卫"))
        assertTrue("要给出推理链：刀口没死就是线索", wolf.contains("X 却没死"))
    }

    @Test
    fun `规则说明里写清楚守救同归`() {
        // 守卫和女巫互相不知道对方做了什么。不提前说明，
        // 守救同归时牌桌上看起来就是「明明守了又救了，怎么还是死了」。
        val game = gameWithGuard()

        assertTrue(WerewolfPrompt.systemPrompt(game, seat = 1).contains("守救同归"))
        assertTrue(WerewolfPrompt.systemPrompt(game, seat = 4).contains("守救同归"))
        assertTrue(WerewolfPrompt.systemPrompt(game, seat = 7).contains("守救同归"))
    }

    @Test
    fun `规则说明里写明出局不翻牌`() {
        // 真局教训：模型对「标准局放逐翻牌」先验太强，规则没明说，
        // 猎人就编出「3号放逐翻出好人」，全场跟着一句不存在的翻牌走。
        val prompt = WerewolfPrompt.systemPrompt(gameWithGuard(), seat = 3)
        assertTrue("要点名本局是暗牌局", prompt.contains("暗牌局"))
        assertTrue("要断掉翻牌谣言", prompt.contains("不是事实"))
        assertTrue("没有白痴时要明说没有翻牌环节", prompt.contains("不存在任何翻牌环节"))
    }

    @Test
    fun `有白痴的板子写明白痴翻牌例外`() {
        val game = WerewolfGame.withRoles(
            config = WerewolfConfig(
                composition = mapOf(
                    WerewolfRole.WEREWOLF to 2,
                    WerewolfRole.SEER to 1,
                    WerewolfRole.IDIOT to 1,
                    WerewolfRole.VILLAGER to 4,
                ),
                hasSheriff = false,
            ),
            roster = roster(8),
            roles = listOf(
                WerewolfRole.WEREWOLF,
                WerewolfRole.WEREWOLF,
                WerewolfRole.SEER,
                WerewolfRole.IDIOT,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
            ),
        )

        val prompt = WerewolfPrompt.systemPrompt(game, seat = 3)
        assertTrue("白痴是唯一的翻牌例外", prompt.contains("唯一的例外是白痴"))
        assertFalse("暗牌局正文不该再说没有翻牌环节", prompt.contains("不存在任何翻牌环节"))
    }

    @Test
    fun `女巫藏身份但有被查杀跳银水的例外`() {
        // 真局教训：女巫被查杀票出，到死不跳身份——提示词只教藏，
        // 没教「藏不下去了怎么用银水自证」，结果硬辩到被抗推。
        val prompt = WerewolfPrompt.systemPrompt(gameWithGuard(), seat = 4)
        assertTrue("要教藏身份", prompt.contains("把自己藏起来"))
        assertTrue("要有逃生门：报银水自证", prompt.contains("报银水"))
        assertTrue("要点明自证逻辑", prompt.contains("狼知道自己刀了谁"))
    }

    @Test
    fun `第一天死讯压在警长竞选之后`() {
        // 线上标准流程：第 1 天先上警竞选，竞选和投票结束才公布昨夜死讯。
        // 顺序反了等于让所有人先看到谁死了，再对着尸体竞选。
        val game = WerewolfGame.withRoles(
            config = WerewolfConfig(
                composition = mapOf(
                    WerewolfRole.WEREWOLF to 2,
                    WerewolfRole.SEER to 1,
                    WerewolfRole.WITCH to 1,
                    WerewolfRole.GUARD to 1,
                    WerewolfRole.HUNTER to 1,
                    WerewolfRole.VILLAGER to 2,
                ),
                hasSheriff = true,
            ),
            roster = roster(8),
            roles = listOf(
                WerewolfRole.WEREWOLF,
                WerewolfRole.WEREWOLF,
                WerewolfRole.SEER,
                WerewolfRole.WITCH,
                WerewolfRole.GUARD,
                WerewolfRole.HUNTER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
            ),
        ).guardProtect(3).beginWolfVote()

        // 夜里狼刀了 4 号（女巫），女巫没用解药
        val dawn = game.wolfVote(mapOf(1 to 4, 2 to 4)).seerCheck(6).witchAct(heal = false)

        assertEquals(Phase.DAY_SHERIFF_SIGNUP, dawn.phase)
        assertTrue("死讯还没公布", dawn.history.none { it is GameEvent.Dawn })
        assertEquals("死讯先存着", listOf(4), dawn.pendingDawn?.deaths)

        // 竞选发言阶段，模型看不到死讯
        val prompt = WerewolfPrompt.systemPrompt(dawn, seat = 3)
        assertFalse("竞选时不能看到谁出局了", prompt.contains("已出局"))
        assertFalse(prompt.contains("4 号出局"))
        assertTrue(
            "没公布死讯时，死者还在存活名单里（不然名单本身就泄底）",
            prompt.contains("共 8 人"),
        )

        // 竞选 + 投票结束，死讯才公布
        val elected = dawn.signUpSheriff(setOf(3, 7))
            .beginSheriffVote()
            .castSheriffVotes(mapOf(1 to 3, 2 to 3, 5 to 3, 6 to 3, 8 to 3))

        assertTrue("竞选结束后死讯才播报", elected.history.any { it is GameEvent.Dawn })
        assertNull("暂存的死讯已经清掉", elected.pendingDawn)
    }

    @Test
    fun `狼人知道上一夜定了谁，但结果不归他们管`() {
        // 刀口是狼队自己定的，他们当然知道；死没死是天亮上帝播报说了算。
        // 夜里他们还不知道结果，播报要等第二天早上。
        val game = WerewolfGame.withRoles(
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
        ).copy(
            day = 2,
            phase = Phase.NIGHT_WOLF_CHAT,
            nights = listOf(NightRecord(day = 1, wolfTarget = 5, deaths = listOf(5))),
        )

        val wolf = WerewolfPrompt.systemPrompt(game, seat = 1)

        assertTrue("狼人要知道自己定了谁", wolf.contains("上一夜你们定了刀 5 号"))
        assertFalse("结果不来自私有信息", wolf.contains("5 号出局"))
    }

    @Test
    fun `第1天只报了一个验人不该被当成悍跳`() {
        // 真机案例：真预言家第 1 夜验的正好是当夜被刀的人，只报了一个人就被说成
        // 「死无对证」，好人全票投他，狼人顺势拿到警徽——真预言家第一天就走了。
        val prompt = WerewolfPrompt.systemPrompt(gameWithGuard(), seat = 3)

        assertTrue(prompt.contains("第 1 天只报了一个验人是正常的"))
        assertTrue(prompt.contains("验到死人只是运气不好"))
    }

    @Test
    fun `预言家不许编警徽流`() {
        // 真机案例：真预言家只验了 7 号，却在竞选时说「警徽流先验 8 号再验 10 号」，
        // 编出来的警徽流对不上，全场当场认定他是悍跳
        val prompt = WerewolfPrompt.systemPrompt(gameWithGuard(), seat = 3)

        assertTrue(prompt.contains("警徽流只报你真的验过的人"))
        assertTrue(prompt.contains("第 1 天你只验了一个人"))
    }

    @Test
    fun `规则说明写清上警只是举手`() {
        // 真机案例：8 号拿「你上警前全程没说过一句话」当质疑点，
        // 而上警本来只是举手报名——这个质疑是冲着空气的
        val prompt = WerewolfPrompt.systemPrompt(gameWithGuard(), seat = 7)

        assertTrue(prompt.contains("上警只是**举手报名**"))
        assertTrue(prompt.contains("不要拿「他上警前没说话」当质疑点"))
    }

    @Test
    fun `狼人知道自称守人是诈守卫`() {
        // 真机案例：猎人诈称「我昨晚守的」并编出「真正被守的是 10 号」，
        // 狼队全信，第 3 夜据此推理「守卫不能连守 → 5 号是裸位」——推理前提就是错的
        val wolf = WerewolfPrompt.systemPrompt(gameWithGuard(), seat = 1)

        assertTrue(wolf.contains("诈守卫"))
        // 推理前提必须写对：守卫的限制是「不能连续两夜守同一个人」，
        // 不是「守过谁以后就不能再守谁」——后者会让狼把没被守过的人当成裸位
        assertTrue(wolf.contains("不能连续两夜守同一个人"))
    }

    @Test
    fun `死讯没公布时谁都不许谈昨夜`() {
        // 真实案例：狼人警长的竞选发言里说「昨夜明明是平安夜」——
        // 他当然知道昨夜死了人（那把刀是他自己动的），这句话当场自曝。
        // 死讯公布之前，任何人都不知道昨晚发生了什么。
        val game = gameWithGuard().copy(
            phase = Phase.DAY_SHERIFF_SIGNUP,
            pendingDawn = PendingDawn(day = 1, deaths = listOf(4)),
        )

        val good = WerewolfPrompt.systemPrompt(game, seat = 3)
        assertTrue("要写明昨夜还没公布", good.contains("昨夜还没公布"))
        assertTrue("要禁止谈昨夜", good.contains("不要谈昨夜"))

        val wolf = WerewolfPrompt.systemPrompt(game, seat = 1)
        assertTrue("狼人更不能提昨夜", wolf.contains("不要谈昨夜"))

        // 死讯公布之后，这条提醒就该消失
        val announced = game.copy(pendingDawn = null)
        assertFalse(
            "公布之后不该再提醒",
            WerewolfPrompt.systemPrompt(announced, seat = 3).contains("昨夜还没公布"),
        )
    }

    @Test
    fun `好人的人数账要算清楚`() {
        // 真实案例：场上只剩 3 好 1 狼，三个好人里两个互相咬，
        // 狼人投了其中一个——好人把好人投出去了，夜里再被刀一个就绑票输掉
        val prompt = WerewolfPrompt.systemPrompt(gameWithGuard(), seat = 7)

        assertTrue("人不多时票要砸在狼身上", prompt.contains("票必须砸在狼身上"))
        assertTrue("跟票不是狼的证据", prompt.contains("跟着多数投票本身不是狼的证据"))
        assertTrue("神职该上警就上警", prompt.contains("神职该上警就上警"))
    }

    @Test
    fun `狼人不要编造现场细节`() {
        // 真实案例：狼人竞选发言写「他低头看自己左手，像在数指节」——
        // 这种描写只有演的人才会写，好人一眼看穿
        val wolf = WerewolfPrompt.systemPrompt(
            gameWithGuard().copy(phase = Phase.NIGHT_WOLF_CHAT),
            seat = 1,
        )

        assertTrue(wolf.contains("不要编造现场细节"))
        assertTrue("狼队内部别用命令式的说法", wolf.contains("死命令"))
    }

    @Test
    fun `玩家写的额外设定进提示词的最后`() {
        // 位置很要紧：放在最后才压得过内置打法。放前面会被后面固定的几段稀释掉，
        // 模型照着内置打法走，玩家写的东西等于白写。
        val game = gameWithGuard()
        val prompt = WerewolfPrompt.systemPrompt(game, seat = 3, custom = "狼人白天要更凶一点")

        assertTrue(prompt.contains("狼人白天要更凶一点"))
        val customIndex = prompt.indexOf("本局额外设定")
        assertTrue("自定义段要标出来", customIndex > 0)
        assertTrue(
            "自定义段必须在最后",
            customIndex > prompt.indexOf("预言家查验") &&
                customIndex > prompt.indexOf("基本打法"),
        )
    }

    @Test
    fun `没写自定义时不加那一段`() {
        val prompt = WerewolfPrompt.systemPrompt(gameWithGuard(), seat = 3)
        assertFalse(prompt.contains("本局额外设定"))
    }

    @Test
    fun `自定义设定不会泄进公共播报`() {
        // 信息隔离的红线：玩家写的这段只进 system prompt，
        // 不进 transcript、不进播报——不然就是全场都知道
        val game = gameWithGuard()
        val prompt = WerewolfPrompt.systemPrompt(game, seat = 3, custom = "3 号是预言家")
        assertTrue(prompt.contains("3 号是预言家"))

        // 播报里不能出现
        val broadcast = WerewolfPrompt.describe(GameEvent.Dawn(day = 1, deadSeats = listOf(2)))
        assertFalse(broadcast.contains("3 号是预言家"))
    }

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

    @Test
    fun `风格画像紧跟人设之后，且声明优先于日常人设`() {
        val prompt = WerewolfPrompt.systemPrompt(
            standardGame(), seat = 6,
            persona = "你说话很冲，喜欢抬杠。",
            playStyle = PlayerStyle.AGGRESSIVE,
        )
        assertTrue(prompt.startsWith("你说话很冲"))
        val styleAt = prompt.indexOf("【你在牌桌上的性格】")
        val personAt = prompt.indexOf("你说话很冲")
        val identityAt = prompt.indexOf("你正在玩一局狼人杀")
        assertTrue("风格画像要在人设之后", styleAt > personAt)
        assertTrue("风格画像要在身份之前", styleAt < identityAt)
        assertTrue(prompt.contains("优先于上面那段日常性格"))
    }

    @Test
    fun `不传风格画像时提示词和老行为一样`() {
        val withStyle = WerewolfPrompt.systemPrompt(standardGame(), seat = 6, persona = "口癖")
        assertTrue(!withStyle.contains("【你在牌桌上的性格】"))
    }

    @Test
    fun `自由式也注入风格画像`() {
        val prompt = WerewolfPrompt.systemPrompt(
            standardGame(), seat = 6,
            style = WerewolfPromptStyle.FREE,
            playStyle = PlayerStyle.ANALYTICAL,
        )
        assertTrue(prompt.contains("【你在牌桌上的性格】"))
        assertTrue(prompt.contains("逻辑推演"))
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
    fun `狼人盘身份只盘好人，只有倒钩才投队友`() {
        // 实测：狼白天按「盘身份」的要求认真分析全场，把队友也归进了「很可疑」，
        // 最后一票砸在队友身上。盘身份是演给好人看的，队友不在候选里。
        //
        // 唯一的例外是倒钩：投队友是为了让好人信你，那时这一票是道具不是失误。
        val wolf = WerewolfPrompt.systemPrompt(standardGame(), seat = 1)

        assertTrue(wolf.contains("只盘好人"))
        assertTrue(wolf.contains("绝不能出现在你的怀疑名单上"))
        assertTrue("倒钩是唯一例外", wolf.contains("演倒钩"))
        assertTrue(wolf.contains("除此之外决不投队友"))
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
    fun `插爆判断里被查杀不算被点破`() {
        // 真局教训：判断简报原来写「你已经被点破，再聊下去只会暴露更多」，
        // 模型把「被查杀」直接归进去，一天不辩就自爆。指控和判决必须分开写。
        val day = standardGame().copy(phase = Phase.DAY_DISCUSSION)
        val brief = WerewolfPrompt.explodeWatchBrief(day, seat = 1, speaker = 3, speech = "3 号查杀 1 号。")

        assertTrue("要点名被查杀不算", brief.contains("被查杀、被踩、被点名表水都不是理由"))
        assertTrue("要说清指控不是判决", brief.contains("那是一句指控，不是判决"))
        assertTrue("要写出自爆等于替预言家作证", brief.contains("替预言家作证"))
        assertTrue("自爆条件只留两条", brief.contains("夜里的战术需要吞掉这个白天") && brief.contains("牵出队友"))
        assertFalse("不能再授权「被点破就爆」", brief.contains("你已经被点破"))
        assertFalse("不再提「把身份说死」这类第二条件", brief.contains("把身份说死了"))
    }

    @Test
    fun `规则写清自爆的终局与无警长时的发言顺序`() {
        val day = standardGame().copy(phase = Phase.DAY_DISCUSSION)
        // 自由式也吃同一份规则：这两条是硬事实（自爆永久出局、没人主持就随机），不是打法
        val free = WerewolfPrompt.systemPrompt(day, seat = 1, style = WerewolfPromptStyle.FREE)

        assertTrue("自爆的狼这一局不再回来", free.contains("出局是永久的"))
        assertTrue("没有警长时起点和方向随机", free.contains("起点和方向都由系统随机指定"))
        assertFalse("不再写「从死者下位起」的固定起点", free.contains("从昨夜死者的下一位开始"))
    }

    @Test
    fun `有警长的板子也说明警长不在场时随机`() {
        val roles = listOf(
            WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF,
            WerewolfRole.SEER, WerewolfRole.WITCH,
            WerewolfRole.HUNTER, WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
        )
        val game = WerewolfGame.withRoles(
            config = WerewolfConfig(
                composition = roles.groupingBy { it }.eachCount(),
                hasSheriff = true,
                maxVoteRounds = 2,
            ),
            roster = roster(roles.size),
            roles = roles,
        ).copy(phase = Phase.DAY_DISCUSSION)

        val text = WerewolfPrompt.systemPrompt(game, seat = 1)
        assertTrue("警长缺席也要说明", text.contains("警长不在场时"))
        assertTrue(text.contains("由系统随机指定"))
    }

    @Test
    fun `规则写清单爆顺延、双爆才吞警徽`() {
        val roles = listOf(
            WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF,
            WerewolfRole.SEER, WerewolfRole.WITCH, WerewolfRole.HUNTER,
            WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
        )
        val game = WerewolfGame.withRoles(
            config = WerewolfConfig(
                composition = roles.groupingBy { it }.eachCount(),
                hasSheriff = true,
                maxVoteRounds = 2,
            ),
            roster = roster(9),
            roles = roles,
        ).copy(phase = Phase.DAY_DISCUSSION)

        // 自由式也吃这份 rulesBrief：警徽顺延是硬规则，不是打法
        val free = WerewolfPrompt.systemPrompt(game, seat = 1, style = WerewolfPromptStyle.FREE)

        assertTrue("单爆只是顺延", free.contains("顺延到下一个白天重新竞选"))
        assertTrue("双爆才吞警徽", free.contains("双爆"))
        assertTrue("PK 发言被爆同样计数", free.contains("PK 发言都算"))
        assertFalse("不能再写「一次自爆就没有警长」", free.contains("本局按「无人上警」处理"))
    }

    @Test
    fun `规则写明竞选发言之后有退水`() {
        val roles = listOf(
            WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF,
            WerewolfRole.SEER, WerewolfRole.WITCH, WerewolfRole.HUNTER,
            WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
        )
        val game = WerewolfGame.withRoles(
            config = WerewolfConfig(
                composition = roles.groupingBy { it }.eachCount(),
                hasSheriff = true,
                maxVoteRounds = 2,
            ),
            roster = roster(9),
            roles = roles,
        ).copy(phase = Phase.DAY_SHERIFF_SPEECH)

        // 退水是硬规则：退了不再被投票、也没有票，全员退水就没有警长。
        // 自由式同样吃这份 rulesBrief
        val free = WerewolfPrompt.systemPrompt(game, seat = 1, style = WerewolfPromptStyle.FREE)

        assertTrue("要点名退水这一步", free.contains("退水"))
        assertTrue("退水的人不再被投票", free.contains("退水的人不再被投票"))
        assertTrue("全员退水就没有警长", free.contains("所有候选人都退水"))
        assertTrue("只剩一个人不退就直接当选", free.contains("直接当选"))
    }

    @Test
    fun `自爆提示只写给狼 受理条件是标记本身`() {
        val day = standardGame().copy(phase = Phase.DAY_DISCUSSION)

        val wolf = WerewolfPrompt.task(day, seat = 1)
        assertTrue(wolf.contains("【自爆】"))
        assertFalse("不再要求点名队友", wolf.contains("还活着的队友"))
        assertFalse("不再有「写不出这个号码就别写标记」的劝退", wolf.contains("写不出这个号码"))

        // 好人看不到这条：写给好人等于提前告诉全场「这局的狼会自爆」
        assertFalse(WerewolfPrompt.task(day, seat = 6).contains("【自爆】"))
    }

    @Test
    fun `自由模式的守卫熊狼王简报不带藏身和怂恿诱导`() {
        val day = gameWithGuard().copy(phase = Phase.DAY_DISCUSSION)

        // 守卫：报不报守人是战术自由，自由模式不劝
        val freeGuard = WerewolfPrompt.systemPrompt(day, seat = 5, style = WerewolfPromptStyle.FREE)
        assertFalse("自由式守卫不劝藏守人", freeGuard.contains("报出你守过谁"))
        assertTrue("守卫机制还在", freeGuard.contains("守自己同样是合法选择"))
        assertTrue("引导式守卫提醒还在", WerewolfPrompt.systemPrompt(day, seat = 5).contains("报出你守过谁"))

        // 熊：跳不跳身份是自由度
        val bearGame = WerewolfGame.withRoles(
            config = WerewolfConfig(
                composition = mapOf(
                    WerewolfRole.WEREWOLF to 2,
                    WerewolfRole.BEAR to 1,
                    WerewolfRole.VILLAGER to 2,
                ),
            ),
            roster = roster(5),
            roles = listOf(
                WerewolfRole.WEREWOLF,
                WerewolfRole.WEREWOLF,
                WerewolfRole.BEAR,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
            ),
        ).copy(phase = Phase.DAY_DISCUSSION)
        val freeBear = WerewolfPrompt.systemPrompt(bearGame, seat = 3, style = WerewolfPromptStyle.FREE)
        assertFalse("自由式熊不劝藏身份", freeBear.contains("不用急着自报身份"))
        assertTrue("引导式熊提醒还在", WerewolfPrompt.systemPrompt(bearGame, seat = 3).contains("不用急着自报身份"))

        // 狼王：「别轻易放弃」是怂恿语气
        val kingGame = WerewolfGame.withRoles(
            config = WerewolfConfig(
                composition = mapOf(
                    WerewolfRole.WEREWOLF_KING to 1,
                    WerewolfRole.VILLAGER to 3,
                ),
            ),
            roster = roster(4),
            roles = listOf(
                WerewolfRole.WEREWOLF_KING,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
            ),
        ).copy(phase = Phase.DAY_DISCUSSION)
        val freeKing = WerewolfPrompt.systemPrompt(kingGame, seat = 1, style = WerewolfPromptStyle.FREE)
        assertFalse("自由式狼王不怂恿", freeKing.contains("别轻易放弃"))
        assertTrue("狼王枪的机制还在", freeKing.contains("队友已经全灭之后也有效"))
        assertTrue("引导式狼王怂恿还在", WerewolfPrompt.systemPrompt(kingGame, seat = 1).contains("别轻易放弃"))
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
            // 只认药品状态的原文：「自刀骗解药：」这种战术文案里也有「解药」两个字
            assertFalse(
                "$seat 号不该知道药品余量",
                other.contains("解药：还在") || other.contains("解药：已经用完")
            )
        }
    }

    @Test
    fun `解药已尽时女巫夜里零信息，连自己被刀都不说`() {
        // 第 2 夜、解药用尽、狼刀落在女巫自己身上。按用户的口径，刀口信息只在
        // 解药还可用时披露——解药一空，今晚谁被刀、是不是平安夜、甚至自己被刀，
        // 一概不告诉她；毒药作废是用完解药的代价。
        val game = standardGame().copy(
            phase = Phase.NIGHT_WITCH,
            day = 2,
            tonightVictim = 4,
            witch = WitchStock(antidoteAvailable = false),
        )
        val prompt = WerewolfPrompt.systemPrompt(game, seat = 4)
        assertFalse("自己被刀也不许说", prompt.contains("被狼人袭击"))
        assertFalse("必死通知是刀口信息的衍生，一并撤掉", prompt.contains("你今晚必死"))
        assertTrue("只说明她剩下的抉择", prompt.contains("毒药的抉择"))
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
    fun `女巫看得到自己的用药履历，别人看不到`() {
        // 真局事故：提示词只报「解药：已经用完」，不报药用在谁身上。于是女巫第一天
        // 平安夜时不知道那瓶药救的是谁，整段推理在猜「我是不是自救的」，还推出
        // 「狼知道我是女巫」；第二天卡在「只写了用完，没写目标」，只能从死讯倒推。
        val game = standardGame().copy(
            phase = Phase.DAY_DISCUSSION,
            nights = listOf(
                NightRecord(day = 1, wolfTarget = 6, healed = true),
                NightRecord(day = 2, wolfTarget = 8, poisonTarget = 2),
            ),
        )
        val witch = WerewolfPrompt.systemPrompt(game, seat = 4)
        assertTrue("要报出救的是谁", witch.contains("第 1 夜用解药救了 6 号"))
        assertTrue("也要报出毒的是谁", witch.contains("第 2 夜用毒药毒了 2 号"))
        assertTrue("要提醒救到的不一定是好人", witch.contains("狼会自刀骗解药"))

        listOf(1, 3, 5, 6).forEach { seat ->
            assertFalse(
                "$seat 号不该看到女巫的用药记录",
                WerewolfPrompt.systemPrompt(game, seat).contains("用药记录"),
            )
        }
    }

    @Test
    fun `守救同归时用药履历要解释人为什么还是死了`() {
        // 只说「你救了 6 号」，而 6 号最后还是出局，女巫要么以为自己判断错，
        // 要么怀疑引擎算错了。这局是守卫也守了刀口，必须写成守救同归。
        val game = standardGame().copy(
            phase = Phase.DAY_DISCUSSION,
            nights = listOf(
                NightRecord(
                    day = 1,
                    wolfTarget = 6,
                    guarded = 6,
                    healed = true,
                    mutualProtection = true,
                    deaths = listOf(6),
                ),
            ),
        )
        val witch = WerewolfPrompt.systemPrompt(game, seat = 4)
        assertTrue("要说清是守救同归", witch.contains("守救同归"))
        assertTrue("点明他照样出局", witch.contains("照样出局"))
    }

    @Test
    fun `能否自救要写死，不能让女巫自己猜本局设定`() {
        // 原来 rules 写「第一夜是否允许自救看本局设定」——女巫自己并不知道设定是什么。
        val strict = standardGame().let {
            it.copy(config = it.config.copy(witchCanSelfHealOnFirstNight = false))
        }
        assertTrue(
            "关掉首夜自救就要明写",
            WerewolfPrompt.systemPrompt(strict, seat = 4).contains("任何时候都不能自救"),
        )
        assertTrue(
            "开着就写明第一夜能自救、之后不能",
            WerewolfPrompt.systemPrompt(standardGame(), seat = 4)
                .contains("第一夜你被刀还能自救，之后就不能了"),
        )
    }

    @Test
    fun `毒药无视守护这条要写给女巫`() {
        // 守卫挡的是刀，不是药。不明写，女巫会以为守过的人毒不死而改刀口。
        val witch = WerewolfPrompt.systemPrompt(standardGame(), seat = 4)
        assertTrue(witch.contains("毒药无视守护"))
    }

    @Test
    fun `白天的狼能看到昨晚头狼的战术，好人看不到`() {
        // 白天的上下文只喂公开消息，夜聊原文在白天是看不见的。
        // 战术安排只存一份给狼本人——不存的话，昨晚商量出来的分工等于没商量。
        val game = standardGame().copy(
            phase = Phase.DAY_DISCUSSION,
            wolfPlan = "今晚刀 5 号；白天 3 号跳预言家，4 号负责冲锋。",
        )

        val wolf = WerewolfPrompt.systemPrompt(game, seat = 1)
        assertTrue(wolf.contains("昨晚头狼定的战术"))
        assertTrue(wolf.contains("3 号跳预言家"))

        val good = WerewolfPrompt.systemPrompt(game, seat = 6)
        assertFalse("好人不能看到狼队的战术", good.contains("昨晚头狼定的战术"))
        assertFalse(good.contains("3 号跳预言家"))
    }

    @Test
    fun `白天的狼看到昨晚定刀是谁，不再看到没谈拢`() {
        // 定刀成功后 wolfVoteRound 还停在 2、wolfVotes 已经是第二轮那份一致的票。
        // 白天再说「上一轮没谈拢」就是自相矛盾的情报：文案说分歧，票型却是统一的。
        val game = standardGame().copy(
            phase = Phase.DAY_DISCUSSION,
            day = 2,
            wolfVoteRound = 2,
            wolfVotes = mapOf(1 to 5, 2 to 5),
            nights = listOf(
                NightRecord(day = 1, wolfTarget = 5, deaths = listOf(5)),
            ),
        )

        val wolf = WerewolfPrompt.systemPrompt(game, seat = 1)
        assertFalse("定完刀就不该再说没谈拢", wolf.contains("没能统一意见"))
        assertTrue("白天要知道昨晚定了谁", wolf.contains("上一夜你们定了刀 5 号"))
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
        // 自刀和空刀都是正经战术，候选里必须把自己和队友都列出来
        assertTrue("自己也在候选里", chatTask.contains("1 号"))
        assertTrue("队友也在候选里", chatTask.contains("2 号"))
        assertTrue("要说清能自刀", chatTask.contains("自刀"))
        assertTrue(chatTask.contains("3 号"))

        val wolfTask = WerewolfPrompt.task(game.copy(phase = Phase.NIGHT_WOLF), seat = 1)
        assertTrue(wolfTask.contains("定刀"))
        assertTrue(wolfTask.contains("1 号"))
        assertTrue(wolfTask.contains("2 号"))
        assertTrue("填 null 就是空刀", wolfTask.contains("空刀"))
        assertTrue(wolfTask.contains("3 号"))

        val seerTask = WerewolfPrompt.task(game.copy(phase = Phase.NIGHT_SEER), seat = 3)
        assertTrue(seerTask.contains("查验"))
        assertFalse("预言家不能验自己", seerTask.contains("可选目标：3 号"))

        val discussTask = WerewolfPrompt.task(game.copy(phase = Phase.DAY_DISCUSSION), seat = 6)
        assertTrue(discussTask.contains("轮到你发言"))
        assertTrue("发言结尾要交代这一票给谁", discussTask.contains("我这一票投"))

        val voteTask = WerewolfPrompt.task(game.copy(phase = Phase.DAY_VOTE), seat = 6)
        assertTrue(voteTask.contains("投票"))
        assertTrue("能判断就投、分不清才弃票", voteTask.contains("完全分不清时宁可弃票"))

        val hunterTask = WerewolfPrompt.task(game.copy(phase = Phase.HUNTER_SHOOT), seat = 5)
        assertTrue(hunterTask.contains("可以开枪"))
    }

    @Test
    fun `讨论发言有两段骨架，且按身份换内容`() {
        // 「AI 不会玩」的另一个来源：一段发言六句，全是「我觉得 3 号有问题」「大家小心」，
        // 一句能核对的信息都没有。骨架把「结论 → 依据 → 票向」钉死，自由式不享受这份教学。
        val talk = standardGame().copy(phase = Phase.DAY_DISCUSSION)

        val wolf = WerewolfPrompt.task(talk, seat = 1)
        assertTrue("第一句给结论", wolf.contains("第一句就给结论"))
        assertTrue("结尾给票向", wolf.contains("我这一票投 X 号"))
        assertTrue("狼要能解释得通", wolf.contains("解释得通"))

        val seer = WerewolfPrompt.task(talk, seat = 3)
        assertTrue("预言家先说验人、再给警徽流", seer.contains("再给警徽流"))
        assertTrue("对跳要点名", seer.contains("悍跳"))

        val villager = WerewolfPrompt.task(talk, seat = 6)
        assertTrue("平民不报身份", villager.contains("不要报身份"))

        val free = WerewolfPrompt.task(talk, seat = 1, style = WerewolfPromptStyle.FREE)
        assertFalse("自由式不给发言骨架", free.contains("第一句就给结论"))
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

    // ------------------------------------------------------------------
    // 和守卫博弈（狼队的下刀思路）
    // ------------------------------------------------------------------

    /**
     * 第 2 夜的局面：昨夜（第 1 夜）狼刀了 [target]，[deaths] 是那一夜的实际死亡名单。
     *
     * 死亡名单是**播报过**的事实，狼从上帝播报里听得到；守卫守了谁听不到，
     * 所以这里刻意不填 `guarded`——填了也不能出现在狼的提示词里。
     */
    private fun nightTwo(target: Int, deaths: List<Int>) = gameWithGuard().copy(
        day = 2,
        phase = Phase.NIGHT_WOLF,
        nights = listOf(NightRecord(day = 1, wolfTarget = target, deaths = deaths)),
        history = listOf(GameEvent.Dawn(1, deaths)),
    )

    @Test
    fun `有守卫时，昨夜没死的刀口今晚该补而不是换人`() {
        // 真机案例：狼第二夜照旧闷头刀预言家，撞上守卫的守，空了一整夜还以为是运气差。
        val wolf = WerewolfPrompt.systemPrompt(nightTwo(target = 6, deaths = emptyList()), seat = 1)

        assertTrue("要写清撞守等于这一夜白过", wolf.contains("完全无效"))
        assertTrue("要指出守卫不能连守这个破绽", wolf.contains("不能再守"))
        assertTrue("要给出可执行结论：连刀 6 号", wolf.contains("今晚连刀 6 号"))
    }

    @Test
    fun `有守卫时，昨夜刀死了就该防着守卫今晚去守预言家`() {
        val wolf = WerewolfPrompt.systemPrompt(nightTwo(target = 6, deaths = listOf(6)), seat = 1)

        assertTrue(wolf.contains("他**出局了**"))
        assertTrue("要提醒别把刀预言家当默认选项", wolf.contains("别把「刀预言家」当成默认选项"))
        assertTrue("要给外置位这条路", wolf.contains("外置位"))
        assertFalse("刀口已经死了，就不该再说连刀他", wolf.contains("今晚连刀 6 号"))
    }

    @Test
    fun `没有守卫的板子不讲守卫博弈，改讲女巫的解药`() {
        val plain = standardGame().copy(
            day = 2,
            phase = Phase.NIGHT_WOLF,
            nights = listOf(NightRecord(day = 1, wolfTarget = 6, deaths = emptyList())),
            history = listOf(GameEvent.Dawn(1, emptyList())),
        )
        val wolf = WerewolfPrompt.systemPrompt(plain, seat = 1)

        assertTrue(wolf.contains("本局没有守卫"))
        assertTrue("平安夜只可能是解药，要说清", wolf.contains("解药"))
        assertFalse("不能讲一个本局不存在的身份", wolf.contains("和守卫博弈"))
        // 打法清单也要按板子裁：没有守卫却让狼去「盘守卫」，等于教它找一个不存在的人
        assertFalse("没有守卫就不该叫它盘守卫", wolf.contains("盘守卫"))
        assertFalse("没有守卫时「有人自称守人」不可能是诈守卫", wolf.contains("诈守卫"))
    }

    @Test
    fun `白天不占篇幅讲下刀博弈`() {
        val day = nightTwo(target = 6, deaths = emptyList()).copy(phase = Phase.DAY_DISCUSSION)

        assertFalse(WerewolfPrompt.systemPrompt(day, seat = 1).contains("和守卫博弈"))
    }

    @Test
    fun `守卫守了谁不会漏进狼的提示词`() {
        // 刀口和守人对不上时，狼只能知道「刀口没死」，不能知道守卫守的是谁。
        // 一旦漏了，狼第二天就能按守人路线挑裸位，守卫这个身份等于白拿。
        val game = nightTwo(target = 6, deaths = emptyList()).copy(lastGuardedSeat = 7)

        val wolf = WerewolfPrompt.systemPrompt(game, seat = 1)
        val guard = WerewolfPrompt.systemPrompt(game, seat = 5)

        assertFalse("守卫的守人路线不能给狼", wolf.contains("守的是 7 号"))
        assertTrue("但守卫自己要知道", guard.contains("你昨夜守的是 7 号"))
    }

    // ------------------------------------------------------------------
    // 板型打法：按板子分开，且不串给对面
    // ------------------------------------------------------------------

    /** 按板子的身份构成摆一局，座位顺序 = 构成声明顺序。 */
    private fun deal(config: WerewolfConfig): WerewolfGame {
        val n = config.playerCount
        val roles = config.composition.flatMap { (role, count) -> List(count) { role } }
        return WerewolfGame.withRoles(config, roster(n), roles)
    }

    @Test
    fun `每张板子都有自己的板型打法`() {
        // 8 张板子的人数结构差得很远（6 人局两边各两刀、12 人熊局打民边只要 3 刀），
        // 不给这一层，模型只能按 8 人局的经验硬套。
        WerewolfBoards.all.forEach { board ->
            val text = Boards.text(board.config)
            assertTrue("${board.key} 缺板型容错", text.common.isNotEmpty())
            assertTrue("${board.key} 缺狼队打法", text.wolves.isNotEmpty())
        }
    }

    @Test
    fun `板型打法按阵营分开：狼的那段不给好人`() {
        WerewolfBoards.all.forEach { board ->
            val game = deal(board.config)
            val text = Boards.text(board.config)
            val wolf = game.players.first { it.role.isWerewolf }.seat
            val good = game.players.first { !it.role.isWerewolf }.seat

            val goodPrompt = WerewolfPrompt.systemPrompt(game, good)
            assertTrue("${board.key} 好人看不到板上的人数算术", goodPrompt.contains(text.common.first()))
            assertFalse("${board.key} 狼的打法漏给了好人", goodPrompt.contains(text.wolves.first()))

            assertTrue(
                "${board.key} 狼看不到自己的板型打法",
                WerewolfPrompt.systemPrompt(game, wolf).contains(text.wolves.first()),
            )
        }
    }

    @Test
    fun `没配过的板子不会硬套别张板子的结论`() {
        // 自定义局（或少见人数）不该拿到「打民边只要 3 刀」这种只对某张板子成立的结论。
        val custom = WerewolfConfig(
            composition = mapOf(
                WerewolfRole.WEREWOLF to 2,
                WerewolfRole.SEER to 1,
                WerewolfRole.VILLAGER to 3,
            )
        )

        assertEquals(BoardText(), Boards.text(custom))
        assertFalse(WerewolfPrompt.systemPrompt(deal(custom), seat = 1).contains("打民边"))
    }

    @Test
    fun `身份规则跟着身份走，换张板子也还在`() {
        val guard10 = WerewolfPrompt.boardBrief(WerewolfBoards.configOf(WerewolfBoards.GUARD_10))
        val guard12 = WerewolfPrompt.boardBrief(WerewolfBoards.configOf(WerewolfBoards.GUARD_12))

        assertTrue(guard10.contains("守卫每夜先于狼人守一个人"))
        assertTrue("同一份守卫规则不该只在 10 人局出现", guard12.contains("守卫每夜先于狼人守一个人"))
        assertFalse("没守卫的板子不该出现守卫规则", WerewolfPrompt.boardBrief(WerewolfConfig()).contains("守卫每夜"))
    }

    // ------------------------------------------------------------------
    // 读人框架 / 屠边账 / 时间预算（对照专栏那局补上的三块）
    // ------------------------------------------------------------------

    @Test
    fun `读人框架含轮次理论：放逐优先级与警推狼刀领先`() {
        // 权威教学里最核心、我们之前整块缺失的一层：每天该出谁有既定优先级。
        // 光会读人不够，读完还得知道「查杀>对跳>行为>发言」和不要分票。
        val good = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)

        assertTrue("要有放逐轮次一块：$good", good.contains("放逐有轮次"))
        assertTrue("要说查杀优先于对跳：$good", good.contains("查杀 > 对跳"))
        assertTrue("要说外置位不替代当天轮次：$good", good.contains("别用外置位替代当天的轮次"))
        assertTrue("要说分票是狼人行为：$good", good.contains("分票是典型的狼人行为"))
        assertTrue("要有警推/狼刀领先判断：$good", good.contains("警推领先还是狼刀领先"))
        assertTrue("要说不到轮次别拍身份：$good", good.contains("不到你的轮次别拍身份"))
        // 轮次理论属于读人框架，夜间决策 includeReading=false 时不该注入
        val lean = WerewolfPrompt.systemPrompt(standardGame(), seat = 3, includeReading = false)
        assertFalse("夜间决策不该带轮次理论", lean.contains("放逐有轮次"))
    }

    @Test
    fun `好人能从发言读身份，且读法辨证不鼓励乱杀`() {
        // 专栏那局五个人跳预言家，真假的差别在验人心路、警徽流和保人，不在嗓门。
        // 这套框架是公共的：好人和狼都看得到，所以文案里不能出现「你们狼」这种漏视角的话。
        val good = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)
        val wolf = WerewolfPrompt.systemPrompt(standardGame(), seat = 1)

        assertTrue("好人要有从发言读人的框架", good.contains("怎么从发言读人"))
        assertTrue("要教看验人心路", good.contains("验人的心路"))
        assertTrue("要教看谁在保人", good.contains("谁在保人"))
        assertTrue("要提醒划水冲锋都反着看", good.contains("划水和冲锋都要反着看"))
        assertTrue("狼也要看到这套框架（才知道自己哪里会被抓）", wolf.contains("怎么从发言读人"))
        assertFalse("公共框架不能漏出阵营视角", good.contains("你们狼"))
    }

    @Test
    fun `读人框架含硬反证翻案与有查杀走查杀`() {
        // 12 人局教训：1 号报 2 号查杀、2 号翻牌猎人开枪带走 1 号——硬反证出现后
        // 3、6 号翻了站边，7 号女巫夜里想通、白天却没翻。框架里必须写明「出现就重盘」。
        // 另一个教训：旧文案「被查杀者不表水=狼」把真平民 9 号害死了，改成权威的「有查杀走查杀」。
        val good = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)

        assertTrue("要有硬反证翻案一条：$good", good.contains("硬反证"))
        assertTrue("要说翻牌/开枪这种可核的证据：$good", good.contains("开枪"))
        assertTrue("要有查杀走查杀一条：$good", good.contains("有查杀走查杀"))
        assertTrue("要说表水态度不是第一依据：$good", good.contains("表水态度只作参考"))
    }

    @Test
    fun `首夜心路不构成证据，且要能验算座位`() {
        // 12 人局复盘：1 号悍跳说「首验开对角验 9 号」，但 12 人局 1 号对角是 7 号、
        // 9 号是下家位——它自己的思考里知道是 7，发言仍说 9。全场却因为「心路清晰」信了它。
        // 框架必须写明：首夜零信息，谁都能编心路；唯一能核的是理由本身能不能验算。
        val good = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)

        assertTrue("要有首夜心路反过来的约束：$good", good.contains("第 1 夜零信息"))
        assertTrue("要说首夜说得出理由不算证据：$good", good.contains("谁都能给随机的一刀编一套漂亮心路"))
        assertTrue("要能验算座位：$good", good.contains("对不上就是编的"))
        assertTrue("要给具体对角例子：$good", good.contains("1 号对角是 7 号"))
        assertTrue("要说心路朴素是实话：$good", good.contains("心路朴素"))
    }

    @Test
    fun `警徽流不把验发过言的人当错`() {
        // 12 号骂 4 号「放着警上发过言的 2、3 不验」——把验发过言的人当成负面，
        // 而 1 号「从抢警徽的人里挖一个」却被当成正面。同一把尺子量出相反结论，
        // 说明这条标准本身是软的，不能当硬证据。
        val good = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)

        assertTrue("要明说验发过言的人照样可能有破绽：$good", good.contains("验发过言的人照样可能有破绽"))
    }

    @Test
    fun `警徽流按权威教学：奔着狼留、看兑现、改流合法`() {
        // 对照狼人杀官方攻略 + 盘逻辑教学补齐判据：
        // - 好坏看「是不是奔着狼留」，悍跳破绽是「不敢验发言差的人」
        // - 留法按发言位置分（前置偏拉票/中置结合/后置纯找狼），别搞警上警下各一张的机械标准
        // - 白天发言结束前改流合法，验过划掉补新目标不算改口，只看有没有兑现
        // - 别数「改了几次」这种可被话术利用的软指标
        val good = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)

        assertTrue("要说好坏看奔着狼留：$good", good.contains("是不是奔着狼留的"))
        assertTrue("要说悍跳不敢验发言差的人：$good", good.contains("不敢验发言差的人"))
        assertTrue("要说按发言位置分：$good", good.contains("留法按发言位置分"))
        assertTrue("要否定机械标准：$good", good.contains("别搞「警上警下必须各一张」的机械标准"))
        assertTrue("要说白天改流合法：$good", good.contains("白天发言结束前改警徽流完全正常"))
        assertTrue("要说验过补新目标不算改口：$good", good.contains("都不算改口"))
        assertTrue("别说「换成反对他最凶的」这种个例判据：$good", !good.contains("反对他最凶"))
        assertTrue("要说别数改了几次：$good", good.contains("别数「改了几次」这种账"))
    }

    @Test
    fun `警上补入诈身份看退水与互验冲锋狼`() {
        // 官方攻略：诈身份的好人一般退水，悍跳狼绝对不退；第一天真假通常互验对方阵营的冲锋狼。
        // 这两条只在「警上是照妖镜」条目里，需要有警长的板子才输出。
        val good = WerewolfPrompt.systemPrompt(gameWithGuard(), seat = 3)

        assertTrue("要说诈身份会退水：$good", good.contains("诈身份的好人一般会退水"))
        assertTrue("要说悍跳狼不退水：$good", good.contains("悍跳狼绝对不退"))
        assertTrue("要说互验对方冲锋狼：$good", good.contains("互验对方阵营的冲锋狼"))
        assertTrue("要说自己阵营找狼的大概率是狼：$good", good.contains("想在自己阵营里找狼"))
    }

    @Test
    fun `盘对跳第一看能不能当场核对而非验人记录数量`() {
        // 旧文案说「报不出连续验人记录就是悍跳」，但第 1 天根本不存在连续记录——
        // 两个预言家首夜都只验了一个人。这条会让好人误伤说实话的 4 号。
        val good = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)

        assertTrue("要改成第一看能当场核对：$good", good.contains("盘对跳第一看"))
        assertTrue("要明确心路流畅不算证据：$good", good.contains("心路讲得再流畅也不算证据"))
        assertTrue("首夜例外仍要在：$good", good.contains("第 1 天只报了一个验人是正常的"))
    }

    @Test
    fun `夜间决策不带读人框架，白天发言照旧带`() {
        // 夜里的下刀 / 查验 / 用药没有发言可读，「从发言读人」那一大段白烧输入。
        // 用 includeReading=false 关掉它；默认仍全量，老行为不变。
        val full = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)
        val lean = WerewolfPrompt.systemPrompt(standardGame(), seat = 3, includeReading = false)

        assertTrue("默认要带读人框架", full.contains("怎么从发言读人"))
        assertFalse("夜间决策不该带读人框架", lean.contains("怎么从发言读人"))
        // 底线不能因为精简而丢：身份、本轮任务、规则都还在
        assertTrue("精简后身份还在", lean.contains("你的身份是"))
        assertTrue("精简后规则还在", lean.contains("本局板子"))
    }

    @Test
    fun `猎人开枪有可执行的读人顺序，不是凭最怀疑`() {
        // 专栏的答案：该打一言未发的深水狼或冲锋狼，不是嗓门大的、也不是救过人的神职。
        val hunter = WerewolfPrompt.systemPrompt(standardGame(), seat = 5)

        assertTrue("猎人要有开枪的读人顺序", hunter.contains("开枪前按这个顺序挑"))
        assertTrue("要点出深水狼", hunter.contains("深水狼"))
        assertTrue("要点出冲锋狼", hunter.contains("冲锋狼"))
        assertTrue("要提醒别打跳得凶的", hunter.contains("挡枪"))
    }

    @Test
    fun `屠边账只对狼，按存活神民动态算且不泄露具体身份`() {
        // 屠边局狼每晚下刀前要算打哪边更快；好人读到只会稀释自己的判断。
        val game = standardGame()
        val wolf = WerewolfPrompt.systemPrompt(game, seat = 1)
        val good = WerewolfPrompt.systemPrompt(game, seat = 3)

        assertTrue("狼要看到屠边账", wolf.contains("屠边账"))
        assertTrue("账本要给出先打哪边的结论", wolf.contains("哪边更近就先打穿哪边"))
        assertFalse("屠边账不能漏给好人", good.contains("屠边账"))
        // 账本只能说「还有几张神牌」，不能替狼点名谁是哪个身份。
        assertFalse("不能泄露具体身份", wolf.contains("3 号是预言家"))
    }

    @Test
    fun `好人能识破反水做身份，但不会被教成见反水就打`() {
        val good = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)

        assertTrue("要教金水反咬的读法", good.contains("接了金水却当场反咬"))
        assertTrue("要给落地判据", good.contains("看他把票砸在哪"))
        assertTrue("必须辩证，不能把真金水冤死", good.contains("真金水也可能嫌验人理由不硬"))
        assertFalse("狼侧战术细节不能漏给好人", good.contains("拿一张狼牌换"))
    }

    @Test
    fun `读人框架含身份力度与心态破绽，且都带反向口子`() {
        // 专栏那局：真预连续报对神职身份是力度，「希望验到好人」是心态破绽；
        // 但同一局也有人替后者翻案（不专业反而真实），所以两条都必须自带反例。
        val good = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)

        assertTrue("要有「看链条不看单次」", good.contains("报对的力度看链条，不看单次"))
        assertTrue("要提醒撞对一次不能当铁预", good.contains("报对一次不等于从此免检"))
        assertTrue("要有心态破绽这条", good.contains("听他找的是狼还是票"))
        assertTrue("心态也必须留反向口子", good.contains("但这不等于狼"))

        // 公共框架，狼也看得到，但拿不到指认真好人的新武器
        assertTrue("狼也该看到这套框架", WerewolfPrompt.systemPrompt(standardGame(), seat = 1).contains("报对的力度看链条"))
    }

    @Test
    fun `读人框架能识破工整话术，但不把话糙当真预`() {
        // 专栏里有人用「验人逻辑闭环／票型分析」三段式包装出错误结论，
        // 也有人靠「真相只有一个」的口头禅压人。格式本身不是证据。
        val good = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)

        assertTrue("要有工整话术辨识", good.contains("话术工整不等于身份真"))
        assertTrue("要说格式不是证据", good.contains("格式不是证据"))
        assertTrue("落点要可验证", good.contains("报的查杀后来对不对"))
        assertTrue("别把话糙当悍跳", good.contains("别因为「不够专业」就把人打成悍跳"))
    }

    @Test
    fun `屠城局不算屠边账`() {
        val slayAll = standardGame().copy(config = WerewolfConfig(winRule = WinRule.SLAY_ALL))
        assertFalse(WerewolfPrompt.systemPrompt(slayAll, seat = 1).contains("屠边账"))
    }

    @Test
    fun `时间预算告诉模型时限与结论前置，不限档不写`() {
        val fast = WerewolfPrompt.systemPrompt(standardGame(), seat = 3, limit = WerewolfTurnLimit.FAST)

        assertTrue("要说思考时限", fast.contains("想的时间大约 30 秒"))
        assertTrue("发言段不限时不写秒数", fast.contains("说出口之后不限时间"))
        assertFalse("不该再报发言秒数", fast.contains("说的时间大约"))
        assertTrue("要教结论前置", fast.contains("先把结论说出来"))
        assertTrue("要说超时的后果", fast.contains("关掉你这一局的深度思考"))

        // 不传（测试、复盘等场合）和不限档都不写，保持原来的行为
        assertFalse(WerewolfPrompt.systemPrompt(standardGame(), seat = 3).contains("时间预算"))
        assertFalse(
            WerewolfPrompt.systemPrompt(standardGame(), seat = 3, limit = WerewolfTurnLimit.UNLIMITED)
                .contains("时间预算")
        )
    }

    @Test
    fun `发言长度写进提示词，不传时不注入`() {
        val short = WerewolfPrompt.systemPrompt(
            standardGame(), seat = 3, speechLength = WerewolfSpeechLength.SHORT,
        )

        assertTrue("要说上限", short.contains("80 字以内"))
        assertTrue("要说档位名", short.contains("简短"))
        assertTrue("要说明超长会被截", short.contains("截断"))
        // 不传（测试、复盘等场合）不注入，保持原行为
        assertFalse(
            WerewolfPrompt.systemPrompt(standardGame(), seat = 3).contains("【发言长度】"),
        )
    }

    @Test
    fun `不限档不报字数也不说截断，改成结论前置`() {
        val prompt = WerewolfPrompt.systemPrompt(
            standardGame(), seat = 3, speechLength = WerewolfSpeechLength.UNLIMITED,
        )

        assertTrue("要说档位名", prompt.contains("不限"))
        assertFalse("没有上限就不该报字数", prompt.contains("字以内"))
        assertFalse("没有截断兜底就不能说截", prompt.contains("截断"))
        assertTrue("不限档要靠结论前置保住重点", prompt.contains("先把结论说出来"))
    }

    @Test
    fun `时间预算排在自定义设定之前，用户仍能覆盖`() {
        val prompt = WerewolfPrompt.systemPrompt(
            standardGame(), seat = 3, limit = WerewolfTurnLimit.STANDARD,
            custom = "狼人白天要更凶一点",
        )
        assertTrue(prompt.contains("【本局额外设定"))
        assertTrue(prompt.contains("时间预算"))
        assertTrue(
            "自定义设定必须在时间预算之后（后写优先）",
            prompt.indexOf("【本局额外设定") > prompt.indexOf("【时间预算】"),
        )
    }

    // ------------------------------------------------------------------
    // 残局挡刀：好人穿神衣服
    // ------------------------------------------------------------------

    /** 8 人局去掉女巫和两个平民，剩 1/2 狼、3 预言家、5 猎人、8 平民。 */
    private fun endgame() = standardGame().let { g ->
        val dead = setOf(4, 6, 7)
        g.copy(
            day = 3,
            players = g.players.map { if (it.seat in dead) it.copy(alive = false) else it },
            history = listOf(GameEvent.Dawn(1, dead.toList())),
        )
    }

    @Test
    fun `残局好人才会收到穿神衣服挡刀，狼看不到`() {
        val g = endgame()
        val villager = WerewolfPrompt.systemPrompt(g, seat = 8)
        val seer = WerewolfPrompt.systemPrompt(g, seat = 3)
        val wolf = WerewolfPrompt.systemPrompt(g, seat = 1)

        assertTrue("平民要能穿神衣服", villager.contains("穿神的衣服"))
        assertTrue("要说清这是允许的伪装", villager.contains("策略性伪装"))
        assertTrue("平民要知道优先跳已出局的神", villager.contains("已经出局的神"))
        assertTrue("要有跳了别缩的翻车点", villager.contains("跳了就别缩"))
        assertTrue("神职残局可以跳明挡刀", seer.contains("藏着已经不如站出来值钱"))
        assertFalse("狼绝不能知道平民也会穿神衣服", wolf.contains("穿神的衣服"))
    }

    @Test
    fun `人多的时候不给挡刀策略，免得把好人架上焦点位`() {
        assertFalse(WerewolfPrompt.systemPrompt(standardGame(), seat = 8).contains("穿神的衣服"))
    }

    @Test
    fun `狼侧屠边账不再默认平民不会跳身份`() {
        // 只剩 1 个平民时狼才会去算「平民这边更近」那条分支。
        val g = standardGame().let { game ->
            game.copy(players = game.players.map {
                if (it.seat in setOf(7, 8)) it.copy(alive = false) else it
            })
        }
        val wolf = WerewolfPrompt.systemPrompt(g, seat = 1)
        assertFalse("不能再写死平民不会跳身份", wolf.contains("平民不会跳身份"))
        assertTrue("狼要提防穿神衣服挡刀的平民", wolf.contains("穿神衣服挡刀"))
    }

    // ------------------------------------------------------------------
    // 提示词风格：引导式（默认）保持原样，自由式只给事实和底线
    // ------------------------------------------------------------------

    @Test
    fun `不传风格时和显式引导式完全一致`() {
        val game = standardGame()
        (1..8).forEach { seat ->
            assertEquals(
                "$seat 号默认提示词必须和引导式逐字相同",
                WerewolfPrompt.systemPrompt(game, seat),
                WerewolfPrompt.systemPrompt(game, seat, style = WerewolfPromptStyle.GUIDED),
            )
        }
    }

    @Test
    fun `自由式拿掉全部内置打法`() {
        val game = standardGame()
        val good = WerewolfPrompt.systemPrompt(game, seat = 3, style = WerewolfPromptStyle.FREE)
        val wolf = WerewolfPrompt.systemPrompt(game, seat = 1, style = WerewolfPromptStyle.FREE)

        // 公共打法层：读人框架、板型结论、狼队菜单、屠边账
        assertFalse("自由式不给读人框架", good.contains("怎么从发言读人"))
        assertFalse("自由式不给验人心路", good.contains("验人的心路"))
        assertFalse("自由式不给基本打法整段", good.contains("【基本打法】"))
        assertFalse("自由式不给狼队战术菜单", wolf.contains("【狼队打法】"))
        assertFalse("自由式不给屠边账", wolf.contains("屠边账"))
        assertFalse("自由式不给刀法分析", wolf.contains("撞守"))
        // 身份作业：预言家的三段式发言、女巫的用药教学
        assertFalse("自由式不给身份作业", good.contains("你的白天发言只有三件任务"))
        assertFalse("自由式不给残局穿神衣服", good.contains("穿神衣服挡刀"))
    }

    @Test
    fun `自由式照样给规则与身份机制，只砍打法`() {
        // 曾经这一档只给「人数 + 配比 + 有无警长」，理由是规则都在模型训练数据里。
        // 实测打不住：本局有几条不是标准局先验（屠边、警长 1.5 票、守救同归、
        // 自爆当晚仍定刀），模型按网上的记忆推，结论整个是错的；更常见的是它压根
        // 不知道别人那张牌能干什么，白天一本正经把规则说错。
        // 现在口径：**规则是事实，照给；打法才是答案，砍掉。**
        val game = standardGame()
        val good = WerewolfPrompt.systemPrompt(game, seat = 3, style = WerewolfPromptStyle.FREE)
        val wolf = WerewolfPrompt.systemPrompt(game, seat = 1, style = WerewolfPromptStyle.FREE)

        // 事实层：这几样拿掉游戏就没法打
        assertTrue("身份还在", good.contains("你的身份是预言家"))
        assertTrue("局势还在", good.contains("【当前局势】"))
        assertTrue("本轮任务还在", good.contains("【本轮任务】"))
        assertTrue("私有信息还在", wolf.contains("【只有你知道的事】"))
        assertTrue("狼队友还在", wolf.contains("你的狼队友"))
        // 保密底线（狼队内部的事不出白天）是硬规则，自由式照给
        assertTrue("保密底线还在", wolf.contains("不许出现「队友」"))

        assertTrue("配比还在", good.contains("8 人局：2 狼人、预言家、女巫、猎人、3 平民"))
        // 全局规则：胜负、绑票、平票、遗言、自爆、暗牌
        assertTrue("胜负规则要给", good.contains("【胜负规则】"))
        assertTrue("绑票要给", good.contains("【绑票】"))
        assertTrue("平票怎么处理要给", good.contains("【白天投票】"))
        assertTrue("遗言规则要给", good.contains("【遗言】"))
        assertTrue("自爆机制要给", good.contains("【自爆】"))
        assertTrue("要让狼知道自爆当晚仍定刀", wolf.contains("当晚仍在狼队里"))
        assertTrue("暗牌局要给", good.contains("暗牌局"))
        assertTrue("要断掉翻牌谣言", good.contains("不是事实"))
        // 身份技能与限制：**别人也要知道**，否则解释不了场上的事
        assertTrue("猎人的枪要给", good.contains("猎人被狼人杀死或被投票放逐时可以开枪"))
        assertTrue("猎人枪的毒例外要给", good.contains("被女巫毒死时不能开枪"))
        assertTrue("女巫的药要给", good.contains("一瓶解药和一瓶毒药"))
        assertTrue("女巫同夜不能双药要给", good.contains("同一夜不能同时用两瓶药"))

        // 打法仍然一句不给
        assertFalse("不给基本打法整段", good.contains("【基本打法】"))
        assertFalse("不给读人框架", good.contains("怎么从发言读人"))
        assertFalse("不给身份作业", good.contains("你的白天发言只有三件任务"))
        assertFalse("不给狼队战术菜单", wolf.contains("【狼队打法】"))
        assertFalse("不给刀法分析", wolf.contains("撞守"))
        assertFalse("不给屠边账", wolf.contains("屠边账"))
    }

    @Test
    fun `自由式的狼王板子要给枪的完整限制`() {
        // 狼王最容易错的两条：被票出能开枪（回合内也算）、被毒不能开枪。
        // 只有 rules 层写着，自由式曾整段拿不到——它会把「被毒死没开枪」当成别人在装。
        val kingGame = WerewolfGame.withRoles(
            config = WerewolfConfig(
                composition = mapOf(
                    WerewolfRole.WEREWOLF to 1,
                    WerewolfRole.WEREWOLF_KING to 1,
                    WerewolfRole.SEER to 1,
                    WerewolfRole.WITCH to 1,
                    WerewolfRole.HUNTER to 1,
                    WerewolfRole.VILLAGER to 3,
                ),
                winRule = WinRule.SLAY_SIDE,
                hasSheriff = false,
            ),
            roster = roster(8),
            roles = listOf(
                WerewolfRole.WEREWOLF,
                WerewolfRole.WEREWOLF_KING,
                WerewolfRole.SEER,
                WerewolfRole.WITCH,
                WerewolfRole.HUNTER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
            ),
        )
        val free = WerewolfPrompt.systemPrompt(kingGame, seat = 6, style = WerewolfPromptStyle.FREE)

        assertTrue("被票出能开枪", free.contains("被投票放逐时可以开枪"))
        assertTrue("被毒不能开枪", free.contains("被毒死或被猎人带走时不能开枪"))
    }

    @Test
    fun `屠边局要点名神职名单并逼狼选边`() {
        // 原来只写「杀光所有神职，或杀光所有平民」。两个坑：
        // ① 模型把「神职」理解成预女猎，漏掉守卫/白痴/熊，刀口数按错的算；
        // ② 「或」被读成「随便」，于是今晚压神边、明晚砍民，最后两边都差一刀。
        val guardGame = gameWithGuard()
        val free = WerewolfPrompt.systemPrompt(guardGame, seat = 3, style = WerewolfPromptStyle.FREE)

        assertTrue("要点名本局的神职", free.contains("本局算神的是："))
        assertTrue("守卫也得算神", free.contains("守卫"))
        assertTrue("要写明全灭才算屠神", free.contains("全部出局"))
        assertTrue("白痴翻牌不算出局要写清", free.contains("翻牌不算出局"))
        assertTrue("狼王不算神", free.contains("狼王）永远不算神"))
        assertTrue("必须选一条边打穿", free.contains("两条边必须选一条打穿"))

        // 屠城局没有「边」，这套话不该出现
        val cityGame = WerewolfGame.withRoles(
            config = WerewolfConfig(
                composition = mapOf(
                    WerewolfRole.WEREWOLF to 2,
                    WerewolfRole.SEER to 1,
                    WerewolfRole.WITCH to 1,
                    WerewolfRole.HUNTER to 1,
                    WerewolfRole.VILLAGER to 3,
                ),
                winRule = WinRule.SLAY_ALL,
                hasSheriff = false,
            ),
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
        val city = WerewolfPrompt.systemPrompt(cityGame, seat = 3, style = WerewolfPromptStyle.FREE)
        assertTrue("屠城局规则是票权", city.contains("屠城局"))
        assertFalse("屠城局没有选边这回事", city.contains("两条边必须选一条打穿"))
        assertFalse("屠城局也不给屠边账", city.contains("【屠边账"))
    }

    @Test
    fun `自由式狼既没有屠边进度也没有屠边账`() {
        // 屠边进度（还剩几神几民）曾经两种风格都发，理由是「板子开局公布、出局有播报，谁都数得出来」。
        // 撤回：数得出来不等于该替它数。真人坐牌桌上，数剩余神民是玩家自己的一步；
        // 把算好的数字直接摆在狼面前，等于给它配计算器，而好人没有任何对等的东西。
        // 现在规则里仍点名本局神职名单（硬规则），剩下数几张、打哪边让它自己算。
        val game = gameWithGuard()
        val free = WerewolfPrompt.systemPrompt(game, seat = 1, style = WerewolfPromptStyle.FREE)
        assertFalse("自由式不给屠边进度", free.contains("【屠边进度】"))
        assertFalse("自由式也不给屠边账", free.contains("【屠边账"))
        assertFalse("更不会替它算哪边更近", free.contains("更近"))

        val guided = WerewolfPrompt.systemPrompt(game, seat = 1, style = WerewolfPromptStyle.GUIDED)
        assertTrue("引导式照旧给账本", guided.contains("【屠边账"))
        assertFalse("账本也不再单独甩一份剩余神民数", guided.contains("【屠边进度】"))
    }

    @Test
    fun `特殊身份的机制进公共板子说明`() {
        // 白痴、熊、狼王的机制都只在各自 kit 的 rules 里。自由式砍掉 boardBrief 时
        // 它们一起消失，于是场上没人知道「被票翻牌不死、此后没投票权」「熊为什么咆哮」。
        val fool = WerewolfPrompt.boardBrief(WerewolfBoards.configOf(WerewolfBoards.FOOL_12))
        assertTrue("白痴翻牌机制", fool.contains("白痴被投票放逐时翻开身份"))
        assertTrue("翻牌后失去投票权", fool.contains("不能再投票"))

        val bear = WerewolfPrompt.boardBrief(WerewolfBoards.configOf(WerewolfBoards.BEAR_12))
        assertTrue("熊咆哮机制", bear.contains("熊是好人神职"))
        assertTrue("熊死了不咆哮", bear.contains("熊死了也不会咆哮"))

        val guard = WerewolfPrompt.boardBrief(WerewolfBoards.configOf(WerewolfBoards.GUARD_10))
        assertTrue("守卫不能连守", guard.contains("不能连续两夜守同一个人"))
    }

    @Test
    fun `自由式的守卫板子照样给守救同归规则`() {
        // 和暗牌局同类：守救同归是守卫+女巫同时在场才成立的硬规则事实，
        // 模型推不出来，而且守卫 notes 里那句「守的人当夜不会被刀死」和它直接矛盾。
        // 自由模式砍掉 boardBrief/rulesBrief 时把这条一起砍没了，会误导守卫和女巫。
        val guardGame = gameWithGuard()
        val free = WerewolfPrompt.systemPrompt(guardGame, seat = 3, style = WerewolfPromptStyle.FREE)
        val guided = WerewolfPrompt.systemPrompt(guardGame, seat = 3)

        assertTrue("自由式必须给守救同归", free.contains("守救同归"))
        assertTrue("引导式也保留", guided.contains("守救同归"))
        // 无守卫的板子不该出现这条规则
        val noGuard = WerewolfPrompt.systemPrompt(standardGame(), seat = 3, style = WerewolfPromptStyle.FREE)
        assertFalse("无守卫板子不给守救同归", noGuard.contains("守救同归"))
    }

    @Test
    fun `藏身份收尾只在引导式给`() {
        // 「不要暴露身份」是打法倾向不是游戏规则：它会把模型从头到尾
        // 按死在隐藏路线上，跳身份这个最核心的自由度就没了。
        // 自由式把这个决定还给模型；引导式照旧教它藏。
        val game = standardGame()
        val freeHunter = WerewolfPrompt.systemPrompt(game, seat = 5, style = WerewolfPromptStyle.FREE)
        val guidedHunter = WerewolfPrompt.systemPrompt(game, seat = 5, style = WerewolfPromptStyle.GUIDED)
        val freeWolf = WerewolfPrompt.systemPrompt(game, seat = 1, style = WerewolfPromptStyle.FREE)
        val guidedWolf = WerewolfPrompt.systemPrompt(game, seat = 1, style = WerewolfPromptStyle.GUIDED)
        val freeVillager = WerewolfPrompt.systemPrompt(game, seat = 6, style = WerewolfPromptStyle.FREE)

        assertFalse("自由式猎人不受藏身份约束", freeHunter.contains("不要主动说出来"))
        assertTrue("引导式猎人照旧藏", guidedHunter.contains("不要主动说出来"))
        assertFalse("自由式狼也不受收尾约束", freeWolf.contains("不能在白天直接说出来"))
        assertTrue("引导式狼照旧有收尾", guidedWolf.contains("不能在白天直接说出来"))
        // 硬规则在 notes 里，自由式照样给：狼队内部的事不能出现在白天
        assertTrue("自由式狼保密底线还在", freeWolf.contains("不许出现「队友」"))
        // 平民本来就没有要藏的（conceal 为 null），两种风格都不出现
        assertFalse("平民没有收尾", freeVillager.contains("不能在白天直接说出来"))
        // 自由式里没有「公共打法」这一层，狼的提示词不能再引用它
        assertFalse("自由式不引用公共打法", freeWolf.contains("公共打法里说"))
        assertTrue("引导式狼保留引用澄清", guidedWolf.contains("公共打法里说"))
    }

    @Test
    fun `自由式任务文案去掉打法教学`() {
        // 跳不跳、什么时候自爆、药怎么用、警徽给谁——自由模式里全还给模型。
        val day = standardGame().copy(phase = Phase.DAY_DISCUSSION)
        val checks = standardGame().copy(
            phase = Phase.DAY_DISCUSSION,
            seerChecks = listOf(SeerCheck(day = 1, targetSeat = 2, isWerewolf = true)),
        )

        // 预言家白天：「先报验人」等于强制跳身份
        assertFalse("自由式不强制预言家报验人", WerewolfPrompt.task(checks, seat = 3, style = WerewolfPromptStyle.FREE).contains("先报验人"))
        assertTrue("引导式照旧教先报验人", WerewolfPrompt.task(checks, seat = 3).contains("先报验人"))

        // 白天讨论：论证结构不给；自爆只留纯机制（怎么写才受理、代价），不给时机教学
        val freeWolf = WerewolfPrompt.task(day, seat = 1, style = WerewolfPromptStyle.FREE)
        assertFalse("自由式不讲论证结构", freeWolf.contains("具体依据"))
        assertFalse("自由式不自爆时机教学", freeWolf.contains("值得的情况只有一种"))
        assertFalse("自由式不拿「不许爆」代打", freeWolf.contains("都不许爆"))
        assertFalse("自由式不举「救 X 号」示例", freeWolf.contains("救 X 号"))
        assertTrue("自由式保留自爆机制说明", freeWolf.contains("【自爆】"))
        assertTrue("受理条件要说明", freeWolf.contains("系统就受理"))
        assertFalse("受理不再要求点名队友", freeWolf.contains("还活着的队友"))
        assertTrue("引导式自爆教学还在", WerewolfPrompt.task(day, seat = 1).contains("值得爆的只有两种"))
        assertTrue(
            "引导式要点明被查杀不是自爆理由",
            WerewolfPrompt.task(day, seat = 1).contains("被查杀不是理由")
        )
        assertTrue("格式要求保留", freeWolf.contains("不要写「某某说：」"))
        assertTrue("反复读提醒保留", freeWolf.contains("复读旧话"))

        // 竞选发言：演说教学不给；自爆只留纯机制
        val speech = standardGame().copy(phase = Phase.DAY_SHERIFF_SPEECH)
        val freeSpeech = WerewolfPrompt.task(speech, seat = 6, style = WerewolfPromptStyle.FREE)
        assertFalse("自由式不教竞选演说", freeSpeech.contains("越像真预言家"))
        val freeWolfSpeech = WerewolfPrompt.task(speech, seat = 1, style = WerewolfPromptStyle.FREE)
        assertFalse("自由式不自爆时机教学", freeWolfSpeech.contains("另一种打法"))
        assertTrue("自由式保留竞选自爆机制", freeWolfSpeech.contains("【自爆】"))
        assertTrue("竞选格式保留", freeSpeech.contains("不要念座位号清单"))
        assertTrue("引导式竞选教学照旧", WerewolfPrompt.task(speech, seat = 6).contains("越像真预言家"))

        // 夜里各步的策略措辞
        val seerNight = standardGame().copy(phase = Phase.NIGHT_SEER)
        assertFalse("自由式不教验人策略", WerewolfPrompt.task(seerNight, seat = 3, style = WerewolfPromptStyle.FREE).contains("别浪费这一验"))
        val witchNight = standardGame().copy(phase = Phase.NIGHT_WITCH, tonightVictim = 7)
        val freeWitch = WerewolfPrompt.task(witchNight, seat = 4, style = WerewolfPromptStyle.FREE)
        assertFalse("自由式不教用药时机", freeWitch.contains("更关键的时候"))
        assertTrue("可以不用药的机制保留", freeWitch.contains("也可以什么都不做"))

        // 守卫（带守卫的局才有守夜阶段）
        val guardNight = gameWithGuard().copy(phase = Phase.NIGHT_GUARD)
        assertFalse("自由式不教守人策略", WerewolfPrompt.task(guardNight, seat = 5, style = WerewolfPromptStyle.FREE).contains("首夜空守是常见打法"))
        assertTrue("引导式教首夜空守（权威主流）", WerewolfPrompt.task(guardNight, seat = 5).contains("首夜空守是常见打法"))

        // 狼聊：怂恿语气与盘守卫的打法指令不给，「自刀空刀可选」与「撞守无效」机制保留
        val guardChat = gameWithGuard().copy(phase = Phase.NIGHT_WOLF_CHAT)
        val freeChat = WerewolfPrompt.task(guardChat, seat = 1, style = WerewolfPromptStyle.FREE)
        assertFalse("自由式不怂恿自刀空刀", freeChat.contains("别怕队友觉得奇怪"))
        assertTrue("自刀空刀可选保留", freeChat.contains("自刀和空刀也可以"))
        assertFalse("自由式不教盘守卫", freeChat.contains("必须把守卫算进去"))
        assertTrue("撞守规则保留", freeChat.contains("撞上守卫就无效"))

        // 上警：风险评估代打不给
        val signup = standardGame().copy(phase = Phase.DAY_SHERIFF_SIGNUP)
        val freeSignup = WerewolfPrompt.task(signup, seat = 6, style = WerewolfPromptStyle.FREE)
        assertFalse("自由式不代打风险评估", freeSignup.contains("被狼人优先盯上"))
        assertTrue("报名机制保留", freeSignup.contains("上过警的人首轮不能投警长票"))
    }

    @Test
    fun `自由式私有段与决策指令不代打判断`() {
        // 白痴：翻牌机制保留，翻牌后的发言教学不给
        val idiotGame = WerewolfGame.withRoles(
            config = WerewolfConfig(
                composition = mapOf(
                    WerewolfRole.WEREWOLF to 2,
                    WerewolfRole.SEER to 1,
                    WerewolfRole.IDIOT to 1,
                    WerewolfRole.VILLAGER to 4,
                ),
            ),
            roster = roster(8),
            roles = listOf(
                WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF, WerewolfRole.SEER,
                WerewolfRole.IDIOT, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
            ),
        )
        val freeIdiot = WerewolfPrompt.systemPrompt(idiotGame, seat = 4, style = WerewolfPromptStyle.FREE)
        val guidedIdiot = WerewolfPrompt.systemPrompt(idiotGame, seat = 4)
        assertTrue("翻牌机制保留", freeIdiot.contains("翻开身份但不死"))
        assertFalse("自由式不教翻牌后怎么说话", freeIdiot.contains("只剩一张嘴"))
        assertTrue("自由式暗牌规则也要写明翻牌例外", freeIdiot.contains("唯一的例外是白痴"))
        assertTrue("引导式照旧", guidedIdiot.contains("只剩一张嘴"))

        // 警长私有段：移交机制保留，判断代打不给
        val sheriffGame = gameWithGuard().copy(sheriffSeat = 6)
        val freeSheriff = WerewolfPrompt.privateBriefing(sheriffGame, seat = 6, style = WerewolfPromptStyle.FREE)
        val guidedSheriff = WerewolfPrompt.privateBriefing(sheriffGame, seat = 6)
        assertTrue("移交机制保留", freeSheriff.contains("把警徽交给谁"))
        assertFalse("自由式不代打警徽判断", freeSheriff.contains("比撕掉更糟"))
        assertTrue("引导式照旧", guidedSheriff.contains("比撕掉更糟"))

        // 决策指令：警徽与方向两处
        val game = gameWithGuard()
        assertFalse(
            "自由式警徽指令不代打",
            WerewolfDecisions.badgeTransfer(game, 6, free = true).instruction.contains("比撕掉更糟"),
        )
        assertTrue(
            "引导式警徽指令照旧",
            WerewolfDecisions.badgeTransfer(game, 6).instruction.contains("比撕掉更糟"),
        )
        assertFalse(
            "自由式方向指令不举策略例子",
            WerewolfDecisions.speechDirection(game, 6, free = true).instruction.contains("可疑的人先说"),
        )
        assertTrue(
            "引导式方向指令照旧",
            WerewolfDecisions.speechDirection(game, 6).instruction.contains("可疑的人先说"),
        )
    }

    @Test
    fun `自由式不破坏信息隔离`() {
        val game = standardGame()
        // 好人的自由式提示词里不该出现任何狼的私有信息
        (3..8).forEach { seat ->
            val good = WerewolfPrompt.systemPrompt(game, seat, style = WerewolfPromptStyle.FREE)
            assertFalse("$seat 号好人不能看到狼队友", good.contains("你的狼队友"))
            assertFalse("$seat 号好人不能看到狼的战术安排", good.contains("昨晚头狼定的战术"))
        }
        // 狼的私有简报也不该把别人的身份说出来（只给队友名单，不给全场身份表）
        val wolf = WerewolfPrompt.systemPrompt(game, seat = 1, style = WerewolfPromptStyle.FREE)
        assertFalse("不能泄露预言家身份", wolf.contains("3 号是预言家"))
        assertFalse("不能泄露女巫身份", wolf.contains("4 号是女巫"))
    }

    @Test
    fun `两种风格都给时间预算`() {
        val game = standardGame()
        val guided = WerewolfPrompt.systemPrompt(
            game, seat = 3, limit = WerewolfTurnLimit.FAST, style = WerewolfPromptStyle.GUIDED,
        )
        val free = WerewolfPrompt.systemPrompt(
            game, seat = 3, limit = WerewolfTurnLimit.FAST, style = WerewolfPromptStyle.FREE,
        )
        assertTrue("引导式给时间预算", guided.contains("【时间预算】"))
        // 时限是环境约束不是打法：不给会让模型铺垫太长被掐断，测的就成了超时兜底
        assertTrue("自由式也给时间预算", free.contains("【时间预算】"))
        assertTrue("自由式也要结论前置", free.contains("先把结论说出来"))
    }

    @Test
    fun `狼可以对跳混淆视听，两种风格都不再被锁成平民`() {
        val game = standardGame()
        val guided = WerewolfPrompt.systemPrompt(game, seat = 1, style = WerewolfPromptStyle.GUIDED)
        val free = WerewolfPrompt.systemPrompt(game, seat = 1, style = WerewolfPromptStyle.FREE)

        for ((name, prompt) in listOf("引导式" to guided, "自由式" to free)) {
            assertFalse("${name}不能再写「只有一份档案：普通好人」", prompt.contains("只有一份档案：普通好人"))
            assertTrue("${name}要说清可以跳假身份", prompt.contains("宣称的身份可以自己挑"))
            assertTrue("${name}要说混淆视听是正当武器", prompt.contains("混淆视听"))
            // 真事 vs 假话的边界必须给：否则狼怕自曝就什么都不敢跳
            assertTrue("${name}要区分自曝与混淆", prompt.contains("叫自曝"))
            assertTrue("${name}要给假验人假银水的具体例子", prompt.contains("报假验人"))
        }
        // 引导式 playbook 里那句旧文案也要改掉
        assertFalse(guided.contains("只有一个身份：普通好人"))
        assertTrue("引导式 playbook 也要说这是正当武器", guided.contains("正当武器"))
    }

    @Test
    fun `狼的对跳许可不泄漏给好人`() {
        val game = standardGame()
        (3..8).forEach { seat ->
            val good = WerewolfPrompt.systemPrompt(game, seat)
            assertFalse("$seat 号好人不能看到狼的对跳许可", good.contains("宣称的身份可以自己挑"))
            assertFalse("$seat 号好人不能看到真事假话的区分", good.contains("叫自曝"))
        }
    }

    @Test
    fun `对跳许可不拆掉狼队真事的保密底线`() {
        // 开放对跳 ≠ 允许泄底：队友、刀口、夜聊仍然只能在夜里说
        // 「不许提刀过谁」只在白天分支给（夜里还在定刀，说这句自相矛盾）
        val night = WerewolfPrompt.systemPrompt(standardGame(), seat = 1)
        val day = WerewolfPrompt.systemPrompt(
            standardGame().copy(phase = Phase.DAY_DISCUSSION), seat = 1,
        )
        assertTrue("保密底线还在", night.contains("狼队内部的一切只能在夜里说"))
        assertTrue("不许说我们狼", night.contains("不许出现「队友」「我们狼」"))
        assertTrue("不许提刀过谁", day.contains("一个字都不许提你们刀过谁"))
        // 公共「神职不要报身份」要被点破为不约束狼，否则模型自相矛盾
        assertTrue("要澄清神职保密不约束狼", night.contains("不约束你"))
    }

    @Test
    fun `术语约定两种风格都给 且否掉自造说法`() {
        val game = standardGame()
        listOf(WerewolfPromptStyle.GUIDED, WerewolfPromptStyle.FREE).forEach { style ->
            val prompt = WerewolfPrompt.systemPrompt(game, seat = 3, style = style)
            assertTrue("$style 也要有术语约定", prompt.contains("【术语约定】"))
            assertTrue("$style 要说警徽流", prompt.contains("警徽流"))
            assertTrue("$style 要禁止验人链这种自造词", prompt.contains("不要说成「验人链"))
            assertTrue("$style 要禁止自造身份", prompt.contains("不要生造"))
        }
    }

    @Test
    fun `公开台账拼在局势之后、私有信息之前`() {
        val game = standardGame()
        val ledger = "【公开台账】\n· 声称身份：1 号自称预言家（第 1 天）\n"
        val with = WerewolfPrompt.systemPrompt(game, seat = 6, ledger = ledger)
        val without = WerewolfPrompt.systemPrompt(game, seat = 6)

        assertTrue("传了就要出现", with.contains("1 号自称预言家"))
        assertFalse("不传时不能凭空多一段", without.contains("【公开台账】"))
        // 位置很重要：台账是公开材料，必须挨着「已经发生的事」，
        // 落到私有简报之后就会被读成「只有我知道的情报」。
        assertTrue(
            "台账要排在私有信息之前",
            with.indexOf("【公开台账】") < with.indexOf("【只有你知道的事】"),
        )
        assertTrue("台账要排在局势之后", with.indexOf("【已经发生的事】") < with.indexOf("【公开台账】"))
    }

    // ------------------------------------------------------------------
    // 搜索式
    // ------------------------------------------------------------------

    @Test
    fun `搜索式只给版型和胜负`() {
        val game = standardGame()
        val search = WerewolfPrompt.systemPrompt(game, seat = 6, style = WerewolfPromptStyle.SEARCH)

        assertTrue("版型要说清几人几狼", search.contains("【版型】本局 8 人"))
        assertTrue("狼的数量", search.contains("狼人 ×2"))
        assertTrue("胜负条件", search.contains("【胜负】"))
        assertTrue("屠边是本局规则，模型查不到", search.contains("屠边"))
    }

    @Test
    fun `搜索式不再给规则细节与打法`() {
        val game = standardGame()
        val search = WerewolfPrompt.systemPrompt(
            game, seat = 6, style = WerewolfPromptStyle.SEARCH, webSearch = true,
        )
        val guided = WerewolfPrompt.systemPrompt(game, seat = 6, style = WerewolfPromptStyle.GUIDED)

        // 引导式里有、搜索式里一条都不该留：这些正是「查得到」的部分。
        listOf(
            "【遗言】",
            "【自爆】",
            "【术语约定】",
            "怎么从发言读人",
            "先定站边",
        ).forEach { marker ->
            assertTrue("引导式应当有：$marker", guided.contains(marker))
            assertFalse("搜索式不该再有：$marker", search.contains(marker))
        }
        assertTrue("搜索式要比引导式短得多", search.length * 3 < guided.length)
    }

    @Test
    fun `搜索式仍然给私有事实与输出要求`() {
        val game = standardGame()
        val wolf = WerewolfPrompt.systemPrompt(game, seat = 1, style = WerewolfPromptStyle.SEARCH)
        // 不知道自己是谁、队友是谁，模型这一局就没法打——这是三档共有的底线
        assertTrue("身份", wolf.contains("你的身份是狼人"))
        assertTrue("队友", wolf.contains("你的狼队友"))
        assertTrue("本轮任务", wolf.contains("【本轮任务】"))

        val seer = WerewolfPrompt.systemPrompt(game, seat = 3, style = WerewolfPromptStyle.SEARCH)
        assertTrue("预言家要看得到自己的验人底账", seer.contains("预言家"))
        assertFalse("但不能看到狼的队友信息", seer.contains("你的狼队友"))
    }

    @Test
    fun `只有真的能联网时才告诉它可以查`() {
        val game = standardGame()
        val canSearch = WerewolfPrompt.systemPrompt(
            game, seat = 6, style = WerewolfPromptStyle.SEARCH, webSearch = true,
        )
        val cannot = WerewolfPrompt.systemPrompt(
            game, seat = 6, style = WerewolfPromptStyle.SEARCH, webSearch = false,
        )
        assertTrue(canSearch.contains("你可以联网检索"))
        // 这一档叫「搜索式」，就不是「可以查也可以不查」：留余地的话模型会一律凭记忆答
        assertTrue("要明确要求它该查就查", canSearch.contains("该查就查"))
        // 厂商不支持时不能这么说：模型会把「我记得」当成「我查过」，编得更理直气壮
        assertFalse(cannot.contains("你可以联网检索"))

        // 引导式/自由式即使能联网也不提这件事：这两档给的是内置打法，不需要它去查
        assertFalse(
            WerewolfPrompt.systemPrompt(game, seat = 6, webSearch = true).contains("你可以联网检索")
        )
    }

    @Test
    fun `自由式仍然保留规则，没有被搜索式带跑`() {
        val game = standardGame()
        val free = WerewolfPrompt.systemPrompt(game, seat = 6, style = WerewolfPromptStyle.FREE)
        assertTrue("自由式给规则", free.contains("【遗言】"))
        assertTrue("自由式给术语", free.contains("【术语约定】"))
        assertFalse("但不给读人框架", free.contains("怎么从发言读人"))
    }

    // ------------------------------------------------------------------
    // P2：对照权威教学补齐的各身份判据
    // ------------------------------------------------------------------

    @Test
    fun `女巫补入报银水带队、毒对跳、首刀上警与捏毒威慑`() {
        // 权威女巫攻略：用过解药后阳光起跳报银水带队，是常规打法而非只有逃生门。
        val witch = WerewolfPrompt.systemPrompt(standardGame(), seat = 4)

        assertTrue("要有用解药后报银水带队：$witch", witch.contains("阳光起跳报银水"))
        assertTrue("要对跳女巫夜里毒：$witch", witch.contains("直接用毒药送走他"))
        assertTrue("要有捏毒威慑：$witch", witch.contains("捏着毒药本身就是威慑"))
        assertTrue("要有首夜被刀上警告知：$witch", witch.contains("首夜被刀又不能自救"))
    }

    @Test
    fun `猎人补入枪徽流威慑、黑枪与吃毒前跳明`() {
        // 权威猎人攻略：枪徽流施压、首刀无信息可黑枪、感觉要吃毒主动跳明。
        val hunter = WerewolfPrompt.systemPrompt(standardGame(), seat = 5)

        assertTrue("要能对跳猎人优先开枪：$hunter", hunter.contains("对跳猎人的"))
        assertTrue("要有预告枪口施压：$hunter", hunter.contains("预告你的枪口"))
        assertTrue("要有黑枪选项：$hunter", hunter.contains("不开枪（黑枪）"))
        assertTrue("要有吃毒前跳明：$hunter", hunter.contains("感觉自己要被女巫毒"))
    }

    @Test
    fun `平民补入不认推、找神禁忌与站边`() {
        // 权威平民守则：绝对不能认推、不要找神点神、划水=扛推位、尽早站边。
        val villager = WerewolfPrompt.systemPrompt(standardGame(), seat = 6)

        assertTrue("要有绝对不认推：$villager", villager.contains("绝对不要认推"))
        assertTrue("要有别找神点神：$villager", villager.contains("找神"))
        assertTrue("要有划水坐低身份：$villager", villager.contains("划水是最容易被抗推的理由"))
        assertTrue("要有尽早站边：$villager", villager.contains("尽早站边、统一票型"))
    }

    @Test
    fun `预言家补入警徽流防自爆与不验已定义牌`() {
        val seer = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)

        assertTrue("要有防自爆备选：$seer", seer.contains("防自爆的备选"))
        assertTrue("要别验已定义过的人：$seer", seer.contains("别把已经定义过的人再流进警徽流"))
    }

    @Test
    fun `第二轮贴吧论坛判据：轮次统领、验人改口、投票铁律`() {
        // 换来源（贴吧大局观帖 + 网杀经验站）后的高价值新点。
        val good = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)

        assertTrue("要有轮次比发言更重要的统领：$good", good.contains("轮次比发言更重要"))
        assertTrue("要说轮次是先手权：$good", good.contains("多握一轮就多一分主动权"))
        assertTrue("要有投票铁律宁放过容错牌：$good", good.contains("宁放过一个容错牌"))
        assertTrue("要说投错平民=狼多一轮：$good", good.contains("投错一个平民，狼直接多一轮"))
        assertTrue("要有验人信息改口判据：$good", good.contains("验人信息本身前后改口"))
        assertTrue("要说真预言家不会记错验人：$good", good.contains("不可能记错自己验了谁"))
        assertTrue("要说验人力度不是站边理由：$good", good.contains("验人力度"))
        assertTrue("要说神牌技能为追轮次服务：$good", good.contains("神牌的技能只为追轮次服务"))
        assertTrue("要说过错好人送轮次：$good", good.contains("毒错一个好人等于白送狼一轮"))
    }

    @Test
    fun `第二轮判据：警徽流不判死刑与外置位判断`() {
        val good = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)

        assertTrue("要说警徽流不能判死刑：$good", good.contains("警徽流不能判死刑"))
        assertTrue("要说好流不等于真：$good", good.contains("好流不等于真"))
        assertTrue("要说爆水反而像真预：$good", good.contains("爆水"))
        assertTrue("要说心路出问题可判假：$good", good.contains("心路历程出问题却可以直接判假"))
        assertTrue("要用自己视角核外置位：$good", good.contains("用你自己的视角核外置位"))
        assertTrue("要说关键轮次弃票是狼面：$good", good.contains("关键轮次弃票"))
    }

    @Test
    fun `第二轮判据：狼人悍跳别过度编心路`() {
        // 网杀经验：悍跳按真预言家模板说即可，特意编一大段花哨心路反而越编越漏视角。
        val wolf = WerewolfPrompt.systemPrompt(standardGame(), seat = 1)

        assertTrue("要说别编花哨心路：$wolf", wolf.contains("别特意编一大段花哨心路"))
        assertTrue("要说越编越漏视角：$wolf", wolf.contains("编得越多越容易漏视角"))
    }

    @Test
    fun `第二轮判据：平民诈身份只报后置位查杀`() {
        // 百度知道/网杀经验：好人诈身份只给后置位上警的人发查杀，别报金水/警下查杀。
        val villager = WerewolfPrompt.systemPrompt(standardGame(), seat = 6)

        assertTrue("要说只给后置位上警发查杀：$villager", villager.contains("只给后置位上警的人发查杀"))
        assertTrue("要说别报金水：$villager", villager.contains("别报金水"))
    }

    @Test
    fun `读人框架补入团队票型维度`() {
        // 权威「找真预言家三步法」的第二步：观团队与票型，我们之前只看发言。
        val good = WerewolfPrompt.systemPrompt(standardGame(), seat = 3)

        assertTrue("要看谁站在背后：$good", good.contains("看谁站在他背后"))
        assertTrue("要警惕过度积极站边：$good", good.contains("警惕过度积极站边"))
        assertTrue("要说三步法：$good", good.contains("查验和警徽流合不合理"))
    }
}
