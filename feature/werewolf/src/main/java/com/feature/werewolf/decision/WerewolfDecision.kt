package com.feature.werewolf.decision

import com.core.ai.structured.StructuredSpec
import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.SpeechDirection
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** 狼人夜刀 / 预言家查验的结果。[reason] 会作为夜间发言显示，所以要求模型写一句人话。 */
@Serializable
data class SeatChoice(
    val target: Int,
    val reason: String = "",
)

/** 女巫用药。两种药互斥，都不填等于什么都不做。 */
@Serializable
data class WitchChoice(
    val heal: Boolean = false,
    val poison: Int? = null,
    val reason: String = "",
)

/** 白天投票 / 猎人开枪。`target` 为 null 表示弃票或不开枪。 */
@Serializable
data class VoteChoice(
    val target: Int? = null,
    val reason: String = "",
)

/**
 * 警长选发言方向：警左 / 警右 / 死左 / 死右。
 *
 * 用中文标签当值而不是布尔，是因为选项从两个变成四个了；[SpeechDirection.ofLabel]
 * 认中文名，也认旧档里的「顺时针 / 逆时针」。兜底永远选警左，宁可平淡也不赌。
 */
@Serializable
data class DirectionChoice(
    val direction: String = SpeechDirection.SHERIFF_LEFT.displayName,
    val reason: String = "",
)

/**
 * 别人说完一句之后，「我要不要当场插爆」。
 *
 * 做成结构化决策而不是再识别一次文本标记：这里没有「发言」这回事——
 * 它没轮到自己开口，答案只有爆/不爆两种，没有正文需要解析。
 * 兜底永远是 false：插爆是锦上添花，判断不出来就当没这回事。
 *
 * [gain] 是**这笔账**：爆掉这一天能换到什么（保住哪个队友、断掉哪条信息链）。
 * 单要一个「爆/不爆」，模型容易凭「现在很难受」就爆；先让它把收益写出来，
 * 白拿的账（换不到东西、纯粹是被点破后恼羞成怒）会自己显形。
 * 不落存档，只在自爆那条记录的 reasoning 里留给复盘看。
 */
@Serializable
data class ExplodeChoice(
    val explode: Boolean = false,
    /** 爆了能换到什么。一句话；explode=false 时留空。 */
    val gain: String = "",
    val reason: String = "",
)

/**
 * 竞选发言之后的退水：要不要放弃竞选。
 *
 * 兜底永远是不退水——退了就再也拿不到这个警徽，而「没想清楚」不该被当成让位。
 * [reason] 只留在复盘里，牌桌上只会出现一句「我退水。」/「我不退水。」：
 * 真正的原因常常是「我在给队友让位」，说出来等于把狼队供出去。
 */
@Serializable
data class WithdrawChoice(
    val withdraw: Boolean = false,
    val reason: String = "",
)

/**
 * 各个阶段的结构化决策规格。
 *
 * 每个规格都把「合法目标」**明写进 instruction**，并在 `validate` 里再查一遍。
 * 两道关都不是多余的：
 *
 * - instruction 里的列表是给模型看的，能大幅降低它选到已出局玩家的概率；
 * - validate 是给解析器看的，模型仍然可能选错，选错之后必须当成失败去重试，
 *   而不是把非法座位号喂给 [WerewolfGame] 让它抛异常——那会打断整局。
 *
 * 兜底值一律取「规则上一定合法」的动作：狼刀随机一个好人、投票随机一个候选、
 * 猎人不开枪、女巫不用药。宁可这局平淡，也不能因为模型失灵而卡死。
 */
object WerewolfDecisions {

    /**
     * 「理由」类字段的统一约束。
     *
     * 投票、查验、用药这些理由都属于**内心活动**：只有本人看得到，不进朗读。
     * 所以这里必须明写「不会说出口」，否则模型会把它写成一段发言（「各位，
     * 我之所以投 3 号是因为……」），既占屏又和真正的发言混在一起。
     */
    private const val INNER_REASON_HINT =
        "reason 是你心里的判断（没人听见，也不会朗读）：一句话说清理由，不要复述别人已经说过的内容。"

