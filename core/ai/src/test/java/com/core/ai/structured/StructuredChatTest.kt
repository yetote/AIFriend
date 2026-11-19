package com.core.ai.structured

import com.core.ai.entity.IAI
import com.core.ai.model.ChatDelta
import com.core.ai.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@Serializable
private data class VoteDecision(val target: Int)

/**
 * 结构化决策的测试。
 *
 * 全部走一个「照本宣科的假 IAI」：预设好每次回复的原话，就能精确控制
 * 「第一次跑偏、第二次纠正」这种现实中很难复现的路径，也不用联网。
 */
class StructuredChatTest {

    private val spec = StructuredSpec(
        instruction = """输出 {"target": 座位号}，target 必须是你要投的存活玩家座位号。""",
        serializer = VoteDecision.serializer(),
        validate = { it.target in 1..8 },
    )

    private val userMessage = listOf(ChatMessage.user("现在请投票。"))

    /** 记录每次调用传入的参数，便于断言 jsonMode、system 拼装是否正确。 */
    private data class Call(
        val messages: List<ChatMessage>,
        val systemPrompt: String?,
        val jsonMode: Boolean,
    )

    private class ScriptedAI(
        private val replies: List<String>,
        override val supportsJsonMode: Boolean = true,
    ) : IAI {
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
            return replies.getOrNull(calls.lastIndex)
                ?: throw IllegalStateException("预设回复已用完，说明多调了一次")
        }

