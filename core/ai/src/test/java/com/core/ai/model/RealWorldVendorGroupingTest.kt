package com.core.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用百炼 / 方舟页面上真实出现的 id 形态跑一遍分组，确认不会出现
 * 「拿模型名当分组标题」这种假分组，也不会把非对话模型放进来。
 */
class RealWorldVendorGroupingTest {

    @Test
    fun `百炼真实 id 的分组结果`() {
        // 取自阿里云百炼「选择模型」页面上的 id 写法
        val ids = listOf(
            "qwen3.8-max",
            "qwen3.7-plus",
            "qwen3.8-flash",
            "deepseek-v4-pro-0813",
            "deepseek-v4.1-flash",
            "kimi/kimi-k3",
            "kimi-k3",
            "glm-5.3",
            "ZHIPU/GLM-5.3",
            "MiniMax-M3.1-Flash",
            "mimo-v2.6-pro-ultraspeed"
        )

        val grouped = ids.groupBy { vendorLabelOf(it, "通义千问") }

        assertEquals(listOf("qwen3.8-max", "qwen3.7-plus", "qwen3.8-flash"), grouped["通义千问"])
        assertEquals(listOf("deepseek-v4-pro-0813", "deepseek-v4.1-flash"), grouped["DeepSeek"])
        assertEquals(listOf("kimi/kimi-k3", "kimi-k3"), grouped["Kimi"])
        assertEquals(listOf("glm-5.3", "ZHIPU/GLM-5.3"), grouped["GLM"])
        assertEquals(listOf("MiniMax-M3.1-Flash"), grouped["MiniMax"])
        assertEquals(listOf("mimo-v2.6-pro-ultraspeed"), grouped["小米"])

        // 关键：没有任何一个分组标题是「模型名」
        val fakeGroups = grouped.keys.filter { key -> key.contains("-") && !key.contains("/") }
        assertTrue("出现了以模型名当标题的假分组: $fakeGroups", fakeGroups.isEmpty())
    }

    @Test
    fun `方舟真实 id 的分组结果`() {
        val ids = listOf(
            "doubao-seed-evolving",
            "doubao-seed-2-1-pro-260915",
            "deepseek-v4.1-flash"
        )

        val grouped = ids.groupBy { vendorLabelOf(it, "豆包") }

        assertEquals(
            listOf("doubao-seed-evolving", "doubao-seed-2-1-pro-260915"),
            grouped["豆包"]
        )
        assertEquals(listOf("deepseek-v4.1-flash"), grouped["DeepSeek"])
    }
}
