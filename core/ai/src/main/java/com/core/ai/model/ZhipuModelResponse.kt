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
 *
 * 忽略大小写是必要的：智谱定价页把免费档写作 `GLM-4.7-Flash`（大写），
 * 而 `/models` 返回的 id 大小写并不稳定。大小写敏感的话，一旦上游换成大写，
 * 整张列表会被滤成空，用户只看到兜底的那几个付费模型，免费的反而调不到。
 */
fun List<ZhipuModel>.toChatModelIds(): List<String> = asSequence()
    .mapNotNull { it.id }
    .filter { it.startsWith("glm-", ignoreCase = true) }
    .distinct()
    .toList()