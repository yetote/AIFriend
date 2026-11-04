package com.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DeepSeek Chat Completions 请求体（POST /chat/completions）。
 * 与旧的 Responses 风格 [AIRequestBody] 不同，这里使用 messages 数组承载多轮上下文。
 */
@Serializable
data class DeepSeekChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

/**
 * DeepSeek Chat Completions 响应体（只声明当前用到的字段，其余字段由 ignoreUnknownKeys 忽略）。
 */
@Serializable
data class DeepSeekCompletionResponse(
    val id: String = "",
    val model: String = "",
    val choices: List<DeepSeekChoice> = emptyList()
)

@Serializable
data class DeepSeekChoice(
    val index: Int = 0,
    val message: DeepSeekResponseMessage = DeepSeekResponseMessage(),
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class DeepSeekResponseMessage(
    val role: String = "",
    val content: String? = null,
    /** deepseek-reasoner 的思维链，不展示给用户，保留字段方便后续做「思考过程」。 */
    @SerialName("reasoning_content")
    val reasoningContent: String? = null
)
