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
    /** 出局者得了几票。 */
    val exileVotes: Int = 0,
)
