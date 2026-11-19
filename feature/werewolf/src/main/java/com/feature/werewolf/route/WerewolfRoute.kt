package com.feature.werewolf.route

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feature.werewolf.contract.WerewolfScreen
import com.feature.werewolf.export.WerewolfExport
import com.feature.werewolf.engine.SeatTiming
import com.feature.werewolf.engine.TurnKind
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfTurnLimit
import com.feature.werewolf.vm.WerewolfViewModel
import com.ui.werewolf.UIWerewolfHistoryScreen
import com.ui.werewolf.UIWerewolfPaceSheet
import com.ui.werewolf.UIWerewolfPromptSettingsScreen
import com.ui.werewolf.UIWerewolfReplaySetupScreen
import com.ui.werewolf.UIWerewolfReviewScreen
import com.ui.werewolf.UIWerewolfSetupScreen
import com.ui.werewolf.UIWerewolfTableScreen
import com.ui.werewolf.data.UIWerewolfFriend
import com.ui.werewolf.data.UIWerewolfMode
import com.ui.werewolf.data.UIWerewolfNotice
import com.ui.werewolf.data.UIWerewolfPromptStyle
import com.ui.werewolf.data.UIWerewolfPace
import com.ui.werewolf.data.UIWerewolfPreset
import com.ui.werewolf.data.UIWerewolfSeatPace
import com.ui.werewolf.data.UIWerewolfTurnLimit
import com.ui.werewolf.data.formatSpent

/**
 * 狼人杀的入口。
 *
 * 只做三件事：把领域数据翻成展示模型、把界面操作翻回 ViewModel、在「列表 / 配置 / 对局」
 * 之间切换。规则、请求调度、存档都在更下层，这一层不出现任何判定逻辑。
 */
