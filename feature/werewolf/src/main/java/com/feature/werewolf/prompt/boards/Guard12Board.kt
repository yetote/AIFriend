package com.feature.werewolf.prompt.boards

import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig

/** 12 人守卫局：4 狼 + 预女猎守 + 4 民，开警长。线下标准局之一。 */
internal object Guard12Board : BoardProfile {

    override val key = WerewolfBoards.GUARD_12

    override fun text(config: WerewolfConfig) = BoardText(
        common = listOf(
            "12 人局 4 狼 4 神 4 民，还开警长：4 票已经接近绑票线，警徽落在谁手上，" +
                "谁就掌握当天的节奏。"
        ),
        wolves = listOf(
            "4 狼最怕好人抱团：先把警徽抢到手里、把发言顺序控制在自己人这边，" +
                "比多刀一个人有用。"
        ),
    )
}
