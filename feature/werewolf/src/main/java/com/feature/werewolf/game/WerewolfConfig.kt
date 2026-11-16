package com.feature.werewolf.game

/** 狼人胜利条件。 */
enum class WinRule {
    /** 屠边：杀光所有神职，或杀光所有平民，狼人即获胜。线下主流规则。 */
    SLAY_SIDE,

    /** 屠城：存活狼人数 >= 存活好人数时狼人获胜。更短平快，适合 AI 局。 */
    SLAY_ALL,
}

/**
 * 一局游戏的规则配置。
 *
 * 所有可调参数集中在这里，[WerewolfGame] 内部不出现魔法数字。
 * 身份构成用 Map 表达而不是固定枚举，是为了让 6 人局、9 人局、12 人局共用同一套引擎。
 */
data class WerewolfConfig(
    val composition: Map<WerewolfRole, Int> = Standard8,
    val winRule: WinRule = WinRule.SLAY_SIDE,
    /** 白天投票最多几轮。2 表示平票后加投一轮，再加投仍平票则无人出局。 */
    val maxVoteRounds: Int = 2,
    /**
     * 女巫能否在第一夜自救。
     *
     * 线下常见规则是「解药不能自救，第一夜除外」，所以这里只控制第一夜；
     * 第二夜之后一律禁止，不受这个开关影响。要让女巫全程可自救，需要改规则而不是开开关。
     */
    val witchCanSelfHealOnFirstNight: Boolean = true,
) {
    init {
        require(composition.isNotEmpty()) { "身份构成不能为空" }
        composition.forEach { (role, count) ->
            require(count >= 0) { "${role.displayName} 数量不能为负：$count" }
        }
        require((composition[WerewolfRole.WEREWOLF] ?: 0) >= 1) { "至少要有一名狼人" }
        require((composition[WerewolfRole.VILLAGER] ?: 0) + composition.filterKeys { it.isGod }.values.sum() >= 1) {
            "至少要有一名好人"
        }
        // 引擎按「场上有唯一一个行动者」实现夜晚阶段，多神职同场会语义不明，直接挡住。
        listOf(WerewolfRole.SEER, WerewolfRole.WITCH, WerewolfRole.HUNTER).forEach { role ->
            require((composition[role] ?: 0) <= 1) { "${role.displayName} 最多 1 名" }
        }
        require(maxVoteRounds >= 1) { "投票轮次至少为 1" }
    }

    val playerCount: Int get() = composition.values.sum()

    companion object {
        /** 8 人局：2 狼 + 预言家 / 女巫 / 猎人 + 3 民。 */
        val Standard8: Map<WerewolfRole, Int> = mapOf(
            WerewolfRole.WEREWOLF to 2,
            WerewolfRole.SEER to 1,
            WerewolfRole.WITCH to 1,
            WerewolfRole.HUNTER to 1,
            WerewolfRole.VILLAGER to 3,
        )

        /** 9 人局：3 狼 + 预女猎 + 3 民。狼人需要多数决，是本引擎最典型的板子。 */
        val Standard9: Map<WerewolfRole, Int> = mapOf(
            WerewolfRole.WEREWOLF to 3,
            WerewolfRole.SEER to 1,
            WerewolfRole.WITCH to 1,
            WerewolfRole.HUNTER to 1,
            WerewolfRole.VILLAGER to 3,
        )
    }
}
