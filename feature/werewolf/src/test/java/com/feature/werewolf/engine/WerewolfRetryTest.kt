package com.feature.werewolf.engine

import com.core.ai.entity.IAI
import com.core.ai.model.ChatDelta
import com.core.ai.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 「失败一步、就地重发」的测试。
 *
 * 重点不是重试本身，而是**失败的请求没有副作用**：
 * - 没吐字之前失败 → 挂起等人，重发用同一组参数、同一条路径；
 * - 已经吐了字 → 绝不从头再放一遍（会重复半句）；
 * - 放弃 → 原异常照常抛出，让整局按存档停下。
 */
class WerewolfRetryTest {

    /** 类名要是 ServerException：装饰器按类名判断「可重试的传输错误」。 */
    private class ServerException(code: Int) : Exception("请求失败（$code）")

    private class Unauthorized : Exception("API Key 无效或已过期，请检查配置")

    /** 一个按脚本回答的假 AI：每次调用弹出一个结果。 */
    private class ScriptedAI(
        private val results: MutableList<Result<String>>,
        val seenModels: MutableList<String> = mutableListOf(),
    ) : IAI {
        override suspend fun requestModel() = Unit
        override var modelSet: MutableSet<String> = mutableSetOf()
        override var brandName: String = "测试"
        override var company: String = "测试"
        @Suppress("DEPRECATION")
        override suspend fun chat(message: String, model: String): String = chat(
            messages = listOf(ChatMessage.user(message)), model = model
        )

        override suspend fun chat(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
        ): String {
            seenModels += model
            val r = results.removeAt(0)
            return r.getOrThrow()
        }

        override fun chatStream(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
        ): Flow<ChatDelta> = flow {
            // 直接复用一次性结果，仅用来验证「何时重发、何时抛」的分支
            emit(ChatDelta(content = chat(messages, model, systemPrompt, enableThinking, jsonMode)))
        }
    }

    private fun stub(results: List<Result<String>>) = ScriptedAI(results.toMutableList())

    // ---------------------------------------------------------------------
    // 协调器
    // ---------------------------------------------------------------------

    @Test
    fun `park 发布提示直到用户决定`() = runBlocking {
        val coordinator = WerewolfRetryCoordinator()
        assertNull(coordinator.prompt.value)

        val done = async {
            coordinator.park(seat = 3, message = "3 号：限流", attempt = 1)
        }
        // 等 park 发布提示
        while (coordinator.prompt.value == null) kotlinx.coroutines.yield()
        assertEquals(3, coordinator.prompt.value!!.seat)
        assertTrue(done.isActive)

        coordinator.resolve(RetryDecision.RETRY)
        assertEquals(RetryDecision.RETRY, done.await())
        assertNull(coordinator.prompt.value)
    }

    @Test
    fun `同时只挂一个请求，第二个排队`() = runBlocking {
        val coordinator = WerewolfRetryCoordinator()
        val first = async { coordinator.park(1, "1 号", 1) }
        while (coordinator.prompt.value == null) kotlinx.coroutines.yield()
        val second = async { coordinator.park(2, "2 号", 1) }
        kotlinx.coroutines.yield()
        // 提示仍然是第一个座位的
        assertEquals(1, coordinator.prompt.value!!.seat)

        coordinator.resolve(RetryDecision.RETRY)
        assertEquals(RetryDecision.RETRY, first.await())
        // 第一个收起后，第二个才浮上来
        while (coordinator.prompt.value == null) kotlinx.coroutines.yield()
        assertEquals(2, coordinator.prompt.value!!.seat)
        coordinator.resolve(RetryDecision.GIVE_UP)
        assertEquals(RetryDecision.GIVE_UP, second.await())
    }

    @Test
    fun `取消挂起的请求会收掉提示`() = runBlocking {
        val coordinator = WerewolfRetryCoordinator()
        val job = launch { coordinator.park(5, "5 号", 1) }
        while (coordinator.prompt.value == null) kotlinx.coroutines.yield()
        coordinator.cancelAll()
        job.join()
        assertNull(coordinator.prompt.value)
    }

    // ---------------------------------------------------------------------
    // 装饰器
    // ---------------------------------------------------------------------

    @Test
    fun `传输错误挂起，点重发后用同一组参数再请求`() = runBlocking {
        val coordinator = WerewolfRetryCoordinator()
        val stub = stub(
            listOf(Result.failure(ServerException(429)), Result.success("好了"))
        )
        val ai = RetryableAI(stub, 3, "小咪", coordinator)

        val answer = async {
            ai.chat(listOf(ChatMessage.user("轮到你发言")), model = "m-1")
        }
        while (coordinator.prompt.value == null) kotlinx.coroutines.yield()
        assertEquals("小咪：请求过于频繁，被上游限流（429）", coordinator.prompt.value!!.message)

        coordinator.resolve(RetryDecision.RETRY)
        assertEquals("好了", answer.await())
        // 两次请求用的是同一个模型、同一句话——没有重新拼上下文
        assertEquals(listOf("m-1", "m-1"), stub.seenModels)
        assertNull(coordinator.prompt.value)
    }

