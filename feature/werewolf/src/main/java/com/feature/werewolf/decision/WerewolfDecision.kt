package com.feature.werewolf.decision

import com.core.ai.structured.StructuredSpec
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

    /** 狼人夜刀。候选排除狼人自己人——不给自己人下刀，省得模型想出「自刀」这种局。 */
    fun wolfKill(game: WerewolfGame, seat: Int): StructuredSpec<SeatChoice> {
        val candidates = game.aliveSeats.filter { it != seat && !game.isWerewolf(it) }
        return StructuredSpec(
            instruction = "输出 {\"target\": 座位号, \"reason\": \"一句话理由\"}。" +
                "target 必须是今晚要刀的目标，只能从 ${list(candidates)} 中选，不能选自己或队友。" +
                "reason 用一句中文说明你为什么刀他，这句话队友会看到。",
            serializer = SeatChoice.serializer(),
            validate = { it.target in candidates },
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
        return StructuredSpec(
            instruction = buildString {
                append("输出 {\"heal\": true 或 false, \"poison\": 座位号或 null, \"reason\": \"一句话理由\"}。")
                append("heal 表示是否用解药救今晚被刀的人")
                append(if (game.witchCanHeal) "（现在可以用）" else "（现在不能用了）")
                append("；poison 表示是否用毒药毒死一个人")
                append(if (game.witchCanPoison) "（现在可以用）" else "（现在不能用了）")
                append("。两种药同一夜不能一起用，不想用药就 heal=false 且 poison=null。")
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
                "reason 用一句中文说明你的判断。",
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
                "reason 用一句中文说明理由。",
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
     */
    fun sheriffSignup(game: WerewolfGame, seat: Int): StructuredSpec<VoteChoice> {
        return StructuredSpec(
            instruction = "输出 {\"target\": 座位号或 null, \"reason\": \"一句话理由\"}。" +
                "想竞选警长就把 target 填成你自己的 $seat 号；不想上警就填 null。" +
                "reason 用一句话说明你的动机（上警就当作竞选宣言的第一句）。",
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
                "你是警下的人，这一票决定警徽归谁。reason 用一句中文说明理由。",
            serializer = VoteChoice.serializer(),
            validate = { it.target == null || it.target in valid },
        )
    }

    /** 警徽移交。出局的警长决定交给谁，或者撕掉。 */
    fun badgeTransfer(game: WerewolfGame, seat: Int): StructuredSpec<VoteChoice> {
        val valid = game.aliveSeats.filter { it != seat }
        return StructuredSpec(
            instruction = "遗言里还要交代警徽。输出 {\"target\": 座位号或 null, \"reason\": \"你的遗言\"}。" +
                "target 是你把警徽交给的人，只能从 ${list(valid)} 中选；" +
                "想撕掉警徽（谁也不给）就填 null。" +
                "reason 会被当成你的遗言公开说出来，用 1-2 句中文交代你的判断。" +
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

    // ---------------------------------------------------------------------
    // 兜底
    // ---------------------------------------------------------------------

    /** 从候选里随机挑一个；候选为空时返回 null，调用方自己决定怎么退化。 */
    fun <T> pick(candidates: List<T>, random: Random): T? =
        if (candidates.isEmpty()) null else candidates[random.nextInt(candidates.size)]

    /** 狼刀兜底：随机刀一个好人。 */
    fun fallbackWolfKill(game: WerewolfGame, seat: Int, random: Random): SeatChoice {
        val candidates = game.aliveSeats.filter { it != seat && !game.isWerewolf(it) }
        return SeatChoice(pick(candidates, random) ?: seat, "先听队友的，我没有特别的倾向。")
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
