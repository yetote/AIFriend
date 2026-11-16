package com.feature.werewolf.game

/**
 * 阵营。
 *
 * 只用两分法：[Faction.WEREWOLF] 与 [Faction.GOOD]。狼人杀的所有胜负判定都能归到这两个集合上，
 * 不需要为「神职」再建一个阵营——神职只是好人里的一个子集（见 [WerewolfRole.isGod]）。
 */
enum class Faction {
    WEREWOLF,
    GOOD,
    ;

    /** 中文名。上帝播报和提示词都要用，写在一处避免各写一份。 */
    val displayName: String
        get() = when (this) {
            WEREWOLF -> "狼人"
            GOOD -> "好人"
        }
}

/**
 * 身份。
 *
 * 身份的**归属**（谁是什么身份）由 [WerewolfGame.players] 决定，这里只描述身份的静态属性：
 * 属于哪个阵营、是否神职、应该在第几个夜晚行动。规则判定不要散在 UI 或编排层。
 *
 * 加一个新身份要动的地方只有四处：这里、它自己的阶段（如果夜里行动）、
 * 提示词里的私有信息、以及复盘和牌桌上怎么称呼它。规则本身写在 [WerewolfGame] 里。
 */
enum class WerewolfRole(val faction: Faction) {
    WEREWOLF(Faction.WEREWOLF),
    SEER(Faction.GOOD),
    WITCH(Faction.GOOD),
    HUNTER(Faction.GOOD),

    /**
     * 守卫。每夜先于狼人行动，守一个人，那个人当夜不会被狼刀死。
     *
     * 「先于狼人」是规则的一部分：守卫看不见刀口，只能盲守。让他后行动，
     * 守护就变成了「指定一个人免死」，强度完全不同。
     */
    GUARD(Faction.GOOD),

    /**
     * 白痴。被投票放逐时翻牌，不死，但从此失去投票权（也不能再被投）。
     *
     * 夜里被刀、被毒照常会死——白痴只是「票不死」，不是无敌。
     */
    IDIOT(Faction.GOOD),

    VILLAGER(Faction.GOOD),
    ;

    /** 神职：预言家、女巫、猎人、守卫、白痴。屠边规则里「神全灭」指的就是这一组。 */
    val isGod: Boolean
        get() = this == SEER || this == WITCH || this == HUNTER || this == GUARD || this == IDIOT

    /** 中文名，直接用于上帝播报和提示词，避免多处各写一份。 */
    val displayName: String
        get() = when (this) {
            WEREWOLF -> "狼人"
            SEER -> "预言家"
            WITCH -> "女巫"
            HUNTER -> "猎人"
            GUARD -> "守卫"
            IDIOT -> "白痴"
            VILLAGER -> "平民"
        }

    /** 板子列表里的单字简称。12 人板的身份构成用全名一行排不下。 */
    val shortName: String
        get() = when (this) {
            WEREWOLF -> "狼"
            SEER -> "预"
            WITCH -> "女"
            HUNTER -> "猎"
            GUARD -> "守"
            IDIOT -> "白"
            VILLAGER -> "民"
        }
}
