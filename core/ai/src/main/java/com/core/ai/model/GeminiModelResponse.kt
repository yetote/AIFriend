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
)

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
 * Gemini 的 60 个模型里混着 TTS、图像、Live、语音转写、机器人等，不能直接展示。
 */
fun List<GeminiModel>.toChatModelIds(): List<String> = asSequence()
    .map { it.id.removePrefix("models/") }
    .filter { it.startsWith("gemini") }
    .filterNot { id ->
        listOf(
            "embedding", "aqa", "image", "tts", "live", "audio", "transcribe",
            "robotics", "computer-use", "nano-banana", "omni", "customtools",
            "extended-thinking", "native"
        ).any { marker -> id.contains(marker, ignoreCase = true) }
    }
    .distinct()
    .toList()
