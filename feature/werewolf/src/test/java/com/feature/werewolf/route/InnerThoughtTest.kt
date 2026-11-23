package com.feature.werewolf.route

import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.game.Phase
import com.ui.werewolf.data.UIWerewolfLineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「说出口」和「心里想」的分界。
 *
 * 这条线两头都要钉住：
 *
 * - **朗读**：内心活动永远不念，上帝视角也不例外。念出来一圈投票就是十几段独白。
 * - **排版**：内心活动不能套用发言的样式。没人听见的字写成发言的样子，
 *   用户会以为「他刚才在桌上说了这句」，复盘时对不上自己听到的内容。
 */
class InnerThoughtTest {

    @Test
    fun `只有发言和夜聊算说出口`() {
        assertTrue(MessageKind.SPEECH.isSpokenOutLoud)
        // 狼人夜聊是对队友说的，不是心里想。只念发言却跳过夜聊，观战就少了一半剧情
        assertTrue(MessageKind.WOLF_CHAT.isSpokenOutLoud)

        assertFalse(MessageKind.VOTE.isSpokenOutLoud)
        assertFalse(MessageKind.NIGHT_ACT.isSpokenOutLoud)
        assertFalse(MessageKind.DIVINE.isSpokenOutLoud)
        assertFalse(MessageKind.BALLOT.isSpokenOutLoud)
    }

    @Test
    fun `唱票卡不朗读 但不算内心活动`() {
        // 它是「结果」不是「谁说的话」也不是「谁心里想的」：
        // 永不朗读靠 neverSpoken，排版靠独立的 BALLOT 卡片，两者分开。
        assertTrue("永不朗读", MessageKind.BALLOT.neverSpoken)
        assertFalse("不是内心活动", MessageKind.BALLOT.isInnerThought)
        assertEquals(UIWerewolfLineKind.BALLOT, lineKindOf(MessageKind.BALLOT))
    }

    @Test
    fun `投票和夜间行动算内心活动`() {
        assertTrue(MessageKind.VOTE.isInnerThought)
        assertTrue(MessageKind.NIGHT_ACT.isInnerThought)

        assertFalse(MessageKind.SPEECH.isInnerThought)
        assertFalse(MessageKind.WOLF_CHAT.isInnerThought)
        assertFalse(MessageKind.DIVINE.isInnerThought)
    }

    @Test
    fun `内心活动排成内心样式 不冒充发言`() {
        // 投票单列一类：它和夜里那些念头一样没人听见，但牌桌上要换个底色 ——
        // 票会被当场执行，念头的分量比它轻
        assertEquals(UIWerewolfLineKind.VOTE, lineKindOf(MessageKind.VOTE))
        assertEquals(UIWerewolfLineKind.INNER, lineKindOf(MessageKind.NIGHT_ACT))

        assertEquals(UIWerewolfLineKind.SPEECH, lineKindOf(MessageKind.SPEECH))
        // 夜聊留着密谈卡：它是说出口的，只是听众只有狼
        assertEquals(UIWerewolfLineKind.PRIVATE, lineKindOf(MessageKind.WOLF_CHAT))
        assertEquals(UIWerewolfLineKind.DIVINE, lineKindOf(MessageKind.DIVINE))
    }

    @Test
    fun `夜间行动按阶段带不同标签`() {
        val guard = WerewolfMessage(
            day = 1, phase = Phase.NIGHT_GUARD, seat = 3,
            kind = MessageKind.NIGHT_ACT, text = "我守 5 号。", audience = setOf(3),
        )
        val seer = guard.copy(phase = Phase.NIGHT_SEER, text = "我查 7 号。")
        val witch = guard.copy(phase = Phase.NIGHT_WITCH, text = "我用解药。")
        val vote = guard.copy(phase = Phase.DAY_VOTE, kind = MessageKind.VOTE, text = "我投 3 号。")

        assertEquals("守人", innerLabelOf(guard))
        assertEquals("查验", innerLabelOf(seer))
        assertEquals("用药", innerLabelOf(witch))
        assertEquals("投票", innerLabelOf(vote))
        // 说出口的话不该挂内心标签
        assertNull(innerLabelOf(vote.copy(kind = MessageKind.SPEECH)))
    }
}
