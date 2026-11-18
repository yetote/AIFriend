package com.feature.werewolf.route

import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfLive
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.ui.werewolf.data.UIWerewolfLive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「正在说」那段半句话的可见性映射。
 *
 * 这是这轮改动里唯一**会泄漏信息**的地方：流式内容带着某个座位的身份线索
 * （夜里在守人、在查验），一旦漏给人类玩家，这局就废了。
 * 所以每条可见性规则都单独翻一次面来测，而不只测「能看到」。
 */
class WerewolfStreamingMappingTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry(id = "p$it", name = "玩家$it") }

    /** 1、2、3 狼；4 预言家；5 女巫；6 猎人；7、8、9 平民。 */
    private fun standard9(): WerewolfGame = WerewolfGame.withRoles(
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
    )

    private fun live(
        phase: Phase,
        seat: Int,
        humanSeat: Int?,
        transcript: List<WerewolfMessage> = emptyList(),
        revealedIndex: Int = Int.MAX_VALUE,
        running: Boolean = true,
        text: String = "我觉得",
        reasoning: String = "先看他的表态",
    ): UIWerewolfLive = WerewolfUiState(
        table = WerewolfTable(standard9().copy(phase = phase), transcript),
        live = WerewolfLive(seat = seat, phase = phase, text = text, reasoning = reasoning),
        running = running,
    ).toLive(
        humanSeat = humanSeat,
        turn = null,
        avatars = emptyMap(),
        autoSpeak = true,
        speechReady = true,
        revealedIndex = revealedIndex,
    )

    @Test
    fun `观战时能看见正在说的话和思考过程`() {
        val ui = live(Phase.DAY_DISCUSSION, seat = 4, humanSeat = null)

        val streaming = ui.streaming
        assertNotNull(streaming)
        assertEquals(4, streaming!!.seat)
        assertEquals("玩家4", streaming.speaker)
        assertEquals("我觉得", streaming.text)
        assertEquals("心里话是观战的乐趣之一", "先看他的表态", streaming.reasoning)
    }

    @Test
    fun `参与中的局拿不到别人的思考过程`() {
        val ui = live(Phase.DAY_DISCUSSION, seat = 4, humanSeat = 9)

        val streaming = ui.streaming
        assertNotNull("他正在公开说话，这句本身是公开的", streaming)
        assertEquals("我觉得", streaming!!.text)
        assertEquals("听不到别人想什么，这才是牌桌", "", streaming.reasoning)
    }

    @Test
    fun `夜里单人行动时，别人正在说话这件事本身就是隐私`() {
        // 预言家在验人。把他亮在牌桌上，等于告诉全场谁是预言家
        val ui = live(Phase.NIGHT_SEER, seat = 4, humanSeat = 9)

        assertNull(ui.streaming)
    }

    @Test
    fun `狼人商议只对狼队友可见`() {
        val wolf = live(Phase.NIGHT_WOLF_CHAT, seat = 2, humanSeat = 1)
        assertNotNull("1 号是狼，能听到队友商量", wolf.streaming)
        assertEquals("队友的心里话同样不给看", "", wolf.streaming!!.reasoning)

        val good = live(Phase.NIGHT_WOLF_CHAT, seat = 2, humanSeat = 9)
        assertNull("9 号是好人，夜里不该看见狼在说话", good.streaming)
    }

    @Test
    fun `朗读还没念到的时候不抢先打字`() {
        // 前面还有两句话没念完（闸门停在第 0 条），此时把下一句打出来就是把语音剧透了
        val backlog = listOf(
            WerewolfMessage(1, Phase.DAY_DISCUSSION, 1, MessageKind.SPEECH, "一"),
            WerewolfMessage(1, Phase.DAY_DISCUSSION, 2, MessageKind.SPEECH, "二"),
            WerewolfMessage(1, Phase.DAY_DISCUSSION, 3, MessageKind.SPEECH, "三"),
        )
        val ui = live(
            Phase.DAY_DISCUSSION,
            seat = 4,
            humanSeat = 9,
            transcript = backlog,
            revealedIndex = 0,
        )

        assertNull(ui.streaming)
    }

    @Test
    fun `朗读念完之后立刻恢复流式`() {
        val backlog = listOf(
            WerewolfMessage(1, Phase.DAY_DISCUSSION, 1, MessageKind.SPEECH, "一"),
            WerewolfMessage(1, Phase.DAY_DISCUSSION, 2, MessageKind.SPEECH, "二"),
        )
        val ui = live(
            Phase.DAY_DISCUSSION,
            seat = 4,
            humanSeat = 9,
            transcript = backlog,
            revealedIndex = backlog.lastIndex,
        )

        assertNotNull("队列已经追平，这时不打字反而像卡住了", ui.streaming)
    }

    @Test
    fun `对局没在跑的时候不显示任何流式内容`() {
        val ui = live(Phase.DAY_DISCUSSION, seat = 4, humanSeat = 9, running = false)
        assertNull(ui.streaming)
    }
}
