package com.feature.werewolf.game

/**
 * 一位 AI 每一步最多能想多久。
 *
 * 为什么需要一个显式的上限：模型端的「深度思考」没有时间概念，实测有座位一局里
 * 连着想十几分钟，最后给出的还是很差的答案。它不会报错、不会中断，牌桌就那么停着，
 * 用户只能盯着一个不动的思考框，分不清是 AI 在深思还是这局已经死了。
 * 传输层那 8 分钟 / 30 分钟的超时是**兜底**，不是节奏控制——它只管「链路有没有断」。
 *
 * 思考和发言不是一回事，**思考是主要的限时对象**：
 * - **思考**（投票、查验、用药、下刀，以及发言步开头那段没吐字的时间）
 *   想太久说明模型在反复权衡，多等一分钟换来的往往不是更好的选择，而是更犹豫的选择。
 *   决策步全程受它管；发言步从请求开始到吐出第一个字受它管。
 * - **发言**正常情况下几十秒就吐完，但要留一道**安全上限**：有的模型不走
 *   `reasoning_content`，而是把思考直接写进正文（`<think>` 已在传输层拆掉，
 *   但也有不带标签、直接「边想边写」的），这时看门狗会误以为已经在发言，
 *   发言段若不限，思考就彻底没了上限——实测狼人夜聊能想十几二十分钟。
 *   上限给得足够宽（正常一段发言远用不到），超了就保留已经说出的半句。
 *
 * [UNLIMITED] 是留给「我就想看它们慢慢吵」的：0 表示不设限，退回到传输层超时。
 */
enum class WerewolfTurnLimit(
    val label: String,
    /** 一步思考的上限（决策步全程、发言步吐字之前），0 表示不设限。 */
    val decisionSeconds: Int,
    /** 发言段（吐出第一个字之后）的安全上限，0 表示不设限。 */
    val speechSeconds: Int,
) {
    FAST("快速", decisionSeconds = 30, speechSeconds = 60),
    STANDARD("标准", decisionSeconds = 60, speechSeconds = 90),
    RELAXED("宽松", decisionSeconds = 120, speechSeconds = 150),
    UNLIMITED("不限", decisionSeconds = 0, speechSeconds = 0),
    ;

    val decisionMs: Long get() = decisionSeconds * 1000L

    val speechMs: Long get() = speechSeconds * 1000L

    /** 一句话说清这一档会发生什么，给设置面板用。 */
    val hint: String
        get() = when (this) {
            FAST -> "思考 30 秒、发言最多 1 分钟。慢模型会被频繁兜底，牌局能跑得很快"
            STANDARD -> "思考 1 分钟、发言最多 1 分半。多数对话模型够用"
            RELAXED -> "思考 2 分钟、发言最多 2 分半。留给想得多的模型，代价是整局变慢"
            UNLIMITED -> "不设上限，只受网络超时约束。一个座位就可能卡十几分钟"
        }

    companion object {
        /** 认不出的名字按标准处理：存档里可能来自更高版本，别让它整局读不出来。 */
        fun of(name: String?): WerewolfTurnLimit =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}
