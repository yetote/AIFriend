package com.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日志开到 ALL 之后，所有输出都会经过 [redact]。
 *
 * 这里守两件事：**不能把 Key 打进 logcat**，**也不能为了打码把整行信息毁掉**
 * （讯飞鉴权参数就挂在 URL query 上，掩码吃过头会把 date 一起吞了，反而没法排查）。
 */
class RedactTest {

    @Test
    fun `Bearer token 被掩码且保留头尾`() {
        val out = redact("-> Authorization: Bearer sk-abcdefghijklmnop1234")

        assertFalse(out, out.contains("sk-abcdefghijklmnop1234"))
        assertTrue(out, out.contains("Bearer sk-a…1234"))
    }

    @Test
    fun `X-Api-Key 被掩码`() {
        val out = redact("-> X-Api-Key: 9f8e7d6c-5b4a-3210-fedc-ba9876543210")

        assertFalse(out, out.contains("9f8e7d6c-5b4a-3210"))
        // 留头尾是为了分辨「新版 UUID / 旧版 token」，全打 *** 就分辨不了
        assertTrue(out, out.contains("9f8e…3210"))
    }

    @Test
    fun `URL query 里的 authorization 不会吃掉后面的参数`() {
        val out = redact(
            "REQUEST: wss://tts-api.xfyun.cn/v2/tts?authorization=YXBpX2tleT0ieHl6Ig==" +
                "&date=Wed%2C+18+Nov+2026+13%3A52%3A05+GMT&host=tts-api.xfyun.cn"
        )

        assertFalse(out, out.contains("YXBpX2tleT0ieHl6Ig=="))
        assertTrue(out, out.contains("date=Wed"))
        assertTrue(out, out.contains("host=tts-api.xfyun.cn"))
    }

    @Test
    fun `JSON body 里的字段被掩码`() {
        val out = redact("""{"api_key":"1234567890abcdef","model":"gpt-4o"}""")

        assertFalse(out, out.contains("1234567890abcdef"))
        assertTrue(out, out.contains("gpt-4o"))
    }

    @Test
    fun `短值直接打星`() {
        val out = redact("api_key: abc123")

        assertFalse(out, out.contains("abc123"))
        assertTrue(out, out.contains("***"))
    }

    @Test
    fun `超长内容被截断并注明`() {
        val out = redact("A".repeat(10_000))

        assertTrue(out.length < 3_700)
        assertTrue(out, out.contains("已截断"))
    }
}
