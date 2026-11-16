package com.ui.werewolf.data

/**
 * 狼人杀界面的展示模型。
 *
 * ui 模块不依赖 feature 模块（全项目都是 feature → ui 的单向依赖），
 * 所以这里不出现 `Friend` / `WerewolfGameSummary` 这些领域类型，
 * 由 route 层做一次映射。好处是界面能脱离数据库单独预览。
 */

/** 选人列表里的一项。 */
data class UIWerewolfFriend(
    val id: String,
    val name: String,
    val avatarPath: String?,
    val role: String,
    val model: String,
)

/**
 * 座位条上的一格。
 *
 * 只表达「这一局有几个位置、填了几个」，不表达「谁坐几号」——
 * 座位号开局时洗牌决定，配置页不该给出一个之后会变的数字。
 */
data class UIWerewolfSeat(
    val name: String?,
    val avatarPath: String?,
    /** 这一格是「你」自己。 */
    val isSelf: Boolean = false,
)

/** 一局的历史记录。 */
data class UIWerewolfRecord(
    val id: String,
    val day: Int,
    /** 已经是给用户看的文案，例如「白天讨论」。 */
    val phaseLabel: String,
    val finished: Boolean,
    /** 已结束时的胜负，未结束为 null。 */
    val winnerLabel: String?,
    /** 例如「3 分钟前」。 */
    val timeLabel: String,
)

/** 玩法：自己坐一个座位，或纯观战。 */
enum class UIWerewolfMode(val label: String, val hint: String) {
    PLAY(label = "我参与", hint = "你占一个座位，能发言、投票、被刀"),
    SPECTATE(label = "纯观战", hint = "全部由 AI 出演，你能看到所有人的身份"),
}

/** 板子。身份构成文案由调用方给，ui 不重复定义规则。 */
data class UIWerewolfPreset(
    val key: String,
    val title: String,
    val playerCount: Int,
    val composition: String,
)
