package com.feature.werewolf.prompt.boards

import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig

/**
 * 12 人狼王守卫局：狼王 + 3 狼 + 预女猎守 + 4 民，开警长。
 *
 * 狼王 + 守卫 + 警长三要素齐备的经典 12 人竞技板子：警徽归属、守救博弈、狼王开枪追轮次三线交织。
 */
internal object King12Board : BoardProfile {

    override val key = WerewolfBoards.KING_12

    override fun text(config: WerewolfConfig) = BoardText(
        common = listOf(
            "12 人狼王守卫局（狼王 + 3 狼 + 预女猎守 + 4 民，开警长）：" +
                "4 狼 4 神 4 民且带警徽与狼王枪，白天推掉狼王会立刻换走一张牌，" +
                "好人若不先把毒药、守卫和轮次算清，很容易在残局被狼王一枪打穿屠边。"
        ),
        wolves = listOf(
            "抢下警徽就能把发言和投票节奏握在狼队手里；" +
                "狼王被抗推出局时，优先带走已明跳的预言家、守卫或没开毒的女巫，" +
                "用一换一强行把神边轮次压到斩杀线。"
        ),
    )
}
