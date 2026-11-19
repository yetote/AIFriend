package com.feature.werewolf.prompt.boards

import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig

/** 9 人标准局：3 狼 + 预女猎 + 3 民。三狼在场，好人的票权第一次不再压倒性。 */
internal object Standard9Board : BoardProfile {

    override val key = WerewolfBoards.STANDARD_9

    override fun text(config: WerewolfConfig) = BoardText(
        common = listOf(
            "9 人局 3 狼 3 神 3 民：狼队票权 3 比 6，还压不过好人，但只要把票拆散，" +
                "3 票就能变成决定性的少数。"
        ),
        wolves = listOf(
            "3 狼可以分头站边：两个人跟一派、一个人跟另一派，好人互咬起来时你们的 3 票就是天平。"
        ),
    )
}
