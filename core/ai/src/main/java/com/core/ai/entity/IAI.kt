package com.core.ai.entity

import com.core.ai.model.ChatDelta
import com.core.ai.model.ChatMessage
import com.core.ai.model.ChatReply
import com.core.ai.model.GatewayModel
import com.core.ai.model.ModelPrice
import com.core.ai.model.TokenCounter
import com.core.ai.model.TokenUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface IAI {
    suspend fun requestModel()

    var modelSet: MutableSet<String>

    var brandName: String
    var company: String

    /**
     * 带价格的模型列表，聚合平台用。
     *
     * 官方厂商（DeepSeek、豆包…）走自家的计费体系，APK 里没有可靠的价目，
     * 所以默认返回空列表，UI 那边就不显示价格 —— 编一个价格比不显示更糟。
     */
    val gatewayModels: List<GatewayModel>
        get() = emptyList()

    /** 这个服务商是有价格的聚合平台还是官方厂商。 */
    val isGateway: Boolean
        get() = false

    /**
     * 现在能不能真的发出请求：Key 之类的前置条件是否齐了。
     *
     * 只在「发请求前拦一下」的场景用（探活、开局检查）。没有 Key 还去发，
     * 拿回来的一定是 401，用户看到「API Key 无效」——可他压根没填过，
     * 该说的是「还没配 Key」。两者的处理动作完全不同。
     */
    fun hasCredentials(): Boolean = true

    /** 价格标注用的货币符号，默认美元。 */
    val priceCurrency: String
        get() = ModelPrice.USD

    /**
     * 该厂商是否支持 `response_format = {"type":"json_object"}`。
     *
     * 星火和 Gemini 的 OpenAI 兼容层不支持这个字段，硬发会被 400 拒掉。
     * 返回 false 的厂商，[com.core.ai.structured.decide] 会自动降级成
     * 「只靠提示词约束 + 解析兜底」——功能不变，只是模型跑偏的概率高一些。
     */
    val supportsJsonMode: Boolean
        get() = true

    /**
     * 该厂商能不能在对话接口里直接联网搜索。
     *
     * 默认 false：**没验证过的厂商一律当不支持**。这一条比 [supportsJsonMode] 更严格，
     * 因为联网参数的失败方式很隐蔽——有的厂商直接 400，有的静默忽略，
     * 还有的（豆包）在 `tools` 与 `web_search_options` 同时出现时静默关掉搜索。
     * 猜错的代价是「搜索式看起来开了，其实一个字都没查」，用户完全看不出。
     *
     * 目前确认支持的：豆包（`web_search_options`）、千问（`enable_search`）、
     * 混元（`enable_web_search`）、智谱（`tools[].web_search`）。
     * DeepSeek 的对话接口没有内置搜索（只有独立的 `/tools/search`，需要自己编排工具循环）；
     * Kimi 的 `$web_search` 已于 2026-10 下线，改成独立 REST 接口，同样要自己编排。
     */
    val supportsWebSearch: Boolean
        get() = false

    /**
     * 多轮对话：传入完整历史（不含 system），由实现方按厂商协议补 system 并转换为请求体。
     * [enableThinking] 为 false 时尽量关闭深度思考（部分厂商只能降档或忽略）。
     * [jsonMode] 为 true 时要求模型输出 JSON 对象，仅在 [supportsJsonMode] 为 true 时生效。
     * [webSearch] 为 true 时请求服务端联网检索，仅在 [supportsWebSearch] 为 true 时生效。
     * 未接入的厂商默认抛出异常，避免静默返回错误内容。
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String? = null,
        enableThinking: Boolean = true,
        jsonMode: Boolean = false,
        webSearch: Boolean = false,
    ): String = throw UnsupportedOperationException("「$brandName」暂未接入对话")

    /**
     * 和 [chat] 一样发起一次性请求，但把 token 用量一起带出来。
     *
     * 默认实现用 [TokenCounter] 估算：支持 usage 的服务商会覆盖它给真值，
     * 不支持的也不会让上层拿到 0——界面照样能显示一个量级并标「约」。
     */
    suspend fun chatWithUsage(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String? = null,
        enableThinking: Boolean = true,
        jsonMode: Boolean = false,
        webSearch: Boolean = false,
    ): ChatReply {
        val text = chat(messages, model, systemPrompt, enableThinking, jsonMode, webSearch)
        return ChatReply(
            text = text,
            usage = TokenUsage(
                input = TokenCounter.estimateInput(messages, systemPrompt),
                output = TokenCounter.estimate(text),
                estimated = true,
            ),
        )
    }

    /**
     * 流式对话：以增量片段的形式返回正文与思考过程。
     *
     * 默认实现退化成「一次性请求，再推一整段」——这样即使某个厂商没接流式，
     * 上层也不用为它单开一条分支，界面照样能渲染，只是没有逐字效果。
     * OpenAI 兼容的厂商都在 [OpenAICompatibleAI] 里覆盖成了真正的 SSE。
     *
     * 实现方要保证：出错时抛出异常，而不是安静地结束。上层靠这一点区分
     * 「说完了」和「被切断了」——前者要落库，后者要留个重试入口。
     */
    fun chatStream(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String? = null,
        enableThinking: Boolean = true,
        jsonMode: Boolean = false,
        webSearch: Boolean = false,
    ): Flow<ChatDelta> = flow {
        emit(
            ChatDelta(
                content = chat(
                    messages = messages,
                    model = model,
                    systemPrompt = systemPrompt,
                    enableThinking = enableThinking,
                    jsonMode = jsonMode,
                    webSearch = webSearch,
                )
            )
        )
    }

    @Deprecated("改用 chat(messages, model, systemPrompt, enableThinking)")
    suspend fun chat(message: String, model: String): String
}
