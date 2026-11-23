package com.feature.werewolf.game

/**
 * 白天发言的起手位置与方向。
 *
 * 只有**有警长**的板子才有方向可选：警长每天在讨论开始前选一次。
 * 四种选法分成两组：
 * - **警左 / 警右**：从警长自己的左手边 / 右手边起，绕一圈，警长最后说（归票位不变）。
 * - **死左 / 死右**：从昨夜死者的左手边 / 右手边起。多人倒牌时按座位号最小的那个算。
 *   平安夜没人倒牌，这两种不可选。
 *
 * 「左」统一指**座位号增大的方向**，「右」指座位号减小——线上没有真正的左右手，
 * 只能钉死一个方向词，否则每个人心里的「左手边」都不同。
 *
 * 没警长的板子不用方向：起点和顺序都由系统随机指定（见 `WerewolfGame.opening`）。
 */
enum class SpeechDirection(val displayName: String) {
    /** 从警长的下一位（座位号 +1 方向）起，绕一圈，警长最后。 */
    SHERIFF_LEFT("警左"),

    /** 从警长的上一位（座位号 -1 方向）起，倒着绕，警长最后。 */
    SHERIFF_RIGHT("警右"),

    /** 从昨夜死者的下一位起，绕一圈。 */
    DEATH_LEFT("死左"),

    /** 从昨夜死者的上一位起，倒着绕。 */
    DEATH_RIGHT("死右");

    /** true = 按座位号递增的方向排；false = 递减。 */
    val clockwise: Boolean
        get() = this == SHERIFF_LEFT || this == DEATH_LEFT

    /** 这一项锚在昨夜死者身上（平安夜不可选）。 */
    val anchoredOnDeath: Boolean
        get() = this == DEATH_LEFT || this == DEATH_RIGHT

    companion object {

        /** 今天可选的几项。平安夜只剩警左 / 警右。 */
        fun options(hasDawnDeath: Boolean): List<SpeechDirection> =
            entries.filter { hasDawnDeath || !it.anchoredOnDeath }

        /**
         * 从模型或界面给的标签解析。
         *
         * 认中文名（警左…），也认旧档里的「顺时针 / 逆时针」——那是之前只有两项时的叫法，
         * 现在分别等于警左、警右，老存档的 `SPEECH_DIRECTION` 事件靠这里读回来。
         */
        fun ofLabel(raw: String?): SpeechDirection? = when (raw?.trim()) {
            null, "" -> null
            SHERIFF_LEFT.displayName, "SHERIFF_LEFT", "CLOCKWISE", "顺时针", "left" -> SHERIFF_LEFT
            SHERIFF_RIGHT.displayName, "SHERIFF_RIGHT", "COUNTER_CLOCKWISE", "逆时针", "right" -> SHERIFF_RIGHT
            DEATH_LEFT.displayName, "DEATH_LEFT", "deadLeft" -> DEATH_LEFT
            DEATH_RIGHT.displayName, "DEATH_RIGHT", "deadRight" -> DEATH_RIGHT
            else -> null
        }
    }
}
