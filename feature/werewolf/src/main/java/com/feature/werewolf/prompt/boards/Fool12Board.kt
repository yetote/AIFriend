package com.feature.werewolf.prompt.boards

import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig

/**
 * 12 人白痴局：4 狼 + 预女猎白 + 4 民，开警长。
 *
 * 和守卫局的区别是容错方向：守卫防夜里的刀，白痴防白天的错。
 */
internal object Fool12Board : BoardProfile {

    override val key = WerewolfBoards.FOOL_12

    override fun text(config: WerewolfConfig) = BoardText(
        common = listOf(
            "本局有白痴：好人被投错一次不会立刻减员，但白痴翻牌后就没有投票权了——" +
                "错一次，少一张票。"
        ),
        wolves = listOf(
            "白痴翻牌后不能投票，但他**仍然算神职**：打神边时他是四个神里最好杀的那个" +
                "（没有夜间能力、不会开枪）。"
        ),
    )
}
