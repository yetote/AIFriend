package com.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容的 Chat Completions 请求体。
 * DeepSeek、豆包（火山方舟）等厂商都使用同一套 { model, messages, stream } 结构。
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    /** 智谱 / 豆包 / Kimi 的思考开关：{"type": "disabled"} */
    val thinking: ThinkingConfig? = null,
    /** 通义千问的思考开关 */
    @SerialName("enable_thinking")
    val enableThinking: Boolean? = null,
    /** 智谱 GLM-5 系列无法关闭思考，只能降档：low / high / max */
    @SerialName("reasoning_effort")
    val reasoningEffort: String? = null
)

@Serializable
data class ThinkingConfig(
    val type: String
)

/**
 * Chat Completions 响应体（只声明当前用到的字段，其余字段由 ignoreUnknownKeys 忽略）。
 */
@Serializable
data class ChatCompletionResponse(
    val id: String = "",
    val model: String = "",
    val choices: List<ChatChoice> = emptyList()
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatResponseMessage = ChatResponseMessage(),
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class ChatResponseMessage(
    val role: String = "",
    val content: String? = null,
    /** 深度思考模型的思维链，不展示给用户，保留字段方便后续做「思考过程」。 */
    @SerialName("reasoning_content")
    val reasoningContent: String? = null
)
