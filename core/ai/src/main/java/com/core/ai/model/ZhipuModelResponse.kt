package com.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ZhipuModelResponse(
    @SerialName("data")
    val zhipuModelList: List<ZhipuModel> = emptyList(),
    @SerialName("object")
    val type: String? = null
)

@Serializable
data class ZhipuModel(
    @SerialName("created")
    val created: Long? = null, // 时间戳建议使用 Long，防止数值溢出
    @SerialName("id")
    val id: String? = null,
    @SerialName("object")
    val type: String? = null,
    @SerialName("owned_by")
    val ownedBy: String? = null
)

/**
 * 智谱当前开放的条目基本都是 GLM 对话模型，这里只保留 glm- 前缀，避免以后混入向量模型。
 */
fun List<ZhipuModel>.toChatModelIds(): List<String> = asSequence()
    .mapNotNull { it.id }
    .filter { it.startsWith("glm-") }
    .distinct()
    .toList()