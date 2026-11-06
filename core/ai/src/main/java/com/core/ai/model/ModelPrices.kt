package com.core.ai.model

/**
 * 按服务商查模型价格。
 *
 * 两条数据来源，能力差别很大，所以集中在这里做适配：
 *
 * 1. **OpenRouter**：`GET /models` 的每个模型自带 `pricing` 字段
 *    （每 token 的美元字符串），能实时拿到，精度最高。
 * 2. **硅基流动**：`GET /models` 只返回 id 和 owned_by，**没有价格**。
 *    价格只在官方定价页上，所以靠 [LivePriceCache]（每天抓一次）+ 内置快照兜底。
 *
 * 查不到就返回 null，UI 不显示价格 —— 显示错的价比不显示更糟。
 */
object ModelPrices {

    /**
     * 从 OpenRouter 风格的 `pricing` 对象取价格。
     *
     * 原始字段是「每 token 美元」的字符串，例如 `"0.0000002"`，
     * 乘 1e6 就是每百万 token 的美元价。
     * 有些模型只标 `prompt`/`completion`，有些还带缓存价，这里只取前两者。
     */
    fun fromOpenRouter(
        prompt: String?,
        completion: String?
    ): ModelPrice? {
        val input = prompt?.toDoubleOrNull() ?: return null
        val output = completion?.toDoubleOrNull() ?: return null
        if (input < 0.0 || output < 0.0) return null
        return ModelPrice(
            inputPerMillion = input * TOKENS_PER_MILLION,
            outputPerMillion = output * TOKENS_PER_MILLION
        )
    }

    /**
     * 查一个模型在某服务商下的价格。
     *
     * [providerId] 用 `AIProviderCatalog` 的 id 判断数据来源，
     * 避免这里依赖 repository 模块的类。
     */
    fun lookup(providerId: String, modelId: String): ModelPrice? {
        val normalized = modelId.trim()
        if (normalized.isEmpty()) return null
        return when (providerId) {
            PROVIDER_SILICONFLOW ->
                // 先看今天抓到的价，没有再用内置快照。
                // 内置快照是定价页某一刻的抄本，站点调价后就对不上，
                // 所以它只用来顶没网、或者当天还没抓到的首屏。
                LivePriceCache.snapshot().prices[normalized]
                    ?: SiliconFlowPriceSnapshot.prices[normalized]

            else -> null
        }
    }

    /** 该服务商的价格用什么货币标注。 */
    fun currencyOf(providerId: String): String = when (providerId) {
        PROVIDER_SILICONFLOW -> ModelPrice.CNY
        else -> ModelPrice.USD
    }

    /**
     * 该服务商的价格是否来自「平台接口之外」。
     *
     * 硅基流动的 `/models` 从不返回价格，价格一律来自定价页抓取或内置快照，
     * 所以恒为 true —— UI 据此加一句来源注脚，免得用户以为是平台此刻的精确报价。
     */
    fun isSnapshot(providerId: String): Boolean = providerId == PROVIDER_SILICONFLOW

    /**
     * 该服务商的价格能不能主动刷新。
     *
     * 只有硅基流动符合：它的价格在定价页上，我们能抓。
     * OpenRouter 的价格随 `GET /models` 一起来，跟着「测试并保存」就更新了，
     * 给它一个独立的刷新按钮只会让用户以为要多点一次才生效。
     */
    fun canRefresh(providerId: String): Boolean = providerId == PROVIDER_SILICONFLOW

    /**
     * 价格对应的日期；实时接口返回 null。
     *
     * 抓到过就用抓取日期，没抓到过就用内置快照的日期 ——
     * 注脚上写的日子必须和列表里的价对应，否则比不写更容易误导。
     */
    fun snapshotDate(providerId: String): String? {
        if (!isSnapshot(providerId)) return null
        val snapshot = LivePriceCache.snapshot()
        if (snapshot.isUsable) return formatDate(snapshot.fetchedAt)
        return SiliconFlowPriceSnapshot.CAPTURED_AT
    }

    private fun formatDate(millis: Long): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return format.format(java.util.Date(millis))
    }

    private const val PROVIDER_SILICONFLOW = "siliconflow"
    private const val TOKENS_PER_MILLION = 1_000_000.0
}