    /**
     * 狼人夜刀。
     *
     * 候选是**全部存活座位**，包括自己和队友：自刀骗解药、刀队友做身份都是线下正经打法。
     * 引擎 v12 起也放开了这两条（以前有两道 require 拦着）。
     *
     * `target = null` 是**空刀**：不是失误，是战术——连着空两夜能让好人误判「有守卫守中了」。
     * 所以这里用 [VoteChoice] 而不是 [SeatChoice]：形状和投票一样，人类座位那边
     * 「空刀」按钮直接走弃权那条路，不用再加一种提交类型。
     */
    fun wolfKill(game: WerewolfGame, seat: Int): StructuredSpec<VoteChoice> {
        val candidates = game.aliveSeats
        return StructuredSpec(
            instruction = buildString {
                append("输出 {\"target\": 座位号或 null, \"reason\": \"一句话理由\"}。")
                append("target 是今晚要刀的人，可以从 ${list(candidates)} 里选——")
                append("**包括你自己（$seat 号）和你的队友**：自刀骗解药、刀队友做身份都是合法战术。")
                append("不想刀任何人就填 null（空刀），这也是合法战术，不是失误。")
                append("reason 用一句中文说明你为什么这么选，这句话队友会看到。")
            },
            serializer = VoteChoice.serializer(),
            validate = { it.target == null || it.target in candidates },
        )
    }

    /** 预言家查验。候选是除自己外的所有存活玩家。 */
    fun seerCheck(game: WerewolfGame, seat: Int): StructuredSpec<SeatChoice> {
        val candidates = game.aliveSeats.filter { it != seat }
        return StructuredSpec(
            instruction = "输出 {\"target\": 座位号, \"reason\": \"一句话理由\"}。" +
                "target 必须是今晚要查验的人，只能从 ${list(candidates)} 中选，不能选自己。" +
                "reason 用一句中文说明你的判断依据。",
            serializer = SeatChoice.serializer(),
            validate = { it.target in candidates },
        )
    }

    /** 女巫用药。校验比前两个复杂：药是否还在、能不能自救、两种药是否互斥。 */
    fun witch(game: WerewolfGame, seat: Int): StructuredSpec<WitchChoice> {
        // 毒药候选里去掉自己。这不是校验的补充，而是**信息**：女巫看不到名单时
        // 会去猜能不能毒到自己，猜错就白白重试一轮。
        val poisonCandidates = game.aliveSeats.filter { it != seat }
        val victim = game.tonightVictim
        val antidoteGone = !game.witch.antidoteAvailable
        val doomed = !antidoteGone && victim == seat &&
            !(game.day == 1 && game.config.witchCanSelfHealOnFirstNight)
        return StructuredSpec(
            instruction = buildString {
                append("输出 {\"heal\": true 或 false, \"poison\": 座位号或 null, \"reason\": \"一句话理由\"}。")
                if (antidoteGone) {
                    // 解药一空，夜里零信息：刀口、平安夜、甚至自己被刀都不再披露。
                    // 白天的死讯播报是全场公开的，那才是她该有的信息渠道。
                    append("你的解药已经用完，今晚谁被袭击、还是没有人被袭击，你都不会被告知。")
                } else {
                    append(
                        when (victim) {
                            null -> "今晚没有人被狼人袭击。"
                            seat -> "今晚被狼人袭击的是你自己（$seat 号）。"
                            else -> "今晚被狼人袭击的是 $victim 号。"
                        }
                    )
                }
                append("heal 表示是否用解药救今晚被刀的人")
                append(
                    when {
                        // 「不能用」有好几种原因，必须分清：说成同一句，女巫会以为自己漏了信息。
                        // 自己中刀时优先说「救不了你自己」——库存状态上一句已经交待过，
                        // 这一刻对她有用的信息是「后果」，不是「药还剩几瓶」。
                        antidoteGone -> "（解药已经用完了，只能填 false）"
                        doomed -> "（救不了你自己）"
                        victim == null -> "（今晚没人被刀，用不上）"
                        else -> "（现在可以用）"
                    }
                )
                append("；poison 表示是否用毒药毒死一个人")
                append(
                    if (game.witchCanPoison) {
                        "（现在可以用），只能从 ${list(poisonCandidates)} 中选，不能毒自己"
                    } else {
                        "（毒药已经用完了）"
                    }
                )
                append("。两种药同一夜不能一起用，不想用药就 heal=false 且 poison=null。")
                if (doomed && game.witchCanPoison) {
                    // 女巫本人今晚必死，毒药不留就等于作废。不明说的话，模型会用
                    // 「留到更关键的时刻」这种说法把唯一一次机会省掉。
                    append("注意：你今晚必死，这是你唯一一次用毒药的机会，留着不会再有下次。")
                }
                append(INNER_REASON_HINT)
            },
            serializer = WitchChoice.serializer(),
            validate = { choice ->
                val healOk = !choice.heal || (
                    game.witchCanHeal &&
                        // 女巫不能自救，第一夜按配置可能例外；这里跟引擎保持同一套判断
                        (game.tonightVictim != seat || (game.day == 1 && game.config.witchCanSelfHealOnFirstNight))
                    )
                val poisonOk = choice.poison == null || (
                    game.witchCanPoison &&
                        choice.poison in game.aliveSeats &&
                        choice.poison != seat
                    )
                healOk && poisonOk && !(choice.heal && choice.poison != null)
            },
        )
    }

