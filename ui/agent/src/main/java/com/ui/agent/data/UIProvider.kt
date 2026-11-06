package com.ui.agent.data

/**
 * UI 层的厂商选项，避免 ui 模块反向依赖 feature 层。
 */
data class UIProvider(
    val brand: String,
    val company: String,
    val thinkingSwitchable: Boolean = true,
    /**
     * 是否是聚合平台。
     *
     * 官方厂商（DeepSeek、豆包、Kimi…）是「一家公司一个模型家族」，
     * 聚合平台（硅基流动、OpenRouter）是「一个平台几十家厂商」。
     * 两者混在一排 chip 里，用户会以为硅基流动也是个模型厂商，
     * 所以创建页要把它们分成两组摆。
     */
    val isGateway: Boolean = false
)
