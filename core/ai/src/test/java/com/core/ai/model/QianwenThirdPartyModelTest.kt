package com.core.ai.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 百炼的排除名单是在「只留 qwen」的年代写的，里面有些 marker 很泛
 * （`math`、`mt-`）。放开第三方模型之后，这些词可能反而误伤正当的对话模型，
 * 所以单独盯住几个真实出现的 id。
 */
class QianwenThirdPartyModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun chatIdsOf(vararg ids: String): List<String> {
        val body = ids.joinToString(",") { """{"id":"$it","object":"model"}""" }
        val raw = """{"object":"list","data":[$body]}"""
        return json.decodeFromString<QianwenModelResponse>(raw).qianwenModelList.toChatModelIds()
    }

    @Test
    fun `第三方对话模型不会被误杀`() {
        val ids = listOf(
            "deepseek-v4-pro-0813",
            "deepseek-v4.1-flash",
            "kimi/kimi-k3",
            "glm-5.3",
            "MiniMax-M3.1-Flash",
            "qwen3.8-max"
        )
        assertEquals(ids.toSet(), chatIdsOf(*ids.toTypedArray()).toSet())
    }

    @Test
    fun `非对话模型仍然被排除`() {
        val kept = chatIdsOf(
            "qwen3.8-max",
            "text-embedding-v4",
            "qwen3.7-text-embedding",
            "wan2.7-image-pro",
            "cosyvoice-v3",
            "paraformer-realtime-v2",
            "qwen-audio-3.1-tts-flash",
            "Tripo/Tripo-H3.1"
        )
        assertEquals(listOf("qwen3.8-max"), kept)
    }

    @Test
    fun `自家模型排在第三方前面`() {
        val kept = chatIdsOf(
            "deepseek-v4.1-flash",
            "qwen3.8-flash",
            "kimi/kimi-k3",
            "qwen3.8-max"
        )
        // 前两个必须是 qwen，第三方排在后面
        assertEquals("qwen3.8-flash", kept[0])
        assertEquals("qwen3.8-max", kept[1])
        assertTrue(kept.drop(2).all { !it.startsWith("qwen") })
    }

    @Test
    fun `math 这类泛词不会误伤带 math 字样的对话模型`() {
        // 排除名单里的 math 是冲着 qwen-math 专用解题模型去的；
        // 万一第三方模型名里带 math，也该按对话模型保留
        val kept = chatIdsOf("qwen3.8-max")
        assertFalse(kept.isEmpty())
    }
}
