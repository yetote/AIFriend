package com.ui.werewolf.data

/**
 * 复盘页的全部内容。**只在分出胜负之后**才会构建。
 *
 * 复盘和牌桌的区别在信息性质：牌桌上「谁能看到什么」是核心，复盘里一切都已经公开。
 * 所以这个模型里没有 audience、没有「仅你可见」——它描述的是**已经结束的过去**，
 * 而不是**正在进行的博弈**。把两者塞进同一个模型，迟早会有一处该藏的信息没藏住。
 */
data class UIWerewolfReview(
    /** 「狼人胜」/「好人胜」。 */
    val winnerLabel: String,
    /** 狼人赢还是好人赢，决定横幅配色。 */
    val wolfWon: Boolean,
    /** 为什么会结束，例如「狼人全部出局」。光有胜负，玩家不知道最后一刀是谁的功劳。 */
    val reason: String,
    /** 「9 人 · 4 天 · 5 人出局」。 */
    val facts: String,
    /** 你在这局里是谁。观战为 null。 */
    val selfLine: String?,
    /** 按阵营分好组的底牌。 */
    val groups: List<UIWerewolfRoleGroup> = emptyList(),
    /** 逐日票型，按天正序。 */
    val days: List<UIWerewolfDayReport> = emptyList(),
    /** 逐夜行动，按夜正序。 */
    val nights: List<UIWerewolfNightReport> = emptyList(),
    /**
     * 逐夜明细：那一夜**每个人做了什么、为什么**。
     *
     * [nights] 给的是结果（「狼刀 6 号」「平安夜」），这里给的是过程：
     * 狼队为什么刀他、守卫守了谁、预言家验出了什么、女巫用没用药。
     * 只看结果的复盘等于一张死亡表，看不出这一局输在哪一步。
     */
    val nightDetails: List<UIWerewolfNightDetail> = emptyList(),
    /**
     * 逐日对话，按天正序。
     *
     * 这是唯一按「谁说了什么」组织的部分：票型和夜间行动回答「结果怎么来的」，
     * 对话回答「他当时是怎么说的」。旧档没有逐条消息，这里为空。
     */
    val dialogues: List<UIWerewolfDialogueDay> = emptyList(),
    /** 预言家查验记录。 */
    val checks: List<UIWerewolfCheckReport> = emptyList(),
    /**
     * 数据不全。
     *
     * 只有一种来源：这局是旧版本存档续下来的，那时候还没记夜间行动和票型历史。
     * 与其显示成「什么都没发生」，不如明说这一段缺了——编一个看起来完整的复盘更糟。
     */
    val incomplete: Boolean = false,
)

/** 一组底牌的归属。配色和文案都以它为准，不去匹配标题字符串——标题随时会改。 */
enum class UIWerewolfGroupKind {
    WOLF,
    GOD,
    VILLAGER,
}

/** 一组底牌。「狼人」和「神职」「平民」分开，是因为复盘第一眼要数的是「几个狼」。 */
data class UIWerewolfRoleGroup(
    val kind: UIWerewolfGroupKind,
    val title: String,
    val members: List<UIWerewolfRevealedSeat> = emptyList(),
)

/** 一位玩家的最终归宿。 */
data class UIWerewolfRevealedSeat(
    val seat: Int,
    val name: String,
    val avatarPath: String? = null,
    val roleLabel: String,
    val isSelf: Boolean = false,
    /** 活到最后。 */
    val alive: Boolean = true,
    /** 「第 2 天被票出」。活着的时候为 null。 */
    val fate: String? = null,
    /** 整局收了多少票。0 不显示——写一个「0 票」反而像在挖苦人。 */
    val votes: Int = 0,
)

/** 一天的投票。 */
data class UIWerewolfDayReport(
    val day: Int,
    /** 「放逐 5 号（4 票）」/「平票，无人出局」/「全员弃票」。 */
    val result: String,
    /** 这天真的有人出局。没有的话结果行不标红。 */
    val exiled: Boolean,
    /** 加投轮数。0 表示一天只投了一轮。 */
    val extraRounds: Int = 0,
    val ballots: List<UIWerewolfBallotLine> = emptyList(),
)

/** 一张票。[target] 为 null 是弃票。 */
data class UIWerewolfBallotLine(
    val voter: Int,
    val target: Int?,
    /** 这一票投给了当天被放逐的人。复盘时最该被看见的就是这些票。 */
    val key: Boolean = false,
)

