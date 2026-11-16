package com.feature.werewolf.engine

import com.core.ai.entity.IAI
import com.core.ai.model.ChatMessage
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 编排层的测试。
 *
 * 这一层的 bug 不会抛异常，只会让 AI「表现得很怪」——所以每条调度原则都要单独断一次：
 * 发言是否真的串行、投票是否真的互相看不到、夜聊有没有漏进好人上下文。
 * 全部用假 IAI，不联网。
 */
class WerewolfDirectorTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry(id = "p$it", name = "玩家$it") }

    /** 3 狼 9 人局：1、2、3 狼；4 预言家；5 女巫；6 猎人；7、8、9 平民。 */
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

    // ------------------------------------------------------------------
    // 假模型
    // ------------------------------------------------------------------

    private data class Call(
        val messages: List<ChatMessage>,
        val systemPrompt: String?,
        val jsonMode: Boolean,
    ) {
        val userText: String get() = messages.joinToString("\n") { it.content }
    }

    /** 按剧本回话：jsonMode 时吐预设的 JSON，否则吐一句话。 */
    private class ScriptedAI(private val replies: List<String>) : IAI {
        val calls = mutableListOf<Call>()

        override suspend fun requestModel() = Unit
        override var modelSet: MutableSet<String> = mutableSetOf()
        override var brandName: String = "测试厂商"
        override var company: String = "测试"

        override suspend fun chat(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
        ): String {
            calls += Call(messages, systemPrompt, jsonMode)
            return replies.getOrNull(calls.lastIndex) ?: replies.last()
        }

        @Deprecated("测试用不到")
        override suspend fun chat(message: String, model: String): String =
            chat(listOf(ChatMessage.user(message)), model)
    }

    /**
     * 会自己看说明的假模型：从 system prompt 的「只能从 X 号、Y 号 中选」里取第一个候选。
     *
     * 这样整局都能跑出合法动作，不需要为每个阶段手写剧本——用来验证编排能收敛。
     */
    private class AutoAI : IAI {
        val calls = mutableListOf<Call>()

        override suspend fun requestModel() = Unit
        override var modelSet: MutableSet<String> = mutableSetOf()
        override var brandName: String = "自动"
        override var company: String = "自动"

        override suspend fun chat(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
        ): String {
            calls += Call(messages, systemPrompt, jsonMode)
            val system = systemPrompt.orEmpty()
            if (!jsonMode) return "${seatOf(system)} 号觉得应该谨慎。"

            // 女巫的说明书里有 heal 字段，单独处理成「不用药」，永远合法
            if (system.contains("\"heal\"")) {
                return """{"heal": false, "poison": null, "reason": "药先留着。"}"""
            }
            val target = firstCandidate(system) ?: return "我还没想好"
            return """{"target": $target, "reason": "我跟票。"}"""
        }

        private fun seatOf(system: String): Int =
            Regex("""你是 (\d+) 号玩家""").find(system)?.groupValues?.get(1)?.toInt() ?: 0

        private fun firstCandidate(system: String): Int? {
            val block = Regex("""只能从 ([^。]+) 中选""").find(system)?.groupValues?.get(1) ?: return null
            return Regex("""(\d+) 号""").find(block)?.groupValues?.get(1)?.toInt()
        }

        @Deprecated("测试用不到")
        override suspend fun chat(message: String, model: String): String =
            chat(listOf(ChatMessage.user(message)), model)
    }

    /** 永远输出垃圾，用来验证兜底。 */
    private class GarbageAI : IAI {
        var callCount = 0

        override suspend fun requestModel() = Unit
        override var modelSet: MutableSet<String> = mutableSetOf()
        override var brandName: String = "胡说"
        override var company: String = "胡说"

        override suspend fun chat(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
        ): String {
            callCount++
            return "我觉得大家都挺可疑的。"
        }

        @Deprecated("测试用不到")
        override suspend fun chat(message: String, model: String): String =
            chat(listOf(ChatMessage.user(message)), model)
    }

    private fun agentsOf(vararg pairs: Pair<Int, IAI>): Map<Int, SeatAgent> =
        pairs.associate { (seat, ai) -> seat to SeatAgent(ai = ai, model = "test-model") }

    // ------------------------------------------------------------------
    // 夜晚：并发 + 多数决
    // ------------------------------------------------------------------

    @Test
    fun `狼人并发出刀，多数决生效`() = runBlocking {
        val wolfA = ScriptedAI(listOf("""{"target": 7, "reason": "7 号话少。"}"""))
        val wolfB = ScriptedAI(listOf("""{"target": 7, "reason": "我也觉得是他。"}"""))
        val wolfC = ScriptedAI(listOf("""{"target": 8, "reason": "8 号太急了。"}"""))
        val director = WerewolfDirector(agentsOf(1 to wolfA, 2 to wolfB, 3 to wolfC))

        // 从下刀阶段起手：这一段验的是「并发 + 多数决」，商议另有专门用例
        val result = director.nightWolf(WerewolfTable(standard9().beginWolfVote()))

        assertEquals("两票对一票，7 号被刀", 7, result.game.tonightVictim)
        assertEquals(Phase.NIGHT_SEER, result.game.phase)
    }

    @Test
    fun `狼人平票会加商一轮，第二轮定下目标`() = runBlocking {
        // 第一轮 7/8/9 各一票 → 平票；第二轮 7/7/8 → 定 7 号
        val director = WerewolfDirector(
            agentsOf(
                1 to ScriptedAI(listOf("""{"target": 7}""", """{"target": 7}""")),
                2 to ScriptedAI(listOf("""{"target": 8}""", """{"target": 7}""")),
                3 to ScriptedAI(listOf("""{"target": 9}""", """{"target": 8}""")),
            )
        )

        val result = director.nightWolf(WerewolfTable(standard9().beginWolfVote()))

        assertEquals(2, result.game.wolfVoteRound)
        assertEquals(7, result.game.tonightVictim)
        assertNotEquals(Phase.NIGHT_WOLF, result.game.phase)
    }

    @Test
    fun `狼人商议按座位串行，且只给狼人看`() = runBlocking {
        val director = WerewolfDirector(
            agentsOf(1 to AutoAI(), 2 to AutoAI(), 3 to AutoAI())
        )

        val result = director.nightWolfChat(WerewolfTable(standard9()))

        // 商议完全身就该自己去定刀，不能停在商议阶段——那会让整局卡在这里
        assertEquals("商议完自动进入下刀", Phase.NIGHT_WOLF, result.game.phase)
        assertEquals(1, result.game.wolfVoteRound)

        val wolves = result.transcriptFor(1).filter { it.kind == MessageKind.WOLF_CHAT }
        assertEquals("三只狼各说一句", listOf(1, 2, 3), wolves.map { it.seat })
        assertEquals("好人一条都看不到", 0, result.transcriptFor(4).count { it.kind == MessageKind.WOLF_CHAT })
    }

    @Test
    fun `商议是串行的，后说的狼能看见队友已经说了什么`() = runBlocking {
        // 这条锁的是「商量」这件事本身：并发收集的话每只狼都在对着空白表态，
        // 各说各的，等于没商量，第一夜就必然分票
        val seat1 = ScriptedAI(listOf("我倾向 7 号。"))
        val seat2 = ScriptedAI(listOf("我跟 1 号，刀 7 号。"))
        val seat3 = ScriptedAI(listOf("那就 7 号。"))
        val director = WerewolfDirector(agentsOf(1 to seat1, 2 to seat2, 3 to seat3))

        director.nightWolfChat(WerewolfTable(standard9()))

        assertTrue("1 号先开口，没有前文", seat1.calls.single().userText.contains("现在只有你们狼人醒着"))
        assertTrue("2 号必须看到 1 号说了什么", seat2.calls.single().userText.contains("我倾向 7 号。"))
        assertTrue("3 号要看到前两位", seat3.calls.single().userText.contains("我跟 1 号，刀 7 号。"))
    }

    @Test
    fun `商议阶段不改局面，刀口只在定刀之后才有`() = runBlocking {
        val director = WerewolfDirector(
            agentsOf(1 to AutoAI(), 2 to AutoAI(), 3 to AutoAI())
        )

        val afterTalk = director.nightWolfChat(WerewolfTable(standard9()))

        assertNull("只是说话，不该产生刀口", afterTalk.game.tonightVictim)
        assertTrue("也不该产生死讯", afterTalk.game.history.none { it is GameEvent.Dawn })
    }

    // ------------------------------------------------------------------
    // 夜晚：预言家 / 女巫
    // ------------------------------------------------------------------

    @Test
    fun `预言家查验结果只进私有记录`() = runBlocking {
        val seerAI = ScriptedAI(listOf("""{"target": 2, "reason": "2 号像狼。"}"""))
        // 手动把局面推到预言家阶段
        val game = standard9().copy(phase = Phase.NIGHT_SEER, tonightVictim = 7)
        val director = WerewolfDirector(agentsOf(4 to seerAI))

        val result = director.nightSeer(WerewolfTable(game))

        assertEquals(1, result.game.seerChecks.size)
        assertTrue(result.game.seerChecks.single().isWerewolf)
        assertEquals("查验记录不该出现在公开消息里", 0, result.publicTranscript.count { it.kind == MessageKind.NIGHT_ACT })
        assertEquals(1, result.transcriptFor(4).count { it.kind == MessageKind.NIGHT_ACT })
    }

    @Test
    fun `女巫用药后直接天亮，且用药细节不外泄`() = runBlocking {
        val witchAI = ScriptedAI(listOf("""{"heal": true, "poison": null, "reason": "7 号是好人。"}"""))
        val game = standard9().copy(phase = Phase.NIGHT_WITCH, tonightVictim = 7)
        val director = WerewolfDirector(agentsOf(5 to witchAI))

        val result = director.nightWitch(WerewolfTable(game))

        assertEquals("用了解药，7 号活着", true, result.game.playerAt(7).alive)
        assertEquals(Phase.DAY_DISCUSSION, result.game.phase)
        assertEquals(1, result.transcriptFor(5).count { it.kind == MessageKind.NIGHT_ACT })
        assertEquals(0, result.publicTranscript.count { it.kind == MessageKind.NIGHT_ACT })
    }

    @Test
    fun `女巫不用药时天亮播报会公布死者座位`() = runBlocking {
        val witchAI = ScriptedAI(listOf("""{"heal": false, "poison": null, "reason": "再等等。"}"""))
        val game = standard9().copy(phase = Phase.NIGHT_WITCH, tonightVictim = 7)
        val director = WerewolfDirector(agentsOf(5 to witchAI))

        val result = director.nightWitch(WerewolfTable(game))

        assertEquals(false, result.game.playerAt(7).alive)
        val dawn = result.publicTranscript.last { it.kind == MessageKind.DIVINE }
        assertTrue("播报要说出 7 号出局", dawn.text.contains("7 号"))
        assertFalse("但不能说死因", dawn.text.contains("狼人袭击"))
    }

    // ------------------------------------------------------------------
    // 白天：串行发言
    // ------------------------------------------------------------------

    @Test
    fun `白天发言严格串行，后一位能看到前一位`() = runBlocking {
        val ais = (1..9).associateWith { _ -> AutoAI() as IAI }
        val director = WerewolfDirector(ais.mapValues { SeatAgent(it.value, "test-model") })

        val result = director.discussion(WerewolfTable(standard9().copy(phase = Phase.DAY_DISCUSSION)))

        val speeches = result.transcript.filter { it.kind == MessageKind.SPEECH }
        assertEquals("存活 9 人，一人一句", 9, speeches.size)
        assertEquals("按座位顺序发言", (1..9).toList(), speeches.map { it.seat })

        // 4 号的请求里必须已经包含 3 号说过的话；同时不能包含 5 号（还没轮到）
        val fourthCall = (ais[4] as AutoAI).calls.single()
        assertTrue(fourthCall.userText.contains("3 号："))
        assertFalse("不能提前看到后面的人", fourthCall.userText.contains("5 号："))
    }

    @Test
    fun `每说完一句就往外推一次，不是攒完一圈才给`() = runBlocking {
        // 一圈九个人要等一分多钟。全攒到阶段结束才推，中间界面是静止的，
        // 看着像卡死；这个用例锁住的是「说完一个就推一个」
        val ais = (1..9).associateWith { _ -> AutoAI() as IAI }
        val pushed = mutableListOf<Int>()
        val director = WerewolfDirector(
            agents = ais.mapValues { SeatAgent(it.value, "test-model") },
            onTranscript = { table -> pushed += table.transcript.count { it.kind == MessageKind.SPEECH } },
        )

        director.discussion(WerewolfTable(standard9().copy(phase = Phase.DAY_DISCUSSION)))

        assertEquals("九个人应该推九次", 9, pushed.size)
        assertEquals("每次推的消息条数逐条加一", (1..9).toList(), pushed)
    }

    // ------------------------------------------------------------------
    // 白天：并发投票
    // ------------------------------------------------------------------

    @Test
    fun `投票并发收集，谁都看不到别人投了谁`() = runBlocking {
        val ais = (1..9).associateWith { _ -> AutoAI() as IAI }
        val director = WerewolfDirector(ais.mapValues { SeatAgent(it.value, "test-model") })

        val game = standard9().copy(phase = Phase.DAY_VOTE)
        val result = director.vote(WerewolfTable(game))

        assertNotEquals("投票应该结束并推进阶段", Phase.DAY_VOTE, result.game.phase)
        // 每条投票请求里都不该出现「某某：我投X号」这种别人的票
        ais.forEach { (seat, ai) ->
            val text = (ai as AutoAI).calls.single().userText
            assertFalse("$seat 号不该看到别人的投票", text.contains("我投"))
        }
    }

    @Test
    fun `投票会公布每个人的票`() = runBlocking {
        val ais = (1..9).associateWith { _ -> AutoAI() as IAI }
        val director = WerewolfDirector(ais.mapValues { SeatAgent(it.value, "test-model") })

        val result = director.vote(WerewolfTable(standard9().copy(phase = Phase.DAY_VOTE)))

        assertEquals(9, result.publicTranscript.count { it.kind == MessageKind.VOTE })
    }

    @Test
    fun `守护局的整局能跑通，守卫的话只有他自己能看到`() = runBlocking {
        // 10 人守卫局：7 号是守卫
        val game = WerewolfGame.withRoles(
            config = WerewolfConfig(composition = WerewolfConfig.Guard10),
            roster = roster(10),
            roles = listOf(
                WerewolfRole.WEREWOLF,
                WerewolfRole.WEREWOLF,
                WerewolfRole.WEREWOLF,
                WerewolfRole.SEER,
                WerewolfRole.WITCH,
                WerewolfRole.HUNTER,
                WerewolfRole.GUARD,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
            ),
        )
        val agents = game.players.associate { it.seat to SeatAgent(AutoAI(), "test-model") }

        val result = WerewolfDirector(agents, random = Random(7)).play(WerewolfTable(game))

        assertTrue("守护局也要能打完", result.game.isFinished)
        assertEquals("第一夜从守卫开始", Phase.NIGHT_GUARD, result.transcript.first().phase)
        val guardMessage = result.transcript.first()
        assertEquals("守卫的行踪只能是私有消息", setOf(7), guardMessage.audience)
        assertTrue(
            "别人看不到守卫守了谁",
            result.transcriptFor(1).none { it.phase == Phase.NIGHT_GUARD },
        )
        // 每一夜都要有人守，否则守护毫无意义
        assertTrue("应该有守中刀口或有守人的记录", result.game.nights.any { it.guarded != null })
    }

    // ------------------------------------------------------------------
    // 兜底
    // ------------------------------------------------------------------

    @Test
    fun `模型两次都输出垃圾时走兜底，游戏不卡死`() = runBlocking {
        val garbage = GarbageAI()
        val director = WerewolfDirector(
            agentsOf(1 to garbage, 2 to garbage, 3 to garbage)
        )

        val result = director.nightWolf(WerewolfTable(standard9().beginWolfVote()))

        assertNotEquals("必须离开狼人阶段", Phase.NIGHT_WOLF, result.game.phase)
        // 每个狼人至少被问了一次，格式不对会重试
        assertTrue(garbage.callCount >= 3)
        // 平票会加商，所以消息数至少一轮 3 条
        assertTrue(result.transcript.count { it.kind == MessageKind.WOLF_CHAT } >= 3)
    }

    // ------------------------------------------------------------------
    // 整局
    // ------------------------------------------------------------------

    @Test
    fun `整局能从头跑到分出胜负`() = runBlocking {
        repeat(10) { seed ->
            val game = WerewolfGame.deal(WerewolfConfig(), roster(8), Random(seed))
            val agents = game.players.associate { it.seat to SeatAgent(AutoAI(), "test-model") }
            val director = WerewolfDirector(agents, random = Random(seed + 100))

            val result = director.play(WerewolfTable(game))

            assertTrue("种子 $seed 没有结束", result.game.isFinished)
            assertNotNull("种子 $seed 没有胜方", result.game.winner)
            assertTrue(
                "最后一条播报应该是游戏结束",
                result.publicTranscript.last { it.kind == MessageKind.DIVINE }.text.contains("游戏结束"),
            )
            assertTrue("应该有人发过言", result.publicTranscript.any { it.kind == MessageKind.SPEECH })
            assertTrue("应该投过票", result.publicTranscript.any { it.kind == MessageKind.VOTE })
        }
    }

    @Test
    fun `没有配置模型的座位会明确报错，而不是静默跳过`() = runBlocking {
        val director = WerewolfDirector(agentsOf(1 to ScriptedAI(listOf("""{"target": 7}"""))))

        val error = runCatching {
            director.nightWolf(WerewolfTable(standard9().beginWolfVote()))
        }.exceptionOrNull()

        assertNotNull("缺模型必须报错", error)
        assertTrue(error!!.message.orEmpty().contains("没有配置模型"))
    }
}
