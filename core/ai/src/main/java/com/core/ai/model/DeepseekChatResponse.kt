package com.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DeepseekChatResponse(
    val id: String = "",
    val `object`: String = "",
    val created_at: Long = 0L,                     // 建议使用 Long 防数值溢出
    val completed_at: Long = 0L,
    val status: String = "",
    val background: Boolean = false,
    val model: String = "",
    val service_tier: String = "",
    val store: Boolean = false,
    val tool_choice: String = "",
    val truncation: String = "",

    // 浮点数参数统一声明为 Double（解决 0.0 解析报错）
    val frequency_penalty: Double = 0.0,
    val presence_penalty: Double = 0.0,
    val temperature: Double = 0.0,
    val top_p: Double = 0.0,
    val top_logprobs: Int = 0,
    val parallel_tool_calls: Boolean = false,

    // 映射 JSON 中的 "usage" -> deepseekUsage
    @SerialName("usage")
    val deepseekUsage: DeepseekUsage? = null,

    val output: List<Output> = emptyList(),
    val tools: List<JsonElement> = emptyList(),
    val reasoning: Reasoning? = null,
    val text: Text? = null,

    // 可能为 null 的可选字段
    val content_filters: JsonElement? = null,
    val error: JsonElement? = null,
    val incomplete_details: JsonElement? = null,
    val instructions: JsonElement? = null,
    val max_output_tokens: JsonElement? = null,
    val max_tool_calls: JsonElement? = null,
    val metadata: JsonElement? = null,
    val moderation: JsonElement? = null,
    val previous_response_id: JsonElement? = null,
    val prompt_cache_key: JsonElement? = null,
    val prompt_cache_retention: JsonElement? = null,
    val safety_identifier: JsonElement? = null,
    val user: JsonElement? = null
)

@Serializable
data class Output(
    val id: String = "",
    val type: String = "",
    val status: String = "",
    val phase: String? = null,                    // reasoning 节点无此字段，设为可空
    val role: String? = null,                     // reasoning 节点无此字段，设为可空

    // 映射 JSON 中的 "content" -> deepseekContent
    @SerialName("content")
    val deepseekContent: List<DeepseekContent> = emptyList(),

    val summary: List<JsonElement> = emptyList()
)

@Serializable
data class DeepseekContent(
    val type: String = "",
    val text: String = "",
    val annotations: List<JsonElement> = emptyList(), // reasoning 节点无此字段，给默认空列表
    val logprobs: List<JsonElement> = emptyList()      // reasoning 节点无此字段，给默认空列表
)

@Serializable
data class Reasoning(
    val effort: JsonElement? = null,
    val summary: JsonElement? = null
)

@Serializable
data class Text(
    val format: Format? = null,
    val verbosity: JsonElement? = null
)

@Serializable
data class Format(
    val type: String = ""
)

@Serializable
data class DeepseekUsage(
    val input_tokens: Int = 0,
    val output_tokens: Int = 0,
    val total_tokens: Int = 0,
    val input_tokens_details: InputTokensDetails? = null,
    val output_tokens_details: OutputTokensDetails? = null
)

@Serializable
data class InputTokensDetails(
    val cached_tokens: Int = 0
)

@Serializable
data class OutputTokensDetails(
    val reasoning_tokens: Int = 0
)