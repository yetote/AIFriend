package com.feature.werewolf.game

/**
 * 板子清单。
 *
 * 「板子」在这里有唯一含义：一个固定的座位人数 + 身份构成 + 胜负规则。
 * 玩家在开局页挑一张卡片，就是挑了一个 [key]。
 *
 * 为什么要提成公共对象：提示词自定义要**按板子**分别保存，
 * 而保存需要一个稳定的标识。`WerewolfConfig` 自己没有——
 * 它的 `composition` 是 Map，没有自定义 toString，Map 的迭代顺序也不保证，
 * 拿它当 key 迟早对不上。所以用这里这张表里的字符串常量。
 *
 * 顺序按人数排，不是按推荐度：玩家挑板子时先问「凑得齐几个人」，
 * 把 6 人局放在最后会让人以为要凑满 12 个才能开局。
 */
object WerewolfBoards {

    const val QUICK_6 = "quick6"
    const val STANDARD_8 = "standard8"
    const val STANDARD_9 = "standard9"
    const val GUARD_10 = "guard10"
    const val KING_10 = "king10"
    const val GUARD_12 = "guard12"
    const val KING_12 = "king12"
    const val FOOL_12 = "fool12"
    const val BEAR_12 = "bear12"

    /** 一张板子：稳定标识 + 名字 + 规则配置。 */
    data class Board(
        val key: String,
        val title: String,
        val config: WerewolfConfig,
    )

    /**
     * 开警长的板子。
     *
     * `maxVoteRounds = 2` 跟着一起开：警长在场上才能主持 PK，这两个开关是配套的。
     * 只开警长而把加投关掉，等于有一位「票权 1.5 但按不出第二轮」的警长。
     */
    fun sheriffBoard(composition: Map<WerewolfRole, Int>): WerewolfConfig =
        WerewolfConfig(composition = composition, hasSheriff = true, maxVoteRounds = 2)

    val all: List<Board> = listOf(
        Board(QUICK_6, "6 人快局", WerewolfConfig(composition = WerewolfConfig.Quick6)),
        Board(STANDARD_8, "8 人标准局", WerewolfConfig(composition = WerewolfConfig.Standard8)),
        Board(STANDARD_9, "9 人标准局", WerewolfConfig(composition = WerewolfConfig.Standard9)),
        Board(GUARD_10, "10 人守卫局", WerewolfConfig(composition = WerewolfConfig.Guard10)),
        Board(KING_10, "10 人狼王局", WerewolfConfig(composition = WerewolfConfig.King10)),
        Board(GUARD_12, "12 人守卫局", sheriffBoard(WerewolfConfig.Guard12)),
        Board(KING_12, "12 人狼王守卫局", sheriffBoard(WerewolfConfig.King12)),
        Board(FOOL_12, "12 人白痴局", sheriffBoard(WerewolfConfig.Fool12)),
        Board(BEAR_12, "12 人熊局", sheriffBoard(WerewolfConfig.Bear12)),
    )

    /** 按 key 取配置。认不出来就回落到标准 8 人局。 */
    fun configOf(boardKey: String): WerewolfConfig =
        all.firstOrNull { it.key == boardKey }?.config ?: WerewolfConfig()

    /** 按 key 取板子名。 */
    fun titleOf(boardKey: String): String =
        all.firstOrNull { it.key == boardKey }?.title ?: "自定义局"

    /**
     * 按配置**反查**板子的 key：认不出就回空串。
     *
     * 续档时用得上：存档里只有 `WerewolfConfig`（没有 key，key 是界面概念），
     * 而这一局专属的提示词自定义是按 key 存的。不反查就只能按「全局」那一层接着打，
     * 玩家给这张板子单独写的要求会在暂停/继续之后悄悄消失。
     *
     * 比对的是整个配置（data class 相等），不是像提示词认板子那样只比结构三件套——
     * 反查出来的 key 要去取一段**存储**，认错就等于把别张板子的要求套上来，宁可认不出。
     */
    fun keyOf(config: WerewolfConfig): String =
        all.firstOrNull { it.config == config }?.key.orEmpty()
}