@Composable
fun WerewolfRoute(
    onExit: () -> Unit,
    viewModel: WerewolfViewModel = viewModel(),
) {
    val friends by viewModel.friends.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val state by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()
    val humanSeat by viewModel.humanSeat.collectAsState()
    val humanTurn by viewModel.humanTurn.collectAsState()
    val apiChecks by viewModel.apiChecks.collectAsState()
    val verifying by viewModel.verifying.collectAsState()
    val recordView by viewModel.record.collectAsState()
    // 复盘演出：非空就是牌桌在放存档（`replay`），或者正等着补替身（`replaySetup`）
    val replayState by viewModel.replay.collectAsState()
    val replaySetup by viewModel.replaySetup.collectAsState()
    val autoSpeak by viewModel.autoSpeak.collectAsState()
    val speechReady by viewModel.speechReady.collectAsState()
    // 提示词风格是全局偏好，直接读 VM 的那一份——开局页和对局都不做本地副本
    val promptStyle by viewModel.promptStyle.collectAsState()
    val revealedIndex by viewModel.revealedIndex.collectAsState()
    val speechBusy by viewModel.speechBusy.collectAsState()
    val speakingSeat by viewModel.speakingSeat.collectAsState()
    val notice by viewModel.notice.collectAsState()
    // 这一局的节奏：档位、关掉思考的座位、每座位耗时账本。都在面板里直接呈现
    val paceLimit by viewModel.pacing.limit.collectAsState()
    val paceThinkingOff by viewModel.pacing.thinkingOff.collectAsState()
    val paceTimings by viewModel.pacing.timings.collectAsState()
    val seatThinking by viewModel.seatThinking.collectAsState()
    // 导出复盘要用它写缓存文件并拉起系统分享
    val context = LocalContext.current

    /**
     * 导入对局文件用的文件选择器。
     *
     * MIME 不限类型（传通配符）：`.wwg` 是自定义后缀，Android 没有它对应的类型，
     * 收窄成 json 会让文件在选择器里直接变灰。认不认得出这个文件交给解析层判断——
     * 那一步能说清楚原因，而在这儿筛一遍只会让用户点不到自己的文件。
     */
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importGame(uri) }

    // 入口是列表，不是配置页，更不是上次的牌桌
    var screen by remember { mutableStateOf<WerewolfScreen>(WerewolfScreen.History) }
    var showReview by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(UIWerewolfMode.PLAY) }
    var presetKey by remember { mutableStateOf(WerewolfBoards.STANDARD_8) }
    // 开局前选的思考时限。存成 UI 枚举而不是引擎枚举，是为了让这一层和配置页说同一种话；
    // 开局时才映射成引擎那一份（见 `toTurnLimit`）
    var turnLimit by remember { mutableStateOf(UIWerewolfTurnLimit.STANDARD) }
    /** 牌桌上打开的对局设置面板。 */
    var showPaceSheet by remember { mutableStateOf(false) }

    /**
     * 「用户刚点了接着打或新开一局」。
     *
     * 不能只凭 `state.table != null` 进牌桌：ViewModel 活在 Activity 上，退出页面时
     * 上一局的局面还在里面，一重新进来就会被自动扔回那张牌桌——这正是要修掉的行为。
     * 局面还在是事实，「用户想看它」不是，只有点过按钮才算。
     */
    var pendingEnter by remember { mutableStateOf(false) }

    /** 正在为哪一局做准备（探活 + 重建座位）。列表上那一张卡据此显示「连接中…」。 */
    var resumingId by remember { mutableStateOf<String?>(null) }
    var selectedIds by rememberSaveable(saver = SelectedIdsSaver) { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // 真正开始跑了才进对局页。
    //
    // 判断条件里带 running 而不是只看 table 非空：续档要先探活，这一步可能来回几秒，
    // 期间 table 还是上一次的旧局面。只看 table 会在探活还没回来时就把人推到牌桌上，
    // 牌桌上一动不动，看着像卡死。
    LaunchedEffect(state.table, state.running, pendingEnter) {
        if (pendingEnter && state.running && state.table != null) {
            screen = WerewolfScreen.Table
            pendingEnter = false
            resumingId = null
        }
    }

    // 续档失败（连不上、存档坏了）由 ViewModel 写成一条错误，卡片要跟着退出「连接中…」
    LaunchedEffect(error) {
        if (error != null) resumingId = null
    }

    // 新的一局开始了，复盘必须收起来，否则会盖在还没打完的牌桌上
    LaunchedEffect(state.finished) {
        if (!state.finished) showReview = false
    }

    // 超时那句话。放在这一层拼：引擎只知道座位号，名字和「他当时在干什么」
    // 只有摆在牌桌前面的这一层知道。
    val timeoutNotice = state.lastTimeout?.let { timeout ->
        val player = state.table?.game?.players?.firstOrNull { it.seat == timeout.seat }
        val who = if (player != null) "${timeout.seat} 号 ${player.name}" else "${timeout.seat} 号"
        val what = if (timeout.kind == TurnKind.SPEECH) "发言" else "决定"
        "$who 的$what 超出 ${timeout.limitSeconds} 秒时限，已按兜底继续；本局不再给它开深度思考"
    }

    // 返回键逐层退：演出 → 补替身页 → 列表翻开的复盘 → 牌桌上的复盘 → 牌桌 → 配置页 → 列表 → 退出
    BackHandler {
        when {
            replayState != null -> viewModel.closeReplay()
            replaySetup != null -> viewModel.cancelReplaySetup()
            recordView != null -> viewModel.closeRecord()
            screen == WerewolfScreen.Table && showReview -> showReview = false
            screen == WerewolfScreen.Table -> {
                // 回列表要真的停下：留在后台继续跑，用户在看列表，AI 却在替他打牌
                viewModel.pause()
                screen = WerewolfScreen.History
            }
            screen == WerewolfScreen.Setup -> screen = WerewolfScreen.History
            else -> onExit()
        }
    }

    val uiFriends = remember(friends) { friends.map { it.toUiFriend() } }
    // 座位 → 好友头像。牌桌上认人主要靠头像，名字在 60dp 里会被截断。
    val avatars = remember(friends) { friends.associate { it.id to it.avatarPath } }
    val records = remember(summaries) { summaries.map { it.toRecord() } }
    val preset = PRESETS.firstOrNull { it.key == presetKey } ?: PRESETS.first()
    val uiApiChecks = remember(apiChecks) { apiChecks.mapValues { it.value.toUiCheck() } }
    val promptState by viewModel.promptState.collectAsState()
    val showThinking by viewModel.showThinking.collectAsState()

    // 从列表点开的那一局。它和「正在打的这一局」是两件事，所以单独一层盖在最上面。
    val recordReview = remember(recordView, avatars) {
        recordView?.let { view ->
            // customPrompt 从存档里带过来：复盘要说明这一局是按什么设定打的
            WerewolfUiState(
                table = view.table,
                finished = true,
                customPrompt = view.customPrompt,
            ).toReview(view.humanSeat, avatars)
        }
    }

    /**
     * 导出对局文件。两个复盘入口（列表翻出来的 / 刚打完的）都走这里。
     *
     * 开局时间从存档摘要里取：文件里必须带着它，换台手机导回来列表才排得对。
     * 取不到（摘要还没刷新）才退回当前时间——宁可排序差一点，也不能让导出失败。
     */
    fun shareGameFile(table: WerewolfTable?, gameId: String?, customPrompt: String?) {
        if (table == null || gameId.isNullOrBlank()) return
        val createdAt = summaries.firstOrNull { it.id == gameId }?.createdAt
            ?: System.currentTimeMillis()
        // 设定也一起带走：换台手机导回来之后，复盘里那一块不该平白空着
        WerewolfExport.shareGame(context, table, gameId, createdAt, customPrompt)
    }

    // 演出盖在复盘上面：它是从复盘页点进来的，退回去应该回到复盘，而不是直接回列表
    val replay = replayState
    if (replay != null) {
        UIWerewolfTableScreen(
            live = remember(replay, avatars, autoSpeak, speechReady) {
                replay.toLive(avatars, autoSpeak, speechReady)
            },
            onExit = viewModel::closeReplay,
            onToggleSpeak = {
                if (speechReady) viewModel.setAutoSpeak(!autoSpeak) else viewModel.retrySpeech()
            },
            onReplayToggle = viewModel::toggleReplay,
            onReplayPrev = viewModel::replayPrev,
            onReplayNext = viewModel::replayNext,
            onReplaySpeed = viewModel::cycleReplaySpeed,
        )
        return
    }

    // 局内角色被删了：先补人，补完才开始演
    val setup = replaySetup
    if (setup != null) {
        UIWerewolfReplaySetupScreen(
            missing = setup.missing,
            candidates = setup.candidates,
            onCancel = viewModel::cancelReplaySetup,
            onConfirm = viewModel::confirmReplaySetup,
        )
        return
    }

    if (recordReview != null) {
        UIWerewolfReviewScreen(
            review = recordReview,
            onBack = viewModel::closeRecord,
            onRestart = {
                viewModel.closeRecord()
                screen = WerewolfScreen.Setup
            },
            onExport = { WerewolfExport.share(context, recordReview) },
            onExportGame = { shareGameFile(recordView?.table, recordView?.id, recordView?.customPrompt) },
            onPlay = viewModel::requestReplay,
        )
        return
    }

    when (screen) {
        WerewolfScreen.History -> UIWerewolfHistoryScreen(
            records = records,
            resumingId = resumingId,
            error = error,
            onClearError = viewModel::clearError,
            onResume = { id ->
                pendingEnter = true
                resumingId = id
                viewModel.resume(id)
            },
            onOpenRecord = { id -> viewModel.openRecord(id) },
            onDiscard = { id -> viewModel.discard(id) },
            onClearFinished = viewModel::clearFinished,
            onNewGame = { screen = WerewolfScreen.Setup },
            onBack = onExit,
            // 文件选择器按 MIME 过滤，自定义后缀没有对应的类型，只能放开到全部
            onImport = { importLauncher.launch(arrayOf("*/*")) },
            notice = notice?.let { UIWerewolfNotice(text = it.text, error = it.error) },
            onClearNotice = viewModel::clearNotice,
        )

        WerewolfScreen.Setup -> UIWerewolfSetupScreen(
            mode = mode,
            presets = PRESETS,
            presetKey = presetKey,
            friends = uiFriends,
            selectedIds = selectedIds,
            seats = buildSeats(uiFriends, selectedIds, mode, preset.playerCount),
            error = error,
            apiChecks = uiApiChecks,
            checking = verifying,
            turnLimit = turnLimit,
            onTurnLimitChange = { turnLimit = it },
            promptStyle = promptStyle.toUi(),
            onPromptStyleChange = { viewModel.setPromptStyle(it.toPromptStyle()) },
            // 朗读是全局偏好（DataStore），这里直接读同一份值：开局前关掉，进牌桌不用再找喇叭
            speechOn = autoSpeak,
            speechReady = speechReady,
            onSpeechChange = viewModel::setAutoSpeak,
            onModeChange = { next ->
                mode = next
                // 「我参与」比「纯观战」少要一位，切过去时多出来的人要放掉，
                // 否则会带着 8 个人去开 7 人的局
                selectedIds = trimSelection(selectedIds, uiFriends, next, presetKey)
            },
            onPresetChange = { key ->
                presetKey = key
                selectedIds = trimSelection(selectedIds, uiFriends, mode, key)
            },
            onToggleFriend = { id ->
                selectedIds = if (id in selectedIds) {
                    selectedIds - id
                } else {
                    selectedIds + id
                }
                // 换人之后旧结论就失效了：上一条错误可能正是说「有几个座位连不上」，
                // 留着会让用户以为刚取消的那个人还有问题
                viewModel.clearError()
            },
            onClearError = viewModel::clearError,
            onCheckApi = { viewModel.checkApi(selectedIds) },
            onOpenPromptSettings = { screen = WerewolfScreen.PromptSettings },
            // 回列表而不是退出页面：配置页是从列表进来的，退出去等于多按一次
            onBack = { screen = WerewolfScreen.History },
            onStart = {
                pendingEnter = true
                viewModel.startGame(
                    mode = mode.toMode(),
                    friendIds = selectedIds.toList(),
                    config = configOf(presetKey),
                    boardKey = presetKey,
                    turnLimit = turnLimit.toTurnLimit(),
                )
            },
            onStartAnyway = {
                pendingEnter = true
                viewModel.startGame(
                    mode = mode.toMode(),
                    friendIds = selectedIds.toList(),
                    config = configOf(presetKey),
                    boardKey = presetKey,
                    // 走到这里说明用户已经看见红灯并选择继续，不必再拦一次
                    skipCheck = true,
                    turnLimit = turnLimit.toTurnLimit(),
                )
            },
        )

        WerewolfScreen.PromptSettings -> {
            // 每次进页面重读一次：用户可能在别处改过，页面要显示磁盘上的最新值
            LaunchedEffect(presetKey) { viewModel.loadPromptSettings(presetKey) }
            UIWerewolfPromptSettingsScreen(
                state = promptState,
                showThinking = showThinking,
                onShowThinkingChange = viewModel::setShowThinking,
                onSave = { global, board -> viewModel.savePromptSettings(global, board) },
                onClearGlobal = { viewModel.clearPromptSettings(global = true) },
                onClearBoard = { viewModel.clearPromptSettings(global = false) },
                onBack = { screen = WerewolfScreen.Setup },
            )
        }

        WerewolfScreen.Table -> {
            val review = remember(state, humanSeat, avatars) {
                if (state.finished) state.toReview(humanSeat, avatars) else null
            }
            if (showReview && review != null) {
                UIWerewolfReviewScreen(
                    review = review,
                    onBack = { showReview = false },
                    onRestart = {
                        showReview = false
                        screen = WerewolfScreen.Setup
                    },
                    onExport = { WerewolfExport.share(context, review) },
                    onExportGame = { shareGameFile(state.table, state.gameId, state.customPrompt) },
                    // 刚打完这一局也能直接演一遍：ViewModel 会拿终局局面拼出记录，
                    // 不需要先回列表翻存档
                    onPlay = viewModel::requestReplay,
                )
            } else {
                val pace = remember(state.table, paceLimit, paceThinkingOff, paceTimings, seatThinking, avatars, state.finished) {
                    buildPace(
                        table = state.table,
                        limit = paceLimit,
                        timings = paceTimings,
                        thinkingOff = paceThinkingOff,
                        roleThinking = seatThinking,
                        avatars = avatars,
                        // 暂停时也给改：下一步继续时就用新档位。
                        // 只有真正结束了才禁用——那一局不会再跑起来了
                        running = !state.finished,
                    )
                }
                // 面板要压在牌桌上面，必须是同一个 Box 的兄弟节点；
                // 直接并排放在 when 分支里会变成纵向两块，牌桌被顶掉一半
                Box(modifier = Modifier.fillMaxSize()) {
                UIWerewolfTableScreen(
                    live = state.toLive(
                        humanSeat = humanSeat,
                        turn = humanTurn,
                        avatars = avatars,
                        autoSpeak = autoSpeak,
                        speechReady = speechReady,
                        revealedIndex = revealedIndex,
                        speakingSeat = speakingSeat,
                        speechBusy = speechBusy,
                        showThinking = showThinking,
                        decisionSeconds = paceLimit.decisionSeconds,
                        speechSeconds = paceLimit.speechSeconds,
                        timeoutNotice = timeoutNotice,
                        timeoutAt = state.lastTimeout?.at ?: 0L,
                    ),
                    // 牌桌左上角的返回 = 收起牌桌回列表，不是退出狼人杀
                    onExit = {
                        viewModel.pause()
                        screen = WerewolfScreen.History
                    },
                    onSpeech = viewModel::submitSpeech,
                    onVote = { viewModel.submitTarget(it) },
                    onSeat = { viewModel.submitSeat(it) },
                    onWitch = { heal, poison -> viewModel.submitWitch(heal, poison) },
                    onReview = if (review != null) ({ showReview = true }) else null,
                    // 喇叭可用时切换开关；灰着（引擎没绑上）时点它重试一次绑定。
                    onToggleSpeak = {
                        if (speechReady) viewModel.setAutoSpeak(!autoSpeak)
                        else viewModel.retrySpeech()
                    },
                    // 参与局里打开思考 = 读心。默认关着，牌桌上挂一条提示条提醒
                    onToggleThinking = { viewModel.setShowThinking(!showThinking) },
                    onOpenSettings = {
                        // 面板开着的时候语音还在念，那是正常的——设置不打断牌局
                        showPaceSheet = true
                    },
                )
                if (showPaceSheet) {
                    UIWerewolfPaceSheet(
                        pace = pace,
                        onLimitChange = { limit -> viewModel.setTurnLimit(limit.toTurnLimit()) },
                        onToggleThinking = { seat, on -> viewModel.setSeatThinking(seat, on) },
                        speechOn = autoSpeak,
                        speechReady = speechReady,
                        onSpeechChange = viewModel::setAutoSpeak,
                        onDismiss = { showPaceSheet = false },
                    )
                }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 展示模型映射
// ---------------------------------------------------------------------------

private val PRESETS = WerewolfBoards.all.map { board ->
    UIWerewolfPreset(
        key = board.key,
        title = board.title,
        playerCount = board.config.playerCount,
        composition = compositionText(board.config.composition) +
            if (board.config.hasSheriff) " · 警长" else "",
    )
}

/**
 * 座位条：先放已选的好友，再放「你」，剩下的留空。
 *
 * 刻意不给每格编上「座位号」——座位是开局时洗牌决定的，
 * 这里写一个数字，开局后就会对不上。
 */
/**
 * 按当前玩法与板子裁剪已选。
 *
 * 换板子（8→9）不会超员，但换玩法会：「纯观战」的 8 个人切到「我参与」只需要 7 个。
 * 多留一个不会报错，只会在开局时被 ViewModel 挡回来，用户不知道哪里点错了。
 */
private fun trimSelection(
    ids: Set<String>,
    friends: List<UIWerewolfFriend>,
    mode: UIWerewolfMode,
    presetKey: String,
): Set<String> {
    val total = PRESETS.firstOrNull { it.key == presetKey }?.playerCount ?: 0
    val limit = total - if (mode == UIWerewolfMode.PLAY) 1 else 0
    return friends.filter { it.id in ids }.take(limit).map { it.id }.toSet()
}

/**
 * 选择集合的存档器。
 *
 * `Set<String>` 不能直接进 Bundle，转成 ArrayList 才能跟着页面重建一起恢复，
 * 否则转屏一次选择就清空了。
 */
private val SelectedIdsSaver: Saver<androidx.compose.runtime.MutableState<Set<String>>, ArrayList<String>> =
    Saver(
        save = { ArrayList(it.value) },
        restore = { mutableStateOf(it.toSet()) },
    )

/**
 * 把「这一局的节奏」拼成面板要的形状。
 *
 * 放在 route 而不是 ViewModel：它要同时读局面（谁在几号、谁出局了）、头像、
 * 引擎的耗时账本和角色属性，四样东西分别属于四层。让 VM 去拼，
 * 它就得把头像和座位名一起搬进状态里，而那些本来就在这一层。
 */
private fun buildPace(
    table: WerewolfTable?,
    limit: WerewolfTurnLimit,
    timings: Map<Int, SeatTiming>,
    thinkingOff: Set<Int>,
    roleThinking: Map<Int, Boolean>,
    avatars: Map<String, String?>,
    running: Boolean,
): UIWerewolfPace {
    val seats = table?.game?.players.orEmpty().map { player ->
        val timing = timings[player.seat]
        // 角色本身的设定：关掉的座位在面板上不给点，那是好友的属性
        val roleOn = roleThinking[player.seat] == true
        UIWerewolfSeatPace(
            seat = player.seat,
            name = player.name,
            avatarPath = avatars[player.id],
            thinkingOn = roleOn && player.seat !in thinkingOff,
            thinkingAvailable = roleOn,
            lastLabel = timing?.takeIf { it.turns > 0 }?.let { formatSpent(it.lastMs) },
            // 只有一步的时候「平均」和「最近」是同一个数，写出来只是多一列噪声
            averageLabel = timing?.takeIf { it.turns > 1 }?.let { formatSpent(it.averageMs) },
            timeoutCount = timing?.timeouts ?: 0,
            eliminated = !player.alive,
        )
    }.sortedWith(compareBy({ it.eliminated }, { it.seat }))
    return UIWerewolfPace(
        limit = limit.toUi(),
        seats = seats,
        running = running,
        totalTimeouts = timings.values.sumOf { it.timeouts },
    )
}

/** UI 枚举和引擎枚举靠**名字**对齐（顺序和拼写都一致），不用写 when 分支。 */
private fun UIWerewolfTurnLimit.toTurnLimit(): WerewolfTurnLimit =
    WerewolfTurnLimit.entries.firstOrNull { it.name == name } ?: WerewolfTurnLimit.STANDARD

private fun WerewolfTurnLimit.toUi(): UIWerewolfTurnLimit =
    UIWerewolfTurnLimit.entries.firstOrNull { it.name == name } ?: UIWerewolfTurnLimit.STANDARD

private fun UIWerewolfPromptStyle.toPromptStyle(): WerewolfPromptStyle =
    WerewolfPromptStyle.entries.firstOrNull { it.name == name } ?: WerewolfPromptStyle.GUIDED

private fun WerewolfPromptStyle.toUi(): UIWerewolfPromptStyle =
    UIWerewolfPromptStyle.entries.firstOrNull { it.name == name } ?: UIWerewolfPromptStyle.GUIDED
