package com.feature.werewolf.route

import com.feature.werewolf.engine.SeatTiming
import com.feature.werewolf.engine.TurnKind
import com.feature.werewolf.engine.TurnTimeout
import com.feature.werewolf.engine.WerewolfLive
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfProgress
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WerewolfTurnLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 参与局（人类在桌上）的夜里可见性。
 *
 * 夜里「谁在动」本身就是身份。参与局里只要有一个座位的名字冒出来，
 * 那张牌就等于被翻开了——所以夜里除了本人，谁都不该在界面上留下痕迹。
 */
class WerewolfParticipateVisibilityTest {

    /** 1、2 号是狼，3、4、5 号是好人。 */
    private fun game(phase: Phase): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(
            composition = mapOf(
                WerewolfRole.WEREWOLF to 2,
                WerewolfRole.VILLAGER to 3,
            ),
        ),
        roster = (1..5).map { RosterEntry(id = "p$it", name = "玩家$it") },
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
        ),
    ).copy(phase = phase)

    private fun state(phase: Phase, live: WerewolfLive) = WerewolfUiState(
        table = WerewolfTable(game(phase)),
        live = live,
        running = true,
    )

    @Test
    fun `狼人下刀时 参与局看不到狼人的气泡`() {
        // 夜里下刀是并发阶段：「当前是谁」本来就不存在，
        // 但正在下刀的那几个人就是全部狼人——气泡一冒出来，狼就全亮了
        val live = state(
            Phase.NIGHT_WOLF,
            WerewolfLive(seat = 1, phase = Phase.NIGHT_WOLF, reasoning = "刀 3 号比较稳"),
        ).toLive(humanSeat = 2, turn = null, avatars = emptyMap())

        assertNull("参与局的夜里不该出现别人的气泡", live.streaming)
    }

    @Test
    fun `观战时夜里照样看得到气泡`() {
        val live = state(
            Phase.NIGHT_WOLF,
            WerewolfLive(seat = 1, phase = Phase.NIGHT_WOLF, reasoning = "刀 3 号比较稳"),
        ).toLive(humanSeat = null, turn = null, avatars = emptyMap())

        assertNotNull("观战是上帝视角，夜里也要看得见", live.streaming)
    }

    @Test
    fun `自己是狼时 狼人商议看得见`() {
        // 人类自己是 1 号狼：夜聊是他和 2 号狼的正事，必须看得见
        val live = state(
            Phase.NIGHT_WOLF_CHAT,
            WerewolfLive(seat = 2, phase = Phase.NIGHT_WOLF_CHAT, text = "刀 4 号吧"),
        ).toLive(humanSeat = 1, turn = null, avatars = emptyMap())

        assertNotNull("自己是狼时狼人商议要看得见", live.streaming)
    }

    @Test
    fun `自己是好人时 狼人商议看不见`() {
        val live = state(
            Phase.NIGHT_WOLF_CHAT,
            WerewolfLive(seat = 1, phase = Phase.NIGHT_WOLF_CHAT, text = "刀 4 号吧"),
        ).toLive(humanSeat = 3, turn = null, avatars = emptyMap())

        assertNull("好人看不见狼人密谈", live.streaming)
    }

    @Test
    fun `参与局里界面拿不到谁是狼`() {
        val live = state(
            Phase.DAY_DISCUSSION,
            WerewolfLive(seat = 1, phase = Phase.DAY_DISCUSSION, text = "我跳预言家"),
        ).toLive(humanSeat = 3, turn = null, avatars = emptyMap())

        assertTrue(
            "参与中的局里不该把真实阵营发到界面：${live.seats.map { it.seat to it.wolf }}",
            live.seats.none { it.wolf },
        )
        assertTrue("身份名同样不该给", live.seats.all { it.roleLabel == null })
    }

    @Test
    fun `观战时界面拿得到谁是狼`() {
        val live = state(
            Phase.DAY_DISCUSSION,
            WerewolfLive(seat = 1, phase = Phase.DAY_DISCUSSION, text = "我跳预言家"),
        ).toLive(humanSeat = null, turn = null, avatars = emptyMap())

        assertEquals(2, live.seats.count { it.wolf })
        assertEquals(2, live.seats.count { it.roleLabel == "狼人" })
    }

    /** 夜里这五个阶段，任何一个都不该在参与局的界面上留下痕迹。 */
    @Test
    fun `夜里所有阶段都不亮座位也不出气泡`() {
        val nights = listOf(
            Phase.NIGHT_GUARD,
            Phase.NIGHT_WOLF_CHAT,
            Phase.NIGHT_WOLF,
            Phase.NIGHT_SEER,
            Phase.NIGHT_WITCH,
        )
        for (phase in nights) {
            val live = state(
                phase,
                WerewolfLive(seat = 1, phase = phase, text = "夜里的话", reasoning = "夜里在想"),
            ).toLive(humanSeat = 3, turn = null, avatars = emptyMap())

            assertNull("$phase 不该出气泡", live.streaming)
            assertTrue(
                "$phase 不该亮任何座位：${live.seats.filter { it.active }.map { it.seat }}",
                live.seats.none { it.active },
            )
        }
    }

    /** 白天是公开的：发言、投票的时候该看到谁在动。 */
    @Test
    fun `白天发言时该看到说话的人`() {
        val live = WerewolfUiState(
            table = WerewolfTable(game(Phase.DAY_DISCUSSION)),
            live = WerewolfLive(seat = 2, phase = Phase.DAY_DISCUSSION, text = "我跳预言家"),
            // 座位高亮来自引擎进度：引擎此刻在等谁
            progress = WerewolfProgress(
                day = 1,
                phase = Phase.DAY_DISCUSSION,
                step = 1,
                total = 5,
                seat = 2,
            ),
            running = true,
        ).toLive(humanSeat = 3, turn = null, avatars = emptyMap())

        assertNotNull("白天发言要有气泡", live.streaming)
        assertTrue("说话的人要亮起来", live.seats.first { it.seat == 2 }.active)
    }

    // ------------------------------------------------------------------
    // 夜里顶栏那一行
    // ------------------------------------------------------------------

    /** 1、2 狼 · 3 预言家 · 4 女巫 · 5 守卫 · 6 平民。 */
    private fun gameWithGods(phase: Phase): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(
            composition = mapOf(
                WerewolfRole.WEREWOLF to 2,
                WerewolfRole.SEER to 1,
                WerewolfRole.WITCH to 1,
                WerewolfRole.GUARD to 1,
                WerewolfRole.VILLAGER to 1,
            ),
        ),
        roster = (1..6).map { RosterEntry(id = "p$it", name = "玩家$it") },
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.SEER,
            WerewolfRole.WITCH,
            WerewolfRole.GUARD,
            WerewolfRole.VILLAGER,
        ),
    ).copy(phase = phase)

    /** 造一个「此刻引擎在某个夜间阶段」的参与局界面。 */
    private fun nightLive(phase: Phase, humanSeat: Int?, step: Int = 1, total: Int = 1) =
        WerewolfUiState(
            table = WerewolfTable(gameWithGods(phase)),
            live = WerewolfLive(seat = 1, phase = phase, text = "夜里的话"),
            progress = WerewolfProgress(
                day = 1,
                phase = phase,
                step = step,
                total = total,
                seat = 1,
            ),
            running = true,
        ).toLive(humanSeat = humanSeat, turn = null, avatars = emptyMap())

    @Test
    fun `夜里顶栏不报守卫和女巫在行动`() {
        // 「守卫守护」「女巫用药」是在报这个角色在场。好人夜里只该看到一句中性话。
        val guard = nightLive(Phase.NIGHT_GUARD, humanSeat = 6)
        assertEquals("夜间进行中", guard.stageLabel)
        assertFalse("不能报出守卫在动：${guard.stageLabel}", guard.stageLabel!!.contains("守卫"))

        val witch = nightLive(Phase.NIGHT_WITCH, humanSeat = 6)
        assertEquals("夜间进行中", witch.stageLabel)
        assertFalse("不能报出女巫在动：${witch.stageLabel}", witch.stageLabel!!.contains("女巫"))
    }

    @Test
    fun `夜里顶栏不报狼队思考人数`() {
        // 「狼人商议 1/3」= 把这局有几匹狼直接报给全场。平民一眼就知道。
        val live = nightLive(Phase.NIGHT_WOLF_CHAT, humanSeat = 6, step = 1, total = 3)

        assertEquals("夜间进行中", live.stageLabel)
        assertFalse("不能报狼人商议：${live.stageLabel}", live.stageLabel!!.contains("狼人商议"))
        assertFalse("不能报狼的数量：${live.stageLabel}", live.stageLabel!!.contains("3"))
    }

    @Test
    fun `夜里兜底那一行也不报阶段名`() {
        // 没有引擎进度时走 phaseLabel 兜底，那一条同样不能漏。
        val live = WerewolfUiState(
            table = WerewolfTable(gameWithGods(Phase.NIGHT_WOLF_CHAT)),
            live = WerewolfLive(seat = 1, phase = Phase.NIGHT_WOLF_CHAT, text = "夜里的话"),
            running = true,
        ).toLive(humanSeat = 6, turn = null, avatars = emptyMap())

        assertEquals("夜间进行中", live.phaseLabel)
    }

    @Test
    fun `本人正处在这一阶段时看得见阶段名`() {
        // 他本来就知道自己是什么角色，这句不泄露新信息；
        // 而且他得知道自己正卡在用药 / 下刀这样的环节里，不然不知道该做什么。
        assertEquals("女巫用药", nightLive(Phase.NIGHT_WITCH, humanSeat = 4).stageLabel)
        assertEquals("守卫守护", nightLive(Phase.NIGHT_GUARD, humanSeat = 5).stageLabel)
        assertEquals("预言家查验", nightLive(Phase.NIGHT_SEER, humanSeat = 3).stageLabel)
        assertEquals("狼人商议", nightLive(Phase.NIGHT_WOLF_CHAT, humanSeat = 1).stageLabel)
        assertEquals("狼人下刀", nightLive(Phase.NIGHT_WOLF, humanSeat = 2).stageLabel)
    }

    @Test
    fun `观战时夜里照旧给阶段名和进度`() {
        // 观战是上帝视角，夜里阶段名和进度都该看得见。
        val live = nightLive(Phase.NIGHT_WOLF_CHAT, humanSeat = null, step = 1, total = 2)

        assertTrue("观战要看得到狼人在商议：${live.stageLabel}", live.stageLabel!!.contains("狼人商议"))
    }

    @Test
    fun `白天顶栏照旧报进度和座位`() {
        // 白天是公开的，隐藏的是夜里，别把白天也一起藏了。
        val live = WerewolfUiState(
            table = WerewolfTable(game(Phase.DAY_DISCUSSION)),
            live = WerewolfLive(seat = 2, phase = Phase.DAY_DISCUSSION, text = "我跳预言家"),
            progress = WerewolfProgress(
                day = 1,
                phase = Phase.DAY_DISCUSSION,
                step = 1,
                total = 5,
                seat = 2,
            ),
            running = true,
        ).toLive(humanSeat = 3, turn = null, avatars = emptyMap())

        assertTrue("白天要报谁在说话：${live.stageLabel}", live.stageLabel!!.contains("2 号"))
    }

    // ------------------------------------------------------------------
    // 夜里的超时提示
    // ------------------------------------------------------------------

    private fun timeout(seat: Int, phase: Phase, kind: TurnKind = TurnKind.DECISION) =
        TurnTimeout(seat, kind, 60_000L, 62_000L, phase = phase)

    @Test
    fun `夜里的超时提示不点名`() {
        // 「5 号的决定超出 60 秒」= 当着全场的面报出 5 号是夜里在动的人
        // （夜里的决定只有守卫 / 预言家 / 女巫 / 狼人做）。
        val text = timeoutNoticeText(
            timeout = timeout(5, Phase.NIGHT_WITCH),
            nameOf = { "玩家$it" },
            humanSeat = 3,
        )

        assertFalse("不能报座位号：$text", text.contains("5 号"))
        assertFalse("不能报名字：$text", text.contains("玩家5"))
        assertFalse("不能报「决定」：$text", text.contains("决定"))
        assertTrue("还是要说清楚发生了什么：$text", text.contains("已按兜底继续"))
    }

    @Test
    fun `夜里的超时提示对本人照旧点名`() {
        // 他本来就知道自己是谁、正在做什么，点名不泄露新信息。
        val text = timeoutNoticeText(
            timeout = timeout(5, Phase.NIGHT_WITCH),
            nameOf = { "玩家$it" },
            humanSeat = 5,
        )

        assertTrue("本人要看得见：$text", text.contains("5 号"))
    }

    @Test
    fun `白天的超时提示照旧点名`() {
        // 白天谁在说话是公开的，点名不构成泄露。
        val text = timeoutNoticeText(
            timeout = timeout(2, Phase.DAY_DISCUSSION, TurnKind.SPEECH),
            nameOf = { "玩家$it" },
            humanSeat = 3,
        )

        assertTrue("白天要点名：$text", text.contains("2 号"))
        assertTrue("白天要点名：$text", text.contains("玩家2"))
    }

    @Test
    fun `观战时夜里的超时提示照旧点名`() {
        // 观战是上帝视角。
        val text = timeoutNoticeText(
            timeout = timeout(5, Phase.NIGHT_WITCH),
            nameOf = { "玩家$it" },
            humanSeat = null,
        )

        assertTrue("观战要点名：$text", text.contains("5 号"))
    }

    // ------------------------------------------------------------------
    // 对局设置面板：按座位的运行时数据也是夜间行动的侧信道
    // ------------------------------------------------------------------

    /**
     * 3 号（预言家）白天走了一步 10 秒，夜里走了一步 62 秒并且超时了。
     *
     * 夜里那一步的痕迹**一条都不能进参与局面板**：
     * 顶栏的脱敏提示会让参与者知道「刚才有人超时」，面板上再写清楚是谁超时、
     * 谁的思考被系统关了，两处一对就把 3 号锁死了。
     */
    private fun paceOf(participating: Boolean) = buildPace(
        table = WerewolfTable(gameWithGods(Phase.NIGHT_WOLF_CHAT)),
        limit = WerewolfTurnLimit.STANDARD,
        timings = mapOf(
            3 to SeatTiming(
                seat = 3,
                lastMs = 62_000L,
                totalMs = 72_000L,
                turns = 2,
                timeouts = 1,
                dayLastMs = 10_000L,
                dayTotalMs = 10_000L,
                dayTurns = 1,
                dayTimeouts = 0,
            ),
        ),
        // 引擎那边这个座位被关掉了深度思考（夜里超时导致的）
        thinkingOff = setOf(3),
        playerThinkingOff = emptySet(),
        roleThinking = mapOf(3 to true),
        avatars = emptyMap(),
        running = true,
        bubbleDefault = false,
        bubbleVisible = emptyMap(),
        participating = participating,
    )

    @Test
    fun `参与局面板里看不到夜里的超时次数`() {
        val seat = paceOf(participating = true).seats.first { it.seat == 3 }

        assertEquals("按座位的超时次数不能给", 0, seat.timeoutCount)
    }

    @Test
    fun `参与局面板里看不到夜里被关掉的思考`() {
        val seat = paceOf(participating = true).seats.first { it.seat == 3 }

        assertTrue("被系统关掉的思考不能露出来", seat.thinkingOn)
    }

    @Test
    fun `参与局面板里的最近耗时是白天那一步`() {
        val seat = paceOf(participating = true).seats.first { it.seat == 3 }

        assertEquals("要看白天的账，不是夜里的", "10s", seat.lastLabel)
    }

    @Test
    fun `观战局面板照旧显示真实数据`() {
        val seat = paceOf(participating = false).seats.first { it.seat == 3 }

        assertEquals("上帝视角照实显示", 1, seat.timeoutCount)
        assertFalse("上帝视角照实显示", seat.thinkingOn)
        assertEquals("上帝视角照实显示", "1 分 02 秒", seat.lastLabel)
    }
}