    /**
     * 守卫守护。
     *
     * 候选里**包含自己**——守自己是常见选择，引擎不该替守卫排除。
     * 允许空守，所以用 [VoteChoice]（target 可空）而不是 [SeatChoice]。
     */
    fun guardProtect(game: WerewolfGame, seat: Int): StructuredSpec<VoteChoice> {
        val valid = game.guardTargets
        return StructuredSpec(
            instruction = "输出 {\"target\": 座位号或 null, \"reason\": \"一句话理由\"}。" +
                "target 是你要守护的人，只能从 ${list(valid)} 中选（可以选你自己）；不想守任何人就填 null。" +
                (game.lastGuardedSeat?.let { "你昨夜守的是 $it 号，不能连续两夜守同一个人。" } ?: "") +
                INNER_REASON_HINT,
            serializer = VoteChoice.serializer(),
            validate = { it.target == null || it.target in valid },
        )
    }

    /** 白天投票。[candidates] 由调用方传：平票加投时是收窄后的名单。 */
    fun vote(game: WerewolfGame, candidates: List<Int>): StructuredSpec<VoteChoice> {
        val valid = candidates.filter { it in game.votingSeats }
        return StructuredSpec(
            instruction = "输出 {\"target\": 座位号或 null, \"reason\": \"一句话理由\"}。" +
                "target 是你要投的人，只能从 ${list(valid)} 中选。**这一票必须投出去**：" +
                "弃票只有在名单里全是你已经确认的好人（自己验出来的金水、公开认下的好人）时才成立，" +
                "「拿不准」「不想得罪人」「再看看」都不是弃票的理由——好人常常是被自己的弃票输掉的。" +
                "**票要和你今天说过的话一致**：你在发言里说怀疑谁，就投谁；" +
                "说了 A 却投 B，在场上等于当众承认自己在打哑谜。" +
                "reason 是心里的依据（没人听见）：一句话点出你凭什么怀疑他——" +
                "他的哪句话前后对不上、哪张票反常，别只写「感觉」「像狼」。",
            serializer = VoteChoice.serializer(),
            validate = { it.target == null || it.target in valid },
        )
    }

    /**
     * 上警报名。
     *
     * 用 [VoteChoice] 而不是新建一个 `Boolean` 决策：`target` 填自己的座位号就是上警、
     * 填 null 就是不上警。牌桌上的输入面板、人类提交路径、存档都因此不用为它再加一条分支，
     * 而「选自己」这个动作在界面上也说得通（点自己的头像报名）。
     *
     * **这一步只出决定，不出话**：线下就是举手，理由属于竞选发言环节。所以 instruction
     * 不再诱导模型写竞选宣言（写成 `reason` 会被当成发言播出去，第一阶段就会变成
     * 12 个人各讲一段）。
     */
    fun sheriffSignup(game: WerewolfGame, seat: Int): StructuredSpec<VoteChoice> {
        return StructuredSpec(
            instruction = "输出 {\"target\": 座位号或 null}。" +
                "想竞选警长就把 target 填成你自己的 $seat 号；不想上警就填 null。" +
                "这一步只是举手报名，不说话：别写竞选宣言，那些理由留到竞选发言环节。" +
                "reason 留空字符串。",
            serializer = VoteChoice.serializer(),
            validate = { it.target == null || it.target == seat },
        )
    }

