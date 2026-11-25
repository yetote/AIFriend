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
    /**
     * 本次调用的 token 用量。只有服务端回报 usage 的那一帧才有值，
     * 拿不到的平台全程为 null，由调用方用 [TokenCounter] 估算。
     */
    val usage: TokenUsage? = null,
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
    /** 最后一帧只带 usage、没有 choices，单独分出来免得被当成心跳丢掉。 */
    data class Usage(val usage: TokenUsage) : ChatStreamEvent
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
    val usage: TokenUsageResponse? = null,
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
        // usage 帧的 choices 是空的，必须先看 usage 再看 delta，否则会被 Skip 吞掉。
        chunk.usage?.toUsage()?.let { return ChatStreamEvent.Usage(it) }
        val delta = chunk.choices.firstOrNull()?.delta ?: return ChatStreamEvent.Skip

        val content = delta.content.orEmpty()
        // 两个字段名只有一个会有值；都空说明这一帧只带了 role 或 finish_reason
        val reasoning = delta.reasoningContent ?: delta.reasoning.orEmpty()
        if (content.isEmpty() && reasoning.isEmpty()) return ChatStreamEvent.Skip
        return ChatStreamEvent.Delta(content = content, reasoning = reasoning)
    }
}

/**
 * 把正文开头的 `<think>…</think>` 拆成思维链。
 *
 * MiniMax（官方兼容层默认）、经网关/混元转发的 MiniMax、部分 DeepSeek-R1 / QwQ 部署
 * **不走 `reasoning_content` 字段**，而是把思考过程直接塞进 `content`，用 `<think>` 包住。
 * 不拆的后果很严重：上层看到「正文已经开始吐字」，狼人杀的看门狗就从思考时限切到
 * 发言时限——思考彻底失去上限，一只狼夜聊能想十几分钟；同时整段思考还会被当成发言念出来。
 *
 * 只认**开头**的 `<think>`（允许前导空白）：正文中间出现的同名字符串是模型在说话，不能吞。
 * 标签可能被切在两帧之间，所以是有状态的，一条流一个实例；流结束时调用 [finish] 吐出残留。
 */
class ThinkTagSplitter {
    private enum class State { START, THINK, CONTENT }

    private var state = State.START
    private val buffer = StringBuilder()

    /** 喂一帧原始 content，返回拆好的 (正文, 思维链)。 */
    fun feed(chunk: String): Pair<String, String> {
        if (chunk.isEmpty()) return "" to ""
        if (state == State.CONTENT) return chunk to ""
        buffer.append(chunk)
        val content = StringBuilder()
        val reasoning = StringBuilder()
        drain(content, reasoning)
        return content.toString() to reasoning.toString()
    }

    /** 流结束：开头还在攒的算正文；没等到闭合标签的算思维链（模型被截断了）。 */
    fun finish(): Pair<String, String> {
        val rest = buffer.toString()
        buffer.setLength(0)
        return when (state) {
            State.START -> { state = State.CONTENT; rest to "" }
            State.THINK -> rest.removeSuffixPartial(CLOSE).let { "" to it }
            State.CONTENT -> rest to ""
        }
    }

    private fun drain(content: StringBuilder, reasoning: StringBuilder) {
        while (true) {
            when (state) {
                State.START -> {
                    val lead = buffer.indexOfFirst { !it.isWhitespace() }
                    if (lead < 0) return // 全是空白，继续攒
                    val rest = buffer.substring(lead)
                    when {
                        rest.startsWith(OPEN) -> {
                            buffer.setLength(0)
                            buffer.append(rest.substring(OPEN.length))
                            state = State.THINK
                        }
                        OPEN.startsWith(rest) -> return // 标签被切成两半，等下一帧
                        else -> {
                            state = State.CONTENT
                            content.append(buffer)
                            buffer.setLength(0)
                            return
                        }
                    }
                }

                State.THINK -> {
                    val close = buffer.indexOf(CLOSE)
                    if (close >= 0) {
                        reasoning.append(buffer, 0, close)
                        val after = buffer.substring(close + CLOSE.length).trimStart()
                        buffer.setLength(0)
                        state = State.CONTENT
                        content.append(after)
                        return
                    }
                    // 末尾可能是半个 `</think>`，留着等下一帧
                    val keep = partialSuffixLength(buffer, CLOSE)
                    val emit = buffer.length - keep
                    reasoning.append(buffer, 0, emit)
                    buffer.delete(0, emit)
                    return
                }

                State.CONTENT -> {
                    content.append(buffer)
                    buffer.setLength(0)
                    return
                }
            }
        }
    }

    companion object {
        private const val OPEN = "<think>"
        private const val CLOSE = "</think>"

        /** 一次性回复用：去掉开头的 `<think>…</think>`，只留正文。 */
        fun strip(text: String): String {
            val splitter = ThinkTagSplitter()
            val (c1, _) = splitter.feed(text)
            val (c2, _) = splitter.finish()
            return c1 + c2
        }

        private fun partialSuffixLength(text: CharSequence, tag: String): Int {
            for (len in minOf(tag.length - 1, text.length) downTo 1) {
                if (text.regionMatchesAt(text.length - len, tag, len)) return len
            }
            return 0
        }

        private fun CharSequence.regionMatchesAt(start: Int, tag: String, len: Int): Boolean {
            for (i in 0 until len) if (this[start + i] != tag[i]) return false
            return true
        }

        private fun String.removeSuffixPartial(tag: String): String =
            dropLast(partialSuffixLength(this, tag))
    }
}
