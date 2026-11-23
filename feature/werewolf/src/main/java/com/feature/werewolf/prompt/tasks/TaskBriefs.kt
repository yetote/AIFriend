package com.feature.werewolf.prompt.tasks

import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPromptStyle
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
                // 守卫可以守自己，所以候选里刻意包含自己的座位号，
                // 和 WerewolfDecisions.guardProtect 里的候选保持同一份
                append("第 ${game.day} 夜，你要守护一个人。")
                append("可选目标：").append(seatsText(game.guardTargets)).append("（含你自己）。")
                if (game.lastGuardedSeat != null) {
                    append("你昨夜守的是 ${game.lastGuardedSeat} 号，今晚不能再守他。")
                }
                if (guided) {
                    // 「空守白过」是策略判断：自由模式里守谁、守不守，让守卫自己盘。
                    append("空守等于这一夜白过，除非你确信狼人会空刀。")
                }
                append("先用一句话说明你为什么守他，再给出座位号。")
            }

            Phase.NIGHT_WOLF_CHAT -> {
                append("第 ${game.day} 夜，狼人睁眼。先和队友商量今晚刀谁。")
                append("可选目标：").append(seatsText(game.aliveSeats)).append("（含你自己和队友）。")
                if (guided) {
                    // 引导式把「自刀骗解药/空刀」当战术推荐；自由式只说这是可选动作，
                    // 不怂惐也不要求「把话说透」——用不用、怎么用，狼队自己商量。
                    append("自刀（刀自己人，骗女巫的解药）和空刀都是正经战术，想用就直说，别怕队友觉得奇怪。" +
                        "如果打的是空刀或自刀，这一轮就要把话说透：为什么这一刀值得这么用。")
                } else {
                    append("自刀和空刀也可以。")
                }
                append("用 1-2 句中文说出你想刀谁、为什么；队友已经先说了，就先回应他。")
                // 「只盯着预言家刀」是最常见的偷懒打法：撞上一次守就白过一夜，
                // 而狼队往往意识不到那一刀根本没落地。把守卫摆进商量内容里。
                // 「撞守无效」是游戏事实，两种风格都说；但「必须盘守卫会守谁」
                // 是打法指令，自由式不给。
                if (game.config.has(WerewolfRole.GUARD)) {
                    if (guided) {
                        append("商量的时候必须把守卫算进去：他今晚会守谁、这一刀会不会撞守；" +
                            "撞守的一刀是彻底无效，不是打残。")
                    } else {
                        append("这一刀撞上守卫就无效（他守的人死不了）。")
                    }
                }
                append("这一步只是商量，不要输出 JSON，也别说「好的」这种没有信息的话。")
            }

            Phase.NIGHT_WOLF -> {
                append("第 ${game.day} 夜，现在定刀。")
                if (game.wolfVoteRound > 1) {
                    append("上一轮你们指了不同的人，这一轮必须统一——再谈不拢今晚就空刀。")
                } else {
                    append("刚才已经商量过，现在各自定下目标：所有狼必须指同一个人，否则今晚空刀。")
                }
                append("可选目标：").append(seatsText(game.aliveSeats))
                    .append("（含你自己和队友）；不想刀人就填 null，主动空刀。")
                if (guided && game.config.has(WerewolfRole.GUARD)) {
                    append("理由里要交代守卫这一层：他今晚最可能守谁，你为什么觉得这一刀落不到守护上。")
                }
                append("先一句话说明理由，再给出目标座位号。")
            }

            Phase.NIGHT_SEER -> {
                val targets = game.aliveSeats.filter { it != seat }
                append("第 ${game.day} 夜，你要查验一个人。")
                append("可选目标：").append(seatsText(targets)).append("。")
                if (guided) {
                    append("优先验还没查过的人——你已经掌握的信息比其他人多，别浪费这一验。")
                }
                append("理由写你**怀疑他什么**（一句话：他哪句话、哪个动作不对劲），")
                append("别写「随机选择」这种没有信息的话，也别写你打算怎么用这条验人——")
            }

            Phase.NIGHT_WITCH -> {
                if (game.witch.antidoteAvailable) {
                    val victim = game.tonightVictim
                    if (victim == null) {
                        append("今晚没有人被狼人袭击，你不需要用解药。")
                    } else {
                        append("今晚 ${victim} 号被狼人袭击。")
                    }
                    append("决定是否用解药，或者用毒药毒死一个人（同一夜只能用一种药）。")
                    if (guided) {
                        // 「留到更关键的时候」是用药时机判断；自由模式里什么时候用，女巫自己定。
                        append("也可以什么都不做，把药留到更关键的时候。")
                    } else {
                        append("也可以什么都不做。")
                    }
                } else {
                    // 解药一空，夜里就是零信息：刀口、平安夜、甚至自己被刀都不再披露。
                    // 白天的死讯播报是全场公开的，那才是她该有的信息渠道。
                    append("你的解药已经用完，今晚谁被袭击、还是没有人被袭击，你都不会被告知。")
                    if (game.witch.poisonAvailable) {
                        append("你只剩毒药的抉择：决定是否毒死一个人，或者什么都不做。")
                    } else {
                        append("两瓶药都已用完，今晚没有可以做的事。")
                    }
                }
            }

            Phase.DAY_SHERIFF_SIGNUP ->
                append(
                    "第 ${game.day} 天，警长竞选开始，先问你要不要上警。" +
                        "想当警长就把 target 填成你自己的 $seat 号，不想就填 null。" +
                        "当上警长票权 1.5 票、还能决定当天的发言起点；" +
                        "而且上过警的人不能投警长票。" +
                        "这一步只是举手报名，不发言——别写竞选宣言，那些话留到竞选发言环节。" +
                        // 「被狼人优先盯上」是风险评估代打；上不上警，自己权衡。
                        if (guided) "代价是被狼人优先盯上，报不报名自己权衡。" else ""
                )

            Phase.DAY_SHERIFF_SPEECH -> {
                append("现在轮到你竞选发言。用 4-6 句说服警下把票投给你。")
                if (guided) {
                    // 怎么说服（先报验人、讲心路、越具体越像真）是演说教学；
                    // 自由模式里怎么说，竞选者自己决定。
                    append(
                        "第一句就把结论摆出来：你验的是谁、结果是什么（预言家），" +
                            "或者你认准谁是该出的人。别把最硬的信息憋到最后。" +
                            "后面几句交代你**为什么这么判断**：验人的心路历程、警徽流准备怎么留，" +
                            "你觉得前一后置位谁像悍跳、谁在挡枪；再说你当选后先推谁。" +
                            "后面的人会拿你这段和别人的对跳：报得越具体、验人和警徽流越接得上，越像真预言家。"
                    )
                }
                // 格式要求，两种风格都给
                append("不要念座位号清单，不要写「某某说：」这样的格式。" +
                    "如果你已经上台说过一次（PK 重新发言），必须回应对方这一轮说的话，" +
                    "不要把刚才那段再念一遍——复读是最容易被抓的破绽。")
                // 竞选发言是唯一能「炸掉警徽」的位置：一爆下去选举中止，本局没有警长。
                // 只写给狼，而且要说清这是拿命换的——不写清楚，模型会把自爆当成一种表态。
                if (player.role.isWerewolf) {
                    if (guided) {
                        // 教学只在引导式给：怎么演、什么时候值得爆。
                        append(
                            "另一种打法：抢不到这个警徽时，可以在这一步自爆——竞选中止，" +
                                "警徽顺延到下一个白天再选；等下次竞选再自爆一次（双爆），" +
                                "警徽才彻底作废，好人白丢一个归票的位置。" +
                                "这是拿两条命换一次战术收益，算得出收益再用；" +
                                "写法是发言最开头写「【自爆】」，后面的话照常说完。"
                        )
                    } else {
                        // 自由式只说自爆的机制。
                        append(
                            "自爆的机制：发言最开头写「【自爆】」就表示自爆，你当场出局，" +
                                "竞选中止、警徽顺延到下一个白天再选（连续两次被打断才作废）。"
                        )
                    }
                }
            }

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
                        "用 4-6 句中文说清你的判断，别只丢一句结论就过。"
                )
                if (guided) {
                    // 「先说你怀疑谁、再给具体依据、正面回应质疑」是怎么发言的教学；
                    // 自由模式里说什么、怎么论证，发言人自己决定。
                    append(
                        "先说你怀疑谁、为什么，再给出支撑这个判断的**具体依据**：" +
                            "谁哪句话前后对不上、谁的站边或警徽流反常、票型能说明什么。" +
                            "如果有人质疑你或你上一票投得反常，正面回应，别绕开。"
                    )
                }
                // 格式与反复读：两种风格都给
                append(
                    "只说你自己的话，不要替别人发言，不要写「某某说：」这样的格式。" +
                        "这一段必须和今天你已经说过的内容不同：结论没变就补新的理由或回应别人的质疑，" +
                        "复读旧话在别人眼里就是背词。"
                )
                // 验人是全场最硬的信息，也是好人认预言家的唯一凭据。
                // 不点出来，模型会按人设聊感受，把一整晚的查验结果烂在手里，
                // 然后被全场当成「跳了预言家却不报验人」投出去。
                // 但「先报验人」同时是「必须跳预言家」——跳不跳是这张牌的核心自由度，
                // 自由模式里由预言家自己决定。
                if (guided && player.role == WerewolfRole.SEER && game.seerChecks.isNotEmpty()) {
                    val last = game.seerChecks.last()
                    append(
                        "开口第一件事先报验人：第 ${last.day} 夜你验的是 ${last.targetSeat} 号，" +
                            "结果是" + (if (last.isWerewolf) "狼人" else "好人") +
                            "。报完再谈判断——跳了预言家却不报验人，好人只能当你悍跳。"
                    )
                }
                // 自爆只对狼有意义。写给所有人等于向好人泄露「这局的狼会自爆」，
                // 虽然规则公开可查，但提示词里不该出现一句只对狼有用的话。
                // 自爆的教学（什么时候值得爆）是打法，自由式不给。
                if (player.role.isWerewolf) {
                    if (guided) {
                        // 什么时候值得爆是打法，只在引导式给。
                        // 只留两条：原来那条「自己已经被点破」被读成「被查杀就该爆」。
                        append(
                            "自爆能立刻结束这一天、直接进入黑夜，用掉的是一条命。" +
                                "值得爆的只有两种：**夜里的战术需要吞掉这个白天**" +
                                "（比如双爆吞警徽），或者**你再说下去会牵出队友**。" +
                                "**被查杀不是理由**——那只是一句指控，你还能反跳、反咬他是悍跳狼。" +
                                "真要爆，就在发言最开头写「【自爆】」，后面的话照常说。"
                        )
                    } else {
                        // 自由式只说自爆的机制：什么是自爆、后果、怎么写才会被受理。
                        // 什么时候值得爆、救谁还是保自己，狼自己定。
                        append(
                            "自爆的机制：发言最开头写「【自爆】」就表示自爆，你当场出局，" +
                                "这一天立刻结束、直接进入黑夜，**这一天不会有人被放逐**，" +
                                "你这轮的发言也到此为止。只要你是狼，讨论环节写了这个标记，" +
                                "系统就受理，动机由你自己判断。"
                        )
                    }
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
