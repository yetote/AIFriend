package com.core.ai.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 锁定 DeepSeek Chat Completions 的报文契约，防止请求体再退回 /responses 的 {model,input} 写法。
 */
class DeepSeekContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `request body follows chat completions contract`() {
        val body = DeepSeekChatRequest(
            model = "deepseek-chat",
            messages = listOf(
                ChatMessage.system("你是「小谋」，产品策略顾问。"),
                ChatMessage.user("帮我起个名字")
            )
        )

        val element = Json.parseToJsonElement(json.encodeToString(body)).jsonObject

        assertEquals("deepseek-chat", element["model"]!!.jsonPrimitive.content)
        val messages = element["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("你是「小谋」，产品策略顾问。", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("帮我起个名字", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `response parses assistant content`() {
        val raw = """
            {
              "id": "chat-1",
              "object": "chat.completion",
              "model": "deepseek-chat",
              "choices": [
                {
                  "index": 0,
                  "message": {"role": "assistant", "content": "就叫「小谋」吧"},
                  "finish_reason": "stop"
                }
              ],
              "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
            }
        """.trimIndent()

        val response = json.decodeFromString<DeepSeekCompletionResponse>(raw)

        assertEquals(1, response.choices.size)
        assertEquals("就叫「小谋」吧", response.choices.first().message.content)
        assertEquals("stop", response.choices.first().finishReason)
    }
}
