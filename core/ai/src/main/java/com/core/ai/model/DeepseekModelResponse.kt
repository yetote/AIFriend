package com.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeepseekModelResponse(
    @SerialName("data")
    val deepseekModelList: List<DeepseekModel>,
    val `object`: String
)
@Serializable
data class DeepseekModel(
    val id: String,
    val `object`: String,
    val owned_by: String
)