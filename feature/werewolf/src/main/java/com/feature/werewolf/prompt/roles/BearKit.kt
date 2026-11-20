package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfRole

/** 熊：夜里不行动，但每天天亮替全场排除两个邻座——它给的是位置信息，不是身份信息。 */
internal object BearKit : RoleKit {

    override val roles = setOf(WerewolfRole.BEAR)

    override fun rules(role: WerewolfRole): List<String> = listOf(
        "熊是好人神职，夜里不行动。每天天亮时只要熊还活着、左右邻座有存活狼人，" +
            "上帝就会公开宣布熊在咆哮；没咆哮不代表邻座一定都是好人，熊死了也不会咆哮。"
    )

    override fun notes(game: WerewolfGame, seat: Int): List<String> =
        notes(game, seat, WerewolfPromptStyle.GUIDED)

    override fun notes(game: WerewolfGame, seat: Int, style: WerewolfPromptStyle): List<String> {
        val lines = mutableListOf(
            "你是熊：夜里不行动。天亮时只要你活着、左右邻座有存活狼人，" +
                "上帝就会公开宣布你咆哮。这是所有人都能听到的线索，不是你的私聊。",
        )
        // 「不用急着自报身份」是藏身诱导：跳不跳、什么时候跳是这张牌的自由度，
        // 自由模式里由熊自己决定；引导式才给风险提醒。
        if (style == WerewolfPromptStyle.GUIDED) {
            lines += "你不用急着自报身份——让别人先从咆哮里推，你一旦报了，狼人就会先刀你。"
        }
        return lines
    }

    override fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = listOf(
        "你不用急着自报身份：让别人先从咆哮里推，你一报，狼人下一夜就刀你。",
        "你的发言只有一个作用——帮好人排除，或者帮狼队把怀疑引开。",
    )

    override fun conceal(role: WerewolfRole): String =
        "不要主动说自己是熊——咆哮本身已经给了好人线索，报身份只会让狼人先刀你。"
}
