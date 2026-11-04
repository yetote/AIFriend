package com.feature.friend.model

/**
 * 一个可选的模型提供方（对应 core:ai 里的一种 IAI 实现）。
 */
data class Provider(
    val brand: String,
    val company: String,
    val defaultModel: String,
    /** 模型列表拉取失败时的兜底选项，避免创建页空着。 */
    val fallbackModels: List<String>,
    /** 是否支持关闭深度思考；星火、Gemini 目前没有对应参数。 */
    val thinkingSwitchable: Boolean = true
)
