package com.ui.werewolf.data

/**
 * 复盘页的全部内容。**只在分出胜负之后**才会构建。
 *
 * 复盘和牌桌的区别在信息性质：牌桌上「谁能看到什么」是核心，复盘里一切都已经公开。
 * 所以这个模型里没有 audience、没有「仅你可见」——它描述的是**已经结束的过去**，
 * 而不是**正在进行的博弈**。把两者塞进同一个模型，迟早会有一处该藏的信息没藏住。
 */
data class UIWerewolfReview(
    /** 「狼人胜」/「好人胜」。 */
    val winnerLabel: String,
    /** 狼人赢还是好人赢，决定横幅配色。 */
    val wolfWon: Boolean,
    /** 为什么会结束，例如「狼人全部出局」。光有胜负，玩家不知道最后一刀是谁的功劳。 */
    val reason: String,
    /** 「9 人 · 4 天 · 5 人出局」。 */
    val facts: String,
    /** 你在这局里是谁。观战为 null。 */
    val selfLine: String?,
    /** 按阵营分好组的底牌。 */
    val groups: List<UIWerewolfRoleGroup> = emptyList(),
    /** 逐日票型，按天正序。 */
    val days: List<UIWerewolfDayReport> = emptyList(),
    /** 逐夜行动，按夜正序。 */
    val nights: List<UIWerewolfNightReport> = emptyList(),
    /** 预言家查验记录。 */
    val checks: List<UIWerewolfCheckReport> = emptyList(),
    /**
     * 数据不全。
     *
     * 只有一种来源：这局是旧版本存档续下来的，那时候还没记夜间行动和票型历史。
     * 与其显示成「什么都没发生」，不如明说这一段缺了——编一个看起来完整的复盘更糟。
     */
    val incomplete: Boolean = false,
)

/** 一组底牌的归属。配色和文案都以它为准，不去匹配标题字符串——标题随时会改。 */
enum class UIWerewolfGroupKind {
    WOLF,
    GOD,
    VILLAGER,
}

/** 一组底牌。「狼人」和「神职」「平民」分开，是因为复盘第一眼要数的是「几个狼」。 */
data class UIWerewolfRoleGroup(
    val kind: UIWerewolfGroupKind,
    val title: String,
    val members: List<UIWerewolfRevealedSeat> = emptyList(),
)

/** 一位玩家的最终归宿。 */
data class UIWerewolfRevealedSeat(
    val seat: Int,
    val name: String,
    val avatarPath: String? = null,
    val roleLabel: String,
    val isSelf: Boolean = false,
    /** 活到最后。 */
    val alive: Boolean = true,
    /** 「第 2 天被票出」。活着的时候为 null。 */
    val fate: String? = null,
    /** 整局收了多少票。0 不显示——写一个「0 票」反而像在挖苦人。 */
    val votes: Int = 0,
)

/** 一天的投票。 */
data class UIWerewolfDayReport(
    val day: Int,
    /** 「放逐 5 号（4 票）」/「平票，无人出局」/「全员弃票」。 */
    val result: String,
    /** 这天真的有人出局。没有的话结果行不标红。 */
    val exiled: Boolean,
    /** 加投轮数。0 表示一天只投了一轮。 */
    val extraRounds: Int = 0,
    val ballots: List<UIWerewolfBallotLine> = emptyList(),
)

/** 一张票。[target] 为 null 是弃票。 */
data class UIWerewolfBallotLine(
    val voter: Int,
    val target: Int?,
    /** 这一票投给了当天被放逐的人。复盘时最该被看见的就是这些票。 */
    val key: Boolean = false,
)

/** 一夜的行动。 */
data class UIWerewolfNightReport(
    val day: Int,
    /** 「狼刀 6 号」/「狼人空刀」。 */
    val wolfLine: String,
    /** 「守卫守 6 号」。没有守卫、守卫出局或空守时为 null。 */
    val guardLine: String? = null,
    /** 「女巫用解药救下」/「女巫毒 7 号」。没用则 null。 */
    val witchLine: String? = null,
    /** 「6 号出局」/「平安夜」。 */
    val deathLine: String,
)

/** 一次查验。复盘时它从私有信息变成公开信息，这正是复盘最有意思的部分。 */
data class UIWerewolfCheckReport(
    val day: Int,
    val target: Int,
    val isWolf: Boolean,
)
