package com.feature.werewolf.route

import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.game.Phase

/**
 * 舞台上的「第几天 / 什么时候」。
 *
 * 引擎是一路跑到底的：语音还在念第一夜的狼人商议，`game.phase` 早已推进到天亮播报。
 * 直接读实时阶段就会出现「嘴里还在夜里，背景已经泛白」——声音和画面各说各的。
 *
 * 所以朗读没追上时，舞台时间必须停在**已播到最后一条**消息所处的阶段上：
 * 那条消息才是观众此刻眼睛和耳朵都在的那个瞬间。追平了就没有「过去」可言，
 * 用实时阶段（否则最后一条播完，界面会停在上一阶段不动）。
 *
 * 这条规则只此一处：背景、第几天、白天黑夜、进度、座位高亮都从它取值。
 * 各写一份的话，迟早有一处忘了跟闸门，于是又出现「画面跑到声音前面」。
 */
internal data class StageMoment(val day: Int, val phase: Phase?)

internal fun stageMoment(
    transcript: List<WerewolfMessage>,
    revealed: Int,
    gated: Boolean,
    fallbackDay: Int,
    fallbackPhase: Phase?,
): StageMoment {
    // 空 transcript 时 lastIndex 是 -1，`revealed < -1` 恒不成立，自然落到 fallback。
    val behind = gated && revealed < transcript.lastIndex
    // revealed = -1（第一条还没念完）时给开头那条：队列里已经在放它了。
    val message = if (behind) transcript.getOrNull(revealed.coerceAtLeast(0)) else null
    return if (message == null) {
        StageMoment(fallbackDay, fallbackPhase)
    } else {
        StageMoment(message.day, message.phase)
    }
}
