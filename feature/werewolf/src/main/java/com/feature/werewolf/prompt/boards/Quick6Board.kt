package com.feature.werewolf.prompt.boards

import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig

/**
 * 6 人快局：2 狼 + 预女 + 2 民。
 *
 * 两边都只有 2 个人，狼人打穿任意一边只要两刀，所以好人的容错是 0——
 * 这种板子里「再观察一天」等于直接输。
 */
internal object Quick6Board : BoardProfile {

    override val key = WerewolfBoards.QUICK_6

    override fun text(config: WerewolfConfig) = BoardText(
        common = listOf(
            "6 人局只有 2 神 2 民：狼人打穿任意一边都只要两刀，好人**一次都不能投错**。"
        ),
        wolves = listOf(
            "你们只有 2 个人：白天站同一边，但别同时咬一个人——一个冲锋、一个跟票，票型才不会整齐得反常。"
        ),
    )
}
