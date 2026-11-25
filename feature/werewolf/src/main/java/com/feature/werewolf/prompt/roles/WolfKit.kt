package com.feature.werewolf.prompt.roles

import com.feature.werewolf.engine.isNight
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfRole

/**
 * 狼人（含狼王）。
 *
 * v2 结构调整：
 * - 白天聚焦「严格执行昨晚头狼分工 + 伪装好人盘逻辑」，不再在每个白天发言里倾倒 11 套战术百科；
 * - 战术菜单（[WolfTactics]）、守卫博弈（[WolfKillBrief]）与屠边账（[WolfSideMath]）只在夜间注入，
 *   大幅降低白天上下文噪音，杜绝非悍跳狼临时起意抢跳预言家撞车队友。
 */
internal object WolfKit : RoleKit {

    override val roles = setOf(WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF_KING)

    override fun rules(role: WerewolfRole): List<String> = when (role) {
        WerewolfRole.WEREWOLF_KING -> listOf(
            "狼王是狼人：夜里照常参与商议和下刀；白天被投票放逐时可以开枪带走一个人，" +
                "那一枪即使在狼队已经全灭后也照样有效，可能直接改写胜负。" +
                "狼王被毒死或被猎人带走时不能开枪。"
        )

        else -> emptyList()
    }

    override fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = buildList {
        add("**严格执行狼队分工**：白天照昨晚头狼定好的打法演——没安排你悍跳就绝不要抢跳预言家，避免自己人撞车；被安排悍跳就坚持到底不退水。")
        add("白天立场伪装好人，但对外宣称的身份可按战术挑（悍跳预言家、假女巫盗银水等都是正当武器），不要把自己演成全程无存在感的透明人。")
        add("发言必须符合好人闭眼视角：挑对方真实可核的发言或票型矛盾，**不要编造「手抖、眼神躲闪」等不存在的现场细节**，也绝不泄露只有狼才知道的夜间视野。")
        add("白天盘身份**只盘好人**（归类预/女巫/猎/守/民），**绝不把队友写进怀疑名单或投给队友**——唯一例外是明确在演倒钩战术。队友被踩时也别急着抱团护场。")
        add("盘神职找刀口：预言家看验人与警徽流；猎人靠排除法防踩雷；有人报出能对上昨夜刀口的银水多半是真女巫。")
        if (config.has(WerewolfRole.GUARD)) {
            add("盘守卫：特征往往是发言克制、暗中关注刀口；记住**有人自称「我昨晚守的」多半是诈守卫**。守卫不能连续两夜守同一个人，平安夜刀口次夜大概率裸着。")
        }
        add("盘出的结论要落到票上：嘴上打谁就投谁，别让发言与投票自相矛盾。")
    }

    override fun notes(game: WerewolfGame, seat: Int): List<String> =
        buildNotes(game, seat, guided = true)

    override fun notes(game: WerewolfGame, seat: Int, style: WerewolfPromptStyle): List<String> =
        buildNotes(game, seat, guided = style == WerewolfPromptStyle.GUIDED)

