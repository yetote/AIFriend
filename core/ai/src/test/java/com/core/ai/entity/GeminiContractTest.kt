package com.core.ai.entity

import com.core.ai.model.ChatMessage
import com.core.ai.model.ResponseFormat
import com.core.repository.settings.AIProviderCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Google AI Studio（Gemini / Gemma 4）OpenAI 兼容层的请求契约测试。
 *
 * 重点锁住三条线：
 * 1. 支持原生 `response_format = {"type": "json_object"}`（狼人杀结构化决策不再降级盲猜）；
 * 2. 关闭深度思考时按代际安全映射 `reasoning_effort`：
 *    - Gemini 2.5 Flash / Flash-Lite 用 `"none"` 彻底关思考；
 *    - Gemini 2.5 Pro / Gemini 3.x / Gemini 4.x / Gemma 4 / latest 别名用 `"low"` 降档（绝不发 `"none"` 撞 400）；
 *    - Gemini 2.0 无思考能力，不发 `reasoning_effort`；
 * 3. 目录里配置好高配额免费模型与 `gemma` 前缀，支持开关深度思考。
 */
class GeminiContractTest {

    private val gemini = AIGemini()

    private fun build(
        model: String,
        enableThinking: Boolean = true,
        jsonMode: Boolean = false,
    ) = gemini.buildRequest(
        messages = listOf(ChatMessage.user("hi")),
        model = model,
        systemPrompt = "你是测试助手",
        enableThinking = enableThinking,
        jsonMode = jsonMode,
        webSearch = false,
        stream = false,
    )

    @Test
    fun `支持原生 JSON 模式，普通聊天不带 response_format`() {
        assertTrue("Gemini 兼容层已支持 json_object", gemini.supportsJsonMode)
        assertNull(build("gemini-3.5-flash-lite", jsonMode = false).responseFormat)
        assertEquals(
            ResponseFormat.JSON_OBJECT,
            build("gemini-3.5-flash-lite", jsonMode = true).responseFormat?.type,
        )
        assertEquals(
            ResponseFormat.JSON_OBJECT,
            build("gemma-4-31b-it", jsonMode = true).responseFormat?.type,
        )
    }

    @Test
    fun `开启思考时不强行覆盖服务端默认思考深度`() {
        assertNull(build("gemini-3.8-flash", enableThinking = true).reasoningEffort)
        assertNull(build("gemini-4-hydrogen", enableThinking = true).reasoningEffort)
        assertNull(build("gemma-4-31b-it", enableThinking = true).reasoningEffort)
    }

    @Test
    fun `关闭思考时按模型代际安全映射 reasoning_effort`() {
        // 2.5 Flash / Lite 支持 none
        assertEquals("none", build("gemini-2.5-flash", enableThinking = false).reasoningEffort)
        assertEquals("none", build("gemini-2.5-flash-lite", enableThinking = false).reasoningEffort)

        // 2.5 Pro / 3.x / 4.x / Gemma 4 不支持 none，统一降到 low
        assertEquals("low", build("gemini-2.5-pro", enableThinking = false).reasoningEffort)
        assertEquals("low", build("gemini-3.5-flash-lite", enableThinking = false).reasoningEffort)
        assertEquals("low", build("gemini-3.8-flash", enableThinking = false).reasoningEffort)
        assertEquals("low", build("gemini-4-hydrogen", enableThinking = false).reasoningEffort)
        assertEquals("low", build("gemma-4-31b-it", enableThinking = false).reasoningEffort)
        assertEquals("low", build("gemma-4-26b-a4b-it", enableThinking = false).reasoningEffort)
        assertEquals("low", build("gemini-flash-latest", enableThinking = false).reasoningEffort)

        // 2.0 无思考能力，不传字段避免 400
        assertNull(build("gemini-2.0-flash", enableThinking = false).reasoningEffort)
    }

    @Test
    fun `目录配置包含免费主力模型与 Gemma 4 且支持思考开关`() {
        val provider = AIProviderCatalog.Gemini
        assertTrue(provider.thinkingSwitchable)
        assertTrue(provider.ownModelPrefixes.containsAll(listOf("gemini", "gemma")))
        assertTrue(provider.fallbackModels.contains("gemini-3.5-flash-lite"))
        assertTrue(provider.fallbackModels.contains("gemini-4-hydrogen"))
        assertTrue(provider.fallbackModels.contains("gemma-4-31b-it"))
        assertTrue(provider.fallbackModels.contains("gemma-4-26b-a4b-it"))
    }
}
