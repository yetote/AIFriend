package com.feature.werewolf.game

/**
 * 一位 AI 每一步最多能想多久。
 *
 * 为什么需要一个显式的上限：模型端的「深度思考」没有时间概念，实测有座位一局里
 * 连着想十几分钟，最后给出的还是很差的答案。它不会报错、不会中断，牌桌就那么停着，
 * 用户只能盯着一个不动的思考框，分不清是 AI 在深思还是这局已经死了。
 * 传输层那 8 分钟 / 30 分钟的超时是**兜底**，不是节奏控制——它只管「链路有没有断」。
 *
 * 决策和发言分开给，因为两者性质不同：
 * - **决策**（投票、查验、用药、下刀）只要一个 JSON，想太久说明模型在反复权衡，
 *   多等一分钟换来的往往不是更好的选择，而是更犹豫的选择。
 * - **发言**是流式的，时间基本花在「说」上，按字算才合理，所以给得更宽。
 *
 * [UNLIMITED] 是留给「我就想看它们慢慢吵」的：0 表示不设限，退回到传输层超时。
 */
enum class WerewolfTurnLimit(
    val label: String,
    /** 一步决策的上限，0 表示不设限。 */
    val decisionSeconds: Int,
    /** 一次发言的上限，0 表示不设限。 */
    val speechSeconds: Int,
) {
    FAST("快速", decisionSeconds = 30, speechSeconds = 75),
    STANDARD("标准", decisionSeconds = 60, speechSeconds = 150),
    RELAXED("宽松", decisionSeconds = 120, speechSeconds = 300),
    UNLIMITED("不限", decisionSeconds = 0, speechSeconds = 0),
    ;

    val decisionMs: Long get() = decisionSeconds * 1000L

    val speechMs: Long get() = speechSeconds * 1000L

    /** 一句话说清这一档会发生什么，给设置面板用。 */
    val hint: String
        get() = when (this) {
            FAST -> "决策 30 秒、发言 75 秒。慢模型会被频繁兜底，牌局能跑得很快"
            STANDARD -> "决策 1 分钟、发言 2 分半。多数对话模型够用"
            RELAXED -> "决策 2 分钟、发言 5 分钟。留给想得多的模型，代价是整局变慢"
            UNLIMITED -> "不设上限，只受网络超时约束。一个座位就可能卡十几分钟"
        }

    companion object {
        /** 认不出的名字按标准处理：存档里可能来自更高版本，别让它整局读不出来。 */
        fun of(name: String?): WerewolfTurnLimit =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}
