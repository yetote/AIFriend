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