package com.feature.werewolf.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三车道调度（windowedAsync）。
 *
 * 窗口=3 且指定慢车道时，慢车道那个 item 不占快车道许可，能和其它快车道 item 同时开跑；
 * 返回顺序仍与输入一致（调用方靠下标对应座位）。
 */
class WindowedAsyncTest {

    @Test
    fun `慢车道不占快车道许可，能与快车道同时开跑`() = runBlocking {
        val director = WerewolfDirector(emptyMap())
        // 窗口 3 → 慢车道 1 条 + 快车道 2 条。让慢车道 + 两个快车道都卡在同一个门上，
        // 若慢车道占了快车道许可，第 3 个协程就进不来，门永远等不满 → 超时。
        val gate = CompletableDeferred<Unit>()
        var entered = 0
        val enteredLock = Any()
        val slow = CompletableDeferred<Unit>()
        val fast1 = CompletableDeferred<Unit>()
        val fast2 = CompletableDeferred<Unit>()

        val measuring = coroutineScope {
            // 用 async 让三个 block 都启动，慢车道先把自己标记好再等门
            val m = async {
                director.windowedAsync(
                    items = listOf(1, 2, 3),
                    window = 3,
                    slowLane = 1,
                ) { seat ->
                    when (seat) {
                        1 -> slow.complete(Unit)
                        2 -> fast1.complete(Unit)
                        3 -> fast2.complete(Unit)
                    }
                    synchronized(enteredLock) { entered++ }
                    gate.await()
                    seat
                }
            }
            // 三个都要先启动（慢车道不占许可才能三个同时进来）
            slow.await()
            fast1.await()
            fast2.await()
            gate.complete(Unit)
            m.await()
        }

        assertEquals(listOf(1, 2, 3), measuring)
        assertEquals(3, entered)
    }

    @Test
    fun `没指定慢车道时退化为普通窗口并发，返回顺序不变`() = runBlocking {
        val director = WerewolfDirector(emptyMap())
        // 窗口 2、4 个 item，block 故意倒序完成，返回仍须按输入顺序
        val result = director.windowedAsync(
            items = listOf(10, 20, 30, 40),
            window = 2,
        ) { it }
        assertEquals(listOf(10, 20, 30, 40), result)
    }

    @Test
    fun `不足窗口数时不设慢车道，全部并发`() = runBlocking {
        val director = WerewolfDirector(emptyMap())
        // items.size(2) <= window(3)，即使指定 slowLane 也退化为普通并发，返回顺序不变
        val result = director.windowedAsync(
            items = listOf(7, 8),
            window = 3,
            slowLane = 7,
        ) { it }
        assertEquals(listOf(7, 8), result)
        assertTrue(result.size == 2)
    }
}
