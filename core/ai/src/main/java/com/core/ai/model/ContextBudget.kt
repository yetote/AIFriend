package com.core.ai.model

/**
 * 上下文预算：每次请求只发送最近的一部分历史，避免对话越长 token 越爆炸。
 *
 * - 按估算 token 从最新往回保留，直到预算用尽
 * - 至少保留 [MIN_MESSAGES] 条，防止最后几轮被截断
 * - 只影响发给模型的内容，本地展示和持久化的历史不受影响
 * - 用字符估算而不是真实 tokenizer，是为了不引入各厂商的分词依赖；
 *   中文按 1 字 ≈ 1 token，英文按 4 字符 ≈ 1 token
 *
 * 放在 core:ai 而不是某个 feature 里：截断的对象就是 [ChatMessage]，
 * 任何发起对话的模块（1:1、聊天室、狼人杀）都要用同一套规则，
 * 否则同一条消息在不同页面占的预算不一样，问题很难复现。
 */
object ContextBudget {
    const val MAX_HISTORY_TOKENS = 6_000
    const val MIN_MESSAGES = 6
}

fun List<ChatMessage>.trimToBudget(
    maxTokens: Int = ContextBudget.MAX_HISTORY_TOKENS,
    minMessages: Int = ContextBudget.MIN_MESSAGES
): List<ChatMessage> {
    if (isEmpty()) return this

    var usedTokens = 0
    val kept = ArrayDeque<ChatMessage>()
    for (message in asReversed()) {
        val tokens = estimateTokens(message.content)
        if (kept.size >= minMessages && usedTokens + tokens > maxTokens) break
        kept.addFirst(message)
        usedTokens += tokens
    }
    return kept.toList()
}

fun estimateTokens(text: String): Int {
    var cjk = 0
    var other = 0
    for (char in text) {
        if (char.code >= 0x2E80) cjk++ else other++
    }
    return cjk + (other + 3) / 4
}
