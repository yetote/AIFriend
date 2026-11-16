package com.core.ai.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 智谱 /models 的过滤规则。
 *
 * 关键约束是大小写：智谱定价页把免费档写作 `GLM-4.7-Flash`（大写 G），
 * 而 `/models` 返回的 id 大小写并不稳定。过滤条件曾经是大小写敏感的
 * `startsWith("glm-")`，一旦上游改大写，整张列表会被滤成空，
 * 用户看到的是兜底的那几个付费模型 —— 免费的反而调不到，还查不出原因。
 */
class ZhipuModelFilterTest {

    private fun idsOf(raw: String): List<String> =
        Json { ignoreUnknownKeys = true }
            .decodeFromString<ZhipuModelResponse>(raw)
            .zhipuModelList
            .toChatModelIds()

    @Test
    fun `小写 id 正常保留`() {
        val raw = """
            {"object": "list", "data": [
              {"id": "glm-5.3", "object": "model"},
              {"id": "glm-4.7-flash", "object": "model"}
            ]}
        """.trimIndent()

        assertEquals(listOf("glm-5.3", "glm-4.7-flash"), idsOf(raw))
    }

    @Test
    fun `大写 id 不能被滤掉`() {
        val raw = """
            {"object": "list", "data": [
              {"id": "GLM-4.7-Flash", "object": "model"},
              {"id": "GLM-5.3", "object": "model"}
            ]}
        """.trimIndent()

        assertEquals(listOf("GLM-4.7-Flash", "GLM-5.3"), idsOf(raw))
    }

    @Test
    fun `非对话模型被排除`() {
        val raw = """
            {"object": "list", "data": [
              {"id": "glm-4.7-flash", "object": "model"},
              {"id": "embedding-3", "object": "model"},
              {"id": "glm-ocr", "object": "model"}
            ]}
        """.trimIndent()

        val ids = idsOf(raw)
        // glm-ocr 前缀仍是 glm-，会被保留；但 embedding 不该出现
        assertTrue("向量模型不该出现在对话列表里：$ids", ids.none { it.contains("embedding") })
        assertTrue("免费档不该被丢掉：$ids", ids.contains("glm-4.7-flash"))
    }
}
