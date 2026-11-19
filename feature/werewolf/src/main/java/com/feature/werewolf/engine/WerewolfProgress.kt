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
    /** 正在进行的是谁。串行阶段（发言、遗言）有确定的「当前」，并发阶段为 null。 */
    val seat: Int? = null,
    /**
     * 并发阶段**正在请求**的座位们。
     *
     * 警长投票、上警报名、狼人商议、夜间投票都是好几个人同时在问模型，
     * 这时候没有唯一的「当前」——但界面上不能只写「准备中」：
     * 用户看到的是一个转圈 Eleven 秒，却不知道场上在发生什么。
     * 把这一批人的座位报出来，顶栏就能写「3、5、7 号在投票」。
     */
    val seats: List<Int> = emptyList(),
    /** 补充说明，比如「平票，重新商定」。 */
    val note: String? = null,
) {

    /**
     * 阶段、进度和「在等谁」：「发言 3/8 · 5 号」。
     *
     * **不带天数**：牌桌顶栏的标题那一行已经在报「第 3 天」，副标题再报一遍
     * 就是同一句话写两遍——原来底下还另有一条状态条，三处报的是同一件事。
     * 座位跟在后面说明「在等谁」，比在底下另开一条省地方。
     */
    val stageLabel: String
        get() = buildString {
            append(stageText)
            val busy = if (seats.isNotEmpty()) seats else listOfNotNull(seat)
            if (busy.isNotEmpty()) {
                append(" · ").append(busy.joinToString("、")).append(" 号")
            }
        }

    /**
     * 只讲阶段和进度，既不带天数也不带座位：「狼人商议 1/3」。
     *
     * 夜里只能用这个：夜里谁在动、动的那个座位是谁，本身就是秘密
     * （狼人在商议、守卫在守人、预言家在查验——亮出来等于报身份）。
     */
    val stageText: String
        get() = buildString {
            append(action)
            when {
                step <= 0 -> append(" 准备中")
                total > 1 -> append(' ').append(step).append('/').append(total)
            }
            note?.let { append(" · ").append(it) }
        }

    /**
     * 整天一句：「第 2 天 · 发言 3/8 · 5 号」。
     *
     * 界面上不分两行显示，所以顶栏不用它；日志、存档摘要这类
     * 「脱离牌桌单独出现」的地方才需要把天数带上。
     */
    val label: String
        get() = buildString {
            append("第 ").append(day)
            append(if (phase.isNight) " 夜 · " else " 天 · ")
            append(stageLabel)
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
