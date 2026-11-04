package com.core.ai.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 锁定 OpenAI 兼容的 Chat Completions 报文契约（DeepSeek 与豆包共用），
 * 防止请求体再退回 /responses 的 {model,input} 写法。
 */
class ChatCompletionContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `request body follows chat completions contract`() {
        val body = ChatCompletionRequest(
            model = "deepseek-flash",
            messages = listOf(
                ChatMessage.system("你是「小谋」，产品策略顾问。"),
                ChatMessage.user("帮我起个名字")
            )
        )

        val element = Json.parseToJsonElement(json.encodeToString(body)).jsonObject

        assertEquals("deepseek-flash", element["model"]!!.jsonPrimitive.content)
        val messages = element["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("你是「小谋」，产品策略顾问。", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("帮我起个名字", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `thinking-off params are encoded per provider`() {
        val zhipu = ChatCompletionRequest(
            model = "glm-5.3-flash",
            messages = listOf(ChatMessage.user("hi")),
            reasoningEffort = "low"
        )
        val zhipuJson = Json.parseToJsonElement(json.encodeToString(zhipu)).jsonObject
        assertEquals("low", zhipuJson["reasoning_effort"]!!.jsonPrimitive.content)
        assertEquals(null, zhipuJson["thinking"])

        val qwen = ChatCompletionRequest(
            model = "qwen3.8-flash",
            messages = listOf(ChatMessage.user("hi")),
            enableThinking = false
        )
        val qwenJson = Json.parseToJsonElement(json.encodeToString(qwen)).jsonObject
        assertEquals(false, qwenJson["enable_thinking"]!!.jsonPrimitive.boolean)

        val kimi = ChatCompletionRequest(
            model = "kimi-k3.1-pro",
            messages = listOf(ChatMessage.user("hi")),
            thinking = ThinkingConfig("disabled")
        )
        val kimiJson = Json.parseToJsonElement(json.encodeToString(kimi)).jsonObject
        assertEquals(
            "disabled",
            kimiJson["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun `response parses assistant content and reasoning`() {
        val raw = """
            {
              "id": "chat-1",
              "object": "chat.completion",
              "model": "doubao-seed-evolving",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "就叫「小谋」吧",
                    "reasoning_content": "先分析用户需求……"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
            }
        """.trimIndent()

        val response = json.decodeFromString<ChatCompletionResponse>(raw)

        assertEquals(1, response.choices.size)
        assertEquals("就叫「小谋」吧", response.choices.first().message.content)
        assertEquals("先分析用户需求……", response.choices.first().message.reasoningContent)
        assertEquals("stop", response.choices.first().finishReason)
    }
}
