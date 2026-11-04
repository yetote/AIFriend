package com.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DoubaoModelResponse(
    @SerialName("data")
    val doubaoModelList: List<DoubaoModel> = emptyList(),
    @SerialName("object")
    val objectType: String? = null
)

@Serializable
data class DoubaoModel(
    @SerialName("created")
    val created: Long? = null, // 时间戳建议使用 Long，防止超出 Int 范围
    @SerialName("api_supported")
    val apiSupported: Map<String, Boolean> = emptyMap(),
    @SerialName("domain")
    val domain: String? = null,
    @SerialName("features")
    val features: DoubaoFeatures? = null,
    @SerialName("id")
    val id: String? = null,
    @SerialName("modalities")
    val modalities: DoubaoModalities? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("object")
    val objectType: String? = null,
    @SerialName("status")
    val status: String? = null,
    @SerialName("task_type")
    val taskType: List<String> = emptyList(),
    @SerialName("token_limits")
    val tokenLimits: DoubaoTokenLimits? = null,
    @SerialName("version")
    val version: String? = null
)

@Serializable
data class DoubaoFeatures(
    @SerialName("batch")
    val batch: DoubaoBatch? = null,
    @SerialName("cache")
    val cache: DoubaoCache? = null,
    @SerialName("structured_outputs")
    val structuredOutputs: DoubaoStructuredOutputs? = null,
    @SerialName("tools")
    val tools: DoubaoTools? = null
)

@Serializable
data class DoubaoModalities(
    @SerialName("input_modalities")
    val inputModalities: List<String> = emptyList(),
    @SerialName("output_modalities")
    val outputModalities: List<String> = emptyList()
)

@Serializable
data class DoubaoTokenLimits(
    @SerialName("context_window")
    val contextWindow: Long? = null,
    @SerialName("max_input_token_length")
    val maxInputTokenLength: Long? = null,
    @SerialName("max_output_token_length")
    val maxOutputTokenLength: Long? = null,
    @SerialName("max_reasoning_token_length")
    val maxReasoningTokenLength: Long? = null
)

@Serializable
data class DoubaoBatch(
    @SerialName("batch_chat")
    val batchChat: Boolean? = null,
    @SerialName("batch_job")
    val batchJob: Boolean? = null
)

@Serializable
data class DoubaoCache(
    @SerialName("prefix_cache")
    val prefixCache: Boolean? = null,
    @SerialName("session_cache")
    val sessionCache: Boolean? = null
)

@Serializable
data class DoubaoStructuredOutputs(
    @SerialName("json_object")
    val jsonObject: Boolean? = null,
    @SerialName("json_schema")
    val jsonSchema: Boolean? = null
)

@Serializable
data class DoubaoTools(
    @SerialName("function_calling")
    val functionCalling: Boolean? = null
)

/**
 * 方舟的 /models 会返回图片、视频、向量模型以及第三方模型（Kimi / DeepSeek / 通义 等），
 * 直接全塞进选择列表会让人无从下手。这里只保留：
 * 1. 支持 chat/completions 的模型；
 * 2. 仍在服务中的模型（过滤 Retiring）；
 * 3. 豆包自有系列（id 以 doubao- 开头）。
 * 按发布时间倒序，方便把新模型排在前面。
 */
fun List<DoubaoModel>.toChatModelIds(): List<String> = asSequence()
    .filter { it.apiSupported["chat/completions"] == true }
    .filter { it.status != "Retiring" }
    .filter { it.id?.startsWith("doubao-") == true }
    .sortedByDescending { it.created ?: 0L }
    .mapNotNull { it.id?.takeIf { id -> id.isNotBlank() } }
    .distinct()
    .toList()