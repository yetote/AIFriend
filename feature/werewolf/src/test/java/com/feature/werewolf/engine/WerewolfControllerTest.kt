package com.feature.werewolf.engine

import com.core.ai.entity.IAI
import com.core.ai.model.ChatMessage
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.store.WerewolfGameStore
import com.feature.werewolf.store.WerewolfGameSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对局控制器的测试。
 *
 * 两个最容易出事的地方：
 * 1. **出错时局面丢了** —— 网络一断就把整局清空，用户回来什么都找不到；
 * 2. **续档读回来的不是原来那局** —— 读回来了但少了点什么，接着打就会出怪事。
 * 两者都不会抛异常，只能靠断言。
 */
class WerewolfControllerTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry("p$it", "玩家$it") }

    private data class Saved(val id: String, val table: WerewolfTable, val createdAt: Long)

    /** 内存版存档，行为跟真库一致，但不碰 Room。 */
    private class FakeStore : WerewolfGameStore {
        val saves = mutableListOf<Saved>()

        override suspend fun save(id: String, table: WerewolfTable, createdAt: Long) {
            saves += Saved(id, table, createdAt)
        }

        override suspend fun load(id: String): WerewolfTable? =
            saves.lastOrNull { it.id == id }?.table

        override suspend fun latestUnfinished(): Pair<String, WerewolfTable>? {
            val last = saves.lastOrNull { !it.table.game.isFinished } ?: return null
            return last.id to last.table
        }

        override suspend fun summaries(): List<WerewolfGameSummary> =
            saves.groupBy { it.id }.map { (id, list) ->
                val last = list.last().table
                WerewolfGameSummary(
                    id = id,
                    createdAt = list.first().createdAt,
                    updatedAt = list.last().createdAt,
                    finished = last.game.isFinished,
                    day = last.game.day,
                    phase = last.game.phase.name,
                    winner = last.game.winner?.name,
                )
            }

        override suspend fun delete(id: String) {
            saves.removeAll { it.id == id }
        }
    }

    private class FixedAI : IAI {
        override suspend fun requestModel() = Unit
        override var modelSet: MutableSet<String> = mutableSetOf()
        override var brandName: String = "测试"
        override var company: String = "测试"

        override suspend fun chat(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
        ): String = if (jsonMode) """{"target": 1}""" else "我先听听大家怎么说。"

        @Deprecated("测试用不到")
        override suspend fun chat(message: String, model: String): String =
            chat(listOf(ChatMessage.user(message)), model)
    }

    /** 模拟网络断了：传输层异常会一路抛上来，`decide()` 不会吞。 */
    private class BrokenAI : IAI {
        override suspend fun requestModel() = Unit
        override var modelSet: MutableSet<String> = mutableSetOf()
        override var brandName: String = "断网"
        override var company: String = "断网"

        override suspend fun chat(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
        ): String = throw IllegalStateException("网络连接失败")

        @Deprecated("测试用不到")
        override suspend fun chat(message: String, model: String): String =
            chat(listOf(ChatMessage.user(message)), model)
    }

    private fun controller(store: FakeStore, ai: IAI = FixedAI()) = WerewolfController(
        store = store,
        agentsFor = { SeatAgent(ai, "test-model") },
        clock = { 1000L },
    )

    // ------------------------------------------------------------------

    @Test
    fun `开局先落档，再开始打`() = runBlocking {
        val store = FakeStore()
        val table = WerewolfGame.deal(WerewolfConfig(), roster(8))

        controller(store).start("g1", table)

        assertEquals(1, store.saves.size)
        assertFalse("刚开局当然没结束", store.saves.single().table.game.isFinished)
        assertEquals("开局时间要落下来", 1000L, store.saves.single().createdAt)
    }

    @Test
    fun `整局打完，最后一档是已结束的局面`() = runBlocking {
        val store = FakeStore()
        val c = controller(store)

        c.start("g1", WerewolfGame.deal(WerewolfConfig(), roster(8)))
        c.play()

        val last = store.saves.last()
        assertTrue("最后存下来的必须是结束态", last.table.game.isFinished)
        assertNotNull(last.table.game.winner)
        assertTrue(c.state.value.finished)
        assertFalse(c.state.value.running)
        assertNull(c.state.value.error)
    }

    @Test
    fun `每推进一个阶段就落一次档`() = runBlocking {
        val store = FakeStore()
        val c = controller(store)

        c.start("g1", WerewolfGame.deal(WerewolfConfig(), roster(8)))
        c.play()

        // 一局至少几个夜晚和白天，档数应该明显多于「只存了开局和结束」
        assertTrue("实际存了 ${store.saves.size} 次", store.saves.size > 4)
    }

    @Test
    fun `网络出错时局面不丢，可以稍后继续`() = runBlocking {
        val store = FakeStore()
        val c = controller(store, BrokenAI())

        c.start("g1", WerewolfGame.deal(WerewolfConfig(), roster(8)))
        c.play()

        val state = c.state.value
        assertNotNull("要给出错误提示", state.error)
        assertTrue(state.error!!.contains("网络"))
        assertNotNull("局面不能丢，否则玩家回来一无所有", state.table)
        assertFalse(state.running)
        assertFalse("没打完不能标成结束", state.finished)
        assertTrue("开局那一档还在", store.saves.isNotEmpty())
    }

    @Test
    fun `续档读回来的就是原来那局`() = runBlocking {
        val store = FakeStore()
        val table = WerewolfGame.deal(WerewolfConfig(), roster(8))

        controller(store).start("g1", table)

        val reopened = controller(store)
        assertTrue(reopened.resume("g1"))
        assertEquals(WerewolfTable(table), reopened.state.value.table)
        assertEquals("续档时开局时间要从存档读，不能用当前时间", 1000L, store.saves.single().createdAt)
    }

    @Test
    fun `没有存档时续档返回 false`() = runBlocking {
        assertFalse(controller(FakeStore()).resume("不存在"))
    }

    @Test
    fun `已结束的局不会被重新推进`() = runBlocking {
        val store = FakeStore()
        val c = controller(store)
        c.start("g1", WerewolfGame.deal(WerewolfConfig(), roster(8)))
        c.play()

        val finishedTable = store.saves.last().table
        val before = store.saves.size

        val second = controller(store)
        second.start("g2", finishedTable)
        second.play()

        assertEquals("结束的局不该再跑一遍", before + 1, store.saves.size)
        assertTrue(second.state.value.finished)
    }

    @Test
    fun `放弃会删掉存档`() = runBlocking {
        val store = FakeStore()
        val c = controller(store)
        c.start("g1", WerewolfGame.deal(WerewolfConfig(), roster(8)))

        c.abandon()

        assertTrue("存档应该被删掉", store.saves.isEmpty())
        assertNull(c.state.value.table)
    }
}
