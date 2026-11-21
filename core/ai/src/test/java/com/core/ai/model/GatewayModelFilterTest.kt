package com.core.ai.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 聚合网关（硅基流动等）的模型列表解析。
 *
 * 这些平台的 `/models` 里混着向量、重排、语音、图像模型，
 * 直接展示会让用户选中一个根本不能聊的模型，然后在聊天页报错。
 */
class GatewayModelFilterTest {

    private val json = Json { ignoreUnknownKeys = true }

    /*
     * 过滤会先看 LivePriceCache 里平台标注的非对话名单，而它是进程级单例。
     * 别的测试类塞过名单就会改变这里的判断，所以每个用例先清空，
     * 测的才是「没有平台名单时纯靠名字判断」的行为。
     */
    @org.junit.Before
    fun resetCache() {
        LivePriceCache.clear()
    }

    @Test
    fun `parses openai compatible model list`() {
        val raw = """
            {
              "object": "list",
              "data": [
                {"id": "deepseek-ai/DeepSeek-V3", "object": "model", "owned_by": "deepseek-ai"},
                {"id": "Qwen/Qwen2.5-72B-Instruct", "object": "model", "owned_by": "Qwen"}
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<OpenAIModelResponse>(raw)

        assertEquals(2, response.modelList.size)
        assertEquals("deepseek-ai/DeepSeek-V3", response.modelList.first().id)
        assertEquals(
            listOf("deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B-Instruct"),
            response.modelList.toSelectableChatModels()
        )
    }

    @Test
    fun `drops embedding rerank and speech models`() {
        val raw = """
            {
              "data": [
                {"id": "BAAI/bge-large-zh-v1.5"},
                {"id": "BAAI/bge-reranker-v2-m3"},
                {"id": "FunAudioLLM/CosyVoice2-0.5B"},
                {"id": "Kwai-Kolors/Kolors"},
                {"id": "deepseek-ai/DeepSeek-V3"},
                {"id": "Qwen/Qwen2.5-72B-Instruct"}
              ]
            }
        """.trimIndent()

        val models = json.decodeFromString<OpenAIModelResponse>(raw)
            .modelList
            .toSelectableChatModels()

        assertEquals(
            listOf("deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B-Instruct"),
            models
        )
    }

    @Test
    fun `keeps unknown models instead of guessing them away`() {
        // 新模型的名字往往不在任何白名单里。宁可多列一个，
        // 也不要因为没认出来就把用户想用的模型藏起来。
        val raw = """
            {"data": [{"id": "some-new-lab/unknown-model-2027"}]}
        """.trimIndent()

        val models = json.decodeFromString<OpenAIModelResponse>(raw)
            .modelList
            .toSelectableChatModels()

        assertEquals(listOf("some-new-lab/unknown-model-2027"), models)
    }

    @Test
    fun `deduplicates and sorts case insensitively`() {
        val raw = """
            {"data": [
              {"id": "Qwen/Qwen2.5-72B"},
              {"id": "deepseek-ai/DeepSeek-V3"},
              {"id": "Qwen/Qwen2.5-72B"}
            ]}
        """.trimIndent()

        val models = json.decodeFromString<OpenAIModelResponse>(raw)
            .modelList
            .toSelectableChatModels()

        assertEquals(
            listOf("deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B"),
            models
        )
    }

    @Test
    fun `blank ids are ignored`() {
        val raw = """{"data": [{"id": ""}, {"id": "   "}, {"id": "glm-4-flash"}]}"""

        val models = json.decodeFromString<OpenAIModelResponse>(raw)
            .modelList
            .toSelectableChatModels()

        assertEquals(listOf("glm-4-flash"), models)
    }

    @Test
    fun `isLikelyChatModel flags obvious non chat models`() {
        assertFalse(isLikelyChatModel("text-embedding-3-large"))
        assertFalse(isLikelyChatModel("whisper-large-v3"))
        assertFalse(isLikelyChatModel("stable-diffusion-xl"))
        assertTrue(isLikelyChatModel("deepseek-chat"))
        assertTrue(isLikelyChatModel("glm-4-flash"))
    }

    @Test
    fun `missing data field degrades to empty list`() {
        val response = json.decodeFromString<OpenAIModelResponse>("""{"object":"list"}""")

        assertEquals(emptyList<String>(), response.modelList.toSelectableChatModels())
    }

    @Test
    fun `LoRA 部署模型归到 Qwen 而不是自成一个 LoRA 组`() {
        // 真实列表里这些模型没有 owned_by，上游退化成 id 首段，
        // 于是一个叫「LoRA」的假厂商被建了出来。
        val model = GatewayModel(id = "LoRA/Qwen/Qwen2.5-7B-Instruct", vendor = "LoRA")

        assertEquals("Qwen", model.vendorLabel)
    }

    @Test
    fun `没有厂商前缀的模型归到其他而不是自己当组名`() {
        assertEquals("其他", GatewayModel(id = "Kev-4B", vendor = "Kev-4B").vendorLabel)
        assertEquals("其他", GatewayModel(id = "SemIf", vendor = "SemIf").vendorLabel)
        assertEquals("其他", GatewayModel(id = "SemIf", vendor = "").vendorLabel)
    }

    @Test
    fun `正常的 owned_by 保持不变`() {
        assertEquals(
            "deepseek-ai",
            GatewayModel(id = "deepseek-ai/DeepSeek-V3", vendor = "deepseek-ai").vendorLabel
        )
        // owned_by 带公司前缀时只取最后一段
        assertEquals(
            "Qwen",
            GatewayModel(id = "Qwen/Qwen3.5-27B", vendor = "Alibaba/Qwen").vendorLabel
        )
    }

    @Test
    fun `行内标签去掉与组名重复的前缀`() {
        assertEquals(
            "DeepSeek-V3",
            GatewayModel(id = "deepseek-ai/DeepSeek-V3", vendor = "deepseek-ai").displayName
        )
        assertEquals("Kev-4B", GatewayModel(id = "Kev-4B", vendor = "Kev-4B").displayName)
    }

    @Test
    fun `LoRA 部署保留标记以免和普通部署混同`() {
        // 同一个 Qwen2.5-7B，LoRA 部署和普通部署是两条不同的计费项
        assertEquals(
            "LoRA/Qwen2.5-7B-Instruct",
            GatewayModel(id = "LoRA/Qwen/Qwen2.5-7B-Instruct", vendor = "LoRA").displayName
        )
    }

    @Test
    fun `Pro 档保留在标签里免得和普通版撞名`() {
        // Pro/deepseek-ai/DeepSeek-V3.2 与 deepseek-ai/DeepSeek-V3.2 是两档服务，
        // 价格不同；去掉 Pro 会出现两条同名却不同价的行。
        assertEquals(
            "Pro/DeepSeek-V3.2",
            GatewayModel(id = "Pro/deepseek-ai/DeepSeek-V3.2", vendor = "deepseek-ai").displayName
        )
        assertEquals(
            "DeepSeek-V3.2",
            GatewayModel(id = "deepseek-ai/DeepSeek-V3.2", vendor = "deepseek-ai").displayName
        )
    }
}
