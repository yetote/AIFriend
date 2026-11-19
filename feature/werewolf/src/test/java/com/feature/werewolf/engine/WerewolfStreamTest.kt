package com.feature.werewolf.engine

import com.core.ai.entity.IAI
import com.core.ai.model.ChatDelta
import com.core.ai.model.ChatMessage
import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 流式发言的测试。
 *
 * 这一层要锁住三件事：
 * 1. 半句话只能**显示**——不能进 transcript、不能被后面的 AI 看见；
 * 2. 说完了必须把「正在说话」收回，否则牌桌上会留一个永远在打字的光标；
 * 3. 厂商不支持流式（一个片段都没有）时不能把这句话变成空白。
 */
class WerewolfStreamTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry(id = "p$it", name = "玩家$it") }

    /** 3 狼 9 人局，和编排层其余测试用同一套身份。 */
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

    /** 8 号出局、正在留遗言的局面：只需要一个座位开口，最容易断言。 */
    private fun lastWordsOf8(): WerewolfGame = standard9().let { game ->
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

    /**
     * 按片段吐字的假模型。
     *
     * [pieces] 为空模拟「厂商完全不支持流式」：一个增量都不发，直接结束。
     */
    private class StreamingAI(
        private val pieces: List<String>,
        private val reasonings: List<String> = emptyList(),
        private val chatReply: String = "",
        private val failAt: Int = Int.MAX_VALUE,
        private val failError: Throwable = IOException("连接被切断了"),
    ) : IAI {
        var chatCalls = 0
        var streamCalls = 0

        override suspend fun requestModel() = Unit
        override var modelSet: MutableSet<String> = mutableSetOf()
        override var brandName: String = "流式测试"
        override var company: String = "测试"

        override suspend fun chat(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
        ): String {
            chatCalls++
            return chatReply
        }

        override fun chatStream(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
        ): Flow<ChatDelta> = flow {
            streamCalls++
            pieces.forEachIndexed { index, piece ->
                if (index == failAt) throw failError
                emit(ChatDelta(content = piece, reasoning = reasonings.getOrElse(index) { "" }))
            }
        }

        @Deprecated("测试用不到")
        override suspend fun chat(message: String, model: String): String =
            chat(message.let { listOf(ChatMessage.user(it)) }, model)
    }

    @Test
    fun `发言按片段推给界面，说完收回`() = runBlocking {
        val ai = StreamingAI(
            pieces = listOf("我觉得", " 3 号", "有点急。"),
            reasonings = listOf("先看", "他的表态", ""),
        )
        val lives = mutableListOf<WerewolfLive?>()
        val director = WerewolfDirector(
            agents = mapOf(8 to SeatAgent(ai, "test-model")),
            onLive = { lives += it },
            // 节流关掉：测试要看到每一个增量，而不是「80ms 内只推最后一次」
            streamIntervalMs = 0,
        )

        val result = director.lastWords(WerewolfTable(lastWordsOf8()))

        val pushes = lives.filterNotNull()
        assertTrue("应该推过至少一次半句话", pushes.isNotEmpty())
        assertNull("说完必须收回，否则光标会一直闪", lives.last())
        assertEquals(1, ai.streamCalls)
        assertEquals("走了流式就不该再补一次普通请求", 0, ai.chatCalls)

        // 推的是**累计**文本，不是增量：界面只做整体替换，不需要自己拼
        assertEquals("我觉得 3 号有点急。", pushes.last().text)
        assertTrue(
            "每次推送都应该比上一次长",
            pushes.map { it.text.length } == pushes.map { it.text.length }.sorted(),
        )
        assertEquals(8, pushes.first().seat)
        assertEquals(Phase.DAY_LAST_WORDS, pushes.first().phase)
        assertTrue("思考过程也要一起推，界面才好折叠展示", pushes.last().reasoning.contains("他的表态"))

        // 半句话只存在于回调里，落进 transcript 的必须是完整的、剥过前缀的那一句
        val speech = result.transcript.single { it.seat == 8 }
        assertEquals("我觉得 3 号有点急。", speech.text)
    }

    @Test
    fun `一个片段都没收到时回落成普通请求，不会说出空白`() = runBlocking {
        val ai = StreamingAI(pieces = emptyList(), chatReply = "我先看看局势。")
        val lives = mutableListOf<WerewolfLive?>()
        val director = WerewolfDirector(
            agents = mapOf(8 to SeatAgent(ai, "test-model")),
            onLive = { lives += it },
            streamIntervalMs = 0,
        )

        val result = director.lastWords(WerewolfTable(lastWordsOf8()))

        assertEquals("流式什么也没给，必须补一次普通请求", 1, ai.chatCalls)
        assertEquals("我先看看局势。", result.transcript.single { it.seat == 8 }.text)
        assertEquals("这种情况不该推出过内容", 0, lives.filterNotNull().size)
        assertNull(lives.last())
    }

    @Test
    fun `流式中途报错时也要收回正在说话`() = runBlocking {
        // 网络断在一半：异常必须往上抛（编排层要显示错误），但界面上的半句话必须清掉
        val ai = StreamingAI(pieces = listOf("我觉得", " 3 号"), failAt = 1)
        val lives = mutableListOf<WerewolfLive?>()
        val director = WerewolfDirector(
            agents = mapOf(8 to SeatAgent(ai, "test-model")),
            onLive = { lives += it },
            streamIntervalMs = 0,
        )

        val thrown = runCatching {
            director.lastWords(WerewolfTable(lastWordsOf8()))
        }.exceptionOrNull()

        assertTrue("传输层错误必须原样抛出，不能伪装成「AI 没说话」", thrown is IOException)
        assertNull("出错时同样要收回", lives.last())
        assertTrue("报错前已经推出去的片段是真实发生过的", lives.filterNotNull().isNotEmpty())
    }

    @Test
    fun `超时断在半句话时保留这半句`() = runBlocking {
        // 真机案例：全员开深度思考，一句话说到一半超过静默上限被掐。
        // 这种情况如果照别处一样往上抛，整局就停在「模型响应超时」上，
        // 续档还会重放同一阶段撞同一个超时——这局等于打不下去了。
        // 半句话不好听，但牌桌能往前走；而「连接被重置」那种链路坏了仍然要上报。
        val ai = StreamingAI(
            pieces = listOf("我觉得", " 3 号"),
            failAt = 1,
            failError = java.net.SocketTimeoutException("模型响应超时"),
        )
        val lives = mutableListOf<WerewolfLive?>()
        val director = WerewolfDirector(
            agents = mapOf(8 to SeatAgent(ai, "test-model")),
            onLive = { lives += it },
            streamIntervalMs = 0,
        )

        val result = director.lastWords(WerewolfTable(lastWordsOf8()))

        assertEquals("说出口的半句要落下来", "我觉得", result.transcript.single { it.seat == 8 }.text)
        assertNull("收回照样要做", lives.last())
    }

    @Test
    fun `狼人夜聊也走流式，但内容不会进好人视角`() = runBlocking {
        // 夜聊和白天发言共用 askSpeech，所以流式是自动生效的；
        // 这里锁的是「内容仍然带 audience」，流式没有绕过可见性
        val ai = StreamingAI(pieces = listOf("刀 4 号吧。"))
        val director = WerewolfDirector(
            agents = (1..3).associate { it to SeatAgent(ai, "test-model") },
            streamIntervalMs = 0,
        )

        val result = director.nightWolfChat(WerewolfTable(standard9()))

        val chats = result.transcript.filter { it.kind == MessageKind.WOLF_CHAT }
        assertEquals("三只狼各说一句", 3, chats.size)
        assertEquals(setOf(1, 2, 3), chats.first().audience)
        assertEquals("好人拿不到狼人夜话", false, chats.first().visibleTo(4))
    }
}
