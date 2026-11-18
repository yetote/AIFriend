package com.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 404 的文案要能落到「哪个模型、下一步做什么」。
 *
 * 用户手上的角色可能是批量建出来的十几个，只说「接口或模型不存在」等于让他
 * 挨个去试；而这类 404 大半不是 ID 写错，是模型没在控制台开通。
 */
class ModelHintTest {

    @Test
    fun `404 会点名模型并给出下一步`() {
        val message = withModelHint("接口或模型不存在（404），请检查模型 ID 或开通状态", "豆包", "qwen3-0-6b-20250429")

        assertTrue(message, message.contains("qwen3-0-6b-20250429"))
        assertTrue(message, message.contains("豆包"))
        assertTrue(message, message.contains("开通"))
    }

    @Test
    fun `非 404 的错误不加尾巴`() {
        val message = withModelHint("API Key 无效或已过期，请检查配置", "豆包", "doubao-seed-evolving")

        assertEquals("API Key 无效或已过期，请检查配置", message)
        assertFalse(message.contains("doubao-seed-evolving"))
    }

    @Test
    fun `没有模型名时保持原样`() {
        val original = "接口或模型不存在（404），请检查模型 ID 或开通状态"

        assertEquals(original, withModelHint(original, "豆包", null))
        assertEquals(original, withModelHint(original, null, "  "))
    }

    @Test
    fun `没有品牌时只说模型`() {
        val message = withModelHint("请求失败（404）", null, "hy4")

        assertTrue(message, message.contains("「hy4」"))
        assertFalse(message, message.contains("null"))
    }
}
