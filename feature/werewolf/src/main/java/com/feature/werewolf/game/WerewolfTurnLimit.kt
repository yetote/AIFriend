package com.feature.werewolf.game

/**
 * 一位 AI 每一步最多能想多久。
 *
 * 为什么需要一个显式的上限：模型端的「深度思考」没有时间概念，实测有座位一局里
 * 连着想十几分钟，最后给出的还是很差的答案。它不会报错、不会中断，牌桌就那么停着，
 * 用户只能盯着一个不动的思考框，分不清是 AI 在深思还是这局已经死了。
 * 传输层那 8 分钟 / 30 分钟的超时是**兜底**，不是节奏控制——它只管「链路有没有断」。
 *
 * 思考和发言不是一回事，但**只有思考需要限时**：
 * - **思考**（投票、查验、用药、下刀，以及发言步开头那段没吐字的时间）
 *   想太久说明模型在反复权衡，多等一分钟换来的往往不是更好的选择，而是更犹豫的选择。
 *   决策步全程受它管；发言步从请求开始到吐出第一个字受它管。
 * - **发言**对 AI 来说不存在「说」的额外耗时——正文往外流就是吐 token，
 *   和思考走同一条链路，吐多快取决于模型，掐它只会把话说一半。
 *   真正的故障（流挂住、喋喋不休）由传输层兜底（静默 5 分钟、总 30 分钟）。
 *   所以各档位都不设发言上限；字段保留是为了有需要时能自定义，0 = 不限。
 *
 * [UNLIMITED] 是留给「我就想看它们慢慢吵」的：0 表示不设限，退回到传输层超时。
 */
enum class WerewolfTurnLimit(
    val label: String,
    /** 一步思考的上限（决策步全程、发言步吐字之前），0 表示不设限。 */
    val decisionSeconds: Int,
    /** 发言段上限，0 表示不设限。各档位默认不限——AI 说话即吐 token，掐它没有意义。 */
    val speechSeconds: Int,
) {
    FAST("快速", decisionSeconds = 30, speechSeconds = 0),
    STANDARD("标准", decisionSeconds = 60, speechSeconds = 0),
    RELAXED("宽松", decisionSeconds = 120, speechSeconds = 0),
    UNLIMITED("不限", decisionSeconds = 0, speechSeconds = 0),
    ;

    val decisionMs: Long get() = decisionSeconds * 1000L

    val speechMs: Long get() = speechSeconds * 1000L

    /** 一句话说清这一档会发生什么，给设置面板用。 */
    val hint: String
        get() = when (this) {
            FAST -> "思考 30 秒、发言不限。慢模型会被频繁兜底，牌局能跑得很快"
            STANDARD -> "思考 1 分钟、发言不限。多数对话模型够用"
            RELAXED -> "思考 2 分钟、发言不限。留给想得多的模型，代价是整局变慢"
            UNLIMITED -> "不设上限，只受网络超时约束。一个座位就可能卡十几分钟"
        }

    companion object {
        /** 认不出的名字按标准处理：存档里可能来自更高版本，别让它整局读不出来。 */
        fun of(name: String?): WerewolfTurnLimit =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}
