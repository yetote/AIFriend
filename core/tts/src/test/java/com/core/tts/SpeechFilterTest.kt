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

    private fun speakable(
        isPublicSpeech: Boolean,
        speakerSeat: Int?,
        selfSeat: Int?,
        text: String = "我怀疑 3 号",
    ) = SpeechFilter.speakable(isPublicSpeech, speakerSeat, selfSeat, text)
}
