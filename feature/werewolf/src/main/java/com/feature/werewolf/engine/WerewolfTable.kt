package com.feature.werewolf.engine

import com.core.ai.entity.IAI
import com.core.ai.model.TokenUsage
import com.feature.werewolf.game.Ballot
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfGame

/**
 * 一位玩家背后的模型。
 *
 * [persona] 只放「性格与说话风格」（对应 `Friend.prompt`），不要放整段 `Friend.systemPrompt`——
 * 那里有「你是用户的 AI 好友」这类措辞，会让模型以为自己还在陪用户聊天。
 * 座位号、身份这些由 [com.feature.werewolf.prompt.WerewolfPrompt] 每轮现拼。
 */
data class SeatAgent(
    val ai: IAI,
    val model: String,
    val persona: String? = null,
    val thinkingEnabled: Boolean = false,
    /**
     * 这一座位在牌桌上的打牌风格画像（决策气质 + 读人习惯）。
     *
     * 和 [persona] 分工：persona 写「平时怎么说话」，这里写「打牌时怎么想」。
     * 不进存档——续档时 buildAgents 会按同一个 friendId 重新分配，确定性保证风格不变。
     * null = 不注入（人类座位、或用户关掉风格画像）。
     */
    val playStyle: com.feature.werewolf.prompt.PlayerStyle? = null,
)

/** 一条消息在局内的性质。UI 按这个决定怎么渲染，编排层按它决定谁能看见。 */
enum class MessageKind {
    /** 白天公开发言。 */
    SPEECH,

    /** 狼人夜间商议。仅狼人可见。 */
    WOLF_CHAT,

    /** 公开投票表态。 */
    VOTE,

    /** 夜间行动的一句话（预言家查验、女巫用药）。仅本人可见。 */
    NIGHT_ACT,

    /** 上帝播报，来自 [WerewolfGame.history]。 */
    DIVINE,

    /**
     * 唱票卡：一轮投票结束后公开全场票型。
     *
     * 和 [DIVINE] 的区别是它带结构化的 [BallotCard]（谁投谁 + 加权票数），
     * 界面画成一张卡而不是一句播报。仍是**公开**消息，但刻意不朗读——
     * 把「3→5、5→5」念成一串既难听又没信息量。
     */
    BALLOT,
    ;

    /**
     * 说出口的话。
     *
     * 判断标准不是「别人能不能看见」，而是**桌上的人有没有真的听见**：
     * 白天的发言是对全场说的，狼人夜聊是对队友说的，两者都是真实对话，
     * 所以都值得朗读（观战时夜聊才会被念）。
     */
    val isSpokenOutLoud: Boolean
        get() = this == SPEECH || this == WOLF_CHAT

    /**
     * 内心活动：投票的理由、夜里查验与用药的念头。
     *
     * 这些**永远不朗读**，上帝视角也不例外。朗读是为了让用户听见牌桌上
     * 你来我往的对话，把每个人心里的盘算也念一遍，节奏立刻拖垮——一圈人
     * 投票就是十几段独白，而且念出来会和发言混成一片，分不清谁在说话、
     * 谁在心里想。
     */
    val isInnerThought: Boolean
        get() = this == VOTE || this == NIGHT_ACT

    /**
     * 永不朗读：内心活动 + 唱票卡。
     *
     * 唱票卡不是「想」也不是「说」，是把票型画成卡片的数据；念成
     * 「三到五、五到五」没有意义。观战的上帝视角也照此处理。
     */
    val neverSpoken: Boolean
        get() = isInnerThought || this == BALLOT
}

/**
 * 一轮唱票的结构化数据。
 *
 * [ballots] 是原始票型（谁投谁，[Ballot.targetSeat] 为 null 是弃票），
 * [tally] 是**当时的加权票数**（目标座位 → 票，警长 1.5、普通 1）。
 * 权重在投票那一刻就算好存下来，不靠复盘时的终局状态反推——警长可能已经移交或出局。
 */
data class BallotCard(
    val ballots: List<Ballot> = emptyList(),
    val tally: Map<Int, Double> = emptyMap(),
) {
    /** 弃票人数。每一张 targetSeat 为 null 的票就是一个人放弃。 */
    val abstained: Int get() = ballots.count { it.targetSeat == null }
}

/**
 * 局内的一条消息。
 *
 * [audience] 是**可见性**：null 表示全员可见，非 null 表示只有这几个座位能看到。
 *
 * 这个字段是狼人杀和聊天室最本质的差别。聊天室的消息天然全员可见，狼人杀不是：
 * 狼人半夜说的话如果第二天进了所有人的上下文，游戏直接没法玩。
 * 把可见性放在消息上而不是「拼上下文时记得过滤」，是因为后者一定会漏——
 * 而且漏了不会报错，只会让 AI 表现得很奇怪，很难查。
 */