    /**
     * 退水：竞选发言说完了，要不要放弃竞选。
     *
     * 必须把三件事写清楚，模型不会自己推出来：
     * 退了就**不再被投票**、首轮也**没有警长票**（上过警的人首轮不投）——
     * 但平票 PK 时台外的人都能投，退水的人这一轮有票，别把他写成永远的哑巴；
     * 剩下的候选还有谁；如果只剩一个人不退，他直接当选。
     *
     * 兜底是不退水（[WithdrawChoice] 的默认值）——
     * 「想不清楚」不该被当成让位，让位是有成本的。
     */
    fun withdraw(game: WerewolfGame, seat: Int): StructuredSpec<WithdrawChoice> {
        val remaining = game.sheriffCandidates.filter { it != seat }
        // 「退了会怎样」按警上还剩几个人给：一个人时退水就是白送警徽，得说清楚
        val stakes = if (remaining.isEmpty()) {
            "警上只剩你一个，你一退水本局就没有警长。"
        } else {
            "现在除了你以外，还在竞选的是 ${list(remaining)}；" +
                "他们如果全都退水，你直接当选。"
        }
        return StructuredSpec(
            instruction = "输出 {\"withdraw\": true 或 false, \"reason\": \"一句话理由\"}。" +
                "竞选发言已经说完，现在问你要不要**退水**（放弃竞选）。" +
                "退水的人不再被投票；首轮你也没有警长票（上过警的人首轮不投），" +
                "但只要出现平票 PK，台上之外的人都能投，你这一轮照样有票。" +
                stakes + INNER_REASON_HINT,
            serializer = WithdrawChoice.serializer(),
            validate = { true },
        )
    }

    /** 警长投票：只能投上警的那几个人，且不能投自己（自己上警了本来也投不了）。 */
    fun sheriffVote(game: WerewolfGame, candidates: List<Int>): StructuredSpec<VoteChoice> {
        val valid = candidates.sorted()
        // 首轮只有警下能投；PK 轮台上的人不投、其余存活都能投（含上过警没进 PK 的）。
        // 这句话不能写死「你是警下的人」——PK 轮会问到上过警的人，自相矛盾。
        val who = if (game.sheriffRound <= 1) {
            "你是警下的人，这一票决定警徽归谁。"
        } else {
            "现在是平票 PK：台上的人不能投，你在台外，这一票决定警徽归谁。"
        }
        return StructuredSpec(
            instruction = "输出 {\"target\": 座位号或 null, \"reason\": \"一句话理由\"}。" +
                "target 是你要选为警长的人，只能从 ${list(valid)} 中选；实在不想选就填 null。" +
                who + INNER_REASON_HINT,
            serializer = VoteChoice.serializer(),
            validate = { it.target == null || it.target in valid },
        )
    }

    /**
     * 警徽移交。出局的警长决定交给谁，或者撕掉。
     *
     * 两种情况要分开写：[seat] 是被猎人一枪带走的时候，他**没有遗言**，
     * 这一句是唯一能留下的信息——不能让他以为自己在发表遗言，
     * 否则模型会写出一大段悼词，牌桌上一具尸体突然开始长篇大论。
     *
     * [free] 为 true 时不给「交给错的人比撕掉更糟」——那是判断代打。
     */
    fun badgeTransfer(game: WerewolfGame, seat: Int, free: Boolean = false): StructuredSpec<VoteChoice> {
        val valid = game.aliveSeats.filter { it != seat }
        val shot = game.playerAt(seat).deathCause == DeathCause.HUNTER_SHOOT
        val lead = if (shot) {
            "你被猎人开枪带走，当场出局，**没有遗言**。只有一个动作：处理警徽。"
        } else {
            "你在遗言里要交代警徽。"
        }
        val tail = if (shot) {
            "reason 只写你挑这个人的一句理由，不超过 20 字，它会作为你的最后一句话显示。"
        } else {
            "reason 会被当成你的遗言公开说出来，用 1-2 句中文交代你的判断。"
        }
        return StructuredSpec(
            instruction = lead + "输出 {\"target\": 座位号或 null, \"reason\": \"一句话理由\"}。" +
                "target 是你把警徽交给的人，只能从 ${list(valid)} 中选；" +
                "想撕掉警徽（谁也不给）就填 null。" + tail +
                if (free) "" else "注意：把警徽交给一个可能是狼的人，比撕掉更糟。",
            serializer = VoteChoice.serializer(),
            validate = { it.target == null || it.target in valid },
        )
    }

