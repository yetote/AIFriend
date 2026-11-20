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
 * [UNLIMITED] 用 `chars = 0` 表示「不裁、也不在提示词里限字数」——
 * 0 不是「零字」，和思考时限里 0 = 不限是同一个约定。
 */
enum class WerewolfSpeechLength(
    val label: String,
    /** 一句发言的目标上限（字）。引擎裁剪与提示词共用这一个数；0 = 不限。 */
    val chars: Int,
    /** 一句话说明这一档是什么体验，给开局页用。 */
    val hint: String,
) {
    SHORT("简短", 80, "一两句话讲清判断，牌局节奏最快"),
    STANDARD("标准", 200, "默认。能展开一点推理，又不拖节奏"),
    LONG("详细", 400, "留给爱长篇论证的强模型，整局会更久"),
    UNLIMITED("不限", 0, "完全不裁。适合看它们辩论，上下文会明显变大"),
    ;

    /**
     * 上下文里一天发言的字数预算，随档位缩放（标准档 = 原来的 3200）。
     *
     * 不限档按公式会塌到 800 字——发言不裁了，当天的原文反而更占地方，
     * 预算得放大而不是缩小。给一个足够宽的固定值：再宽也总要有一条线，
     * 否则 12 个话痨模型能把一次请求撑到几十 K 字。
     */
    val contextBudget: Int get() = if (chars == 0) 8000 else chars * 12 + 800

    companion object {
        /** 认不出的名字按标准处理：存档里可能来自更高版本，别让它整局读不出来。 */
        fun of(name: String?): WerewolfSpeechLength =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}
