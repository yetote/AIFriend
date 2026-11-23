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
    val reasoningEffort: String? = null,
    /**
     * 要求模型只输出一个 JSON 对象。
     *
     * 默认 null，配合 `explicitNulls = false` 不会出现在报文里——普通聊天一个字节都不变。
     * 不支持这个字段的厂商（星火、Gemini 的兼容层）由
     * [com.core.ai.entity.IAI.supportsJsonMode] 挡住，不会发出去被 400。
     */
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null,
    /**
     * 让流式响应在最后一帧带 `usage`。
     *
     * null 时不出现在报文里。部分国产兼容层不认这个字段会直接 400，
     * 由 [com.core.ai.entity.OpenAICompatibleAI] 在流式失败后降级成一次性请求兜底。
     */
    @SerialName("stream_options")
    val streamOptions: StreamOptions? = null,
    /**
     * 联网搜索开关。**各家字段名不一样，这里按厂商分了四个口子**，
     * 由 [com.core.ai.entity.OpenAICompatibleAI.applyWebSearch] 按 provider 选一个填。
     *
     * 之所以不统一：OpenAI 兼容的只是报文**形状**，不是参数语义。
     * 把千问的 `enable_search` 发给混元会当场 400，把豆包的 `web_search_options`
     * 发给千问则一个字都不生效——两种失败方式都很难从报错里看出来。
     *
     * - 豆包（方舟）：`web_search_options: {}`，空对象即开启，服务端自己判断搜不搜；
     * - 千问（百炼）：`enable_search: true`；
     * - 混元（TokenHub）：`enable_web_search: true`；
     * - 智谱：`tools: [{"type":"web_search","web_search":{"enable":true}}]`。
     */
    @SerialName("web_search_options")
    val webSearchOptions: WebSearchOptions? = null,
    @SerialName("enable_search")
    val enableSearch: Boolean? = null,
    @SerialName("enable_web_search")
    val enableWebSearch: Boolean? = null,
    /**
     * 服务端内置工具。目前唯一用途是智谱的 `web_search`。
     *
     * 注意豆包的文档明确写了「tools 与 web_search_options 同时出现时，联网搜索不生效」，
     * 所以这两条路径**永远只走一条**，不能合并成一个开关。
     */
    val tools: List<ServerTool>? = null,
)

/**
 * 豆包的联网配置。
 *
 * 传 `{}` 就是开启，具体搜不搜由模型自己判断；`search_context_size` 之类的子字段
 * 官方标注为「暂不生效」，所以这里一个都不填——填了是给未来挖坑。
 */
@Serializable
data class WebSearchOptions(
    /** 混元之外没人用这个字段；留给「只想显式打开/关闭」的场合。 */
    val enable: Boolean? = null,
)

/** 服务端工具声明，形状按智谱的 `web_search` 写。 */
@Serializable
data class ServerTool(
    val type: String,
    @SerialName("web_search")
    val webSearch: ServerToolConfig? = null,
)

@Serializable
data class ServerToolConfig(
    val enable: Boolean? = null,
    /** 智谱只有开了它才在回复里带引用链接，否则搜了也看不出来。 */
    @SerialName("search_result")
    val searchResult: Boolean? = null,
)

@Serializable
data class StreamOptions(
    @SerialName("include_usage")
    val includeUsage: Boolean = true
)

@Serializable
data class ResponseFormat(
    val type: String
) {
    companion object {
        const val JSON_OBJECT = "json_object"
    }
}

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
    val choices: List<ChatChoice> = emptyList(),
    /** 非流式请求的服务端用量；不回的平台留 null，由本地估算兜底。 */
    val usage: TokenUsageResponse? = null
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
