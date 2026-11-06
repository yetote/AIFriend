package com.ui.agent.data

/**
 * 价格档位，只用于上色。
 *
 * 特意不在 ui 层直接用 `core.ai.PriceTier`：ui:home 依赖 ui:agent，
 * 但不依赖 core:ai，自己再定义一份又会让两处颜色逻辑分叉。
 * 这里放一个纯展示用的档位，两边的页面都能用。
 */
enum class UIPriceTone {
    /** 免费或极便宜。 */
    CHEAP,

    /** 日常够用。 */
    NORMAL,

    /** 贵。 */
    EXPENSIVE
}

/**
 * 创建页里展示的一个模型。
 *
 * 官方厂商这边 [price] 恒为 null（APK 里没有可靠的官方价目），
 * 聚合平台则尽量填上 —— 用户就是靠它判断贵不贵的。
 */
data class UIModelOption(
    /** 真正的模型 id，选中时回传给 ViewModel。 */
    val id: String,
    /** 显示的短名：去掉厂商前缀，避免在分组里重复。 */
    val label: String,
    /** 已格式化好的价格文本，例如 `¥2 入 / ¥8 出 /M`；null 表示没查到价格。 */
    val price: String? = null,
    /** 价格档位，用来给价格文字着色，不用比数字也能看出便宜还是贵。 */
    val tone: UIPriceTone? = null,
    /**
     * 超出常规上下文后更贵的档位说明，一行一档，例如 `超 128k：¥1.8 入 / ¥14.4 出 /M`。
     *
     * 空表示不分档，UI 不占额外高度。
     */
    val bands: List<String> = emptyList(),
    /**
     * 价格排序权重：综合成本指数，越小越便宜；null 表示没查到价格。
     *
     * 只用于排序，不显示给用户 —— 用户看的是 [price] 那行文本。
     * 放在这里是因为「全部」这个跨厂商视图要在 UI 层按价格重排，
     * 而 ui 层拿不到 core 的 ModelPrice。
     */
    val sortWeight: Double? = null,
    /** 所属厂商。跨厂商的「全部」视图里用来补一行来源，避免同名模型分不清。 */
    val vendorName: String? = null
)

/**
 * 聚合平台里同一厂商的一组模型。
 *
 * 硅基流动一个列表里塞了十几家厂商（DeepSeek、Qwen、GLM、Kimi…），
 * 平铺出来几十行全是 `vendor/model` 的字符串，很难找；
 * 按厂商分组之后才像一个模型市场。
 */
data class UIModelGroup(
    val vendor: String,
    val models: List<UIModelOption>,
    /** 该组「起价」，例如 `¥2`；null 表示这组查不到价格。 */
    val from: String? = null
) {
    /** 是不是跨厂商汇总的那一栏（「全部」）。 */
    val isAll: Boolean get() = ALL_VENDOR_LABEL == vendor
}

/** 「全部」那一栏的标题，判定和渲染共用一处，免得改字漏改判断。 */
const val ALL_VENDOR_LABEL: String = "全部"

