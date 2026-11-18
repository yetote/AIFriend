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
    speechBusy: Boolean = false,
): StageMoment {
    if (transcript.isEmpty()) return StageMoment(fallbackDay, fallbackPhase)

    // 两条都要判，缺一个就会把舞台交给引擎：
    //
    // - `revealed < lastIndex`：后面还有没念到的；
    // - `speechBusy`：**最后一条正在念**。`revealed` 是「取出即前进」（为了边看边听），
    //   所以念最后一条时它已经等于 lastIndex，看着像「追平了」，但喇叭还在响。
    //   这时引擎早把状态推到下一阶段了——遗言念到一半背景就入夜，正是这条漏判。
    val behind = gated && (speechBusy || revealed < transcript.lastIndex)
    if (!behind) return StageMoment(fallbackDay, fallbackPhase)

    // revealed 可能落在可见范围之外（理论上不会），夹一下再取，取不到就回落实时阶段
    val message = transcript.getOrNull(revealed.coerceIn(0, transcript.lastIndex))
        ?: return StageMoment(fallbackDay, fallbackPhase)
    return StageMoment(message.day, message.phase)
}
