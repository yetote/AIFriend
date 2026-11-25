package com.feature.werewolf.vm

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.ai.entity.IAI
import com.core.ai.factory.AIFactory
import com.core.ai.model.Friend
import com.core.ai.model.toFriend
import com.core.ai.probe.FriendProbeStore
import com.core.repository.storage.AIProviderSettings
import com.core.repository.settings.TTSProviderCatalog
import com.core.repository.storage.FriendRepository
import com.core.repository.storage.TTSProviderSettings
import com.core.repository.storage.WerewolfPromptSettings
import com.core.repository.storage.WerewolfSettings
import com.core.tts.CloudSpeechEngine
import com.core.tts.SpeechEngine
import com.feature.werewolf.decision.DirectionChoice
import com.feature.werewolf.decision.ExplodeChoice
import com.feature.werewolf.decision.SeatChoice
import com.feature.werewolf.decision.VoteChoice
import com.feature.werewolf.decision.WitchChoice
import com.feature.werewolf.decision.WithdrawChoice
import com.feature.werewolf.engine.EXPLODE_MARKER
import com.feature.werewolf.engine.HumanSeatAI
import com.feature.werewolf.engine.HumanTurn
import com.feature.werewolf.engine.SeatAgent
import com.feature.werewolf.engine.SeatVoices
import com.feature.werewolf.engine.WerewolfController
import com.feature.werewolf.engine.RetryDecision
import com.feature.werewolf.engine.RetryPrompt
import com.feature.werewolf.engine.RetryableAI
import com.feature.werewolf.engine.WerewolfPacing
import com.feature.werewolf.engine.WerewolfRetryCoordinator
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.prompt.assignPlayerStyles
import com.feature.werewolf.engine.persistence.WerewolfGameFile
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.SELF_PLAYER_ID
import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.SpeechDirection
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPlayer
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WerewolfSpeechLength
import com.feature.werewolf.game.WerewolfTurnLimit
import com.feature.werewolf.replay.ReplayFrame
import com.feature.werewolf.store.WerewolfGameSummary
import com.feature.werewolf.store.WerewolfStore
import com.ui.werewolf.data.UIWerewolfFriend
import com.ui.werewolf.data.UIWerewolfMissingSeat
import com.ui.werewolf.data.UIWerewolfPromptState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /**
     * 那一局生效的玩家设定（提示词）。
     *
     * 从存档里读：设置页只保留最新那份，改过之后旧局就查不到了，
     * 而复盘最常问的一句正是「这局是按什么设定打的」。
     * 旧档（v10 及以前）没有这一段，为 null。
     */
    val customPrompt: String? = null,
)

/** 一条非错误的提示。红色错误条管失败，这个管「做成了」。 */
data class WerewolfNotice(val text: String, val error: Boolean = false)

/**
 * 续档探活失败后，给用户一次「换人再打」的机会。
 *
 * 一个座位连不上，多数时候不是存档坏了，而是那个好友的 Key 过期、模型下线。
 * 与其让用户把整局删了重开（进度全丢），不如让他把这个座位换成另一个能连上的好友：
 * 座位号、身份、死活、已经产生的历史发言全保留，只换背后跑的那个人。
 *
 * 这里只装**展示所需**的快照：连不上的是哪几个座位、每个座位可以换谁。
 * 真正的替换在 [WerewolfViewModel.swapSeatsAndResume] 里做，界面不碰领域数据。
 */
data class WerewolfRecast(
    val gameId: String,
    /** 连不上的座位。seat 是牌桌号码，oldName 是原好友名。 */
    val failed: List<FailedSeat>,
    /**
     * 每个失败座位能换成谁。key 是座位号，value 是候选好友快照。
     * 已经探活 OK 的好友优先排前面——换上去能直接打通，不用再赌一把。
     */
    val candidatesBySeat: Map<Int, List<RecastCandidate>>,
    /** 上一条探活错误，留在面板顶部说明「为什么让你换人」。 */
    val reason: String,
) {
    data class FailedSeat(
        val seat: Int,
        val oldName: String,
        val oldModel: String,
        /**
         * 这个座位的好友**已经不在了**（被删了 / 换设备没同步）。
         *
         * 和「连不上」分开：不在了只能换人；连不上也许换个 Key 就能开打，
         * 用户会想先试后者，界面不能把两件事说成一件。
         */
        val gone: Boolean = false,
    )

    data class RecastCandidate(
        val id: String,
        val name: String,
        val model: String,
        /** 这个好友此刻探活是不是绿的。 */
        val reachable: Boolean,
    )
}

/**
 * 开演出之前要补人的那几位。
 *
 * [candidates] 已经**排除这一局出现过的角色**：替身的名字和声音会直接出现在牌桌上，
 * 撞上局内已有的人，用户根本分不清谁是谁。
 */
data class ReplaySetup(
    /** 要演的那一局。存在这里而不是去 `_record` 拿：刚打完的局没有 record。 */
    val record: WerewolfRecordView,
    val missing: List<UIWerewolfMissingSeat>,
    val candidates: List<UIWerewolfFriend>,
    /** 演出要按哪个视角放（复盘页的上帝视角开关）。 */
    val godView: Boolean,
)

/**
 * 复盘演出的播放状态。
 *
 * [frames] 在开始时就一次性算好（见 `ReplayTimeline`），播放只是往前走游标：
 * 每一步都重新过滤一遍 transcript 的话，暂停、跳条这些操作会各算一次，
 * 出错时也是「有的地方算得对、有的地方算错了」，很难查。
 */
