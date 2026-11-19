package com.feature.werewolf.prompt.tasks

import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.prompt.seatsText

/**
 * 当前阶段要做什么。
 *
 * 按**阶段**拆，不按身份：同一个座位一天里要过竞选的举手、白天的发言、夜里的行动，
 * 把这些按角色分文件反而要来回跳。身份相关的内容在 `prompt/roles` 的私有简报和
 * playbook 里，这里只管「这一步的动作用什么话说清楚」。
 *
 * 写具体动作而不是「请行动」：`请从 1、2、5 号中选一个` 比 `请选择目标` 的跑偏率低一个量级，
 * 尤其是小模型。
 */
internal fun taskBrief(game: WerewolfGame, seat: Int): String {
    val player = game.playerAt(seat)
    return buildString {
        append("【本轮任务】")
        when (game.phase) {
            Phase.NIGHT_GUARD -> {
                // 守卫可以守自己，所以候选里刻意包含自己的座位号，
                // 和 WerewolfDecisions.guardProtect 里的候选保持同一份
                append("第 ${game.day} 夜，你要守护一个人。")
                append("可选目标：").append(seatsText(game.guardTargets)).append("（含你自己）。")
                if (game.lastGuardedSeat != null) {
                    append("你昨夜守的是 ${game.lastGuardedSeat} 号，今晚不能再守他。")
                }
                append("空守等于这一夜白过，除非你确信狼人会空刀。")
                append("先用一句话说明你为什么守他，再给出座位号。")
            }

            Phase.NIGHT_WOLF_CHAT -> {
                val targets = game.aliveSeats.filter { it != seat && !game.isWerewolf(it) }
                append("第 ${game.day} 夜，狼人睁眼。先和队友商量今晚刀谁。")
                append("可选目标：").append(seatsText(targets)).append("。")
                append("用 1-2 句中文说出你想刀谁、为什么；队友已经先说了，就先回应他。")
                // 「只盯着预言家刀」是最常见的偷懒打法：撞上一次守就白过一夜，
                // 而狼队往往意识不到那一刀根本没落地。把守卫摆进商量内容里。
                if (game.config.has(WerewolfRole.GUARD)) {
                    append("商量的时候必须把守卫算进去：他今晚会守谁、这一刀会不会撞守；" +
                        "撞守的一刀是彻底无效，不是打残。")
                }
                append("这一步只是商量，不要输出 JSON，也别说「好的」这种没有信息的话。")
            }

            Phase.NIGHT_WOLF -> {
                val targets = game.aliveSeats.filter { it != seat && !game.isWerewolf(it) }
                append("第 ${game.day} 夜，现在定刀。")
                if (game.wolfVoteRound > 1) {
                    append("上一轮你们指了不同的人，这一轮必须统一——再谈不拢今晚就空刀。")
                } else {
                    append("刚才已经商量过，现在各自定下目标：所有狼必须指同一个人，否则今晚空刀。")
                }
                append("可选目标：").append(seatsText(targets)).append("。")
                if (game.config.has(WerewolfRole.GUARD)) {
                    append("理由里要交代守卫这一层：他今晚最可能守谁，你为什么觉得这一刀落不到守护上。")
                }
                append("先一句话说明理由，再给出目标座位号。")
            }

            Phase.NIGHT_SEER -> {
                val targets = game.aliveSeats.filter { it != seat }
                append("第 ${game.day} 夜，你要查验一个人。")
                append("可选目标：").append(seatsText(targets)).append("。")
                append("优先验还没查过的人——你已经掌握的信息比其他人多，别浪费这一验。")
                append("理由写你**怀疑他什么**（一句话：他哪句话、哪个动作不对劲），")
                append("别写「随机选择」这种没有信息的话，也别写你打算怎么用这条验人——")
            }

            Phase.NIGHT_WITCH -> {
                val victim = game.tonightVictim
                if (victim == null) {
                    append("今晚没有人被狼人袭击，你不需要用解药。")
                } else {
                    append("今晚 ${victim} 号被狼人袭击。")
                }
                append("决定是否用解药，或者用毒药毒死一个人（同一夜只能用一种药）。")
                append("也可以什么都不做，把药留到更关键的时候。")
            }

            Phase.DAY_SHERIFF_SIGNUP ->
                append(
                    "第 ${game.day} 天，警长竞选开始，先问你要不要上警。" +
                        "想当警长就把 target 填成你自己的 $seat 号，不想就填 null。" +
                        "当上警长票权 1.5 票、还能决定当天的发言起点，代价是被狼人优先盯上；" +
                        "而且上过警的人不能投警长票。" +
                        "这一步只是举手报名，不发言——别写竞选宣言，那些话留到竞选发言环节。"
                )

            Phase.DAY_SHERIFF_SPEECH ->
                append(
                    "现在轮到你竞选发言。用 2-3 句说服警下把票投给你：" +
                        "你的判断、你觉得谁是狼、你当选之后打算先推谁。" +
                        "不要念座位号清单，不要写「某某说：」这样的格式。" +
                        "如果你已经上台说过一次（PK 重新发言），必须回应对方这一轮说的话，" +
                        "不要把刚才那段再念一遍——复读是最容易被抓的破绽。"
                )

            Phase.DAY_SHERIFF_VOTE ->
                append(
                    "第 ${game.day} 天，警下投票选警长。候选人是 " +
                        seatsText(game.sheriffCandidates) + "。你可以投其中一个，也可以弃票。"
                )

            Phase.DAY_LAST_WORDS ->
                if (game.sheriffSeat == seat) {
                    append(
                        "你已经出局了。这是你的遗言，同时还要交代警徽交给谁：" +
                            "把要带走警徽的人填进 target，想撕掉就填 null。" +
                            "reason 会被当成你的遗言公开说出来，用 1-2 句交代你的判断。"
                    )
                } else {
                    append(
                        "你已经出局了，现在说遗言。用 1-3 句交代你的判断：" +
                            "你怀疑谁、为什么，或者提醒活着的人别投错。" +
                            "只说你自己的话，不要替别人发言。"
                    )
                }

            Phase.DAY_DISCUSSION -> {
                append(
                    "第 ${game.day} 天白天讨论，轮到你发言。" +
                        "用 2-3 句中文说出你的判断：你怀疑谁、为什么，或者解释你上一轮为什么那样投票。" +
                        "只说你自己的话，不要替别人发言，不要写「某某说：」这样的格式。" +
                        "这一段必须和今天你已经说过的内容不同：结论没变就补理由或回应别人的质疑。"
                )
                // 验人是全场最硬的信息，也是好人认预言家的唯一凭据。
                // 不点出来，模型会按人设聊感受，把一整晚的查验结果烂在手里，
                // 然后被全场当成「跳了预言家却不报验人」投出去。
                if (player.role == WerewolfRole.SEER && game.seerChecks.isNotEmpty()) {
                    val last = game.seerChecks.last()
                    append(
                        "开口第一件事先报验人：第 ${last.day} 夜你验的是 ${last.targetSeat} 号，" +
                            "结果是" + (if (last.isWerewolf) "狼人" else "好人") +
                            "。报完再谈判断——跳了预言家却不报验人，好人只能当你悍跳。"
                    )
                }
                // 自爆只对狼有意义。写给所有人等于向好人泄露「这局的狼会自爆」，
                // 虽然规则公开可查，但提示词里不该出现一句只对狼有用的话
                if (player.role.isWerewolf) {
                    append(
                        "自爆能立刻结束这一天、直接进入黑夜，但它只换来一天，用掉的是一条命。" +
                            "唯一值得的情况：**另一个队友**今天已经确定要被投出去，" +
                            "你爆了才能让他活下来。被怀疑的是你自己、你想搅乱讨论、" +
                            "你觉得再聊下去会露——这些都不许爆，爆了也救不回来。" +
                            "真要爆，就在发言最开头写「【自爆】救 X 号」，X 必须是还活着的队友座位号；" +
                            "写不出这个号码就别写标记，系统不会受理没有救到人的自爆。"
                    )
                }
            }

            Phase.DAY_VOTE -> {
                val candidates = game.voteCandidates ?: game.aliveSeats
                append("第 ${game.day} 天投票。")
                if (game.voteRound > 1) {
                    append("上一轮平票，这一轮只能投 ").append(seatsText(candidates)).append("。")
                } else {
                    append("可选目标：").append(seatsText(candidates)).append("。")
                }
                append("投票要指座位号，不要指名字；也可以弃票。")
            }

            Phase.HUNTER_SHOOT -> {
                val targets = game.aliveSeats
                append(
                    "你出局了，身份是${player.role.displayName}，现在可以开枪带走一个人，也可以放弃。" +
                        "这一枪会先结算，然后才判定胜负——即使队友已经全灭也还有翻盘机会。"
                )
                if (targets.isNotEmpty()) {
                    append("可选目标：").append(seatsText(targets)).append("。")
                }
            }

            Phase.FINISHED -> append("游戏已经结束。")
        }
    }
}
