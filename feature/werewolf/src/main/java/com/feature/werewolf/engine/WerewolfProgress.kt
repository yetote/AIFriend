package com.feature.werewolf.engine

import com.feature.werewolf.game.Phase

/**
 * 一次「现在正在做什么」的进度快照。
 *
 * 这里的数字不是给人看的装饰。8 人局一局下来 60-100 次请求，串行阶段（白天发言）
 * 每一步都要等模型返回，一个夜晚加上白天可能几十秒没有任何输出。没有进度，
 * 玩家会以为卡死了——而实际上只是在等第 4 个人说话。
 *
 * [step] 从 1 开始。为 0 表示这个阶段刚开始、还在等第一批结果（并发阶段无法更细）。
 */
data class WerewolfProgress(
    val day: Int,
    val phase: Phase,
    val step: Int,
    val total: Int,
    /** 正在进行的是谁。并发阶段（狼人商议、投票）没有确定的「当前」，为 null。 */
    val seat: Int? = null,
    /** 补充说明，比如「平票，重新商定」。 */
    val note: String? = null,
) {

    /** 直接给界面显示的一句话，例如「第 2 天 · 发言 3/8」。 */
    val label: String
        get() = buildString {
            append("第 ").append(day)
            append(if (phase.isNight) " 夜 · " else " 天 · ")
            append(action)
            when {
                step <= 0 -> append(" 准备中")
                total > 1 -> append(' ').append(step).append('/').append(total)
            }
        }

    private val action: String
        get() = when (phase) {
            Phase.NIGHT_GUARD -> "守卫守护"
            Phase.NIGHT_WOLF_CHAT -> "狼人商议"
            Phase.NIGHT_WOLF -> "狼人下刀"
            Phase.NIGHT_SEER -> "预言家查验"
            Phase.NIGHT_WITCH -> "女巫用药"
            Phase.DAY_SHERIFF_SIGNUP -> "上警"
            Phase.DAY_SHERIFF_SPEECH -> "竞选发言"
            Phase.DAY_SHERIFF_VOTE -> "警长投票"
            Phase.DAY_DISCUSSION -> "发言"
            Phase.DAY_VOTE -> "投票"
            Phase.DAY_LAST_WORDS -> "遗言"
            Phase.HUNTER_SHOOT -> "开枪"
            Phase.FINISHED -> "结算"
        }
}

/**
 * 夜晚阶段算「夜」，其余都算「天」。守卫在狼人之前，所以第一个算进来。
 *
 * 猎人开枪两边都可能发生（天亮被刀之后、白天被票出之后），但按线下习惯
 * 都归到白天的播报里，所以算「天」。
 */
val Phase.isNight: Boolean
    get() = this == Phase.NIGHT_GUARD || this == Phase.NIGHT_WOLF_CHAT ||
        this == Phase.NIGHT_WOLF || this == Phase.NIGHT_SEER || this == Phase.NIGHT_WITCH
