package com.feature.werewolf.vm

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.ai.entity.IAI
import com.core.ai.factory.AIFactory
import com.core.ai.model.Friend
import com.core.ai.model.toFriend
import com.core.ai.probe.ApiProbeResult
import com.core.ai.probe.probe
import com.core.repository.storage.AIProviderSettings
import com.core.repository.settings.TTSProviderCatalog
import com.core.repository.storage.FriendRepository
import com.core.repository.storage.TTSProviderSettings
import com.core.repository.storage.WerewolfSettings
import com.core.tts.CloudSpeechEngine
import com.core.tts.SpeechCandidate
import com.core.tts.SpeechEngine
import com.core.tts.SpeechFilter
import com.core.tts.SpeechQueue
import com.core.tts.SpeechTiming
import com.feature.werewolf.decision.SeatChoice
import com.feature.werewolf.decision.VoteChoice
import com.feature.werewolf.decision.WitchChoice
import com.feature.werewolf.engine.HumanSeatAI
import com.feature.werewolf.engine.HumanTurn
import com.feature.werewolf.engine.SeatAgent
import com.feature.werewolf.engine.SeatVoices
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
import kotlinx.coroutines.withTimeoutOrNull
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
    /** TTS 凭证独立加载：朗读用的 Key 和 chat 的不是一把。 */
    private val ttsSettings = TTSProviderSettings(application)
    private val werewolfSettings = WerewolfSettings(application)
    private val aiFactory = AIFactory()

    // ---- 自动朗读 ----------------------------------------------------
    // 引擎是 Android 对象，必须在 ViewModel 里创建（它要 applicationContext / MediaPlayer）。
    // 用云端 MiniMax：系统 TTS 在国产 ROM 上绑定不稳，同一 APK 时响时不响。
    // 抽象成接口，下面这套调度逻辑（筛选/去重/闸门）与具体引擎完全解耦。
    private val speechEngine: SpeechEngine = CloudSpeechEngine(application)
    private val speechQueue = SpeechQueue()

    private val _autoSpeak = MutableStateFlow(true)
    val autoSpeak: StateFlow<Boolean> = _autoSpeak.asStateFlow()

    /** 朗读开关是否真的能出声。系统没装中文语音包时为 false，界面据此提示。 */
    private val _speechReady = MutableStateFlow(speechEngine.available)
    val speechReady: StateFlow<Boolean> = _speechReady.asStateFlow()

    /**
     * 界面可以显示到第几条消息（原始 transcript 下标，-1 表示还没有）。
     *
     * 朗读开着时跟着队列消费走：念到哪、显示到哪，文字不再抢在语音前面。
     * 朗读关着或没语音包时直接给末尾，退回「全部立即显示」的普通行为——
     * 否则闸门会把文字永久藏住，用户关了语音反而看不到任何发言。
     */
    private val _revealedIndex = MutableStateFlow(-1)
    val revealedIndex: StateFlow<Int> = _revealedIndex.asStateFlow()

    /**
     * 此刻正在被朗读的那条消息属于哪个座位。null 表示没在念，或念的东西没有座位
     * （上帝播报、投票汇总）。
     *
     * 和 [progress] 的 `seat` 不是一回事：那是「引擎此刻在问谁」，这是「喇叭此刻在念谁」。
     * 引擎比朗读跑得快，两者经常不是同一个人，牌桌上要亮的是**正在说话**的那个。
     */
    private val _speakingSeat = MutableStateFlow<Int?>(null)
    val speakingSeat: StateFlow<Int?> = _speakingSeat.asStateFlow()

    /**
     * 喇叭手里还有没有活：队列里积压着没念的行，或者某一句正在念。
     *
     * 界面拿它决定「能不能提前显示下一条正在生成的发言」。只看 [revealedIndex] 不够：
     * 念 A 的时候进度已经推到 A，看起来「追平了」，可这句还没播完——此时把 B 的流式
     * 文字打出来，用户看到的就是「下一个人的对话框先冒出来、然后又消失」（等 B 真被
     * 念到才重新出现）。
     */
    private val _speechBusy = MutableStateFlow(false)
    val speechBusy: StateFlow<Boolean> = _speechBusy.asStateFlow()

    /** 串行朗读循环：一次只在读一句，上一句读完才 poll 下一句。 */
    private var speakJob: Job? = null

    /** 厂商 → 实例。同一厂商的多个座位共用一个实例，和聊天室的做法一致。 */
    private val ais = mutableMapOf<String, IAI>()

    private val human = HumanSeatAI()

    init {
        viewModelScope.launch {
            runCatching { providerSettings.load() }
            // TTS 凭证独立异步加载；available / 朗读都依赖它的内存快照。
            runCatching { ttsSettings.load() }
            // 云端引擎的 available 取决于是否配了 TTS Key，而凭证是异步 load 的：
            // 构造时一定读不到，加载完必须主动重读一次，否则喇叭一直灰。
            speechEngine.retry()
            speechEngine.onReady?.invoke(speechEngine.available)
        }
    }

    private val json = Json { encodeDefaults = true }

    /** 存档生成时绑定的座位表，续档时按同样的规则重建。 */
    private var agents: Map<Int, SeatAgent> = emptyMap()

    /**
     * 座位 → 该座位好友的朗读音色。
     *
     * 单独存一张表而不是塞进 [SeatAgent]：引擎层不该知道 TTS 的存在。
     * 开局/续档时和 agents 一起按同一份 friends 建好，弃局时一起清空。
     */
    private var seatVoiceIds: Map<Int, String?> = emptyMap()

    /** 座位 → 该座位好友指定的 TTS 配置（TTSProvider.id），null = 默认配置。 */
    private var seatTtsConfigIds: Map<Int, String?> = emptyMap()

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
     * 把新增的消息放进朗读队列。
     *
     * **只有「说出口的话」会进声音**：白天发言、狼人夜聊（观战时才听得到）。
     * 投票理由、夜里查验用药的念头属于内心活动（[MessageKind.isInnerThought]），
     * 任何模式都不读——一圈人投票就是十几段独白，念出来既拖节奏，又和真正的
     * 发言混在一起分不清。它们照常进 transcript、照常显示，只是不发声。
     *
     * 参与的局再收紧一层：只读 audience 为 null 的发言。
     * 私有消息（狼人夜聊、夜间行动）本来 audience 就不包含人类座位，
     * `transcriptFor` 里根本不会出现；这里再判一次 isPublic 是双保险：
     * 语音是不可撤回的，一旦读错一句「我是狼」，说出去的话收不回来。
     * 不读自己说的话：自己刚打完的字再念一遍是纯粹的噪音。
     *
     * **纯观战（上帝视角）读得更多**：观战没有「自己」这一格，夜聊和上帝播报
     * 本来就摊在屏幕上，不念等于把剧情念丢一半。私密性由调用方保证：
     * 只有 `humanSeat == null` 才为 true。
     */
    private fun enqueueSpeech(transcript: List<com.feature.werewolf.engine.WerewolfMessage>) {
        if (!_autoSpeak.value) return
        val self = _humanSeat.value
        // 观战 = 上帝视角：没有座位，能念的都念
        val readEverything = self == null
        val candidates = transcript.mapIndexed { index, message ->
            val speakable = SpeechFilter.speakable(
                isPublicSpeech = message.kind.isSpokenOutLoud && message.isPublic,
                speakerSeat = message.seat,
                selfSeat = self,
                text = message.text,
                readEverything = readEverything,
                isInnerThought = message.kind.isInnerThought,
            )
            SpeechCandidate(
                text = message.text,
                speakable = speakable,
                // 只有发言座位才有音色/TTS 配置；上帝播报 seat=null、人类自己的话不朗读，取不到就是 null。
                voiceId = message.seat?.let { seatVoiceIds[it] },
                ttsConfigId = message.seat?.let { seatTtsConfigIds[it] },
                seat = message.seat,
            )
        }
        val added = speechQueue.offer(candidates)
        if (added.isNotEmpty()) {
            // 在启动循环之前就置忙：从入队到协程真正派发之间还有一段空档，
            // 那时界面不该认为「喇叭已经闲了」。
            _speechBusy.value = true
            startSpeakLoop()
        }
    }

    /** 串行读：poll 一句、等它读完、再读下一句。同时只会有一个循环在跑。 */
    private fun startSpeakLoop() {
        if (speakJob?.isActive == true) return
        speakJob = viewModelScope.launch {
            try {
                while (isActive && _autoSpeak.value) {
                    val line = speechQueue.poll() ?: break
                    // 取出就放行文字，不等念完——用户边看边听；等念完才显示
                    // 会变成「听完了才给看」，比文字抢跑更难受。
                    _revealedIndex.value = speechQueue.revealedIndex
                    // 不出声的行（参与局里的投票/播报）瞬间过，只推进显示进度。
                    // 这里不点亮座位：它们没有「说话的人」，亮一下反而像在念错人。
                    if (!line.speakable) continue
                    // 喇叭开始念谁，牌桌上就亮谁。必须放在 speakable 之后——
                    // 狼人夜聊在参与的局里 speakable=false，亮了就等于把夜里的活人摊出来。
                    _speakingSeat.value = line.seat
                    // 一句一个超时：合成被挂住、播放回调丢失时，宁可这句不出声，
                    // 也不能把队列（以及跟着队列走的界面文字）永久卡在这里。
                    val finished = withTimeoutOrNull(SpeechTiming.totalTimeoutMs(line.text)) {
                        suspendCancellableCoroutine<Unit> { cont ->
                            speechEngine.speak(
                                text = line.text,
                                utteranceId = line.index.toString(),
                                onDone = { if (cont.isActive) cont.resume(Unit) },
                                voiceId = line.voiceId,
                                ttsConfigId = line.ttsConfigId,
                            )
                            cont.invokeOnCancellation { speechEngine.stop() }
                        }
                        true
                    }
                    if (finished != true) {
                        Log.w(TAG, "朗读超时，跳过第 ${line.index} 条（${line.text.length} 字）")
                        speechEngine.stop()
                    }
                    _speakingSeat.value = null
                }
            } finally {
                // 循环正常结束、被取消、抛异常，都要把高亮收掉：
                // 留一个永远亮着的座位，用户会以为那个人说了十分钟话
                _speakingSeat.value = null
                // 队列已空（或被清），喇叭闲下来了，界面可以重新显示正在生成的下一条
                _speechBusy.value = false
            }
        }
    }

    /**
     * 停在这里等朗读把当前阶段念完。
     *
     * 引擎原来是一路跑到天亮的：语音还在念第一夜的狼人商议，投票结果早就算好了。
     * 界面上虽然用闸门把文字压住了，但那是「藏」不是「同步」——引擎侧已经走到
     * 第二天，重开、暂停、看进度都会露出真实位置，而且越跑越远，最后差出好几分钟。
     * 这里在每个阶段边界踩一脚刹车，让两边真正走在一起。
     *
     * 关朗读、没配语音包、或等待超时都直接放行：声音可以缺，局面不能挂。
     */
    private suspend fun awaitSpeechCaughtUp() {
        if (!_autoSpeak.value || !_speechReady.value) return
        val target = (state.value.table?.transcript?.size ?: return) - 1
        val start = _revealedIndex.value
        if (target <= start) return
        // 同时等两个条件：追上末尾，或者用户在等的过程中把朗读关了。
        // 只等 _revealedIndex 的话，关朗读会清空队列、进度再也不动，
        // 引擎要空等满 5 分钟才继续——用户看到的就是「点了静音，牌桌反而僵住了」。
        val done = withTimeoutOrNull(SpeechTiming.CATCH_UP_TIMEOUT_MS) {
            combine(_revealedIndex, _autoSpeak) { revealed, speak -> revealed >= target || !speak }
                .first { it }
            true
        }
        if (done != true) {
            Log.w(TAG, "朗读 ${SpeechTiming.CATCH_UP_TIMEOUT_MS / 1000}s 内没追平（$start/$target），引擎继续")
        }
    }

    /** 换局/续档时把队列和显示进度一起重置：历史全显示，新消息才跟朗读走。 */
    private fun resetSpeech(fromIndex: Int) {
        speechQueue.reset(fromIndex)
        _revealedIndex.value = fromIndex - 1
        _speakingSeat.value = null
        _speechBusy.value = false
    }

    /** 重开朗读时跳过关闭期间积下的消息，显示进度也一起跳到当前末尾。 */
    private fun skipSpeechTo(index: Int) {
        speechQueue.skipTo(index)
        _revealedIndex.value = speechQueue.revealedIndex
    }

    /** 停掉朗读并清空队列。开局、续档、暂停、放弃都走这里。 */
    private fun stopSpeech(clearQueue: Boolean) {
        speakJob?.cancel()
        speakJob = null
        if (clearQueue) speechQueue.clear()
        _speakingSeat.value = null
        _speechBusy.value = false
        speechEngine.stop()
    }

    /** 开关：关掉时立刻闭嘴，打开时把当前已有的发言重新入队（从游标继续，不重读历史）。 */
    fun setAutoSpeak(enabled: Boolean) {
        _autoSpeak.value = enabled
        viewModelScope.launch { werewolfSettings.setAutoSpeak(enabled) }
        if (enabled) {
            // 关着的时候积下的发言不补念，从「现在」开始听
            skipSpeechTo(state.value.table?.transcript?.size ?: 0)
            // 引擎上次绑定失败（喇叭灰）时，借这次「打开」重建一次。
            // 国产 ROM 的 init 偶发失败，不重建就一直是灰的、出不了声。
            if (!speechEngine.initialized || !speechEngine.available) speechEngine.retry()
        } else {
            stopSpeech(clearQueue = true)
        }
    }

    /** 点灰色喇叭时单独重试绑定引擎，不切换朗读开关。 */
    fun retrySpeech() {
        speechEngine.retry()
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

                val friendsById = chosen.associateBy { it.id }
                stopSpeech(clearQueue = true)
                _humanSeat.value = humanSeat
                agents = buildAgents(game.players, friendsById, humanSeat)
                seatVoiceIds = buildSeatVoiceIds(game.players, friendsById, humanSeat)
                seatTtsConfigIds = buildSeatTtsConfigIds(game.players, friendsById)
                _error.value = null
                // 全新开局：消息流是空的，从第 0 条开始读。
                // 必须在 controller.start 之前复位——start 会发状态，
                // 采集器异步跑，放后面会漏掉早期发言或读到上一局的残留。
                resetSpeech(fromIndex = 0)
                controller.start(UUID.randomUUID().toString(), game)
                controller.play(awaitStage = { awaitSpeechCaughtUp() })
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
                val friendsById = friends.associateBy { it.id }
                agents = buildAgents(table.game.players, friendsById, humanSeat)
                seatVoiceIds = buildSeatVoiceIds(table.game.players, friendsById, humanSeat)
                seatTtsConfigIds = buildSeatTtsConfigIds(table.game.players, friendsById)
                stopSpeech(clearQueue = true)
                _humanSeat.value = humanSeat
                // 续档：已有的一大段历史不补念，从当前末尾往下听。
                // 同样要在 resume 发状态之前复位，避免采集器读到历史后补念。
                resetSpeech(fromIndex = table.transcript.size)
                if (!controller.resume(id)) {
                    _error.value = "这条存档读不出来，可能已经损坏"
                    return@launch
                }
                _error.value = null
                controller.play(awaitStage = { awaitSpeechCaughtUp() })
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
                seatVoiceIds = emptyMap()
                seatTtsConfigIds = emptyMap()
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
     * 按座位取朗读音色：好友配了就用配的，没配的从这家服务商里挑一个**还没被占用**的。
     *
     * 分配逻辑在 [SeatVoices]（纯函数、有单测），这里只负责把「好友资料」翻成它的入参：
     * 好友的 `ttsConfigId` 决定这个座位实际走哪家 TTS，回落规则必须和
     * `CloudSpeechEngine` 保持一致（`byId(id) ?: default()`），否则会拿 A 家的音色去 B 家合成。
     */
    private fun buildSeatVoiceIds(
        players: List<WerewolfPlayer>,
        friendsById: Map<String, Friend>,
        humanSeat: Int?,
    ): Map<Int, String?> = SeatVoices.assign(
        seats = players.map { it.seat },
        humanSeat = humanSeat,
        explicit = players.associate { player -> player.seat to friendsById[player.id]?.voiceId },
        providerOf = { seat ->
            val configId = players.firstOrNull { it.seat == seat }
                ?.let { friendsById[it.id]?.ttsConfigId }
            (TTSProviderCatalog.byId(configId) ?: TTSProviderCatalog.default()).id
        },
    )

    /** 按座位取朗读配置：好友配了就用配的，没配（null）走默认配置。 */
    private fun buildSeatTtsConfigIds(
        players: List<WerewolfPlayer>,
        friendsById: Map<String, Friend>,
    ): Map<Int, String?> = players.associate { player ->
        player.seat to friendsById[player.id]?.ttsConfigId
    }

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
        const val TAG = "WerewolfViewModel"
    }
}
