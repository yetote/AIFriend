package com.feature.werewolf.engine

import com.core.ai.entity.IAI
import com.core.ai.model.ChatMessage
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 进度反馈的测试。
 *
 * 进度本身不影响规则，但它决定「玩家会不会以为卡死了」。断的是两件事：
 * 步进的顺序对不对、平票重来时有没有提示。
 */
class WerewolfProgressTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry("p$it", "玩家$it") }

    private fun standard9(): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(composition = WerewolfConfig.Standard9),
        roster = roster(9),
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.SEER,
            WerewolfRole.WITCH,
            WerewolfRole.HUNTER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
        ),
    )

    /** 固定回复的假模型：jsonMode 时吐预设 JSON，否则吐一句话。 */
    private class ConstAI(
        private val json: String = """{"target": 1}""",
        private val speech: String = "我先听听大家怎么说。",
    ) : IAI {
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
        ): String = if (jsonMode) json else speech

        @Deprecated("测试用不到")
        override suspend fun chat(message: String, model: String): String =
            chat(listOf(ChatMessage.user(message)), model)
    }

    // ------------------------------------------------------------------
    // 文案
    // ------------------------------------------------------------------

    @Test
    fun `标签按阶段和昼夜拼接`() {
        assertEquals("第 2 夜 · 狼人商议 1/3", WerewolfProgress(2, Phase.NIGHT_WOLF_CHAT, 1, 3).label)
        // 商议和下刀是两个阶段，标签不能都叫「商议」，否则玩家分不清现在是在说话还是在动手
        assertEquals("第 2 夜 · 狼人下刀 1/3", WerewolfProgress(2, Phase.NIGHT_WOLF, 1, 3).label)
        assertEquals("第 2 天 · 发言 3/8", WerewolfProgress(2, Phase.DAY_DISCUSSION, 3, 8).label)
        assertEquals("第 1 夜 · 预言家查验", WerewolfProgress(1, Phase.NIGHT_SEER, 1, 1).label)
        assertEquals("第 3 天 · 投票 准备中", WerewolfProgress(3, Phase.DAY_VOTE, 0, 9).label)
        // 猎人开枪算白天：天亮被刀、白天被票出，都会在天亮的播报里出现
        assertTrue(WerewolfProgress(1, Phase.HUNTER_SHOOT, 1, 1).label.startsWith("第 1 天"))
    }

    // ------------------------------------------------------------------
    // 发言：必须按座位报，一个都不能跳
    // ------------------------------------------------------------------

    @Test
    fun `白天发言按座位顺序报进度`() = runBlocking {
        val events = mutableListOf<WerewolfProgress>()
        val agents = (1..9).associate { it to SeatAgent(ConstAI(), "test") }
        val director = WerewolfDirector(agents, onProgress = { events += it })

        director.discussion(WerewolfTable(standard9().copy(phase = Phase.DAY_DISCUSSION)))

        assertEquals("9 个人各报一次", 9, events.size)
        assertEquals("座位顺序", (1..9).toList(), events.map { it.seat })
        assertEquals("步进从 1 到 9", (1..9).toList(), events.map { it.step })
        assertTrue("每一步都知道总数是 9", events.all { it.total == 9 })
    }

    @Test
    fun `谁在忙跟在顶栏那一行后面`() {
        // 顶栏本来就显示「第 1 天 · 投票 7/11」，把座位接在后面就够了，
        // 不用在底下另开一条
        val concurrent = WerewolfProgress(
            day = 1,
            phase = Phase.DAY_VOTE,
            step = 2,
            total = 11,
            seats = listOf(3, 5, 7),
        )
        assertEquals("第 1 天 · 投票 2/11 · 3、5、7 号", concurrent.label)
        // 不带座位的那版给夜里用
        assertEquals("第 1 天 · 投票 2/11", concurrent.plainLabel)

        // 串行阶段是当前这个座位
        val serial = WerewolfProgress(
            day = 1,
            phase = Phase.DAY_DISCUSSION,
            step = 1,
            total = 8,
            seat = 5,
        )
        assertEquals("第 1 天 · 发言 1/8 · 5 号", serial.label)

        assertEquals(
            "没人可报时就不该多这一截",
            "第 1 天 · 投票 2/11",
            concurrent.copy(seats = emptyList()).label,
        )
    }

    // ------------------------------------------------------------------
    // 夜聊：并发的，报完再报完成；平票要说明
    // ------------------------------------------------------------------

    @Test
    fun `狼人定刀先报准备中，再报完成`() = runBlocking {
        val events = mutableListOf<WerewolfProgress>()
        val agents = mapOf(
            1 to SeatAgent(ConstAI("""{"target": 7}"""), "test"),
            2 to SeatAgent(ConstAI("""{"target": 7}"""), "test"),
            3 to SeatAgent(ConstAI("""{"target": 7}"""), "test"),
        )
        val director = WerewolfDirector(agents, onProgress = { events += it })

        director.nightWolf(WerewolfTable(standard9().beginWolfVote()))

        // 并发阶段现在是「开始报一次 + 每回一个报一次 + 收齐报一次」，
        // 比原来的「开始 + 完成」细——界面能说出「现在是谁在定刀」
        assertEquals("开始时报准备中", 0, events.first().step)
        assertEquals(3, events.first().total)
        assertEquals("收齐时报满", 3, events.last().step)
        assertEquals(
            "进度只能往前，不能乱跳",
            events.map { it.step }.sorted(),
            events.map { it.step },
        )
        assertNull("没平票就不该有提示", events.first().note)
        assertTrue("要报出谁在忙", events.any { it.seats.isNotEmpty() })
    }

    @Test
    fun `商议按座位报进度，头狼先来`() = runBlocking {
        // 和定刀的「开始 + 完成」不同：商议是串行的，每只狼开口时报一次，
        // 界面才能显示「等 2 号说话」而不是干等一圈
        val events = mutableListOf<WerewolfProgress>()
        val agents = (1..3).associate { it to SeatAgent(ConstAI(), "test") }
        val director = WerewolfDirector(agents, onProgress = { events += it })

        val result = director.nightWolfChat(WerewolfTable(standard9()))

        val head = result.game.nightHead
        assertNotNull("每晚都要指定头狼", head)
        assertEquals("头狼先报进度", head, events.first().seat)
        assertEquals("其余按座位", listOf(1, 2, 3).filter { it != head }, events.drop(1).map { it.seat })
        assertTrue("都属于商议阶段", events.all { it.phase == Phase.NIGHT_WOLF_CHAT })
    }

    @Test
    fun `狼人平票时会提示重新商定`() = runBlocking {
        val events = mutableListOf<WerewolfProgress>()
        val agents = mapOf(
            1 to SeatAgent(ConstAI("""{"target": 7}"""), "test"),
            2 to SeatAgent(ConstAI("""{"target": 8}"""), "test"),
            3 to SeatAgent(ConstAI("""{"target": 9}"""), "test"),
        )
        val director = WerewolfDirector(agents, onProgress = { events += it })

        director.nightWolf(WerewolfTable(standard9().beginWolfVote()))

        // 一轮：定刀 开始 + 完成（2 条）→ 平票回到商议（3 条，按座位）
        // → 再定刀（2 条，开头那句带「平票，重新定刀」）
        // 事件条数随进度粒度变（现在每回一个报一次），所以断言的是**顺序和内容**：
        // 先定刀 → 平票回商议 → 再定刀，且两处提示都在
        assertNull(events[0].note)
        val chatIndex = events.indexOfFirst { it.phase == Phase.NIGHT_WOLF_CHAT }
        assertTrue("平票之后要回到商议", chatIndex > 0)
        assertEquals("平票，再商一次", events[chatIndex].note)
        val secondWolfIndex = events.indexOfLast {
            it.phase == Phase.NIGHT_WOLF && it.note == "平票，重新定刀"
        }
        assertTrue("再定刀时要说明是平票重来", secondWolfIndex > chatIndex)
    }

    // ------------------------------------------------------------------
    // 投票
    // ------------------------------------------------------------------

    @Test
    fun `投票也报进度，总数是存活人数`() = runBlocking {
        val events = mutableListOf<WerewolfProgress>()
        val agents = (1..9).associate { it to SeatAgent(ConstAI("""{"target": 1}"""), "test") }
        val director = WerewolfDirector(agents, onProgress = { events += it })

        director.vote(WerewolfTable(standard9().copy(phase = Phase.DAY_VOTE)))

        assertTrue(events.isNotEmpty())
        assertTrue("都属于投票阶段", events.all { it.phase == Phase.DAY_VOTE })
        assertEquals("开始时报准备中", 0, events.first().step)
        assertEquals("完成时报满", 9, events.last().step)
        assertEquals(9, events.last().total)
    }

    @Test
    fun `投票并发时每收齐一票就报一次，进度条不会干等`() = runBlocking {
        // 回归点：曾经投票用 map{async}.awaitAll()，只在两端报 0 和 9，
        // 中间 11 个人投完了界面还是「准备中」，看着像卡死。
        // 现在每收到一票报一次，step 必须严格 1、2、3……单调推进，不能乱序。
        val events = mutableListOf<WerewolfProgress>()
        val agents = (1..9).associate { it to SeatAgent(ConstAI("""{"target": 1}"""), "test") }
        val director = WerewolfDirector(agents, onProgress = { events += it })

        director.vote(WerewolfTable(standard9().copy(phase = Phase.DAY_VOTE)))

        val steps = events.map { it.step }
        assertEquals("0 起步", 0, steps.first())
        assertEquals("最后报满", 9, steps.last())
        assertEquals(
            "进度只能往前，不能乱跳",
            steps.sorted(),
            steps,
        )
        // 中间不能干等：9 个人投票，step 要一路推到 9，不能只有 0 和 9
        assertTrue("中途要有推进：$steps", steps.size >= 10)
        assertNull("没有平票就不该有提示", events.first().note)
        assertTrue(
            "要报出谁在忙",
            events.any { it.seats.isNotEmpty() },
        )
    }

    // ------------------------------------------------------------------
    // 整局
    // ------------------------------------------------------------------

    @Test
    fun `整局过程中进度持续更新，不会长时间停在同一个标签`() = runBlocking {
        val events = mutableListOf<WerewolfProgress>()
        val game = WerewolfGame.deal(WerewolfConfig(), roster(8))
        val agents = game.players.associate { it.seat to SeatAgent(ConstAI("""{"target": 1}"""), "test") }
        val director = WerewolfDirector(agents, onProgress = { events += it })

        director.play(WerewolfTable(game))

        assertTrue("一局下来应该有不少进度", events.size > 10)
        assertTrue("夜晚和白天都出现过", events.any { it.phase == Phase.NIGHT_WOLF } && events.any { it.phase == Phase.DAY_DISCUSSION })
        assertTrue("标签不会全是空串", events.all { it.label.isNotBlank() })
    }
}
