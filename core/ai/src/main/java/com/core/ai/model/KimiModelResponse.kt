package com.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KimiModelResponse(
    @SerialName("data")
    val kimiModelList: List<KimiModel> = emptyList(),
    @SerialName("object")
    val type: String? = null
)

@Serializable
data class KimiModel(
    @SerialName("context_length")
    val contextLength: Int? = null,
    @SerialName("created")
    val created: Long? = null, // 时间戳使用 Long 防止 Int 溢出
    @SerialName("id")
    val id: String? = null,
    @SerialName("object")
    val type: String? = null,
    @SerialName("owned_by")
    val ownedBy: String? = null,
    @SerialName("parent")
    val parent: String? = null,
    @SerialName("permission")
    val permission: List<KimiPermission> = emptyList(),
    @SerialName("reasoning_efforts")
    val reasoningEfforts: KimiReasoningEfforts? = null,
    @SerialName("root")
    val root: String? = null,
    @SerialName("supports_dynamic_tools")
    val supportsDynamicTools: Boolean? = null,
    @SerialName("supports_image_in")
    val supportsImageIn: Boolean? = null,
    @SerialName("supports_reasoning")
    val supportsReasoning: Boolean? = null,
    @SerialName("supports_thinking_type")
    val supportsThinkingType: String? = null,
    @SerialName("supports_video_in")
    val supportsVideoIn: Boolean? = null,
    @SerialName("think_efforts")
    val thinkEfforts: KimiThinkEfforts? = null
)

@Serializable
data class KimiPermission(
    @SerialName("created")
    val created: Long? = null,
    @SerialName("group")
    val group: String? = null,
    @SerialName("id")
    val id: String? = null,
    @SerialName("is_blocking")
    val isBlocking: Boolean? = null,
    @SerialName("object")
    val type: String? = null,
    @SerialName("organization")
    val organization: String? = null
)

@Serializable
data class KimiReasoningEfforts(
    @SerialName("default_effort")
    val defaultEffort: String? = null,
    @SerialName("support")
    val support: Boolean? = null,
    @SerialName("valid_efforts")
    val validEfforts: List<String> = emptyList()
)

@Serializable
data class KimiThinkEfforts(
    @SerialName("default_effort")
    val defaultEffort: String? = null,
    @SerialName("support")
    val support: Boolean? = null,
    @SerialName("valid_efforts")
    val validEfforts: List<String> = emptyList()
)