        @Deprecated("测试用不到")
        override suspend fun chat(message: String, model: String): String =
            chat(listOf(ChatMessage.user(message)), model)
    }

    /**
     * 边想边答的假模型：先把思维链一段段吐出来，最后才给正文。
     *
     * 分别记 `chatStream` 和 `chat` 的调用次数——这条测试要锁住的就是
     * 「要思维链才走流式，不要就别多开一条流」。
     */
    private class ThinkingAI(
        private val reasoningPieces: List<String>,
        private val content: String,
    ) : IAI {
        var chatCalls = 0
        var streamCalls = 0

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
            chatCalls++
            return content
        }

        override fun chatStream(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
        ): Flow<ChatDelta> = flow {
            streamCalls++
            reasoningPieces.forEach { emit(ChatDelta(content = "", reasoning = it)) }
            emit(ChatDelta(content = content))
        }

        @Deprecated("测试用不到")
        override suspend fun chat(message: String, model: String): String =
            chat(listOf(ChatMessage.user(message)), model)
    }

    // ------------------------------------------------------------------
    // 思维链
    // ------------------------------------------------------------------

    @Test
    fun `要思维链时走流式，思考边生成边回调`() = runBlocking {
        // 投票、查验这些决策回的是 JSON，正文的中间态没法用；但开深度思考的座位
        // 在写 JSON 之前想的那一大段，是「他为什么这么打」——牌桌要能边想边看到它。
        val ai = ThinkingAI(
            reasoningPieces = listOf("先看 3 号", "的票型，", "他可疑。"),
            content = """{"target": 3}""",
        )
        val seen = mutableListOf<String>()

        val outcome = ai.decide(
            userMessage, "m", spec,
            fallback = { VoteDecision(1) },
            onReasoning = { seen += it },
        )

        assertEquals(3, outcome.value.target)
        assertEquals(1, ai.streamCalls)
        assertEquals("要走流式就不该再补一次一次性请求", 0, ai.chatCalls)
        // 回调的是**累计**文本，不是增量：显示方只做整体替换
        assertEquals(
            listOf("先看 3 号", "先看 3 号的票型，", "先看 3 号的票型，他可疑。"),
            seen,
        )
        assertEquals("先看 3 号的票型，他可疑。", outcome.reasoning)
    }

    @Test
    fun `不要思维链时仍走一次性请求，不多开一条流`() = runBlocking {
        // 关着深度思考的座位没有思维链可显示。这种时候多开一条流，
        // 只是多押一处「厂商支不支持 stream + json_object」的风险。
        val ai = ThinkingAI(listOf("想了很多"), """{"target": 4}""")

        val outcome = ai.decide(userMessage, "m", spec, fallback = { VoteDecision(1) })

        assertEquals(4, outcome.value.target)
        assertEquals(1, ai.chatCalls)
        assertEquals(0, ai.streamCalls)
        assertEquals("", outcome.reasoning)
    }

    /** 模拟网络/模型超时。 */
    private class TimeoutAI : IAI {        var calls = 0

        /** 每次调用开的思考开关，用来断言「超时后有没有降级」。 */
        val thinkingFlags = mutableListOf<Boolean>()

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
            calls++
            thinkingFlags += enableThinking
            throw java.net.SocketTimeoutException("模型响应超时")
        }

        @Deprecated("测试用不到")
        override suspend fun chat(message: String, model: String): String =
            chat(listOf(ChatMessage.user(message)), model)
    }

    // ------------------------------------------------------------------
    // 超时
    // ------------------------------------------------------------------

    @Test
    fun `超时不该把整局卡死，用兜底值继续`() = runBlocking {
        // 真机案例：所有 AI 都开深度思考，警长投票阶段（十几个人同时深度思考）
        // 有人请求超时。超时是「这个座位这一次没答上来」，不是「整局废了」——
        // 抛出去会让用户点「继续」再撞一次同样的超时，这一局永远打不完。
        val ai = TimeoutAI()

        val outcome = ai.decide(userMessage, "m", spec, fallback = { VoteDecision(1) })

        assertEquals(1, outcome.value.target)
        assertTrue("要标记成用了兜底", outcome.usedFallback)
        assertTrue("两次机会都用过了", outcome.attempts >= 2)
    }

    @Test
    fun `超时重试时关掉深度思考`() = runBlocking {
        // 3 分钟那档被反复打穿的头号原因是思考本身。用一模一样的参数再撞一次，
        // 只会把等待时间翻倍，然后照样失败——用户等十几分钟换来一句「网络异常」。
        // 第二次必须降级：关掉思考，这次通常几十秒就回来。
        val ai = TimeoutAI()

        ai.decide(userMessage, "m", spec, fallback = { VoteDecision(1) }, enableThinking = true)

        assertEquals(listOf(true, false), ai.thinkingFlags)
    }

    @Test
    fun `Key 这类硬错误照样抛出去`() = runBlocking {
        // 超时可以兜底，认证失败不行：兜底会让 AI 开始乱投票，
        // 而用户真正该看到的是「Key 无效」
        val ai = object : IAI {
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
            ): String = throw IllegalStateException("Key 无效")

            @Deprecated("测试用不到")
            override suspend fun chat(message: String, model: String): String =
                chat(listOf(ChatMessage.user(message)), model)
        }

        val error = runCatching {
            ai.decide(userMessage, "m", spec, fallback = { VoteDecision(1) })
        }.exceptionOrNull()

        assertTrue("应该抛出去：$error", error is IllegalStateException)
    }

    // ------------------------------------------------------------------
    // 正常路径
    // ------------------------------------------------------------------

    @Test
    fun `干净 JSON 一次成功`() = runBlocking {
        val ai = ScriptedAI(listOf("""{"target": 3}"""))

        val outcome = ai.decide(userMessage, "m", spec, fallback = { VoteDecision(1) })

        assertEquals(3, outcome.value.target)
        assertEquals(1, outcome.attempts)
        assertFalse(outcome.usedFallback)
    }

    @Test
    fun `markdown 代码块里的 JSON 也能解析`() = runBlocking {
        val ai = ScriptedAI(listOf("```json\n{\"target\": 5}\n```"))

        val outcome = ai.decide(userMessage, "m", spec, fallback = { VoteDecision(1) })

        assertEquals(5, outcome.value.target)
        assertFalse(outcome.usedFallback)
    }

    @Test
    fun `带解释的散文里也能抠出 JSON`() = runBlocking {
        val ai = ScriptedAI(listOf("我的分析是 6 号很可疑。\n{\"target\": 6}\n希望这个回答有帮助。"))

        val outcome = ai.decide(userMessage, "m", spec, fallback = { VoteDecision(1) })

        assertEquals(6, outcome.value.target)
        assertFalse(outcome.usedFallback)
    }

    @Test
    fun `第一个对象不合法时会继续试后面的`() = runBlocking {
        val ai = ScriptedAI(listOf("""先这样 {"target": } 再这样 {"target": 7}"""))

        val outcome = ai.decide(userMessage, "m", spec, fallback = { VoteDecision(1) })

        assertEquals(7, outcome.value.target)
        assertEquals(1, outcome.attempts)
    }

    // ------------------------------------------------------------------
    // 重试
    // ------------------------------------------------------------------

    @Test
    fun `第一次没有 JSON 会带原因重试并成功`() = runBlocking {
        val ai = ScriptedAI(listOf("我觉得应该投 3 号", """{"target": 3}"""))

        val outcome = ai.decide(userMessage, "m", spec, fallback = { VoteDecision(1) })

        assertEquals(3, outcome.value.target)
        assertEquals(2, outcome.attempts)
        assertFalse(outcome.usedFallback)

        // 重试时把上一次的原话和失败原因一起带回去，比单纯「再试一次」有效
        val retry = ai.calls[1].messages
        assertEquals(ai.calls[0].messages.size + 2, retry.size)
        assertEquals("我觉得应该投 3 号", retry[retry.lastIndex - 1].content)
        assertTrue(retry.last().content.contains("没有找到 JSON 对象"))
    }

    // ------------------------------------------------------------------
    // 兜底
    // ------------------------------------------------------------------

    @Test
    fun `两次都失败则返回兜底值`() = runBlocking {
        val ai = ScriptedAI(listOf("投 3 号", "还是投 3 号"))

        val outcome = ai.decide(userMessage, "m", spec, fallback = { VoteDecision(2) })

        assertEquals(2, outcome.value.target)
        assertEquals(2, outcome.attempts)
        assertTrue(outcome.usedFallback)
        assertEquals("没有找到 JSON 对象", outcome.lastError)
    }

    @Test
    fun `取值不合法等同解析失败`() = runBlocking {
        // 9 号不在 1..8 范围内：JSON 能解析，但决策不合法
        val ai = ScriptedAI(listOf("""{"target": 9}""", """{"target": 9}"""))

        val outcome = ai.decide(userMessage, "m", spec, fallback = { VoteDecision(4) })

        assertEquals(4, outcome.value.target)
        assertTrue(outcome.usedFallback)
        assertTrue(outcome.lastError!!.contains("不在允许范围"))
    }

    // ------------------------------------------------------------------
    // 厂商能力与提示词
    // ------------------------------------------------------------------

    @Test
    fun `支持 json 模式的厂商会带上 jsonMode`() = runBlocking {
        val ai = ScriptedAI(listOf("""{"target": 3}"""), supportsJsonMode = true)

        ai.decide(userMessage, "m", spec, fallback = { VoteDecision(1) })

        assertTrue(ai.calls.single().jsonMode)
    }

    @Test
    fun `不支持 json 模式的厂商降级为纯提示词`() = runBlocking {
        val ai = ScriptedAI(listOf("""{"target": 3}"""), supportsJsonMode = false)

        val outcome = ai.decide(userMessage, "m", spec, fallback = { VoteDecision(1) })

        assertFalse(ai.calls.single().jsonMode)
        // 降级之后照样能拿到结果——靠的是提示词约束 + 括号扫描
        assertEquals(3, outcome.value.target)
    }

    @Test
    fun `输出要求会拼在原 system 之后`() = runBlocking {
        val ai = ScriptedAI(listOf("""{"target": 3}"""))

        ai.decide(userMessage, "m", spec, systemPrompt = "你是 3 号玩家。", fallback = { VoteDecision(1) })

        val system = ai.calls.single().systemPrompt!!
        assertTrue(system.startsWith("你是 3 号玩家。"))
        assertTrue(system.contains("【输出要求】"))
        assertTrue(system.contains("target 必须是你要投的存活玩家座位号"))
        assertTrue(system.contains("不要 markdown 代码块"))
    }

    @Test
    fun `没有 system 时也能只带输出要求`() {
        val system = buildOutputRequirement(null, "输出 {\"target\": 1}")
        assertTrue(system.startsWith("【输出要求】"))
        assertFalse(system.contains("\n\n【输出要求】"))
    }

    @Test
    fun `尝试次数少于一次直接拒绝`() {
        val ai = ScriptedAI(emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ai.decide(userMessage, "m", spec, fallback = { VoteDecision(1) }, maxAttempts = 0) }
        }
    }
}
