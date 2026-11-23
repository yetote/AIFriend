package com.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 一次模型调用的 token 用量。
 *
 * [input] 是发出去的上下文（system + 历史），[output] 是模型这一轮生成的正文 + 思维链。
 * 两个方向分开记：复盘时「谁说得最多」看 output，「谁把上下文撑大」看 input。
 *
 * [estimated] 为 true 表示数字是本地按字符算出来的近似值，不是服务端回报的账单数。
 * 各家兼容层对 `usage` 的支持很不齐（有的只在非流式返回、有的干脆不回），
 * 拿不到真数时也要给用户一个能看的量级——但必须标清楚是估算，
 * 不能让用户拿它去和平台账单对账。
 */
data class TokenUsage(
    val input: Int = 0,
    val output: Int = 0,
    val estimated: Boolean = false,
) {
    val total: Int get() = input + output

    operator fun plus(other: TokenUsage): TokenUsage = TokenUsage(
        input = input + other.input,
        output = output + other.output,
        // 只要有一段是估的，合起来就只能算估的。
        estimated = estimated || other.estimated,
    )

    companion object {
        val EMPTY = TokenUsage()

        /**
         * 展示用的一句话，例如「入 5.2k / 出 0.8k」。
         *
         * 不合并成一个总数：一次请求的大头是重复发送的上下文，把它和「模型这轮写了多少」
         * 加在一起，用户只会觉得「怎么这么贵」却看不出贵在哪。分开之后，
         * 入大＝提示词重发，出大＝它话多，一眼能分清。
         */
        fun format(usage: TokenUsage): String =
            "${if (usage.estimated) "约 " else ""}入 ${compact(usage.input)} / 出 ${compact(usage.output)}"

        /** 千位以上压缩成 5.2k / 12k / 1.2M，完整数字只在账单里有意义。 */
        private fun compact(value: Int): String = when {
            value < 1_000 -> value.toString()
            value < 100_000 -> "${(value / 100) / 10.0}".removeSuffix(".0") + "k"
            value < 1_000_000 -> "${value / 1_000}k"
            else -> "${(value / 100_000) / 10.0}".removeSuffix(".0") + "M"
        }
    }
}

/**
 * OpenAI 兼容接口返回的 `usage`。
 *
 * 字段名按各家写法都收一份：OpenAI / DeepSeek / 多数网关用 prompt_tokens，
 * Anthropic 风格的兼容层用 input_tokens；两边都出现时优先带值的那个。
 */
@Serializable
data class TokenUsageResponse(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
    @SerialName("input_tokens")
    val inputTokens: Int? = null,
    @SerialName("output_tokens")
    val outputTokens: Int? = null,
) {
    fun toUsage(): TokenUsage? {
        val input = promptTokens ?: inputTokens ?: return null
        val output = completionTokens ?: outputTokens ?: return null
        return TokenUsage(input = input, output = output, estimated = false)
    }
}

/** 一次完整回复 + 它的用量。给需要统计的场景用，普通对话继续用只返回正文的 chat。 */
data class ChatReply(val text: String, val usage: TokenUsage)

/**
 * 本地 token 估算器。
 *
 * 只用来给界面一个量级，不参与计费。规则刻意保持简单：
 * - CJK / emoji 这类非 ASCII 字符按 1 字符 ≈ 1 token；
 * - 连续 ASCII 按 4 字符 ≈ 1 token（英文分词的大致比例）；
 * - 空白计入所在段，不单独算。
 *
 * 真实值一律优先取服务端 usage；这里只是「连 usage 都不回」时的兜底。
 */
object TokenCounter {

    fun estimate(text: String?): Int =
        if (text.isNullOrEmpty()) 0 else estimateTokens(text)

    /**
     * 估算发出去的上下文。
     *
     * 每条消息按 4 token 的消息边框开销算；[ChatMessage.reasoning] 是
     * `@Transient`，不会真的发给模型，所以不计入。
     */
    fun estimateInput(messages: List<ChatMessage>, systemPrompt: String?): Int {
        var total = 3
        if (!systemPrompt.isNullOrBlank()) {
            total += estimate(systemPrompt) + 4
        }
        messages.forEach { total += estimate(it.content) + 4 }
        return total
    }
}
