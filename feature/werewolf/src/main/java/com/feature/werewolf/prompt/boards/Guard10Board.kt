package com.feature.werewolf.prompt.boards

import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig

/**
 * 10 人守卫局：3 狼 + 预女猎守 + 3 民。
 *
 * 这张板子最容易被忽略的一条：**平民只有 3 个**。屠边规则下打民边只要 3 刀，
 * 比打 4 个神快一刀——狼不该默认去硬刀预言家。
 */
internal object Guard10Board : BoardProfile {

    override val key = WerewolfBoards.GUARD_10

    override fun text(config: WerewolfConfig) = BoardText(
        common = listOf(
            "10 人局 4 神 3 民：平民只有 3 个，狼人**打民边只要 3 刀**，比打 4 个神快一刀。"
        ),
        wolves = listOf(
            "打民边比打神快：3 个平民砍完就赢，而守卫护的多半是神——" +
                "砍外置位的民，往往比硬刀一个被守着的预言家省刀。"
        ),
    )
}