data class WerewolfReplay(
    val recordId: String,
    val table: WerewolfTable,
    val humanSeat: Int?,
    val godView: Boolean,
    val frames: List<ReplayFrame>,
    /** 已经演到第几帧，-1 = 还没开始。 */
    val cursor: Int = -1,
    val playing: Boolean = false,
    /** 只影响句与句之间的停顿，朗读本身不加速（加速朗读会变调）。 */
    val speed: Float = 1f,
    val speakingSeat: Int? = null,
) {
    val hasContent: Boolean get() = frames.isNotEmpty()
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

    // ---- 自动朗读与复盘演出播放器 -------------------------------------
    // 引擎是 Android 对象，在 ViewModel 里创建，具体调度全部委托给独立播放器类，
    // 方便纯 JVM 单测并隔离并发状态。
    private val speechEngine: SpeechEngine = CloudSpeechEngine(application)
    private val speechPlayer = WerewolfSpeechPlayer(
        scope = viewModelScope,
        engine = speechEngine,
        onWarn = { msg -> Log.w(TAG, msg) },
    )
    private val replayPlayer = WerewolfReplayPlayer(
        scope = viewModelScope,
        engine = speechEngine,
        autoSpeak = speechPlayer.autoSpeak,
        speechReady = speechPlayer.speechReady,
        onBeforeStart = { speechPlayer.stop(clearQueue = true) },
    )

    val autoSpeak: StateFlow<Boolean> = speechPlayer.autoSpeak

    /** 对局里要不要显示 AI 的思考过程。默认关——参与局看别人的思考等于读心。 */
    private val _showThinking = MutableStateFlow(false)
    val showThinking: StateFlow<Boolean> = _showThinking.asStateFlow()

    /**
     * 每个座位的思考气泡可见性**覆盖值**（本局有效，不进存档）。
     *
     * 缺席 = 跟随全局开关；true/false = 单独开/关，优先于全局。
     * 长按思考气泡就是写一个 false——某个 AI 的内心戏看着累，单独给它关掉，
     * 不用为一它把全场的思考都藏了。
     */
    private val _bubbleVisible = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val bubbleVisible: StateFlow<Map<Int, Boolean>> = _bubbleVisible.asStateFlow()

    /**
     * 提示词风格。默认引导式，和设置层默认值对齐；init 里读一次 DataStore 覆盖。
     *
     * 和 autoSpeak 一样是全局偏好：开局页选完写进 DataStore，续档读当前值——
     * 玩家在设置里切了风格就是想立刻生效，不该被存档里那份按回去。
     */
    private val _promptStyle = MutableStateFlow(WerewolfPromptStyle.GUIDED)
    val promptStyle: StateFlow<WerewolfPromptStyle> = _promptStyle.asStateFlow()

    /**
     * 牌手风格画像总开关。默认开，init 读 DataStore 覆盖。
     *
     * 和 promptStyle 一样是全局偏好：开局/续档读当前值，不进存档。
     * 关掉后所有座位都不注入风格画像，回到「只有陪伴人设」的老行为。
     */
    private val _playerStyleOn = MutableStateFlow(true)
    val playerStyleOn: StateFlow<Boolean> = _playerStyleOn.asStateFlow()

    /**
     * 开局页手动指定的风格（friendId → styleId）。空 = 全部自动分配。
     * 和 roleByFriend 一样是本局选择，不持久化；续档时按 friendId 重新自动分配。
     */
    private var pinnedStyles: Map<String, String> = emptyMap()

    /** 朗读开关是否真的能出声。系统没装中文语音包时为 false，界面据此提示。 */
    val speechReady: StateFlow<Boolean> = speechPlayer.speechReady

    /** 界面可以显示到第几条消息（原始 transcript 下标，-1 表示还没有）。 */
    val revealedIndex: StateFlow<Int> = speechPlayer.revealedIndex

    /** 此刻正在被朗读的那条消息属于哪个座位。null 表示没在念或系统播报。 */
    val speakingSeat: StateFlow<Int?> = speechPlayer.speakingSeat

    /** 喇叭手里还有没有活：队列里积压着没念的行，或者某一句正在念。 */
    val speechBusy: StateFlow<Boolean> = speechPlayer.speechBusy

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
            speechPlayer.refreshAfterCredentialsLoaded()
        }
    }

    private val json = Json { encodeDefaults = true }

    /** 存档生成时绑定的座位表，续档时按同样的规则重建。 */
    private var agents: Map<Int, SeatAgent> = emptyMap()

    /**
     * 「某一步请求失败、等人重发」的挂起处。
     *
     * 每个 AI 座位共用这一个协调器：牌桌上一次只显示一条重试条，
     * 所以并发阶段同时撞上限流也不会冒出好几张卡片。
     */
    private val retryCoordinator = WerewolfRetryCoordinator()
    val retryPrompt: StateFlow<RetryPrompt?> = retryCoordinator.prompt

    /**
     * 座位 → 这个座位的角色**本来**开不开深度思考。
     *
     * 对局设置面板要区分两件事：「角色没开」和「本局被关掉了」。
     * 前者点开关也打不开（那是好友自己的属性，要去编辑好友），后者可以点回来。
     * 所以界面需要知道角色原本的设定，不能只看 [WerewolfPacing.thinkingOff]。
     */
    private val _seatThinking = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val seatThinking: StateFlow<Map<Int, Boolean>> = _seatThinking.asStateFlow()

    /** 提示词自定义的存储。只存 DataStore，不进存档。 */
    private val promptSettings = WerewolfPromptSettings(getApplication())

    private val _promptState = MutableStateFlow(UIWerewolfPromptState())
    val promptState: StateFlow<UIWerewolfPromptState> = _promptState.asStateFlow()

    /**
     * 读出提示词微调页要显示的内容。
     *
     * 每次进页面都重读：用户可能在别的页面改过（或者刚在设置里清空过），
     * 页面显示的必须是磁盘上的最新值，不是打开时的那一份。
     */
    fun loadPromptSettings(boardKey: String) {
        viewModelScope.launch {
            val global = promptSettings.global.first()
            val board = promptSettings.board(boardKey).first()
            _promptState.value = UIWerewolfPromptState(
                global = global,
                board = board,
                boardTitle = WerewolfBoards.titleOf(boardKey),
                boardKey = boardKey,
                hasGlobal = global.isNotBlank(),
                hasBoard = board.isNotBlank(),
            )
        }
    }

    /** 保存两段自定义。 */
    fun savePromptSettings(global: String, board: String) {
        val key = _promptState.value.boardKey
        viewModelScope.launch {
            promptSettings.setGlobal(global)
            promptSettings.setBoard(key, board)
            loadPromptSettings(key)
        }
    }

    /** 清掉某一段，回到内置提示词。 */
    fun clearPromptSettings(global: Boolean) {
        val key = _promptState.value.boardKey
        viewModelScope.launch {
            if (global) promptSettings.clearGlobal() else promptSettings.clearBoard(key)
            loadPromptSettings(key)
        }
    }

    private val controller = WerewolfController(
        store = store,
        // 每个座位在开局/续档时已经分配好，这里只是按座位取
        agentsFor = { player -> agents[player.seat] ?: throw IllegalStateException("${player.seat} 号没有模型") },
    )

    val state: StateFlow<WerewolfUiState> = controller.state

    /**
     * 这一局的节奏：时限档位、每个座位的耗时账本、被关掉深度思考的座位。
     *
     * 直接把手上的对象暴露出去（而不是转成一份快照）：对局设置面板要在**中途**改档位，
     * 转成快照就得再写一条「改回去」的通道，两边容易不一致。
     */
    val pacing: WerewolfPacing get() = controller.pacing

    /** 改这一局的思考时限。改完立刻对下一个请求生效。 */
    fun setTurnLimit(limit: WerewolfTurnLimit) = controller.setTurnLimit(limit)

    /** 手动关/开某个座位的深度思考。开只是恢复角色自己的设定。 */
    fun setSeatThinking(seat: Int, on: Boolean) = controller.setSeatThinking(seat, on)

    /** 催一下当前所有正在思考的 AI，让它们立刻给出结论。 */
    fun nudgeThinking() = controller.nudgeThinking()

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

    /**
     * 一条**不是错误**的提示（导入成功、导入后另存了一份……）。
     *
     * 单独一条流而不是塞进 [error]：红色提示条是「出事了」，绿色的是「做成了」。
     * 混在一起用，导入成功也会红着脸出现，用户第一反应是「又失败了」。
     */
    private val _notice = MutableStateFlow<WerewolfNotice?>(null)
    val notice: StateFlow<WerewolfNotice?> = _notice.asStateFlow()

    fun clearNotice() {
        _notice.value = null
    }

    /**
     * 界面层要报一句话时用（导出失败、存档读不出来）。
     *
     * 不直接在界面里弹 Toast：这一页的提示条已经是统一出口，
     * 多一种提示样式只会让人以为出了不同级别的问题。
     */
    fun notify(text: String, error: Boolean = false) {
        _notice.value = WerewolfNotice(text, error = error)
    }

    /**
     * 续档探活失败后等的换人决定。非空时列表页弹换人面板。
     *
     * 和 [error] 分开：错误条说「连不上」，面板说「换成谁」。两者同时出现，
     * 用户才知道发生了什么、下一步能做什么。
     */
    private val _recast = MutableStateFlow<WerewolfRecast?>(null)
    val recast: StateFlow<WerewolfRecast?> = _recast.asStateFlow()

    fun clearRecast() {
        _recast.value = null
    }

    /** 好友 id → 那个模型的连通状况，与首页好友列表共用 [FriendProbeStore]。 */
    private val _apiChecks = MutableStateFlow<Map<String, ApiCheck>>(emptyMap())
    val apiChecks: StateFlow<Map<String, ApiCheck>> = _apiChecks.asStateFlow()

    /** 这一轮检测有没有跑完。配置页据此把「检测连接」变成「检测中」。 */
    private val _verifying = MutableStateFlow(false)
    val verifying: StateFlow<Boolean> = _verifying.asStateFlow()

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

        // 实时订阅全局探活中心：在首页探活过的好友，进狼人杀选人页立刻带出绿/红状态，
        // 切到搜索式时自动切到搜索式前提的探活通道，互不串台。
        combine(_friends, FriendProbeStore.checks, _promptStyle) { friends, snapshot, style ->
            FriendProbeStore.resolveForFriends(
                friends = friends,
                webSearch = style == WerewolfPromptStyle.SEARCH,
                snapshot = snapshot,
            )
        }
            .onEach { _apiChecks.value = it }
            .launchIn(viewModelScope)

        viewModelScope.launch { runCatching { werewolfSettings.autoSpeak.collect { speechPlayer.applyPreference(it) } } }
        viewModelScope.launch { runCatching { werewolfSettings.showThinking.collect { _showThinking.value = it } } }
        viewModelScope.launch {
            runCatching { werewolfSettings.playerStyleOn.collect { _playerStyleOn.value = it } }
        }
        viewModelScope.launch {
            runCatching {
                werewolfSettings.promptStyle.collect { name ->
                    _promptStyle.value = runCatching {
                        WerewolfPromptStyle.valueOf(name)
                    }.getOrDefault(WerewolfPromptStyle.GUIDED)
                }
            }
        }

        // 消息流一有新发言就喂给朗读播放器。
        state
            .map { it.table?.transcript.orEmpty() }
            .distinctUntilChanged()
            .onEach { transcript -> speechPlayer.offer(transcript, _humanSeat.value) }
            .launchIn(viewModelScope)
    }

    // -----------------------------------------------------------------
    // 自动朗读与设置偏好
    // -----------------------------------------------------------------

    private suspend fun awaitSpeechCaughtUp() {
        val size = state.value.table?.transcript?.size ?: return
        speechPlayer.awaitCaughtUp(size)
    }

    private fun resetSpeech(fromIndex: Int) = speechPlayer.reset(fromIndex)

    private fun stopSpeech(clearQueue: Boolean) = speechPlayer.stop(clearQueue)

    /** 开关：关掉时立刻闭嘴，打开时把当前已有的发言重新入队（从游标继续，不重读历史）。 */
    /** 对局里要不要显示 AI 的思考过程。 */
    fun setShowThinking(enabled: Boolean) {
        _showThinking.value = enabled
        viewModelScope.launch { runCatching { werewolfSettings.setShowThinking(enabled) } }
    }

    /** 长按思考气泡：本局隐藏这个座位的气泡（覆盖全局开关）。 */
    fun hideThinkingBubble(seat: Int) = setBubbleVisible(seat, false)

    /** 单独设置一个座位的思考气泡可见性；写进覆盖表，优先于全局开关。 */
    fun setBubbleVisible(seat: Int, visible: Boolean) {
        _bubbleVisible.value = _bubbleVisible.value + (seat to visible)
    }

    /** 清掉本局的气泡覆盖。开新局/读档时调用：那是另一局了，别把上一局的隐藏带过来。 */
    private fun resetBubbleVisible() {
        _bubbleVisible.value = emptyMap()
    }

    /** 切提示词风格。下一次开局或续档生效；正在跑的局不受影响（导演已按开局的值建好了）。 */
    fun setPromptStyle(style: WerewolfPromptStyle) {
        _promptStyle.value = style
        viewModelScope.launch { runCatching { werewolfSettings.setPromptStyle(style.name) } }
    }

    /** 牌手风格画像总开关。下一次开局/续档生效；正在跑的局不受影响。 */
    fun setPlayerStyleOn(enabled: Boolean) {
        _playerStyleOn.value = enabled
        viewModelScope.launch { runCatching { werewolfSettings.setPlayerStyleOn(enabled) } }
    }

    fun setAutoSpeak(enabled: Boolean) {
        viewModelScope.launch { werewolfSettings.setAutoSpeak(enabled) }
        speechPlayer.setAutoSpeak(enabled, state.value.table?.transcript?.size ?: 0)
    }

    /** 点灰色喇叭时单独重试绑定引擎，不切换朗读开关。 */
    fun retrySpeech() {
        speechPlayer.retry()
    }

    /**
     * 牌桌错误条上的「重新请求」：把挂住的那一步用原参数再发一次。
     *
     * 没有挂起的请求时是空操作——用户可能晚点了几十毫秒，
     * 那一步已经被时限兜底放走了，这时不该凭空再发起一次。
     */
    fun retryFailedStep() {
        retryCoordinator.resolve(RetryDecision.RETRY)
    }

    /** 错误条上的「放弃这一步」：让原来的错误照常往上抛，整局按存档停下。 */
    fun giveUpFailedStep() {
        retryCoordinator.resolve(RetryDecision.GIVE_UP)
    }

    override fun onCleared() {
        // 演出循环也要停：它和朗读共用一个喇叭，ViewModel 都没了还在演就是幽灵声音
        replayPlayer.stopLoopOnly()
        speechPlayer.release()
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
            val allOk = runProbe(
                chosen = friends.filter { it.id in friendIds },
                force = true,
                webSearch = probeRequiresSearch(),
            )
            // 用户点这个按钮就是要解掉那条红字，全绿了还挂着会让人以为没生效。
            // 万一是别的原因（比如人数不对），点开局会重新提示一次，不会被藏掉
            if (allOk) _error.value = null
        }
    }

    /**
     * 进到「AI 设置」页、或者改了打法风格之后自动验一遍。
     *
     * 和 [checkApi] 的区别是**不强制**：十分钟内、同一前提下测通过的座位直接复用，
     * 所以来回翻页不会再花一次钱。但前提换了（普通 → 搜索式）就必须重测——
     * 这正是「开局后才发现某个座位不支持联网」的堵口处。
     *
     * 失败不写 [_error]：这时候用户只是路过这一页，把红字顶到他脸上等于报错。
     * 结论会落在每个座位自己的标记上，点开局时才升级成一条明确的拦截。
     */
    fun checkApiIfNeeded(friendIds: Collection<String>) {
        if (friendIds.isEmpty()) return
        if (state.value.running) return
        viewModelScope.launch {
            val friends = _friends.value.ifEmpty { friendRepository.load().map { it.toFriend() } }
            // 不加 `if (_verifying) return`：正在跑的那一轮可能是上一个前提的，
            // 直接丢掉等于让风格切换后的检查永远不发生。probeLock 会排队，
            // 轮到这一轮时 planProbes 已经能看出前提变了
            runProbe(
                chosen = friends.filter { it.id in friendIds },
                force = false,
                webSearch = probeRequiresSearch(),
            )
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
    /**
     * 读出这一局要用的额外设定：全局的那段 + 这张板子专属的那段。
     *
     * 两段拼在一起交给导演，导演塞进每个座位的 system prompt 最后——
     * 位置在那里，用户写的话才压得过内置的默认打法。
     */
    private suspend fun loadCustomPrompt(boardKey: String): String? {
        val global = promptSettings.global.first()
        val board = if (boardKey.isBlank()) "" else promptSettings.board(boardKey).first()
        val parts = listOf(global, board).filter { it.isNotBlank() }
        return parts.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    fun startGame(
        mode: WerewolfMode,
        friendIds: List<String>,
        config: WerewolfConfig = WerewolfConfig(),
        skipCheck: Boolean = false,
        /** 板子的稳定标识，用来取这一局专属的提示词自定义。 */
        boardKey: String = "",
        /** 这一局的思考时限。超过就按兜底继续，并关掉那个座位的深度思考。 */
        turnLimit: WerewolfTurnLimit = WerewolfTurnLimit.STANDARD,
        /** 这一局发言的目标长度。引擎裁剪与提示词共用。 */
        speechLength: WerewolfSpeechLength = WerewolfSpeechLength.STANDARD,
        /**
         * 指定身份（玩家 id → 身份）。参与局只会有「我」那一席，纯观战局是整页导演派单；
         * 没指定的人随机补，人数超过板子构成会报错并显示到开局页。
         */
        roleAssignment: Map<String, WerewolfRole> = emptyMap(),
        /**
         * 指定头狼（狼队指挥）的玩家 id。他必须是狼（没指身份时引擎自动派成狼），
         * 指定后每晚战术都由他定直到出局。参与局里只能是自己。
         */
        alphaId: String? = null,
        /** 手动指定的牌手风格（好友 id → 风格 id）。没指定的按 friendId 自动分配。 */
        styleByFriend: Map<String, String> = emptyMap(),
    ) {
        pinnedStyles = styleByFriend
        val previous = playJob
        playJob = viewModelScope.launch {
            // 上一局如果是「暂停」状态，它的协程可能还停在最后一次落档里；
            // 等它真的结束再开新局，免得两边同时写库
            previous?.cancelAndJoin()
            // 上一局万一停在重试条上，换局时把那条挂起的请求放走，别让它跟着新局复活
            retryCoordinator.cancelAll()
            try {
                // 新局换新账本：上一局的耗时、被关掉的思考都不该带到这一局
                controller.pacing.reset(turnLimit)
                // 气泡的单独隐藏同样是一局一份
                resetBubbleVisible()

                val friends = _friends.value.ifEmpty { friendRepository.load().map { it.toFriend() } }
                val chosen = friendIds.mapNotNull { id -> friends.firstOrNull { it.id == id } }

                val expected = config.playerCount - if (mode == WerewolfMode.PLAY) 1 else 0
                if (chosen.size != expected) {
                    _error.value = "需要选 $expected 位好友，当前选了 ${chosen.size} 位"
                    return@launch
                }

                // 玩家写的额外设定：全局的那段 + 这张板子专属的那段。
                // 在这里读一次就够了——导演是在 play 里创建的，它拿的是当时的值。
                controller.applyCustomPrompt(loadCustomPrompt(boardKey))
                // 提示词风格同样在开局这一刻定死，后面改设置不影响这一局。
                controller.applyPromptStyle(_promptStyle.value)
                // 发言长度同样开局定死；续档时由控制器从存档接回。
                controller.applySpeechLength(speechLength)

                // 开局前先确认每个模型真的能回话。这一步失败比打到第三天某个 AI 突然
                // 报错要好得多：那时牌桌已经铺开，用户分不清是游戏坏了还是模型坏了
                if (!skipCheck) {
                    val failed = verifyReachable(chosen)
                    if (failed.isNotEmpty()) {
                        _error.value = reachabilityError(failed, "。可以换人重试，或者仍然开局")
                        return@launch
                    }
                }

                val roster = chosen.map { RosterEntry(id = it.id, name = it.name, model = it.model) } +
                    if (mode == WerewolfMode.PLAY) {
                        listOf(RosterEntry(id = SELF_PLAYER_ID, name = "你", model = "human"))
                    } else {
                        emptyList()
                    }

                val game = WerewolfGame.deal(
                    config, roster, Random.Default,
                    // 派单由调用方按模式过滤：参与局只有「我」那一席，观战局是整页导演。
                    // 不在这里再按 mode 拦一道——那会让 route 新开的入口默认失效，
                    // 而且「哪些模式能指定身份」本来就不该记在引擎入口上。
                    assignment = roleAssignment,
                    alphaId = alphaId,
                )
                val humanSeat = if (mode == WerewolfMode.PLAY) {
                    game.players.first { it.id == SELF_PLAYER_ID }.seat
                } else {
                    null
                }

                val friendsById = chosen.associateBy { it.id }
                stopSpeech(clearQueue = true)
                _humanSeat.value = humanSeat
                agents = buildAgents(game.players, friendsById, humanSeat)
                _seatThinking.value = agents.mapValues { it.value.thinkingEnabled }
                speechPlayer.configureVoices(
                    voiceIds = buildSeatVoiceIds(game.players, friendsById, humanSeat),
                    ttsConfigIds = buildSeatTtsConfigIds(game.players, friendsById),
                )
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
            retryCoordinator.cancelAll()
            try {
                val table = store.load(id) ?: run {
                    _error.value = "这条存档读不出来，可能已经损坏"
                    return@launch
                }
                // 另一局了，上一局长按藏掉的气泡不跟过来
                resetBubbleVisible()
                val friends = _friends.value.ifEmpty { friendRepository.load().map { it.toFriend() } }
                val humanSeat = table.game.players.firstOrNull { it.id == SELF_PLAYER_ID }?.seat

                // 续档同样要带上玩家写的额外设定：提示词不进存档，
                // 用的是**当前**设置——玩家可能刚在设置里改过。
                //
                // 但设置分两层（全局 + 按板子），板子那一层是按 key 存的，而存档里只有
                // config。所以得先按规则反查这一局是哪张板子，否则续档会把玩家给这张
                // 板子单独写的那段丢掉：开局时生效、一暂停一继续就没了，看着像设置被吃了。
                controller.applyCustomPrompt(loadCustomPrompt(WerewolfBoards.keyOf(table.game.config)))
                // 续档只用当前设置——玩家刚切过风格就是想立刻生效，不按存档里的旧值。
                controller.applyPromptStyle(_promptStyle.value)

                // 续档也要检：这一局可能是昨天开的，Key 过期、模型下线都在这中间发生过
                //
                // 两种坏座位都要收进换人面板，不能只检「连不上」：
                // - 连不上：好友还在，但 Key 过期 / 模型下线；
                // - 好友不在了：这个 id 在好友列表里查无此人（被删了、换设备没同步）。
                //   后者连探活都做不了——没有资料就没有模型可测。以前这里是
                //   mapNotNull 悄悄丢掉，最后在建座位时炸成「找不到小咪的资料」，
                //   用户只看到一句没出口的错误，想换人都没地方换。
                val friendsById = friends.associateBy { it.id }
                val missing = missingFriendSeats(table.game.players, friendsById, humanSeat)
                val participants = table.game.players
                    .filter { it.seat != humanSeat }
                    .mapNotNull { player -> friendsById[player.id] }
                val failed = verifyReachable(participants)
                if (failed.isNotEmpty() || missing.isNotEmpty()) {
                    val reason = recastReason(
                        missing = missing,
                        failedNames = failed.map { it.name },
                        failedDetail = _apiChecks.value[failed.firstOrNull()?.id]?.message.orEmpty(),
                        searchStyle = probeRequiresSearch(),
                    )
                    // 原因可能很长，但「点下方换人」这句提示必须留着——
                    // 它是用户知道这块有个「换人」按钮的唯一指引
                    _error.value = (reason.take(170) + "。点下方换人后继续这一局").take(200)
                    _recast.value = buildRecast(id, table, failed, missing, friends, humanSeat, reason)
                    return@launch
                }
                _recast.value = null

                // 先备好座位再让控制器接手：座位表没建好就 resume，
                // 一旦 play() 起来会立刻撞上「X 号没有模型」
                agents = buildAgents(table.game.players, friendsById, humanSeat)
                _seatThinking.value = agents.mapValues { it.value.thinkingEnabled }
                speechPlayer.configureVoices(
                    voiceIds = buildSeatVoiceIds(table.game.players, friendsById, humanSeat),
                    ttsConfigIds = buildSeatTtsConfigIds(table.game.players, friendsById),
                )
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
     * 续档要换人的那几席，连同每个座位能换成谁。
     *
     * 两类坏座位都收进来：
     * - **连不上**：好友还在，但探活失败（Key 过期、模型下线）；
     * - **好友不在了**：存档里的 id 在好友列表里查无此人（被删了 / 没同步）。
     *   这类座位连探活都做不了，只能直接换人。
     *
     * 失败好友通过 id 反查座位——存档的 player.id 就是好友 id，这是唯一的连接点。
     * 人类座位（SELF_PLAYER_ID）不参与：他不是模型，换不了。
     */
    private fun buildRecast(
        gameId: String,
        table: com.feature.werewolf.engine.WerewolfTable,
        failed: List<Friend>,
        missing: List<com.feature.werewolf.game.WerewolfPlayer>,
        friends: List<Friend>,
        humanSeat: Int?,
        reason: String,
    ): WerewolfRecast {
        val failedIds = failed.map { it.id }.toSet()
        val missingIds = missing.map { it.id }.toSet()
        val failedSeats = table.game.players
            .filter { it.seat != humanSeat }
            .filter { it.id in failedIds || it.id in missingIds }
            .map { player ->
                WerewolfRecast.FailedSeat(
                    seat = player.seat,
                    oldName = player.name,
                    oldModel = player.model,
                    // 「已经不在了」和「连不上」要分开说：前者只能换人，
                    // 后者换个 Key 可能就好，用户会想先试后者
                    gone = player.id in missingIds,
                )
            }
            .sortedBy { it.seat }
        return WerewolfRecast(
            gameId = gameId,
            failed = failedSeats,
            candidatesBySeat = recastCandidates(
                failedSeats = failedSeats.map { it.seat },
                players = table.game.players,
                friends = friends,
                checks = _apiChecks.value,
            ),
            reason = reason,
        )
    }

    /**
     * 换人后接着打：把 [swaps]（座位 → 新好友 id）落到这一局，保留进度并续上。
     *
     * 流程刻意复用 [resume] 的收尾（controller.resume 恢复 createdAt/节奏、重建 agents、
     * 复位朗读），只在中间插一步 [WerewolfController.recast] 把换过的座位表写进去。
     * 这样「换人续档」和「普通续档」走的是同一条路，不会两处行为长歪。
     */
    fun swapSeatsAndResume(gameId: String, swaps: Map<Int, String>) {
        val previous = playJob
        playJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            retryCoordinator.cancelAll()
            try {
                val table = store.load(gameId) ?: run {
                    _error.value = "这条存档读不出来，可能已经损坏"
                    return@launch
                }
                val friends = _friends.value.ifEmpty { friendRepository.load().map { it.toFriend() } }
                val friendsById = friends.associateBy { it.id }
                val humanSeat = table.game.players.firstOrNull { it.id == SELF_PLAYER_ID }?.seat

                val newPlayers = applyRecast(table.game.players, friendsById, swaps)
                // 换上去的好友找不到时 applyRecast 会原样留着那个座位——这里兜底报出来，
                // 不让一局带着坏座位继续跑
                val bad = swaps.filter { (seat, fid) ->
                    newPlayers.first { it.seat == seat }.id != fid
                }
                if (bad.isNotEmpty()) {
                    _error.value = "换人的好友资料找不到了，请重试"
                    return@launch
                }
                val newTable = table.copy(game = table.game.copy(players = newPlayers))

                controller.applyCustomPrompt(loadCustomPrompt(WerewolfBoards.keyOf(table.game.config)))
                controller.applyPromptStyle(_promptStyle.value)

                if (!controller.resume(gameId)) {
                    _error.value = "这条存档读不出来，可能已经损坏"
                    return@launch
                }
                // resume 用的是存档里的旧座位表，立刻用换过的新表替换并落档
                controller.recast(newTable)

                // agents / 音色 / TTS 映射全部按新座位重建，否则 play 时会拿旧好友的资料
                agents = buildAgents(newPlayers, friendsById, humanSeat)
                _seatThinking.value = agents.mapValues { it.value.thinkingEnabled }
                speechPlayer.configureVoices(
                    voiceIds = buildSeatVoiceIds(newPlayers, friendsById, humanSeat),
                    ttsConfigIds = buildSeatTtsConfigIds(newPlayers, friendsById),
                )
                stopSpeech(clearQueue = true)
                _humanSeat.value = humanSeat
                resetSpeech(fromIndex = newTable.transcript.size)

                _recast.value = null
                _error.value = null
                controller.play(awaitStage = { awaitSpeechCaughtUp() })
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _error.value = t.message?.take(200) ?: "换人失败，请重试"
            }
        }
    }

    /**
     * 局中热换人：对局打到一半某模型频繁报错/超时/没额度时，
     * 在牌桌原地替换该座位背后的好友，保留座位号、身份、死活、历史进度，无缝续打。
     */
    fun swapSeatInGame(seat: Int, newFriendId: String) {
        val previous = playJob
        playJob = viewModelScope.launch {
            // 先停引擎与重试条，再停朗读（和 explodeNow 同序，避免 awaitStage 抢跑）
            previous?.cancelAndJoin()
            retryCoordinator.cancelAll()
            stopSpeech(clearQueue = true)
            try {
                val currentTable = controller.state.value.table ?: return@launch
                if (currentTable.game.isFinished) return@launch
                val humanSeat = _humanSeat.value
                if (seat == humanSeat) return@launch

                val friends = _friends.value.ifEmpty { friendRepository.load().map { it.toFriend() } }
                val friendsById = friends.associateBy { it.id }
                val targetFriend = friendsById[newFriendId] ?: run {
                    _error.value = "换人的好友资料找不到了，请重试"
                    return@launch
                }
                if (currentTable.game.players.any { it.id == newFriendId }) {
                    _error.value = "${targetFriend.name} 已经在这局里了，请换一位不在场上的好友"
                    return@launch
                }

                val newPlayers = applyRecast(currentTable.game.players, friendsById, mapOf(seat to newFriendId))
                if (newPlayers.firstOrNull { it.seat == seat }?.id != newFriendId) {
                    _error.value = "换人的座位不存在，请重试"
                    return@launch
                }
                val newTable = currentTable.copy(game = currentTable.game.copy(players = newPlayers))

                // 新模型接手这个座位：清掉旧模型的超时账本与自动关闭思考标记
                controller.pacing.resetSeat(seat)
                controller.recast(newTable)

                agents = buildAgents(newPlayers, friendsById, humanSeat)
                _seatThinking.value = agents.mapValues { it.value.thinkingEnabled }
                speechPlayer.configureVoices(
                    voiceIds = buildSeatVoiceIds(newPlayers, friendsById, humanSeat),
                    ttsConfigIds = buildSeatTtsConfigIds(newPlayers, friendsById),
                )
                resetSpeech(fromIndex = newTable.transcript.size)
                _error.value = null
                controller.play(awaitStage = { awaitSpeechCaughtUp() })
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _error.value = t.message?.take(200) ?: "局中换人失败，请重试"
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
        // 开着重试条退出时，把挂起的那一步也放走——否则它会在后台一直等到本局时限
        retryCoordinator.cancelAll()
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

    /**
     * 从文件管理器挑回来的一个对局文件，落进列表。
     *
     * 读文件、解析、落库全在这里做，界面只负责把 Uri 递过来：文件可能不在本地
     * （云盘、下载目录的挂载点），读它是一次真正的 IO，不能放在组合函数里。
     *
     * **不覆盖已有的局**：同一个文件导第二次、或者导的正好是本机自己打的那一局，
     * 都另存一份。用户手里的进度比「id 唯一」重要得多——真要覆盖，他自己会先删。
     */
    fun importGame(uri: Uri) {
        viewModelScope.launch {
            _notice.value = null
            _error.value = null

            val text = withContext(Dispatchers.IO) {
                runCatching { readImportText(uri) }
            }.getOrElse { error ->
                _notice.value = WerewolfNotice("这个文件读不出来：${error.message ?: "没有读取权限"}", error = true)
                return@launch
            }

            when (val read = WerewolfGameFile.read(text)) {
                is WerewolfGameFile.Read.Rejected ->
                    _notice.value = WerewolfNotice(read.reason, error = true)

                is WerewolfGameFile.Read.Ok -> {
                    val game = read.game
                    val (id, copied) = resolveImportId(game.id)
                    // 没写开局时间的文件（早期裸快照）只能拿现在顶上：
                    // 时间是列表唯一的排序依据，缺了就排到最后，用户会以为没导进来
                    val createdAt = game.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
                    runCatching { store.save(id, game.table, createdAt, game.customPrompt) }
                        .onFailure {
                            _notice.value = WerewolfNotice("写入存档失败：${it.message ?: "存储空间不足"}", error = true)
                            return@launch
                        }
                    refresh()
                    _notice.value = WerewolfNotice(importMessage(game.table.game, copied))
                }
            }
        }
    }

    /** 一个文件最多读这么多。一局几十 KB，8MB 只可能是挑错了文件。 */
    private val maxImportBytes = 8 * 1024 * 1024

    private fun readImportText(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val stream = resolver.openInputStream(uri)
            ?: throw IllegalStateException("拿不到文件内容")
        stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > maxImportBytes) {
                    throw IllegalStateException("文件超过 8MB，不像是一局对局")
                }
            }
            return out.toString("UTF-8")
        }
    }

    /**
     * 给导入的这一局找个没被占用的 id。
     *
     * 文件里带的是**导出那一台机器**上的 id。同一个文件导两次、或者导回自己刚打完的那一局，
     * id 就会撞上。这里不覆盖，接一个后缀另存——覆盖等于悄悄丢掉一局，
     * 而它可能正是用户手上打到一半的那一局。
     *
     * 返回「用的 id」和「是不是另存的一份」：后者要写在提示里，否则列表上多出一局
     * 一模一样的对局，看起来像导入出错了。
     */
    private suspend fun resolveImportId(preferred: String): Pair<String, Boolean> {
        val base = preferred.ifBlank { UUID.randomUUID().toString() }
        if (!exists(base)) return base to false
        var n = 2
        while (exists("$base-import$n")) n++
        return "$base-import$n" to true
    }

    private suspend fun exists(id: String): Boolean = store.load(id) != null

    private fun importMessage(game: WerewolfGame, copied: Boolean): String {
        val shape = "${game.players.size} 人局 · 第 ${game.day} 天" +
            if (game.isFinished) "结束" else "，还没打完"
        val head = if (copied) "这一局列表里已经有了，已另存一份：" else "已导入："
        // 没打完的局要能续上，靠的是「参与的角色都还在这台机器的好友里」。
        // 文件里只有座位号和当时的名字，通知一下，免得点「接着打」才知道缺人。
        val tail = if (game.isFinished) "" else "。要接着打，参与的 AI 角色得都在这台手机上"
        return head + shape + tail
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
                speechPlayer.clearVoices()
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
            val view = readRecord(id)
            if (view == null) {
                _error.value = "这条存档读不出来，可能已经损坏"
                return@launch
            }
            _record.value = view
        }
    }

    /**
     * 读一局存档，**不改任何界面状态**。
     *
     * 列表上的「导出」要用它：用户只是想把这局带走，不该被顺手丢进复盘页。
     * 导出失败（存档坏了）时返回 null，由调用方决定怎么提示。
     */
    suspend fun readRecord(id: String): WerewolfRecordView? {
        val table = store.load(id) ?: return null
        return WerewolfRecordView(
            id = id,
            table = table,
            humanSeat = table.game.players.firstOrNull { it.id == SELF_PLAYER_ID }?.seat,
            customPrompt = store.customPrompt(id),
        )
    }

    /** 关掉复盘，回列表。 */
    fun closeRecord() {
        closeReplay()
        _record.value = null
    }

    // ---------------------------------------------------------------------
    // 复盘演出（实际播放循环委托给 WerewolfReplayPlayer）
    // ---------------------------------------------------------------------

    /** 非空表示牌桌在放复盘演出（不是在对局）。 */
    val replay: StateFlow<WerewolfReplay?> = replayPlayer.replay

    private val _replaySetup = MutableStateFlow<ReplaySetup?>(null)

    /** 非空表示演出前要先给几个「人已经不在了」的座位指定替身。 */
    val replaySetup: StateFlow<ReplaySetup?> = _replaySetup.asStateFlow()

    /**
     * 用户点了复盘页的「演出」。
     *
     * 先查这一局的角色还在不在：存档里只有座位号和当时的名字，头像、音色都来自好友列表。
     * 有人被删掉就不能直接放——那一格会既没声音也没脸。这时候停下来让用户指定替身，
     * 每个座位各挑一个**不在这局里**的好友，或者让这一格只显示文字。
     */
    fun requestReplay(godView: Boolean) {
        viewModelScope.launch {
            // 两个入口：列表里翻出来的复盘（`_record`），和刚打完那一局的复盘（`state`）。
            // 后者手里没有 record——直接拿终局局面拼一个出来，内容完全一样。
            val record = _record.value ?: state.value.let { current ->
                val table = current.table
                if (!current.finished || table == null) {
                    null
                } else {
                    WerewolfRecordView(
                        id = current.gameId.orEmpty(),
                        table = table,
                        humanSeat = table.game.players.firstOrNull { it.id == SELF_PLAYER_ID }?.seat,
                        // 手上这局还没从库里读回来，直接用控制器状态里的那份
                        customPrompt = current.customPrompt,
                    )
                }
            } ?: return@launch
            val friends = loadFriendsForReplay()
            val byId = friends.associateBy { it.id }
            val inGame = record.table.game.players.map { it.id }.toSet()

            val missing = record.table.game.players.filter { it.id != SELF_PLAYER_ID && it.id !in byId }
            if (missing.isEmpty()) {
                startReplay(record, godView, emptyMap(), friends)
                return@launch
            }
            _replaySetup.value = ReplaySetup(
                record = record,
                missing = missing.map { UIWerewolfMissingSeat(seat = it.seat, name = it.name) },
                candidates = friends.filter { it.id !in inGame }.map {
                    UIWerewolfFriend(
                        id = it.id,
                        name = it.name,
                        avatarPath = it.avatarPath,
                        role = it.role,
                        model = it.model,
                    )
                },
                godView = godView,
            )
        }
    }

    /** 补人页点了「开始演出」。[assignments] 座位 → 替身好友 id；null = 这个座位静音。 */
    fun confirmReplaySetup(assignments: Map<Int, String?>) {
        val setup = _replaySetup.value ?: return
        _replaySetup.value = null
        viewModelScope.launch {
            startReplay(setup.record, setup.godView, assignments, loadFriendsForReplay())
        }
    }

    fun cancelReplaySetup() {
        _replaySetup.value = null
    }

    /** 好友列表在内存里就直接用，没有才回数据库读一次（读是挂起的，只能在这里做）。 */
    private suspend fun loadFriendsForReplay(): List<Friend> =
        _friends.value.ifEmpty {
            runCatching { friendRepository.load().map { it.toFriend() } }.getOrDefault(emptyList())
        }

    private fun startReplay(
        record: WerewolfRecordView,
        godView: Boolean,
        assignments: Map<Int, String?>,
        friends: List<Friend>,
    ) {
        val byId = friends.associateBy { it.id }
        val players = record.table.game.players

        val voiceBook = players.mapNotNull { player ->
            val friend = byId[player.id]
                ?: assignments[player.seat]?.let { byId[it] }
                ?: return@mapNotNull null
            player.id to friend
        }.toMap()

        val started = replayPlayer.start(
            record = record,
            godView = godView,
            voiceIds = buildSeatVoiceIds(players, voiceBook, record.humanSeat),
            ttsIds = buildSeatTtsConfigIds(players, voiceBook),
            mutedSeats = assignments.filterValues { it == null }.keys,
        )
        if (!started) {
            _error.value = "这一局的记录里还没有任何消息，没法演出"
        }
    }

    fun closeReplay() {
        replayPlayer.close()
        _replaySetup.value = null
    }

    /** 播放 / 暂停。已经演到结尾时再点播放，从头再来一遍。 */
    fun toggleReplay() = replayPlayer.toggle()

    fun replayNext() = replayPlayer.next()

    fun replayPrev() = replayPlayer.prev()

    /** 1x → 1.5x → 2x → 1x。 */
    fun cycleReplaySpeed() = replayPlayer.cycleSpeed()

    /** 演出页里直接切上帝视角。 */
    fun setReplayGodView(godView: Boolean) = replayPlayer.setGodView(godView)

    /** 放弃当前这局，存档一并删掉。 */
    fun abandon() {
        stopSpeech(clearQueue = true)
        viewModelScope.launch {
            playJob?.cancelAndJoin()
            playJob = null
            controller.abandon()
            _humanSeat.value = null
            agents = emptyMap()
            speechPlayer.clearVoices()
            refresh()
        }
    }

    // ---------------------------------------------------------------------
    // 连通性检查
    // ---------------------------------------------------------------------

    /**
     * 确认这些座位背后的模型都能回话，返回**连不上的好友**（空 = 全通过）。
     *
     * 只看 [ApiCheckState.FAILED]：还没测的说明刚被跳过（同厂商已绿），不能当成失败。
     *
     * 返回列表而不是 Boolean：续档要用这列表算出「哪几个座位要换人」，
     * 只给一个 true/false 的话，调用方还得再翻一遍 [apiChecks]，两处口径容易错开。
     */
    private suspend fun verifyReachable(chosen: List<Friend>): List<Friend> {
        if (chosen.isEmpty()) return emptyList()
        runProbe(chosen, force = false, webSearch = probeRequiresSearch())

        val checks = _apiChecks.value
        return chosen.filter { checks[it.id]?.state == ApiCheckState.FAILED }
    }

    /**
     * 这一局要不要按「搜索式」的前提去验连通。
     *
     * 风格是全局偏好、不进存档，所以续档也跟着当前选择走——
     * 用户切回引导式再续旧局，本来就不需要联网。
     */
    private fun probeRequiresSearch(): Boolean = _promptStyle.value == WerewolfPromptStyle.SEARCH

    /** 把探活失败写成一句人话。开局和续档共用，保证两处说法一致。 */
    private fun reachabilityError(failed: List<Friend>, suffix: String): String {
        val names = failed.joinToString("、") { it.name }
        val reason = _apiChecks.value[failed.first().id]?.message.orEmpty()
        // 把「谁的哪把 Key 不行」写清楚：只说「开局失败」，用户只能一个个去试。
        // 搜索式下失败的多半是「连得上、但不会联网」，说成「连不上」会把人往 Key 上引
        return buildString {
            if (probeRequiresSearch()) {
                append("搜索式要求真的联网检索，${failed.size} 个座位做不到：$names")
            } else {
                append("${failed.size} 个座位连不上：$names")
            }
            if (reason.isNotBlank()) append("（$reason）")
            append(suffix)
        }.take(200)
    }

    /**
     * 跑一轮探活并把结果同步进 [apiChecks]，返回「是否全部通过」。
     *
     * 实际请求与去重缓存委托给进程内共享的 [FriendProbeStore]：
     * 首页好友列表刚刚探活通过的模型在十分钟内、同一前提下直接跳过，不必每次开局重复等待。
     */
    private suspend fun runProbe(chosen: List<Friend>, force: Boolean, webSearch: Boolean): Boolean {
        if (chosen.isEmpty()) return true
        _verifying.value = true
        return try {
            val allOk = FriendProbeStore.probe(
                friends = chosen,
                force = force,
                webSearch = webSearch,
                loadCredentials = {
                    withContext(Dispatchers.IO) { runCatching { providerSettings.load() } }
                },
                aiFor = { brand -> aiFor(brand) },
            )
            syncApiChecks(extra = chosen, webSearch = webSearch)
            allOk
        } finally {
            _verifying.value = false
        }
    }

    private fun syncApiChecks(
        extra: List<Friend> = emptyList(),
        webSearch: Boolean = probeRequiresSearch(),
    ) {
        val all = (_friends.value + extra).distinctBy { it.id }
        _apiChecks.value = FriendProbeStore.resolveForFriends(all, webSearch = webSearch)
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

    /**
     * 必须有目标的那一类：**只有预言家查验**了。
     *
     * 狼刀原来也走这里，v12 起改走 [submitTarget]——空刀是合法战术，
     * 而这条路传不了 null。
     */
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

    /** 警长选发言顺序：警左 / 警右（从自己左右手边起）、死左 / 死右（从昨夜死者起）。 */
    fun submitDirection(direction: SpeechDirection) {
        if (humanTurn.value?.jsonMode != true) return
        human.submit(json.encodeToString(DirectionChoice(direction.displayName)))
    }

    /** 竞选发言之后的退水：退了就不再被投票，首轮也没有警长票（PK 轮才有）。 */
    fun submitWithdraw(withdraw: Boolean) {
        if (humanTurn.value?.jsonMode != true) return
        human.submit(json.encodeToString(WithdrawChoice(withdraw)))
    }

    /**
     * 别人发言后的「要不要插爆」判断。
     *
     * 这是白天发言中触发的一次性询问，不是阶段，所以靠 HumanTurn.explodeWatch 认。
     * 提交 ExplodeChoice；选 false 就是继续忍着（引擎会等这次判断收完再继续）。
     */
    fun submitExplode(explode: Boolean, reason: String = "") {
        if (humanTurn.value?.explodeWatch != true) return
        human.submit(json.encodeToString(ExplodeChoice(explode = explode, reason = reason)))
    }

    /**
     * 人类点牌桌上的「自爆」。
     *
     * 两条路，取决于此刻引擎在等谁：
     * - **正轮到自己自由发言**（`turn` 在等、而且不要 JSON）：把标记交回去就行。
     *   这和 AI 在发言开头写【自爆】是同一条受理路径，不打断、不重启，
     *   气泡里那句「我自爆。」也照样落库。
     * - **别人正在说话**（或者正等自己做一次结构化选择）：导演挂在那个请求上，
     *   从外面叫不停它。只能取消这一局，落一次自爆，然后重新开跑。
     *   取消是安全的：牌桌上已经说的话都在 state 里，自爆本身又是阶段终点，
     *   续上之后不会出现「话说到一半」。
     *
     * 能不能爆由状态机说了算（[com.feature.werewolf.game.WerewolfGame.canExplode]：
     * 白天发言环节、活着、是狼）。按钮只在能爆的时候画出来，这里再兜一次底。
     */
    fun explodeNow() {
        val seat = _humanSeat.value ?: return
        val table = controller.state.value.table ?: return
        if (!table.game.canExplode(seat)) return

        val turn = humanTurn.value
        if (turn != null && !turn.jsonMode) {
            human.submit(EXPLODE_MARKER)
            return
        }

        val previous = playJob
        playJob = viewModelScope.launch {
            // 先停引擎、再停朗读。反过来（先 stopSpeech）会让 awaitStage 里的
            // 「等喇叭念完」当场满足，引擎可能抢在取消之前又往前走一步
            previous?.cancelAndJoin()
            // 这一步万一正挂着「请求失败、等你重发」，也一起放走：
            // 自爆之后那一步已经没有意义，留着它只会在重启后立刻又弹出来
            retryCoordinator.cancelAll()
            stopSpeech(clearQueue = true)
            if (!controller.explodeNow(seat)) return@launch
            // 自爆那句已经落库，从新末尾开始念：历史不补念，也不再把
            // 自己那句念一遍（人类座位通常没有音色，但配置过就该按配置走）
            resetSpeech(fromIndex = controller.state.value.table?.transcript?.size ?: 0)
            controller.play(awaitStage = { awaitSpeechCaughtUp() })
            refresh()
        }
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
    ): Map<Int, SeatAgent> {
        // 风格按 friendId 稳定分配：同桌尽量不重样，续档重算也一致。
        // 关掉总开关时给空映射，assignPlayerStyles 收到空 pinned 也会自动分配——
        // 这里用 null 分支直接不注入，避免白算一遍。
        val styleById = if (_playerStyleOn.value) {
            assignPlayerStyles(players.map { it.id }, pinnedStyles)
        } else {
            emptyMap()
        }
        return players.associate { player ->
            val agent = if (player.seat == humanSeat) {
                SeatAgent(ai = human, model = "human", persona = "玩家本人")
            } else {
                val friend = friendsById[player.id]
                    ?: throw IllegalStateException("找不到 ${player.name}（${player.id}）的资料")
                val ai = aiFor(friend.brand)
                    ?: throw IllegalStateException("暂不支持的厂商：${friend.brand}")
                SeatAgent(
                    // 传输层失败（429 / 断网）挂在请求内部等人重发；引擎、存档都不知情。
                    // 人类座位不包——他本来就在界面上等自己输入，没有「失败重试」这回事。
                    ai = RetryableAI(ai, player.seat, player.name, retryCoordinator),
                    model = friend.model,
                    persona = friend.prompt,
                    thinkingEnabled = friend.thinkingEnabled,
                    playStyle = styleById[player.id],
                )
            }
            player.seat to agent
        }
    }

    /**
     * 这个厂商的对话接口有没有内置联网搜索。
     *
     * 静态能力，查一下就够，不用发请求——所以能在选人那一步就告诉用户
     * 「这个模型搜索式用不了」，而不是等自检跑完才看到红字。
     */
    fun supportsWebSearchFor(brand: String): Boolean = aiFor(brand)?.supportsWebSearch == true

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