    /** 猎人开枪。目标可以为 null（放弃），所以只需要校验座位存在。 */
    fun hunterShoot(game: WerewolfGame): StructuredSpec<VoteChoice> {
        val valid = game.aliveSeats
        return StructuredSpec(
            instruction = "输出 {\"target\": 座位号或 null, \"reason\": \"一句话理由\"}。" +
                "target 是你要带走的人，只能从 ${list(valid)} 中选；不想开枪就填 null。" +
                "reason 用一句中文说明你的理由。",
            serializer = VoteChoice.serializer(),
            validate = { it.target == null || it.target in valid },
        )
    }

    /**
     * 警长选今天的发言顺序：警左 / 警右 / 死左 / 死右。
     *
     * 把每个选项的**第一个发言人**算给模型看——方向选择的战术意义全在
     * 「谁先开口、谁挨着谁说话」，只给四个词它只能瞎猜。
     * 警左 / 警右自己都是最后归票；死左 / 死右按死者的位置排，自己不一定垫底，
     * 这两条差别也要写进去，否则模型会以为选死左等于让自己早说。
     *
     * 平安夜没人倒牌，死左 / 死右直接不给：没有锚点，给了也排不出来。
     *
     * [free] 为 true 时不给「想帮可疑的人先说 / 可信的人定调」的策略举例。
     */
    fun speechDirection(game: WerewolfGame, seat: Int, free: Boolean = false): StructuredSpec<DirectionChoice> {
        val dead = game.dawnDeadSeats
        val options = SpeechDirection.options(hasDawnDeath = dead.isNotEmpty())
        return StructuredSpec(
            instruction = buildString {
                append("输出 {\"direction\": \"").append(options.joinToString("|") { it.displayName })
                append("\", \"reason\": \"一句话理由\"}。")
                append("你是警长，现在决定今天白天的发言顺序（左 = 座位号增大的方向，右 = 座位号减小的方向）：")
                options.forEach { direction ->
                    append("· ").append(direction.displayName).append("：")
                    append("从 ").append(game.discussionOrderFor(direction).firstOrNull() ?: 0).append(" 号开始")
                    if (direction.anchoredOnDeath) {
                        append("（昨夜倒牌的是 ${list(dead)}，按座位号最小的算）")
                    }
                    append("；")
                }
                append("警左 / 警右都是你自己最后一个发言（归票位）")
                // 平安夜没有死左 / 死右，这句话也得跟着消失：
                // 只减选项、留着「死左死右按死者排」，模型会照着这句话硬选一个不存在的
                if (options.any { it.anchoredOnDeath }) {
                    append("；死左 / 死右按死者的位置排，你自己不一定垫底")
                }
                append("。")
                if (!free) {
                    append("想想哪个方向对你今天的判断更有利：")
                    append("比如想让可疑的人先说、或者让可信的人先定调。")
                }
                append(INNER_REASON_HINT)
            },
            serializer = DirectionChoice.serializer(),
            validate = { choice ->
                val picked = SpeechDirection.ofLabel(choice.direction)
                picked != null && picked in options
            },
        )
    }

