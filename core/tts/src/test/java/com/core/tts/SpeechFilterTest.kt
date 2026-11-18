package com.core.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechFilterTest {

    @Test
    fun `AI 的公开发言要读`() {
        assertTrue(speakable(isPublicSpeech = true, speakerSeat = 3, selfSeat = 1))
    }

    @Test
    fun `自己说的话不读`() {
        assertFalse(speakable(isPublicSpeech = true, speakerSeat = 1, selfSeat = 1))
    }

    @Test
    fun `私有发言永远不读 哪怕座位数对得上`() {
        // 这是最要紧的一条：狼人夜聊一旦念出来，旁边的人全听见了，
        // 而且话已经出口，不像界面文字可以撤掉。
        assertFalse(speakable(isPublicSpeech = false, speakerSeat = 3, selfSeat = 1))
    }

    @Test
    fun `观战时读所有公开发言`() {
        assertTrue(speakable(isPublicSpeech = true, speakerSeat = 3, selfSeat = null))
    }

    @Test
    fun `上帝播报没有座位 不读`() {
        assertFalse(speakable(isPublicSpeech = true, speakerSeat = null, selfSeat = 1))
    }

    @Test
    fun `空白文本不读 否则会读出莫名的停顿`() {
        assertFalse(speakable(isPublicSpeech = true, speakerSeat = 3, selfSeat = 1, text = "  "))
    }

    @Test
    fun `上帝视角下狼人夜聊也读`() {
        // 纯观战时牌桌上本来就没有秘密：夜聊、查验、用药全在屏幕上，
        // 只念公开发言等于把剧情念丢一半
        assertTrue(
            speakable(
                isPublicSpeech = false,
                speakerSeat = 3,
                selfSeat = null,
                readEverything = true,
            )
        )
    }

    @Test
    fun `上帝视角下上帝播报也读`() {
        // 播报没有座位，普通视角下要跳过；观战时要照着念出来
        assertTrue(
            speakable(
                isPublicSpeech = false,
                speakerSeat = null,
                selfSeat = null,
                text = "第1天 天亮：熊 11 号在咆哮",
                readEverything = true,
            )
        )
    }

    @Test
    fun `上帝视角下投票表态也读`() {
        assertTrue(
            speakable(isPublicSpeech = true, speakerSeat = 5, selfSeat = null, readEverything = true)
        )
    }

    @Test
    fun `内心活动永远不读 上帝视角也不读`() {
        // 投票理由、夜里查验用药的念头：没人听见，也不该被念出来。
        // 一圈人投票念过去就是十几段独白，节奏直接拖垮，还和真正的发言混成一片
        assertFalse(
            speakable(
                isPublicSpeech = true,
                speakerSeat = 5,
                selfSeat = null,
                text = "我投 3 号。他昨天的发言太飘",
                readEverything = true,
                isInnerThought = true,
            )
        )
        assertFalse(
            speakable(
                isPublicSpeech = false,
                speakerSeat = 7,
                selfSeat = null,
                text = "我守 3 号。",
                readEverything = true,
                isInnerThought = true,
            )
        )
    }

    @Test
    fun `上帝视角下仍然不读空白`() {
        // 队列是按条消费的，空文本会让喇叭停一次，听起来像卡带
        assertFalse(
            speakable(
                isPublicSpeech = true,
                speakerSeat = 5,
                selfSeat = null,
                text = "",
                readEverything = true,
            )
        )
    }

    @Test
    fun `上帝视角下也不读自己的话`() {
        // 目前观战拿不到人类座位，这条是防御：将来若允许「参与 + 上帝视角」，
        // 自己的话也不该被念一遍
        assertFalse(
            speakable(
                isPublicSpeech = true,
                speakerSeat = 1,
                selfSeat = 1,
                readEverything = true,
            )
        )
    }

    private fun speakable(
        isPublicSpeech: Boolean,
        speakerSeat: Int?,
        selfSeat: Int?,
        text: String = "我怀疑 3 号",
        readEverything: Boolean = false,
        isInnerThought: Boolean = false,
    ) = SpeechFilter.speakable(
        isPublicSpeech,
        speakerSeat,
        selfSeat,
        text,
        readEverything,
        isInnerThought,
    )
}
