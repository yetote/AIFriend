package com.core.ai.model

import kotlinx.serialization.Serializable
@Serializable
data class AIRequestBody(
    val model: String,
    val input: String
)
