package com.feature.werewolf.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.ai.entity.IAI
import com.core.ai.factory.AIFactory
import com.core.ai.model.Friend
import com.core.ai.model.toFriend
import com.core.ai.probe.ApiProbeResult
import com.core.ai.probe.probe
import com.core.repository.storage.FriendRepository
import com.feature.werewolf.decision.SeatChoice
import com.feature.werewolf.decision.VoteChoice
import com.feature.werewolf.decision.WitchChoice
import com.feature.werewolf.engine.HumanSeatAI
import com.feature.werewolf.engine.HumanTurn
import com.feature.werewolf.engine.SeatAgent
import com.feature.werewolf.engine.WerewolfController
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPlayer
import com.feature.werewolf.store.WerewolfGameSummary
import com.feature.werewolf.store.WerewolfStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.random.Random

/** 这一局里玩家自己是否参与。 */
enum class WerewolfMode {
    /** 你自己坐一个座位，能发言、投票、被刀。 */
    PLAY,

    /** 纯观战：8 个 AI 自己打，你看全局。 */
    SPECTATE,
}

/**
 * 狼人杀界面的 ViewModel。
 *
 * 它只做三件事：把「选哪些好友、什么模式」组装成一局、把人机座位分派给模型、
 * 把人类操作转成编排层能吃的 JSON。规则判定、请求调度、存档全部在更底层，
 * 那些地方不该被 Android 依赖污染。
 */
class WerewolfViewModel(application: Application) : AndroidViewModel(application) {

    private val store = WerewolfStore(application)
    private val friendRepository = FriendRepository(application)
    private val aiFactory = AIFactory()

    /** 厂商 → 实例。同一厂商的多个座位共用一个实例，和聊天室的做法一致。 */
    private val ais = mutableMapOf<String, IAI>()

    private val human = HumanSeatAI()

    private val json = Json { encodeDefaults = true }

    /** 存档生成时绑定的座位表，续档时按同样的规则重建。 */
    private var agents: Map<Int, SeatAgent> = emptyMap()

    private val controller = WerewolfController(
        store = store,
        // 每个座位在开局/续档时已经分配好，这里只是按座位取
        agentsFor = { player -> agents[player.seat] ?: throw IllegalStateException("${player.seat} 号没有模型") },
    )

    val state: StateFlow<WerewolfUiState> = controller.state

    /** 轮到人类时非空，界面据此弹出输入区。 */
    val humanTurn: StateFlow<HumanTurn?> = human.turn

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _summaries = MutableStateFlow<List<WerewolfGameSummary>>(emptyList())
    val summaries: StateFlow<List<WerewolfGameSummary>> = _summaries.asStateFlow()

    /** 人类坐几号。null 表示观战。 */
    private val _humanSeat = MutableStateFlow<Int?>(null)
    val humanSeat: StateFlow<Int?> = _humanSeat.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 好友 id → 那个模型的连通状况。只存内存，不进存档。 */
    private val _apiChecks = MutableStateFlow<Map<String, ApiCheck>>(emptyMap())
    val apiChecks: StateFlow<Map<String, ApiCheck>> = _apiChecks.asStateFlow()

    /** 这一轮检测有没有跑完。配置页据此把「检测连接」变成「检测中」。 */
    private val _verifying = MutableStateFlow(false)
    val verifying: StateFlow<Boolean> = _verifying.asStateFlow()

    /**
     * 保证同一时刻只有一轮探活在跑。
     *
     * 用户完全可能在检测还没回来时又点一次，或者点完检测直接去点开局；
     * 不串行化就会出现两拨请求、结果互相覆盖，红绿乱跳。
     */
    private val probeLock = Mutex()

    init {
        refresh()
    }

    /** 关掉配置页上的错误提示。 */
    fun clearError() {
        _error.value = null
    }

    /** 刷新好友列表和战绩列表。 */
    fun refresh() {
        viewModelScope.launch {
            runCatching { friendRepository.load().map { it.toFriend() } }
                .onSuccess { _friends.value = it }
            runCatching { store.summaries() }
                .onSuccess { _summaries.value = it }
        }
    }

    /**
     * 主动检测选中的这些好友能不能调通。
     *
     * [force] 为 true：用户明确点了按钮，即使刚测过也重测一遍。
     * 刚测完就是全绿时再点一次会白花钱，但那比「点了按钮什么也没发生」好——
     * 后者的后果是用户开始怀疑这个功能坏了。
     */
    fun checkApi(friendIds: Collection<String>) {
        if (friendIds.isEmpty()) return
        if (state.value.running) return
        viewModelScope.launch {
            val friends = _friends.value.ifEmpty { friendRepository.load().map { it.toFriend() } }
            val allOk = runProbe(friends.filter { it.id in friendIds }, force = true)
            // 用户点这个按钮就是要解掉那条红字，全绿了还挂着会让人以为没生效。
            // 万一是别的原因（比如人数不对），点开局会重新提示一次，不会被藏掉
            if (allOk) _error.value = null
        }
    }

