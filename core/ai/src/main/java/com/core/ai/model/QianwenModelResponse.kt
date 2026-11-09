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
 * 这里按关键词排除非对话模型，只保留可对话的模型，并按发布时间倒序。
 *
 * 早先这里只留 `qwen` 前缀，等于把百炼代理的第三方模型（DeepSeek、Kimi、GLM、
 * MiniMax…）全滤掉了。但百炼本身就是一个「一把 Key 调多家」的平台，
 * 用户开了 Key 却看不到那些模型，会以为功能坏了 —— 所以改成按「是不是对话模型」
 * 判断，而不是按「是不是自家模型」判断。
 *
 * 顺序上自家模型排前面：找通义的人多半要 Qwen，第三方是补充。
 */
private val QIANWEN_NON_CHAT_MARKERS = listOf(
    "embedding", "rerank", "ocr", "audio", "image", "video", "tts", "omni",
    "realtime", "livetranslate", "mt-", "wanx", "wan2", "wan3", "cosyvoice",
    "paraformer", "sambert", "character", "deep-research", "deep-search", "math",
    "asr", "tripo", "happyhorse", "happyoyster", "decision-model"
)

fun List<QianwenModel>.toChatModelIds(): List<String> = asSequence()
    .sortedByDescending { it.created ?: 0L }
    .mapNotNull { it.id }
    .filter { id ->
        QIANWEN_NON_CHAT_MARKERS.none { marker -> id.contains(marker, ignoreCase = true) }
    }
    .distinct()
    .sortedBy { if (it.startsWith("qwen")) 0 else 1 }
    .toList()