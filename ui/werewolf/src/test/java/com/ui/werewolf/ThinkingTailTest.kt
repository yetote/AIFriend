package com.ui.werewolf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 思考气泡取尾的测试。
 *
 * 这里守的是**观感**而不是规则：气泡内容必须永远停在「最新想到的地方」。
 * 以前的实现按长度整行筛，正在生长的句子一超标就被删，窗口于是退回上一句——
 * 用户看到的是气泡自己往回翻页。这几条用例就是把这个回归钉住。
 */
class ThinkingTailTest {

    @Test
    fun `流式追加时，尾巴永远是当前文本的结尾`() {
        val full = "我觉得五号在强推一号，这个位置很不干净。" +
            "而且他上一轮认的票和发言对不上，" +
            "先记着，等会儿再听他怎么说。"
        for (i in 1..full.length) {
            val text = full.substring(0, i)
            val lines = ThinkingTail.lines(text)
            assertTrue("第 $i 字时什么都不显示", lines.isNotEmpty())
            assertTrue(
                "第 $i 字时显示的不是最新内容：$lines",
                text.endsWith(lines.last()),
            )
        }
    }

    @Test
    fun `正在生长的那一句不会在同一次思考里消失`() {
        // 单行、无换行、无标点：以前一行超过 60 字就会被整行丢掉，
        // 气泡于是空回去，等下一行出现再跳回来
        val line = "五号的发言前后矛盾".repeat(8)
        for (len in 40..line.length) {
            val lines = ThinkingTail.lines(line.substring(0, len))
            assertTrue("第 $len 字时气泡空了", lines.isNotEmpty())
            assertTrue(
                "第 $len 字时没有停在最新处：$lines",
                line.substring(0, len).endsWith(lines.last()),
            )
        }
    }

    @Test
    fun `相邻两帧内容只向前，不回退`() {
        val full = "先听一号怎么解释昨晚的刀口，再说投票的事。他要是还在保三号，那就基本对上了。"
        var prevLast = ""
        for (i in 1..full.length) {
            val lines = ThinkingTail.lines(full.substring(0, i))
            val cur = lines.joinToString("")
            if (prevLast.isNotEmpty()) {
                // 上一帧末段的最后两个字必须还在：整段被换掉就说明窗口往回跳了
                val tail = prevLast.takeLast(2)
                assertTrue("第 $i 字时回退了（$tail 不见了）：$lines", cur.contains(tail))
            }
            prevLast = lines.last()
        }
    }

    @Test
    fun `长内容只留尾巴，不会把气泡撑爆`() {
        val sentence = "一号有点急。" + "二号还在装。" + "三号换票了。" + "四号一直没说话。" +
            "五号被推之后开始乱咬。" + "六号的票最干净。" + "七号在跟风。" + "八号是好人。"
        val long = sentence.repeat(3)
        val lines = ThinkingTail.lines(long)
        assertTrue("气泡最多 4 行，实际 ${lines.size} 行：$lines", lines.size <= 4)
        // 但最新那一句必须在
        assertTrue("最新内容被挤掉了：$lines", long.endsWith(lines.last()))
    }

    @Test
    fun `多行思考每行各自独立，不会串行`() {
        val lines = ThinkingTail.lines("第一句。\n第二句。\n第三句。")
        assertEquals(listOf("第一句。", "第二句。", "第三句。"), lines)
    }

    @Test
    fun `空内容和纯空白不画东西`() {
        assertEquals(emptyList<String>(), ThinkingTail.lines(""))
        assertEquals(emptyList<String>(), ThinkingTail.lines("   \n\n  "))
    }

    @Test
    fun `优先在标点处断开，不把句子切成碎片`() {
        // 这一整句 22 个字超过一行（16 字），但第一个逗号在第 12 个字，
        // 应该在逗号后断，而不是在第 16 个字上硬切
        val lines = ThinkingTail.lines("我觉得这个位置很不干净，先记下来，等会儿再说")
        assertEquals(2, lines.size)
        assertEquals("我觉得这个位置很不干净，", lines.first())
        assertEquals("先记下来，等会儿再说", lines.last())
    }
}
