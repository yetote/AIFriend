package com.core.ai.model

import kotlin.math.abs

/**
 * 分档价格里的一档。
 *
 * 有些模型按**上下文长度**分段计价：短上下文一个价，超过某个长度之后更贵。
 * 例如 `Qwen/Qwen3.5-27B` 的输入 `[0, 128k)` 是 ¥0.6、`[128k, +∞)` 是 ¥1.8。
 *
 * [ModelPrice] 里的基础价永远是最低档（也就是常规上下文的价格），
 * 更贵的那几档放在这里，让用户知道「聊长了会变贵」，但不会在列表里
 * 一上来就看到最贵的数字。
 */
data class PriceBand(
    /** 这一档从第几个输入 token 开始生效。128k → 128000。 */
    val fromTokens: Long,
    /** 输入每百万 token 的价格。 */
    val inputPerMillion: Double,
    /** 输出每百万 token 的价格。 */
    val outputPerMillion: Double
)

/**
 * 一个模型的价格：每百万 token 的输入 / 输出费用。
 *
 * 单位统一成「定价页面上的标价货币 / 百万 token」。
 * 聚合平台的原始数据五花八门（OpenRouter 给的是每 token 美元的字符串，
 * 硅基流动只给中文 HTML 页面），所以各自在拿到数据时就折算好，
 * UI 只负责渲染，不再关心单位和换算。
 */
data class ModelPrice(
    /** 输入（prompt）每百万 token 的价格。 */
    val inputPerMillion: Double,
    /** 输出（completion）每百万 token 的价格。 */
    val outputPerMillion: Double,
    /**
     * 超出常规上下文之后更贵的档位，按起点升序。
     *
     * 空表示这个模型不分档（绝大多数模型如此）。
     */
    val bands: List<PriceBand> = emptyList()
) {
    /** 输入输出都标 0，说明平台把这模型列为免费。 */
    val isFree: Boolean
        get() = inputPerMillion <= 0.0 && outputPerMillion <= 0.0

    /** 是否按上下文长度分段计价。 */
    val isTiered: Boolean
        get() = bands.isNotEmpty()

    /**
     * 用来横向比「贵不贵」的单一指标，单位是该价格体系的货币。
     *
     * 输出权重高于输入：聊天场景里输出 token 才是花费大头，
     * 按平均算会让「输入极便宜、输出极贵」的模型显得比实际划算。
     *
     * 只算基础档（最低档）：排序比的是「正常用起来多少钱」，
     * 拿长上下文那档去比会把分档模型全部排到后面，失去参考意义。
     */
    val costIndex: Double
        get() = inputPerMillion * INPUT_WEIGHT + outputPerMillion * OUTPUT_WEIGHT

    /**
     * 折算成显示文本，例如 `¥2 入 / ¥8 出 /M`。
     *
     * [currency] 由数据来源决定 —— 硅基流动标人民币、OpenRouter 标美元，
     * 在源头就知道单位，比在这里猜可靠。
     */
    fun format(currency: String): String {
        if (isFree) return "免费"
        return "${brief(currency, inputPerMillion)} 入 / ${brief(currency, outputPerMillion)} 出 /M"
    }

    /**
     * 更贵那几档的说明，一行一档，例如 `超 128k：¥1.8 入 / ¥14.4 出 /M`。
     *
     * 不分档返回空列表，UI 就不用额外占一行。
     */
    fun bandLabels(currency: String): List<String> = bands.map { band ->
        "超 ${formatTokens(band.fromTokens)}：" +
            "${brief(currency, band.inputPerMillion)} 入 / " +
            "${brief(currency, band.outputPerMillion)} 出 /M"
    }

    /**
     * 经济性档位。
     *
     * 阈值按美元口径标定，换到人民币就按汇率折算后再比 —— 直接用同一组
     * 数字的话，所有人民币标价的模型都会被误判成「贵」。
     */
    fun tier(currency: String): PriceTier {
        if (isFree) return PriceTier.CHEAP
        val usd = if (currency == CNY) costIndex / CNY_PER_USD else costIndex
        return when {
            usd < CHEAP_THRESHOLD -> PriceTier.CHEAP
            usd < EXPENSIVE_THRESHOLD -> PriceTier.NORMAL
            else -> PriceTier.EXPENSIVE
        }
    }

    private fun brief(currency: String, value: Double): String {
        val rounded = if (abs(value - value.toInt()) < 0.005) {
            value.toInt().toString()
        } else {
            String.format("%.2f", value).trimEnd('0').trimEnd('.')
        }
        return "$currency$rounded"
    }

    /** 128000 → `128k`；1000000 → `1M`；零碎数字原样输出。 */
    private fun formatTokens(tokens: Long): String {
        if (tokens <= 0L) return "0"
        if (tokens % 1_000L != 0L) return tokens.toString()
        val thousands = tokens / 1_000L
        return if (thousands % 1_000L == 0L) "${thousands / 1_000L}M" else "${thousands}k"
    }

    companion object {
        /** 人民币标价的货币符号。 */
        const val CNY = "¥"

        /** 美元标价的货币符号。 */
        const val USD = "$"

        /** 免费模型。 */
        val FREE = ModelPrice(0.0, 0.0)

        private const val INPUT_WEIGHT = 0.3
        private const val OUTPUT_WEIGHT = 0.7

        /** 便宜 / 贵 的分档阈值，单位美元 / 百万 token。 */
        private const val CHEAP_THRESHOLD = 1.0
        private const val EXPENSIVE_THRESHOLD = 6.0

        /** 人民币折美元用的粗略汇率，只用于分档，不用于显示价格。 */
        private const val CNY_PER_USD = 7.2
    }
}

/**
 * 一个模型的经济性档位，用于在列表上着色，让用户一眼看出便宜还是贵。
 */
enum class PriceTier {
    /** 免费，或者低到可以忽略。 */
    CHEAP,

    /** 日常够用。 */
    NORMAL,

    /** 贵，适合偶尔用。 */
    EXPENSIVE
}
