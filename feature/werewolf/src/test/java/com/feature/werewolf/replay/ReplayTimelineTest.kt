package com.feature.werewolf.replay

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复盘演出的时间线。
 *
 * 这一层的错都很「安静」：不会崩，只是牌桌上多一具尸体、警徽早挂了半小时，
 * 或者夜聊被当众念了出来。所以每条规则都要有测试盯着。
 */
class ReplayTimelineTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry(id = "p$it", name = "玩家$it") }

    private fun standard9(history: List<GameEvent>, sheriffSeat: Int? = null): WerewolfGame =
        WerewolfGame.withRoles(
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
        ).copy(history = history, sheriffSeat = sheriffSeat)

    private fun speech(day: Int, phase: Phase, seat: Int, text: String, kind: MessageKind = MessageKind.SPEECH) =
        WerewolfMessage(day = day, phase = phase, seat = seat, kind = kind, text = text)

    /** 上帝播报的文案必须和真实生成一致，否则 [ReplayTimeline] 的对齐会失效。 */
    private fun broadcast(event: GameEvent) = WerewolfMessage(
        day = event.day,
        phase = Phase.DAY_DISCUSSION,
        seat = null,
        kind = MessageKind.DIVINE,
        text = WerewolfPrompt.describe(event),
    )

    @Test
    fun `死亡跟着播报走，不会一开场就一地尸体`() {
        val dawn = GameEvent.Dawn(day = 1, deadSeats = listOf(8))
        val table = WerewolfTable(
            game = standard9(history = listOf(dawn)),
            transcript = listOf(
                // 夜里他们还在商量，这时候 8 号当然没死
                WerewolfMessage(1, Phase.NIGHT_WOLF_CHAT, 2, MessageKind.WOLF_CHAT, "刀 8 号。", audience = setOf(1, 2, 3)),
                broadcast(dawn),
                speech(1, Phase.DAY_DISCUSSION, 5, "8 号昨晚被刀了。"),
            ),
        )

        val frames = ReplayTimeline.build(table, humanSeat = null, godView = true)

        assertTrue("播报之前不该有人出局", frames[0].deadSeats.isEmpty())
        assertEquals("播报那一帧，8 号就该灰了", setOf(8), frames[1].deadSeats)
        assertTrue("之后一直保持着", 8 in frames[2].deadSeats)
    }

    @Test
    fun `放逐也是到投票播报才出局`() {
        val exiled = GameEvent.Exiled(day = 1, seat = 5, voteCount = 4.0)
        val table = WerewolfTable(
            game = standard9(history = listOf(exiled)),
            transcript = listOf(
                WerewolfMessage(1, Phase.DAY_VOTE, 3, MessageKind.VOTE, "我投 5 号。"),
                broadcast(exiled),
            ),
        )

        val frames = ReplayTimeline.build(table, humanSeat = null, godView = false)

        // 投票的理由属于投票阶段，那时候 5 号还没被投出去——顺序反了，
        // 用户会看到一个「已经出局的人在投票」
        assertTrue("投票阶段 5 号还活着", frames[0].deadSeats.isEmpty())
        assertEquals(setOf(5), frames[1].deadSeats)
    }

    @Test
    fun `警徽按事件先后换手`() {
        val elected = GameEvent.SheriffElected(day = 1, seat = 4, votes = 5)
        val passed = GameEvent.SheriffBadgePassed(day = 2, fromSeat = 4, toSeat = 7)
        val table = WerewolfTable(
            game = standard9(history = listOf(elected, passed)),
            transcript = listOf(
                broadcast(elected),
                WerewolfMessage(2, Phase.DAY_LAST_WORDS, 4, MessageKind.SPEECH, "我把警徽交给 7 号。"),
                broadcast(passed),
            ),
        )

        val frames = ReplayTimeline.build(table, humanSeat = null, godView = false)

        assertEquals("选出来之后挂在 4 号身上", 4, frames[0].sheriffSeat)
        assertEquals("遗言还没交待完，仍然是 4 号", 4, frames[1].sheriffSeat)
        assertEquals("交完才换人", 7, frames[2].sheriffSeat)
    }

    @Test
    fun `没有警长的局一直不挂警徽`() {
        val vacant = GameEvent.SheriffVacant(day = 1, reason = "无人上警")
        val table = WerewolfTable(
            game = standard9(history = listOf(vacant), sheriffSeat = 3),
            transcript = listOf(broadcast(vacant)),
        )

        val frames = ReplayTimeline.build(table, humanSeat = null, godView = false)

        assertNull("从没选出来过，就不该有人挂着警徽", frames[0].sheriffSeat)
    }

    @Test
    fun `普通视角看不到夜聊，上帝视角才看得到`() {
        val table = WerewolfTable(
            game = standard9(history = emptyList()),
            transcript = listOf(
                WerewolfMessage(1, Phase.NIGHT_WOLF_CHAT, 2, MessageKind.WOLF_CHAT, "刀 8 号。", audience = setOf(1, 2, 3)),
                speech(1, Phase.DAY_DISCUSSION, 5, "我觉得 2 号有问题。"),
            ),
        )

        val plain = ReplayTimeline.build(table, humanSeat = 5, godView = false)
        assertEquals("参与的局只看得见公开消息", 1, plain.size)
        assertFalse("夜聊一个字都不能出现在普通视角", plain.any { it.message.kind == MessageKind.WOLF_CHAT })

        val god = ReplayTimeline.build(table, humanSeat = 5, godView = true)
        assertEquals(2, god.size)
        assertTrue("上帝视角里夜聊进画面", god[0].message.kind == MessageKind.WOLF_CHAT)
        assertTrue("而且会被念出来——上帝视角就是要听见", god[0].speakable)
    }

    @Test
    fun `内心活动永远不朗读`() {
        val table = WerewolfTable(
            game = standard9(history = emptyList()),
            transcript = listOf(
                WerewolfMessage(1, Phase.DAY_VOTE, 3, MessageKind.VOTE, "我投 5 号。"),
                WerewolfMessage(1, Phase.NIGHT_SEER, 4, MessageKind.NIGHT_ACT, "验 6 号。", audience = setOf(4)),
            ),
        )

        // 连上帝视角也不念：一圈投票十几段独白，念出来既拖节奏又分不清谁在说、谁在想
        val god = ReplayTimeline.build(table, humanSeat = null, godView = true)
        assertFalse(god[0].speakable)
        assertFalse(god[1].speakable)
    }

    @Test
    fun `自己在座时不念自己的话`() {
        val table = WerewolfTable(
            game = standard9(history = emptyList()),
            transcript = listOf(
                speech(1, Phase.DAY_DISCUSSION, 5, "我要投 2 号。"),
                speech(1, Phase.DAY_DISCUSSION, 6, "我也投 2 号。"),
            ),
        )

        val frames = ReplayTimeline.build(table, humanSeat = 5, godView = false)

        assertFalse("自己刚打完的字再念一遍是噪音", frames[0].speakable)
        assertTrue(frames[1].speakable)
    }

    @Test
    fun `空存档不会炸`() {
        val table = WerewolfTable(game = standard9(history = emptyList()), transcript = emptyList())
        assertTrue(ReplayTimeline.build(table, humanSeat = null, godView = false).isEmpty())
    }

    @Test
    fun `被枪带走的人到开枪那一刻才出局`() {
        val exiled = GameEvent.Exiled(day = 1, seat = 6, voteCount = 5.0)
        val shot = GameEvent.HunterShot(day = 1, hunterSeat = 6, targetSeat = 3)
        val table = WerewolfTable(
            game = standard9(history = listOf(exiled, shot)),
            transcript = listOf(
                broadcast(exiled),
                WerewolfMessage(1, Phase.HUNTER_SHOOT, 6, MessageKind.SPEECH, "我带 3 号。"),
                broadcast(shot),
            ),
        )

        val frames = ReplayTimeline.build(table, humanSeat = null, godView = false)

        assertEquals("放逐之后只有猎人出局", setOf(6), frames[0].deadSeats)
        assertEquals("枪还没响，3 号还活着", setOf(6), frames[1].deadSeats)
        assertEquals("枪响之后两个都不在了", setOf(3, 6), frames[2].deadSeats)
    }
}
