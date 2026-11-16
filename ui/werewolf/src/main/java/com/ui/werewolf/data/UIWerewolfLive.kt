package com.ui.werewolf.data

/**
 * 牌桌上一枚座位的实时状态。
 *
 * 这里面**没有**「是不是狼」之外的任何私有信息：谁是什么身份由 [roleLabel] 决定，
 * 而它只在「观战」或「已结束」时才非 null。参与中的局里，别人的身份连 UI 都拿不到，
 * 想泄露也没有路径。
 */
data class UIWerewolfSeatLive(
    val seat: Int,
    val name: String,
    val avatarPath: String? = null,
    /** 是不是「你」。座位环上要圈出来，不然切回页面会找不到自己。 */
    val isSelf: Boolean = false,
    val alive: Boolean = true,
    /** 出局方式。null 表示还在桌上。 */
    val fate: UIWerewolfFate? = null,
    /** 身份名。观战/结束时才有，其余时候为 null。 */
    val roleLabel: String? = null,
    /** 狼人阵营。只在身份公开后用来给名字上色。 */
    val wolf: Boolean = false,
    /** 此刻正在行动或发言。 */
    val active: Boolean = false,
    /** 当前得票数。null 表示还没人投。 */
    val votes: Int? = null,
)

/**
 * 出局方式。
 *
 * [short] 是贴在头像下的一个字，[full] 是详情里的一句话。
 * 分开是因为座位环上只能放一个字——但只用一个字又不够，色弱的人看不出「刀」和「毒」的差别，
 * 所以两个都用：颜色 + 字形双编码。
 */
enum class UIWerewolfFate(val short: String, val full: String) {
    WOLF_KILL("刀", "夜里被狼人刀了"),
    WITCH_POISON("毒", "被女巫毒死"),
    EXILE("票", "被投票放逐"),
    HUNTER_SHOOT("枪", "被猎人开枪带走"),
}

/** 一条记录在界面上的性质。不同性质用不同排版，不要都塞成一行字。 */
enum class UIWerewolfLineKind {
    /** 上帝播报：所有人都知道的事实。 */
    DIVINE,

    /** 公开发言。 */
    SPEECH,

    /** 投票表态。 */
    VOTE,

    /** 只有这个视角看得见的话（狼人夜聊、查验结果、用药）。 */
    PRIVATE,
}

/** 牌桌上的一条记录。 */
data class UIWerewolfLine(
    val index: Int,
    val kind: UIWerewolfLineKind,
    val seat: Int? = null,
    val speaker: String? = null,
    val avatarPath: String? = null,
    val text: String,
)

/** 轮到人类时要做的事。UI 按这个决定弹哪种输入面板。 */
enum class UIWerewolfTurnKind {
    /** 白天自由发言。 */
    SPEECH,

    /** 投票放逐。 */
    VOTE,

    /** 选一个座位（狼刀、查验、开枪）。 */
    SEAT,

    /** 女巫用药。 */
    WITCH,
}

data class UIWerewolfTarget(
    val seat: Int,
    val name: String,
    /** 已经出局的座位不能选，但仍列出来——不然「5 号怎么不见了」会让人以为界面出错。 */
    val selectable: Boolean = true,
)

data class UIWerewolfTurn(
    val kind: UIWerewolfTurnKind,
    /** 本人座位。 */
    val seat: Int,
    val title: String,
    val hint: String? = null,
    val targets: List<UIWerewolfTarget> = emptyList(),
    /** 能不能选择「不做」。投票弃票、猎人不开枪。 */
    val canAbstain: Boolean = false,
    /** 女巫：今晚的解药能不能用。 */
    val canHeal: Boolean = false,
    /** 女巫：毒药还在不在。 */
    val canPoison: Boolean = false,
    /** 女巫：今晚被刀的是谁（解药救的就是他）。 */
    val healTarget: String? = null,
)

/**
 * 对局进行中的一份快照。
 *
 * 牌桌要同时回答四个问题：我是谁、场上有谁、刚刚发生了什么、现在轮到谁。
 * 这四件事都在这里，界面不做任何推断——尤其是「轮到谁」，判断依据是 `phase` 加本人身份，
 * 界面不去猜提示词里写了什么。
 */
data class UIWerewolfLive(
    val day: Int,
    /** 例如「白天讨论」「狼人行动」。 */
    val phaseLabel: String,
    /** 夜晚要换暗色。不换页：换页会丢掉牌桌的连续感，也让人以为进了另一个地方。 */
    val night: Boolean,
    val running: Boolean,
    val finished: Boolean,
    /** 已结束时的胜负，「狼人胜」/「好人胜」。 */
    val winnerLabel: String?,
    /** 例如「第 2 天 · 发言 3/8」。还没开始时为 null。 */
    val progressLabel: String?,
    /** 当前阶段内部进度，0..1。并发阶段为 null（不知道中间态，就不假装知道）。 */
    val progressFraction: Float?,
    val seats: List<UIWerewolfSeatLive> = emptyList(),
    /** 记录流，时间正序，最后一条最新。可见性已按视角过滤，界面不再判断。 */
    val lines: List<UIWerewolfLine> = emptyList(),
    /** 你的座位。观战为 null。 */
    val selfSeat: Int? = null,
    /** 你的身份，例如「女巫」。观战时为 null。 */
    val selfRoleLabel: String? = null,
    /** 你的阵营，例如「好人」。 */
    val selfFactionLabel: String? = null,
    /** 纯观战：看的是上帝视角，别人的身份和狼人夜聊都可见。 */
    val spectating: Boolean = false,
    /** 轮到你时的操作面板。null 表示现在不归你。 */
    val turn: UIWerewolfTurn? = null,
    /** 出错信息（多为网络或 Key 问题），局面不会因此丢失。 */
    val error: String? = null,
)
