package com.feature.werewolf.prompt

import com.feature.werewolf.decision.WerewolfDecisions
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.NightRecord
import com.feature.werewolf.game.PendingDawn
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.SeerCheck
import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WerewolfSpeechLength
import com.feature.werewolf.game.WerewolfTurnLimit
import com.feature.werewolf.game.WinRule
import com.feature.werewolf.game.WitchStock
import com.feature.werewolf.game.witchActSettled
import com.feature.werewolf.prompt.boards.BoardText
import com.feature.werewolf.prompt.boards.Boards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 狼人杀 v2 提示词系统完整测试。
 *
 * 重点守护四大维度：
 * 1. **信息隔离底线**：私有身份、队友、查验、刀口、用药、守人绝不泄漏给不该看的人；
 * 2. **硬规则完备且零重复**：暗牌不翻牌、猎人开枪≠验狼、守救同归、警长/退水/自爆规则只写一遍；
 * 3. **反踩坑回归（对应真实 12 人局事故）**：预言家/双药空女巫不被禁言、女巫禁穿真预抢警徽、
 *    非预好人不再被强逼「争到底」、警长票与白天放逐分开、守卫具备逐夜守护履历与双死反推意识、
 *    狼人白天聚焦执行头狼分工不堆砌 11 套战术百科、屠边账在暗牌局绝不偷看死人身份；
 * 4. **按阶段与按风格裁剪**：夜间零白天噪音、警上只给警上准则、VOTE 给核对事实、GUIDED/FREE/SEARCH 清晰分界。
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

    /** 带守卫+警长的 8 人局：1、2 狼；3 预言家；4 女巫；5 守卫；6 猎人；7、8 平民。 */
    private fun gameWithGuard(): WerewolfGame = WerewolfGame.withRoles(
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
    )

    private fun deal(config: WerewolfConfig): WerewolfGame {
        val roles = config.composition.flatMap { (role, count) -> List(count) { role } }
        return WerewolfGame.withRoles(config, roster(config.playerCount), roles)
    }

    // ------------------------------------------------------------------
    // 1. 信息隔离底线
    // ------------------------------------------------------------------

    @Test
    fun `狼队友与战术安排只给狼，绝不漏给好人`() {
        val game = standardGame().copy(
            phase = Phase.DAY_DISCUSSION,
            wolfPlan = "今晚刀 5 号；白天 1 号悍跳预言家，2 号冲锋。",
        )
        val wolf = WerewolfPrompt.systemPrompt(game, seat = 1)
        assertTrue(wolf.contains("你的狼队友"))
        assertTrue(wolf.contains("2 号"))
        assertTrue(wolf.contains("昨晚头狼定的战术"))
        assertTrue(wolf.contains("1 号悍跳预言家"))

        (3..8).forEach { seat ->
            val good = WerewolfPrompt.systemPrompt(game, seat)
            assertFalse("$seat 号不该看到狼队友", good.contains("狼队友"))
            assertFalse("$seat 号不该看到狼队战术", good.contains("昨晚头狼定的战术"))
            assertFalse("$seat 号不该看到混淆视听许可", good.contains("混淆视听"))
        }
    }

    @Test
    fun `查验记录只给预言家且预言家不被禁言锁死`() {
        val game = standardGame().copy(
            phase = Phase.DAY_DISCUSSION,
            seerChecks = listOf(
                SeerCheck(day = 1, targetSeat = 2, isWerewolf = true),
                SeerCheck(day = 2, targetSeat = 6, isWerewolf = false),
            ),
        )
        val seer = WerewolfPrompt.systemPrompt(game, seat = 3)
        assertTrue(seer.contains("查验 2 号 → 狼人"))
        assertTrue(seer.contains("查验 6 号 → 好人"))
        assertTrue(seer.contains("还没查验过的存活玩家"))
        assertFalse("预言家绝不该看到「以上信息不能在白天直接说出来」", seer.contains("以上信息不能在白天直接说出来"))

        listOf(1, 4, 5, 6, 7).forEach { seat ->
            val other = WerewolfPrompt.systemPrompt(game, seat)
            assertFalse("$seat 号不该看到查验记录", other.contains("查验 2 号"))
        }
    }

    @Test
    fun `女巫药品、刀口与用药履历只给女巫，解药空后夜间零刀口信息`() {
        val night1 = standardGame().copy(phase = Phase.NIGHT_WITCH, tonightVictim = 7)
        val witch1 = WerewolfPrompt.systemPrompt(night1, seat = 4)
        assertTrue(witch1.contains("今晚被狼人袭击的是 7 号"))
        assertTrue(witch1.contains("解药：还在"))
        assertTrue(witch1.contains("毒药无视守护"))

        listOf(1, 2, 3, 5, 6).forEach { seat ->
            val other = WerewolfPrompt.systemPrompt(night1, seat)
            assertFalse(other.contains("今晚被狼人袭击的是"))
            assertFalse(other.contains("解药：还在"))
        }

        // 解药用尽：夜里连自己被刀都不报
        val night2 = standardGame().copy(
            phase = Phase.NIGHT_WITCH,
            day = 2,
            tonightVictim = 4,
            witch = WitchStock(antidoteAvailable = false),
        )
        val witch2 = WerewolfPrompt.systemPrompt(night2, seat = 4)
        assertFalse("解药用尽不报被刀", witch2.contains("被狼人袭击"))
        assertFalse("解药用尽不报必死", witch2.contains("你今晚必死"))
        assertTrue("只剩毒药抉择", witch2.contains("毒药的抉择"))

        // 第一夜被刀可自救不报必死
        val selfNight1 = standardGame().copy(phase = Phase.NIGHT_WITCH, day = 1, tonightVictim = 4)
        val witchSelf1 = WerewolfPrompt.systemPrompt(selfNight1, seat = 4)
        assertTrue(witchSelf1.contains("第一夜你还能自救"))
        assertFalse(witchSelf1.contains("你已经救不了自己"))

        // 不能自救配置写死
        val strict = standardGame().let { it.copy(config = it.config.copy(witchCanSelfHealOnFirstNight = false)) }
        assertTrue(WerewolfPrompt.systemPrompt(strict, seat = 4).contains("任何时候都不能自救"))
    }

    @Test
    fun `女巫看得到用药履历和守救同归说明，双药空后白天不被禁言`() {
        val game = standardGame().copy(
            day = 2,
            phase = Phase.DAY_DISCUSSION,
            witch = WitchStock(antidoteAvailable = false, poisonAvailable = false),
            nights = listOf(
                NightRecord(day = 1, wolfTarget = 6, guarded = 6, healed = true, mutualProtection = true, deaths = listOf(6)),
                NightRecord(day = 2, wolfTarget = 8, poisonTarget = 2, deaths = listOf(8, 2)),
            ),
        )
        val witch = WerewolfPrompt.systemPrompt(game, seat = 4)
        assertTrue(witch.contains("第 1 夜用解药救了 6 号"))
        assertTrue(witch.contains("守救同归"))
        assertTrue(witch.contains("第 2 夜用毒药毒了 2 号"))
        assertTrue("双药已空应提示直接亮明女巫身份", witch.contains("双药已空"))
        assertTrue("任务段明确允许亮明女巫身份", witch.contains("直接亮明女巫身份"))
        assertFalse("双药已空女巫绝不能被套「以上信息不能在白天直接说出来」", witch.contains("以上信息不能在白天直接说出来"))
        assertFalse("双药已空女巫绝不能看到「不要报身份」", witch.contains("不要报身份"))

        // 别人看不到用药记录
        assertFalse(WerewolfPrompt.systemPrompt(game, seat = 6).contains("用药记录"))
    }

    @Test
    fun `守卫白天读昨夜守人、拥有逐夜守护记录，且绝不漏给狼`() {
        val game = gameWithGuard().copy(
            day = 3,
            phase = Phase.DAY_DISCUSSION,
            lastGuardedSeat = 7,
            tonightGuardedSeat = 4,
            nights = listOf(
                NightRecord(day = 1, guarded = null),
                NightRecord(day = 2, guarded = 7),
                NightRecord(day = 3, guarded = 4),
            ),
        )
        val guardDay = WerewolfPrompt.systemPrompt(game, seat = 5)
        assertTrue("白天读昨夜守 4 号", guardDay.contains("你昨夜守的是 4 号"))
        assertFalse("白天不把前夜 7 号当昨夜", guardDay.contains("你昨夜守的是 7 号"))
        assertTrue("守卫要有完整逐夜守护记录", guardDay.contains("【你的守护记录】第 1 夜空守；第 2 夜守了 7 号；第 3 夜守了 4 号"))

        val guardNight = WerewolfPrompt.privateBriefing(game.copy(phase = Phase.NIGHT_GUARD), seat = 5)
        assertTrue("夜间读 lastGuardedSeat 7 号", guardNight.contains("你昨夜守的是 7 号"))

        val wolf = WerewolfPrompt.systemPrompt(game, seat = 1)
        assertFalse("守卫私有守护记录绝不漏给狼", wolf.contains("【你的守护记录】"))
        assertFalse("守卫具体守人历史绝不漏给狼", wolf.contains("第 2 夜守了 7 号"))
        assertFalse("昨夜守了谁绝不漏给狼", wolf.contains("守的是 4 号"))
    }

    @Test
    fun `第一天死讯压在警长竞选之后，未公布前禁止谈昨夜`() {
        val game = gameWithGuard().guardProtect(3).beginWolfVote()
        val dawn = game.wolfVote(mapOf(1 to 4, 2 to 4)).seerCheck(6).witchActSettled(heal = false)

        assertEquals(Phase.DAY_SHERIFF_SIGNUP, dawn.phase)
        assertNull(dawn.history.firstOrNull { it is GameEvent.Dawn })
        assertEquals(listOf(4), dawn.pendingDawn?.deaths)

        val prompt = WerewolfPrompt.systemPrompt(dawn, seat = 3)
        assertFalse(prompt.contains("已出局"))
        assertTrue(prompt.contains("共 8 人"))
        assertTrue(prompt.contains("昨夜还没公布"))
        assertTrue(prompt.contains("不要谈昨夜"))
        // 预言家本人（3 号）的「还没查验过的存活玩家」也不得提前把尚未公布死讯的 4 号剔除
        assertTrue("预言家未验名单不应提前剔除 pendingDawn 死者 4 号：$prompt", prompt.contains("4 号"))

        val announced = dawn.copy(pendingDawn = null)
        assertFalse(WerewolfPrompt.systemPrompt(announced, seat = 3).contains("昨夜还没公布"))
    }

    // ------------------------------------------------------------------
    // 2. 硬规则完备且零重复
    // ------------------------------------------------------------------

    @Test
    fun `硬规则写清暗牌不翻牌、猎人开枪不等于验狼、守救同归、警长规则不重复`() {
        val guardPrompt = WerewolfPrompt.systemPrompt(gameWithGuard(), seat = 3)
        assertTrue(guardPrompt.contains("暗牌局"))
        assertTrue("必须明写猎人开枪不等于坐实对方是狼", guardPrompt.contains("猎人开枪带走某人只代表猎人的怀疑"))
        assertTrue(guardPrompt.contains("守救同归"))
        assertTrue(guardPrompt.contains("上警只是举手报名"))
        assertTrue(guardPrompt.contains("双爆"))
        assertTrue(guardPrompt.contains("不能投自己"))
        assertTrue(guardPrompt.contains("PK 发言后不再退水"))
        assertTrue(guardPrompt.contains("先遗言、再开枪"))

        // 狼王局明确自刀/被刀与自爆均不能开枪；熊局明确座位首尾成环
        val kingRoles = listOf(
            WerewolfRole.WEREWOLF_KING, WerewolfRole.WEREWOLF, WerewolfRole.SEER,
            WerewolfRole.WITCH, WerewolfRole.HUNTER, WerewolfRole.BEAR,
            WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
        )
        val kingBearGame = WerewolfGame.withRoles(
            WerewolfConfig(composition = kingRoles.groupingBy { it }.eachCount(), hasSheriff = true),
            (1..8).map { RosterEntry("p$it", "${it}号") },
            kingRoles,
        )
        val kingPrompt = WerewolfPrompt.systemPrompt(kingBearGame, seat = 1)
        assertTrue(kingPrompt.contains("自刀/被刀"))
        assertTrue(kingPrompt.contains("自爆"))
        val bearPrompt = WerewolfPrompt.systemPrompt(kingBearGame, seat = 6)
        assertTrue(bearPrompt.contains("1 号与最大号互为邻座"))

        // 被放逐猎人进遗言阶段时，任务提示需明确「说完遗言再开枪」
        val exiledHunter = kingBearGame.copy(
            phase = Phase.DAY_LAST_WORDS,
            pendingLastWords = listOf(5),
            pendingHunterSeat = 5,
            afterLastWordsPhase = Phase.HUNTER_SHOOT,
        )
        assertTrue(WerewolfPrompt.task(exiledHunter, seat = 5).contains("说完遗言后将进入你的开枪环节"))

        // 被枪杀警长交警徽时，提示词与导演实现一致：reason 仅作内心记录、不公开显示
        val shotSheriffGame = kingBearGame.copy(
            phase = Phase.DAY_LAST_WORDS,
            sheriffSeat = 3,
            players = kingBearGame.players.map {
                if (it.seat == 3) it.copy(alive = false, deathCause = com.feature.werewolf.game.DeathCause.HUNTER_SHOOT) else it
            },
        )
        val badgeSpec = com.feature.werewolf.decision.WerewolfDecisions.badgeTransfer(shotSheriffGame, seat = 3)
        assertTrue(badgeSpec.instruction.contains("不会公开显示或朗读"))
        assertFalse(badgeSpec.instruction.contains("作为你的最后一句话显示"))

        // boardBrief 不再重复一遍警长流程
        val board = WerewolfPrompt.boardBrief(gameWithGuard().config)
        assertFalse("boardBrief 不应再重复写警长流程", board.contains("本局有警长"))
        assertTrue("但特殊身份规则保留在 boardBrief", board.contains("守卫每夜先于狼人守一个人"))

        // 无白痴不提白痴翻牌，有白痴则写明唯一例外
        assertTrue(guardPrompt.contains("不存在任何翻牌环节"))
        val foolPrompt = WerewolfPrompt.systemPrompt(deal(WerewolfBoards.configOf(WerewolfBoards.FOOL_12)), seat = 3)
        assertTrue(foolPrompt.contains("唯一例外是白痴"))
        assertFalse(foolPrompt.contains("不存在任何翻牌环节"))
    }

    @Test
    fun `屠边局列出本局神职名单，屠城局不写屠边`() {
        val guardFree = WerewolfPrompt.systemPrompt(gameWithGuard(), seat = 3, style = WerewolfPromptStyle.FREE)
        assertTrue(guardFree.contains("本局算神的是："))
        assertTrue(guardFree.contains("守卫"))
        assertTrue(guardFree.contains("狼王）永远不算神"))

        val city = standardGame().copy(config = WerewolfConfig(winRule = WinRule.SLAY_ALL))
        val cityPrompt = WerewolfPrompt.systemPrompt(city, seat = 1)
        assertTrue(cityPrompt.contains("屠城局"))
        assertFalse(cityPrompt.contains("屠边账"))
    }

    @Test
    fun `播报不泄露身份、不点名熊座位，长历史旧天折叠最近两天逐条`() {
        val dawn = WerewolfPrompt.describe(GameEvent.Dawn(1, listOf(5)))
        assertTrue(dawn.contains("5 号"))
        assertFalse(dawn.contains("猎人"))

        val roar = WerewolfPrompt.describe(GameEvent.BearRoar(1, 11))
        assertTrue(roar.contains("熊在咆哮"))
        assertFalse(roar.contains("11 号"))

        val history = listOf(
            GameEvent.Dawn(1, listOf(8)),
            GameEvent.Exiled(1, 4, 5.0),
            GameEvent.Dawn(2, emptyList()),
            GameEvent.NoExile(2),
            GameEvent.Dawn(3, listOf(6)),
            GameEvent.Exiled(3, 2, 4.0),
            GameEvent.Dawn(4, emptyList()),
        )
        val sit = WerewolfPrompt.publicSituation(standardGame().copy(day = 4, history = history))
        assertTrue(sit.contains("第1天：夜里 8 号出局；4 号被放逐（5 票）"))
        assertTrue(sit.contains("第2天：夜里平安；无人被放逐"))
        assertFalse(sit.contains("· 第1天 天亮"))
        assertTrue(sit.contains("· 第3天 天亮：6 号出局"))
    }

    // ------------------------------------------------------------------
    // 3. v2 反踩坑回归（直击 12 人局全部痛点）
    // ------------------------------------------------------------------

    @Test
    fun `女巫严禁冒充真预抢警徽，且非预好人不再被强逼争到底`() {
        val sheriffGame = gameWithGuard().copy(
            phase = Phase.DAY_SHERIFF_SPEECH,
            sheriffCandidates = listOf(1, 3, 4, 7),
            sheriffRunners = listOf(1, 3, 4, 7),
        )
        val witchSpeech = WerewolfPrompt.systemPrompt(sheriffGame, seat = 4)
        assertTrue("女巫明确禁止冒充真预抢警徽", witchSpeech.contains("严禁冒充真预言家抢警徽"))
        assertFalse("不再强逼非预好人「既然报了名就要争到底」", witchSpeech.contains("既然报了名就要争到底"))
        assertTrue("非预好人被明确提醒听完对跳应主动退水", witchSpeech.contains("主动退水"))

        val villagerWithdraw = WerewolfPrompt.task(sheriffGame.copy(phase = Phase.DAY_SHERIFF_WITHDRAW), seat = 7)
        assertTrue("平民退水阶段应被明确建议退水", villagerWithdraw.contains("应当选择退水"))

        val seerWithdraw = WerewolfPrompt.task(sheriffGame.copy(phase = Phase.DAY_SHERIFF_WITHDRAW), seat = 3)
        assertTrue("真预言家退水阶段应坚定争徽", seerWithdraw.contains("不要退水"))
    }

    @Test
    fun `狼人上警与竞选严格服从头狼分工，白天不再倾倒 11 套战术百科`() {
        val wolfSignup = WerewolfPrompt.systemPrompt(
            gameWithGuard().copy(phase = Phase.DAY_SHERIFF_SIGNUP, wolfPlan = "6 号悍跳，1 号冲锋。"),
            seat = 1,
        )
        assertTrue("非悍跳狼默认不上警留警下投票", wolfSignup.contains("默认不上警"))
        assertTrue("狼人竞选要对照昨晚头狼分工", wolfSignup.contains("对照昨晚头狼分工"))
        assertFalse("白天不该倾倒 11 套狼队打法菜单", wolfSignup.contains("【狼队打法菜单】"))
        assertFalse("白天不该倾倒夜间守卫博弈", wolfSignup.contains("【和守卫博弈】"))

        // 夜间商议（NIGHT_WOLF_CHAT）给战术菜单；定刀阶段（NIGHT_WOLF）不再重复塞 11 套战术省 token
        val wolfChat = WerewolfPrompt.systemPrompt(
            gameWithGuard().copy(phase = Phase.NIGHT_WOLF_CHAT, nightHead = 1),
            seat = 1,
        )
        assertTrue("夜聊要给狼队打法菜单", wolfChat.contains("【狼队打法菜单】"))
        assertTrue("夜聊要给守卫博弈", wolfChat.contains("【和守卫博弈】"))
        assertTrue("夜聊要给公平屠边账", wolfChat.contains("【屠边账"))
        assertFalse("屠边账不该偷看暗牌替狼算精确剩余刀数", wolfChat.contains("再打 2 刀就能打穿"))

        val wolfKnife = WerewolfPrompt.systemPrompt(
            gameWithGuard().copy(phase = Phase.NIGHT_WOLF),
            seat = 1,
        )
        assertFalse("定刀阶段不需要 11 套战术菜单", wolfKnife.contains("【狼队打法菜单】"))
        assertTrue("定刀阶段仍保留守卫博弈与公平屠边账", wolfKnife.contains("【和守卫博弈】") && wolfKnife.contains("【屠边账"))

        // 双向警徽流 + 盘狼坑 + 警上发金水退水警报 + 4 狼经典配比
        val seerDay = WerewolfPrompt.systemPrompt(gameWithGuard().copy(phase = Phase.DAY_DISCUSSION), seat = 3)
        assertTrue("预言家教双向警徽流", seerDay.contains("双向警徽流"))
        assertTrue("白天读人教盘齐全部狼坑", seerDay.contains("盘齐全部狼坑"))
        assertTrue("移交警徽决策提醒预言家按双向警徽流传递验人", WerewolfDecisions.badgeTransfer(gameWithGuard(), seat = 3).instruction.contains("双向警徽流"))

        val guard12Chat = WerewolfPrompt.systemPrompt(
            deal(WerewolfBoards.configOf(WerewolfBoards.GUARD_12)).copy(phase = Phase.NIGHT_WOLF_CHAT, nightHead = 1),
            seat = 1,
        )
        assertTrue("多人狼队头狼有经典配比建议", guard12Chat.contains("1 悍跳 + 1 冲锋 + 1-2 倒钩/深水"))
    }

    @Test
    fun `警长票与白天放逐分开，双死夜提醒盘一刀一毒两种可能`() {
        val sheriffVote = WerewolfPrompt.systemPrompt(
            gameWithGuard().copy(
                phase = Phase.DAY_SHERIFF_VOTE,
                sheriffCandidates = listOf(2, 3),
                sheriffRunners = listOf(2, 3),
            ),
            seat = 7,
        )
        assertTrue("警长投票页要明确警长票不等于必投查杀", sheriffVote.contains("不等于警长票必须投给报查杀的人"))
        assertTrue("警上流程只注警上准则，不灌白天长文", sheriffVote.contains("【警上推理准则】"))
        assertFalse("警上不灌白天推理准则", sheriffVote.contains("【白天推理准则】"))

        val dayTalk = WerewolfPrompt.systemPrompt(
            gameWithGuard().copy(phase = Phase.DAY_DISCUSSION),
            seat = 5, // 守卫
        )
        assertTrue("白天推理准则写明双死夜必是一刀一毒", dayTalk.contains("双死夜必是一刀一毒"))
        assertTrue("提醒守卫接警徽不等于原警长必是真预", dayTalk.contains("接了警徽绝不等于他必是真预言家"))
    }

    @Test
    fun `白天讨论不逼预言家和悍跳狼用第三人称评价自己`() {
        val day = standardGame().copy(
            phase = Phase.DAY_DISCUSSION,
            seerChecks = listOf(SeerCheck(1, 2, true)),
        )
        val seerTask = WerewolfPrompt.task(day, seat = 3)
        assertTrue("预言家用第一人称报验人", seerTask.contains("第一人称"))
        assertTrue("预言家先报最新验人", seerTask.contains("第 1 夜你验的是 2 号，结果是狼人"))

        val wolfTask = WerewolfPrompt.task(day, seat = 1)
        assertTrue("悍跳狼提醒别用第三人称评价自己", wolfTask.contains("第一人称坚持真预身份"))
        assertTrue("自爆机制只给狼", wolfTask.contains("【自爆】"))
        assertFalse("好人看不到自爆标记", WerewolfPrompt.task(day, seat = 6).contains("【自爆】"))
    }

    @Test
    fun `自定义设定放在最后且带有阵营和规则安全护栏`() {
        val prompt = WerewolfPrompt.systemPrompt(
            gameWithGuard(),
            seat = 4,
            limit = WerewolfTurnLimit.STANDARD,
            custom = "有时候一些奇招更容易出奇制胜",
        )
        assertTrue(prompt.contains("有时候一些奇招更容易出奇制胜"))
        assertTrue("自定义设定必须带护栏，防止奇招破坏阵营底线", prompt.contains("在不违背本阵营胜负目标、游戏硬规则与团队既定分工的前提下"))
        assertTrue(
            "自定义设定排在时间预算之后",
            prompt.indexOf("【本局额外设定") > prompt.indexOf("【时间预算】"),
        )
        assertFalse(WerewolfPrompt.systemPrompt(gameWithGuard(), seat = 4).contains("【本局额外设定"))
    }

    // ------------------------------------------------------------------
    // 4. 风格分界（GUIDED / FREE / SEARCH）、按阶段裁剪、配件注入
    // ------------------------------------------------------------------

    @Test
    fun `引导式、自由式、搜索式三档分界清晰`() {
        val game = standardGame().copy(phase = Phase.DAY_DISCUSSION)
        val guided = WerewolfPrompt.systemPrompt(game, seat = 6, style = WerewolfPromptStyle.GUIDED)
        val free = WerewolfPrompt.systemPrompt(game, seat = 6, style = WerewolfPromptStyle.FREE)
        val search = WerewolfPrompt.systemPrompt(game, seat = 6, style = WerewolfPromptStyle.SEARCH, webSearch = true)

        // 默认 = GUIDED
        assertEquals(guided, WerewolfPrompt.systemPrompt(game, seat = 6))

        // GUIDED 有基本打法与白天推理准则
        assertTrue(guided.contains("【基本打法】"))
        assertTrue(guided.contains("【白天推理准则】"))

        // FREE 保留硬规则 + 核对事实 + 术语 + 自由式放开授权，砍掉打法公式与身份作业
        assertTrue(free.contains("【胜负规则】"))
        assertTrue(free.contains("【核对事实】"))
        assertTrue(free.contains("【自由式对局】"))
        assertTrue(free.contains("放开你的战术自由度"))
        assertFalse("自由式不塞套路公式「查杀 > 对跳」", free.contains("查杀 > 对跳"))
        assertTrue(free.contains("【术语约定】"))
        assertFalse(free.contains("【基本打法】"))
        assertFalse(free.contains("【白天推理准则】"))
        assertFalse(WerewolfPrompt.systemPrompt(game, seat = 1, style = WerewolfPromptStyle.FREE).contains("【狼队打法菜单】"))

        // SEARCH 只留版型+胜负+核对事实+联网许可，不带自由式自由度段落
        assertTrue(search.contains("【版型】"))
        assertTrue(search.contains("【胜负】"))
        assertTrue(search.contains("【核对事实】"))
        assertFalse(search.contains("【自由式对局】"))
        assertFalse(search.contains("查杀 > 对跳"))
        assertTrue(search.contains("你可以联网检索"))
        assertFalse(search.contains("【遗言】"))
        assertFalse(search.contains("【术语约定】"))
        assertFalse(
            WerewolfPrompt.systemPrompt(game, seat = 6, style = WerewolfPromptStyle.SEARCH, webSearch = false)
                .contains("你可以联网检索")
        )
    }

    @Test
    fun `VOTE 范围用精简核对事实替代长篇准则，夜间不注读人准则`() {
        val day = standardGame().copy(phase = Phase.DAY_DISCUSSION)
        val all = WerewolfPrompt.systemPrompt(day, seat = 6)
        val vote = WerewolfPrompt.systemPrompt(day, seat = 6, readingScope = ReadingTells.Scope.VOTE)
        val night = WerewolfPrompt.systemPrompt(standardGame(), seat = 6, includeReading = false)

        assertTrue(all.contains("【白天推理准则】"))
        assertTrue(vote.contains("【核对事实】"))
        assertTrue("引导式 VOTE 保留出人优先级提醒", vote.contains("查杀 > 对跳"))
        assertFalse(vote.contains("【白天推理准则】"))
        assertFalse(night.contains("【白天推理准则】"))
    }

    @Test
    fun `狼队开局风向卡仅在第 1 夜引导式商议阶段注入给狼，绝不漏给好人或自由式或次夜`() {
        val seed = "【双狼悍跳搅局流】安排一狼正跳预言家、一狼警上反水或补跳试探。"
        val night1Chat = gameWithGuard().copy(
            day = 1,
            phase = Phase.NIGHT_WOLF_CHAT,
            nightHead = 1,
            wolfStyleSeed = seed,
        )
        val wolfPrompt = WerewolfPrompt.systemPrompt(night1Chat, seat = 1, style = WerewolfPromptStyle.GUIDED)
        assertTrue("第 1 夜引导式狼聊看到风向卡", wolfPrompt.contains("【本局狼队风向启发"))
        assertTrue("风向卡原文注入", wolfPrompt.contains(seed))

        // 好人、自由式、定刀阶段、第 2 夜都不注入
        assertFalse("好人绝看不到风向卡", WerewolfPrompt.systemPrompt(night1Chat, seat = 3).contains(seed))
        assertFalse("自由式不灌风向卡", WerewolfPrompt.systemPrompt(night1Chat, seat = 1, style = WerewolfPromptStyle.FREE).contains(seed))
        assertFalse("定刀阶段不灌风向卡", WerewolfPrompt.systemPrompt(night1Chat.copy(phase = Phase.NIGHT_WOLF), seat = 1).contains(seed))
        assertFalse("第 2 夜不再灌首夜风向卡", WerewolfPrompt.systemPrompt(night1Chat.copy(day = 2), seat = 1).contains(seed))
    }

    @Test
    fun `残局挡刀只给残局好人，不给狼且不给人数充足局`() {
        val dead = setOf(4, 6, 7)
        val end = standardGame().let { g ->
            g.copy(
                day = 3,
                phase = Phase.DAY_DISCUSSION,
                players = g.players.map { if (it.seat in dead) it.copy(alive = false) else it },
                history = listOf(GameEvent.Dawn(1, dead.toList())),
            )
        }
        val villager = WerewolfPrompt.systemPrompt(end, seat = 8)
        val seer = WerewolfPrompt.systemPrompt(end, seat = 3)
        val wolf = WerewolfPrompt.systemPrompt(end, seat = 1)

        assertTrue(villager.contains("穿一件神的衣服"))
        assertTrue(villager.contains("绝不要和还活着的真预言家"))
        assertTrue(seer.contains("藏着已经不如站出来值钱"))
        assertFalse(wolf.contains("穿一件神的衣服"))
        assertFalse(WerewolfPrompt.systemPrompt(standardGame(), seat = 8).contains("穿一件神的衣服"))
    }

    @Test
    fun `时间预算、发言长度、风格画像与公开台账按约定位置注入`() {
        val game = standardGame().copy(phase = Phase.DAY_DISCUSSION)
        val ledger = "【公开台账】\n· 声称身份：3 号自称预言家（第 1 天）\n"
        val prompt = WerewolfPrompt.systemPrompt(
            game = game,
            seat = 6,
            persona = "你说话很冲。",
            playStyle = PlayerStyle.AGGRESSIVE,
            ledger = ledger,
            limit = WerewolfTurnLimit.FAST,
            speechLength = WerewolfSpeechLength.SHORT,
        )
        assertTrue(prompt.startsWith("你说话很冲。"))
        assertTrue(prompt.indexOf("【你在牌桌上的性格】") in 1 until prompt.indexOf("你正在玩一局狼人杀"))
        assertTrue(prompt.indexOf("【公开台账】") in prompt.indexOf("【已经发生的事】") until prompt.indexOf("【只有你知道的事】"))
        assertTrue(prompt.contains("思考约 30 秒"))
        assertTrue(prompt.contains("80 字以内"))
        assertTrue(prompt.contains("先把结论说出来"))
    }

    @Test
    fun `插爆简报写明被查杀不是自爆理由，每张板子都有匹配打法`() {
        val sheriffGame = standardGame().let { base ->
            base.copy(
                config = base.config.copy(hasSheriff = true),
                phase = Phase.DAY_DISCUSSION,
                sheriffSeat = 2,
            )
        }
        val brief = WerewolfPrompt.explodeWatchBrief(
            sheriffGame,
            seat = 1,
            speaker = 3,
            speech = "3 号查杀 1 号。",
            roundContext = "已起跳：2号(队友)跳预言家、3号跳预言家",
        )
        assertTrue(brief.contains("被查杀、被踩、被点名表水都不是理由"))
        assertTrue(brief.contains("替预言家作证"))
        assertTrue("应标注警长是否队友", brief.contains("当前警长：2 号（我方队友"))
        assertTrue("应明确非队友边界", brief.contains("其余所有座位都是你的对手"))
        assertTrue("应注入本轮上下文", brief.contains("已起跳：2号(队友)跳预言家"))

        WerewolfBoards.all.forEach { board ->
            val text = Boards.text(board.config)
            assertTrue(board.key, text.common.isNotEmpty())
            assertTrue(board.key, text.wolves.isNotEmpty())
        }
        val custom = WerewolfConfig(
            composition = mapOf(WerewolfRole.WEREWOLF to 2, WerewolfRole.SEER to 1, WerewolfRole.VILLAGER to 3)
        )
        assertEquals(BoardText(), Boards.text(custom))
    }

    @Test
    fun `局势时间线与第二夜起夜死警长无遗言规则一致`() {
        val day2Situation = WerewolfPrompt.publicSituation(standardGame().copy(day = 2, phase = Phase.DAY_DISCUSSION))
        assertTrue(day2Situation.contains("刚结束第 2 夜，现在是第 2 天白天，今晚将进入第 3 夜"))

        val night2Situation = WerewolfPrompt.publicSituation(standardGame().copy(day = 2, phase = Phase.NIGHT_WOLF))
        assertTrue(night2Situation.contains("现在是第 2 夜，第 1 天白天已结束"))

        val poisonedSheriffDay2 = gameWithGuard().copy(
            day = 2,
            phase = Phase.DAY_LAST_WORDS,
            sheriffSeat = 3,
            pendingLastWords = listOf(3),
            players = gameWithGuard().players.map {
                if (it.seat == 3) it.copy(alive = false, deathCause = com.feature.werewolf.game.DeathCause.WITCH_POISON, deathDay = 2) else it
            },
        )
        assertTrue(poisonedSheriffDay2.isSilentBadgeTransfer(3))
        val day2BadgeSpec = com.feature.werewolf.decision.WerewolfDecisions.badgeTransfer(poisonedSheriffDay2, seat = 3)
        assertTrue("第二夜起夜死警长无遗言", day2BadgeSpec.instruction.contains("没有遗言"))
        assertTrue(day2BadgeSpec.instruction.contains("不会公开显示或朗读"))

        val day1Sheriff = poisonedSheriffDay2.copy(
            day = 1,
            players = gameWithGuard().players.map {
                if (it.seat == 3) it.copy(alive = false, deathCause = com.feature.werewolf.game.DeathCause.WOLF_KILL, deathDay = 1) else it
            },
        )
        assertFalse("首夜出局警长仍有遗言", day1Sheriff.isSilentBadgeTransfer(3))
        val day1BadgeSpec = com.feature.werewolf.decision.WerewolfDecisions.badgeTransfer(day1Sheriff, seat = 3)
        assertTrue(day1BadgeSpec.instruction.contains("会被当成你的遗言公开说出来"))
    }

    @Test
    fun `自由式决策指令与警长私有段不代打判断`() {
        val sheriffGame = gameWithGuard().copy(sheriffSeat = 6)
        val freeSheriff = WerewolfPrompt.privateBriefing(sheriffGame, seat = 6, style = WerewolfPromptStyle.FREE)
        val guidedSheriff = WerewolfPrompt.privateBriefing(sheriffGame, seat = 6)
        assertFalse(freeSheriff.contains("比撕掉更糟"))
        assertTrue(guidedSheriff.contains("比撕掉更糟"))
        assertFalse(WerewolfDecisions.badgeTransfer(sheriffGame, 6, free = true).instruction.contains("比撕掉更糟"))
        assertTrue(WerewolfDecisions.badgeTransfer(sheriffGame, 6).instruction.contains("比撕掉更糟"))
    }

    @Test
    fun `投票任务排除自己和翻牌白痴，有守卫与无守卫的狼刀博弈分别生效`() {
        val foolGame = deal(WerewolfBoards.configOf(WerewolfBoards.FOOL_12))
        val idiotSeat = foolGame.players.first { it.role == WerewolfRole.IDIOT }.seat
        val voterSeat = foolGame.players.first { it.role == WerewolfRole.VILLAGER }.seat
        val voteTask = WerewolfPrompt.task(foolGame.copy(phase = Phase.DAY_VOTE, idiotRevealed = true), seat = voterSeat)
        assertFalse(voteTask.contains("$idiotSeat 号"))
        assertFalse(voteTask.contains("$voterSeat 号"))

        val withGuardNight = gameWithGuard().copy(
            day = 2,
            phase = Phase.NIGHT_WOLF,
            nights = listOf(NightRecord(day = 1, wolfTarget = 6, deaths = emptyList())),
        )
        assertTrue(WerewolfPrompt.systemPrompt(withGuardNight, seat = 1).contains("今晚连刀 6 号"))

        val noGuardNight = standardGame().copy(
            day = 2,
            phase = Phase.NIGHT_WOLF,
            nights = listOf(NightRecord(day = 1, wolfTarget = 6, deaths = emptyList())),
        )
        val wolfNoGuard = WerewolfPrompt.systemPrompt(noGuardNight, seat = 1)
        assertTrue(wolfNoGuard.contains("本局没有守卫"))
        assertFalse(wolfNoGuard.contains("和守卫博弈"))
    }

    @Test
    fun `全局规则包含退水不能补发言、女巫中刀仍可用毒，守卫白天给抗推拍身份提示`() {
        val guardGame = gameWithGuard().copy(phase = Phase.DAY_DISCUSSION, day = 2)
        val guardSeat = guardGame.players.first { it.role == WerewolfRole.GUARD }.seat
        val wolfSeat = guardGame.players.first { it.role.isWerewolf }.seat

        val wolfPrompt = WerewolfPrompt.systemPrompt(guardGame, seat = wolfSeat)
        val freeWolfPrompt = WerewolfPrompt.systemPrompt(guardGame, seat = wolfSeat, style = WerewolfPromptStyle.FREE)
        val guardPrompt = WerewolfPrompt.systemPrompt(guardGame, seat = guardSeat)

        // 2: 退水不能补发言 / 前置位被质疑不能在退水回应（引导式 + 自由式全体共享规则）
        assertTrue(wolfPrompt.contains("退水阶段只能表态「退水/不退水」、不能补发言或反驳"))
        assertTrue(freeWolfPrompt.contains("退水阶段只能表态「退水/不退水」、不能补发言或反驳"))

        // 4: 女巫中刀当夜仍可用毒 + 警长 1.5 票口径（全体玩家遵守的硬规则）
        assertTrue(wolfPrompt.contains("女巫当夜被狼刀中，仍然可以在天亮出局前照常使用毒药"))
        assertTrue(freeWolfPrompt.contains("女巫当夜被狼刀中，仍然可以在天亮出局前照常使用毒药"))
        assertTrue(wolfPrompt.contains("3 狼含警长共 3.5 票"))

        // 3: 守卫白天面临抗推或残局必须拍身份报逐夜守护记录
        assertTrue(guardPrompt.contains("必须立刻拍明守卫身份并报出完整的逐夜守护记录"))
        assertTrue(guardPrompt.contains("必须果断拍明守卫身份并报出完整的逐夜守护记录"))
    }
}
