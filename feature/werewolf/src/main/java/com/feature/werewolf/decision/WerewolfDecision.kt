package com.feature.werewolf.decision

import com.core.ai.structured.StructuredSpec
import com.feature.werewolf.game.DeathCause
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
 * 警长选发言方向。`clockwise` = 顺时针（从警长下一位起），false = 逆时针（从上一位倒着绕）。
 *
 * 只有两个选项且都合法，`validate` 恒真；兜底选顺时针（老规则），宁可平淡也不赌。
 */
@Serializable
data class DirectionChoice(
    val clockwise: Boolean,
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
        val doomed = victim == seat &&
            !(game.witch.antidoteAvailable && game.day == 1 && game.config.witchCanSelfHealOnFirstNight)
        return StructuredSpec(
            instruction = buildString {
                append("输出 {\"heal\": true 或 false, \"poison\": 座位号或 null, \"reason\": \"一句话理由\"}。")
                append(
                    when (victim) {
                        null -> "今晚没有人被狼人袭击。"
                        seat -> "今晚被狼人袭击的是你自己（$seat 号）。"
                        else -> "今晚被狼人袭击的是 $victim 号。"
                    }
                )
                append("heal 表示是否用解药救今晚被刀的人")
                append(
                    when {
                        // 「不能用」有好几种原因，必须分清：说成同一句，女巫会以为自己漏了信息。
                        // 自己中刀时优先说「救不了你自己」——库存状态上一句已经交待过，
                        // 这一刻对她有用的信息是「后果」，不是「药还剩几瓶」。
                        doomed -> "（救不了你自己）"
                        !game.witch.antidoteAvailable -> "（解药已经用完了）"
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
                "target 是你要投的人，只能从 ${list(valid)} 中选；不想投任何人就填 null。" +
                INNER_REASON_HINT,
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

    /** 警长投票：只能投上警的那几个人，且不能投自己（自己上警了本来也投不了）。 */
    fun sheriffVote(game: WerewolfGame, candidates: List<Int>): StructuredSpec<VoteChoice> {
        val valid = candidates.sorted()
        return StructuredSpec(
            instruction = "输出 {\"target\": 座位号或 null, \"reason\": \"一句话理由\"}。" +
                "target 是你要选为警长的人，只能从 ${list(valid)} 中选；实在不想选就填 null。" +
                "你是警下的人，这一票决定警徽归谁。" + INNER_REASON_HINT,
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
     */
    fun badgeTransfer(game: WerewolfGame, seat: Int): StructuredSpec<VoteChoice> {
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
                "注意：把警徽交给一个可能是狼的人，比撕掉更糟。",
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
     * 警长选今天的发言方向。
     *
     * 把两头的「第一个发言的人」算给模型看——方向选择的战术意义全在
     * 「谁先开口、谁挨着警长说话」，只给顺/逆两个字它只能瞎猜。
     * 警长自己最后归票，两个方向都不变，instruction 里说明白，
     * 免得模型以为选逆序就能让自己早说。
     */
    fun speechDirection(game: WerewolfGame, seat: Int): StructuredSpec<DirectionChoice> {
        val alive = game.aliveSeats.sorted()
        val clockwiseFirst = alive.firstOrNull { it > seat }
        val counterFirst = alive.lastOrNull { it < seat } ?: alive.lastOrNull()
        return StructuredSpec(
            instruction = buildString {
                append("输出 {\"clockwise\": true 或 false, \"reason\": \"一句话理由\"}。")
                append("你是警长，现在决定今天白天的发言方向：")
                append("clockwise=true 是顺时针，从 $clockwiseFirst 号开始按座位号递增绕一圈；")
                append("clockwise=false 是逆时针，从 $counterFirst 号开始按座位号递减倒着绕一圈。")
                append("两种方向你自己都是最后一个发言（归票位），")
                append("选方向等于选「谁第一个开口、谁紧挨着你说话」。")
                append("想想哪个方向对你今天的判断更有利：")
                append("比如想让可疑的人先说、或者让可信的人先定调。")
                append(INNER_REASON_HINT)
            },
            serializer = DirectionChoice.serializer(),
            validate = { true },
        )
    }

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

    /** 投票兜底：随机投一个候选，尽量不投自己。 */
    fun fallbackVote(game: WerewolfGame, seat: Int, candidates: List<Int>, random: Random): VoteChoice {
        val prefer = candidates.filter { it != seat }.ifEmpty { candidates }
        val target = pick(prefer, random)
        return VoteChoice(target, if (target == null) "这轮我先不投。" else "我跟一下大家的判断。")
    }

    /** 与 [WerewolfGame.wolves] 对应的身份判断，狼王也算狼。 */
    fun isWolf(game: WerewolfGame, seat: Int): Boolean =
        game.playerAt(seat).role.isWerewolf

    private fun list(seats: List<Int>): String =
        if (seats.isEmpty()) "空（没有可选目标）" else seats.sorted().joinToString("、") { "$it 号" }
}
