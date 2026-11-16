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
    /** 几人局。0 表示旧档升级上来的行，说不清人数，列表不显示。 */
    val playerCount: Int = 0,
    /** 这一局我坐在桌上。false 表示纯观战。 */
    val withHuman: Boolean = false,
) {
    /**
     * 一行的身份摘要，例如「8 人 · 你参与了」。
     *
     * 拼在这里而不是让界面各写一遍：未完成的卡、已结束的行、复盘入口
     * 三处都要这一行字，各写一遍迟早有一处忘了改。
     */
    val metaLabel: String
        get() = buildList {
            if (playerCount > 0) add("$playerCount 人")
            add(if (withHuman) "你参与了" else "纯观战")
        }.joinToString(" · ")
}

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

/** 一次连通性检查的状态。 */
enum class UIWerewolfApiState {
    /** 还没查过。不给任何标记——灰色问号会让「没查」看起来像「查出了问题」。 */
    IDLE,
    CHECKING,
    OK,
    FAILED,
}

/**
 * 某个座位背后那个模型，此刻能不能调通。
 *
 * 它挂在好友上而不是座位上：配置页还没有座位号，而且换板子会重新洗牌，
 * 但「这个模型能不能用」跟坐几号没关系。
 */
data class UIWerewolfApiCheck(
    val state: UIWerewolfApiState,
    /** 失败原因，已经翻成人话，例如「API Key 无效或已过期」。成功时为 null。 */
    val message: String? = null,
    val elapsedMs: Long? = null,
)
