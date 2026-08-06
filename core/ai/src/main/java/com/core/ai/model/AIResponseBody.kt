package com.core.ai.model

data class AIResponseBody(
    val created: String,
    val id: String,
    val model: String,
    val `object`: String,
    val status: String,
    val steps: List<Step>,
    val usage: Usage
)

data class Step(
    val content: List<Content>,
    val signature: String,
    val type: String
)

data class Usage(
    val total_input_tokens: Int,
    val total_output_tokens: Int,
    val total_tokens: Int
)

data class Content(
    val text: String,
    val type: String
)