package com.ui.agent

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 角色列表里的模型标签压缩。
 *
 * 这组用例来自真实数据：设备上的默认服务商是硅基流动，
 * 模型 id 形如 `Pro/deepseek-ai/DeepSeek-V3.1-Terminus`，
 * 原样渲染会把角色名挤变形。
 */
class ShortModelTagTest {

    @Test
    fun `聚合平台的多段 id 只留最后一段`() {
        assertEquals(
            "DeepSeek-V3.1-Terminus",
            shortModelTag("Pro/deepseek-ai/DeepSeek-V3.1-Terminus")
        )
        assertEquals("Kimi-K2.7-Code", shortModelTag("moonshotai/Kimi-K2.7-Code"))
        assertEquals("Qwen3.6-27B", shortModelTag("Qwen/Qwen3.6-27B"))
        assertEquals("GLM-5.3", shortModelTag("zai-org/GLM-5.3"))
    }

    @Test
    fun `官方厂商的短 id 原样保留`() {
        assertEquals("deepseek-flash", shortModelTag("deepseek-flash"))
        assertEquals("glm-4.6", shortModelTag("glm-4.6"))
        assertEquals("gemini-2.5-flash", shortModelTag("gemini-2.5-flash"))
    }

    @Test
    fun `单段 id 不带斜杠时不被截断`() {
        assertEquals("DeepSeek-V4-Pro", shortModelTag("DeepSeek-V4-Pro"))
    }

    @Test
    fun `空值和纯空白返回空串`() {
        assertEquals("", shortModelTag(""))
        assertEquals("", shortModelTag("   "))
    }

    @Test
    fun `前后空白被去掉`() {
        assertEquals("Kimi-K2", shortModelTag("  moonshotai/Kimi-K2  "))
    }

    @Test
    fun `末尾斜杠不会产生空标签`() {
        // 理论上不该出现，但真出现了要退回到原串，不能渲染成空 tag
        assertEquals("abc/", shortModelTag("abc/"))
    }

    @Test
    fun `压缩后的长度不超过渲染上限所需的字符数`() {
        // TAG_MAX_WIDTH = 104dp / 10sp，实际可容纳约 20 个字符；
        // 这里锁住真实数据里最长的那一批，超出就会被省略号截断。
        val real = listOf(
            "Pro/deepseek-ai/DeepSeek-V3.1-Terminus",
            "deepseek-ai/DeepSeek-V4.1-Flash",
            "moonshotai/Kimi-K2.7-Code",
            "inclusionAI/Ling-flash-2.0",
            "PaddlePaddle/PaddleOCR-VL-1.5"
        )
        real.forEach { id ->
            val tag = shortModelTag(id)
            assert(tag.length <= 22) { "$id -> $tag 太长（${tag.length}）" }
        }
    }
}
