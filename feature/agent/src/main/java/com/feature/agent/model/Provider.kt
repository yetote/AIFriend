package com.feature.agent.model

/**
 * 一个可选的模型提供方（对应 core:ai 里的一种 IAI 实现）。
 */
data class Provider(
    val brand: String,
    val company: String,
    val defaultModel: String,
    /** 模型列表拉取失败时的兜底选项，避免创建页空着。 */
    val fallbackModels: List<String>,
    /**
     * 自家模型的 ID 前缀；空 = 不区分（聚合平台没有自家模型）。
     *
     * 只给批量创建用：方舟、百炼、TokenHub 的模型列表里混着代理来的别家模型，
     * 那些模型没在控制台开通就调用会 404，随机挑中了就是「建完说不了话」。
     */
    val ownModelPrefixes: List<String> = emptyList(),
    /** 是否支持关闭深度思考；星火、Gemini 目前没有对应参数。 */
    val thinkingSwitchable: Boolean = true,
    /**
     * 是否是聚合平台（硅基流动、OpenRouter…）。
     *
     * 官方厂商用自己的 Key 直连，用户在开放平台后台自己看账单；
     * 聚合平台则把几十家厂商的模型堆在一个列表里，需要分组、需要标价格，
     * 所以 UI 上要把这两类分开摆，否则用户会以为「硅基流动」是另一个官方模型。
     */
    val isGateway: Boolean = false
)
