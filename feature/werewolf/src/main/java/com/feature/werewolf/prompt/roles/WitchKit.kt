package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole

/** 女巫：两瓶药都是「一次性、不可逆」的资源，提示词的重点是别把药留到不会到来的明天。 */
internal object WitchKit : RoleKit {

    override val roles = setOf(WerewolfRole.WITCH)

    override fun notes(game: WerewolfGame, seat: Int): List<String> {
        val lines = mutableListOf<String>()
        lines += "解药：" + if (game.witch.antidoteAvailable) "还在" else "已经用完"
        lines += "毒药：" + if (game.witch.poisonAvailable) "还在" else "已经用完"
        if (game.phase == Phase.NIGHT_WITCH) {
            val victim = game.tonightVictim
            val canSelfHeal = game.witch.antidoteAvailable &&
                game.day == 1 && game.config.witchCanSelfHealOnFirstNight
            when {
                victim == null ->
                    lines += "今晚没有人被狼人袭击，解药这次没有用处，你可以只用毒药或什么都不做。"

                victim == seat && canSelfHeal ->
                    lines += "**今晚被狼人袭击的就是你自己（$seat 号）。** " +
                        "第一夜你还能自救：用解药就活下来，不用就当场出局。"

                victim == seat -> {
                    // 必死的局面。以前这里只写「被袭击的是 2 号」，模型按普通夜晚的
                    // 思路答「这晚先不用药，毒药留到更关键的时候」——可它天一亮就死了，
                    // 「更关键的时候」永远不会到来，毒药跟着进棺材。
                    // 所以要明写「你今晚会死」和「这是最后机会」，不能只给客观信息。
                    lines += "**今晚被狼人袭击的就是你自己（$seat 号），而你已经救不了自己" +
                        "——天一亮你就出局了。**"
                    if (game.witch.poisonAvailable) {
                        lines += "这是你最后一次行动：毒药现在不用，就跟着你一起作废，" +
                            "以后永远没有第二次机会。想清楚最该带走谁，" +
                            "别把毒药省给一个不会到来的明天。"
                    }
                }

                else -> {
                    lines += "今晚被狼人袭击的是 $victim 号。"
                    lines += "你可以救他，也可以不救。解药只有一次，" +
                        "救他等于把这一次押在「他是好人」上。"
                }
            }
        }
        lines += "解药用一次就没了，可以救活被狼刀的人，但不能救自己（第一夜除外）。"
        lines += "毒药只能用一次，同一夜不能和解药一起用。"
        return lines
    }

    override fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = listOf(
        "你是女巫，白天要把自己藏起来。",
        "不要说自己救过谁、毒过谁，哪怕那是真的——被认出来的女巫下一夜就会被刀。",
        "不要用「我昨晚听说」「我猜他昨晚」这种暗示自己有夜间信息的话。",
        "毒药只有一次，宁可留到确定是狼的那天；救人也只救你确定该救的人，" +
            "别因为「他好像挺重要」就浪费解药。",
    )
}
