package com.feature.werewolf.game

/** 一张选票。[targetSeat] 为 null 表示弃票。 */
data class Ballot(
    val voterSeat: Int,
    val targetSeat: Int?,
)

/**
 * 一次查验记录。
 *
 * 这是**私有信息**：只有预言家本人（和上帝）能看到。它不进 [GameEvent] 历史，
 * 编排层要用它时必须先问「发言的是不是预言家本人」。
 */
data class SeerCheck(
    val day: Int,
    val targetSeat: Int,
    val isWerewolf: Boolean,
)

/** 女巫剩余药品。解药和毒药全局限各一次。 */
data class WitchStock(
    val antidoteAvailable: Boolean = true,
    val poisonAvailable: Boolean = true,
)

/**
 * 一夜的结算记录，**只用于复盘**，不参与任何规则判定。
 *
 * 为什么不能从 [GameEvent.Dawn] 反推：播报只公布「谁死了」。平安夜有三种成因
 * ——狼人空刀、女巫救人、狼刀打在已死的人身上（引擎不允许，但规则上值得写明）
 * ——只看播报分不出来。复盘最想看的恰恰是「那一刀到底落在谁身上」，
 * 所以刀口、用药这些私有信息必须在结算那一刻抄一份下来，否则过后就查不到了。
 */
data class NightRecord(
    val day: Int,
    /** 狼人最终决定刀的人。null 表示空刀（意见不合，或狼已全灭）。 */
    val wolfTarget: Int? = null,
    /**
     * 守卫这一夜守的人。null 表示没有守卫、守卫已出局、或他空守。
     *
     * 单独记一份而不是「刀口没死就是守住了」：刀口没死也可能是女巫用了药，
     * 两者在复盘里是完全不同的结论——一个是守对了，一个是药没了。
     */
    val guarded: Int? = null,
    /** 女巫是否用了救药。 */
    val healed: Boolean = false,
    /** 女巫毒了谁。null 表示这夜没用毒。 */
    val poisonTarget: Int? = null,
    /** 这一夜实际死亡的人，可能含毒杀。 */
    val deaths: List<Int> = emptyList(),
    /**
     * 守救同归：守卫守的人正好是刀口，女巫也用了药，人还是死了。
     *
     * 单独记一笔是为了复盘能解释得通：只摆「狼刀 5 号、守卫守了 5 号、女巫救了 5 号、
     * 5 号死了」，看起来像引擎算错了；写上「守救同归」才知道这是规则。
     */
    val mutualProtection: Boolean = false,
)

/**
 * 还没公布的天亮播报。
 *
 * 第一天的流程是「**先警上竞选，再公布昨夜死讯**」——竞选发言是在还看不见死讯的时候说的，
 * 顺序反了等于让所有人先看到谁死了再去竞选，竞选就变成了对着尸体聊天。
 * 所以这段时间死讯先存在这儿，警长选举结束后才写进 history 并播报。
 */
data class PendingDawn(
    /** 这是第几夜的死讯。 */
    val day: Int,
    /** 昨夜出局的人。空即平安夜。 */
    val deaths: List<Int> = emptyList(),
    /** 熊咆哮的座位（熊还活着且左右邻座有狼时）。 */
    val bearRoarSeat: Int? = null,
)

/** 一轮投票的结局。加投时同一 [VoteRound.day] 会有多条记录，靠 [VoteRound.round] 区分。 */
enum class VoteOutcome {
    /** 有人被放逐。 */
    EXILED,

    /**
     * 票到了白痴身上：翻牌，不死。
     *
     * 和 [EXILED] 分开，是因为复盘里「谁出局了」是按结果统计的——把翻牌记成放逐，
     * 复盘会显示一个明明还活着的人「第 2 天被票出」。
     */
    IDIOT_REVEALED,

    /** 平票，当天还会加投一轮。 */
    TIE,

    /** 平票到底、或全员弃票，无人出局。 */
    NO_EXILE,
}

/**
 * 一轮投票的完整票型。
 *
 * 和 [WerewolfGame.ballots] 的区别：`ballots` 只保留**当前**这一轮（进入下一夜时会清空），
 * 是给牌桌画实时票数用的；这里是逐轮累积的历史，复盘要按天把每一张票摊开看。
 */
data class VoteRound(
    val day: Int,
    /** 当天第几轮，从 1 开始。大于 1 说明前一轮平票。 */
    val round: Int,
    val ballots: List<Ballot>,
    val outcome: VoteOutcome,
    /** [outcome] 为 [VoteOutcome.EXILED] 时是被放逐的人。 */
    val exiledSeat: Int? = null,
    /**
     * 出局者得了几票。
     *
     * 是 [Double] 而不是 [Int]：警长一票算 1.5，票数会出现 3.5 这种值。
     * 用半点制在引擎里累加（见 `WerewolfGame.voteWeight`），这里只是把它还原成票数，
     * 所以不会出现 0.1+0.2 那种浮点尾数。
     */
    val exileVotes: Double = 0.0,
)

/**
 * 票数的显示形式：整数票不写小数点，半点票写一位小数。
 *
 * 引擎内部用「半点」当单位（平民 2 点、警长 3 点），所以这里的值一定落在 .0 / .5 上，
 * 直接判断小数部分即可，不需要四舍五入。
 */
fun formatVotes(value: Double): String {
    val whole = value.toInt()
    return if (value == whole.toDouble()) whole.toString() else value.toString()
}
