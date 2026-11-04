package com.core.ai.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 方舟 /models 返回的模型很杂（图片、视频、向量、第三方），必须只挑出可对话的豆包模型。
 */
class DoubaoModelFilterTest {

    @Test
    fun `only published doubao chat models are selectable`() {
        val raw = """
            {
              "object": "list",
              "data": [
                {"id": "doubao-a", "object": "model", "status": "Published", "created": 3, "api_supported": {"chat/completions": true}},
                {"id": "doubao-retiring", "object": "model", "status": "Retiring", "created": 5, "api_supported": {"chat/completions": true}},
                {"id": "doubao-embedding", "object": "model", "status": "Published", "created": 4, "api_supported": {"embeddings": true}},
                {"id": "kimi-x", "object": "model", "status": "Published", "created": 6, "api_supported": {"chat/completions": true}},
                {"id": "doubao-c", "object": "model", "status": "Published", "created": 1, "api_supported": {"chat/completions": true}}
              ]
            }
        """.trimIndent()

        val response = Json { ignoreUnknownKeys = true }
            .decodeFromString<DoubaoModelResponse>(raw)

        assertEquals(listOf("doubao-a", "doubao-c"), response.doubaoModelList.toChatModelIds())
    }
}
