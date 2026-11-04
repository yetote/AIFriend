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

        assertEquals(
            listOf("qwen3.8-flash", "qwen3.8-max", "qwen3-vl-plus"),
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
