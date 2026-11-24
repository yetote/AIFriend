package com.core.repository.balance

/**
 * 一家厂商的账户余量。
 *
 * 「余量」有两种形态，用一个模型装下：
 * - **金额型**：还剩多少钱（DeepSeek、Kimi、硅基流动、OpenRouter）；
 * - **配额型**：还剩多少额度，不是钱（MiniMax 的 5 小时窗口、智谱的周配额）。
 *
 * 硬塞成「余额」一个数字会让 MiniMax 那几家没法显示——
 * 它们的「剩余 80%」不是钱，标成金额就是错的。
 */
data class BalanceSnapshot(
    val providerId: String,
    val providerName: String,
    /** 主数字：金额或剩余百分比。已经带好单位，UI 直接画。 */
    val headline: String,
    /** 明细行：赠金 / 充值 / 各窗口剩余 / 重置时间…… */
    val details: List<String> = emptyList(),
    /** 紧张程度，决定配色。 */
    val tone: BalanceTone = BalanceTone.Normal,
    /** 查到的时刻（毫秒）。 */
    val fetchedAt: Long = System.currentTimeMillis(),
)

/** 余量的紧张程度。 */
enum class BalanceTone {
    /** 正常，还有不少。 */
    Normal,

    /** 快见底了，该提醒用户充钱。 */
    Low,

    /** 一点不剩。 */
    Empty,

    /** 套餐不限量（MiniMax 某些套餐的周窗口就是无限）。 */
    Unlimited,
}

/**
 * 一次余量查询的结果。
 *
 * 「这家不提供」和「查失败了」是两回事：
 * 前者是产品事实（百炼就没开放余额接口），后者是这次没成（网络抖了、Key 过期了）。
 * 混成一句话，界面上的建议就会指错方向——用户会去折腾网络，而问题在人家压根没这个接口。
 */
sealed interface BalanceResult {
    data class Ok(val snapshot: BalanceSnapshot) : BalanceResult

    /** 这家没有公开的余量查询接口。 */
    data class Unsupported(val reason: String) : BalanceResult

    /** 这次没查成，原因已经翻成人话。 */
    data class Failed(val reason: String) : BalanceResult
}

/**
 * 一家厂商的余量查询方式。
 *
 * 放在服务商目录里（[com.core.repository.settings.AIProvider.balance]），
 * 因为「谁能查余额、查哪个地址」本来就是这家厂商的属性，
 * 散在查询器里写死 switch，以后加一家就要改两个地方。
 */
data class ProviderBalanceSpec(
    /** 接口地址。 */
    val url: String,
    /** 请求方式。火山是签名的 POST，其余都是普通 GET。 */
    val method: BalanceMethod = BalanceMethod.GET,
    /** 解析方式。 */
    val parser: BalanceParserKind,
    /**
     * 鉴权头的值模板，`{key}` 会被替换成凭证。
     *
     * 放成列表是因为有厂家认得、有的不认：智谱的额度接口**必须直接传原始 Key，
     * 加 `Bearer ` 前缀会鉴权失败**，而它别的接口又只认 Bearer。
     * 与其赌一把，不如依次试，第一个不报 401 的算成功——多发一次请求，
     * 换来的是「这家到底认哪种写法」这件事不用赌。
     */
    val authHeaderValues: List<String> = listOf("Bearer {key}"),
    /** 鉴权头名称。 */
    val authHeaderName: String = "Authorization",
    /**
     * 火山这类要签名的接口，签名里用到的 Action 名。
     * 普通 GET 用不到。
     */
    val action: String? = null,
)

enum class BalanceMethod {
    /** 普通 GET，带一条鉴权头。 */
    GET,

    /** 需要自己算签名的 POST（火山 OpenAPI）。 */
    SIGNED_POST,
}

/** 各家响应结构不一样，按解析方式分派。 */
enum class BalanceParserKind {
    DEEPSEEK,
    MOONSHOT,
    MINIMAX,
    ZHIPU,
    SILICONFLOW,
    OPENROUTER,
    VOLCENGINE,
}
