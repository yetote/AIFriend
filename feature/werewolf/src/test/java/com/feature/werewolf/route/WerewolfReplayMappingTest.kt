package com.feature.werewolf.route

import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.prompt.WerewolfPrompt
import com.feature.werewolf.replay.ReplayTimeline
import com.feature.werewolf.vm.WerewolfRecordView
import com.feature.werewolf.vm.WerewolfReplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 演出画面从回放状态映射出来。
 *
 * 要守住的是「演出不能偷看终局」：存档里的 `game` 是最后一刻——
 * 所有人都死了、警徽在最后那个人手上。直接拿它画，第一句话还没说出来
 * 牌桌上就已经是结局了。
 */
class WerewolfReplayMappingTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry(id = "p$it", name = "玩家$it") }

    private fun standard9(history: List<GameEvent>): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(composition = WerewolfConfig.Standard9),
        roster = roster(9),
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.SEER,
            WerewolfRole.WITCH,
            WerewolfRole.HUNTER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
        ),
    ).copy(history = history, day = 3, phase = Phase.FINISHED)

    private fun replayTable(): WerewolfTable {
        val dawn = GameEvent.Dawn(day = 1, deadSeats = listOf(8))
        val exiled = GameEvent.Exiled(day = 2, seat = 5, voteCount = 4.0)
        return WerewolfTable(
            game = standard9(history = listOf(dawn, exiled)),
            transcript = listOf(
                WerewolfMessage(1, Phase.DAY_DISCUSSION, 2, MessageKind.SPEECH, "我先看看局势。"),
                WerewolfMessage(1, Phase.DAY_DISCUSSION, null, MessageKind.DIVINE, WerewolfPrompt.describe(dawn)),
                WerewolfMessage(2, Phase.DAY_VOTE, 3, MessageKind.VOTE, "我投 5 号。"),
                WerewolfMessage(2, Phase.DAY_DISCUSSION, null, MessageKind.DIVINE, WerewolfPrompt.describe(exiled)),
            ),
        )
    }

    private fun replay(cursor: Int, godView: Boolean = false, humanSeat: Int? = null): WerewolfReplay {
        val table = replayTable()
        return WerewolfReplay(
            recordId = "r1",
            table = table,
            humanSeat = humanSeat,
            godView = godView,
            frames = ReplayTimeline.build(table, humanSeat, godView),
            cursor = cursor,
            playing = false,
        )
    }

    @Test
    fun `只显示演到的那些条`() {
        val live = replay(cursor = 1).toLive(emptyMap(), autoSpeak = true, speechReady = true)

        assertEquals("游标在第二条，就只该有两行", 2, live.lines.size)
        assertEquals("我先看看局势。", live.lines.first().text)
        // 播放条的位置和总长要跟着走，否则用户不知道还要看多久
        assertEquals("第 2 / 4 条", live.replay?.positionLabel)
        assertFalse(live.replay!!.playing)
    }

    @Test
    fun `座位状态跟着进度，不是终局`() {
        // 第一条是第 1 天白天的发言：天亮播报在存档里排在它前面，
        // 所以这一刻 8 号已经出局了，而 5 号还活着
        val first = replay(cursor = 0).toLive(emptyMap(), true, true)
        assertFalse("天亮已经过了", first.seats.first { it.seat == 8 }.alive)
        assertTrue("5 号还没被投出去", first.seats.first { it.seat == 5 }.alive)

        // 游标 3：第 2 天的放逐播报，5 号也出局
        val afterExile = replay(cursor = 3).toLive(emptyMap(), true, true)
        assertFalse(afterExile.seats.first { it.seat == 5 }.alive)
    }

    @Test
    fun `夜里那一帧还没有人出局`() {
        val dawn = GameEvent.Dawn(day = 1, deadSeats = listOf(8))
        val table = WerewolfTable(
            game = standard9(history = listOf(dawn)),
            transcript = listOf(
                WerewolfMessage(1, Phase.NIGHT_WOLF_CHAT, 2, MessageKind.WOLF_CHAT, "刀 8 号。", audience = setOf(1, 2, 3)),
                WerewolfMessage(1, Phase.DAY_DISCUSSION, null, MessageKind.DIVINE, WerewolfPrompt.describe(dawn)),
            ),
        )
        val live = WerewolfReplay(
            recordId = "r2",
            table = table,
            humanSeat = null,
            godView = true,
            frames = ReplayTimeline.build(table, null, godView = true),
            cursor = 0,
        ).toLive(emptyMap(), true, true)

        assertTrue("天还没亮，8 号当然还在", live.seats.first { it.seat == 8 }.alive)
    }

    @Test
    fun `普通视角不给身份，上帝视角才给`() {
        val plain = replay(cursor = 3, godView = false).toLive(emptyMap(), true, true)
        assertNull("普通视角看一眼就剧透，那演出没意义", plain.seats.first().roleLabel)
        assertFalse(plain.spectating)

        val god = replay(cursor = 3, godView = true).toLive(emptyMap(), true, true)
        assertNotNull(god.seats.first().roleLabel)
        assertTrue(god.spectating)
    }

    @Test
    fun `演出里没有轮到我，也没有胜负横幅`() {
        val live = replay(cursor = 3, humanSeat = 2).toLive(emptyMap(), true, true)

        assertNull("演出不该弹输入面板", live.turn)
        assertNull("还没演完就宣布胜负等于剧透", live.winnerLabel)
        assertFalse(live.finished)
        assertFalse(live.running)
        assertNull("演出没有正在生成的半句话", live.streaming)
        assertNotNull("播放条必须存在，界面靠它区分回放和对局", live.replay)
    }

    @Test
    fun `从头开始时的播放条状态`() {
        val live = replay(cursor = -1).toLive(emptyMap(), true, true)

        assertTrue("一条都还没演", live.lines.isEmpty())
        assertEquals("第 0 / 4 条", live.replay?.positionLabel)
        assertFalse("第一条之前没有上一条", live.replay!!.canPrev)
        assertTrue(live.replay!!.canNext)
    }

    @Test
    fun `演到最后一条时下一条不可用`() {
        val live = replay(cursor = 3).toLive(emptyMap(), true, true)
        assertFalse("已经在末尾", live.replay!!.canNext)
        assertTrue(live.replay!!.canPrev)
        assertEquals("1x", live.replay!!.speedLabel)
    }

    @Test
    fun `记录视图也能拼出演出，不需要先存一条 record`() {
        // 刚打完那一局的入口：只有 state，没有 _record
        val table = replayTable()
        val record = WerewolfRecordView(id = "", table = table, humanSeat = null)
        val frames = ReplayTimeline.build(record.table, record.humanSeat, godView = false)
        assertEquals(4, frames.size)
    }
}
