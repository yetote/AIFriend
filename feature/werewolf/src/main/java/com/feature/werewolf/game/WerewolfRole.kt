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

    /**
     * 狼王。夜里和普通狼人一样商议、下刀，白天被投票放逐时可以开枪带走一个人。
     *
     * 和猎人同样只能被刀 / 被票触发，被女巫毒死不能开枪；被猎人带走也不引发连锁开枪。
     * 他那一枪是在狼队已经全灭之后打的，所以判胜负必须等枪响完——
     * 最后一神如果被打掉，胜负会当场翻盘。
     */
    WEREWOLF_KING(Faction.WEREWOLF),
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

    /**
     * 熊。夜里不行动，天亮时如果左右相邻座位里有存活狼人，上帝会公开宣布「熊在咆哮」。
     *
     * 规则上只看**活着的邻居**：夜里有人出局后，咆哮可能因为邻居死掉而停止，
     * 这本身就是一条能用的信息。熊死了就不再咆哮——沉默不等于「两边都是好人」。
     */
    BEAR(Faction.GOOD),

    VILLAGER(Faction.GOOD),
    ;

    /** 狼人阵营。狼王也算狼，不能只判 WEREWOLF 一个枚举值。 */
    val isWerewolf: Boolean get() = faction == Faction.WEREWOLF

    /** 死亡时能否开枪。猎人和狼王共用同一套开枪阶段，但触发条件完全相同。 */
    val canShootOnDeath: Boolean get() = this == HUNTER || this == WEREWOLF_KING

    /** 神职：预言家、女巫、猎人、守卫、白痴、熊。屠边规则里「神全灭」指的就是这一组。 */
    val isGod: Boolean
        get() = this == SEER || this == WITCH || this == HUNTER || this == GUARD ||
            this == IDIOT || this == BEAR

    /** 中文名，直接用于上帝播报和提示词，避免多处各写一份。 */
    val displayName: String
        get() = when (this) {
            WEREWOLF -> "狼人"
            WEREWOLF_KING -> "狼王"
            SEER -> "预言家"
            WITCH -> "女巫"
            HUNTER -> "猎人"
            GUARD -> "守卫"
            IDIOT -> "白痴"
            BEAR -> "熊"
            VILLAGER -> "平民"
        }

    /** 板子列表里的单字简称。12 人板的身份构成用全名一行排不下。 */
    val shortName: String
        get() = when (this) {
            WEREWOLF -> "狼"
            WEREWOLF_KING -> "王"
            SEER -> "预"
            WITCH -> "女"
            HUNTER -> "猎"
            GUARD -> "守"
            IDIOT -> "白"
            BEAR -> "熊"
            VILLAGER -> "民"
        }
}
