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
    /**
     * 这个座位背后跑的模型名，显示在名字下方。
     * 空串表示不显示（人类座位是 "human"，老存档没有这一段）。
     */
    val model: String = "",
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
    /**
     * 这个座位此刻持有警徽。
     *
     * 是公开信息，所以不需要 [roleLabel] 那种「观战才给」的保护。
     * 牌桌上必须一直显示：警长的一票值 1.5，不标出来，玩家会算不明白票型。
     */
    val sheriff: Boolean = false,
    /**
     * 这个座位是「你」的狼队友。
     *
     * 只在你本人是狼时才会有 true——那不是剧透，而是你本来就有的信息
     * （线下入夜睁眼，第一件事就是认队友）。不标出来，人类玩家只能靠夜聊猜，
     * 白天连「不能投自己人」都做不到。
     */
    val isWolfMate: Boolean = false,
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

    /**
     * 参与中的局看到的夜间出局：只知道「夜里死了」，不知道是刀还是毒。
     *
     * 刀口和用药是私有信息——AI 玩家拿到的 publicSituation 里只有座位号，
     * 人类玩家要是能从牌桌徽标上读出「毒」，就等于拿到了 AI 没有的情报。
     * 但也不能干脆不给标记：那会让人以为他还活着。观战和结束后显示真实死因。
     */
    NIGHT("夜", "夜里出局"),

    EXILE("票", "被投票放逐"),
    HUNTER_SHOOT("枪", "被开枪带走（猎人 / 狼王）"),

    /** 自爆：狼人自己结束的白天。字形用「爆」，和「票」区分得开。 */
    SELF_EXPLODE("爆", "狼人自爆，白天就此结束"),
}

/** 一条记录在界面上的性质。不同性质用不同排版，不要都塞成一行字。 */
enum class UIWerewolfLineKind {
    /** 上帝播报：所有人都知道的事实。 */
    DIVINE,

    /** 说出口的发言（白天发言、狼人夜聊）。 */
    SPEECH,

    /**
     * 内心活动：投票的理由、夜里查验用药的念头。
     *
     * 和 [SPEECH] 在排版上必须明显不同——这些字**没有人听见**。写成发言的样式，
     * 用户会以为「他刚才在桌上说了这句话」，而实际上牌桌上只有他自己知道。
     */
    INNER,

    /** 只有这个视角看得见的话（狼人夜聊）。 */
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
    /** 内心活动的类型标签（「投票」「查验」）。[UIWerewolfLineKind.INNER] 才有。 */
    val innerLabel: String? = null,
    /** 只有当前视角看得见。内心活动在参与局里恒为 true。 */
    val secret: Boolean = false,
)

/**
 * 某位 AI 正在逐字说、但还没说完的一段话。
 *
 * 它**不是** [UIWerewolfLine]：记录流里的条目是「已经发生的事」，这一条是「正在发生的事」。
 * 混进去会让列表长度不断变化（半句话每次都要改文本），滚动位置会跟着抖；
 * 而且它会污染「总共说了多少条」这个判断（朗读闸门按 index 推进）。
 *
 * [isSelf] 为 true 时说明是「我」在说——那不可能是模型在打字，而是界面在等自己输入，
 * 这时不该显示「正在组织语言」，所以映射时就置空。
 *
 * 不带思考过程：要显示哪一段由 [UIWerewolfLive.showThinking] 决定，
 * 映射那一层已经把不该给的部分清成空串了。
 */
