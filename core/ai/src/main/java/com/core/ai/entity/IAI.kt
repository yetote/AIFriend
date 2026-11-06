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

    /** 价格标注用的货币符号，默认美元。 */
    val priceCurrency: String
        get() = ModelPrice.USD

    /**
     * 多轮对话：传入完整历史（不含 system），由实现方按厂商协议补 system 并转换为请求体。
     * [enableThinking] 为 false 时尽量关闭深度思考（部分厂商只能降档或忽略）。
     * 未接入的厂商默认抛出异常，避免静默返回错误内容。
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String? = null,
        enableThinking: Boolean = true
    ): String = throw UnsupportedOperationException("「$brandName」暂未接入对话")

    @Deprecated("改用 chat(messages, model, systemPrompt, enableThinking)")
    suspend fun chat(message: String, model: String): String
}