    private fun buildNotes(game: WerewolfGame, seat: Int, guided: Boolean): List<String> {
        val lines = mutableListOf<String>()
        val king = game.playerAt(seat).role == WerewolfRole.WEREWOLF_KING
        if (king) {
            lines += "你是狼王：夜里和普通狼一样行动，白天被投票放逐时可以开枪带走一个人。" +
                "那一枪即使在队友已经全灭之后也有效。"
            if (guided) lines += "真到了那一枪的局面，别轻易放弃。"
        }
        val mates = game.wolves().filter { it.seat != seat }
        lines += if (mates.isEmpty()) {
            "你是唯一的狼人。"
        } else {
            "你的狼队友：" + mates.joinToString("、") { mate ->
                "${mate.seat} 号「${mate.name}」" + when {
                    mate.alive -> ""
                    mate.seat in game.ghostWolves -> "（已自爆，今晚还会跟你们一起商议、定刀，定完就离场）"
                    else -> "（已出局）"
                }
            }
        }
        lines += "【保密红线】狼队内部的一切**真事**（队友是谁、商量了什么、今晚刀谁、狼队剩几人）只能在夜里说。" +
            "白天发言不许出现「队友」「我们狼」「昨夜我们」这类字眼，也不许复述夜聊原文——说出口就是自曝。"
        lines += "与真事不同，白天**编造假身份**（跳预言家报假验人、跳女巫报假银水等）叫混淆视听，是狼人的正当武器，不违反保密红线。"
        if (guided) {
            lines += "公共规则里约束真神职「不冒充预言家 / 少裸拍身份」的条款不约束狼人：按团队分工悍跳搅局正是你的活。"
        }
        if (game.wolfPlan != null && !game.phase.isNight) {
            lines += "昨晚头狼定的战术：" + game.wolfPlan
            if (guided) {
                lines += "**白天严格按上述分工执行**：安排谁悍跳就谁悍跳，安排你冲锋/划水/第三方就别抢跳预言家；且绝口不提「安排」「昨晚商量」。"
            }
        }
        if (game.phase == Phase.NIGHT_WOLF_CHAT) {
            lines += "现在是夜里，只有狼人醒着：可以直说刀口与理由，也可以反驳队友。"
            lines += "头狼的安排是建议，队友可以提出异议——别用「这是死命令」「都听我的」这类生硬口吻。"
            val head = game.nightHead
            if (head != null) {
                lines += if (head == seat) {
                    if (guided) {
                        val ratioHint = if (mates.size >= 2) {
                            "多人狼队经典配比为 1 悍跳 + 1 冲锋 + 1-2 倒钩/深水（忌全员倒钩孤立悍跳，也忌全员冲锋一锅端）。"
                        } else {
                            ""
                        }
                        "**今晚你是头狼（狼队指挥）**：你先发言。给出一套具体可执行的方案——" +
                            "今晚刀谁与理由、白天谁上警悍跳预言家（其余狼留警下投票、绝不抢跳）、谁冲锋、谁倒钩/划水。" + ratioHint
                    } else {
                        "**今晚你是头狼**：你先发言。带着队友统一今晚目标与白天分工。"
                    }
                } else {
                    "今晚的头狼是 $head 号，他先发言。" +
                        if (guided) {
                            "听完后明确表态：照执行并补你的理由，或反对并给出新刀口。"
                        } else {
                            "听完他的意见，自己决定支持还是反对，并说明理由。"
                        }
                }
            }
        }
        if (game.phase == Phase.NIGHT_WOLF) {
            lines += "商议结束，现在各自定刀：目标可以是好人、队友或自己（自刀骗解药），填 null 为空刀；多数决，平票可能空刀。"
        }
        game.nights.lastOrNull { it.day == game.day - 1 }?.wolfTarget?.let { target ->
            lines += "上一夜你们定了刀 $target 号。"
        }
        if (game.phase.isNight) {
            if (game.wolfVoteRound > 1 && game.wolfVotes.isNotEmpty()) {
                lines += "上一轮你们没能统一意见：" +
                    game.wolfVotes.entries.sortedBy { it.key }
                        .joinToString("、") { "${it.key} 号" + wishText(it.value) } +
                    "。这一轮必须定下来，再平票今晚就空刀。"
            }
            if (guided) {
                lines += WolfKillBrief.of(game)
                lines += WolfSideMath.of(game)
                // 战术菜单只在商议阶段（NIGHT_WOLF_CHAT）注入，定刀表决阶段（NIGHT_WOLF）已经达成共识，不再重复灌 11 套战术省 token
                if (game.phase == Phase.NIGHT_WOLF_CHAT) {
                    lines += "【狼队打法菜单】头狼从下面挑一套具体分工并点名谁执行，挑定后白天统一照演、别中途变卦："
                    lines += WolfTactics.menu(game.config)
                }
            }
        } else {
            lines += "白天**一个字都不许提你们刀过谁**，也别刻意回避他。"
        }
        return lines
    }

    override fun conceal(role: WerewolfRole): String =
        if (role == WerewolfRole.WEREWOLF_KING) {
            "以上狼队真事绝不能在白天泄露；一旦被票出局，记得狼王的最后一枪可能直接决定胜负。"
        } else {
            "以上狼队真事绝不能在白天泄露。"
        }

    private fun wishText(target: Int?): String =
        if (target == null) "主张空刀" else "想刀 $target 号"
}
