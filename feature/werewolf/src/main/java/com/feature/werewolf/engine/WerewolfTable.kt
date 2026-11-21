package com.feature.werewolf.engine

import com.core.ai.entity.IAI
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
