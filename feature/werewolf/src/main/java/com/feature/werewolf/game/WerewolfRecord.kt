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
