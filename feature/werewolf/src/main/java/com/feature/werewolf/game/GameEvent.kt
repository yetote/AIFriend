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

    /**
     * 投票结果：有人被放逐。
     *
     * [voteCount] 是 [Double]：警长的一票算 1.5，得票会出现 3.5 这种值。
     * 播报里的数字必须和「谁票多」这件事一致，否则玩家会算不明白为什么是他出局。
     */
    data class Exiled(
        override val day: Int,
        val seat: Int,
        val voteCount: Double,
    ) : GameEvent

    /** 投票结果：平票到底或全员弃票，无人出局。 */
    data class NoExile(
        override val day: Int,
    ) : GameEvent

    /**
     * 白痴翻牌：被票到最高票，翻开身份但不死。
     *
     * 这是 [Exiled] 之外的另一条出路，所以不能复用 [NoExile]——那天明明投出了结果，
     * 说「无人被放逐」会让所有人以为自己看错了。翻牌之后身份是公开的，
     * 因此这里可以（也应该）说出「白痴」两个字。
     */
    data class IdiotRevealed(
        override val day: Int,
        val seat: Int,
        val voteCount: Double,
    ) : GameEvent

    /**
     * 开枪。[targetSeat] 为 null 表示放弃。
     *
     * [shooterLabel] 只决定播报里的称呼（「猎人」或「狼王」），不参与规则判定。
     * 开枪之后身份已经公开，所以这里可以说出身份，不必像死亡播报那样只报座位。
     */
    data class HunterShot(
        override val day: Int,
        val hunterSeat: Int,
        val targetSeat: Int?,
        val shooterLabel: String = "猎人",
    ) : GameEvent

    /**
     * 熊咆哮：天亮时熊还活着，且左右邻座里有存活狼人。
     *
     * 这是公开线索，和 [Dawn] 一起宣布。没有咆哮**不**代表两边都是好人——
     * 熊可能已经死了，这是这条信息最容易被误读的地方，播报文案要说清楚。
     */
    data class BearRoar(
        override val day: Int,
        val seat: Int,
    ) : GameEvent

    /**
     * 狼人白天自爆。
     *
     * 这条播报的意义不只是「死了一个人」：它同时宣布**这一天到此为止**——
     * 没有投票、没有人被放逐，直接进入黑夜。不写清楚，其他人会以为
     * 自己漏看了一轮投票。
     */
    data class WolfExploded(
        override val day: Int,
        val seat: Int,
    ) : GameEvent

    /** 警长选出来了。[votes] 是得票数（警下每人一票，所以是整数）。 */
    data class SheriffElected(
        override val day: Int,
        val seat: Int,
        val votes: Int,
    ) : GameEvent

    /**
     * 本局没有警长。
     *
     * [reason] 是引擎给出的短语（「无人上警」「两轮平票」），不用枚举：
     * 这几个原因都不带状态，只是解释给玩家听，写成字符串反而少一处改动。
     */
    data class SheriffVacant(
        override val day: Int,
        val reason: String,
    ) : GameEvent

    /** 警长投票平票，进入 PK：这几个座位再各说一轮，然后重投。 */
    data class SheriffPk(
        override val day: Int,
        val seats: List<Int>,
    ) : GameEvent

    /**
     * 警徽移交。[toSeat] 为 null 表示警长把警徽撕了（或没来得及指定就出局）。
     *
     * 和 [SheriffVacant] 分开：那个是「从来没选出警长」，这个是「有过，现在没了」。
     * 复盘里这两件事的差别很大——前者是开局没人上警，后者是警长死了。
     */
    data class SheriffBadgePassed(
        override val day: Int,
        val fromSeat: Int,
        val toSeat: Int?,
    ) : GameEvent

    /** 游戏结束。到这里才可以公开全部身份。 */
    data class GameOver(
        override val day: Int,
        val winner: Faction,
    ) : GameEvent
}
