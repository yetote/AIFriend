package com.feature.werewolf.prompt.tasks

import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.prompt.seatsText

/**
 * 当前阶段要做什么（v2 重构版）。
 *
 * 核心升级：
 * 1. **按身份 × 阶段精准分支**：不再给平民/女巫/冲锋狼塞「第一句报验人、越像真预越好」的竞选模板；
 * 2. **取消死板填词模板**：消除「并认定 6 号是真预言家」这种第三人称自吹和生套「X 每步跟 Y 票」的机械句；
 * 3. **解除双药已空女巫禁言**：白天讨论按角色和药量给出恰当的身份暴露策略；
 * 4. **与 `WerewolfDirector` 统一口径**：竞选发言统一为 3-5 句，绝不出现两处字数/句数互相打架。
 */
internal fun taskBrief(
    game: WerewolfGame,
    seat: Int,
    style: WerewolfPromptStyle = WerewolfPromptStyle.GUIDED,
): String {
    val player = game.playerAt(seat)
    val guided = style == WerewolfPromptStyle.GUIDED
    return buildString {
        append("【本轮任务】")
        when (game.phase) {
            Phase.NIGHT_GUARD -> {
                append("第 ${game.day} 夜，你要守护一个人。")
                append("可选目标：").append(seatsText(game.guardTargets)).append("（含你自己）。")
                if (game.lastGuardedSeat != null) {
                    append("你昨夜守的是 ${game.lastGuardedSeat} 号，今晚不能再守他。")
                }
                if (guided) {
                    if (game.day == 1) {
                        append("首夜空守是常见打法：避开与女巫解药撞成守救同归，同时保留第 2 夜的灵活度。")
                    } else {
                        append("顺着狼今晚最想灭的刀口抿刀补防：单边或明显偏真的拿徽预言家优先保一轮验人；双预五五开时优先自守或守双药女巫。")
                    }
                }
                append("空守也是合法选择。先一句话说清理由，再给出座位号（空守填 null）。")
            }

            Phase.NIGHT_WOLF_CHAT -> {
                append("第 ${game.day} 夜，狼人睁眼，与队友商量今晚刀谁与白天分工。")
                append("可选目标：").append(seatsText(game.aliveSeats)).append("（含自己和队友，也可空刀）。")
                if (guided) {
                    append("自刀骗解药和空刀都是正经战术，要用就把收益说透。")
                    if (game.config.has(WerewolfRole.GUARD)) {
                        append("商量时必须把守卫算进去：判断今晚是否会撞守（撞守等于这一刀完全无效）。")
                    }
                } else {
                    append("自刀和空刀也可以。")
                    if (game.config.has(WerewolfRole.GUARD)) {
                        append("这一刀撞上守卫就无效。")
                    }
                }
                append("用 2-4 句中文直说你的方案或回应队友，不要输出 JSON，别说「好的」这种空话。")
            }

            Phase.NIGHT_WOLF -> {
                append("第 ${game.day} 夜，现在定刀。")
                if (game.wolfVoteRound > 1) {
                    append("上一轮意见未统一，本轮必须统一——再平票今晚就空刀。")
                } else {
                    append("商议已结束，请按刚达成的共识定刀：多数决，票分散可能空刀。")
                }
                append("可选目标：").append(seatsText(game.aliveSeats))
                    .append("（含自己和队友）；主动空刀填 null。")
                if (guided && game.config.has(WerewolfRole.GUARD)) {
                    append("理由里简要说明对守卫的考量。")
                }
                append("先一句话说明理由，再给出目标座位号。")
            }

            Phase.NIGHT_SEER -> {
                val targets = game.aliveSeats.filter { it != seat }
                append("第 ${game.day} 夜，你要查验一个人。可选目标：").append(seatsText(targets)).append("。")
                if (guided) {
                    append("优先验还没查过、且能帮你打开局面的关键位。")
                }
                append("用一句话写清查验理由（第 1 夜零信息写位置考量即可，别写「随机」应付，也别提前写查验结果后的计划）。")
            }

            Phase.NIGHT_WITCH -> {
                if (game.witch.antidoteAvailable) {
                    val victim = game.tonightVictim
                    if (victim == null) {
                        append("今晚没有人被狼人袭击，不需要用解药。")
                    } else {
                        append("今晚 ${victim} 号被狼人袭击。")
                    }
                    append("决定是否用解药，或用毒药毒死一个人（同一夜只能用一种药）。")
                    if (guided) {
                        append("也可以什么都不做，把药留到更关键的夜晚。")
                    } else {
                        append("也可以什么都不做。")
                    }
                } else {
                    append("你的解药已经用完，今晚谁被袭击、还是没有人被袭击，你都不会被告知。")
                    if (game.witch.poisonAvailable) {
                        append("你只剩毒药的抉择：决定毒死一个人，或者什么都不做。")
                    } else {
                        append("两瓶药都已用完，今晚没有可以做的事。")
                    }
                }
            }

            Phase.NIGHT_PENDING ->
                append("第 ${game.day} 夜，夜间行动进行中，等待其他角色完成。")

            Phase.DAWN_SETTLE ->
                append("第 ${game.day} 夜结算进行中，即将天亮。")

            Phase.DAY_SHERIFF_SIGNUP -> {
                append("第 ${game.day} 天，警长竞选报名：想上警就把 target 填成自己的 $seat 号，不上警填 null。")
                append("警长一票算 1.5 票并决定发言顺序；上过警的人首轮不能投警长票（平票 PK 轮台外才可投）。")
                append("这一步只是举手报名，不发言，reason 留空。")
                if (guided) {
                    append(
                        when {
                            player.role == WerewolfRole.SEER ->
                                "你是真预言家，**必须上警**报验人争警徽。"
                            player.role.isWerewolf ->
                                "对照昨晚头狼分工：只有被安排悍跳预言家（或双狼悍跳）的狼才上警；" +
                                    "如果分工是冲锋、划水或第三方，**默认不上警**，留在警下把警徽票投给悍跳队友。"
                            player.role == WerewolfRole.WITCH ->
                                "女巫默认**不上警**留警下投票（除非首夜被刀且不能自救需要上警告知，或有明确诈身份计划）；" +
                                    "即使上警也绝不要冒充真预言家抢警徽。"
                            player.role == WerewolfRole.VILLAGER ->
                                "平民没有夜间信息，默认**不上警**把警徽票握在手里（除非明确计划上警发查杀诈身份后退水）。"
                            else ->
                                "非预言家神职默认留警下投票，除非有明确诈身份或替好人分压的意图；绝不冒充真预言家抢警徽。"
                        }
                    )
                }
            }

            Phase.DAY_SHERIFF_SPEECH -> {
                val pk = game.sheriffRound > 1
                append(
                    if (pk) {
                        "现在是平票 PK 发言，用 3-5 句回应另一位候选人并说明警下为何该投你，绝不复读上一轮旧话。"
                    } else {
                        "现在轮到你竞选发言，用 3-5 句说清你的核心信息或判断。"
                    }
                )
                if (guided) {
                    append(
                        when {
                            player.role == WerewolfRole.SEER ->
                                "你是真预言家：第一句直接报昨晚查验结果（首夜零信息如实说位置考量即可，不必硬编心路），" +
                                    "随后给清晰的警徽流与当选思路；若前置位给你发金水立刻反水立警拍明真预。"
                            player.role.isWerewolf ->
                                "严格按昨晚头狼分工演：若分工是**悍跳预言家**，开口报假验人、留警徽流并讲清当选思路（不编花哨心路）；" +
                                    "若分工**不是悍跳**，绝不临时抢跳预言家撞车队友，只以好人视角点评场上发言并准备退水。"
                            else ->
                                "你不是预言家：**绝不要冒充「真预言家」报假金水抢警徽**（女巫更不能把银水伪装成预言家查验）。" +
                                    "直接点评前置位起跳漏洞、或给后置位上警发查杀诈身份施压，听完预言家对跳后准备退水。"
                        }
                    )
                }
                append("不要念座位号清单，不要写「某某说：」。")
                if (player.role.isWerewolf) {
                    if (guided) {
                        append("若狼队战术需要吞警徽，可在发言最开头写「【自爆】」中止竞选（单爆顺延、双爆才吞警徽；拿命换收益，算清再用）。")
                    } else {
                        append("自爆机制：发言最开头写「【自爆】」当场出局并中止竞选，警徽顺延到下一个白天（双爆作废）。")
                    }
                }
            }

            Phase.DAY_SHERIFF_WITHDRAW -> {
                append("竞选发言结束，决定是否**退水**（放弃竞选）。")
                append("退水后不再被投票，首轮无警长票（平票 PK 轮台外有票）；全退无警长，仅剩一人不退则直接当选。")
                if (guided) {
                    append(
                        when {
                            player.role == WerewolfRole.SEER ->
                                "你是真预言家，通常**不要退水**，坚定争夺警徽。"
                            player.role.isWerewolf ->
                                "按分工决定：悍跳位**绝不退水**；若你并非悍跳位却意外上警，应**退水**避免分流队友警徽票。"
                            else ->
                                "你不是预言家：如果场上已有明确预言家起跳对跳，你**应当选择退水**（withdraw=true），" +
                                    "把舞台和警徽留给真假预言家，绝不要赖在台上搅局。"
                        }
                    )
                }
            }

            Phase.DAY_SHERIFF_VOTE -> {
                if (game.sheriffRound > 1) {
                    append("第 ${game.day} 天，警长平票 PK 重投（台上候选人不投，台外存活玩家均可投）。")
                } else {
                    append("第 ${game.day} 天，警下投票选警长。")
                }
                append("候选人：").append(seatsText(game.sheriffCandidates)).append("，可投其一或弃票。")
                if (guided) {
                    append("把票投给你综合判断最像真预言家的带队人——**「有查杀走查杀」只管白天放逐顺序，不等于警长票必须投给报查杀的人**（悍跳狼最常用查杀骗警徽）。")
                }
            }

            Phase.DAY_LAST_WORDS -> {
                val shootAfter = game.afterLastWordsPhase == Phase.HUNTER_SHOOT && game.pendingHunterSeat == seat
                if (game.sheriffSeat == seat) {
                    if (player.deathCause == DeathCause.HUNTER_SHOOT) {
                        append("你被猎人开枪带走，按规则无遗言，只需处理警徽：target 填移交的存活座位或 null（撕徽），reason 仅作内心记录不会公开显示。")
                    } else {
                        append("你已出局，请留 1-2 句遗言并处理警徽：target 填移交的存活座位或 null（撕徽），reason 将作为遗言公开。")
                    }
                } else {
                    append("你已出局，请留 1-3 句遗言：给出具体怀疑座位与可核依据，帮活着的人少走弯路。")
                }
                if (shootAfter) {
                    append("说完遗言后将进入你的开枪环节，现在先留遗言，真正开枪在下一步进行。")
                }
            }

            Phase.DAY_DISCUSSION -> {
                append("第 ${game.day} 天白天讨论，轮到你发言。用 4-6 句中文说清你的判断与投票倾向。")
                if (guided) {
                    val witchBothSpent = player.role == WerewolfRole.WITCH &&
                        !game.witch.antidoteAvailable && !game.witch.poisonAvailable
                    val witchAntidoteSpent = player.role == WerewolfRole.WITCH &&
                        !game.witch.antidoteAvailable && game.witch.poisonAvailable

                    append("发言围绕三件事自然展开（别写成生硬口号）：① 亮明核心判断；② 给出 1-2 条能对着公开台账或发言原文核对的具体依据（先接住上一位针对你或与你相反的观点）；③ 结尾明确给出票向「我这一票投 X 号」。")
                    append(
                        when {
                            player.role == WerewolfRole.SEER ->
                                "你是预言家：先用第一人称报最新查验结果与警徽流（绝不用第三人称评价自己），再正面拆穿对跳悍跳者的破绽。"
                            player.role.isWerewolf ->
                                "你是狼人：严格按昨晚头狼分工演——如果自己正在悍跳预言家，用第一人称坚持真预身份并更新验人/警徽流（别第三人称说「认定自己是真预」）；" +
                                    "否则用好人闭眼视角给出站边和可核依据，绝不露狼视角。"
                            witchBothSpent ->
                                "你是**双药已空的女巫**：已无需再藏身份！若你被怀疑、真预言家被冲、或大家没盘清双死夜谁吃刀谁吃毒，" +
                                    "请直接亮明女巫身份并报出每夜救了谁、毒了谁，绝不藏到被推或被猎人误枪。"
                            witchAntidoteSpent ->
                                "你是已交解药的女巫：可视局势阳光跳女巫报银水带队、或暂藏留毒威慑，但绝不要冒充预言家。"
                            player.role == WerewolfRole.GUARD ->
                                "你是守卫：无人重点怀疑你且轮次宽裕时继续藏身、不漏守人路线；" +
                                    "但若你已成为本轮焦点抗推位（被归票、被查杀或有话票矛盾），或残局推错一轮即可能绑票/屠神，" +
                                    "**必须果断拍明守卫身份并报出完整的逐夜守护记录**自证带队，绝不藏到被放逐出局。"
                            else ->
                                "若跳预言家的人都已出局，就复盘谁真谁假、把剩余狼坑点出来。平时不轻易裸拍神职身份，但面临抗推或残局带队需要时果断亮牌自证。"
                        }
                    )
                }
                append("只说自己的话，提到人一律用「X 号」，不要写「某某说：」，不要复述规则或念存活名单。")
                append("公开台账与本日进程里没有的事，不要说成「我记得」；不要复读今天已经说过的话，必须补充新依据或回应最新发言。")
                if (guided && player.role == WerewolfRole.SEER && game.seerChecks.isNotEmpty()) {
                    val last = game.seerChecks.last()
                    append(
                        "开口第一件事先报验人：第 ${last.day} 夜你验的是 ${last.targetSeat} 号，" +
                            "结果是" + (if (last.isWerewolf) "狼人" else "好人") + "。"
                    )
                }
                if (player.role.isWerewolf) {
                    if (guided) {
                        append("自爆机制：在发言最开头写「【自爆】」当场出局并立刻入夜。默认不自爆，只在战术吞白天/吞警徽或再说会牵出队友时用；被查杀或被踩不是自爆理由。")
                    } else {
                        append("自爆机制：发言最开头写「【自爆】」即当场出局、当天立刻入夜且无人放逐，只要写了标记系统就受理。")
                    }
                }
            }

            Phase.DAY_VOTE -> {
                val candidates = (game.voteCandidates ?: game.votingSeats).filter { it != seat }
                append("第 ${game.day} 天放逐投票。")
                if (game.voteRound > 1) {
                    append("上一轮平票，本轮只能投 ").append(seatsText(candidates)).append("。")
                } else {
                    append("可选目标：").append(seatsText(candidates)).append("。")
                }
                append("投票指座位号。有明确怀疑就果断投出；完全分不清时宁可弃票也别无理由乱投。")
                if (guided) {
                    append("尽量与你今天发言里的票向一致（若听完后置位新证据决定改票，在 reason 里写清改票依据）。")
                    if (game.config.hasSheriff && game.aliveSheriffSeat != null) {
                        append("警长已归票时，先判断警长是否可信再决定跟票或独立带票。")
                    }
                }
            }

            Phase.HUNTER_SHOOT -> {
                val targets = game.aliveSeats
                append("你出局了，身份是${player.role.displayName}，现在可以开枪带走一名存活玩家，也可以放弃（填 null）。")
                append("这一枪在胜负判定之前结算，可能直接改写结局。")
                if (targets.isNotEmpty()) {
                    append("可选目标：").append(seatsText(targets)).append("。")
                }
            }

            Phase.FINISHED -> append("游戏已经结束。")
        }
    }
}
