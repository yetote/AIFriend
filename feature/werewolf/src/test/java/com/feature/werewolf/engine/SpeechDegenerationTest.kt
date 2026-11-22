package com.feature.werewolf.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechDegenerationTest {

    @Test
    fun `长串同一字符被截断 正常前缀保留`() {
        val text = "我是12号，今天先听大家说。" + "▮".repeat(80)
        val cut = SpeechDegeneration.cutIndex(text)
        assertTrue("检测到退化", cut != null)
        assertEquals("我是12号，今天先听大家说。", SpeechDegeneration.cut(text))
    }

    @Test
    fun `字符间夹换行也算连续 不漏检刷屏`() {
        // 截图里那种一格一格换行排列的刷屏
        val text = "我先说两句。" + (0 until 40).joinToString("\n") { "▮" }
        val cut = SpeechDegeneration.cutIndex(text)
        assertTrue("夹换行照样检出", cut != null)
        assertEquals("我先说两句。", SpeechDegeneration.cut(text))
    }

    @Test
    fun `短串循环被截断`() {
        val text = "我的看法是三好学生。" + "环".repeat(50)
        assertEquals("我的看法是三好学生。", SpeechDegeneration.cut(text))

        val doubled = "听我说。" + "不知道".repeat(20)
        assertEquals("听我说。", SpeechDegeneration.cut(doubled))
    }

    @Test
    fun `口语叠词不误伤`() {
        listOf(
            "哈哈哈哈哈，可笑死我了。",
            "好好好，就这么定了。",
            "你你你你怎么回事？",
            "不不不不不，我不是这个意思。",  // 5 个，远低于阈值
            "看看看，来了来了。",
        ).forEach { assertTrue("不该截断：$it", SpeechDegeneration.cutIndex(it) == null) }
    }

    @Test
    fun `喊口号式排比不误伤`() {
        // 只有 3 轮，够不着「循环 10 轮」的门槛
        val text = "加油加油加油，坚持坚持坚持，我们能赢！"
        assertNull(SpeechDegeneration.cutIndex(text))
    }

    @Test
    fun `省略号串不算退化`() {
        assertNull(SpeechDegeneration.cutIndex("让我想想………………"))
    }

    @Test
    fun `短文本直接放行`() {
        assertNull(SpeechDegeneration.cutIndex("嗯"))
        assertNull(SpeechDegeneration.cutIndex("好的我明白了"))
    }

    @Test
    fun `退化从开头发生 截成空串`() {
        assertEquals("", SpeechDegeneration.cut("▮".repeat(50)))
    }

    @Test
    fun `情绪字放宽阈值 超长才截断`() {
        // 12 个哈是正常笑，不能砍
        assertNull(SpeechDegeneration.cutIndex("哈哈哈哈哈哈哈哈哈哈哈，太逗了"))
        // 但刷够 24 个还是故障
        assertTrue(SpeechDegeneration.cutIndex("哈".repeat(30)) != null)
    }
}
