package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WinRule

/**
 * 狼队的屠边账（暗牌公平版）——只在**屠边局夜间**给狼人。
 *
 * 严格遵守暗牌信息隔离：绝不偷看已出局好人的隐藏底牌去替狼算「还剩几神几民」；
 * 只汇总公开已知的开局配比、已出局好人总数（狼自己知道哪些死者是队友）以及公开亮明过的身份
 * （开枪猎人、翻牌白痴），提醒狼队自行估算哪条边更近并集中打穿，同时提防残局平民穿神衣服挡刀。
 */
internal object WolfSideMath {

    fun of(game: WerewolfGame): List<String> {
        if (game.config.winRule != WinRule.SLAY_SIDE) return emptyList()

        val initialGods = game.config.composition.entries
            .filter { (role, _) -> role.isGod }
            .sumOf { it.value }
        val initialVillagers = game.config.composition[WerewolfRole.VILLAGER] ?: 0
        if (initialGods == 0 && initialVillagers == 0) return emptyList()

        val deadGoodCount = game.players.count { !it.alive && !it.role.isWerewolf }
        val confirmedDeadHunters = game.history
            .filterIsInstance<GameEvent.HunterShot>()
            .map { it.hunterSeat }
            .distinct()
            .count { seat -> !game.playerAt(seat).role.isWerewolf }
        val revealedIdiotDead = game.idiotRevealed &&
            game.players.any { it.role == WerewolfRole.IDIOT && !it.alive }
        val confirmedDeadGods = confirmedDeadHunters + if (revealedIdiotDead) 1 else 0

        val lines = mutableListOf<String>()
        lines += buildString {
            append("【屠边账（只对你们有用）】开局共 $initialGods 神 $initialVillagers 民；")
            append("目前已出局 $deadGoodCount 名好人")
            if (confirmedDeadGods > 0) {
                append("（其中公开坐实已出局的神职 $confirmedDeadGods 名，其余好人暗牌未翻）")
            }
            append("。哪边更近就先打穿哪边，别神边民边轮流分散刀口。")
        }
        lines += "结合白天跳身份与发言自行估算神边/民边各剩几张；残局提防平民穿神衣服挡刀，别轻易把刚跳明却拿不出硬依据的牌当成真神跳过。"
        lines += "提醒：白天别整局只护同一个人，谁保了谁好人会拿来盘你们共边。"
        return lines
    }
}
