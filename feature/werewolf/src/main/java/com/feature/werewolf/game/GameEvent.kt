package com.feature.werewolf.game

/**
 * 公开事件：**所有玩家都能看到的播报**。
 *
 * 这个 sealed 接口是「信息隔离」的分界线，加事件时务必守住两条：
 *
 * 1. 只有上帝会公开宣布的事才能进来。狼人刀了谁（在被救的情况下）、预言家验了谁、
 *    女巫用了什么药，都不能进——这些是各自角色的私有信息，分别存在
 *    [WerewolfGame.seerChecks] 和 [WerewolfGame.witch] 里。
 * 2. 死亡只公布**座位号**，不公布身份。线下规则里身份是死者的秘密，
 *    除非他开了枪（[HunterShot]）或者游戏结束。
 *
 * 编排层可以放心把整个 history 拼进任何人的上下文，这正是把它独立于私有状态的原因。
 */
sealed interface GameEvent {
    /** 这是第几天发生的事。夜晚 N 与白天 N 共用同一个 day 值。 */
    val day: Int

    /** 天亮播报。[deadSeats] 为空即「平安夜」。 */
    data class Dawn(
        override val day: Int,
        val deadSeats: List<Int>,
    ) : GameEvent {
        val isPeaceful: Boolean get() = deadSeats.isEmpty()
    }

    /** 投票结果：有人被放逐。 */
    data class Exiled(
        override val day: Int,
        val seat: Int,
        val voteCount: Int,
    ) : GameEvent

    /** 投票结果：平票到底或全员弃票，无人出局。 */
    data class NoExile(
        override val day: Int,
    ) : GameEvent

    /** 猎人开枪。[targetSeat] 为 null 表示放弃开枪。 */
    data class HunterShot(
        override val day: Int,
        val hunterSeat: Int,
        val targetSeat: Int?,
    ) : GameEvent

    /** 游戏结束。到这里才可以公开全部身份。 */
    data class GameOver(
        override val day: Int,
        val winner: Faction,
    ) : GameEvent
}
