package com.core.ai.entity

import com.core.ai.model.ChatMessage
import com.core.ai.model.GatewayModel
import com.core.ai.model.ModelPrice

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
     * 多轮对话：传入完整历史（不含 system），由实现方按厂商协议补 system 并转换为请求体。
     * [enableThinking] 为 false 时尽量关闭深度思考（部分厂商只能降档或忽略）。
     * [jsonMode] 为 true 时要求模型输出 JSON 对象，仅在 [supportsJsonMode] 为 true 时生效。
     * 未接入的厂商默认抛出异常，避免静默返回错误内容。
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String? = null,
        enableThinking: Boolean = true,
        jsonMode: Boolean = false
    ): String = throw UnsupportedOperationException("「$brandName」暂未接入对话")

    @Deprecated("改用 chat(messages, model, systemPrompt, enableThinking)")
    suspend fun chat(message: String, model: String): String
}