    /**
     * 别人说完一句之后，要不要当场插爆。
     *
     * 只有两个答案，`validate` 恒真；兜底 false（不爆）。
     * 真正的上下文由调用方压成一小段简报（[WerewolfPrompt.explodeWatchBrief]），
     * 这里只负责把答案的格式和默认值钉死。
     *
     * **先要收益，再要结论**：字段顺序本身就是提示——`gain` 写在 `explode` 前面，
     * 模型得先把「换到什么」想出来，才轮到决定值不值。
     */
    fun explodeWatch(): StructuredSpec<ExplodeChoice> = StructuredSpec(
        instruction = "输出 {\"gain\": \"爆了能换到什么，一句话\", \"explode\": true 或 false, " +
            "\"reason\": \"一句话理由\"}。" +
            "gain 先算账：爆掉这一天能保住谁、断掉什么信息；换不到东西就写「换不到什么」。" +
            "代价固定是一条命（这一局少一个狼，夜里少一票）——只有收益明显大于代价才填 explode=true。" +
            "没有明确把握时填 false，gain 和 reason 照实写。" +
            "gain 和 reason 都是你心里的判断（没人听见，也不会朗读）。",
        serializer = ExplodeChoice.serializer(),
        validate = { true },
    )

    // ---------------------------------------------------------------------
    // 兜底
    // ---------------------------------------------------------------------

    /** 从候选里随机挑一个；候选为空时返回 null，调用方自己决定怎么退化。 */
    fun <T> pick(candidates: List<T>, random: Random): T? =
        if (candidates.isEmpty()) null else candidates[random.nextInt(candidates.size)]

    /**
     * 狼刀兜底：随机刀一个好人。
     *
     * 兜底刻意**不选空刀、不选自刀**：这两个都是要全队配合的战术，
     * 模型失灵时随手空一刀或刀自己人，比「这局打得平淡」糟糕得多。
     */
    fun fallbackWolfKill(game: WerewolfGame, seat: Int, random: Random): VoteChoice {
        val candidates = game.aliveSeats.filter { it != seat && !game.isWerewolf(it) }
        return VoteChoice(pick(candidates, random) ?: seat, "先听队友的，我没有特别的倾向。")
    }

    /** 查验兜底：优先验没查过的人，全查完了就随机。 */
    fun fallbackSeerCheck(game: WerewolfGame, seat: Int, random: Random): SeatChoice {
        val checked = game.seerChecks.map { it.targetSeat }.toSet()
        val unchecked = game.aliveSeats.filter { it != seat && it !in checked }
        val candidates = unchecked.ifEmpty { game.aliveSeats.filter { it != seat } }
        return SeatChoice(pick(candidates, random) ?: seat, "我再确认一下自己的想法。")
    }

    /**
     * 守卫兜底：从能守的人里随机挑一个。
     *
     * 刻意不兜底成空守：空守是「这一夜白过」，模型偶尔失灵不该让好人白丢一次保护。
     */
    fun fallbackGuard(game: WerewolfGame, random: Random): VoteChoice {
        val target = pick(game.guardTargets, random)
        return VoteChoice(target, "先守住场上最像会被刀的人。")
    }

    /**
     * 投票兜底：尽量投一个「说得过去」的候选。
     *
     * 两条排除：不投自己（真预言家被踩急了会顺势投自己，那等于送），
     * 狼不投队友。后一条是硬伤修补——随机数不知道谁是狼，一票砸在队友身上
     * 等于白送一张牌，而且从票型里根本看不出是失误还是叛变，复盘都查不出来。
     */
    fun fallbackVote(game: WerewolfGame, seat: Int, candidates: List<Int>, random: Random): VoteChoice {
        val selfIsWolf = game.playerAt(seat).role.isWerewolf
        val prefer = candidates.filter { it != seat && !(selfIsWolf && game.isWerewolf(it)) }
            .ifEmpty { candidates.filter { it != seat } }
        val target = pick(prefer, random)
        return VoteChoice(target, if (target == null) "这轮我先不投。" else "我跟一下大家的判断。")
    }

    /** 与 [WerewolfGame.wolves] 对应的身份判断，狼王也算狼。 */
    fun isWolf(game: WerewolfGame, seat: Int): Boolean =
        game.playerAt(seat).role.isWerewolf

    private fun list(seats: List<Int>): String =
        if (seats.isEmpty()) "空（没有可选目标）" else seats.sorted().joinToString("、") { "$it 号" }
}
