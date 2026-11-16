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
        assertEquals("第 2 夜 · 狼人商议 1/3", WerewolfProgress(2, Phase.NIGHT_WOLF, 1, 3).label)
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

    // ------------------------------------------------------------------
    // 夜聊：并发的，报完再报完成；平票要说明
    // ------------------------------------------------------------------

    @Test
    fun `狼人商议先报准备中，再报完成`() = runBlocking {
        val events = mutableListOf<WerewolfProgress>()
        val agents = mapOf(
            1 to SeatAgent(ConstAI("""{"target": 7}"""), "test"),
            2 to SeatAgent(ConstAI("""{"target": 7}"""), "test"),
            3 to SeatAgent(ConstAI("""{"target": 7}"""), "test"),
        )
        val director = WerewolfDirector(agents, onProgress = { events += it })

        director.nightWolf(WerewolfTable(standard9()))

        assertEquals("一轮两条：开始 + 完成", 2, events.size)
        assertEquals(0, events[0].step)
        assertEquals(3, events[0].total)
        assertEquals(3, events[1].step)
        assertNull("没平票就不该有提示", events[0].note)
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

        director.nightWolf(WerewolfTable(standard9()))

        // 第一轮 0/3、3/3，第二轮 0/3（带平票提示）、3/3
        assertEquals(4, events.size)
        assertNull(events[0].note)
        assertEquals("平票，重新商定", events[2].note)
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
