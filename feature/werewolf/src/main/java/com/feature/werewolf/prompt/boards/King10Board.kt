package com.feature.werewolf.prompt.boards

import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig

/**
 * 10 人狼王局：狼王 + 2 狼 + 预女猎守 + 3 民。
 *
 * 和守卫 10 人的区别只有一个身份，但打法完全不同：狼王那一枪让「被投出去」变成
 * 狼队的一种进攻手段。
 */
internal object King10Board : BoardProfile {

    override val key = WerewolfBoards.KING_10

    override fun text(config: WerewolfConfig) = BoardText(
        common = listOf(
            "本局有狼王：他被票出去那一枪可能直接带走最后一神、把胜负翻过来；" +
                "平民又只有 3 个，好人两边都漏风。"
        ),
        wolves = listOf(
            "平民只有 3 个，打民边更省刀；必要时**主动把狼王送出去**，" +
                "用那一枪带走刚跳出来的预言家，比夜里一刀一刀猜要快。"
        ),
    )
}
