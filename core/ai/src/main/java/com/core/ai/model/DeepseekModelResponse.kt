package com.core.ai.model

import kotlinx.serialization.Serializable

@Serializable
data class DeepseekModelResponse(
    val `data`: List<Data>,
    val `object`: String
)
@Serializable
data class Data(
    val id: String,
    val `object`: String,
    val owned_by: String
)