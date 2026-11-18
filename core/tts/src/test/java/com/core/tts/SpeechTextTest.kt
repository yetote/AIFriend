package com.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 朗读文本清洗：耳朵听到的必须是「话」，不是排版。
 *
 * 每一条都对应线上真实出现过的文本——角色发言里带舞台说明、markdown、表情，
 * 直接送去合成就是把动作描写念出来，听感立刻变成念稿。
 */
class SpeechTextTest {

    @Test
    fun `括号里的舞台说明不朗读`() {
        assertEquals(
            "我怀疑 3 号。",
            SpeechText.forSpeech("（指尖轻点白角，淡紫眼微微一敛）我怀疑 3 号。"),
        )
        assertEquals("别急。", SpeechText.forSpeech("(叹气) 别急。"))
        assertEquals("收到。", SpeechText.forSpeech("【小声】收到。"))
    }

    @Test
    fun `markdown 符号不朗读 内容保留`() {
        assertEquals("我投 5 号。", SpeechText.forSpeech("**我投 5 号**。"))
        assertEquals("看这里。", SpeechText.forSpeech("[看这里](https://example.com)。"))
    }

    @Test
    fun `表情符号不朗读`() {
        assertEquals("今天先过 4 号。", SpeechText.forSpeech("今天先过 4 号😤✨"))
    }

    @Test
    fun `没有句末标点时补一个 否则语调是平的`() {
        assertEquals("我投 3 号。", SpeechText.forSpeech("我投 3 号"))
        assertEquals("真的吗？", SpeechText.forSpeech("真的吗？"))
    }

    @Test
    fun `重复标点收成一个 连串感叹会被念成一串停顿`() {
        assertEquals("别投他！", SpeechText.forSpeech("别投他！！！"))
        assertEquals("等等。", SpeechText.forSpeech("等等。。。"))
    }

    @Test
    fun `换行当停顿 中文之间的空格去掉`() {
        assertEquals("先听我说，5 号有问题。", SpeechText.forSpeech("先听我说\n\n5 号有问题"))
        assertEquals("我觉得他像狼。", SpeechText.forSpeech("我觉得 他 像狼。"))
    }

    @Test
    fun `只有括号或空白时返回 null`() {
        assertNull(SpeechText.forSpeech("（沉默）"))
        assertNull(SpeechText.forSpeech("   "))
        assertNull(SpeechText.forSpeech("……"))
    }

    @Test
    fun `正常发言原样返回`() {
        val text = "熊在 11 号咆哮，坑在 10 号和 12 号，今天先推 12 号。"
        assertEquals(text, SpeechText.forSpeech(text))
    }

    @Test
    fun `结尾的逗号不会被补成句号`() {
        val cleaned = SpeechText.forSpeech("我投 3 号，因为")!!
        assertTrue(cleaned.endsWith("。"))
        assertTrue(!cleaned.contains("，。"))
    }
}
