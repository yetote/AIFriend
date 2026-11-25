package com.feature.werewolf.game

/** 狼人胜利条件。 */
enum class WinRule {
    /**
     * 屠边：杀光所有神职，或杀光所有平民，狼人即获胜。线下主流规则。
     *
     * **屠边之前先过绑票这一关**：两种规则都过。票权压不过对方时票已经投不出去了，
     * 再演一轮白天也是同一个结局。
     */
    SLAY_SIDE,

    /**
     * 屠城：狼人票权 >= 好人票权时狼人获胜。更短平快，适合 AI 局。
     *
     * 比的是**票权**不是人头——警长一票算 1.5 票，2 狼 2 好且警长是好人时是 4 比 5，
     * 按人头算会在这里提前判负。
     */
    SLAY_ALL,
}

/**
 * 一局游戏的规则配置。
 *
 * 所有可调参数集中在这里，[WerewolfGame] 内部不出现魔法数字。
 * 身份构成用 Map 表达而不是固定枚举，是为了让 6 人局、9 人局、12 人局共用同一套引擎，
 * 加一个新身份（守卫、白痴）也不需要新增一套规则代码。
 */
data class WerewolfConfig(
    val composition: Map<WerewolfRole, Int> = Standard8,
    val winRule: WinRule = WinRule.SLAY_SIDE,
    /**
     * 白天投票最多几轮。默认 1，也就是**平票即无人出局**。
     *
     * 线下能「平票再投一轮」是因为有警长主持 PK：警长在场上、且他指定了 PK 名单，
     * 大家才重投。所以这条和 [hasSheriff] 是配套的——开警长的板子传 2，
     * 没警长的板子保持 1。传 2 而没有警长也能跑，只是规则上讲不通（谁来追加这一轮）。
     *
     * 这条不只是流程细节——正因为票投不出去，2 狼 2 好人时狼人才能靠绑票直接赢
     * （见 [WerewolfGame] 的胜负判定）。
     */
    val maxVoteRounds: Int = 1,
    /**
     * 女巫能否在第一夜自救。
     *
     * 线下常见规则是「解药不能自救，第一夜除外」，所以这里只控制第一夜；
     * 第二夜之后一律禁止，不受这个开关影响。要让女巫全程可自救，需要改规则而不是开开关。
     */
    val witchCanSelfHealOnFirstNight: Boolean = true,
    /**
     * 是否竞选警长。
     *
     * 开了之后天亮的第一件事是上警、竞选、警下投票（被自爆打断就顺延到下一天）；
     * 警长票权 1.5，并且决定当天的发言起点。
     * 关着的时候整套警长流程不存在（[Phase.DAY_SHERIFF_SIGNUP] 等三个阶段不会出现），
     * 所以老档、老配置读出来行为一个字都不变。
     */
    val hasSheriff: Boolean = false,
) {
    init {
        require(composition.isNotEmpty()) { "身份构成不能为空" }
        composition.forEach { (role, count) ->
            require(count >= 0) { "${role.displayName} 数量不能为负：$count" }
        }
        require(composition.filterKeys { it.isWerewolf }.values.sum() >= 1) { "至少要有一名狼人" }
        require((composition[WerewolfRole.VILLAGER] ?: 0) + composition.filterKeys { it.isGod }.values.sum() >= 1) {
            "至少要有一名好人"
        }
        // 引擎按「场上有唯一一个行动者」实现夜晚阶段：两个预言家同场，查验结果该给谁说不清，
        // 两个守卫的守护怎么叠加也说不清。所以这一组身份一律最多 1 名。
        listOf(
            WerewolfRole.WEREWOLF_KING,
            WerewolfRole.SEER,
            WerewolfRole.WITCH,
            WerewolfRole.HUNTER,
            WerewolfRole.GUARD,
            WerewolfRole.IDIOT,
            WerewolfRole.BEAR,
        ).forEach { role ->
            require((composition[role] ?: 0) <= 1) { "${role.displayName} 最多 1 名" }
        }
        require(maxVoteRounds >= 1) { "投票轮次至少为 1" }
    }

    val playerCount: Int get() = composition.values.sum()

    /** 场上有没有这个身份（含已出局的人）。用于「开局时这个板子有没有守卫」这类判断。 */
    fun has(role: WerewolfRole): Boolean = (composition[role] ?: 0) > 0

    companion object {
        /**
         * 6 人快局：2 狼 + 预言家 / 女巫 + 2 民。
         *
         * 两神两民，屠边意味着狼人只要打穿任意一边就赢，好人一次都错不得。
         * 短，但比 8 人局紧张——适合先试一局看看模型的水平。
         */
        val Quick6: Map<WerewolfRole, Int> = mapOf(
            WerewolfRole.WEREWOLF to 2,
            WerewolfRole.SEER to 1,
            WerewolfRole.WITCH to 1,
            WerewolfRole.VILLAGER to 2,
        )

        /** 8 人局：2 狼 + 预言家 / 女巫 / 猎人 + 3 民。 */
        val Standard8: Map<WerewolfRole, Int> = mapOf(
            WerewolfRole.WEREWOLF to 2,
            WerewolfRole.SEER to 1,
            WerewolfRole.WITCH to 1,
            WerewolfRole.HUNTER to 1,
            WerewolfRole.VILLAGER to 3,
        )

        /** 9 人局：3 狼 + 预女猎 + 3 民。三狼在场，投票随时可能被狼队带走。 */
        val Standard9: Map<WerewolfRole, Int> = mapOf(
            WerewolfRole.WEREWOLF to 3,
            WerewolfRole.SEER to 1,
            WerewolfRole.WITCH to 1,
            WerewolfRole.HUNTER to 1,
            WerewolfRole.VILLAGER to 3,
        )

        /** 10 人守卫局：3 狼 + 预女猎守 + 3 民。四神在场，好人第一次有了「挡刀」的余量。 */
        val Guard10: Map<WerewolfRole, Int> = mapOf(
            WerewolfRole.WEREWOLF to 3,
            WerewolfRole.SEER to 1,
            WerewolfRole.WITCH to 1,
            WerewolfRole.HUNTER to 1,
            WerewolfRole.GUARD to 1,
            WerewolfRole.VILLAGER to 3,
        )

        /** 12 人守卫局：4 狼 + 预女猎守 + 4 民。标准线下局之一。 */
        val Guard12: Map<WerewolfRole, Int> = mapOf(
            WerewolfRole.WEREWOLF to 4,
            WerewolfRole.SEER to 1,
            WerewolfRole.WITCH to 1,
            WerewolfRole.HUNTER to 1,
            WerewolfRole.GUARD to 1,
            WerewolfRole.VILLAGER to 4,
        )

        /**
         * 12 人白痴局：4 狼 + 预女猎白 + 4 民。
         *
         * 白痴替好人挡一次放逐：票错了不死，只是从此没有票。和守卫局的区别是——
         * 守卫防夜里的刀，白痴防白天的错，两种容错的方向不一样。
         */
        val Fool12: Map<WerewolfRole, Int> = mapOf(
            WerewolfRole.WEREWOLF to 4,
            WerewolfRole.SEER to 1,
            WerewolfRole.WITCH to 1,
            WerewolfRole.HUNTER to 1,
            WerewolfRole.IDIOT to 1,
            WerewolfRole.VILLAGER to 4,
        )

        /**
         * 10 人狼王守卫局：狼王 + 2 狼 + 预女猎守 + 3 民。
         *
         * 狼王让狼队在「已经输了」的时候还有一次翻盘机会：被票出去的那一枪
         * 如果带走最后一神，胜负会当场倒过来。守卫同时给好人留一层容错，
         * 不然狼王那一枪太容易一击定局。
         */
        val King10: Map<WerewolfRole, Int> = mapOf(
            WerewolfRole.WEREWOLF_KING to 1,
            WerewolfRole.WEREWOLF to 2,
            WerewolfRole.SEER to 1,
            WerewolfRole.WITCH to 1,
            WerewolfRole.HUNTER to 1,
            WerewolfRole.GUARD to 1,
            WerewolfRole.VILLAGER to 3,
        )

        /**
         * 12 人狼王守卫局：狼王 + 3 狼 + 预女猎守 + 4 民（警长）。
         *
         * 线下最经典的 12 人竞技板子之一：既有守卫挡刀与同守同救博弈，又有狼王的翻盘枪，
         * 警徽争夺与白天轮次压力都拉满。
         */
        val King12: Map<WerewolfRole, Int> = mapOf(
            WerewolfRole.WEREWOLF_KING to 1,
            WerewolfRole.WEREWOLF to 3,
            WerewolfRole.SEER to 1,
            WerewolfRole.WITCH to 1,
            WerewolfRole.HUNTER to 1,
            WerewolfRole.GUARD to 1,
            WerewolfRole.VILLAGER to 4,
        )

        /**
         * 12 人熊局：4 狼 + 预女猎白熊 + 3 民。
         *
         * 熊不升级也不下刀，它只提供一条稳定公开的线索：左右邻座有狼就咆哮。
         * 和白痴同桌时，好人多了一层「错了也不立刻输」的余量，适合慢慢盘逻辑。
         */
        val Bear12: Map<WerewolfRole, Int> = mapOf(
            WerewolfRole.WEREWOLF to 4,
            WerewolfRole.SEER to 1,
            WerewolfRole.WITCH to 1,
            WerewolfRole.HUNTER to 1,
            WerewolfRole.IDIOT to 1,
            WerewolfRole.BEAR to 1,
            WerewolfRole.VILLAGER to 3,
        )
    }
}
