package com.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gemini 的 OpenAI 兼容模型列表：GET /v1beta/openai/models
 */
@Serializable
data class GeminiModelResponse(
    @SerialName("data")
    val geminiModelList: List<GeminiModel> = emptyList(),
    @SerialName("object")
    val type: String? = null
) {
    fun toChatModelIds(): List<String> = geminiModelList.toChatModelIds()
}

@Serializable
data class GeminiModel(
    @SerialName("id")
    val id: String = "",
    @SerialName("object")
    val type: String? = null,
    @SerialName("owned_by")
    val ownedBy: String? = null
)

/**
 * 部分接口返回的 id 会带 "models/" 前缀，统一去掉，只保留文本对话模型。
 * Gemini 的 60 多个模型里混着 TTS、图像、Live、语音转写、机器人、Decisions API 等，不能直接展示。
 *
 * 同时保留 **Gemma 4** 系列（`gemma-4-31b-it`、`gemma-4-26b-a4b-it` 等）：
 * Gemma 4 原生支持 system 指令与结构化 JSON，且免费档每日额度高达 14.4K RPD；
 * 刻意不放行更早的 Gemma 2 / 3（OpenAI 兼容层不支持 system role，带系统提示词会直接 400）。
 */
fun List<GeminiModel>.toChatModelIds(): List<String> = asSequence()
    .map { it.id.trim().removePrefix("models/") }
    .filter { it.startsWith("gemini", ignoreCase = true) || it.startsWith("gemma-4", ignoreCase = true) }
    .filterNot { id ->
        listOf(
            "embedding", "aqa", "image", "tts", "live", "audio", "transcribe",
            "robotics", "computer-use", "nano-banana", "omni", "customtools",
            "extended-thinking", "native", "decision"
        ).any { marker -> id.contains(marker, ignoreCase = true) }
    }
    .distinct()
    .toList()
