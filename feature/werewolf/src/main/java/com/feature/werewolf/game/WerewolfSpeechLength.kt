package com.feature.werewolf.game

/**
 * 一句公开发言的目标长度。
 *
 * 之前发言长度只有一个写死的 200 字上限，想改只能去提示词设置里手写
 * 「发言不超过三句话」——那是把**每个玩家都会需要**的配置推给了自定义提示词。
 * 现在做成三档，同时管两处：
 * - **引擎裁剪**：模型收不住话时按档位上限在句末截断（见 `WerewolfDirector.truncateAtSentence`）；
 * - **提示词要求**：直接告诉模型「约 X 字以内」，让它自己收着写，而不是等着被掐。
 *
 * [STANDARD] 的 200 字就是原来的上限，老玩家行为不变。
 */
enum class WerewolfSpeechLength(
    val label: String,
    /** 一句发言的目标上限（字）。引擎裁剪与提示词共用这一个数。 */
    val chars: Int,
    /** 一句话说明这一档是什么体验，给开局页用。 */
    val hint: String,
) {
    SHORT("简短", 80, "一两句话讲清判断，牌局节奏最快"),
    STANDARD("标准", 200, "默认。能展开一点推理，又不拖节奏"),
    LONG("详细", 400, "留给爱长篇论证的强模型，整局会更久"),
    ;

    /** 上下文里一天发言的字数预算，随档位缩放（标准档 = 原来的 3200）。 */
    val contextBudget: Int get() = chars * 12 + 800

    companion object {
        /** 认不出的名字按标准处理：存档里可能来自更高版本，别让它整局读不出来。 */
        fun of(name: String?): WerewolfSpeechLength =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}
