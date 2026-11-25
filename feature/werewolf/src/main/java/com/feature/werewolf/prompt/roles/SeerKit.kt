package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.prompt.seatsText

/** 预言家：全场唯一能稳定产出硬查验的好人牌。 */
internal object SeerKit : RoleKit {

    override val roles = setOf(WerewolfRole.SEER)

    override fun notes(game: WerewolfGame, seat: Int): List<String> {
        val lines = mutableListOf<String>()
        if (game.seerChecks.isEmpty()) {
            lines += "你还没有查验过任何人。"
        } else {
            lines += "你的查验记录（只有你知道）："
            game.seerChecks.forEach { check ->
                lines += "第 ${check.day} 夜 · 查验 ${check.targetSeat} 号 → " +
                    if (check.isWerewolf) "狼人" else "好人"
            }
        }
        val checked = game.seerChecks.map { it.targetSeat }.toSet()
        // 死讯压在 pendingDawn 里尚未公布时，不能提前把昨夜死者从「存活玩家」里删掉，避免警上剧透死讯
        val visibleAlive = if (game.pendingDawn != null) game.sheriffEligibleSeats else game.aliveSeats
        val unknown = visibleAlive.filter { it != seat && it !in checked }
        if (unknown.isNotEmpty()) {
            lines += "还没查验过的存活玩家：" + seatsText(unknown)
        }
        return lines
    }

    override fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = buildList {
        add("你是真预言家，白天发言的核心只有三件事：报验人、留警徽流、正面拆穿对跳悍跳者。")
        if (config.hasSheriff) {
            add("必须上警争警徽、不要轻易退水；哪怕被别人发金水，也要立刻反水立警拍明真预。")
            add("拿警徽后务必约定**双向警徽流**（如「今晚验 X：若我明早倒牌，警徽飞给 X＝金水，撕徽或飞给外置好人＝查杀」），利用警徽飞向把无遗言夜的验人结果传给好人。")
        }
        add("开口第一句先报最新查验（「第 X 夜我验了 Y 号，结果是好人/狼人」），绝不跳过验人只喊情绪口号。")
        add("第 1 夜零信息，如实说明按位置或随机查验即可，不必硬编花哨心路；第 2 夜起要讲清为什么验他。")
        add("警徽流只报你真实打算接下来查验的未定义玩家，并留一个防自爆备选；绝不编造「还没验过的查验结果」，也别把已经定死身份的牌再留进警徽流。")
        add("验人前后必须严丝合缝，绝不改口；遇到对跳直接把矛头对准跳预言家的人，别放着悍跳去纠缠外置位。")
    }
}
