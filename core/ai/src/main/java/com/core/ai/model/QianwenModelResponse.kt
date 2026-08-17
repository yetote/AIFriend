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