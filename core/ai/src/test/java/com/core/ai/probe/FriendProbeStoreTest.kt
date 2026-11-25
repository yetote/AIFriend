package com.core.ai.probe

import com.core.ai.entity.IAI
import com.core.ai.model.ChatMessage
import com.core.ai.model.Friend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class FriendProbeStoreTest {

    private class FakeAI(
        override var brandName: String = "Deepseek",
        override val supportsWebSearch: Boolean = false,
        private val hasKey: Boolean = true,
        private val onChat: suspend (model: String, webSearch: Boolean) -> String = { _, _ -> "OK" },
    ) : IAI {
        override suspend fun requestModel() = Unit
        override var modelSet: MutableSet<String> = mutableSetOf()
        override var company: String = brandName
        override fun hasCredentials(): Boolean = hasKey

        override suspend fun chat(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
            webSearch: Boolean,
        ): String = onChat(model, webSearch)

        @Deprecated("unused")
        override suspend fun chat(message: String, model: String): String = "OK"
    }

    private fun friend(id: String, brand: String = "Deepseek", model: String = "deepseek-chat") =
        Friend(
            id = id,
            name = "好友$id",
            role = "助手",
            prompt = "",
            company = brand,
            brand = brand,
            model = model,
        )

    @Test
    fun `同厂商同模型只测一次且新建同模型好友自动共享状态`() = runBlocking {
        val calls = AtomicInteger(0)
        var now = 1_000L
        val coordinator = FriendProbeCoordinator(clock = { now })

        val ok = coordinator.probe(
            friends = listOf(friend("a"), friend("b")),
            force = true,
            aiFor = { FakeAI { _, _ -> calls.incrementAndGet(); "OK" } },
        )

        assertTrue(ok)
        assertEquals(1, calls.get())

        // 即使 c 是后面新加的同模型好友，也能直接命中共享结果
        val resolved = coordinator.resolveForFriends(
            listOf(friend("a"), friend("b"), friend("c")),
            webSearch = false,
        )
        assertEquals(ApiCheckState.OK, resolved["a"]?.state)
        assertEquals(ApiCheckState.OK, resolved["b"]?.state)
        assertEquals(ApiCheckState.OK, resolved["c"]?.state)
    }

    @Test
    fun `好友更换模型后不会继承旧模型的绿灯`() = runBlocking {
        val coordinator = FriendProbeCoordinator(clock = { 1_000L })

        coordinator.probe(
            friends = listOf(friend("a", model = "deepseek-chat")),
            force = true,
            aiFor = { FakeAI() },
        )

        val afterEdit = coordinator.resolveForFriends(
            listOf(friend("a", model = "deepseek-reasoner")),
            webSearch = false,
        )
        assertNull("换成未测过的新模型后应当回到未检测状态", afterEdit["a"])
    }

    @Test
    fun `首页探活通过后狼人杀普通开局直接复用不重测`() = runBlocking {
        val calls = AtomicInteger(0)
        var now = 10_000L
        val coordinator = FriendProbeCoordinator(clock = { now })
        val roster = listOf(friend("a"), friend("b", brand = "Kimi", model = "kimi-k2"))

        // 首页一键探活
        coordinator.probe(
            friends = roster,
            force = true,
            webSearch = false,
            aiFor = { brand -> FakeAI(brandName = brand) { _, _ -> calls.incrementAndGet(); "OK" } },
        )
        assertEquals(2, calls.get())

        // 3 分钟后进狼人杀开局（force = false, webSearch = false）：应当 0 次额外请求
        now += 3 * 60 * 1000L
        val allOk = coordinator.probe(
            friends = roster,
            force = false,
            webSearch = false,
            aiFor = { brand -> FakeAI(brandName = brand) { _, _ -> calls.incrementAndGet(); "OK" } },
        )
        assertTrue(allOk)
        assertEquals("十分钟内开局应直接复用首页探活结论", 2, calls.get())
    }

    @Test
    fun `搜索式探活失败不污染首页普通对话状态`() = runBlocking {
        val coordinator = FriendProbeCoordinator(clock = { 1_000L })
        val f = friend("a", brand = "Deepseek", model = "deepseek-chat")

        coordinator.probe(
            friends = listOf(f),
            force = true,
            webSearch = false,
            aiFor = { FakeAI(supportsWebSearch = false) },
        )
        val searchOk = coordinator.probe(
            friends = listOf(f),
            force = false,
            webSearch = true,
            aiFor = { FakeAI(supportsWebSearch = false) },
        )

        assertFalse(searchOk)
        assertEquals(ApiCheckState.OK, coordinator.resolveForFriends(listOf(f), webSearch = false)["a"]?.state)
        val searchCheck = coordinator.resolveForFriends(listOf(f), webSearch = true)["a"]
        assertEquals(ApiCheckState.FAILED, searchCheck?.state)
        assertTrue(searchCheck?.searchUnsupported == true)
    }

    @Test
    fun `单厂商凭证变更只失效该厂商缓存不影响其他厂商`() = runBlocking {
        val coordinator = FriendProbeCoordinator(clock = { 1_000L })
        val ds = friend("a", brand = "Deepseek", model = "deepseek-chat")
        val kimi = friend("b", brand = "Kimi", model = "kimi-k2")

        coordinator.probe(
            friends = listOf(ds, kimi),
            force = true,
            aiFor = { brand -> FakeAI(brandName = brand) },
        )

        coordinator.invalidateBrands(listOf("Kimi"))

        val resolved = coordinator.resolveForFriends(listOf(ds, kimi), webSearch = false)
        assertEquals(ApiCheckState.OK, resolved["a"]?.state)
        assertNull("改了 Kimi 的 Key 后 Kimi 缓存应失效", resolved["b"])
    }

    @Test
    fun `中途取消批量探活时已完成的绿灯保留且未完成的检测中会被擦除`() = runBlocking {
        val coordinator = FriendProbeCoordinator(clock = { 1_000L })
        val fastDone = CompletableDeferred<Unit>()
        val slowGate = CompletableDeferred<Unit>()

        val fast = friend("fast", brand = "Deepseek", model = "fast-model")
        val slow = friend("slow", brand = "Kimi", model = "slow-model")

        val job = launch {
            try {
                coordinator.probe(
                    friends = listOf(fast, slow),
                    force = true,
                    aiFor = { brand ->
                        if (brand == "Deepseek") {
                            FakeAI(brandName = brand) { _, _ ->
                                fastDone.complete(Unit)
                                "OK"
                            }
                        } else {
                            FakeAI(brandName = brand) { _, _ ->
                                slowGate.await()
                                "OK"
                            }
                        }
                    },
                )
            } catch (_: CancellationException) {
                // expected
            }
        }

        fastDone.await()
        // 等 fast 结果写入 StateFlow 后再取消 slow
        while (coordinator.resolveForFriends(listOf(fast))["fast"]?.state != ApiCheckState.OK) {
            kotlinx.coroutines.yield()
        }
        job.cancel()
        job.join()

        val resolved = coordinator.resolveForFriends(listOf(fast, slow))
        assertEquals("取消前已跑完的绿灯应保留", ApiCheckState.OK, resolved["fast"]?.state)
        assertNull("被取消时仍在转圈的项应清回未检测", resolved["slow"])
        assertFalse(coordinator.progress.value.active)
    }

    @Test
    fun `耗时格式化清晰可读`() {
        assertEquals("0ms", formatProbeElapsed(-5))
        assertEquals("480ms", formatProbeElapsed(480))
        assertEquals("1.0s", formatProbeElapsed(1_000))
        assertEquals("2.4s", formatProbeElapsed(2_360))
    }
}
