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
    /**
     * 这个模型背后的对话接口有没有内置联网搜索。
     *
     * 这是**静态能力**，不需要发请求就能知道。搜索式下要在选人时就标出来：
     * 让用户选完人、等自检跑完、看到红字才知道自家模型不会联网，
     * 等于让人白等一趟。
     */
    val supportsWebSearch: Boolean = false,
)

/**
 * 续档连不上、让用户换人再打时的一条面板数据。
 *
 * 一个座位换一次：[seat] 是牌桌号码，[candidates] 是能顶上来的好友。
 * 这里不带 feature 层的类型，映射在 route 层做。
 */
data class UIWerewolfRecastSeat(
    val seat: Int,
    val oldName: String,
    val oldModel: String,
    /**
     * 这个座位的好友**已经不在了**（被删了 / 换设备没同步）。
     *
     * 和「连不上」是两种坏法，界面得说不一样：
     * - 不在了：只能换人，换 Key 也没用；
     * - 连不上：Key 过期、模型下线，用户可能想先换个 Key 再试。
     */
    val gone: Boolean = false,
    val candidates: List<UIWerewolfRecastCandidate>,
)

data class UIWerewolfRecastCandidate(
    val id: String,
    val name: String,
    val model: String,
    /** 探活是绿的：换上去大概率直接能打通，界面给个「推荐」。 */
    val reachable: Boolean,
)

/** 一局要换人的完整面板。 */
data class UIWerewolfRecast(
    val reason: String,
    val seats: List<UIWerewolfRecastSeat>,
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
    /**
     * 存档停在夜里还是白天。
     *
     * 牌桌顶栏一直是「第 1 夜 / 第 1 天」分开报的，列表原来不分、一律写「第 1 天」，
     * 于是停在「狼人商议」的局会读成「第一天 · 夜里」——同一个时刻在牌桌和列表里
     * 有两个人叫法。单位记在这里，列表跟着牌桌走。
     */
    val night: Boolean = false,
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

    /**
     * 「第 3 天」「第 2 夜」。
     *
     * 夜和天**共用序号**：第 1 夜属于第 1 天，天亮之后还是第 1 天，第 2 夜才 +1
     * （引擎在 `startNextNight` 里加）。所以单位要跟着 [night] 走，不能一律写「天」——
     * 夜里写「第 1 天」会让人以为是白天，写「第 1 夜」又和天数对不上。
     */
    val dayLabel: String
        get() = "第 $day ${if (night) "夜" else "天"}"
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

/**
 * 「指定身份」选单里的一个选项。
 *
 * [key] 是引擎 [WerewolfRole][com.feature.werewolf.game.WerewolfRole] 的名字，开局时原样
 * `valueOf` 回去；[remaining] 是**扣掉已指定之后**还剩几个——选单里要让人一眼看出
 * 「狼只剩一个名额了」，不然会选出一张构成对不上的牌、点开局才报错。
 */
data class UIWerewolfRoleOption(
    val key: String,
    val label: String,
    /** 板子构成里这个身份的总数。 */
    val total: Int,
    /** 总数减去已经指定出去的数量，可以为 0（选单里灰掉）。 */
    val remaining: Int,
)

/**
 * 提示词微调页的状态。
 *
 * 分两段：**全局**（所有板子都生效）和**当前板子**（只对这一张板子生效）。
 * 用户微调的多半是「这张板子怎么打」——12 人守卫局和 6 人快局该用不同的打法。
 */
data class UIWerewolfPromptState(
    /** 全局补充设定。 */
    val global: String = "",
    /** 当前板子的补充设定。 */
    val board: String = "",
    /** 当前板子的名字，页面上要写清楚这段是给谁用的。 */
    val boardTitle: String = "",
    /** 当前板子的稳定标识，保存时用它当 key。 */
    val boardKey: String = "",
    /** 有没有已经存过的东西（决定「恢复默认」按钮亮不亮）。 */
    val hasGlobal: Boolean = false,
    val hasBoard: Boolean = false,
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
    /**
     * 这次失败是不是「这家没有联网搜索」。
     *
     * 界面上要分开说：连不上是 Key 的问题，没有搜索是厂商能力的问题——
     * 后者让用户去重试一把完全没问题的 Key，是白折腾。
     */
    val searchUnsupported: Boolean = false,
)

/** 裁判 / GM 的快捷候选项（来自已建好友正在使用的模型）。 */
data class UIWerewolfGmFriendOption(
    val brand: String,
    val model: String,
    val friendNames: String,
)

/** 裁判 / GM 可选的已配厂商 / 本地服务（如 Ollama / 免费中转 / 官方厂商）。 */
data class UIWerewolfGmProvider(
    val brand: String,
    val description: String,
    val models: List<String>,
    val loading: Boolean = false,
)
