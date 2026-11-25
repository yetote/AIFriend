package com.core.ai.model

/**
 * 一个模型「花不花钱」的档位。
 *
 * 只分档、不报价：批量创建要回答的问题是「这次建出来的角色会不会烧钱」，
 * 不需要精确到小数点。真要报价，能拿到的是 [ModelPrice]，显示在模型列表里。
 *
 * 为什么不用价格直接判断，而是先折成档位：绝大多数官方厂商（方舟、百炼、
 * TokenHub、MiMo）**没有公开的价格接口**，`GET /models` 只给模型 ID。
 * 能拿到的价格只有 OpenRouter（响应自带 pricing）和硅基流动（定价页抓取）。
 * 剩下的只能看模型名里有没有 `flash` / `lite` / `mini` 这类档位词——
 * 所以 [UNKNOWN] 必须是一等公民，而不是「大概免费」。
 */
enum class ModelCost {
    /** 平台标价为零（OpenRouter 的 `:free`、硅基流动的免费模型）。 */
    FREE,

    /** 轻量档：真价格算出来便宜，或模型名里带 flash / lite / mini 之类的档位词。 */
    LIGHT,

    /** 常规档。 */
    STANDARD,

    /** 旗舰档：pro / max / ultra 这一类。 */
    PREMIUM,

    /** 判断不了。**不等于免费**——没有依据的时候宁可不让它进「免费池」。 */
    UNKNOWN
}

/**
 * 模型档位的判定。
 *
 * 两条依据，优先级明确：
 * 1. 有价格就用价格（平台自己的报价，最可信）；
 * 2. 没有价格才看模型名里的档位词，且只认整段匹配 ——
 *    `MiniMax-M3` 里的 `mini` 是厂商名，不是「mini 档」，
 *    按子串匹配会把它误判成轻量模型。
 */
object ModelCosts {

    /**
     * 判断一个模型属于哪一档。
     *
     * [price] 默认走 [ModelPrices.lookup]（目前覆盖硅基流动的快照）；
     * OpenRouter 这类「价格跟着模型列表一起下发」的平台，调用方应该把手上的
     * [ModelPrice] 直接传进来，不要在这里再查一次。
     */
    fun of(
        providerId: String,
        modelId: String,
        price: ModelPrice? = ModelPrices.lookup(providerId, modelId)
    ): ModelCost {
        val trimmed = modelId.trim()
        if (trimmed.isEmpty()) return ModelCost.UNKNOWN
        if (price != null) return fromPrice(price, ModelPrices.currencyOf(providerId))
        return fromName(trimmed)
    }

    /** 有真实报价时的判定，直接复用 [ModelPrice.tier] 的汇率折算规则。 */
    fun fromPrice(price: ModelPrice, currency: String): ModelCost = when {
        price.isFree -> ModelCost.FREE
        else -> when (price.tier(currency)) {
            PriceTier.CHEAP -> ModelCost.LIGHT
            PriceTier.NORMAL -> ModelCost.STANDARD
            PriceTier.EXPENSIVE -> ModelCost.PREMIUM
        }
    }

    /**
     * 只能看名字时的判定。
     *
     * 先判旗舰再判轻量：`gemini-3-flash-pro` 这种混着写的，按贵的处理更安全 ——
     * 这个函数唯一的用途是「别让贵的混进免费池」，宁可错杀。
     */
    fun fromName(modelId: String): ModelCost {
        val tokens = TOKEN_SPLIT.split(modelId.lowercase()).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ModelCost.UNKNOWN
        if (tokens.any { it in PREMIUM_MARKS }) return ModelCost.PREMIUM
        // OpenRouter 的免费模型就是在 ID 后面加 `:free`，切完正好留下 free 这一段
        if (tokens.any { it in FREE_MARKS }) return ModelCost.FREE
        if (tokens.any { it in BUDGET_MARKS }) return ModelCost.LIGHT
        return ModelCost.UNKNOWN
    }

    /**
     * 「不允许付费」时能不能选它。
     *
     * [ModelCost.UNKNOWN] 不算 —— 查不到价的模型很可能就是最贵的那档，
     * 把它当免费放进去，等于用户关掉了开关却还是在花钱。
     */
    fun isBudget(cost: ModelCost): Boolean =
        cost == ModelCost.FREE || cost == ModelCost.LIGHT

    /** 按 `-` `_` `.` `/` `:` 空格和中文边界切词，用来做整段匹配。 */
    private val TOKEN_SPLIT = Regex("[^a-z0-9\\u4e00-\\u9fa5]+")

    private val FREE_MARKS = setOf("free", "免费")

    private val PREMIUM_MARKS = setOf(
        "pro", "max", "ultra", "opus", "large", "premier", "premium",
        "旗舰", "专业版"
    )

    /**
     * 只收「便宜档」的词。
     *
     * `turbo` 刻意不在里面：它是**延迟档**不是价格档 —— 方舟的
     * doubao-seed-2.1-turbo 就比 lite 贵一档（1.5/7.5 对 0.4/1.35 元每百万）。
     * 这类词放进「免费池」，用户关了付费开关照样会花钱。
     */
    private val BUDGET_MARKS = setOf(
        "flash", "lite", "mini", "nano", "small", "air", "tiny", "gemma",
        "极速", "轻量"
    )
}
