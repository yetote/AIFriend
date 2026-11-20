package com.feature.werewolf.game

/**
 * 白天发言的方向。
 *
 * 只有**有警长**的板子才有方向可选：警长每天在讨论开始前选一次，
 * 从自己该方向的下一位开始发言，自己仍然最后说（归票位不变）。
 * 没警长的板子沿用旧规则——从昨夜死者下位起、按座位号递增，方向固定顺时针。
 */
enum class SpeechDirection(val displayName: String) {
    /** 顺时针：从警长的下一位（座位号增大的方向）开始，默认方向，老档行为不变。 */
    CLOCKWISE("顺时针"),

    /** 逆时针：从警长的上一位（座位号减小的方向）开始倒着绕。 */
    COUNTER_CLOCKWISE("逆时针"),
}