data class WerewolfMessage(
    val day: Int,
    val phase: Phase,
    /** 发言座位。null 表示上帝播报。 */
    val seat: Int?,
    val kind: MessageKind,
    val text: String,
    /** null = 全员可见。 */
    val audience: Set<Int>? = null,
    /**
     * 产生这句话时的模型思考过程（thinking token）。
     *
     * **对局中不显示**——连观战也不显示，看别人推理会把「读心」变成主要玩法；
     * 只在复盘里摊开。它永远不进 [WerewolfPrompt] 的上下文：模型看自己的旧思考
     * 只会复读上一次的推理。
     */
    val reasoning: String = "",
    /**
     * [MessageKind.BALLOT] 携带的结构化票型。其余消息为 null。
     *
     * 不塞进 [text]：票型要按座位画成卡片（谁→谁、每人几票），是数据不是文案。
     * text 里只留一句给人读的摘要，方便存档兼容、日志和没有卡片渲染的场合。
     */
    val ballot: BallotCard? = null,
    /**
     * 产生这条消息的模型调用消耗的 token。
     *
     * 上帝播报（没有模型调用）留默认值 0；复盘按人/按天汇总就靠它。
     * 不进任何提示词上下文——AI 不需要知道别人烧了多少 token。
     */
    val usage: TokenUsage = TokenUsage.EMPTY,
) {
    val isPublic: Boolean get() = audience == null

    fun visibleTo(seat: Int): Boolean = audience == null || seat in audience
}

/**
 * 一局的当前快照：权威状态 + 已经发生的消息。
 *
 * 两个字段都是不可变的，每一步编排都返回新的 table。好处是中途可以随时存档：
 * 切后台、来电、杀进程之后，把 `game` 和 `transcript` 落库就能原样续上。
 */
data class WerewolfTable(
    val game: WerewolfGame,
    val transcript: List<WerewolfMessage> = emptyList(),
) {
    /** 公开部分，给观战视角和回放用。 */
    val publicTranscript: List<WerewolfMessage> get() = transcript.filter { it.isPublic }

    /** 某个座位能看到的全部消息（公开 + 给他的私有消息）。 */
    fun transcriptFor(seat: Int): List<WerewolfMessage> = transcript.filter { it.visibleTo(seat) }

    internal fun append(message: WerewolfMessage): WerewolfTable =
        copy(transcript = transcript + message)
}

/** 自爆时如果没留下别的话，牌桌上就显示这一句。 */
internal const val I_EXPLODED = "我自爆。"

/**
 * 人类点「自爆」时提交的文本。
 *
 * 刻意复用 AI 的标记通道：受理条件、记账、播报全在 [recordExplode] 一条路上，
 * 人类和 AI 的自爆不可能是两套规则。
 */
internal const val EXPLODE_MARKER = "【自爆】"

/**
 * 落一次自爆：先记那句「我自爆。」（或狼自己写的那句话），再让状态机翻夜，
 * 最后把新增的公开事件补成播报。
 *
 * 三个入口共用这一段：
 * - AI 在发言开头写标记（`WerewolfDirector.speakAndMaybeExplode`）；
 * - AI 被别人点名后插爆（`WerewolfDirector.watchExplode`）；
 * - 人类点牌桌上的「自爆」按钮（`WerewolfController.explodeNow`）。
 *
 * 播报文案只在 [com.feature.werewolf.prompt.WerewolfPrompt.describe] 一处，
 * 抄三份迟早有一份忘了改。同一条也保证了「谁爆的、几点爆的、后面还有没有投票」
 * 在三条路径上完全一致。
 */
internal fun WerewolfTable.recordExplode(
    seat: Int,
    text: String = "",
    reasoning: String = "",
    usage: TokenUsage = TokenUsage.EMPTY,
): WerewolfTable {
    val before = game
    val said = text.trim().ifEmpty { I_EXPLODED }
    val recorded = append(
        WerewolfMessage(
            day = before.day,
            phase = before.phase,
            seat = seat,
            kind = MessageKind.SPEECH,
            text = said,
            reasoning = reasoning,
            usage = usage,
        )
    )
    return recorded.copy(game = before.wolfExplode(seat)).syncPublicEvents(before)
}

/**
 * 把 [previous] 之后新增的 history 事件补成上帝播报。
 *
 * 播报消息的 phase 取 **previous.phase**，也就是「产生这条播报的那个阶段」，
 * 不是推进后的阶段：投票结束时状态机直接翻到下一夜，标成新阶段的话，
 * 一条「无人被放逐」的播报就会被记成「守卫阶段」的事情，谁都看得见。
 */
internal fun WerewolfTable.syncPublicEvents(previous: WerewolfGame): WerewolfTable {
    val fresh = game.history.drop(previous.history.size)
    if (fresh.isEmpty()) return this
    var next = this
    fresh.forEach { event ->
        next = next.append(
            WerewolfMessage(
                day = event.day,
                phase = previous.phase,
                seat = null,
                kind = MessageKind.DIVINE,
                text = com.feature.werewolf.prompt.WerewolfPrompt.describe(event),
            )
        )
    }
    return next
}
