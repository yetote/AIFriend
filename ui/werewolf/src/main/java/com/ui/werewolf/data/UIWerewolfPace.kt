package com.ui.werewolf.data

/**
 * 思考时限的四个档位。
 *
 * 这里单独定义一份而不是复用引擎里的枚举：`ui:werewolf` 不依赖 `feature:werewolf`
 * （是 feature 依赖它），所以值只能各写一份，由 route 层做一一映射。
 * 两边的**名称和顺序必须一致**，映射就不必写 when 分支，靠 `entries` 对齐。
 */
enum class UIWerewolfTurnLimit(val label: String, val hint: String) {
    FAST("快速", "思考 30 秒 / 发言不限"),
    STANDARD("标准", "思考 1 分钟 / 发言不限"),
    RELAXED("宽松", "思考 2 分钟 / 发言不限"),
    UNLIMITED("不限", "只受网络超时约束"),
    ;

    /** 一句话说清超时之后会发生什么，面板上直接挂着，免得用户以为「超时就断了」。 */
    val consequence: String
        get() = if (this == UNLIMITED) {
            "不设上限：一个座位就可能让整局停十几分钟"
        } else {
            "超时按兜底继续，并关掉这个座位本局的深度思考"
        }
}

/**
 * 提示词风格。值和引擎里的 `WerewolfPromptStyle` **同名同序**，route 按 name 映射。
 *
 * 这里单独定义一份的理由和 [UIWerewolfTurnLimit] 一样：ui:werewolf 不依赖 feature:werewolf。
 * 引导式是默认——老用户和已有存档的行为不变。
 */
enum class UIWerewolfPromptStyle(val label: String, val hint: String) {
    GUIDED("引导式", "内置读人框架、身份打法和狼队战术。小模型靠它才不会说废话"),
    FREE("自由式", "只给身份、局势和底线，打法全交给模型自己想。适合强推理模型"),
    ;
}

/**
 * 一个座位在本局的时间账本，给对局设置面板看。
 *
 * 只读展示 + 一个开关。这里不做任何判断，超时降级全在引擎里完成。
 */
data class UIWerewolfSeatPace(
    val seat: Int,
    val name: String,
    val avatarPath: String? = null,
    /** 这个座位的深度思考此刻是不是开着。 */
    val thinkingOn: Boolean,
    /**
     * 角色本身是不是开了深度思考。
     *
     * false 表示它从来不开，界面不给点——那不是本局的节奏设置，是角色的属性，
     * 要改得去编辑好友。
     */
    val thinkingAvailable: Boolean,
    /** 最近一步耗时。还没走过为 null。 */
    val lastLabel: String? = null,
    /** 平均耗时，超过两步才有意义。 */
    val averageLabel: String? = null,
    /** 本局超时次数。>0 时界面标红。 */
    val timeoutCount: Int = 0,
    /** 已出局的座位不再耗时，排到后面并压暗。 */
    val eliminated: Boolean = false,
)

/**
 * 一局的节奏设置。
 *
 * [running] 为 false（已结束或还没开始）时，面板只展示不给改——
 * 改一个不会再跑起来的档位没有意义，反而让人以为改完能重打。
 */
data class UIWerewolfPace(
    val limit: UIWerewolfTurnLimit,
    val seats: List<UIWerewolfSeatPace> = emptyList(),
    val running: Boolean = false,
    /** 本局一共超了几次。0 的话顶部不显示这一行。 */
    val totalTimeouts: Int = 0,
)

/**
 * 把毫秒写成人能读的样子。
 *
 * 不写小数点后两位：这个数字的用处是「这个模型有多慢」，看到「12s」和「1 分 05 秒」
 * 就够判断了，多一位精度只会让人以为它很准。
 */
fun formatSpent(ms: Long): String {
    if (ms <= 0L) return "—"
    val seconds = (ms + 500L) / 1000L
    return when {
        seconds < 1L -> "不到 1s"
        seconds < 60L -> "${seconds}s"
        else -> {
            val minutes = seconds / 60L
            val rest = seconds % 60L
            if (rest == 0L) "$minutes 分" else "$minutes 分 ${rest.toString().padStart(2, '0')} 秒"
        }
    }
}
