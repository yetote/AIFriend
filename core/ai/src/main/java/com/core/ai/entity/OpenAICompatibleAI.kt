package com.core.ai.entity

import com.core.ai.isTimeoutLike
import com.core.ai.looksLikeSearchConflict
import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.ChatCompletionResponse
import com.core.ai.model.ChatDelta
import com.core.ai.model.ChatMessage
import com.core.ai.model.ChatReply
import com.core.ai.model.ChatStreamEvent
import com.core.ai.model.ChatStreamParser
import com.core.ai.model.ResponseFormat
import com.core.ai.model.StreamOptions
import com.core.ai.model.ThinkTagSplitter
import com.core.ai.model.TokenCounter
import com.core.ai.model.TokenUsage
import com.core.ai.model.WebSearchOptions
import com.core.network.NetTimeouts
import com.core.repository.ApiConfig
import com.core.repository.WebRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull

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

    /**
     * 按本次请求的模型选择实际配置。
     * 默认共用 [config]；多分组自定义中转站（[AIGateway]）会按模型所属分组路由到对应 API Key。
     */
    protected open fun configFor(model: String): ApiConfig = config

    /**
     * 把内部可能带分组前缀的模型 ID 还原为发给远端接口的真实模型名。
     */
    protected open fun resolveApiModel(model: String): String = model

    override suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String?,
        enableThinking: Boolean,
        jsonMode: Boolean,
        webSearch: Boolean
    ): String = rawChat(messages, model, systemPrompt, enableThinking, jsonMode, webSearch).let { response ->
        val content = response.choices.firstOrNull()?.message?.content?.let(ThinkTagSplitter::strip)
        content?.takeIf { it.isNotBlank() }
            ?: throw emptyReplyError(model)
    }

    override suspend fun chatWithUsage(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String?,
        enableThinking: Boolean,
        jsonMode: Boolean,
        webSearch: Boolean
    ): ChatReply {
        val response = rawChat(messages, model, systemPrompt, enableThinking, jsonMode, webSearch)
        val text = response.choices.firstOrNull()?.message?.content
            ?.let(ThinkTagSplitter::strip)
            ?.takeIf { it.isNotBlank() }
            ?: throw emptyReplyError(model)
        val usage = response.usage?.toUsage() ?: TokenUsage(
            input = TokenCounter.estimateInput(apiMessages(messages, systemPrompt), null),
            output = TokenCounter.estimate(text),
            estimated = true,
        )
        return ChatReply(text = text, usage = usage)
    }

    private suspend fun rawChat(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String?,
        enableThinking: Boolean,
        jsonMode: Boolean,
        webSearch: Boolean
    ): ChatCompletionResponse {
        val request = buildRequest(
            messages, model, systemPrompt, enableThinking, jsonMode, webSearch, stream = false,
        )
        return WebRepository.instance
            .chat<ChatCompletionResponse>(config = configFor(model), body = request)
            .getOrElse { throw it }
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
        jsonMode: Boolean,
        webSearch: Boolean
    ): Flow<ChatDelta> = channelFlow {
        val request = buildRequest(
            messages, model, systemPrompt, enableThinking, jsonMode, webSearch, stream = true,
        )
            // 让服务端在最后一帧带 usage；不支持这个字段的服务会 400，
            // 下面的降级分支会改用非流式请求，那时照样能拿到用量。
            .copy(streamOptions = StreamOptions(includeUsage = true))
        var hasContent = false
        // 正文里内联的 <think> 要在这里拆掉，见 [ThinkTagSplitter]：不拆的话上层以为
        // 已经开始说话，思考时限失效。一条流一个实例。
        val splitter = ThinkTagSplitter()

        val failure = runCatching {
            WebRepository.instance.streamChat(configFor(model), request) { line ->
                when (val event = ChatStreamParser.parse(line)) {
                    is ChatStreamEvent.Delta -> {
                        val (text, inlineThink) = splitter.feed(event.content)
                        val reasoning = event.reasoning + inlineThink
                        if (text.isNotEmpty()) hasContent = true
                        if (text.isNotEmpty() || reasoning.isNotEmpty()) {
                            send(ChatDelta(content = text, reasoning = reasoning))
                        }
                    }

                    is ChatStreamEvent.Usage -> {
                        // 只有服务端真回了 usage 才推这一帧；不回的平台由上层估算。
                        send(ChatDelta(usage = event.usage))
                    }

                    // 大多数厂商发完 [DONE] 就关连接，循环会自然结束；
                    // 不发也不影响，读到 EOF 一样能收尾
                    ChatStreamEvent.Done -> Unit
                    ChatStreamEvent.Skip -> Unit
                }
            }
        }.exceptionOrNull()

        // 流正常收尾（或断在半路）时把拆分器里攒着的残留吐出来
        if (failure == null || failure !is CancellationException) {
            val (tailText, tailThink) = splitter.finish()
            if (tailText.isNotBlank()) hasContent = true
            if (tailText.isNotEmpty() || tailThink.isNotEmpty()) {
                send(ChatDelta(content = tailText, reasoning = tailThink))
            }
        }

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
                // 流式 + 联网的组合比一次性更脆（有的厂商在服务端搜索期间直接断流或 400）。
                // 带搜索再失败一次就把搜索摘掉重试：少查一次远好过整段发言失败——
                // 发言失败会在这张牌上留下一个空回合，用户看到的是「它突然不说话了」。
                //
                // 若流式本身已经因为超时失败：先清掉可能半开僵死的空闲连接池，且非流式降级只给
                // TIMEOUT_FALLBACK_MS（45 秒）上限；若依然无响应则抛回原超时异常让上层立刻兜底，
                // 绝不叠加再死等 8 分钟 ONESHOT_MS。
                if (degradedByTimeout) {
                    WebRepository.instance.evictIdleConnections()
                }
                suspend fun attemptFallback(useSearch: Boolean): ChatReply {
                    if (!degradedByTimeout) {
                        return chatWithUsage(messages, model, systemPrompt, retryThinking, jsonMode, useSearch)
                    }
                    return withTimeoutOrNull(NetTimeouts.TIMEOUT_FALLBACK_MS) {
                        chatWithUsage(messages, model, systemPrompt, retryThinking, jsonMode, useSearch)
                    } ?: throw failure
                }
                val reply = runCatching {
                    attemptFallback(webSearch)
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    // 只在「看起来是搜索冲突」时才摘：限流、5xx、断网跟联网无关，
                    // 摘掉搜索照样失败，还白丢一次检索（用户会以为搜索式从不搜）。
                    if (!webSearch || !error.looksLikeSearchConflict()) throw error
                    attemptFallback(useSearch = false)
                }
                send(
                    ChatDelta(
                        content = reply.text,
                        usage = reply.usage,
                        timeoutDegraded = degradedByTimeout,
                    )
                )
                return@channelFlow
            }
            // 已经吐了一半：不能补发，否则会把前半句重复一遍。交给上层保留半句并给重试。
            throw failure
        }

        if (!hasContent) throw emptyReplyError(model)
    }

    /** 空回复报错带上厂商与模型 ID，多模型混打（狼人杀/群聊）时一眼就能定位是哪一个模型出问题。 */
    private fun emptyReplyError(model: String): IllegalStateException {
        val label = listOf(brandName.trim(), resolveApiModel(model).trim())
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
        val prefix = if (label.isNotEmpty()) "[$label] " else ""
        return IllegalStateException("${prefix}模型没有返回内容，请重试")
    }

    /**
     * 一次性请求和流式请求的报文只差 `stream` 一个字段，其余构造逻辑必须一致。
     *
     * internal 而不是 private：单测要核对「每家厂商的联网字段有没有写对」——
     * 这个字段写错既可能 400，也可能静默不搜，靠肉眼核对报文没有别的办法。
     */
    internal fun buildRequest(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String?,
        enableThinking: Boolean,
        jsonMode: Boolean,
        webSearch: Boolean,
        stream: Boolean,
    ): ChatCompletionRequest = ChatCompletionRequest(
        model = resolveApiModel(model),
        messages = apiMessages(messages, systemPrompt),
        stream = stream
    )
        .applyThinking(enableThinking)
        // 厂商不支持就当作没开，宁可让模型自由发挥，也不要一个 400 把整轮对话打断
        .applyJsonMode(jsonMode && supportsJsonMode)
        // 联网同理：不支持的厂商**一个字节都不加**，避免「以为开了其实没搜」
        .applyWebSearch(webSearch && supportsWebSearch)

    /** 请求里真正发出去的消息（含 system），估算输入 token 时也用它。 */
    private fun apiMessages(
        messages: List<ChatMessage>,
        systemPrompt: String?,
    ): List<ChatMessage> = buildList {
        if (!systemPrompt.isNullOrBlank()) {
            add(ChatMessage.system(systemPrompt))
        }
        addAll(messages)
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

    /**
     * 加上联网搜索开关。默认走豆包那套 `web_search_options: {}`。
     *
     * 子类用别的字段就必须覆盖这里——**参数名不能跨厂商复用**：
     * 千问只认 `enable_search`，混元只认 `enable_web_search`，
     * 智谱要用 `tools[].web_search`，而豆包文档明确说 `tools` 和
     * `web_search_options` 同时出现时搜索不生效。传错字段的后果是
     * 400 或静默不搜，两种都比「开不了」更难排查。
     */
    protected open fun ChatCompletionRequest.applyWebSearch(
        webSearch: Boolean
    ): ChatCompletionRequest =
        if (webSearch) copy(webSearchOptions = WebSearchOptions()) else this

    @Deprecated("改用 chat(messages, model, systemPrompt, enableThinking)")
    override suspend fun chat(message: String, model: String): String =
        chat(
            messages = listOf(ChatMessage.user(message)),
            model = model,
            systemPrompt = null,
            enableThinking = true,
            jsonMode = false,
            webSearch = false,
        )
}
