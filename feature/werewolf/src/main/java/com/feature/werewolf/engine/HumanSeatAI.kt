package com.feature.werewolf.engine

import com.core.ai.entity.IAI
import com.core.ai.model.ChatMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 轮到人类时，把「模型本该看到的东西」原样交给界面。 */
data class HumanTurn(
    val systemPrompt: String,
    val messages: List<ChatMessage>,
    /** true 表示这次要的是结构化决策（JSON），false 是自由发言。 */
    val jsonMode: Boolean,
    /**
     * 这一步属于哪个阶段（Phase 名）。
     *
     * 夜间三路并发时，守卫 / 狼人 / 预言家同时在跑，UI 的 table.phase 会被狼人路的
     * 中间态占据，停在上述某一段——而人类回合的面板必须按「这一步真正属于哪个阶段」
     * 来出（预言家要出「查验谁」）。所以 phase 由导演在提问前直接塞进来，
     * 不依赖 UI 当时看到的 table.phase。
     */
    val phase: String? = null,
)

/**
 * 人类座位的「模型」。
 *
 * 玩法上是这样的：轮到你的座位时，[chat] 不返回任何东西，而是挂起并把
 * [turn] 推给界面；你在界面上做完选择，[submit] 把答案交回来，它才继续。
 *
 * **关键点是它实现了 [IAI]。** 于是编排层完全不需要知道「这个座位是人」——
 * 狼人刀人、投票、发言的代码路径一个字都不用改，人类座位和 AI 座位在导演看来毫无区别。
 * 如果反过来给导演加一条 `if (seat == humanSeat)` 的分支，那这条分支会在每一处
 * 有座位的地方出现，早晚漏掉一个。
 *
 * 私有信息也因此天然正确：交给人类的就是他自己那份 system prompt，
 * 里面只有他的身份、他的队友、他验过的人。
 */
class HumanSeatAI : IAI {

    private val _turn = MutableStateFlow<HumanTurn?>(null)

    /** 当前是否在等人类操作。界面据此显示输入区。 */
    val turn: StateFlow<HumanTurn?> = _turn.asStateFlow()

    @Volatile
    private var waiter: CompletableDeferred<String>? = null

    val waiting: Boolean get() = waiter != null

    /** 导演在提问前写下本步所属阶段，chat() 推 turn 时带上（见 HumanTurn.phase）。 */
    @Volatile
    var currentPhase: String? = null

    override suspend fun requestModel() = Unit

    override var modelSet: MutableSet<String> = mutableSetOf()

    override var brandName: String = "你"
    override var company: String = "本机"

    override suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String?,
        enableThinking: Boolean,
        jsonMode: Boolean,
        // 人类座位不联网：这里是在等用户打字，没有模型参与
        webSearch: Boolean,
    ): String {
        val deferred = CompletableDeferred<String>()
        waiter = deferred
        _turn.value = HumanTurn(
            systemPrompt = systemPrompt.orEmpty(),
            messages = messages,
            jsonMode = jsonMode,
            phase = currentPhase,
        )
        try {
            return deferred.await()
        } finally {
            // 用户中途退出时协程会被取消，这里必须清干净，
            // 否则下次进来界面还停在上一次的输入框上
            waiter = null
            _turn.value = null
        }
    }

    /**
     * 提交人类的选择。[jsonMode] 时必须是合法 JSON。
     *
     * 返回 false 表示当前没有在等输入（比如已经超时或被取消），调用方可以忽略。
     */
    fun submit(reply: String): Boolean {
        val current = waiter ?: return false
        return current.complete(reply)
    }

    @Deprecated("人类座位不走单条消息接口")
    override suspend fun chat(message: String, model: String): String =
        chat(listOf(ChatMessage.user(message)), model)
}
