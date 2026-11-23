package com.core.ai

/**
 * 这次失败是不是「等太久」，而不是「填错了」。
 *
 * 用类名判断而不是引用具体类型：core:ai 不依赖 core:network（依赖方向是反的），
 * 而且各家超时异常类不一样（Socket / Connect / HttpRequest / OkHttp / 协程 Timeout…），
 * 共同点只有名字里带 Timeout。
 *
 * 这个判断只用来决定「要不要换一种更省时间的打法」或者「要不要把半句话留下」，
 * 不影响要不要把异常抛出去。
 */
fun Throwable.isTimeoutLike(): Boolean =
    generateSequence(this) { it.cause }
        .any { cause -> cause::class.simpleName?.contains("Timeout", ignoreCase = true) == true }

/**
 * 这次失败像不像「联网字段把请求弄挂了」。
 *
 * 摘搜索重试是有代价的：这一次检索白丢，模型退化成凭记忆答。所以只在两种情况下摘——
 * 错误信息点名 search / tool（厂商直接说不认这个组合），或者是参数类拒绝（400 / 422）：
 * 那时请求形状本身不被接受，去掉搜索还有机会成。
 *
 * **429 限流、5xx、断网一律不摘**：它们跟联网无关，摘掉搜索照样失败，却白丢一次检索，
 * 而且用户看到的是「选了搜索式却从不搜索」。
 *
 * 只看 message 文本，按类名判断行不通：core:ai 不依赖 core:network，拿不到 `DomainException`。
 */
fun Throwable.looksLikeSearchConflict(): Boolean {
    val text = generateSequence(this) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
        .lowercase()
    if (text.isEmpty()) return false
    if ("search" in text || "tool" in text) return true
    return listOf("400", "422", "bad request", "invalid").any { it in text }
}