    @Test
    fun `放弃重发时原异常照常抛出`() = runBlocking {
        val coordinator = WerewolfRetryCoordinator()
        val original = ServerException(429)
        val ai = RetryableAI(stub(listOf(Result.failure(original))), 3, "小咪", coordinator)

        val answer = async {
            runCatching { ai.chat(listOf(ChatMessage.user("hi")), model = "m") }
        }
        while (coordinator.prompt.value == null) kotlinx.coroutines.yield()
        coordinator.resolve(RetryDecision.GIVE_UP)
        val result = answer.await()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === original)
    }

    @Test
    fun `非传输错误直接抛出，不弹重试条`() = runBlocking {
        val coordinator = WerewolfRetryCoordinator()
        val ai = RetryableAI(stub(listOf(Result.failure(Unauthorized()))), 3, "小咪", coordinator)
        try {
            ai.chat(listOf(ChatMessage.user("hi")), model = "m")
            fail("应当抛出")
        } catch (t: Throwable) {
            assertTrue(t is Unauthorized)
        }
        assertNull(coordinator.prompt.value)
    }

    @Test
    fun `超时不弹重试条，交给引擎自己的兜底`() = runBlocking {
        class SocketTimeoutException : Exception("timeout")
        val coordinator = WerewolfRetryCoordinator()
        val ai = RetryableAI(
            stub(listOf(Result.failure(SocketTimeoutException()))), 3, "小咪", coordinator
        )
        try {
            ai.chat(listOf(ChatMessage.user("hi")), model = "m")
            fail("应当抛出")
        } catch (t: Throwable) {
            assertTrue(t is SocketTimeoutException)
        }
        assertNull(coordinator.prompt.value)
    }

    @Test
    fun `已经吐了字的流失败时不再从头重放`() = runBlocking {
        val coordinator = WerewolfRetryCoordinator()
        val delegate = object : IAI by stub(emptyList()) {
            override fun chatStream(
                messages: List<ChatMessage>,
                model: String,
                systemPrompt: String?,
                enableThinking: Boolean,
                jsonMode: Boolean,
            ): Flow<ChatDelta> = flow {
                emit(ChatDelta(content = "前半句"))
                throw ServerException(429) as Throwable
            }
        }
        val ai = RetryableAI(delegate, 3, "小咪", coordinator)
        val parts = mutableListOf<ChatDelta>()
        try {
            ai.chatStream(listOf(ChatMessage.user("hi")), "m").collect { parts += it }
            fail("应当抛出")
        } catch (t: Throwable) {
            assertTrue(t is ServerException)
        }
        assertEquals(1, parts.size)
        assertEquals("前半句", parts[0].content)
        // 不能因为半句失败就弹重试——重放会把前半句再说一遍
        assertNull(coordinator.prompt.value)
    }

    @Test
    fun `一个字都没吐的流失败可以重发`() = runBlocking {
        val coordinator = WerewolfRetryCoordinator()
        var calls = 0
        val delegate = object : IAI by stub(emptyList()) {
            override fun chatStream(
                messages: List<ChatMessage>,
                model: String,
                systemPrompt: String?,
                enableThinking: Boolean,
                jsonMode: Boolean,
            ): Flow<ChatDelta> = flow {
                calls++
                if (calls == 1) throw ServerException(429)
                emit(ChatDelta(content = "重发后的发言"))
            }
        }
        val ai = RetryableAI(delegate, 3, "小咪", coordinator)
        val parts = async { ai.chatStream(listOf(ChatMessage.user("hi")), "m").toList() }
        while (coordinator.prompt.value == null) kotlinx.coroutines.yield()
        coordinator.resolve(RetryDecision.RETRY)
        assertEquals(listOf("重发后的发言"), parts.await().map { it.content })
        assertEquals(2, calls)
    }

    @Test
    fun `用户取消不弹重试条`() = runBlocking {
        val coordinator = WerewolfRetryCoordinator()
        val delegate = stub(listOf(Result.failure(CancellationException())))
        val ai = RetryableAI(delegate, 3, "小咪", coordinator)
        try {
            ai.chat(listOf(ChatMessage.user("hi")), "m")
            fail("应当抛出取消")
        } catch (t: CancellationException) {
            // 用户退出 / 暂停：照常取消，不当成可重试的失败
        }
        assertNull(coordinator.prompt.value)
    }
}
