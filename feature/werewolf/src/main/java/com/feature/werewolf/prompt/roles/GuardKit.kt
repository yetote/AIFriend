package com.feature.werewolf.prompt.roles

import com.feature.werewolf.engine.isNight
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfRole

/** 守卫：靠预判刀口抢轮次的暗神。 */
internal object GuardKit : RoleKit {

    override val roles = setOf(WerewolfRole.GUARD)

    override fun rules(role: WerewolfRole): List<String> = listOf(
        "守卫每夜先于狼人守一个人，被守的人当夜不会被刀死；不能连续两夜守同一个人，可以守自己。" +
            "守卫和解药同时作用在一个人身上时，那个人**还是会死**（守救同归）。",
    )

    override fun publicStrategy(role: WerewolfRole): String =
        "守卫未决定明跳前不要泄露守人路线，以免被狼队算清裸位。"

    override fun notes(game: WerewolfGame, seat: Int): List<String> =
        notes(game, seat, WerewolfPromptStyle.GUIDED)

    override fun notes(game: WerewolfGame, seat: Int, style: WerewolfPromptStyle): List<String> {
        val guided = style == WerewolfPromptStyle.GUIDED
        // 白天读 tonightGuardedSeat，夜间守护阶段读 lastGuardedSeat，保证「昨夜」永远精准对齐。
        val lastNight = if (game.phase.isNight) game.lastGuardedSeat else game.tonightGuardedSeat
        val lines = mutableListOf<String>()
        lines += if (lastNight == null) {
            "你昨夜没有守人，今晚任何人都可以守。"
        } else {
            "你昨夜守的是 $lastNight 号，今晚不能再守他。"
        }
        guardHistory(game)?.let { lines += it }
        if (game.phase == Phase.NIGHT_GUARD) {
            lines += "现在轮到你守护。你不知道今晚狼人会刀谁，只能凭白天的发言和局势去预判。"
        }
        lines += "你守的人当夜不会被刀死，但你自己也可能被刀——守自己和空守都是合法选择。"
        lines += "你守中人时不会被系统告知；平安夜可能是你守中了、女巫救了，也可能是狼人空刀。"
        if (guided) {
            lines += "未到明跳时机前，白天不要报出你守过谁，也别把平安夜当成公开证据——那会把守人路线交出去。"
        }
        return lines
    }

    /** 完整逐夜守护履历，防止中后期记混自己前几夜到底有没有空守、守过谁。 */
    private fun guardHistory(game: WerewolfGame): String? {
        if (game.nights.isEmpty()) return null
        val text = game.nights.joinToString("；") { night ->
            val act = night.guarded?.let { "守了 $it 号" } ?: "空守"
            "第 ${night.day} 夜$act"
        }
        return "【你的守护记录】$text。"
    }

    override fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = listOf(
        "你是守卫，核心价值在夜里保人，白天不要冒充预言家抢警徽。",
        "首夜主流打法是空守：女巫大概率盲救，空守既避开守救同归，又能把银水信息交给女巫，同时留满第 2 夜的灵活度。",
        "第 2 夜起按优先级守护：双药女巫 > 单边坐实的真预言家 > 关键明好人或自守；双预对跳未分真假时不必赌守预言家，优先自守或守明好人。",
        "女巫未出局且你没面临抗推前，尽量藏好守卫身份、别报守人路线；但若面临被抗推、或残局需要拍身份带队算轮次，果断跳明守卫并报出完整的逐夜守护记录。",
        "若双死夜里警长出局并把警徽交给你，务必同时盘「他是真预吃刀」和「他是悍跳狼吃毒」两种可能——接了警徽绝不等于他必是真预言家。",
    )
}