    /**
     * 开一局。
     *
     * [friendIds] 是参与的好友；[WerewolfMode.PLAY] 时会额外把「你」加进名单，
     * 所以玩的时候只需要选 7 个好友（8 人局）。
     *
     * 座位号由洗牌决定，不固定给「你」1 号——发言顺序是座位顺序，固定 1 号等于每局都先手。
     *
     * [skipCheck] 跳过连通性检查。只在用户已经看见红灯、仍然选择开局时用：
     * 请求能从配置页一路走到这里，说明他读过失败原因了。
     */
    fun startGame(
        mode: WerewolfMode,
        friendIds: List<String>,
        config: WerewolfConfig = WerewolfConfig(),
        skipCheck: Boolean = false,
    ) {
        if (state.value.running) return
        viewModelScope.launch {
            try {
                val friends = _friends.value.ifEmpty { friendRepository.load().map { it.toFriend() } }
                val chosen = friendIds.mapNotNull { id -> friends.firstOrNull { it.id == id } }

                val expected = config.playerCount - if (mode == WerewolfMode.PLAY) 1 else 0
                if (chosen.size != expected) {
                    _error.value = "需要选 $expected 位好友，当前选了 ${chosen.size} 位"
                    return@launch
                }

                // 开局前先确认每个模型真的能回话。这一步失败比打到第三天某个 AI 突然
                // 报错要好得多：那时牌桌已经铺开，用户分不清是游戏坏了还是模型坏了
                if (!skipCheck && !verifyReachable(chosen)) return@launch

                val roster = chosen.map { RosterEntry(id = it.id, name = it.name) } +
                    if (mode == WerewolfMode.PLAY) {
                        listOf(RosterEntry(id = SELF_ID, name = "你"))
                    } else {
                        emptyList()
                    }

                val game = WerewolfGame.deal(config, roster, Random.Default)
                val humanSeat = if (mode == WerewolfMode.PLAY) {
                    game.players.first { it.id == SELF_ID }.seat
                } else {
                    null
                }

                _humanSeat.value = humanSeat
                agents = buildAgents(game.players, chosen.associateBy { it.id }, humanSeat)
                _error.value = null
                controller.start(UUID.randomUUID().toString(), game)
                controller.play()
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                // 好友被删、厂商不支持、发牌校验不通过都会走到这里。
                // 这些必须变成界面上的一句话，不能让它崩掉——否则用户点一次「开局」app 就没了
                _error.value = t.message?.take(200) ?: "开局失败，请重试"
            }
        }
    }

    /** 续上最近一局没打完的。 */
    fun resumeLatest() {
        if (state.value.running) return
        viewModelScope.launch {
            try {
                val (id, table) = store.latestUnfinished() ?: run {
                    _error.value = "没有未完成的对局"
                    return@launch
                }
                val friends = _friends.value.ifEmpty { friendRepository.load().map { it.toFriend() } }
                val humanSeat = table.game.players.firstOrNull { it.id == SELF_ID }?.seat

                // 续档也要检：这一局可能是昨天开的，Key 过期、模型下线都在这中间发生过
                val participants = table.game.players
                    .filter { it.seat != humanSeat }
                    .mapNotNull { player -> friends.firstOrNull { it.id == player.id } }
                if (!verifyReachable(participants)) return@launch

                _humanSeat.value = humanSeat
                agents = buildAgents(table.game.players, friends.associateBy { it.id }, humanSeat)
                _error.value = null
                controller.resume(id)
                controller.play()
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _error.value = t.message?.take(200) ?: "续档失败，请重试"
            }
        }
    }

    /** 放弃当前这局，存档一并删掉。 */
    fun abandon() {
        viewModelScope.launch {
            controller.abandon()
            _humanSeat.value = null
            agents = emptyMap()
            refresh()
        }
    }

    // ---------------------------------------------------------------------
    // 连通性检查
    // ---------------------------------------------------------------------

    /**
     * 确认这些座位背后的模型都能回话。
     *
     * 只看 [ApiCheckState.FAILED]：还没测的说明刚被跳过（同厂商已绿），不能当成失败。
     */
    private suspend fun verifyReachable(chosen: List<Friend>): Boolean {
        if (chosen.isEmpty()) return true
        runProbe(chosen, force = false)

        val checks = _apiChecks.value
        val failed = chosen.filter { checks[it.id]?.state == ApiCheckState.FAILED }
        if (failed.isEmpty()) return true

        val names = failed.joinToString("、") { it.name }
        val reason = checks[failed.first().id]?.message.orEmpty()
        // 把「谁的哪把 Key 不行」写清楚：只说「开局失败」，用户只能一个个去试
        _error.value = buildString {
            append("${failed.size} 个座位连不上：$names")
            if (reason.isNotBlank()) append("（$reason）")
            append("。可以重试、换人，或者仍然开局")
        }.take(200)
        return false
    }

