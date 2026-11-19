package com.feature.werewolf.engine

import com.feature.werewolf.game.Phase

/**
 * 某个座位**此刻正在生成**的内容。
 *
 * 和 [WerewolfMessage] 的分工：消息是「已经说完、已经进 transcript 的话」，
 * 这里是「还没说完、只存在于界面上的半句」。两者绝对不能混：
 * 消息要进上下文、要落档、要被朗读；半句话任何一件都不能做——
 * 否则下一轮别的 AI 会看到一句说到一半的话，存档里也会多出一个残缺的字。
 *
 * [reasoning] 是模型的思维链。默认**只有观战视角**看得到：线下牌桌上听不到别人的心里话，
 * 参与了这局就不该看得见。参与局里用户可以自己在牌桌顶栏把「思考」打开——那等于读心，
 * 是一种明写在界面上的作弊开关（见 `WerewolfRoute.toLive` 的 `showThinking`）。
 */
data class WerewolfLive(
    val seat: Int,
    /** 产生这段内容时所在的阶段。界面靠它判断这段话是不是隐私。 */
    val phase: Phase,
    /** 已经生成的正文。为空表示模型还在思考、一个字都还没吐出来。 */
    val text: String = "",
    /** 已经生成的思考过程。没开深度思考的座位全程为空。 */
    val reasoning: String = "",
    /**
     * 这一步是「想一个结果」还是「说一段话」。
     *
     * 气泡上的秒表要靠它选时限：决策看决策秒数、发言看发言秒数，两者差一倍多。
     * **不能靠 text 是不是空来猜**——发言一开始也会先思考、一个字都还没吐，
     * 那时候 text 同样是空的，猜成决策就会拿短时限去掐一段本该按长时限走的话，
     * 表现就是「气泡时间都过了人还在说」。由引擎显式标注，UI 不再猜。
     */
    val isDecision: Boolean = false,
)
