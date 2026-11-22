package com.feature.werewolf.engine

import com.core.ai.entity.IAI
import com.core.ai.model.ChatDelta
import com.core.ai.model.ChatMessage
import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

            // 上警报名：奇数座位报名，模拟「有人争警徽」的局面。
            // 全都不报的话整局都走「无人上警 → 没有警长」，警长相关路径永远跑不到
            if (system.contains("想竞选警长就把 target 填成你自己")) {
                val seat = seatOf(system)
                return if (seat % 2 == 1) {
                    """{"target": $seat, "reason": "我来带队。"}"""
                } else {
                    """{"target": null, "reason": "我不上警。"}"""
                }
            }
            // 警徽移交：一律撕掉。合法且不留后患，适合无脑自走
            if (system.contains("遗言里还要交代警徽")) {
                return """{"target": null, "reason": "来不及交代了。"}"""
            }
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

    /**
     * 卡住不回的模型。
     *
     * 时限这条路径只能靠真等来验证：档位最小也是 30 秒，所以测试里把
     * [WerewolfPacing.overrideMs] 压到百毫秒级，让「等不到」这件事真的发生。
     */
    private class HangingAI(private val sayFirst: String? = null) : IAI {
        override suspend fun requestModel() = Unit
        override var modelSet: MutableSet<String> = mutableSetOf()
        override var brandName: String = "卡住"
        override var company: String = "卡住"

        override suspend fun chat(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
        ): String {
            kotlinx.coroutines.delay(10_000)
            return "{}"
        }

        override fun chatStream(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
        ): kotlinx.coroutines.flow.Flow<ChatDelta> = kotlinx.coroutines.flow.flow {
            if (sayFirst != null) emit(ChatDelta(content = sayFirst))
            kotlinx.coroutines.delay(10_000)
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

    /**
     * 竞选发言里自爆 = 炸掉警徽。
     *
     * 这条以前被卡住了：自爆要求发言里点名一个活着的队友（救队友那条判据），
     * 而「吞警徽」本来就不需要救谁，于是这个正经花板子永远触发不了。
     */
    @Test
    fun `竞选发言里自爆会炸掉警徽`() = runBlocking {
        val exploder = ScriptedAI(listOf("【自爆】这个警徽今天谁也别想拿。"))
        val director = WerewolfDirector(agentsOf(1 to exploder))
        val game = standard9().copy(
            day = 1,
            phase = Phase.DAY_SHERIFF_SPEECH,
            sheriffCandidates = listOf(1),
            sheriffRunners = listOf(1),
        )

        val result = director.sheriffSpeech(WerewolfTable(game))

        assertTrue("选举要收尾，否则下次 dayOpening 会把游戏拽回上警", result.game.sheriffElectionDone)
        assertNull("本局没有警长", result.game.sheriffSeat)
        assertNotEquals("不能停在竞选发言", Phase.DAY_SHERIFF_SPEECH, result.game.phase)
        assertFalse("爆的狼已经出局", result.game.playerAt(1).alive)
    }

    @Test
    fun `狼人一致定刀只落一条记录`() = runBlocking {
        // 四只狼读同一份上下文、同一个模型，意见一致时连理由都几乎一样。
        // 逐条落账读出来就是同一句话重复四遍，观感像复读机——一致就只留一条。
        val director = WerewolfDirector(
            agentsOf(
                1 to ScriptedAI(listOf("""{"target": 7, "reason": "存在感低。"}""")),
                2 to ScriptedAI(listOf("""{"target": 7, "reason": "存在感低。"}""")),
                3 to ScriptedAI(listOf("""{"target": 7, "reason": "存在感低。"}""")),
            )
        )

        val result = director.nightWolf(WerewolfTable(standard9().beginWolfVote()))

        val chats = result.transcript.filter {
            it.kind == MessageKind.WOLF_CHAT && it.phase == Phase.NIGHT_WOLF
        }
        assertEquals("一致时不该复读三遍", 1, chats.size)
        assertTrue("要说清刀的是谁", chats.single().text.contains("7 号"))
        assertEquals(7, result.game.tonightVictim)
    }

    @Test
    fun `狼人分票时逐条记录谁指了谁`() = runBlocking {
        // 分票时「谁指了谁」是复盘要看的，不能合并
        val director = WerewolfDirector(
            agentsOf(
                1 to ScriptedAI(listOf("""{"target": 7, "reason": "7 号话少。"}""")),
                2 to ScriptedAI(listOf("""{"target": 8, "reason": "8 号太急。"}""")),
                3 to ScriptedAI(listOf("""{"target": 8, "reason": "8 号太急。"}""")),
            )
        )

        val result = director.nightWolf(WerewolfTable(standard9().beginWolfVote()))

        val chats = result.transcript.filter {
            it.kind == MessageKind.WOLF_CHAT && it.phase == Phase.NIGHT_WOLF
        }
        assertEquals("三只狼各落一条", listOf(1, 2, 3), chats.map { it.seat })
        assertEquals(8, result.game.tonightVictim)
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
    fun `狼人商议头狼先说，其余按座位，且只给狼人看`() = runBlocking {
        val director = WerewolfDirector(
            agentsOf(1 to AutoAI(), 2 to AutoAI(), 3 to AutoAI())
        )

        val result = director.nightWolfChat(WerewolfTable(standard9()))

        // 商议完全身就该自己去定刀，不能停在商议阶段——那会让整局卡在这里
        assertEquals("商议完自动进入下刀", Phase.NIGHT_WOLF, result.game.phase)
        assertEquals(1, result.game.wolfVoteRound)

        val head = result.game.nightHead
        assertNotNull("每晚都要指定头狼", head)
        val wolves = result.transcriptFor(1).filter { it.kind == MessageKind.WOLF_CHAT }
        assertEquals("三只狼各说一句", 3, wolves.size)
        assertEquals("头狼第一个发言", head, wolves.first().seat)
        assertEquals(
            "其余按座位",
            listOf(1, 2, 3).filter { it != head },
            wolves.drop(1).map { it.seat },
        )
        assertEquals("好人一条都看不到", 0, result.transcriptFor(4).count { it.kind == MessageKind.WOLF_CHAT })
    }

    @Test
    fun `商议是串行的：头狼先说，后面的狼看得见他的战术`() = runBlocking {
        // 这条锁的是「商量」这件事本身：并发收集的话每只狼都在对着空白表态，
        // 各说各的，等于没商量，第一夜就必然分票。
        // 顺序由随机选出的头狼决定，所以断言不能写死座位——要断言的是
        // 「第一个开口的人没有前文，后面的人都有前文」。
        val texts = mapOf(
            1 to "我倾向 7 号。",
            2 to "我跟 1 号，刀 7 号。",
            3 to "那就 7 号。",
        )
        val scripts = texts.mapValues { ScriptedAI(listOf(it.value)) }
        val director = WerewolfDirector(agentsOf(*scripts.map { it.key to it.value }.toTypedArray()))

        val result = director.nightWolfChat(WerewolfTable(standard9()))

        val head = result.game.nightHead!!
        val headText = texts.getValue(head)
        assertTrue(
            "头狼的提示词里要有指挥身份",
            scripts.getValue(head).calls.single().userText.contains("头狼"),
        )
        scripts.filterKeys { it != head }.forEach { (seat, ai) ->
            val text = ai.calls.single().userText
            assertTrue("$seat 号必须看到头狼说的战术", text.contains(headText))
        }
    }

    @Test
    fun `夜谈结束后战术被存下来，白天才排得上用`() = runBlocking {
        // 白天的上下文里没有夜聊，战术不存一份就等于没商量过。
        // 头狼是随机的，所以断言「存下来的就是头狼那一句」，不写死是谁。
        val lines = mapOf(
            1 to "今晚刀 7 号，白天 2 号跳预言家。",
            2 to "行，我跳预言家。",
            3 to "我冲锋。",
        )
        val director = WerewolfDirector(
            agentsOf(*lines.map { it.key to ScriptedAI(listOf(it.value)) }.toTypedArray())
        )

        val result = director.nightWolfChat(WerewolfTable(standard9()))

        val head = result.game.nightHead
        assertNotNull("每晚都要有头狼", head)
        assertEquals("存的是头狼那一句", lines.getValue(head!!), result.game.wolfPlan)
    }

    @Test
    fun `续档时沿用存档里的头狼`() = runBlocking {
        // 同一条夜里换人当头狼，而 transcript 里已经躺着旧头狼的发言，
        // 场上会凭空多出一个「假头狼」
        val game = standard9().copy(phase = Phase.NIGHT_WOLF_CHAT, nightHead = 2)
        val director = WerewolfDirector(
            agentsOf(
                1 to ScriptedAI(listOf("我听 2 号的。")),
                2 to ScriptedAI(listOf("今晚刀 7 号。")),
                3 to ScriptedAI(listOf("行。")),
            )
        )

        val result = director.nightWolfChat(WerewolfTable(game))

        assertEquals("续档不能换头狼", 2, result.game.nightHead)
        val chats = result.transcript.filter { it.kind == MessageKind.WOLF_CHAT }
        assertEquals("存档里的头狼先发言", 2, chats.first().seat)
    }

    @Test
    fun `导演钉死的头狼每晚都当头狼`() = runBlocking {
        // 座位 1、2、3 是狼：钉死 3 号当头狼——就算 1 号座位更靠前，也轮不到他先说
        val game = standard9().copy(phase = Phase.NIGHT_WOLF_CHAT, alphaWolfSeat = 3)
        val director = WerewolfDirector(
            agentsOf(
                1 to ScriptedAI(listOf("我听 3 号的。")),
                2 to ScriptedAI(listOf("听 3 号安排。")),
                3 to ScriptedAI(listOf("今晚刀 7 号。")),
            )
        )

        val result = director.nightWolfChat(WerewolfTable(game))

        assertEquals("钉死的头狼优先于每晚随机", 3, result.game.nightHead)
        assertEquals("存的是头狼那一句", "今晚刀 7 号。", result.game.wolfPlan)
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
    fun `放逐投票理由只有投票人自己看得到`() = runBlocking {
        val ais = (1..9).associateWith { _ -> AutoAI() as IAI }
        val director = WerewolfDirector(ais.mapValues { SeatAgent(it.value, "test-model") })

        val result = director.vote(WerewolfTable(standard9().copy(phase = Phase.DAY_VOTE)))

        // 票型是公开的（由播报的票数负责），但「我投 X 号 + 理由」这条内心活动
        // 只对投票人自己可见——和警长投票、INNER_REASON_HINT 的「没人听见」对齐。
        val ballots = result.transcript.filter {
            it.phase == Phase.DAY_VOTE && it.kind == MessageKind.VOTE
        }
        assertEquals(9, ballots.size)
        assertTrue("没有一条是全员可见的", ballots.none { it.isPublic })
        ballots.forEach { ballot ->
            val voter = ballot.seat!!
            assertEquals("投票人看得到自己的票", true, ballot.visibleTo(voter))
            val other = if (voter == 1) 2 else 1
            assertEquals("别人看不到这条票", false, ballot.visibleTo(other))
        }
    }

    @Test
    fun `放逐投票结束后留下公开唱票卡，但不朗读也不泄理由`() = runBlocking {
        val ais = (1..9).associateWith { _ -> AutoAI() as IAI }
        val director = WerewolfDirector(ais.mapValues { SeatAgent(it.value, "test-model") })

        val result = director.vote(WerewolfTable(standard9().copy(phase = Phase.DAY_VOTE)))

        val cards = result.transcript.filter { it.kind == MessageKind.BALLOT }
        assertEquals("每轮一张唱票卡", 1, cards.size)
        val card = cards.single()
        assertTrue("唱票卡全员可见", card.isPublic)
        assertTrue("唱票卡不朗读", card.kind.neverSpoken)
        assertFalse("不是内心活动", card.kind.isInnerThought)
        assertEquals("9 个人都投了", 9, card.ballot!!.ballots.size)
        assertTrue("得票统计非空", card.ballot!!.tally.isNotEmpty())
        assertFalse("票型卡里不写投票理由", card.text.contains("我跟票"))
    }

    @Test
    fun `唱票卡不进 AI 上下文 匿名投票仍只知道自己那票`() = runBlocking {
        val cardText = "第 1 天 投票：1→3、2→3、4→弃票"
        val ballotCard = WerewolfMessage(
            day = 1, phase = Phase.DAY_VOTE, seat = null, kind = MessageKind.BALLOT,
            text = cardText,
            ballot = com.feature.werewolf.engine.BallotCard(
                ballots = listOf(
                    com.feature.werewolf.game.Ballot(1, 3),
                    com.feature.werewolf.game.Ballot(2, 3),
                    com.feature.werewolf.game.Ballot(4, null),
                ),
                tally = mapOf(3 to 2.0),
            ),
        )
        val ai = ScriptedAI(listOf("""{"target": 3, "reason": "我跟票。"}"""))
        val table = WerewolfTable(
            game = standard9().copy(phase = Phase.DAY_VOTE),
            transcript = listOf(ballotCard),
        )
        val director = WerewolfDirector((1..9).associate { it to SeatAgent(ai, "test-model") })

        director.vote(table)

        // 唱票卡画给牌桌上的人看，不该出现在模型的请求里——
        // 那会让它看到全场票型，破坏匿名投票。
        assertFalse("AI 上下文里不该有唱票卡摘要", ai.calls.first().userText.contains(cardText))
    }

    // ------------------------------------------------------------------
    // 上下文：票型压缩
    // ------------------------------------------------------------------

    @Test
    fun `白天的上下文里没有夜聊原文`() = runBlocking {
        // 模型分不清「我记得」和「我能说」：把夜聊原文摆在它面前，它就会在白天复述，
        // 线上出现过「昨夜四狼一致刀 4 号」这种当众自曝。
        val ai = ScriptedAI(listOf("我怀疑 3 号。"))
        val wolfChat = WerewolfMessage(
            day = 1,
            phase = Phase.NIGHT_WOLF_CHAT,
            seat = 1,
            kind = MessageKind.WOLF_CHAT,
            text = "今晚我们刀 4 号吧",
            audience = setOf(1, 2, 3),
        )
        val speech = WerewolfMessage(
            day = 1,
            phase = Phase.DAY_DISCUSSION,
            seat = 2,
            kind = MessageKind.SPEECH,
            text = "我觉得 5 号有问题。",
        )
        val table = WerewolfTable(
            game = standard9().copy(phase = Phase.DAY_DISCUSSION),
            transcript = listOf(wolfChat, speech),
        )
        val director = WerewolfDirector((1..9).associate { it to SeatAgent(ai, "test-model") })

        director.discussion(table)

        val context = ai.calls.first().userText
        assertFalse("白天不该看到夜聊原文", context.contains("今晚我们刀 4 号吧"))
        assertTrue("公开发言照常给", context.contains("我觉得 5 号有问题。"))
    }

    @Test
    fun `夜里的上下文仍然给狼看夜聊原文`() = runBlocking {
        val ai = ScriptedAI(listOf("我刀 4 号。"))
        val wolfChat = WerewolfMessage(
            day = 1,
            phase = Phase.NIGHT_WOLF_CHAT,
            seat = 2,
            kind = MessageKind.WOLF_CHAT,
            text = "今晚刀 4 号，他像预言家。",
            audience = setOf(1, 2, 3),
        )
        val table = WerewolfTable(
            game = standard9().copy(phase = Phase.NIGHT_WOLF_CHAT),
            transcript = listOf(wolfChat),
        )
        val director = WerewolfDirector((1..9).associate { it to SeatAgent(ai, "test-model") })

        director.nightWolfChat(table)

        assertTrue(
            "夜里必须看得到队友说了什么，否则没法接话",
            ai.calls.first().userText.contains("今晚刀 4 号，他像预言家。"),
        )
    }

    @Test
    fun `当天投票在上下文里压成一行票型`() = runBlocking {
        val ai = ScriptedAI(listOf("""{"target": 3, "reason": "我跟票。"}"""))
        val table = WerewolfTable(
            game = standard9().copy(phase = Phase.DAY_VOTE),
            transcript = listOf(
                WerewolfMessage(
                    day = 1,
                    phase = Phase.DAY_VOTE,
                    seat = 1,
                    kind = MessageKind.VOTE,
                    text = "我投 3 号。因为他昨天说漏了嘴，而且一直不肯表态。",
                ),
                WerewolfMessage(
                    day = 1,
                    phase = Phase.DAY_VOTE,
                    seat = 2,
                    kind = MessageKind.VOTE,
                    text = "我弃票。",
                ),
            ),
        )
        val director = WerewolfDirector((1..9).associate { it to SeatAgent(ai, "test-model") })

        director.vote(table)

        val context = ai.calls.first().userText
        // 理由只留 15 字：原文 19 字，截到「因为他昨天说漏了嘴，而且一直不」
        assertTrue(
            "两票应该压成一行、理由截到 15 字",
            context.contains("投票：1→3（因为他昨天说漏了嘴，而且一直不）、2→弃票"),
        )
        assertFalse("投票原文不该再进上下文", context.contains("我投 3 号。"))
    }

    @Test
    fun `投票压成票型之后，当天最早的发言不会被挤出去`() = runBlocking {
        // 9 条 200 字的发言 + 9 条带长理由的投票，原文总量超过 3200 字预算。
        // 先压票再裁，就都放得下；先裁再压，1 号那句会被挤掉。
        val ai = ScriptedAI(listOf("""{"target": 4, "reason": "我跟票。"}"""))
        val speeches = (1..9).map { seat ->
            WerewolfMessage(
                day = 1,
                phase = Phase.DAY_DISCUSSION,
                seat = seat,
                kind = MessageKind.SPEECH,
                text = "MARK$seat " + "发".repeat(190),
            )
        }
        val votes = (1..9).map { seat ->
            WerewolfMessage(
                day = 1,
                phase = Phase.DAY_VOTE,
                seat = seat,
                kind = MessageKind.VOTE,
                text = "我投 4 号。" + "因为".repeat(100),
            )
        }
        val table = WerewolfTable(
            game = standard9().copy(phase = Phase.DAY_VOTE),
            transcript = speeches + votes,
        )
        val director = WerewolfDirector((1..9).associate { it to SeatAgent(ai, "test-model") })

        director.vote(table)

        val context = ai.calls.first().userText
        assertTrue("最早的那句发言必须还在", context.contains("MARK1"))
        assertTrue("最后一句也在", context.contains("MARK9"))
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
    // 警长、遗言、自爆
    // ------------------------------------------------------------------

    @Test
    fun `警长竞选：报名、竞选发言、警下投票串起来`() = runBlocking {
        // 1、2 号上警；警下 3-9 号按剧本全投 1 号。
        // 每个座位一个模型实例，所以「第几次调用」是确定的，不看并发顺序
        val agents = (1..9).associate { seat ->
            val replies = if (seat <= 2) {
                listOf(
                    """{"target": $seat, "reason": "我上警。"}""",
                    "我竞选警长，我会先推 3 号。",
                )
            } else {
                listOf(
                    """{"target": null, "reason": "我不上警。"}""",
                    """{"target": 1, "reason": "我选一个看着有主意的。"}""",
                )
            }
            seat to SeatAgent(ScriptedAI(replies), "test-model")
        }
        val director = WerewolfDirector(agents)

        val signedUp = director.sheriffSignup(
            WerewolfTable(standard9().copy(phase = Phase.DAY_SHERIFF_SIGNUP))
        )
        assertEquals(Phase.DAY_SHERIFF_SPEECH, signedUp.game.phase)
        assertEquals(listOf(1, 2), signedUp.game.sheriffCandidates)
        // 上警只是举手：这一步一句发言都不该有
        assertEquals(
            "上警报名阶段不该产生发言",
            0,
            signedUp.transcript.count {
                it.phase == Phase.DAY_SHERIFF_SIGNUP && it.kind == MessageKind.SPEECH
            },
        )
        // 谁上了警由一条上帝播报公开，信息量一样，但不占十二行
        assertTrue(
            "上警名单要有播报",
            signedUp.transcript.any { it.kind == MessageKind.DIVINE && it.text.contains("上警的是") },
        )

        val spoken = director.sheriffSpeech(signedUp)
        assertEquals(Phase.DAY_SHERIFF_VOTE, spoken.game.phase)
        // 只有竞选发言阶段才轮到候选人说话
        assertEquals(
            "两个候选人都发了言",
            2,
            spoken.transcript.count {
                it.kind == MessageKind.SPEECH && it.phase == Phase.DAY_SHERIFF_SPEECH
            },
        )

        val voted = director.sheriffVote(spoken)
        assertEquals(1, voted.game.sheriffSeat)
        // 竞选结束、当天没有死者，所以直接进白天讨论
        assertEquals(Phase.DAY_DISCUSSION, voted.game.phase)
        assertTrue(voted.game.history.any { it is GameEvent.SheriffElected })

        // 警长投票是私下的：每条只对投票人自己可见，不进别人的上下文。
        // 理由里写着「我选一个看着有主意的」——这句不能让被投的 1 号或其他人看见。
        val ballots = voted.transcript.filter {
            it.phase == Phase.DAY_SHERIFF_VOTE && it.kind == MessageKind.VOTE
        }
        assertEquals("7 个警下的人各投一票", 7, ballots.size)
        assertTrue("没有一条是全员可见的", ballots.none { it.isPublic })
        ballots.forEach { ballot ->
            val voter = ballot.seat!!
            assertEquals("${voter} 号的票只有他自己看得见", setOf(voter), ballot.audience)
            assertTrue("投票人看得到自己的票", ballot.visibleTo(voter))
            val other = if (voter == 1) 2 else 1
            assertTrue("别人看不到这票", !ballot.visibleTo(other))
        }
    }

    @Test
    fun `遗言由死者自己说，说完继续白天`() = runBlocking {
        val dead = standard9().let { game ->
            game.copy(
                phase = Phase.DAY_LAST_WORDS,
                pendingLastWords = listOf(8),
                players = game.players.map {
                    if (it.seat == 8) {
                        it.copy(alive = false, deathCause = DeathCause.WOLF_KILL, deathDay = 1)
                    } else {
                        it
                    }
                },
            )
        }
        val director = WerewolfDirector(agentsOf(8 to ScriptedAI(listOf("我死了，3 号和 5 号里有一个有问题。"))))

        val result = director.lastWords(WerewolfTable(dead))

        assertEquals(Phase.DAY_DISCUSSION, result.game.phase)
        assertTrue(
            "遗言要进公开记录，活着的人投票前得听得到",
            result.transcript.any { it.seat == 8 && it.text.contains("3 号") },
        )
    }

    @Test
    fun `警长的遗言里会交代警徽`() = runBlocking {
        val dead = standard9().let { game ->
            game.copy(
                day = 2,
                phase = Phase.DAY_LAST_WORDS,
                sheriffSeat = 4,
                pendingLastWords = listOf(4),
                afterLastWordsPhase = Phase.NIGHT_WOLF,
                players = game.players.map {
                    if (it.seat == 4) {
                        it.copy(alive = false, deathCause = DeathCause.WOLF_KILL, deathDay = 2)
                    } else {
                        it
                    }
                },
            )
        }
        val director = WerewolfDirector(
            agentsOf(4 to ScriptedAI(listOf("""{"target": 7, "reason": "警徽给 7 号，他思路最清楚。"}"""))),
        )

        val result = director.lastWords(WerewolfTable(dead))

        assertEquals(7, result.game.sheriffSeat)
        assertTrue(result.transcript.any { it.text.contains("7 号") && it.text.contains("警徽") })
        assertEquals("遗言说完就进下一夜", Phase.NIGHT_WOLF_CHAT, result.game.phase)
    }

    @Test
    fun `狼人发言带自爆标记就当场自爆，白天提前结束`() = runBlocking {
        // 1、2、3 号是狼。受理只看标记，正文救谁还是保自己都不管
        val exploding = ScriptedAI(listOf("【自爆】救 3 号，别再投他了。"))
        val quiet = ScriptedAI(listOf("我觉得 2 号有点急。"))
        // 只有 1 号会开口：没有警长、没有死讯，发言从 1 号起，他自爆之后这一天就结束了
        val director = WerewolfDirector(
            agentsOf(1 to exploding, 2 to quiet, 3 to quiet, 4 to quiet, 5 to quiet, 6 to quiet, 7 to quiet, 8 to quiet, 9 to quiet),
        )

        val result = director.discussion(
            WerewolfTable(standard9().copy(day = 1, phase = Phase.DAY_DISCUSSION))
        )

        assertFalse(result.game.playerAt(1).alive)
        assertEquals(DeathCause.SELF_EXPLODE, result.game.playerAt(1).deathCause)
        assertTrue(result.game.history.any { it is GameEvent.WolfExploded })
        assertEquals("自爆直接翻夜", Phase.NIGHT_WOLF_CHAT, result.game.phase)
        assertEquals("自爆之后没人再发言", 1, result.transcript.count { it.kind == MessageKind.SPEECH })
        assertFalse(
            "标记本身不该留在记录里",
            result.transcript.any { it.text.contains("【自爆】") },
        )
    }

    @Test
    fun `只写自爆标记没有正文，也算自爆`() = runBlocking {
        // 有狼被逼到墙角，就输出一个光秃秃的「【自爆】」：正文为空。这是他自己的决定，
        // 受理条件只看标记，系统不该替他判断「值不值得」——照常受理，当场出局。
        val blank = ScriptedAI(listOf("【自爆】"))
        val quiet = ScriptedAI(listOf("我觉得 2 号有点急。"))
        val director = WerewolfDirector(
            agentsOf(1 to blank, 2 to quiet, 3 to quiet, 4 to quiet, 5 to quiet, 6 to quiet, 7 to quiet, 8 to quiet, 9 to quiet),
        )

        val result = director.discussion(
            WerewolfTable(standard9().copy(day = 1, phase = Phase.DAY_DISCUSSION))
        )

        assertTrue("裸标记也要受理", !result.game.playerAt(1).alive)
        assertEquals("自爆直接翻夜", Phase.NIGHT_WOLF_CHAT, result.game.phase)
        assertTrue(result.game.history.any { it is GameEvent.WolfExploded })
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
        // 完整票型记在 wolfVotes 里：一致定刀只落一条 WOLF_CHAT（避免复读），
        // 但落账不能少——这里不能再用消息条数代表「每只狼都表态了」
        assertEquals(3, result.game.wolfVotes.size)
        assertTrue(result.transcript.count { it.kind == MessageKind.WOLF_CHAT } >= 1)
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
            // 投票理由是私有消息（只有投票人自己看得到），所以这里查完整 transcript
            assertTrue("应该投过票", result.transcript.any { it.kind == MessageKind.VOTE })
        }
    }

    @Test
    fun `带警长的板子也能整局跑完`() = runBlocking {
        repeat(5) { seed ->
            val config = WerewolfConfig(
                composition = WerewolfConfig.Guard12,
                hasSheriff = true,
                maxVoteRounds = 2,
            )
            val game = WerewolfGame.deal(config, roster(12), Random(seed))
            val agents = game.players.associate { it.seat to SeatAgent(AutoAI(), "test-model") }

            val result = WerewolfDirector(agents, random = Random(seed + 7)).play(WerewolfTable(game))

            assertTrue("种子 $seed 没有结束", result.game.isFinished)
            // 竞选必须有结论：要么有人当选，要么明确记一笔「没有警长」。
            // 少了任何一条都说明这条新流程被跳过了
            assertTrue(
                "种子 $seed 没有竞选结果",
                result.game.history.any {
                    it is GameEvent.SheriffElected || it is GameEvent.SheriffVacant
                },
            )
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

    // ------------------------------------------------------------------
    // 发言上下文
    // ------------------------------------------------------------------

    @Test
    fun `上下文单独列出自己今天说过的话`() = runBlocking {
        // 真预言家在警上、PK、白天把同一段话说了三段，全场据此判它背词悍跳。
        // 自己的发言混在十几个人里权重和「别人说的」一样，摘出来才拦得住复读
        val said = "4号查7号是金水，可7号用守卫自守来回应，这不合逻辑。"
        val table = WerewolfTable(
            standard9().copy(day = 1, phase = Phase.DAY_DISCUSSION),
            transcript = listOf(
                WerewolfMessage(
                    day = 1,
                    phase = Phase.DAY_SHERIFF_SPEECH,
                    seat = 1,
                    kind = MessageKind.SPEECH,
                    text = said,
                ),
            ),
        )
        val speaker = ScriptedAI(listOf("我补充一点，2 号的票型也很可疑。"))
        val quiet = ScriptedAI(listOf("我先听。"))
        val director = WerewolfDirector(
            agentsOf(1 to speaker, 2 to quiet, 3 to quiet, 4 to quiet, 5 to quiet, 6 to quiet, 7 to quiet, 8 to quiet, 9 to quiet),
        )

        director.discussion(table)

        val userText = speaker.calls.first().userText
        assertTrue("要有专门一段", userText.contains("【你今天已经说过的话】"))
        assertTrue("要复述出来才拦得住", userText.contains("4号查7号是金水"))
        assertTrue(userText.contains("不要再说一遍"))
    }

    @Test
    fun `上下文保留昨天的发言`() = runBlocking {
        // 只看当天的话，到第 4 天模型只能从上帝播报反推，实测出现过
        // 「6 号昨天投票给 3 号很可疑」——那天有人自爆，根本没有投票
        val table = WerewolfTable(
            standard9().copy(day = 2, phase = Phase.DAY_DISCUSSION),
            transcript = listOf(
                WerewolfMessage(
                    day = 1,
                    phase = Phase.DAY_DISCUSSION,
                    seat = 3,
                    kind = MessageKind.SPEECH,
                    text = "我昨天怀疑 8 号，他一直在跟票。",
                ),
            ),
        )
        val speaker = ScriptedAI(listOf("今天我先听 2 号。"))
        val quiet = ScriptedAI(listOf("我先听。"))
        val director = WerewolfDirector(
            agentsOf(1 to speaker, 2 to quiet, 3 to quiet, 4 to quiet, 5 to quiet, 6 to quiet, 7 to quiet, 8 to quiet, 9 to quiet),
        )

        director.discussion(table)

        val userText = speaker.calls.first().userText
        assertTrue("昨天的发言要留着", userText.contains("我昨天怀疑 8 号"))
        assertTrue("并且要标明是昨天的", userText.contains("第 1 天（昨天及更早）"))
    }

    @Test
    fun `投票候选里不能出现自己`() = runBlocking {
        // 实测记录里明明白白写着「9 号投 9 号」：真预言家被全场踩到顺着气氛自投，
        // 白送好人一个轮次。线下没有这种打法，候选里直接拿掉
        val ai = ScriptedAI(listOf("""{"target": 3, "reason": "先跟一票"}"""))
        val director = WerewolfDirector(
            agentsOf(*(1..9).map { it to ai }.toTypedArray()),
        )

        director.vote(WerewolfTable(standard9().copy(day = 1, phase = Phase.DAY_VOTE)))

        var checked = 0
        ai.calls.forEach { call ->
            val prompt = call.systemPrompt.orEmpty() + "\n" + call.userText
            val seat = SEAT_IN_PROMPT.find(prompt)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            val segment = CANDIDATE_LINE.find(prompt)?.groupValues?.get(1) ?: return@forEach
            val seats = SEAT_NUMBER_IN_CANDIDATES.findAll(segment).map { it.groupValues[1].toInt() }.toSet()
            assertFalse("$seat 号的候选里不该有自己：$segment", seat in seats)
            checked++
        }
        assertTrue("至少要看几次请求，否则等于没测", checked >= 9)
    }

    @Test
    fun `狼人的每次请求都重申队友，好人看不到`() = runBlocking {
        // 实测：狼白天「盘身份」时把队友写进了怀疑名单，甚至把票投给自己人。
        // 队友名单只在很长的 system prompt 里出现一次，十几段中文压下来就被冲掉了。
        // 修法是在每次请求的正文开头钉一条硬事实——短、硬、只进本人的请求。
        val aiBySeat = (1..9).associateWith { ScriptedAI(listOf("我怀疑 5 号。")) }
        val director = WerewolfDirector(
            aiBySeat.mapValues { (_, ai) -> SeatAgent(ai, "test-model") },
        )

        director.discussion(WerewolfTable(standard9().copy(phase = Phase.DAY_DISCUSSION)))

        val wolf = aiBySeat.getValue(1).calls.first().userText
        assertTrue("1 号必须知道 2、3 号是队友", wolf.contains("你的狼队友是 2 号"))
        assertTrue("队友要全部列出", wolf.contains("3 号"))
        assertTrue(
            "只写「不许提」会让模型以为「不知道」，必须同时说清不能投",
            wolf.contains("不要把票投给他们"),
        )

        val good = aiBySeat.getValue(5).calls.first().userText
        assertFalse("好人不能看到任何队友信息", good.contains("狼队友"))
    }

    private companion object {
        val SEAT_IN_PROMPT = Regex("""你是 (\d+) 号玩家""")
        val CANDIDATE_LINE = Regex("""只能从 ([^中]*) 中选""")
        val SEAT_NUMBER_IN_CANDIDATES = Regex("""(\d+)\s*号""")
    }

    // ------------------------------------------------------------------
    // 思考时限
    // ------------------------------------------------------------------

    @Test
    fun `决策超出时限时按兜底继续，并关掉这个座位的深度思考`() = runBlocking {
        val pacing = WerewolfPacing()
        // 档位最小也是 30 秒，测试里压到 150ms，让「等不到」真的发生
        pacing.overrideMs = 150L
        val director = WerewolfDirector(
            agents = agentsOf(
                1 to HangingAI(),
                2 to ScriptedAI(listOf("""{"target": 7, "reason": "7 号话少。"}""")),
                3 to ScriptedAI(listOf("""{"target": 7, "reason": "我也觉得是他。"}""")),
            ),
            pacing = pacing,
        )

        val result = director.nightWolf(WerewolfTable(standard9().beginWolfVote()))

        // 一个座位卡住不该把整夜空掉：它走兜底，另外两票照样决定刀口
        assertEquals("两票仍能定刀", 7, result.game.tonightVictim)
        assertTrue("超时的座位本局不再开深度思考", 1 in pacing.thinkingOff.value)
        assertEquals("记了一次超时", 1, pacing.timings.value.getValue(1).timeouts)
        assertEquals("没超时的座位不受影响", 0, pacing.timings.value.getValue(2).timeouts)
        assertTrue("没人被关掉思考", 2 !in pacing.thinkingOff.value)
    }

    @Test
    fun `内部超时重试成功的座位也要关掉深度思考`() = runBlocking {
        // 真机反馈的 bug：模型第一次请求超时，decide 内部关思考重试成功，
        // 外层时限没到、outcome 也正常，于是以前当成「没超时」——下一轮又把思考打开，
        // 同一个座位反复撞超时。现在靠 outcome.timedOut 把这个座位本局关掉。
        val pacing = WerewolfPacing()
        pacing.overrideMs = 10_000L // 外层时限给足，超时只发生在 AI 内部
        var calls = 0
        val slowButRecovers = object : IAI {
            override suspend fun requestModel() = Unit
            override var modelSet: MutableSet<String> = mutableSetOf()
            override var brandName: String = "慢模型"
            override var company: String = "测试"

            override suspend fun chat(
                messages: List<ChatMessage>,
                model: String,
                systemPrompt: String?,
                enableThinking: Boolean,
                jsonMode: Boolean,
            ): String {
                calls++
                if (calls == 1) throw java.net.SocketTimeoutException("第一次超时")
                // 关掉思考后第二次很快回来，给一个合法刀口
                return """{"target": 7, "reason": "7 号可疑。"}"""
            }

            @Deprecated("测试用不到")
            override suspend fun chat(message: String, model: String): String =
                chat(listOf(ChatMessage.user(message)), model)
        }
        val director = WerewolfDirector(
            agents = agentsOf(
                1 to slowButRecovers,
                2 to ScriptedAI(listOf("""{"target": 7, "reason": "同意。"}""")),
                3 to ScriptedAI(listOf("""{"target": 7, "reason": "同意。"}""")),
            ),
            pacing = pacing,
        )

        val result = director.nightWolf(WerewolfTable(standard9().beginWolfVote()))

        assertEquals("重试成功，刀口正常定下", 7, result.game.tonightVictim)
        assertTrue("内部降级过的座位本局不再开深度思考", 1 in pacing.thinkingOff.value)
        assertEquals("记了一次超时", 1, pacing.timings.value.getValue(1).timeouts)
        assertFalse("没超时的座位不受影响", 2 in pacing.thinkingOff.value)
    }

    @Test
    fun `发言时底层超时降级也关掉深度思考`() = runBlocking {
        // chatStream 内部超时后关思考补发成功，是正常返回的一场发言，
        // 外层看不到异常。靠 delta.timeoutDegraded 把信号带出来。
        val pacing = WerewolfPacing()
        pacing.overrideMs = 10_000L
        class DegradedSpeechAI : IAI {
            var streamCalls = 0
            override suspend fun requestModel() = Unit
            override var modelSet: MutableSet<String> = mutableSetOf()
            override var brandName: String = "慢模型"
            override var company: String = "测试"

            override fun chatStream(
                messages: List<ChatMessage>,
                model: String,
                systemPrompt: String?,
                enableThinking: Boolean,
                jsonMode: Boolean,
            ): kotlinx.coroutines.flow.Flow<ChatDelta> = kotlinx.coroutines.flow.flow {
                streamCalls++
                // 模拟 OpenAICompatibleAI 的降级补发：超时 → 关思考 → 一次性请求出结果
                emit(ChatDelta(content = "我觉得 5 号在藏。", timeoutDegraded = true))
            }

            @Deprecated("测试用不到")
            override suspend fun chat(message: String, model: String): String = "我觉得 5 号在藏。"

            override suspend fun chat(
                messages: List<ChatMessage>,
                model: String,
                systemPrompt: String?,
                enableThinking: Boolean,
                jsonMode: Boolean,
            ): String = "我觉得 5 号在藏。"
        }
        val game = standard9()
        val degraded = DegradedSpeechAI()
        val agents = game.players.associate { player ->
            player.seat to SeatAgent(
                ai = if (player.seat == 1) degraded else ScriptedAI(listOf("我没什么好说的。")),
                model = "test",
            )
        }
        val director = WerewolfDirector(agents = agents, pacing = pacing)

        director.discussion(WerewolfTable(game.copy(phase = Phase.DAY_DISCUSSION)))

        assertTrue("发言降级过的座位本局不再开深度思考", 1 in pacing.thinkingOff.value)
        assertEquals("记了一次超时", 1, pacing.timings.value.getValue(1).timeouts)
        assertFalse("没超时的座位不受影响", 2 in pacing.thinkingOff.value)
    }

    @Test
    fun `发言超出时限时保留已经说出的半句`() = runBlocking {
        val pacing = WerewolfPacing()
        pacing.overrideMs = 120L
        val game = standard9()
        val agents = game.players.associate { player ->
            player.seat to SeatAgent(ai = HangingAI("我觉得 5 号在藏"), model = "slow")
        }
        val director = WerewolfDirector(agents = agents, pacing = pacing)

        val result = director.discussion(
            WerewolfTable(game.copy(phase = Phase.DAY_DISCUSSION))
        )

        val speeches = result.transcript.filter { it.kind == MessageKind.SPEECH }
        assertEquals("每个人都要留下一条，不能空过", game.aliveSeats.size, speeches.size)
        // 说到一半被打断，已经说出去的话必须留着——清空会让后面的人对着空白接话
        assertTrue("半句要留下：${speeches.first().text}", speeches.first().text.contains("我觉得 5 号"))
        assertEquals("超时的座位这一局都不再开思考", game.aliveSeats.toSet(), pacing.thinkingOff.value)
    }


    @Test
    fun `发言的思考段超时按空话兜底，不吃发言时限`() = runBlocking {
        // 时限口径：气泡里是思考、发言列表里是发言。一个字都没吐出来的时间
        // 受思考时限管，掐得比发言时限早——慢模型不能把整局拖死在「想」上。
        val pacing = WerewolfPacing()
        pacing.overrideThinkingMs = 150L
        pacing.overrideSpeechMs = 10_000L
        val game = standard9()
        val agents = game.players.associate { player ->
            player.seat to SeatAgent(ai = HangingAI(), model = "slow")
        }
        val director = WerewolfDirector(agents = agents, pacing = pacing)

        val result = director.discussion(
            WerewolfTable(game.copy(phase = Phase.DAY_DISCUSSION))
        )

        val speeches = result.transcript.filter { it.kind == MessageKind.SPEECH }
        assertEquals("每个人都要留下一条，不能空过", game.aliveSeats.size, speeches.size)
        assertTrue(
            "一个字没吐就按空话兜底：${speeches.first().text}",
            // SILENT_SPEECH 的值（companion 是 private，抄一份字面量）
            speeches.all { it.text == "……我这轮先听大家的。" },
        )
        assertEquals("超时的座位本局不再开思考", game.aliveSeats.toSet(), pacing.thinkingOff.value)
        assertEquals("按思考时限记账", 1, pacing.timings.value.getValue(1).timeouts)
    }

    @Test
    fun `吐字之后按发言时限计，不吃思考时限`() = runBlocking {
        // 反方向的验证：只要开始往外吐字，就换成发言时钟。思考时限再短，
        // 也不能掐一段已经开了口的话——那会把说了一半的人当成哑巴。
        val pacing = WerewolfPacing()
        pacing.overrideThinkingMs = 50L
        pacing.overrideSpeechMs = 400L
        val game = standard9()
        val agents = game.players.associate { player ->
            player.seat to SeatAgent(ai = HangingAI("我先说一句"), model = "slow")
        }
        val director = WerewolfDirector(agents = agents, pacing = pacing)

        val result = director.discussion(
            WerewolfTable(game.copy(phase = Phase.DAY_DISCUSSION))
        )

        val speeches = result.transcript.filter { it.kind == MessageKind.SPEECH }
        assertTrue("半句要留下：${speeches.first().text}", speeches.first().text.contains("我先说一句"))
        val elapsed = pacing.timings.value.getValue(1).lastMs
        assertTrue(
            "发言段按发言时限走（远超思考时限的 50ms）：${elapsed}ms",
            elapsed >= 300,
        )
    }

    @Test
    fun `用户退出导致的取消不算超时，也不关思考`() = runBlocking {
        // 退出页面、暂停、换局都会把协程取消。这条取消和「时限到了」在异常类型上
        // 可能长得一样，靠「外层是否还活着」区分：外层已取消就不是我们的超时。
        val pacing = WerewolfPacing()
        pacing.overrideMs = 10_000L
        val game = standard9()
        val agents = game.players.associate { player ->
            player.seat to SeatAgent(ai = HangingAI("我刚说到一半"), model = "slow")
        }
        val director = WerewolfDirector(agents = agents, pacing = pacing)

        val job = launch {
            director.discussion(WerewolfTable(game.copy(phase = Phase.DAY_DISCUSSION)))
        }
        delay(120)
        job.cancelAndJoin()

        assertEquals("没人被当成超时", emptySet<Int>(), pacing.thinkingOff.value)
        assertEquals("账本里不该留下一条半截记录", emptyMap<Int, SeatTiming>(), pacing.timings.value)
    }

}
