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
}

/**
 * 身份。
 *
 * 身份的**归属**（谁是什么身份）由 [WerewolfGame.players] 决定，这里只描述身份的静态属性：
 * 属于哪个阵营、是否神职、应该在第几个夜晚行动。规则判定不要散在 UI 或编排层。
 */
enum class WerewolfRole(val faction: Faction) {
    WEREWOLF(Faction.WEREWOLF),
    SEER(Faction.GOOD),
    WITCH(Faction.GOOD),
    HUNTER(Faction.GOOD),
    VILLAGER(Faction.GOOD),
    ;

    /** 神职：预言家、女巫、猎人。屠边规则里「神全灭」指的就是这一组。 */
    val isGod: Boolean
        get() = this == SEER || this == WITCH || this == HUNTER

    /** 中文名，直接用于上帝播报和提示词，避免多处各写一份。 */
    val displayName: String
        get() = when (this) {
            WEREWOLF -> "狼人"
            SEER -> "预言家"
            WITCH -> "女巫"
            HUNTER -> "猎人"
            VILLAGER -> "平民"
        }
}
