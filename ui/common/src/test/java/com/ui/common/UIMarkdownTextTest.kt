package com.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 轻量 Markdown 解析的纯函数测试。
 *
 * 这些用例都来自 AI 发言的真实形态：狼队安排里爱用 **加粗** 和 - 列表，
 * 复盘型发言偶尔来个 # 标题，流式过程中语法经常只吐出一半。
 */
class UIMarkdownTextTest {

    private fun spans(text: String): List<MdSpan> = parseInline(text)

    @Test
    fun `加粗被识别且标记符不残留`() {
        assertEquals(
            listOf(MdSpan("我"), MdSpan("刀 4 号", MdInlineStyle.BOLD), MdSpan("，稳")),
            spans("我**刀 4 号**，稳"),
        )
    }

    @Test
    fun `双下划线同样表示加粗`() {
        assertEquals(
            listOf(MdSpan("首验", MdInlineStyle.BOLD), MdSpan("报 7")),
            spans("__首验__报 7"),
        )
    }

    @Test
    fun `斜体与加粗能同时出现且互不串味`() {
        assertEquals(
            listOf(
                MdSpan("先", MdInlineStyle.ITALIC),
                MdSpan("别急", MdInlineStyle.BOLD),
                MdSpan("，", MdInlineStyle.NORMAL),
            ),
            spans("*先***别急**，"),
        )
    }

    @Test
    fun `行内代码用反引号且内容原样保留`() {
        assertEquals(
            listOf(MdSpan("填 "), MdSpan("null", MdInlineStyle.CODE), MdSpan(" 就行")),
            spans("填 `null` 就行"),
        )
    }

    @Test
    fun `删除线被识别`() {
        assertEquals(
            listOf(MdSpan("这刀", MdInlineStyle.STRIKE), MdSpan("改 5")),
            spans("~~这刀~~改 5"),
        )
    }

    @Test
    fun `没有任何标记时整段原样返回`() {
        assertEquals(listOf(MdSpan("今晚刀4号，守卫首夜大概率守自己")), spans("今晚刀4号，守卫首夜大概率守自己"))
    }

    @Test
    fun `流式半截的加粗标记不吞字不残留解析`() {
        // 只有开头没有结尾：正则匹配不上，整段当普通文本——星号显示出来，
        // 但正文一个字都不能少，等闭合了再突然变粗体
        assertEquals(listOf(MdSpan("**我刀")), spans("**我刀"))
        assertEquals(listOf(MdSpan("我刀**")), spans("我刀**"))
    }

    @Test
    fun `切片拼回去等于去掉标记后的原文`() {
        val raw = "**1号**悍跳，报*7号*查杀，或 `12号` 金水"
        val joined = spans(raw).joinToString("") { it.text }
        assertEquals("1号悍跳，报7号查杀，或 12号 金水", joined)
    }

    @Test
    fun `标题按层级识别`() {
        val lines = parseMarkdown("# 白天安排\n## 刀口\n普通一句")
        assertEquals(3, lines.size)
        assertEquals(MdLineKind.HEADING, lines[0].kind)
        assertEquals(1, lines[0].level)
        assertEquals(MdLineKind.HEADING, lines[1].kind)
        assertEquals(2, lines[1].level)
        assertEquals(MdLineKind.TEXT, lines[2].kind)
    }

    @Test
    fun `有序与无序列表都被识别`() {
        val lines = parseMarkdown("- 6号跳预言家\n* 1号冲锋\n1. 先报7号\n2. 再补12号")
        assertEquals(4, lines.size)
        assertEquals(MdLineKind.BULLET, lines[0].kind)
        assertEquals("•", lines[0].marker)
        assertEquals(MdLineKind.BULLET, lines[1].kind)
        assertEquals(MdLineKind.ORDERED, lines[2].kind)
        assertEquals("1.", lines[2].marker)
        assertEquals("2.", lines[3].marker)
    }

    @Test
    fun `引用与分隔线被识别`() {
        val lines = parseMarkdown("> 上帝：天亮了\n---")
        assertEquals(2, lines.size)
        assertEquals(MdLineKind.QUOTE, lines[0].kind)
        assertEquals(MdLineKind.DIVIDER, lines[1].kind)
    }

    @Test
    fun `列表标记后没空格时不误伤正文`() {
        // "1号" 不是有序列表，"-3" 不是无序列表
        val lines = parseMarkdown("1号发言\n-3 号出局")
        assertEquals(2, lines.size)
        assertEquals(MdLineKind.TEXT, lines[0].kind)
        assertEquals(MdLineKind.TEXT, lines[1].kind)
    }

    @Test
    fun `回车换行统一处理且空行被识别`() {
        val lines = parseMarkdown("甲\r\n\r\n乙")
        assertEquals(3, lines.size)
        assertEquals(MdLineKind.BLANK, lines[1].kind)
    }

    @Test
    fun `围栏代码块被拆成独立块且内部星号不被解析`() {
        val raw = "先看代码：\n```kotlin\nval x = 2 ** 3\nprintln(x)\n```\n解释完毕"
        val blocks = parseMarkdownBlocks(raw)

        assertEquals(3, blocks.size)
        assertEquals(MdBlock.Prose(parseMarkdown("先看代码：")), blocks[0])
        assertEquals(
            MdBlock.CodeBlock(language = "kotlin", code = "val x = 2 ** 3\nprintln(x)", unclosed = false),
            blocks[1],
        )
        assertEquals(MdBlock.Prose(parseMarkdown("解释完毕")), blocks[2])
    }

    @Test
    fun `流式未闭合代码块保留已输出内容并标记未闭合`() {
        val raw = "示例：\n```python\ndef hello():\n    return 1"
        val blocks = parseMarkdownBlocks(raw)

        assertEquals(2, blocks.size)
        val code = blocks[1] as MdBlock.CodeBlock
        assertEquals("python", code.language)
        assertEquals("def hello():\n    return 1", code.code)
        assertEquals(true, code.unclosed)
    }
}