data class UIWerewolfStreaming(
    val seat: Int,
    val speaker: String?,
    val avatarPath: String? = null,
    /** 已经生成的正文。为空表示还在思考，一个字都没说。 */
    val text: String = "",
    /**
     * 已经生成的思考过程。
     *
     * **只有有权限的人拿得到**：参与局看到别人的思考等于读心，所以默认清成空串；
     * 观战、已结束、以及参与局里自己打开了「思考」开关时才有
     * （见 `WerewolfLiveMapping.toLive` 的 `showThinking`）。
     */
    val reasoning: String = "",
    /**
     * 这一步是决策还是发言。气泡秒表按它选时限，不靠 text 是不是空来猜——
     * 发言一开始也在思考、还没吐字，text 同样是空的。
     */
    val isDecision: Boolean = false,
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

    /** 警长选发言方向（顺时针 / 逆时针），两个按钮，没有目标可选。 */
    DIRECTION,
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
    /** 能不能选择「不做」。投票弃票、猎人不开枪、守卫空守。 */
    val canAbstain: Boolean = false,
    /**
     * 「不做」那个按钮上的字。
     *
     * 必须逐阶段给：同一个 `SEAT` 面板，猎人写「不开枪」、守卫写「这晚不守」，
     * 共用一个写死的文案会让守卫以为自己要开枪。
     */
    val abstainLabel: String? = null,
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
    /**
     * 顶栏标题下面那一行，例如「发言 3/8 · 5 号」。还没开始时为 null。
     *
     * 不带天数：标题本身就在报「第 3 天」，副标题再写一遍就是重复。
     */
    val stageLabel: String?,
    val seats: List<UIWerewolfSeatLive> = emptyList(),
    /** 记录流，时间正序，最后一条最新。可见性已按视角过滤，界面不再判断。 */
    val lines: List<UIWerewolfLine> = emptyList(),
    /**
     * 此刻正在生成的一段话。null 表示没人正在开口。
     *
     * 放在 [lines] 之外：它是「正在发生」而不是「已经发生」，两者排版、滚动、
     * 朗读的处理都不一样。可见性在这里已经过滤过，隐私阶段不会漏出来。
     */
    val streaming: UIWerewolfStreaming? = null,
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
    /** 自动朗读别人发言是否开启。 */
    val autoSpeak: Boolean = true,
    /** 系统中文语音是否可用。false 时开关点了也不出声，界面要说明原因。 */
    val speechReady: Boolean = true,
    /**
     * 思考气泡现在给不给看。
     *
     * 观战与已结束恒为 true——那个视角本来就能翻思考（复盘里也摊着）；
     * 参与局默认 false，跟顶栏那个「思考」开关走。打开它等于读心，
     * 所以界面要同时在牌桌上挂一句提醒，别让人忘了自己在作弊。
     */
    val showThinking: Boolean = false,
    /**
     * 喇叭手里还有没念完的行（含正在念的这一句）。
     *
     * 界面靠它压住「下一位正在生成」的气泡：念 A 的时候进度已经推到 A，看起来追平了，
     * 但 A 还在响。这时把 B 的打字打出来，用户看到的是「B 的对话框冒出来又消失」。
     */
    val speechBusy: Boolean = false,
    /**
     * 非 null 表示这张牌桌在放**复盘演出**：没有引擎、没有轮到我，
     * 底下那条是播放条而不是输入区。null = 正常对局。
     */
    val replay: UIWerewolfReplay? = null,
    /**
     * 刚刚有一步超出思考时限了，已经在牌桌上提示过一次。
     *
     * 不作为常驻信息：超时是偶发事件，写成一行常驻状态会让牌桌越来越挤；
     * 界面显示几秒就收掉，真正要长期看的是对局设置里那份「谁最慢、超了几次」。
     */
    val timeoutNotice: String? = null,
    /**
     * 上面那条提示的发生时刻。
     *
     * 单独一个字段是为了给界面当 `LaunchedEffect` 的 key：同一个座位连着超时两次时，
     * 文本可能一模一样，只靠文本变化是触发不了第二次提示的。
     */
    val timeoutAt: Long = 0L,
    /**
     * 这一局给每一段的上限（秒），0 表示不设限。
     *
     * 气泡上要显示「12s / 60s」——只看「已经想了 12 秒」是不够的，
     * 用户不知道这算长还是短，也就不知道要不要去调一档。
     *
     * 口径跟界面走：**气泡里是思考、发言列表里是发言**。
     * 还没吐字（决策步全程、发言步的开头）按 [thinkingDecisionSeconds] 掐；
     * 正文开始往外流，按 [thinkingSpeechSeconds] 从吐字那一刻重新起算。
     */
    val thinkingDecisionSeconds: Int = 0,
    val thinkingSpeechSeconds: Int = 0,
)
