package com.core.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 正文内联 `<think>` 的拆分。
 *
 * 不拆的后果：狼人杀看门狗以为模型已经在「发言」，思考时限失效，
 * 一只狼夜聊能想二十分钟，而且思考原文会被当成发言念出来。
 */
class ThinkTagSplitterTest {

    /** 逐帧喂，最后 finish，把正文和思维链各自拼起来。 */
    private fun run(vararg chunks: String): Pair<String, String> {
        val splitter = ThinkTagSplitter()
        val content = StringBuilder()
        val reasoning = StringBuilder()
        chunks.forEach { chunk ->
            val (c, r) = splitter.feed(chunk)
            content.append(c); reasoning.append(r)
        }
        val (c, r) = splitter.finish()
        content.append(c); reasoning.append(r)
        return content.toString() to reasoning.toString()
    }

    @Test
    fun `开头的 think 块拆进思维链`() {
        assertEquals("刀 3 号。" to "先想想谁是神", run("<think>先想想谁是神</think>\n\n刀 3 号。"))
    }

    @Test
    fun `标签被切在两帧之间也能认出来`() {
        assertEquals(
            "刀 5 号" to "分析局势",
            run("<th", "ink>分析", "局势</th", "ink>", "刀 5 号"),
        )
    }

    @Test
    fun `思考期间一个正文字都不吐，看门狗才能按思考时限计时`() {
        val splitter = ThinkTagSplitter()
        listOf("<think>", "我在想", "还在想", "</thi").forEach {
            assertEquals("", splitter.feed(it).first)
        }
        assertEquals("好，刀 2", splitter.feed("nk>好，刀 2").first)
    }

    @Test
    fun `前导空白不影响识别`() {
        assertEquals("结论" to "想", run("\n  ", "<think>想</think>结论"))
    }

    @Test
    fun `没有 think 的普通正文原样透传`() {
        assertEquals("我是预言家" to "", run("我是", "预言家"))
    }

    @Test
    fun `正文中间出现的 think 字样是在说话，不能吞`() {
        assertEquals("他说 <think> 是什么" to "", run("他说 <think> 是什么"))
    }

    @Test
    fun `以小于号开头但不是 think 标签的正文照常输出`() {
        assertEquals("<b>好人</b>" to "", run("<b>好人</b>"))
    }

    @Test
    fun `没等到闭合标签就断流，残留全算思维链`() {
        assertEquals("" to "想到一半", run("<think>想到一半</th"))
    }

    @Test
    fun `一次性回复去掉开头的 think 块`() {
        assertEquals("{\"target\":3}", ThinkTagSplitter.strip("<think>选 3</think>{\"target\":3}"))
        assertEquals("直接回答", ThinkTagSplitter.strip("直接回答"))
    }
}
