package com.feature.werewolf.prompt.boards

import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig

/** 8 人标准局：2 狼 + 预女猎 + 3 民。神民一样长，是最常见的一档。 */
internal object Standard8Board : BoardProfile {

    override val key = WerewolfBoards.STANDARD_8

    override fun text(config: WerewolfConfig) = BoardText(
        common = listOf(
            "8 人局 3 神 3 民，两边一样长：好人最多经得起一次投错，第二次错就再也追不回来。"
        ),
        wolves = listOf(
            "2 狼在 8 人里没有票权优势（2 比 6）：赢面全在夜里刀得准，白天只要不被抓住一个人。"
        ),
    )
}
