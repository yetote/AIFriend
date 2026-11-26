package com.core.repository.settings

import com.core.repository.storage.AICredentials
import com.core.repository.storage.CustomApiConfig
import com.core.repository.storage.CustomApiGroup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomGroupCodecTest {

    @After
    fun tearDown() {
        AICredentials.replaceAll(emptyList())
    }

    @Test
    fun `多分组模型编码与解码保持无损且支持含冒号和斜杠的模型名`() {
        val encoded = CustomGroupCodec.encode("g_vip", "anthropic/claude-sonnet-4:thinking")
        assertEquals("@g_vip::anthropic/claude-sonnet-4:thinking", encoded)

        val decoded = CustomGroupCodec.decode(encoded)
        assertEquals("g_vip", decoded.groupId)
        assertEquals("anthropic/claude-sonnet-4:thinking", decoded.rawModelId)

        // 重复编码不会叠加前缀
        assertEquals(encoded, CustomGroupCodec.encode("g_vip", encoded))
    }

    @Test
    fun `未编码模型名原样返回且 groupId 为 null`() {
        val decoded = CustomGroupCodec.decode("deepseek-ai/DeepSeek-V3:free")
        assertNull(decoded.groupId)
        assertEquals("deepseek-ai/DeepSeek-V3:free", decoded.rawModelId)
    }

    @Test
    fun `Base URL 归一化忽略大小写与末尾斜杠`() {
        assertEquals(
            CustomGroupCodec.normalizeBaseUrl("https://API.Example.com/v1/"),
            CustomGroupCodec.normalizeBaseUrl("  https://api.example.com/v1  "),
        )
    }

    @Test
    fun `单分组不加前缀，多分组自动按组编码区分同名模型`() {
        val single = CustomApiConfig(
            id = "custom_1",
            name = "星云中转",
            apiKey = "sk-a",
            baseUrl = "https://api.xingyun.com/v1",
            models = listOf("gpt-4o", "claude-sonnet-4"),
        )
        assertEquals(listOf("gpt-4o", "claude-sonnet-4"), single.aggregatedModelIds())

        val multi = single.copy(
            groups = listOf(
                CustomApiGroup(id = "g1", name = "默认", apiKey = "sk-default", models = listOf("gpt-4o", "claude-sonnet-4")),
                CustomApiGroup(id = "g2", name = "VIP", apiKey = "sk-vip", models = listOf("claude-sonnet-4", "gemini-2.5-pro")),
            )
        )
        assertEquals(
            listOf(
                "@g1::gpt-4o",
                "@g1::claude-sonnet-4",
                "@g2::claude-sonnet-4",
                "@g2::gemini-2.5-pro",
            ),
            multi.aggregatedModelIds(),
        )
    }

    @Test
    fun `AICredentials 按模型所属分组精确路由 API Key 并兼容无前缀老好友`() {
        val config = CustomApiConfig(
            id = "custom_relay",
            name = "星云中转",
            baseUrl = "https://api.xingyun.com/v1",
            groups = listOf(
                CustomApiGroup(id = "g1", name = "默认", apiKey = "sk-default", models = listOf("gpt-4o", "claude-sonnet-4")),
                CustomApiGroup(id = "g2", name = "VIP", apiKey = "sk-vip", models = listOf("claude-sonnet-4", "o3")),
            ),
        )
        AICredentials.put(config.toCredential())

        // 同名模型按编码分组走不同 Key
        assertEquals("sk-default", AICredentials.apiKeyFor("custom_relay", "@g1::claude-sonnet-4"))
        assertEquals("sk-vip", AICredentials.apiKeyFor("custom_relay", "@g2::claude-sonnet-4"))
        // 无前缀老好友：只在 g2 的模型仍能命中 g2 的 Key
        assertEquals("sk-vip", AICredentials.apiKeyFor("custom_relay", "o3"))
        // 无前缀老好友：同时在 g1/g2 的模型命中首个包含它的分组
        assertEquals("sk-default", AICredentials.apiKeyFor("custom_relay", "claude-sonnet-4"))
    }
}