    /**
     * 跑一轮探活并把结果写进 [apiChecks]，返回「是否全部通过」。
     *
     * 已通过且还新鲜的结果会被跳过（见 [planProbes]），所以反复调用不会反复花钱。
     */
    private suspend fun runProbe(chosen: List<Friend>, force: Boolean): Boolean = probeLock.withLock {
        if (chosen.isEmpty()) return@withLock true

        val tasks = planProbes(
            targets = chosen.toProbeTargets(),
            known = _apiChecks.value,
            force = force,
            now = System.currentTimeMillis(),
        )
        if (tasks.isEmpty()) return@withLock true

        _verifying.value = true
        try {
            // 先点亮成「检测中」：点了按钮界面毫无变化，用户会以为没点上，然后再点一次
            _apiChecks.update { current ->
                current + tasks.flatMap { it.friendIds }.associateWith {
                    ApiCheck(state = ApiCheckState.CHECKING)
                }
            }

            // AI 实例在这里一次性取好：aiFor 内部是普通 MutableMap，
            // 放到并发分支里去建会有竞争，而建实例本身并不慢
            val ais = tasks.associateWith { task ->
                runCatching { aiFor(task.brand) }.getOrNull()
            }

            val results = coroutineScope {
                tasks.map { task ->
                    async {
                        val ai = ais[task]
                        val result = if (ai == null) {
                            ApiProbeResult(ok = false, elapsedMs = 0, message = "暂不支持的厂商：${task.brand}")
                        } else {
                            ai.probe(task.model)
                        }
                        task to result
                    }
                }.awaitAll()
            }

            val at = System.currentTimeMillis()
            _apiChecks.update { current ->
                current + results.flatMap { (task, result) ->
                    // 同组座位共享一个结果：它们背后是同一个厂商、同一个模型、同一把 Key
                    task.friendIds.map { id ->
                        id to ApiCheck(
                            state = if (result.ok) ApiCheckState.OK else ApiCheckState.FAILED,
                            message = result.message,
                            elapsedMs = result.elapsedMs,
                            at = at,
                        )
                    }
                }
            }
            results.all { it.second.ok }
        } catch (cancelled: CancellationException) {
            // 退出页面时要把「检测中」擦掉，否则下次进来那几个座位会永远转圈
            _apiChecks.update { current ->
                current.filterValues { it.state != ApiCheckState.CHECKING }
            }
            throw cancelled
        } finally {
            _verifying.value = false
        }
    }

    // ---------------------------------------------------------------------
    // 人类操作
    // ---------------------------------------------------------------------

    /** 投票 / 选目标。[target] 为 null 表示弃票或放弃。 */
    fun submitTarget(target: Int?, reason: String = "") {
        val turn = humanTurn.value ?: return
        val payload = if (turn.jsonMode) {
            json.encodeToString(VoteChoice(target, reason))
        } else {
            reason
        }
        human.submit(payload)
    }

    /** 狼刀 / 查验：这两个必须有目标，不能用 [submitTarget] 传 null。 */
    fun submitSeat(target: Int, reason: String = "") {
        if (humanTurn.value?.jsonMode != true) return
        human.submit(json.encodeToString(SeatChoice(target, reason)))
    }

    /** 女巫用药。 */
    fun submitWitch(heal: Boolean, poison: Int?, reason: String = "") {
        if (humanTurn.value?.jsonMode != true) return
        human.submit(json.encodeToString(WitchChoice(heal, poison, reason)))
    }

    /** 自由发言。 */
    fun submitSpeech(text: String) {
        if (humanTurn.value?.jsonMode == true) return
        human.submit(text)
    }

    // ---------------------------------------------------------------------

    /**
     * 按座位分配模型。
     *
     * 人类座位包着 [HumanSeatAI]——编排层因此完全不需要知道哪个座位是人。
     * 找不到好友时直接抛出而不是塞一个假人：那样会打出一局「人格是空白」的怪游戏，
     * 比明确报错难查得多。
     */
    private fun buildAgents(
        players: List<WerewolfPlayer>,
        friendsById: Map<String, Friend>,
        humanSeat: Int?,
    ): Map<Int, SeatAgent> = players.associate { player ->
        val agent = if (player.seat == humanSeat) {
            SeatAgent(ai = human, model = "human", persona = "玩家本人")
        } else {
            val friend = friendsById[player.id]
                ?: throw IllegalStateException("找不到 ${player.name}（${player.id}）的资料")
            val ai = aiFor(friend.brand)
                ?: throw IllegalStateException("暂不支持的厂商：${friend.brand}")
            SeatAgent(
                ai = ai,
                model = friend.model,
                persona = friend.prompt,
                thinkingEnabled = friend.thinkingEnabled,
            )
        }
        player.seat to agent
    }

    private fun aiFor(brand: String): IAI? {
        ais[brand]?.let { return it }
        val created = aiFactory.createAI(brand) ?: return null
        ais[brand] = created
        return created
    }

    private companion object {
        /** 人类座位的固定 id。存档里靠它认出「哪个座位是我」。 */
        const val SELF_ID = "__self__"
    }
}
