package com.core.ai.entity

import com.core.ai.isTimeoutLike
import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.ChatCompletionResponse
import com.core.ai.model.ChatDelta
import com.core.ai.model.ChatMessage
import com.core.ai.model.ChatStreamEvent
import com.core.ai.model.ChatStreamParser
import com.core.ai.model.ResponseFormat
import com.core.repository.ApiConfig
import com.core.repository.WebRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * OpenAI Chat Completions 协议的公共实现：
 * 子类只需要提供 [ApiConfig]（baseUrl / chatUrl / headers）、模型列表解析，
 * 以及按需覆盖 [applyThinking] 来关闭深度思考。
 */
abstract class OpenAICompatibleAI(
    final override var brandName: String,
    final override var company: String,
    /**
     * open：聚合网关的 Key 是运行时才有的，需要在请求前现取，
     * 由子类覆盖成动态读取，不能用构造时算出的快照。
     */
    protected open val config: ApiConfig
) : IAI {

    override var modelSet: MutableSet<String> = mutableSetOf()

    override suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String?,
        enableThinking: Boolean,
        jsonMode: Boolean
    ): String {
        val request = buildRequest(messages, model, systemPrompt, enableThinking, jsonMode, stream = false)

        val response = WebRepository.instance
            .chat<ChatCompletionResponse>(config = config, body = request)
            .getOrElse { throw it }

        val content = response.choices.firstOrNull()?.message?.content
        return content?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("模型没有返回内容，请重试")
    }

    /**
     * 流式对话，走同一套 OpenAI 兼容协议：请求体多一个 `stream: true`，
     * 响应变成一行一个 `data: {...}`。
     *
     * 正文和思维链分别累加，不混在一起——思考过程在界面上是折叠的一块，
     * 一旦拼进正文，就没有办法再把它拆出来了。
     *
     * 结束时**必须有正文**才认这一轮成功。只有 thinking 没有回答，说明模型跑到一半
     * 被截断了（或者这一家的兼容层只回思维链），这时候抛错让上层留重试入口，
     * 比把一个空气泡落库要好。
     */
    override fun chatStream(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String?,
        enableThinking: Boolean,
        jsonMode: Boolean
    ): Flow<ChatDelta> = channelFlow {
        val request = buildRequest(messages, model, systemPrompt, enableThinking, jsonMode, stream = true)
        var hasContent = false

        val failure = runCatching {
            WebRepository.instance.streamChat(config, request) { line ->
                when (val event = ChatStreamParser.parse(line)) {
                    is ChatStreamEvent.Delta -> {
                        if (event.content.isNotEmpty()) hasContent = true
                        send(ChatDelta(content = event.content, reasoning = event.reasoning))
                    }

                    // 大多数厂商发完 [DONE] 就关连接，循环会自然结束；
                    // 不发也不影响，读到 EOF 一样能收尾
                    ChatStreamEvent.Done -> Unit
                    ChatStreamEvent.Skip -> Unit
                }
            }
        }.exceptionOrNull()

        if (failure != null) {
            // 用户按了停止：这不叫失败，不能顺手再发一个完整请求
            if (failure is CancellationException) throw failure
            // 一个字都还没收到就断了，退化成一次性请求。
            // 十家厂商的兼容层对流式的支持并不整齐（个别会直接 400，或者回一个空流），
            // 与其让用户看到报错，不如「憋一会儿再一次性出结果」——功能是对的，只是没有逐字效果。
            //
            // 但如果这次断的是**超时**，就不用原样再来一次：一次性请求在出结果之前
            // 同样是彻底静默的，仍开着深度思考只会再等满一整个超时，然后照样失败——
            // 用户等了十几分钟换来一句「网络异常」。关掉思考再补，通常很快就有内容。
            if (!hasContent) {
                // 超时降级要往外报一声：外层（狼人杀）据此把该座位本局的深度思考关掉。
                // 不报的话外层只看到「正常返回了一整段」，下一轮又把思考打开，
                // 于是每个座位都卡在同一个超时上反复浪费时间。
                val degradedByTimeout = failure.isTimeoutLike()
                val retryThinking = if (degradedByTimeout) false else enableThinking
                send(
                    ChatDelta(
                        content = chat(messages, model, systemPrompt, retryThinking, jsonMode),
                        timeoutDegraded = degradedByTimeout,
                    )
                )
                return@channelFlow
            }
            // 已经吐了一半：不能补发，否则会把前半句重复一遍。交给上层保留半句并给重试。
            throw failure
        }

        if (!hasContent) throw IllegalStateException("模型没有返回内容，请重试")
    }

    /** 一次性请求和流式请求的报文只差 `stream` 一个字段，其余构造逻辑必须一致。 */
    private fun buildRequest(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String?,
        enableThinking: Boolean,
        jsonMode: Boolean,
        stream: Boolean,
    ): ChatCompletionRequest {
        val apiMessages = buildList {
            if (!systemPrompt.isNullOrBlank()) {
                add(ChatMessage.system(systemPrompt))
            }
            addAll(messages)
        }
        return ChatCompletionRequest(model = model, messages = apiMessages, stream = stream)
            .applyThinking(enableThinking)
            // 厂商不支持就当作没开，宁可让模型自由发挥，也不要一个 400 把整轮对话打断
            .applyJsonMode(jsonMode && supportsJsonMode)
    }

    /**
     * 默认不处理思考开关；支持关闭思考的厂商覆盖此方法。
     */
    protected open fun ChatCompletionRequest.applyThinking(
        enableThinking: Boolean
    ): ChatCompletionRequest = this

    /**
     * 加上 `response_format`。子类如果要用别的字段名（个别厂商自己搞了一套），覆盖这里。
     */
    protected open fun ChatCompletionRequest.applyJsonMode(
        jsonMode: Boolean
    ): ChatCompletionRequest =
        if (jsonMode) copy(responseFormat = ResponseFormat(ResponseFormat.JSON_OBJECT)) else this

    @Deprecated("改用 chat(messages, model, systemPrompt, enableThinking)")
    override suspend fun chat(message: String, model: String): String =
        chat(
            messages = listOf(ChatMessage.user(message)),
            model = model,
            systemPrompt = null,
            enableThinking = true
        )
}