/** 一夜的行动。 */
data class UIWerewolfNightReport(
    val day: Int,
    /** 「狼刀 6 号」/「狼人空刀」。 */
    val wolfLine: String,
    /** 「守卫守 6 号」。没有守卫、守卫出局或空守时为 null。 */
    val guardLine: String? = null,
    /** 「女巫用解药救下」/「女巫毒 7 号」。没用则 null。 */
    val witchLine: String? = null,
    /** 「6 号出局」/「平安夜」。 */
    val deathLine: String,
)

/**
 * 一夜的明细。
 *
 * 摘要（[wolfPlan] 等）是这一夜发生的事，一句一句；[lines] 是原话——
 * 狼队怎么商量的、守卫为什么守那个人、预言家为什么验他。
 *
 * 摘要直接取原话的第一句，不重新概括：复盘最怕「换个说法」，
 * 概括会把「他刀 2 号是因为 2 号昨晚发言少」压成「刀 2 号」，理由就丢了。
 */
data class UIWerewolfNightDetail(
    val day: Int,
    /** 「我们定刀 6 号」。没有狼队参与（没有存活狼）时为 null。 */
    val wolfPlan: String? = null,
    /** 「我守 5 号」。 */
    val guardPlan: String? = null,
    /** 「查验 2 号」+ 验人结论。 */
    val seerPlan: String? = null,
    /** 「我用解药」。 */
    val witchPlan: String? = null,
    /** 这一夜的原话：夜聊与各人的夜间行动理由。 */
    val lines: List<UIWerewolfSpeechLine> = emptyList(),
)

/** 一次查验。复盘时它从私有信息变成公开信息，这正是复盘最有意思的部分。 */
data class UIWerewolfCheckReport(
    val day: Int,
    val target: Int,
    val isWolf: Boolean,
)

/**
 * 一天的对话。
 *
 * 这一天从**当夜**开始算：第 1 天包含第一夜的狼人商议、天亮的播报、白天的发言和投票。
 * 和复盘里「第 N 天」的其他说法保持一致，不另起一套编号。
 */
data class UIWerewolfDialogueDay(
    val day: Int,
    /** 按发生顺序排好的全部消息，公开与私密混在一起，靠 [UIWerewolfSpeechLine.secret] 区分。 */
    val lines: List<UIWerewolfSpeechLine> = emptyList(),
) {
    /** 全员可见的部分。复盘默认只给这些。 */
    val open: List<UIWerewolfSpeechLine> get() = lines.filterNot { it.secret }

    /** 这一天有没有当时只有少数人能看的内容（狼人夜聊、查验、用药）。 */
    val hasSecret: Boolean get() = lines.any { it.secret }
}

/**
 * 对话里的一句话。
 *
 * [secret] 不是「敏感」而是**当时的可见性**：狼人夜聊、神职夜间行动在局中只有个别人
 * 看得到，复盘时可以公开，但需要玩家主动打开——一进来就把那一夜的密谋摊开，
 * 「原来他们那晚在商量这个」这个发现过程就没了。
 */
data class UIWerewolfSpeechLine(
    /** 发言座位。上帝播报没有座位，为 null。 */
    val seat: Int?,
    val speaker: String,
    val avatarPath: String? = null,
    /** 「发言」「投票」「狼人夜聊」「查验」「播报」——这句话的性质。 */
    val label: String,
    val text: String,
    val secret: Boolean = false,
    /**
     * 发言者的终局身份，例如「狼人」「预言家」。上帝播报为 null。
     *
     * 局中这是秘密，复盘里已经公开（底牌分组就在页面上方）。写在这里是因为
     * 读对话时最常问的一句是「这人当时是什么身份」——没有它就得往上翻底牌区
     * 一个座位一个座位地对，读三句就要来回滚一次。
     *
     * 存**文案 + 分组**而不是原始枚举：ui 层不该认识规则层的角色类型，
     * 换个板子加个新身份也不用回来改这里。
     */
    val roleLabel: String? = null,
    /** 身份所属分组，只用来上色。null 与 [roleLabel] 为 null 同义。 */
    val roleKind: UIWerewolfGroupKind? = null,
    /**
     * 产生这句话时模型的思考过程。
     *
     * 对局里一律不显示，复盘是它唯一的舞台——「他当时是怎么想的」是复盘里
     * 最有看头的部分，尤其是发言和推理对不上号的时候。
     */
    val reasoning: String = "",
)
