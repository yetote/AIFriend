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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.feature.werewolf.game.SELF_PLAYER_ID
import com.feature.werewolf.game.SpeechDirection
import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WerewolfSpeechLength
import com.feature.werewolf.game.WerewolfTurnLimit
import com.feature.werewolf.vm.WerewolfViewModel
import kotlinx.coroutines.launch
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
import com.ui.werewolf.data.UIWerewolfRecast
import com.ui.werewolf.data.UIWerewolfRecastCandidate
import com.ui.werewolf.data.UIWerewolfRecastSeat
import com.ui.werewolf.data.UIWerewolfRetry
import com.ui.werewolf.data.UIWerewolfRoleOption
import com.ui.werewolf.data.UIWerewolfSeatPace
import com.ui.werewolf.data.UIWerewolfSpeechLength
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
    // 牌手风格画像总开关同样是全局偏好
    val playerStyleOn by viewModel.playerStyleOn.collectAsState()
    val revealedIndex by viewModel.revealedIndex.collectAsState()
    val speechBusy by viewModel.speechBusy.collectAsState()
    val speakingSeat by viewModel.speakingSeat.collectAsState()
    val notice by viewModel.notice.collectAsState()
    // 续档连不上时的换人面板数据。null = 不需要换人。
    val recast by viewModel.recast.collectAsState()
    // 某一步请求失败、正挂着等人重发。它不属于 controller.state（引擎根本不知道断过），
    // 所以单独收一路，再并进牌桌的展示模型。
    val retryPrompt by viewModel.retryPrompt.collectAsState()
    val retryUi = remember(retryPrompt) {
        retryPrompt?.let { UIWerewolfRetry(message = it.message, attempt = it.attempt) }
    }
    // 这一局的节奏：档位、关掉思考的座位、每座位耗时账本。都在面板里直接呈现
    val paceLimit by viewModel.pacing.limit.collectAsState()
    val paceThinkingOff by viewModel.pacing.thinkingOff.collectAsState()
    val paceTimings by viewModel.pacing.timings.collectAsState()
    val seatThinking by viewModel.seatThinking.collectAsState()
    // 每个座位的思考气泡覆盖值：缺席 = 跟全局，长按/面板可单独开或关
    val bubbleVisible by viewModel.bubbleVisible.collectAsState()
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
    // 开局前选的发言长度。同样存 UI 枚举，开局时才映射成引擎那一份
    var speechLength by remember { mutableStateOf(UIWerewolfSpeechLength.STANDARD) }
    // 导演模式：纯观战局指定身份。指定结果按「好友 id → 身份名」存，
    // 开局时才 valueOf 成引擎身份——存名字是为了 UI 层不依赖引擎类型
    var assignRoles by remember { mutableStateOf(false) }
    var roleByFriend by remember { mutableStateOf(emptyMap<String, String>()) }
    // 参与局里「我」的身份：null = 随机。key 和 roleOptions 一致（含头狼的伪 key）。
    // 只动自己这一席，其他人照常随机——娱乐局想练预言家或想当狼，不必为此开导演模式。
    var myRole by remember { mutableStateOf<String?>(null) }
    // 手动指定的牌手风格（好友 id → 风格 id）。空 = 全部由引擎自动分配。
    var styleByFriend by remember { mutableStateOf(emptyMap<String, String>()) }
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
    // 续档换人面板：feature 的 WerewolfRecast → ui 展示模型。
    val uiRecast = remember(recast) {
        recast?.let {
            UIWerewolfRecast(
                reason = it.reason,
                seats = it.failed.map { failed ->
                    UIWerewolfRecastSeat(
                        seat = failed.seat,
                        oldName = failed.oldName,
                        oldModel = failed.oldModel,
                        candidates = (it.candidatesBySeat[failed.seat].orEmpty()).map { c ->
                            UIWerewolfRecastCandidate(
                                id = c.id, name = c.name, model = c.model, reachable = c.reachable,
                            )
                        },
                    )
                },
            )
        }
    }
    // 座位 → 好友头像。牌桌上认人主要靠头像，名字在 60dp 里会被截断。
    val avatars = remember(friends) { friends.associate { it.id to it.avatarPath } }
    val records = remember(summaries) { summaries.map { it.toRecord() } }
    val preset = PRESETS.firstOrNull { it.key == presetKey } ?: PRESETS.first()
    // 导演模式的身份名额：按板子构成列出，再扣掉已经指定出去的数量。
    // 板子/派单一变就要重算，所以放进 remember 的 key
    // 头狼在 UI 侧是一个独立选项（和「狼人」共用狼的名额）：key 存伪角色名，
    // 开局时 roleAssignmentOf 不认识它、自动从身份派单里剔除，alphaIdOf 单独取走
    val roleOptions = remember(presetKey, roleByFriend) {
        val composition = configOf(presetKey).composition
        val wolfCount = composition[WerewolfRole.WEREWOLF] ?: 0
        fun usedWolf() = roleByFriend.values.count { it == WerewolfRole.WEREWOLF.name || it == ALPHA_WOLF_KEY }
        // 头狼只有一个名额（见下面的选项），和普通狼分开数
        fun alphaUsed() = roleByFriend.values.count { it == ALPHA_WOLF_KEY }
        val list = composition.map { (role, count) ->
            val used = if (role == WerewolfRole.WEREWOLF) {
                // 头狼也算狼名额：指定了头狼，普通狼的名额就少一个
                usedWolf()
            } else {
                roleByFriend.values.count { it == role.name }
            }
            UIWerewolfRoleOption(
                key = role.name,
                label = role.displayName,
                total = count,
                remaining = (count - used).coerceAtLeast(0),
            )
        }.sortedByDescending { it.total }.toMutableList()
        if (wolfCount > 0) {
            // 头狼选项紧跟在狼人后面（总数相同，稳定排序不会把它们拆开）
            val wolfIndex = list.indexOfFirst { it.key == WerewolfRole.WEREWOLF.name }
            list.subList(wolfIndex + 1, wolfIndex + 1).add(
                UIWerewolfRoleOption(
                    key = ALPHA_WOLF_KEY,
                    label = "头狼（定战术）",
                    // 头狼**只有一个**：他是狼队里定战术的那一只，不是一种可以凑数的身份。
                    // 之前把 total 写成狼的总数，选单上就出现了「3 个名额」——
                    // 看着像能指定三只头狼，实际点第二个人时旧的那个会被顶掉。
                    total = 1,
                    remaining = (1 - alphaUsed()).coerceAtLeast(0),
                ),
            )
        }
        list
    }
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

    /**
     * 列表里导出某一局。
     *
     * 点导出时手上只有 id，所以要先读一次存档；两种格式共用这一次读取。
     * 读不到（存档坏了）就在列表上挂一条红提示——不弹 Toast，
     * 这一页的提示条已经是统一出口。
     */
    val exportScope = rememberCoroutineScope()
    fun exportRecord(id: String, markdown: Boolean) {
        exportScope.launch {
            val view = viewModel.readRecord(id)
            if (view == null) {
                viewModel.notify("这一局的存档读不出来，可能已经损坏", error = true)
                return@launch
            }
            if (markdown) {
                // 列表里能点到 Markdown 的只有已结束的局，这里仍判一次：
                // 没有赢家就没有复盘，硬拼一份文档只会让人以为导出坏了
                val review = WerewolfUiState(
                    table = view.table,
                    finished = true,
                    customPrompt = view.customPrompt,
                ).toReview(view.humanSeat, avatars)
                if (review == null) {
                    viewModel.notify("这一局还生不出复盘文档", error = true)
                    return@launch
                }
                WerewolfExport.share(context, review)
            } else {
                val createdAt = summaries.firstOrNull { it.id == id }?.createdAt
                    ?: System.currentTimeMillis()
                WerewolfExport.shareGame(context, view.table, id, createdAt, view.customPrompt)
            }
        }
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
            onReplayGodView = viewModel::setReplayGodView,
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
            // 导出从这里走：任何一局都能导对局文件，Markdown 只给已结束的局
            onExportGame = { id -> exportRecord(id, markdown = false) },
            onExportMarkdown = { id -> exportRecord(id, markdown = true) },
            onClearFinished = viewModel::clearFinished,
            onNewGame = { screen = WerewolfScreen.Setup },
            onBack = onExit,
            // 文件选择器按 MIME 过滤，自定义后缀没有对应的类型，只能放开到全部
            onImport = { importLauncher.launch(arrayOf("*/*")) },
            notice = notice?.let { UIWerewolfNotice(text = it.text, error = it.error) },
            onClearNotice = viewModel::clearNotice,
            recast = uiRecast,
            onSwapSeats = { swaps ->
                pendingEnter = true
                recast?.gameId?.let { viewModel.swapSeatsAndResume(it, swaps) }
            },
            onDismissRecast = viewModel::clearRecast,
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
            speechLength = speechLength,
            onSpeechLengthChange = { speechLength = it },
            assignRoles = assignRoles,
            onAssignRolesChange = { assignRoles = it },
            myRole = myRole,
            onMyRoleChange = { myRole = it },
            roleOptions = roleOptions,
            roleByFriend = roleByFriend,
            onAssignRole = { friendId, key ->
                roleByFriend = when {
                    key == null -> roleByFriend - friendId
                    // 头狼只有一个：派新的要把旧的撤回随机，否则引擎只能认一个
                    key == ALPHA_WOLF_KEY ->
                        roleByFriend.filterValues { it != ALPHA_WOLF_KEY } + (friendId to key)
                    else -> roleByFriend + (friendId to key)
                }
            },
            promptStyle = promptStyle.toUi(),
            onPromptStyleChange = { style ->
                viewModel.setPromptStyle(style.toPromptStyle())
                // 搜索式要联网，每步可能多等几十秒。时限比「宽松」还紧的话先抬上去——
                // 否则每一步都会被判超时兜底，而一次超时会把那个座位**整局**的深度思考
                // 关掉，一局下来全员「变傻」，看起来像搜索式本身不能用。
                // 只抬不降，用户随时可以自己调回去。
                if (style == UIWerewolfPromptStyle.SEARCH &&
                    turnLimit.ordinal < UIWerewolfTurnLimit.RELAXED.ordinal
                ) {
                    turnLimit = UIWerewolfTurnLimit.RELAXED
                }
            },
            playerStyleOn = playerStyleOn,
            onPlayerStyleOnChange = { viewModel.setPlayerStyleOn(it) },
            styleByFriend = styleByFriend,
            onAssignStyle = { friendId, styleId ->
                styleByFriend = if (styleId == null) {
                    styleByFriend - friendId
                } else {
                    styleByFriend + (friendId to styleId)
                }
            },
            // 朗读是全局偏好（DataStore），这里直接读同一份值：开局前关掉，进牌桌不用再找喇叭
            speechOn = autoSpeak,
            speechReady = speechReady,
            onSpeechChange = viewModel::setAutoSpeak,
            onModeChange = { next ->
                mode = next
                // 「我参与」比「纯观战」少要一位，切过去时多出来的人要放掉，
                // 否则会带着 8 个人去开 7 人的局
                selectedIds = trimSelection(selectedIds, uiFriends, next, presetKey)
                // 参与局没有导演模式；指定的身份也一起清掉，切回观战不要残留旧派单
                if (next == UIWerewolfMode.PLAY) {
                    assignRoles = false
                    roleByFriend = emptyMap()
                } else {
                    // 「我的身份」是参与局专用，切去观战局交给导演模式
                    myRole = null
                }
            },
            onPresetChange = { key ->
                presetKey = key
                selectedIds = trimSelection(selectedIds, uiFriends, mode, key)
                // 板子换了构成就变了，旧派单对不上新板子，整份作废
                roleByFriend = emptyMap()
                // 自己选的身份同理：新板子里可能根本没有这个身份
                myRole = null
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
                    speechLength = speechLength.toSpeechLength(),
                    roleAssignment = roleAssignmentOf(mode == UIWerewolfMode.PLAY, assignRoles, roleByFriend, myRole),
                    alphaId = alphaIdOf(mode == UIWerewolfMode.PLAY, assignRoles, roleByFriend, myRole),
                    styleByFriend = styleByFriend,
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
                    speechLength = speechLength.toSpeechLength(),
                    roleAssignment = roleAssignmentOf(mode == UIWerewolfMode.PLAY, assignRoles, roleByFriend, myRole),
                    alphaId = alphaIdOf(mode == UIWerewolfMode.PLAY, assignRoles, roleByFriend, myRole),
                    styleByFriend = styleByFriend,
                )
            },
        )

        WerewolfScreen.PromptSettings -> {
            // 每次进页面重读一次：用户可能在别处改过，页面要显示磁盘上的最新值
            LaunchedEffect(presetKey) { viewModel.loadPromptSettings(presetKey) }
            UIWerewolfPromptSettingsScreen(
                state = promptState,
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
                val pace = remember(
                    state.table, paceLimit, paceThinkingOff, paceTimings, seatThinking, avatars, state.finished,
                    bubbleVisible, humanSeat, showThinking,
                ) {
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
                        // 气泡的默认值和牌桌用的是同一套：观战/结束恒开，参与局跟全局开关
                        bubbleDefault = state.finished || humanSeat == null || showThinking,
                        bubbleVisible = bubbleVisible,
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
                        bubbleVisible = bubbleVisible,
                        decisionSeconds = paceLimit.decisionSeconds,
                        speechSeconds = paceLimit.speechSeconds,
                        timeoutNotice = timeoutNotice,
                        timeoutAt = state.lastTimeout?.at ?: 0L,
                        retry = retryUi,
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
                    // 界面枚举和 SpeechDirection 同名同序，按名字转过去就够，不必再写一遍映射
                    onDirection = { direction ->
                        viewModel.submitDirection(SpeechDirection.valueOf(direction.name))
                    },
                    onWithdraw = viewModel::submitWithdraw,
                    onReview = if (review != null) ({ showReview = true }) else null,
                    // 不传 onToggleSpeak：对局的朗读开关收进设置面板（「朗读发言」那一行），
                    // 顶栏不再放喇叭。面板的开关打开时同样会重试绑定引擎，恢复入口没丢。
                    // 复盘演出那条分支仍传，因为演出没有设置面板。
                    // 参与局里打开思考 = 读心。默认关着，牌桌上挂一条提示条提醒
                    onToggleThinking = { viewModel.setShowThinking(!showThinking) },
                    onOpenSettings = {
                        // 面板开着的时候语音还在念，那是正常的——设置不打断牌局
                        showPaceSheet = true
                    },
                    onHideThinkingBubble = viewModel::hideThinkingBubble,
                    onRetry = viewModel::retryFailedStep,
                    onGiveUpStep = viewModel::giveUpFailedStep,
                    // 自爆：能不能点由 live.canExplode 决定（牌桌内部再 gate 一次），
                    // 这里只负责把动作交下去
                    onExplode = viewModel::explodeNow,
                )
                if (showPaceSheet) {
                    UIWerewolfPaceSheet(
                        pace = pace,
                        onLimitChange = { limit -> viewModel.setTurnLimit(limit.toTurnLimit()) },
                        onToggleThinking = { seat, on -> viewModel.setSeatThinking(seat, on) },
                        speechOn = autoSpeak,
                        speechReady = speechReady,
                        onSpeechChange = viewModel::setAutoSpeak,
                        bubbleOn = showThinking,
                        onBubbleChange = viewModel::setShowThinking,
                        onSeatBubbleChange = viewModel::setBubbleVisible,
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
    /** 气泡的全局默认值（观战/结束恒开，参与局跟顶栏开关）。 */
    bubbleDefault: Boolean,
    /** 每个座位的覆盖值；缺席 = 跟全局。 */
    bubbleVisible: Map<Int, Boolean>,
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
            bubbleVisible = bubbleVisible[player.seat] ?: bubbleDefault,
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

/** 同上，发言长度档位按名字映射。 */
private fun UIWerewolfSpeechLength.toSpeechLength(): WerewolfSpeechLength =
    WerewolfSpeechLength.entries.firstOrNull { it.name == name } ?: WerewolfSpeechLength.STANDARD

/**
 * 把开局页的选择转成引擎要的「玩家 id → 身份」。
 *
 * 两条路：
 * - **参与局**只允许指定「我」这一席（[myRole]），其他座位照常随机——
 *   娱乐局想练某个身份很正常，不必为此打开整页导演模式；
 * - **纯观战局**走 [assignRoles] 的逐座位派单。
 *
 * 身份名认不出（换过板子、旧数据）就丢弃，那个座位回落到随机——宁可随机，
 * 也不要让一个坏 key 卡死开局。
 */
/** 头狼在 UI 侧的伪角色 key。不对应任何 WerewolfRole，开局时单独取走。 */
internal const val ALPHA_WOLF_KEY = "ALPHA_WOLF"

internal fun roleAssignmentOf(
    /**
     * true = 自己上桌的参与局，false = 纯观战。
     *
     * 用 Boolean 而不是 UI 枚举：这个判断只关心「我在不在桌上」，
     * 而这个纯函数要能在不依赖 UI 层的单测里跑。
     */
    playing: Boolean,
    assignRoles: Boolean,
    roleByFriend: Map<String, String>,
    myRole: String?,
): Map<String, WerewolfRole> {
    if (playing) {
        val role = myRoleOf(myRole) ?: return emptyMap()
        return mapOf(SELF_PLAYER_ID to role)
    }
    if (!assignRoles) return emptyMap()
    // 头狼的伪 key 在这里被自然剔除（它不对应任何真实身份）：头狼必须
    // 是狼这件事由 deal 用 alphaId 保证，不靠这份派单
    return roleByFriend.mapNotNull { (id, key) ->
        id to (WerewolfRole.entries.firstOrNull { it.name == key } ?: return@mapNotNull null)
    }.toMap()
}

/**
 * 「我的身份」的 key 转成引擎身份。
 *
 * 头狼在 UI 侧是伪 key（引擎里它就是狼 + `alphaId`），所以这里先落成狼人，
 * 谁是头狼由 [alphaIdOf] 单独交代。
 */
private fun myRoleOf(key: String?): WerewolfRole? = when {
    key == null -> null
    key == ALPHA_WOLF_KEY -> WerewolfRole.WEREWOLF
    else -> WerewolfRole.entries.firstOrNull { it.name == key }
}

/** 头狼只能有一个：参与局就是「我」，观战局走导演派单。 */
internal fun alphaIdOf(
    playing: Boolean,
    assignRoles: Boolean,
    roleByFriend: Map<String, String>,
    myRole: String?,
): String? {
    if (playing) {
        return if (myRole == ALPHA_WOLF_KEY) SELF_PLAYER_ID else null
    }
    if (!assignRoles) return null
    return roleByFriend.entries.firstOrNull { it.value == ALPHA_WOLF_KEY }?.key
}

private fun WerewolfTurnLimit.toUi(): UIWerewolfTurnLimit =
    UIWerewolfTurnLimit.entries.firstOrNull { it.name == name } ?: UIWerewolfTurnLimit.STANDARD

private fun UIWerewolfPromptStyle.toPromptStyle(): WerewolfPromptStyle =
    WerewolfPromptStyle.entries.firstOrNull { it.name == name } ?: WerewolfPromptStyle.GUIDED

private fun WerewolfPromptStyle.toUi(): UIWerewolfPromptStyle =
    UIWerewolfPromptStyle.entries.firstOrNull { it.name == name } ?: UIWerewolfPromptStyle.GUIDED
