package com.feature.werewolf.engine

import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfGame

/**
 * 每轮记录板：当前这一轮的**发言顺序**、**谁已经说过**、**谁还没说**。
 *
 * ## 为什么要有这个东西
 *
 * 发言顺序和发言进度本来是编排层的内部安排（谁还没轮到、刚才谁说过了），
 * 模型看不到。它只能从上下文里「猜」——猜错一次，典型表现是：
 * - 把还没发言的人当成已经表过态（「3 号刚才说他是预言家」——3 号还没开口）；
 * - 不知道自己排在第几个，说出「等 5 号说完我再补充」这种空话；
 * - 把上一轮说过的话当成这一轮的，重新论一遍自己说过的事。
 *
 * 这些错误在牌桌上很显眼：模型会替没开口的人发言，会反复背书。
 *
 * ## 和公开台账的分工
 *
 * [PublicLedger] 是**跨天**的：「谁说过什么能核对的话」（跳过预言家、话和票对不上）。
 * 每轮记录板是**本轮**的：「现在轮到谁、谁已经开口」。
 * 两块都是「可核对的事实」，但时间尺度不同，所以分开两块写。
 *
 * ## 信息隔离
 *
 * 白天讨论、竞选发言、遗言都是公开的——谁上警、谁死了、发言顺序，全桌都知道，
 * 因此这些记录对所有座位可见。
 *
 * 狼人商议是**狼队私有**的：好人拿到「狼队发言顺序」等于知道了狼队有几个人、
 * 谁先指挥、谁已经表过态。这是夜里才会揭晓的信息，白天拿到就是泄底，
 * 所以非狼座位直接拿不到这块板子（返回空串，不是拿到一份被裁剪过的）。
 */
object RoundBoard {

    /**
     * 拼出可以直接注入 system prompt 的一段文字。
     *
     * 没有可写的内容时返回空串——**不占位**。空标题比没有标题更糟：
     * 模型会以为「本轮记录」这一节是空的、这局没有轮次信息。
     */
    fun of(game: WerewolfGame, seat: Int): String = render(game, seat)

    internal fun render(game: WerewolfGame, seat: Int): String {
        val speakers = game.currentRoundSpeakers()
        if (speakers.isEmpty()) return ""

        // 夜间狼聊是狼队私有的。
        if (game.phase == Phase.NIGHT_WOLF_CHAT && !game.isWerewolf(seat)) return ""

        val spoken = game.currentRoundSpokenSeats()
        val pending = speakers.filterNot { it in spoken }

        return buildString {
            append("【每轮记录】\n")
            append("本轮阶段：").append(game.phase.displayName)
            append("（第 ").append(game.day).append(" 天）。")
            append("下面是本轮的事实，用来判断该说什么、别说什么：\n")
            append("· 发言顺序：").append(speakers.joinToString("-")).append("\n")
            append("· 已经发过言：")
            append(if (spoken.isEmpty()) "还没有人发言" else spoken.joinToString("-"))
            append("\n")
            append("· 还没发言：")
            append(if (pending.isEmpty()) "没有了，本轮所有人都说过了" else pending.joinToString("-"))
            append("\n")
            // 自己排第几、说没说过：这两件事模型最容易搞错，
            // 而且搞错的后果是「替别人发言」和「重复自己说过的话」。
            val index = speakers.indexOf(seat)
            if (index >= 0) {
                append("· 你是 ").append(seat).append(" 号：")
                if (seat in spoken) {
                    append("本轮已经说过话了，排在第 ").append(index + 1).append(" 位。\n")
                } else {
                    append("本轮第 ").append(index + 1).append(" 个发言。")
                    if (spoken.isNotEmpty()) {
                        append("前面 ").append(spoken.joinToString("-")).append(" 已经说过。")
                    }
                    val onceHint = when (game.phase) {
                        Phase.DAY_DISCUSSION ->
                            "本轮每人仅发言一次，你说完即流转下一位，投票前不会再轮回给你补充或插话（别说「等别人说完我再补充」）。"
                        Phase.DAY_SHERIFF_SPEECH ->
                            "本轮每人仅发言一次，你说完即流转下一位，退水与警长投票前不会再轮回给你补充或插话。"
                        Phase.NIGHT_WOLF_CHAT ->
                            "本轮夜聊每人仅发言一次，你说完即流转下一位队友（除非狼刀平票加商），请一次性把刀口与白天分工说清楚。"
                        Phase.DAY_LAST_WORDS ->
                            "遗言仅有这一次机会，说完即离场。"
                        else ->
                            "本轮每人仅发言一次，说完即流转下一位，不会再轮回给你补充。"
                    }
                    append(onceHint).append("\n")
                }
            }
        }
    }
}
