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
