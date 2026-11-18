package com.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 火山 V3 的帧切分。
 *
 * 这一层错的表现是「朗读偶尔缺字」，而且只在长文本、网络分块刚好落在中间时出现，
 * 极难复现——所以边界情况要在这里按字节摆出来测。
 */
class JsonFrameSplitterTest {

    private fun frames(vararg chunks: ByteArray): List<String> =
        JsonFrameSplitter().let { splitter -> chunks.flatMap { splitter.feed(it) } }

    private fun String.bytes() = toByteArray(Charsets.UTF_8)

    @Test
    fun `一帧完整数据直接切出来`() {
        val json = """{"code":0,"message":"","data":"AAAA"}"""
        assertEquals(listOf(json), frames(json.bytes()))
    }

    @Test
    fun `帧被 HTTP 分块切开会等齐再吐`() {
        val json = """{"code":0,"message":"","data":"AAAABBBB"}"""
        val bytes = json.bytes()
        val splitter = JsonFrameSplitter()

        // 前半段还凑不成完整 JSON，不该吐任何东西
        assertTrue(splitter.feed(bytes.copyOfRange(0, bytes.size / 2)).isEmpty())
        assertEquals(listOf(json), splitter.feed(bytes.copyOfRange(bytes.size / 2, bytes.size)))
    }

    @Test
    fun `一帧里同时含字符串中的括号和逗号`() {
        // 字符串里的 {} 不能参与层级计数，否则会把一帧切成两半
        val json = """{"code":0,"message":"a{b}c","data":"AA"}"""
        assertEquals(listOf(json), frames(json.bytes()))
    }

    @Test
    fun `转义引号不会提前结束字符串`() {
        val json = """{"code":0,"message":"他说\"好\"","data":"AA"}"""
        assertEquals(listOf(json), frames(json.bytes()))
    }

    @Test
    fun `多帧连着或换行分隔都能切`() {
        val a = """{"code":0,"data":"AA"}"""
        val b = """{"code":0,"data":"BB"}"""
        val c = """{"code":20000000,"message":"ok","data":null}"""
        // NDJSON（换行分隔）和直接相连两种形态混着来
        assertEquals(listOf(a, b, c), frames("$a\n$b$c\n".bytes()))
    }

    @Test
    fun `中文字符被切在两个分块之间也不乱码`() {
        // UTF-8 一个汉字三字节，HTTP 分块完全可能切在它中间；
        // 先解码再拼接就会补出「�」——这就是「偶尔缺字」的来源。
        val json = """{"code":0,"message":"合成完成","data":"AA"}"""
        val bytes = json.bytes()
        val cut = (0 until bytes.size).first { (bytes[it].toInt() and 0xC0) == 0x80 } + 1
        val splitter = JsonFrameSplitter()
        val first = splitter.feed(bytes.copyOfRange(0, cut))
        val second = splitter.feed(bytes.copyOfRange(cut, bytes.size))
        assertEquals(emptyList<String>(), first)
        assertEquals(listOf(json), second)
    }

    @Test
    fun `帧外的空白不会混进结果`() {
        val json = """{"code":0,"data":"AA"}"""
        assertEquals(listOf(json), frames("\r\n  $json  \n\n".bytes()))
    }
}
