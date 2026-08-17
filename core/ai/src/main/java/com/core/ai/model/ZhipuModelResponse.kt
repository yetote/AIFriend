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