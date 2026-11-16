package com.ui.werewolf.data

/**
 * 对局进行中的一份快照，供进度页使用。
 *
 * 牌桌落地之前，这一页只回答三个问题：现在几点、在做什么、刚刚发生了什么。
 * 有了它，点「开局」不会变成一片空白等几十秒。
 */
data class UIWerewolfLive(
    val day: Int,
    /** 例如「白天讨论」。 */
    val phaseLabel: String,
    val running: Boolean,
    val finished: Boolean,
    /** 已结束时的胜负，「狼人胜」/「好人胜」。 */
    val winnerLabel: String?,
    /** 例如「第 2 天 · 发言 3/8」。还没开始时为 null。 */
    val progressLabel: String?,
    /** 当前阶段内部进度，0..1。并发阶段为 null（不知道中间态，就不假装知道）。 */
    val progressFraction: Float?,
    /** 上帝播报，时间正序，最后一条最新。 */
    val broadcasts: List<String>,
    /** 出错信息（多为网络或 Key 问题），局面不会因此丢失。 */
    val error: String?,
)
