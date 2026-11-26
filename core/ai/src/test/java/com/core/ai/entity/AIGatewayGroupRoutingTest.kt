package com.core.ai.entity

import com.core.ai.model.ChatMessage
import com.core.ai.model.GatewayModel
import com.core.repository.settings.AIProvider
import com.core.repository.storage.AICredential
import com.core.repository.storage.AICredentialGroup
import com.core.repository.storage.AICredentials
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AIGatewayGroupRoutingTest {

    @After
    fun tearDown() {
        AICredentials.replaceAll(emptyList())
    }

    @Test
    fun `AIGateway 发请求前剥离分组前缀并保持真实模型名`() {
        val provider = AIProvider(
            id = "custom_relay",
            name = "星云中转",
            description = "测试中转站",
            baseUrl = "https://api.xingyun.com/v1",
            consoleUrl = "",
            requiresKey = false,
            isGateway = true,
        )
        AICredentials.put(
            AICredential(
                providerId = "custom_relay",
                apiKey = "sk-default",
                baseUrl = "https://api.xingyun.com/v1",
                groups = listOf(
                    AICredentialGroup(id = "g1", name = "默认", apiKey = "sk-default", models = listOf("claude-sonnet-4")),
                    AICredentialGroup(id = "g2", name = "VIP", apiKey = "sk-vip", models = listOf("claude-sonnet-4")),
                ),
            )
        )

        val gateway = AIGateway(provider)
        val req = gateway.buildRequest(
            messages = listOf(ChatMessage.user("hi")),
            model = "@g2::claude-sonnet-4",
            systemPrompt = null,
            enableThinking = false,
            jsonMode = false,
            webSearch = false,
            stream = false,
        )
        assertEquals("claude-sonnet-4", req.model)
    }

    @Test
    fun `GatewayModel 展示名与分组名自动剥离内部编码前缀`() {
        val m1 = GatewayModel(id = "@g2::claude-sonnet-4", vendor = "VIP")
        assertEquals("VIP", m1.vendorLabel)
        assertEquals("claude-sonnet-4", m1.displayName)

        val m2 = GatewayModel(id = "@g1::Pro/deepseek-ai/DeepSeek-V3", vendor = "默认")
        assertEquals("默认", m2.vendorLabel)
        assertEquals("Pro/deepseek-ai/DeepSeek-V3", m2.displayName)
    }
}
