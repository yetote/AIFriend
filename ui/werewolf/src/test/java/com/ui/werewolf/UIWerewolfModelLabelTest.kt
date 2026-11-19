package com.ui.werewolf

import com.ui.werewolf.data.shortModelLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 座位卡模型名缩写的测试。
 *
 * 守的是「在 72dp 窄列里还认得出是哪个模型」这件事：
 * 聚合平台的完整 id 动辄二十几个字符，不缩写就只剩一串省略号，等于没显示。
 */
class UIWerewolfModelLabelTest {

    @Test
    fun `自家短名原样保留`() {
        assertEquals("qwen3.8-flash", shortModelLabel("qwen3.8-flash"))
        assertEquals("doubao-seed-2.1-lite", shortModelLabel("doubao-seed-2.1-lite"))
        assertEquals("MiniMax-M3", shortModelLabel("MiniMax-M3"))
    }

    @Test
    fun `聚合后缀与组织段去掉，只留可辨识的模型名`() {
        assertEquals("deepseek-chat", shortModelLabel("deepseek/deepseek-chat:free"))
        assertEquals("Hunyuan-MT-7B", shortModelLabel("tencent/Hunyuan-MT-7B"))
        assertEquals("grok-pro", shortModelLabel("x-ai/grok-pro"))
        assertEquals("deepseek-chat", shortModelLabel("deepseek/deepseek-chat"))
    }

    @Test
    fun `人座与空值不显示，超长名截断`() {
        assertEquals("", shortModelLabel(""))
        assertEquals("", shortModelLabel("  "))
        assertEquals("", shortModelLabel("human"))
        val long = shortModelLabel("a-very-long-provider/super-long-model-name-that-overflows")
        assertEquals("真正被截断", true, long.length <= 22)
    }
}
