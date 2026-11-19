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
