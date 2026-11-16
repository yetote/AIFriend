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
import com.core.repository.storage.AIProviderSettings
import com.core.repository.storage.FriendRepository
import com.core.repository.storage.WerewolfSettings
import com.core.tts.SpeechCandidate
import com.core.tts.SpeechEngine
import com.core.tts.SpeechFilter
import com.core.tts.SpeechQueue
import com.core.tts.SystemSpeechEngine
import com.feature.werewolf.decision.SeatChoice
import com.feature.werewolf.decision.VoteChoice
import com.feature.werewolf.decision.WitchChoice
import com.feature.werewolf.engine.HumanSeatAI
import com.feature.werewolf.engine.HumanTurn
import com.feature.werewolf.engine.SeatAgent
import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfController
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.SELF_PLAYER_ID
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPlayer
import com.feature.werewolf.store.WerewolfGameSummary
import com.feature.werewolf.store.WerewolfStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
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
 * 从列表里翻出来看的一局。
 *
 * 单独拿一个类型而不是复用 `WerewolfUiState`：这是**只读的**回顾，
 * 它不在控制器里，也不是「当前这一局」——混进 `state` 的话，
 * 「有没有在打」这件事就得靠别处再判断一次，很容易出现
 * 「明明在看上周的复盘，牌桌却以为自己有局在跑」。
 */
