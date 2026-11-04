package com.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QianwenModelResponse(
    @SerialName("data")
    val qianwenModelList: List<QianwenModel> = emptyList(),
    @SerialName("first_id")
    val firstId: String? = null,
    @SerialName("has_more")
    val hasMore: Boolean? = null,
    @SerialName("last_id")
    val lastId: String? = null,
    @SerialName("object")
    val type: String? = null
)

@Serializable
data class QianwenModel(
    @SerialName("created")
    val created: Long? = null, // 时间戳改为 Long 防止溢出
    @SerialName("id")
    val id: String? = null,
    @SerialName("object")
    val type: String? = null,
    @SerialName("owned_by")
    val ownedBy: String? = null
)

/**
 * 百炼（DashScope 兼容模式）一次会返回近 200 个模型，包含向量、重排、语音、图像、翻译等，
 * 这里按关键词排除非对话模型，只保留 qwen 系列，并按发布时间倒序。
 */
private val QIANWEN_NON_CHAT_MARKERS = listOf(
    "embedding", "rerank", "ocr", "audio", "image", "video", "tts", "omni",
    "realtime", "livetranslate", "mt-", "wanx", "cosyvoice", "paraformer",
    "sambert", "character", "deep-research", "deep-search", "math"
)

fun List<QianwenModel>.toChatModelIds(): List<String> = asSequence()
    .sortedByDescending { it.created ?: 0L }
    .mapNotNull { it.id }
    .filter { it.startsWith("qwen") }
    .filterNot { id ->
        QIANWEN_NON_CHAT_MARKERS.any { marker -> id.contains(marker, ignoreCase = true) }
    }
    .distinct()
    .toList()