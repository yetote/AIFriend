package com.feature.werewolf.prompt

import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole

/**
 * 残局里好人的「穿神衣服挡刀」——只喂残局好人，不喂狼。
 *
 * 明确边界：
 * - 平民优先挑已出局的神来穿，绝不和存活的真预言家对跳；
 * - 神职（尤其双药已空女巫、守卫、猎人）残局直接拍明真实身份报完整信息，比继续藏着更有价值。
 */
internal object GoodBluff {

    private fun isEndgame(game: WerewolfGame): Boolean {
        val alive = game.alivePlayers.size
        val goodAlive = game.alivePlayers.count { !it.role.isWerewolf }
        return alive <= 6 || goodAlive <= 3
    }

    fun of(game: WerewolfGame, seat: Int): List<String> {
        val player = game.playerAt(seat)
        if (player.role.isWerewolf || !isEndgame(game)) return emptyList()

        return buildList {
            add("【残局战术：亮身份与挡刀】现在存活人数已经很少，每一轮放逐和夜刀都直接决定胜负。")
            if (player.role == WerewolfRole.VILLAGER) {
                add("你是平民，残局可以策略性穿一件神的衣服来替真神挡刀（例如预言家、猎人、守卫或女巫）；" +
                    "实际投票仍按你的平民推理来，只是打乱狼队的屠边刀口。")
                add("怎么穿：优先挑一个**已经出局的神**来穿，给一个自洽的简短理由；" +
                    "**绝不要和还活着的真预言家或刚拍明身份的真神对跳**——那不是挡刀，是帮狼送人头。")
                add("翻车点：穿了就别立刻缩回「我其实是民」，否则白送好人一轮怀疑。")
            } else {
                add("你本身是神职，残局**藏着已经不如站出来值钱**：直接拍明你的真实身份并报出完整行动记录，" +
                    "帮好人把票砸在最确定的狼身上，或者吸引狼刀保护其他队友。")
            }
        }
    }
}
