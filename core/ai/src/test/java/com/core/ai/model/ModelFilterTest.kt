package com.core.ai.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelFilterTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `qwen keeps chat models and drops embeddings and audio`() {
        val raw = """
            {
              "object": "list",
              "data": [
                {"id": "qwen3.8-flash", "object": "model", "created": 300},
                {"id": "qwen3.8-max", "object": "model", "created": 200},
                {"id": "qwen-audio-3.1-realtime-flash", "object": "model", "created": 100},
                {"id": "qwen3.7-text-embedding", "object": "model", "created": 50},
                {"id": "qwen3-vl-plus", "object": "model", "created": 150},
                {"id": "deepseek-v4.1-flash", "object": "model", "created": 400}
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<QianwenModelResponse>(raw)

        // 自家 Qwen 排前面；百炼代理的第三方对话模型（DeepSeek）也要保留，
        // 否则用户在控制台看得到、App 里选不到
        assertEquals(
            listOf("qwen3.8-flash", "qwen3.8-max", "qwen3-vl-plus", "deepseek-v4.1-flash"),
            response.qianwenModelList.toChatModelIds()
        )
    }

    @Test
    fun `qianwen drops non chat models that are not qwen`() {
        val raw = """
            {
              "object": "list",
              "data": [
                {"id": "qwen3.8-max", "object": "model", "created": 300},
                {"id": "wan2.7-image-pro", "object": "model", "created": 400},
                {"id": "Tripo/Tripo-H3.1", "object": "model", "created": 500},
                {"id": "paraformer-realtime-v2", "object": "model", "created": 600},
                {"id": "text-embedding-v4", "object": "model", "created": 700},
                {"id": "kimi/kimi-k3", "object": "model", "created": 200}
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<QianwenModelResponse>(raw)

        // 图像 / 3D / 语音 / 向量都不该出现，第三方对话模型 kimi 要留着
        assertEquals(
            listOf("qwen3.8-max", "kimi/kimi-k3"),
            response.qianwenModelList.toChatModelIds()
        )
    }

    @Test
    fun `gemini strips models prefix and drops embeddings`() {
        val raw = """
            {
              "object": "list",
              "data": [
                {"id": "models/gemini-2.5-flash", "object": "model"},
                {"id": "models/gemini-2.5-pro", "object": "model"},
                {"id": "models/text-embedding-004", "object": "model"},
                {"id": "models/gemini-embedding-001", "object": "model"},
                {"id": "models/gemini-3.8-live", "object": "model"},
                {"id": "models/gemini-omni-1.1-flash", "object": "model"}
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<GeminiModelResponse>(raw)

        assertEquals(
            listOf("gemini-2.5-flash", "gemini-2.5-pro"),
            response.geminiModelList.toChatModelIds()
        )
    }
}
