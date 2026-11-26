package com.feature.werewolf.engine

import com.core.ai.entity.IAI
import com.core.ai.model.ChatMessage
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.SELF_PLAYER_ID
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfSpeechLength
import com.feature.werewolf.game.WerewolfTurnLimit
import com.feature.werewolf.engine.persistence.PacingSnapshot
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

    private data class Saved(
        val id: String,
        val table: WerewolfTable,
        val createdAt: Long,
        val customPrompt: String? = null,
        val turnLimit: WerewolfTurnLimit = WerewolfTurnLimit.STANDARD,
        val thinkingOff: Set<Int> = emptySet(),
        val speechLength: WerewolfSpeechLength = WerewolfSpeechLength.STANDARD,
    )

    /** 内存版存档，行为跟真库一致，但不碰 Room。 */
    private class FakeStore : WerewolfGameStore {
        val saves = mutableListOf<Saved>()

        override suspend fun save(
            id: String,
            table: WerewolfTable,
            createdAt: Long,
            customPrompt: String?,
            turnLimit: WerewolfTurnLimit,
            thinkingOff: Set<Int>,
            speechLength: WerewolfSpeechLength,
        ) {
            saves += Saved(id, table, createdAt, customPrompt, turnLimit, thinkingOff, speechLength)
        }

        override suspend fun load(id: String): WerewolfTable? =
            saves.lastOrNull { it.id == id }?.table

        override suspend fun customPrompt(id: String): String? =
            saves.lastOrNull { it.id == id }?.customPrompt

        override suspend fun pacing(id: String): PacingSnapshot? =
            saves.lastOrNull { it.id == id }?.let {
                PacingSnapshot(limit = it.turnLimit, thinkingOff = it.thinkingOff)
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
                    playerCount = last.game.players.size,
                    withHuman = last.game.players.any { it.id == SELF_PLAYER_ID },
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
            webSearch: Boolean,
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
            webSearch: Boolean,
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
    fun `这一局的玩家设定跟着每一档写进存档`() = runBlocking {
        // 复盘要能回答「这局是按什么设定打的」：设置页只保留最新那份，
        // 改过之后旧局就再也查不到了，所以落档时必须自己记一份
        val store = FakeStore()
        val table = WerewolfGame.deal(WerewolfConfig(), roster(8))

        val controller = controller(store)
        controller.applyCustomPrompt("  说话短一点，别写小作文  ")
        // 首尾空白要去掉：提示词是拼出来的一整段，两端空行只会在请求里浪费 token
        assertEquals("说话短一点，别写小作文", controller.state.value.customPrompt)
        controller.start("g1", table)

        assertEquals("说话短一点，别写小作文", store.saves.single().customPrompt)
        assertEquals("说话短一点，别写小作文", store.customPrompt("g1"))
    }

    @Test
    fun `没写设定时存档里就是 null，不写空串`() = runBlocking {
        val store = FakeStore()
        controller(store).start("g1", WerewolfGame.deal(WerewolfConfig(), roster(8)))

        assertNull(store.saves.single().customPrompt)
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
        assertTrue("错误提示应包含原因：${state.error}", state.error!!.contains("网络"))
        assertTrue("错误提示应标明出问题的好友座位与名字：${state.error}", state.error!!.contains("号 玩家"))
        assertTrue("错误提示应标明出问题的模型：${state.error}", state.error!!.contains("test-model"))
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
    fun `删另一局不会动到手上的这一局`() = runBlocking {
        val store = FakeStore()
        val c = controller(store)
        c.start("g1", WerewolfGame.deal(WerewolfConfig(), roster(8)))
        c.start("g2", WerewolfGame.deal(WerewolfConfig(), roster(8)))

        c.discard("g1")

        assertEquals("只该删 g1", listOf("g2"), store.saves.map { it.id })
        assertNotNull("当前局面不能被清掉", c.state.value.table)
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

    @Test
    fun `阶段中途退出续档后，已经说过的话不该重来一遍`() = runBlocking {
        // 真机反馈：退出再进去「像这一轮重新开始」。根因是落档粒度=阶段，
        // 而讨论/发言是一个多座位阶段：中途退出时 flush 存下的是 phase 仍在进行、
        // transcript 已经有前几句的局面。续档从同一阶段开头重跑，前几个人又说一遍。
        // 这里验证：阶段函数必须跳过本阶段已经发言过的座位。
        val store = FakeStore()
        val game = WerewolfGame.deal(WerewolfConfig(), roster(8))
            .copy(phase = Phase.DAY_DISCUSSION)
        // 假设 1、2 号已经发过言，3～8 还没说。真实 flush 时 markSpoken 会把进度
        // 写进 game（随快照落档），这里照着那个存档状态构造。
        val midTable = WerewolfTable(
            game = game.copy(
                spokenPhaseKey = game.currentPhaseKey,
                spokenSeats = setOf(1, 2),
            ),
            transcript = listOf(
                WerewolfMessage(1, Phase.DAY_DISCUSSION, 1, MessageKind.SPEECH, "1 号已经说过"),
                WerewolfMessage(1, Phase.DAY_DISCUSSION, 2, MessageKind.SPEECH, "2 号已经说过"),
            ),
        )
        val c = controller(store)

        c.start("g1", midTable)
        c.play()

        val discussionSpeech = c.state.value.table!!.transcript.filter {
            it.day == 1 && it.phase == Phase.DAY_DISCUSSION && it.kind == MessageKind.SPEECH
        }
        val seat1Count = discussionSpeech.count { it.seat == 1 }
        val seat2Count = discussionSpeech.count { it.seat == 2 }
        assertEquals("1 号只该有一句，不该重说", 1, seat1Count)
        assertEquals("2 号只该有一句，不该重说", 1, seat2Count)
    }

    // ------------------------------------------------------------------
    // 人类自爆
    // ------------------------------------------------------------------

    @Test
    fun `人类点自爆：当场落档，局面直接进黑夜`() = runBlocking {
        val store = FakeStore()
        val dealt = WerewolfGame.deal(WerewolfConfig(), roster(8))
        val wolf = dealt.wolves().first().seat
        val c = controller(store)
        c.start("g1", dealt.copy(day = 1, phase = Phase.DAY_DISCUSSION))

        assertTrue(c.explodeNow(wolf))

        val table = c.state.value.table!!
        assertFalse("自爆者已经出局", table.game.playerAt(wolf).alive)
        assertEquals("白天就此结束", Phase.NIGHT_WOLF_CHAT, table.game.phase)
        assertTrue(
            "牌桌上要有他自己那一句，不能是空气泡",
            table.transcript.any { it.seat == wolf && it.kind == MessageKind.SPEECH && it.text == I_EXPLODED },
        )
        assertTrue(
            "自爆的公开播报也要补上",
            table.transcript.any { it.kind == MessageKind.DIVINE && it.text.contains("自爆") },
        )
        // 这一下不能撤销，必须立刻落档：用户按完就杀进程也不该回到自爆前
        assertEquals("start 一档 + 自爆一档", 2, store.saves.size)
        assertEquals(Phase.NIGHT_WOLF_CHAT, store.saves.last().table.game.phase)
    }

    @Test
    fun `不该自爆的时候叫它，局面和存档一个字都不动`() = runBlocking {
        val store = FakeStore()
        val dealt = WerewolfGame.deal(WerewolfConfig(), roster(8))
        val wolf = dealt.wolves().first().seat
        val good = dealt.players.first { !it.role.isWerewolf }.seat
        val c = controller(store)
        // 开局是夜里：夜里没有「打断白天」这回事
        c.start("g1", dealt)

        assertFalse("夜里不能自爆", c.explodeNow(wolf))
        assertFalse("好人也不能自爆", c.explodeNow(good))
        assertEquals("没落档，说明局面没被改过", 1, store.saves.size)
    }
}
