package com.feature.werewolf.engine

import com.core.ai.entity.IAI
import com.core.ai.isTimeoutLike
import com.core.ai.model.ChatDelta
import com.core.ai.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 牌桌上「某一步请求失败、等人决定」的那条提示。
 *
 * 只描述失败，不含重试次数以外的任何引擎信息：座位号用来让用户知道是谁卡住了，
 * [attempt] 是这一次请求内部已经重发的次数（第一次失败为 1）。
 */
data class RetryPrompt(
    val seat: Int,
    val message: String,
    val attempt: Int,
    val at: Long = System.currentTimeMillis(),
)

enum class RetryDecision { RETRY, GIVE_UP }

/**
 * 挂着「等待用户决定」的请求，一次只处理一个。
 *
 * 用 [Mutex] 把「发布提示 → 等用户点击 → 收起提示」整段串起来：
 * 并发阶段可能有好几个座位同时撞上限流，但牌桌上一次只该出现一条重试条。
 * 第二个失败的请求会排在锁上，前一个处理完（或被时限掐断）才轮到它——
 * 这时它自己的看门狗可能已经超时了，那就走原来的兜底，不会凭空多出一次请求。
 */
class WerewolfRetryCoordinator {
    private val _prompt = MutableStateFlow<RetryPrompt?>(null)
    val prompt: StateFlow<RetryPrompt?> = _prompt.asStateFlow()

    private val lock = Mutex()

    @Volatile
    private var gate: CompletableDeferred<RetryDecision>? = null

    /** 发布提示并挂起，直到界面点「重新请求」或「放弃」。 */
    suspend fun park(seat: Int, message: String, attempt: Int): RetryDecision = lock.withLock {
        val deferred = CompletableDeferred<RetryDecision>()
        gate = deferred
        _prompt.value = RetryPrompt(seat, message, attempt)
        try {
            deferred.await()
        } finally {
            // 超时 / 退出也会走到这里：提示必须一起收掉，不能留在牌桌上当幽灵。
            if (gate === deferred) {
                gate = null
                _prompt.value = null
            }
        }
    }

    fun resolve(decision: RetryDecision) {
        gate?.complete(decision)
    }

    /** 换局 / 放弃时调用：把挂着的那一步也一并放走。 */
    fun cancelAll() {
        gate?.cancel()
        gate = null
        _prompt.value = null
    }
}

/**
 * 给座位背后的 AI 套一层「失败后等人重发」。
 *
 * 只在**传输层**失败上拦：429、5xx、断网这类重试一次可能就好的。
 * 超时的交给引擎原来的「关思考 + 兜底」——它有完整的时间账本，这里插手只会重复。
 * 格式错误也交给 [com.core.ai.structured.decide] 自己的两轮重试，那一段会改写提示词，
 * 这里若把它当成普通失败原样重放，等于白发一次请求。
 *
 * 关键：**park 发生在请求内部**。所以重发用的是同一组参数，走的是 decide 原来的解析，
 * 引擎和存档完全不知道中间断过一次——失败的那一步没有落过库，自然不用回退 transcript。
 */
class RetryableAI(
    private val delegate: IAI,
    private val seat: Int,
    private val seatName: String,
    private val coordinator: WerewolfRetryCoordinator,
) : IAI by delegate {

    override suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String?,
        enableThinking: Boolean,
        jsonMode: Boolean,
    ): String {
        var attempt = 0
        while (true) {
            try {
                return delegate.chat(messages, model, systemPrompt, enableThinking, jsonMode)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (!t.isRetryableTransport()) throw t
                attempt++
                when (park(t, attempt)) {
                    RetryDecision.RETRY -> continue
                    RetryDecision.GIVE_UP -> throw t
                }
            }
        }
    }

    override fun chatStream(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String?,
        enableThinking: Boolean,
        jsonMode: Boolean,
    ): Flow<ChatDelta> = flow {
        var attempt = 0
        while (true) {
            var emitted = false
            var failure: Throwable? = null
            try {
                delegate.chatStream(messages, model, systemPrompt, enableThinking, jsonMode)
                    .collect { delta ->
                        if (delta.content.isNotEmpty() || delta.reasoning.isNotEmpty()) emitted = true
                        emit(delta)
                    }
                return@flow
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                failure = t
            }
            val error = failure!!
            // 已经吐了字就不能从头再放一遍：前半句会重复出现在牌桌上。
            // 这种情况交给引擎保留半句，原本就是这么设计的。
            if (emitted || !error.isRetryableTransport()) throw error
            attempt++
            when (park(error, attempt)) {
                RetryDecision.RETRY -> continue
                RetryDecision.GIVE_UP -> throw error
            }
        }
    }

    private suspend fun park(error: Throwable, attempt: Int): RetryDecision =
        coordinator.park(seat, "$seatName：${readableReason(error)}", attempt)
}

/**
 * 只有「重试一次可能就好」的传输错误才值得占用一条重试条。
 *
 * 异常类型按类名认：core:network 是 core:ai 的 implementation 依赖，
 * 这一层看不到 [DomainException]，硬引用会把模块边界拆掉。
 */
private fun Throwable.isRetryableTransport(): Boolean {
    if (isTimeoutLike()) return false
    return when (this::class.simpleName) {
        "ServerException", "NetworkException" -> true
        // Key 错、模型没开通：重试一百次也一样，照旧走错误条
        else -> false
    }
}

/** 把传输异常翻成牌桌上能读的一句话；重试条不是错误页，别把堆栈式文案摆上去。 */
private fun readableReason(error: Throwable): String {
    val raw = error.message?.take(160)?.takeIf { it.isNotBlank() } ?: "请求失败"
    return when {
        raw.contains("429") -> "请求过于频繁，被上游限流（429）"
        raw.contains("402") -> "账户余额不足（402）"
        raw.contains("401") || raw.contains("403") -> "API Key 无效或无权限"
        raw.contains("404") -> "模型不存在或未开通（404）"
        else -> raw
    }
}
