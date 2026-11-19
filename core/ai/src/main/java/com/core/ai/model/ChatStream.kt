package com.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 流式回复的一小段增量。
 *
 * [content] 是要显示给用户的正文，[reasoning] 是深度思考的思维链。两者分开而不是拼在一起：
 * 思考过程要折叠起来单独展示，混进正文就没法在界面上分开了。
 *
 * 一次推送里两者可能只有一边有内容（先思考后回答，或者不思考直接回答），
 * 所以默认都是空串而不是可空——调用方累加时不用到处判 null。
 */
data class ChatDelta(
    val content: String = "",
    val reasoning: String = "",
    /**
     * 底层这次请求是否因超时降级过（关掉深度思考重试/补发）。
     *
     * 降级发生在 IAI 内部、外层看不到异常，只能靠这个标志往外传。
     * 狼人杀据此把该座位本局的深度思考关掉——不传的话下一轮又开，
     * 模型会一次次撞同一个超时。普通对话不关心它，默认 false。
     */
    val timeoutDegraded: Boolean = false,
)

/**
 * SSE 解析结果。
 *
 * 单独分出 [Skip] 而不是返回 null：一条 SSE 流里混着 `event:`、空行、厂商自定义的
 * 心跳注释，这些**不是错误**，直接跳过就好；只有真正解析不出增量时才需要静默丢弃。
 * 用 null 表达「跳过」会让调用方分不清「跳过」和「坏了」。
 */
sealed interface ChatStreamEvent {
    data class Delta(val content: String, val reasoning: String) : ChatStreamEvent
    data object Done : ChatStreamEvent
    data object Skip : ChatStreamEvent
}

/**
 * OpenAI 兼容流式响应的增量报文。
 *
 * 只声明用得到的字段，其余交给 `ignoreUnknownKeys`：各家在同一套协议上都有自己加的字段
 * （usage、system_fingerprint、厂商私有的 grounding 之类），逐个声明既没必要也很脆。
 */
@Serializable
data class ChatStreamResponse(
    val choices: List<ChatStreamChoice> = emptyList(),
)

@Serializable
data class ChatStreamChoice(
    val index: Int = 0,
    val delta: ChatStreamDelta = ChatStreamDelta(),
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
data class ChatStreamDelta(
    val role: String? = null,
    val content: String? = null,
    /** DeepSeek、Kimi、通义、智谱等用的字段名。 */
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    /** 部分聚合平台（OpenRouter 等）用的字段名。 */
    val reasoning: String? = null,
)

/**
 * SSE 单行解析。
 *
 * 抽成纯函数是为了能直接单测：流式最容易出错的地方不是网络，而是「这一行到底是什么」——
 * 冒号后带不带空格、`[DONE]` 有没有方括号、心跳注释长什么样。这些用真请求测很贵，
 * 用字符串测几乎零成本。
 */
object ChatStreamParser {

    private const val DATA_PREFIX = "data:"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parse(line: String): ChatStreamEvent {
        val trimmed = line.trim()
        // 空行是 SSE 的事件分隔符；`event:` / `id:` / `: ping` 这些都不带正文
        if (trimmed.isEmpty() || !trimmed.startsWith(DATA_PREFIX)) return ChatStreamEvent.Skip

        val payload = trimmed.removePrefix(DATA_PREFIX).trim()
        if (payload.isEmpty()) return ChatStreamEvent.Skip
        // 结束标记各家的写法不完全一样，除了标准的 [DONE]，也有直接发字符串的
        if (payload == "[DONE]" || payload == "DONE") return ChatStreamEvent.Done

        val chunk = runCatching { json.decodeFromString<ChatStreamResponse>(payload) }.getOrNull()
            ?: return ChatStreamEvent.Skip
        val delta = chunk.choices.firstOrNull()?.delta ?: return ChatStreamEvent.Skip

        val content = delta.content.orEmpty()
        // 两个字段名只有一个会有值；都空说明这一帧只带了 role 或 finish_reason
        val reasoning = delta.reasoningContent ?: delta.reasoning.orEmpty()
        if (content.isEmpty() && reasoning.isEmpty()) return ChatStreamEvent.Skip
        return ChatStreamEvent.Delta(content = content, reasoning = reasoning)
    }
}