data class WerewolfRecordView(
    val id: String,
    val table: WerewolfTable,
    /** 那一局我是几号；纯观战为 null。 */
    val humanSeat: Int?,
)

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
    /**
     * 模型服务的 Key / baseUrl。
     *
     * 必须在这里主动加载一次：`AICredentials` 只是 DataStore 的内存快照，
     * 只有调过 [AIProviderSettings.load] 才会被填上。聊天室和新建伙伴页都在自己的
     * init 里做了这件事，狼人杀当时漏了 —— 从首页直接进来的进程快照是空的，
     * 于是每个厂商都「没有 Key」，探活请求不带 Authorization，全部 401。
     */
    private val providerSettings = AIProviderSettings(application)
    private val werewolfSettings = WerewolfSettings(application)
    private val aiFactory = AIFactory()

    // ---- 自动朗读 ----------------------------------------------------
    // 引擎是 Android 对象，必须在 ViewModel 里创建（它要 applicationContext）。
    // 抽象成接口是为了将来换云端语音时，下面这套调度逻辑一行都不用动。
    private val speechEngine: SpeechEngine = SystemSpeechEngine(application)
    private val speechQueue = SpeechQueue()

    private val _autoSpeak = MutableStateFlow(true)
    val autoSpeak: StateFlow<Boolean> = _autoSpeak.asStateFlow()

    /** 朗读开关是否真的能出声。系统没装中文语音包时为 false，界面据此提示。 */
    private val _speechReady = MutableStateFlow(speechEngine.available)
    val speechReady: StateFlow<Boolean> = _speechReady.asStateFlow()

    /** 串行朗读循环：一次只在读一句，上一句读完才 poll 下一句。 */
    private var speakJob: Job? = null

    /** 厂商 → 实例。同一厂商的多个座位共用一个实例，和聊天室的做法一致。 */
    private val ais = mutableMapOf<String, IAI>()

    private val human = HumanSeatAI()

    init {
        viewModelScope.launch { runCatching { providerSettings.load() } }
    }

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

    /**
     * 正在打的那一局的驱动协程。
     *
     * 有它才能「暂停」：`controller.play()` 是个长循环，取消这个协程就停在了
     * 最近一个阶段边界上（每个阶段都已落档），回到列表页不会继续偷偷烧钱。
     */
    private var playJob: Job? = null

    private val _record = MutableStateFlow<WerewolfRecordView?>(null)

    /** 从列表里点开的一局（只读）。非空时界面展示它的复盘。 */
    val record: StateFlow<WerewolfRecordView?> = _record.asStateFlow()

    init {
        refresh()

        // 中文语音包是异步探测的，构造时拿不到结果。等它回调再更新界面，
        // 否则「朗读」开关一进页面就被误标成不可用。
        speechEngine.onReady = { ready -> _speechReady.value = ready }
        // 回调可能早于订阅发生（初始化在另一个线程），补拉一次当前值
        if (speechEngine.initialized) _speechReady.value = speechEngine.available

        viewModelScope.launch { runCatching { werewolfSettings.autoSpeak.collect { _autoSpeak.value = it } } }

        // 消息流一有新发言就喂给朗读队列。采集和播放分开：
        // 采集只管把话放进队列（很快），播放是长挂起的串行循环，
        // 两者搅在一起会让「暂停」既停不干净、又容易漏掉正在读的那句。
        state
            .map { it.table?.transcript.orEmpty() }
            .distinctUntilChanged()
            .onEach { transcript -> enqueueSpeech(transcript) }
            .launchIn(viewModelScope)
    }

    // -----------------------------------------------------------------
    // 自动朗读
    // -----------------------------------------------------------------

    /**
     * 把新增的公开发言放进朗读队列。
     *
     * 筛选规则按用户的选择收紧到「只有 AI 说的话」：
     * - 只读 [MessageKind.SPEECH]：上帝播报（DIVINE）不读——那是事实陈述，
     *   读出来既长又和发言混在一起；投票表态（VOTE）不读——屏幕上一目了然。
     * - 私有消息（狼人夜聊、夜间行动）本来 audience 就不包含人类座位，
     *   `transcriptFor` 里根本不会出现；这里再判一次 isPublic 是双保险：
     *   语音是不可撤回的，一旦读错一句「我是狼」，说出去的话收不回来。
     * - 不读自己说的话：自己刚打完的字再念一遍是纯粹的噪音。
     */
    private fun enqueueSpeech(transcript: List<com.feature.werewolf.engine.WerewolfMessage>) {
        if (!_autoSpeak.value) return
        val self = _humanSeat.value
        val candidates = transcript.mapIndexed { index, message ->
            val speakable = SpeechFilter.speakable(
                isPublicSpeech = message.kind == MessageKind.SPEECH && message.isPublic,
                speakerSeat = message.seat,
                selfSeat = self,
                text = message.text,
            )
            SpeechCandidate(text = message.text, speakable = speakable)
        }
        val added = speechQueue.offer(candidates)
        if (added.isNotEmpty()) startSpeakLoop()
    }

    /** 串行读：poll 一句、等它读完、再读下一句。同时只会有一个循环在跑。 */
    private fun startSpeakLoop() {
        if (speakJob?.isActive == true) return
        speakJob = viewModelScope.launch {
            while (isActive && _autoSpeak.value) {
                val line = speechQueue.poll() ?: break
                suspendCancellableCoroutine<Unit> { cont ->
                    speechEngine.speak(line.text, line.index.toString()) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    cont.invokeOnCancellation { speechEngine.stop() }
                }
            }
        }
    }

    /** 停掉朗读并清空队列。开局、续档、暂停、放弃都走这里。 */
    private fun stopSpeech(clearQueue: Boolean) {
        speakJob?.cancel()
        speakJob = null
        if (clearQueue) speechQueue.clear()
        speechEngine.stop()
    }

    /** 开关：关掉时立刻闭嘴，打开时把当前已有的发言重新入队（从游标继续，不重读历史）。 */
    fun setAutoSpeak(enabled: Boolean) {
        _autoSpeak.value = enabled
        viewModelScope.launch { werewolfSettings.setAutoSpeak(enabled) }
        if (enabled) {
            // 关着的时候积下的发言不补念，从「现在」开始听
            speechQueue.skipTo(state.value.table?.transcript?.size ?: 0)
        } else {
            stopSpeech(clearQueue = true)
        }
    }

    override fun onCleared() {
        stopSpeech(clearQueue = true)
        speechEngine.onReady = null
        speechEngine.release()
        super.onCleared()
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
        val previous = playJob
        playJob = viewModelScope.launch {
            // 上一局如果是「暂停」状态，它的协程可能还停在最后一次落档里；
            // 等它真的结束再开新局，免得两边同时写库
            previous?.cancelAndJoin()
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
                        listOf(RosterEntry(id = SELF_PLAYER_ID, name = "你"))
                    } else {
                        emptyList()
                    }

                val game = WerewolfGame.deal(config, roster, Random.Default)
                val humanSeat = if (mode == WerewolfMode.PLAY) {
                    game.players.first { it.id == SELF_PLAYER_ID }.seat
                } else {
                    null
                }

                stopSpeech(clearQueue = true)
                _humanSeat.value = humanSeat
                agents = buildAgents(game.players, chosen.associateBy { it.id }, humanSeat)
                _error.value = null
                // 全新开局：消息流是空的，从第 0 条开始读。
                // 必须在 controller.start 之前复位——start 会发状态，
                // 采集器异步跑，放后面会漏掉早期发言或读到上一局的残留。
                speechQueue.reset(fromIndex = 0)
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

    /**
     * 续上指定的某一局。
     *
     * 以前只认「最近一局没打完的」，现在列表能摆出好几局，点哪一局就该续哪一局。
     */
    fun resume(id: String) {
        val previous = playJob
        playJob = viewModelScope.launch {
            // 上一局可能刚「暂停」、协程还没真的停下（取消是异步的），等它收尾
            previous?.cancelAndJoin()
            try {
                val table = store.load(id) ?: run {
                    _error.value = "这条存档读不出来，可能已经损坏"
                    return@launch
                }
                val friends = _friends.value.ifEmpty { friendRepository.load().map { it.toFriend() } }
                val humanSeat = table.game.players.firstOrNull { it.id == SELF_PLAYER_ID }?.seat

                // 续档也要检：这一局可能是昨天开的，Key 过期、模型下线都在这中间发生过
                val participants = table.game.players
                    .filter { it.seat != humanSeat }
                    .mapNotNull { player -> friends.firstOrNull { it.id == player.id } }
                if (!verifyReachable(participants)) return@launch

                // 先备好座位再让控制器接手：座位表没建好就 resume，
                // 一旦 play() 起来会立刻撞上「X 号没有模型」
                agents = buildAgents(table.game.players, friends.associateBy { it.id }, humanSeat)
                stopSpeech(clearQueue = true)
                _humanSeat.value = humanSeat
                // 续档：已有的一大段历史不补念，从当前末尾往下听。
                // 同样要在 resume 发状态之前复位，避免采集器读到历史后补念。
                speechQueue.reset(fromIndex = table.transcript.size)
                if (!controller.resume(id)) {
                    _error.value = "这条存档读不出来，可能已经损坏"
                    return@launch
                }
                _error.value = null
                controller.play()
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _error.value = t.message?.take(200) ?: "续档失败，请重试"
            }
        }
    }

    /**
     * 停下手上的这一局，回到列表。
     *
     * 取消协程而不是丢弃局面：每个阶段都已落档，用户下次点「接着打」就从
     * 当前阶段重新开始。留在后台继续跑才是错的——他在看列表，AI 却在替他打牌。
     */
    fun pause() {
        stopSpeech(clearQueue = true)
        val job = playJob
        playJob = null
        viewModelScope.launch {
            // 等它真的停下：取消是异步的，立刻 flush 可能写进一个马上要被覆盖的局面
            job?.cancelAndJoin()
            // 牌桌上可能已经说了几句、但还没到阶段边界，补存一次，
            // 否则「接着打」回来会少掉这几句，看着像退回去了
            runCatching { controller.flush() }
            refresh()
        }
    }

    /** 清掉所有已结束的对局，未完成的留下。存档只增不减迟早会堆成几百条。 */
    fun clearFinished() {
        viewModelScope.launch {
            store.deleteFinished()
            refresh()
        }
    }

    /** 丢掉某一局存档。这一局若正在跑，会先停下来。 */
    fun discard(id: String) {
        viewModelScope.launch {
            // 只停「被删的这一局」的驱动。列表里删一条三周前的旧档，
            // 手上那局不该跟着停——它既没被删，用户也没说要放弃它。
            // 座位表同理：清掉之后当前这局的下一次请求会撞上「X 号没有模型」。
            val isCurrent = id == state.value.gameId
            if (isCurrent) {
                stopSpeech(clearQueue = true)
                playJob?.cancelAndJoin()
                playJob = null
                _humanSeat.value = null
                agents = emptyMap()
            }
            controller.discard(id)
            if (_record.value?.id == id) _record.value = null
            refresh()
        }
    }

    /**
     * 打开一条历史对局看复盘。
     *
     * 只读：不碰控制器。否则看完上周那局再回去，手上的局已经被顶掉了。
     */
    fun openRecord(id: String) {
        viewModelScope.launch {
            val table = store.load(id)
            if (table == null) {
                _error.value = "这条存档读不出来，可能已经损坏"
                return@launch
            }
            _record.value = WerewolfRecordView(
                id = id,
                table = table,
                humanSeat = table.game.players.firstOrNull { it.id == SELF_PLAYER_ID }?.seat,
            )
        }
    }

    /** 关掉复盘，回列表。 */
    fun closeRecord() {
        _record.value = null
    }

    /** 放弃当前这局，存档一并删掉。 */
    fun abandon() {
        stopSpeech(clearQueue = true)
        viewModelScope.launch {
            playJob?.cancelAndJoin()
            playJob = null
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

        // 每次检测前重读一次凭证：用户完全可能刚在设置页换了 Key，
        // 内存快照里还是旧的，测出来的红点会让他以为新 Key 也不行
        withContext(Dispatchers.IO) { runCatching { providerSettings.load() } }

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
}
