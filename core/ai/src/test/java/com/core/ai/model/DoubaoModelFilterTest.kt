package com.core.ai.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 方舟 /models 返回的模型很杂（图片、视频、向量、第三方），必须只挑出可对话的模型。
 *
 * 早期规则还要求 id 以 `doubao-` 开头，结果把方舟代理的第三方模型一起砍了。
 * 方舟本身就是平台，用户能在那边的控制台看到 DeepSeek，App 里却选不到，
 * 会以为功能坏了 —— 所以现在只按平台元数据（api_supported / status）筛。
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

        // 自家豆包排前面；退役的、非对话的都不要；
        // 第三方对话模型 kimi-x 保留（方舟确实提供它）
        assertEquals(
            listOf("doubao-a", "doubao-c", "kimi-x"),
            response.doubaoModelList.toChatModelIds()
        )
    }

    @Test
    fun `third party models without chat support are dropped`() {
        val raw = """
            {
              "object": "list",
              "data": [
                {"id": "deepseek-v4-pro", "object": "model", "status": "Published", "api_supported": {"chat/completions": true}},
                {"id": "some-image-model", "object": "model", "status": "Published", "api_supported": {"images/generations": true}},
                {"id": "some-video-model", "object": "model", "status": "Published", "api_supported": {}},
                {"id": "doubao-seed-evolving", "object": "model", "status": "Published", "api_supported": {"chat/completions": true}}
              ]
            }
        """.trimIndent()

        val response = Json { ignoreUnknownKeys = true }
            .decodeFromString<DoubaoModelResponse>(raw)

        assertEquals(
            listOf("doubao-seed-evolving", "deepseek-v4-pro"),
            response.doubaoModelList.toChatModelIds()
        )
    }
}
