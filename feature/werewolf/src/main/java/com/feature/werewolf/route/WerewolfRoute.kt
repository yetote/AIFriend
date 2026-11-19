package com.feature.werewolf.route

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feature.werewolf.contract.WerewolfScreen
import com.feature.werewolf.export.WerewolfExport
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.vm.WerewolfViewModel
import com.ui.werewolf.UIWerewolfHistoryScreen
import com.ui.werewolf.UIWerewolfPromptSettingsScreen
import com.ui.werewolf.UIWerewolfReplaySetupScreen
import com.ui.werewolf.UIWerewolfReviewScreen
import com.ui.werewolf.UIWerewolfSetupScreen
import com.ui.werewolf.UIWerewolfTableScreen
import com.ui.werewolf.data.UIWerewolfFriend
import com.ui.werewolf.data.UIWerewolfMode
import com.ui.werewolf.data.UIWerewolfNotice
import com.ui.werewolf.data.UIWerewolfPreset

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
    val revealedIndex by viewModel.revealedIndex.collectAsState()
    val speechBusy by viewModel.speechBusy.collectAsState()
    val speakingSeat by viewModel.speakingSeat.collectAsState()
    val notice by viewModel.notice.collectAsState()
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
            WerewolfUiState(table = view.table, finished = true).toReview(view.humanSeat, avatars)
        }
    }

    /**
     * 导出对局文件。两个复盘入口（列表翻出来的 / 刚打完的）都走这里。
     *
     * 开局时间从存档摘要里取：文件里必须带着它，换台手机导回来列表才排得对。
     * 取不到（摘要还没刷新）才退回当前时间——宁可排序差一点，也不能让导出失败。
     */
    fun shareGameFile(table: WerewolfTable?, gameId: String?) {
        if (table == null || gameId.isNullOrBlank()) return
        val createdAt = summaries.firstOrNull { it.id == gameId }?.createdAt
            ?: System.currentTimeMillis()
        WerewolfExport.shareGame(context, table, gameId, createdAt)
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
            onExportGame = { shareGameFile(recordView?.table, recordView?.id) },
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
                    onExportGame = { shareGameFile(state.table, state.gameId) },
                    // 刚打完这一局也能直接演一遍：ViewModel 会拿终局局面拼出记录，
                    // 不需要先回列表翻存档
                    onPlay = viewModel::requestReplay,
                )
            } else {
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
                )
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
