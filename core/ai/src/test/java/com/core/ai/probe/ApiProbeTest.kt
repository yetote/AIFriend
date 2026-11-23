package com.core.ai.probe

import com.core.ai.entity.IAI
import com.core.ai.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 探活的失败路径测试。
 *
 * 这个函数的全部价值在于「出错时给出能看懂的原因」，所以测的都是失败分支：
 * 正常返回只断言一句 ok，其余几个用例各自对应配置页上会出现的一种红字。
 */
class ApiProbeTest {

    /** 只负责按剧本回话或抛错的最小 IAI。 */
    private class FakeAI(
        private val configured: Boolean = true,
        // 放在中间：respond 必须是最后一个参数，尾随 lambda 的写法才成立
        override val supportsWebSearch: Boolean = false,
        private val respond: suspend () -> String,
    ) : IAI {
        /** 记录真正发出过几次请求，用来断言「没 Key 时一次都不该发」。 */
        var chatCalls: Int = 0

        /** 每次请求实际带的搜索开关，用来断言「该带的带上了」。 */
        val searchFlags: MutableList<Boolean> = mutableListOf()

        override var modelSet: MutableSet<String> = mutableSetOf()
        override var brandName: String = "Fake"
        override var company: String = "Fake"

        override suspend fun requestModel() = Unit

        override fun hasCredentials(): Boolean = configured

        override suspend fun chat(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
            webSearch: Boolean,
        ): String {
            chatCalls++
            searchFlags += webSearch
            return respond()
        }

        override suspend fun chat(message: String, model: String): String = respond()
    }

    @Test
    fun `正常返回算连通，且不带失败原因`() = runBlocking {
        val result = FakeAI { "OK" }.probe("m")

        assertTrue(result.ok)
        assertNull(result.message)
        assertTrue("耗时不应该是负数", result.elapsedMs >= 0)
    }

    @Test
    fun `模型回空串也算连通`() = runBlocking {
        // 空回复是模型自己的毛病，但链路、Key、模型名都是对的，
        // 而这三样才是开局前要确认的东西
        val result = FakeAI { "" }.probe("m")

        assertTrue(result.ok)
    }

    @Test
    fun `没配 Key 时直接判失败，一个请求都不发`() = runBlocking {
        // 发出去必然 401，回来写的是「API Key 无效」——用户根本没填过，
        // 提示他「Key 无效」只会让他去怀疑一个还不存在的东西
        val ai = FakeAI(configured = false) { "OK" }
        val result = ai.probe("m")

        assertFalse(result.ok)
        assertEquals(0, ai.chatCalls)
        assertTrue(
            "要说明是没配 Key：${result.message}",
            result.message.orEmpty().contains("API Key"),
        )
    }

    @Test
    fun `连接异常翻译成人话，而不是把堆栈丢给用户`() = runBlocking {
        val result = FakeAI { throw IllegalStateException("Connection refused") }.probe("m")

        assertFalse(result.ok)
        assertEquals("Connection refused", result.message)
    }

    @Test
    fun `超时算失败，文案里带秒数`() = runBlocking {
        val result = FakeAI {
            delay(5_000)
            "永远到不了"
        }.probe("m", timeoutMs = 100)

        assertFalse(result.ok)
        assertTrue(
            "超时文案要说明是等太久：${result.message}",
            result.message.orEmpty().contains("没有响应"),
        )
    }

    @Test
    fun `外部取消原样抛出，不能记成检测失败`() = runBlocking {
        // 用户退出页面时这批请求应该立刻停。如果被 catch 成 ApiProbeResult(false)，
        // 下次进来会看到一片「连不上」的红点，其实是他自己关掉的
        val job = launch {
            FakeAI {
                delay(5_000)
                "OK"
            }.probe("m")
        }
        delay(50)
        job.cancel()
        job.join()

        assertTrue("取消之后协程应该是被取消状态", job.isCancelled)
    }

    @Test
    fun `取消异常不会被当成普通失败吞掉`() = runBlocking {
        var propagated = false
        try {
            FakeAI { throw CancellationException("stop") }.probe("m")
        } catch (cancelled: CancellationException) {
            propagated = true
        }

        assertTrue(propagated)
    }

    @Test
    fun `搜索式下这家没有联网搜索时，一个请求都不发`() = runBlocking {
        // 关键在「不发」而不是「发了再报错」：不支持的厂商收到未知字段可能静默忽略，
        // 那样探活反而是绿的，而用户开局后看到的才是「写着可以查、其实一次没查」
        val ai = FakeAI(supportsWebSearch = false) { "OK" }
        val result = ai.probe("m", webSearch = true)

        assertFalse(result.ok)
        assertEquals(0, ai.chatCalls)
        assertTrue(
            "要指名是联网这件事不行：${result.message}",
            result.message.orEmpty().contains("不支持联网搜索"),
        )
        assertTrue("要单独标记出来，界面才能给对建议", result.searchUnsupported)
    }

    @Test
    fun `连不上不算不支持联网`() = runBlocking {
        // 这两种失败要开不同的药：连不上查 Key，没有搜索换厂商。
        // 混在一起会让用户反复重试一把完全没问题的 Key。
        // 这里这家是支持联网的，所以请求真的发出去了，失败原因是链路本身
        val result = FakeAI(supportsWebSearch = true) {
            throw IllegalStateException("Connection refused")
        }.probe("m", webSearch = true)

        assertFalse(result.ok)
        assertFalse(result.searchUnsupported)
    }

    @Test
    fun `搜索式下支持联网时，请求真的带上搜索`() = runBlocking {
        // 不带搜索的探活是另一道题：它证明连得上，证明不了「带着检索也能回话」
        val ai = FakeAI(supportsWebSearch = true) { "OK" }
        val result = ai.probe("m", webSearch = true)

        assertTrue(result.ok)
        assertEquals(listOf(true), ai.searchFlags)
    }

    @Test
    fun `普通探活不带搜索`() = runBlocking {
        val ai = FakeAI(supportsWebSearch = true) { "OK" }
        ai.probe("m")

        assertEquals(listOf(false), ai.searchFlags)
    }
}
