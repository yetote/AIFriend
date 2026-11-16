package com.feature.werewolf.route

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feature.werewolf.contract.WerewolfScreen
import com.feature.werewolf.engine.HumanTurn
import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.Faction
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPlayer
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WinRule
import com.feature.werewolf.game.VoteOutcome
import com.feature.werewolf.game.formatVotes
import com.feature.werewolf.store.WerewolfGameSummary
import com.feature.werewolf.vm.ApiCheck
import com.feature.werewolf.vm.ApiCheckState
import com.feature.werewolf.vm.WerewolfMode
import com.feature.werewolf.vm.WerewolfViewModel
import com.ui.werewolf.UIWerewolfHistoryScreen
import com.ui.werewolf.UIWerewolfReviewScreen
import com.ui.werewolf.UIWerewolfSetupScreen
import com.ui.werewolf.UIWerewolfTableScreen
import com.ui.werewolf.data.UIWerewolfApiCheck
import com.ui.werewolf.data.UIWerewolfApiState
import com.ui.werewolf.data.UIWerewolfBallotLine
import com.ui.werewolf.data.UIWerewolfCheckReport
import com.ui.werewolf.data.UIWerewolfDayReport
import com.ui.werewolf.data.UIWerewolfFate
import com.ui.werewolf.data.UIWerewolfFriend
import com.ui.werewolf.data.UIWerewolfGroupKind
import com.ui.werewolf.data.UIWerewolfLine
import com.ui.werewolf.data.UIWerewolfLineKind
import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfMode
import com.ui.werewolf.data.UIWerewolfNightReport
import com.ui.werewolf.data.UIWerewolfPreset
import com.ui.werewolf.data.UIWerewolfRecord
import com.ui.werewolf.data.UIWerewolfReview
import com.ui.werewolf.data.UIWerewolfRevealedSeat
import com.ui.werewolf.data.UIWerewolfRoleGroup
import com.ui.werewolf.data.UIWerewolfSeat
import com.ui.werewolf.data.UIWerewolfSeatLive
import com.ui.werewolf.data.UIWerewolfTarget
import com.ui.werewolf.data.UIWerewolfTurn
import com.ui.werewolf.data.UIWerewolfTurnKind

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
    val autoSpeak by viewModel.autoSpeak.collectAsState()
    val speechReady by viewModel.speechReady.collectAsState()

    // 入口是列表，不是配置页，更不是上次的牌桌
    var screen by remember { mutableStateOf<WerewolfScreen>(WerewolfScreen.History) }
    var showReview by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(UIWerewolfMode.PLAY) }
    var presetKey by remember { mutableStateOf(PRESET_STANDARD_8) }

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

    // 返回键逐层退：列表翻开的复盘 → 牌桌上的复盘 → 牌桌 → 配置页 → 列表 → 退出
    BackHandler {
        when {
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

    // 从列表点开的那一局。它和「正在打的这一局」是两件事，所以单独一层盖在最上面。
    val recordReview = remember(recordView, avatars) {
        recordView?.let { view ->
            WerewolfUiState(table = view.table, finished = true).toReview(view.humanSeat, avatars)
        }
    }

    if (recordReview != null) {
        UIWerewolfReviewScreen(
            review = recordReview,
            onBack = viewModel::closeRecord,
            onRestart = {
                viewModel.closeRecord()
                screen = WerewolfScreen.Setup
            },
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
            // 回列表而不是退出页面：配置页是从列表进来的，退出去等于多按一次
            onBack = { screen = WerewolfScreen.History },
            onStart = {
                pendingEnter = true
                viewModel.startGame(
                    mode = mode.toMode(),
                    friendIds = selectedIds.toList(),
                    config = configOf(presetKey),
                )
            },
            onStartAnyway = {
                pendingEnter = true
                viewModel.startGame(
                    mode = mode.toMode(),
                    friendIds = selectedIds.toList(),
                    config = configOf(presetKey),
                    // 走到这里说明用户已经看见红灯并选择继续，不必再拦一次
                    skipCheck = true,
                )
            },
        )

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
                )
            } else {
                UIWerewolfTableScreen(
                    live = state.toLive(
                        humanSeat = humanSeat,
                        turn = humanTurn,
                        avatars = avatars,
                        autoSpeak = autoSpeak,
                        speechReady = speechReady,
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
                    onToggleSpeak = { viewModel.setAutoSpeak(!autoSpeak) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 展示模型映射
// ---------------------------------------------------------------------------

private const val PRESET_QUICK_6 = "quick6"
private const val PRESET_STANDARD_8 = "standard8"
private const val PRESET_STANDARD_9 = "standard9"
private const val PRESET_GUARD_10 = "guard10"
private const val PRESET_GUARD_12 = "guard12"
private const val PRESET_FOOL_12 = "fool12"

/**
 * 板子列表。
 *
 * 身份构成文案由这里根据 `WerewolfConfig` 现拼，不在 UI 里再写一份——
 * 规则改了文案会自动跟着变，不会出现「界面写着 3 神、引擎发 2 神」。
 *
 * 顺序按人数排，不是按推荐度：玩家挑板子时先问「凑得齐几个人」，
 * 把 6 人局放在最后会让人以为要凑满 12 个才能开局。
 *
 * 12 人局默认带警长（线下 12 人标准局就是这样打的），所以这两张卡片是
 * `sheriffBoard(...)` 而不是裸配置；[configOf] 也从这同一份表取，
 * 不再各写一份映射——以前两处都得改一次，改漏了就是「卡片写着带警长、开局却没有」。
 */
private val BOARDS: List<Triple<String, String, WerewolfConfig>> = listOf(
    Triple(PRESET_QUICK_6, "6 人快局", WerewolfConfig(composition = WerewolfConfig.Quick6)),
    Triple(PRESET_STANDARD_8, "8 人标准局", WerewolfConfig(composition = WerewolfConfig.Standard8)),
    Triple(PRESET_STANDARD_9, "9 人标准局", WerewolfConfig(composition = WerewolfConfig.Standard9)),
    Triple(PRESET_GUARD_10, "10 人守卫局", WerewolfConfig(composition = WerewolfConfig.Guard10)),
    Triple(PRESET_GUARD_12, "12 人守卫局", sheriffBoard(WerewolfConfig.Guard12)),
    Triple(PRESET_FOOL_12, "12 人白痴局", sheriffBoard(WerewolfConfig.Fool12)),
)

private val PRESETS = BOARDS.map { (key, title, config) -> preset(key, title, config) }

/**
 * 开警长的板子。
 *
 * `maxVoteRounds = 2` 跟着一起开：警长在场上才能主持 PK，这两个开关是配套的。
 * 只开警长而把加投关掉，等于有一位「票权 1.5 但按不出第二轮」的警长。
 */
private fun sheriffBoard(composition: Map<WerewolfRole, Int>): WerewolfConfig =
    WerewolfConfig(composition = composition, hasSheriff = true, maxVoteRounds = 2)

private fun preset(key: String, title: String, config: WerewolfConfig): UIWerewolfPreset =
    UIWerewolfPreset(
        key = key,
        title = title,
        playerCount = config.playerCount,
        composition = compositionText(config.composition) + if (config.hasSheriff) " · 警长" else "",
    )

private fun configOf(presetKey: String): WerewolfConfig =
    BOARDS.firstOrNull { it.first == presetKey }?.third ?: WerewolfConfig()

/**
 * 身份构成文案，例如「4 狼 · 预女猎守 · 4 民」。
 *
 * 神职合成一组而不是逐个报数：一个 12 人板有六个身份，逐个写「1 预言家 · 1 女巫 · 1 猎人 ·
 * 1 守卫 · 4 平民」在卡片里会折成三行，而玩家扫一眼要的其实就是「几狼几神几民」。
 *
 * 神职直接问 [WerewolfRole.isGod]，不在这里另抄一份名单：抄出来的那份不会随新身份一起更新，
 * 也不会编译报错，只会安静地在卡片上少显示一个「守」或「白」。
 */
private fun compositionText(composition: Map<WerewolfRole, Int>): String = buildList {
    val wolves = composition[WerewolfRole.WEREWOLF] ?: 0
    if (wolves > 0) add("$wolves 狼")
    val gods = WerewolfRole.entries
        .filter { it.isGod }
        .mapNotNull { role -> if ((composition[role] ?: 0) > 0) role.shortName else null }
    if (gods.isNotEmpty()) add(gods.joinToString(""))
    val villagers = composition[WerewolfRole.VILLAGER] ?: 0
    if (villagers > 0) add("$villagers 民")
}.joinToString(" · ")

private fun UIWerewolfMode.toMode(): WerewolfMode = when (this) {
    UIWerewolfMode.PLAY -> WerewolfMode.PLAY
    UIWerewolfMode.SPECTATE -> WerewolfMode.SPECTATE
}

private fun com.core.ai.model.Friend.toUiFriend() = UIWerewolfFriend(
    id = id,
    name = name,
    avatarPath = avatarPath,
    role = role,
    model = model,
)

private fun ApiCheck.toUiCheck() = UIWerewolfApiCheck(
    state = when (state) {
        ApiCheckState.CHECKING -> UIWerewolfApiState.CHECKING
        ApiCheckState.OK -> UIWerewolfApiState.OK
        ApiCheckState.FAILED -> UIWerewolfApiState.FAILED
    },
    message = message,
    elapsedMs = elapsedMs,
)

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

private fun buildSeats(
    friends: List<UIWerewolfFriend>,
    selectedIds: Set<String>,
    mode: UIWerewolfMode,
    total: Int,
): List<UIWerewolfSeat> {
    // 按好友列表顺序取，不按 Set 的迭代顺序，选出来的座位条才是稳定的
    val chosen = friends.filter { it.id in selectedIds }
        .take(total - if (mode == UIWerewolfMode.PLAY) 1 else 0)
    return buildList {
        chosen.forEach { add(UIWerewolfSeat(name = it.name, avatarPath = it.avatarPath)) }
        if (mode == UIWerewolfMode.PLAY && size < total) {
            add(UIWerewolfSeat(name = "你", avatarPath = null, isSelf = true))
        }
        while (size < total) add(UIWerewolfSeat(name = null, avatarPath = null))
    }
}

private fun WerewolfGameSummary.toRecord(): UIWerewolfRecord = UIWerewolfRecord(
    id = id,
    day = day,
    phaseLabel = Phase.entries.firstOrNull { it.name == phase }?.displayName ?: phase,
    finished = finished,
    winnerLabel = winner?.let { name ->
        Faction.entries.firstOrNull { it.name == name }?.let { "${it.displayName}胜" }
    },
    timeLabel = relativeTime(updatedAt),
    playerCount = playerCount,
    withHuman = withHuman,
)

/** 相对时间。绝对时间在战绩列表里没有意义，玩家关心的是「上一局是不是刚才那一局」。 */
private fun relativeTime(at: Long): String {
    val diff = (System.currentTimeMillis() - at).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        else -> "${diff / 86_400_000L} 天前"
    }
}

/**
 * 把一局结束的局面翻成复盘页。
 *
 * 这里可以放心地把所有私有信息都摊开：局已经完了，看不懂的部分唯一的意义就是「原来是这样」。
 * 但仍然守住一条：**不去猜**。票型、刀口、查验都只从引擎记下来的记录里读，
 * 没有记录就明说没有（[UIWerewolfReview.incomplete]），不拿播报文案去反推。
 */
internal fun WerewolfUiState.toReview(
    humanSeat: Int?,
    avatars: Map<String, String?>,
): UIWerewolfReview? {
    val game = table?.game ?: return null
    val winner = game.winner ?: return null

    // 整局累计得票：票型历史是逐轮的，加投会让同一个人被投两次，这里如实累加
    val votesBySeat = game.voteRounds
        .flatMap { it.ballots }
        .mapNotNull { it.targetSeat }
        .groupingBy { it }
        .eachCount()

    fun fateOf(player: WerewolfPlayer): String? {
        val day = player.deathDay
        if (day == null) {
            // 翻过牌的白痴还活着，但「还活着」不是这一局的全部事实：
            // 不说清楚，复盘里会显示一个没有任何归宿标记的白痴
            return if (player.role == WerewolfRole.IDIOT && game.idiotRevealed) "翻过牌，没被投出" else null
        }
        return when (player.deathCause) {
            DeathCause.WOLF_KILL -> "第 $day 夜被刀"
            DeathCause.WITCH_POISON -> "第 $day 夜被毒"
            DeathCause.EXILE -> "第 $day 天被票出"
            DeathCause.HUNTER_SHOOT -> "第 $day 天被枪带走"
            DeathCause.SELF_EXPLODE -> "第 $day 天自爆"
            null -> null
        }
    }

    // 这一局当过警长的人：选出来的 + 后来接手警徽的。
    // 底牌区不改排版，只在身份后面挂一个「警长」——比新增一行图例省事，
    // 也不会让 12 人局的底牌区再宽一截
    val sheriffSeats = game.history.mapNotNull { event ->
        when (event) {
            is GameEvent.SheriffElected -> event.seat
            is GameEvent.SheriffBadgePassed -> event.toSeat
            else -> null
        }
    }.toSet()

    fun members(predicate: (WerewolfPlayer) -> Boolean) = game.players
        .filter(predicate)
        .map { player ->
            UIWerewolfRevealedSeat(
                seat = player.seat,
                name = player.name,
                avatarPath = avatars[player.id],
                roleLabel = player.role.displayName + if (player.seat in sheriffSeats) "·警长" else "",
                isSelf = player.seat == humanSeat,
                alive = player.alive,
                fate = fateOf(player),
                votes = votesBySeat[player.seat] ?: 0,
            )
        }

    val groups = listOfNotNull(
        UIWerewolfRoleGroup(
            kind = UIWerewolfGroupKind.WOLF,
            title = "狼人",
            members = members { it.role == WerewolfRole.WEREWOLF },
        ).takeIf { it.members.isNotEmpty() },
        UIWerewolfRoleGroup(
            kind = UIWerewolfGroupKind.GOD,
            title = "神职",
            members = members { it.role.isGod },
        ).takeIf { it.members.isNotEmpty() },
        UIWerewolfRoleGroup(
            kind = UIWerewolfGroupKind.VILLAGER,
            title = "平民",
            members = members { it.role == WerewolfRole.VILLAGER },
        ).takeIf { it.members.isNotEmpty() },
    )

    val days = game.voteRounds
        .groupBy { it.day }
        .toSortedMap()
        .map { (day, rounds) ->
            // 加投时只摆最后一轮：前一轮的票已经被「再投一次」作废了，
            // 两轮的票并排摆出来，会让人以为 XX 号拿了双倍票
            val last = rounds.maxByOrNull { it.round } ?: return@map null
            // 票到了白痴身上只翻牌，没有人出局。这里如果按「放逐」显示，
            // 复盘会写着「放逐 4 号」，而底牌区那个 4 号明明还活着
            val revealed = last.outcome == VoteOutcome.IDIOT_REVEALED
            val exiled = last.exiledSeat.takeIf { !revealed }
            val abstained = last.ballots.isNotEmpty() && last.ballots.all { it.targetSeat == null }
            UIWerewolfDayReport(
                day = day,
                result = when {
                    revealed -> "${last.exiledSeat} 号翻牌（白痴，${formatVotes(last.exileVotes)} 票），不出局"
                    exiled != null -> "放逐 $exiled 号（${formatVotes(last.exileVotes)} 票）"
                    abstained -> "全员弃票"
                    else -> "平票，无人出局"
                },
                exiled = exiled != null,
                extraRounds = (rounds.size - 1).coerceAtLeast(0),
                ballots = last.ballots
                    .sortedBy { it.voterSeat }
                    .map { ballot ->
                        UIWerewolfBallotLine(
                            voter = ballot.voterSeat,
                            target = ballot.targetSeat,
                            key = exiled != null && ballot.targetSeat == exiled,
                        )
                    },
            )
        }
        .filterNotNull()

    val nights = game.nights
        .sortedBy { it.day }
        .map { night ->
            UIWerewolfNightReport(
                day = night.day,
                wolfLine = night.wolfTarget?.let { "狼刀 $it 号" } ?: "狼人空刀",
                guardLine = night.guarded?.let { "守卫守 $it 号" },
                witchLine = when {
                    night.healed -> "女巫用解药救下"
                    night.poisonTarget != null -> "女巫毒 ${night.poisonTarget} 号"
                    else -> null
                },
                deathLine = if (night.deaths.isEmpty()) {
                    "平安夜"
                } else {
                    night.deaths.joinToString("、") + " 号出局"
                },
            )
        }

    val selfLine = humanSeat?.let { seat ->
        val me = game.players.firstOrNull { it.seat == seat } ?: return@let null
        buildString {
            append("你是 $seat 号 · ${me.role.displayName} · ${me.role.faction.displayName}")
            if (me.alive) {
                append("，活到了最后。")
            } else {
                append("，").append(fateOf(me) ?: "已出局").append("。")
            }
            append("下面是当时你看不到的底牌。")
        }
    }

    val deaths = game.players.count { !it.alive }

    return UIWerewolfReview(
        winnerLabel = "${winner.displayName}胜",
        wolfWon = winner == Faction.WEREWOLF,
        reason = finishReason(game, winner),
        facts = "${game.players.size} 人局 · 打到第 ${game.day} 天 · $deaths 人出局",
        selfLine = selfLine,
        groups = groups,
        days = days,
        nights = nights,
        checks = game.seerChecks.map { UIWerewolfCheckReport(it.day, it.targetSeat, it.isWerewolf) },
        // 每局至少有一夜，一条夜间记录都没有只可能是旧档
        incomplete = game.nights.isEmpty(),
    )
}

/** 为什么会结束。胜负本身在横幅上，这里补的是「哪一边先撑不住」。 */
private fun finishReason(game: WerewolfGame, winner: Faction): String {
    if (winner == Faction.GOOD) return "狼人全部出局，好人守住了。"

    // 绑票要单独说，因为它在屠边局里也成立，而且现场看起来最反直觉：
    // 神和民都还有人，怎么就结束了？答案是他们已经凑不出票把狼投出去
    val aliveWolves = game.players.count { it.alive && it.role == WerewolfRole.WEREWOLF }
    val aliveGood = game.players.count { it.alive && it.faction == Faction.GOOD }
    if (aliveWolves >= aliveGood) {
        // 有没有警长，「为什么投不出去」的说法不一样：警长在也只能多投一轮 PK，
        // 凑不出票这件事不会因为有个警徽就改变。照抄「没有警长就没有 PK」会自相矛盾
        val tail = if (game.config.hasSheriff) {
            "警长在场也最多主持一轮 PK，票数差在这里已经追不回来了。"
        } else {
            "没有警长就没有 PK，平票等于无人出局，夜里再少一个只会更糟。"
        }
        return "场上还剩 $aliveWolves 狼 $aliveGood 好人，狼人已经绑票：白天全员投也凑不出比狼更多的票，$tail"
    }

    return when (game.config.winRule) {
        WinRule.SLAY_ALL -> "存活的狼人已经不少于好人，投票再也翻不过来了。"

        WinRule.SLAY_SIDE -> {
            // 只问「开局有过、现在全没了」的那一类，避免把没有平民的板子判成屠民
            val godsWiped = game.players.any { it.role.isGod } &&
                game.players.none { it.alive && it.role.isGod }
            val villagersWiped = game.players.any { it.role == WerewolfRole.VILLAGER } &&
                game.players.none { it.alive && it.role == WerewolfRole.VILLAGER }
            when {
                godsWiped && villagersWiped -> "神职和平民都已经死光，狼人屠边成功。"
                godsWiped -> "神职全部出局，狼人屠边成功。"
                villagersWiped -> "平民全部出局，狼人屠边成功。"
                else -> "好人一方已经无力回天。"
            }
        }
    }
}

/**
 * 把一局翻译成牌桌能直接画的东西。
 *
 * 三条规则：
 *
 * 1. **可见性在这一层过滤完**，界面不再判断。观战看全量（上帝视角，狼人夜聊也看得见），
 *    参与看 `transcriptFor(我的座位)`。
 * 2. **身份只在公开时给出**：观战，或者已经分出胜负。参与中的局里，别人的身份连 UI 都拿不到，
 *    想从界面泄露也没有路径。
 * 3. **「现在该我做什么」只看 `phase` 加本人身份**，不解析提示词。提示词是文案，
 *    随时会改，拿它当协议早晚对不上。
 */
private fun WerewolfUiState.toLive(
    humanSeat: Int?,
    turn: HumanTurn?,
    avatars: Map<String, String?>,
    autoSpeak: Boolean = true,
    speechReady: Boolean = true,
): UIWerewolfLive {
    val table = table
    val game = table?.game
    val reveal = humanSeat == null || finished
    val voteCounts = game?.ballots
        ?.mapNotNull { it.targetSeat }
        ?.groupingBy { it }
        ?.eachCount()
        .orEmpty()

    val me = game?.players?.firstOrNull { it.seat == humanSeat }

    // 夜里「现在轮到谁」本身就是隐私：狼人商议挨个开口，守卫、预言家、女巫也都只有一个座位
    // 在动。把这个人亮在牌桌上，等于把身份直接摊给好人看。观战和本人轮到时照常亮。
    val actorSeat = progress?.seat?.takeIf { seat ->
        reveal || !progress.phase.hidesActor || seat == humanSeat ||
            (me?.role == WerewolfRole.WEREWOLF && progress.phase == Phase.NIGHT_WOLF_CHAT)
    }

    val seats = game?.players?.map { player ->
        // 翻过牌的白痴身份是公开的：牌桌上必须给所有人看见，
        // 否则会留下一个「没有身份标记却还活着」的座位，读起来自相矛盾
        val known = reveal || (player.role == WerewolfRole.IDIOT && game.idiotRevealed)
        UIWerewolfSeatLive(
            seat = player.seat,
            name = player.name,
            avatarPath = avatars[player.id],
            isSelf = player.seat == humanSeat,
            alive = player.alive,
            // 参与中的局不暴露刀/毒：那是 AI 玩家拿不到的私有信息。
            // 只给一个中性的「夜」，让「他死了」这件事照常可见。
            // 观战和已结束才显示真实死因——那时复盘本来就该看清。
            fate = player.deathCause?.toFate()?.let { fate ->
                if (!reveal && (fate == UIWerewolfFate.WOLF_KILL || fate == UIWerewolfFate.WITCH_POISON)) {
                    UIWerewolfFate.NIGHT
                } else {
                    fate
                }
            },
            roleLabel = if (known) player.role.displayName else null,
            wolf = player.role == WerewolfRole.WEREWOLF,
            active = actorSeat == player.seat,
            votes = voteCounts[player.seat],
            // 警长是公开身份，牌桌上必须一直挂着——它是「谁的一票值 1.5」的唯一提示
            sheriff = game.sheriffSeat == player.seat,
        )
    }.orEmpty()

    val visible = when {
        table == null -> emptyList()
        humanSeat == null -> table.transcript
        else -> table.transcriptFor(humanSeat)
    }

    val lines = visible.mapIndexed { index, message ->
        val speaker = message.seat?.let { seat -> game?.players?.firstOrNull { it.seat == seat } }
        UIWerewolfLine(
            index = index,
            kind = when (message.kind) {
                MessageKind.DIVINE -> UIWerewolfLineKind.DIVINE
                MessageKind.SPEECH -> UIWerewolfLineKind.SPEECH
                MessageKind.VOTE -> UIWerewolfLineKind.VOTE
                MessageKind.WOLF_CHAT, MessageKind.NIGHT_ACT -> UIWerewolfLineKind.PRIVATE
            },
            seat = message.seat,
            speaker = speaker?.name,
            avatarPath = speaker?.id?.let { avatars[it] },
            text = message.text,
        )
    }

    return UIWerewolfLive(
        day = game?.day ?: 1,
        phaseLabel = game?.phase?.displayName ?: "准备中",
        night = game?.phase?.let { it.isNightPhase() } ?: false,
        running = running,
        finished = finished,
        winnerLabel = game?.winner?.let { "${it.displayName}胜" },
        progressLabel = progress?.label,
        // 并发阶段（狼人商议、投票）没有确定的「第几个」，那时宁可不画进度
        progressFraction = progress?.let { p ->
            if (p.step > 0 && p.total > 0) p.step.toFloat() / p.total else null
        },
        seats = seats,
        lines = lines,
        selfSeat = humanSeat,
        selfRoleLabel = me?.role?.displayName,
        selfFactionLabel = me?.role?.faction?.displayName,
        spectating = humanSeat == null,
        turn = if (game != null) buildTurn(game, humanSeat, me, turn) else null,
        error = error,
        autoSpeak = autoSpeak,
        speechReady = speechReady,
    )
}

/**
 * 现在轮到我做什么。
 *
 * 只认 `phase` + 本人身份。人类座位和 AI 座位共用同一条请求路径，
 * 所以「该不该弹面板」不能靠 `turn != null` 判断——`turn` 只说明导演正在等这个座位。
 */
private fun buildTurn(
    game: WerewolfGame,
    humanSeat: Int?,
    me: WerewolfPlayer?,
    waiting: HumanTurn?,
): UIWerewolfTurn? {
    if (humanSeat == null || me == null || waiting == null) return null

    val others = game.alivePlayers
        .filter { it.seat != humanSeat }
        .map { UIWerewolfTarget(seat = it.seat, name = it.name) }

    return when (game.phase) {
        // 上警报名：点自己 = 上警，点「不上警」= 弃权。
        // 复用 VOTE 面板而不是新做一个开关：这一步的答案就是「一个座位或 null」，
        // 和投票的形状完全一样，多一种面板只会多一处要维护的按钮文案
        Phase.DAY_SHERIFF_SIGNUP -> UIWerewolfTurn(
            kind = UIWerewolfTurnKind.VOTE,
            seat = humanSeat,
            title = "要不要竞选警长",
            hint = "警长票权 1.5 票，上警的人不能投警长票",
            targets = listOf(UIWerewolfTarget(seat = humanSeat, name = "上警")),
            canAbstain = true,
            abstainLabel = "不上警",
        )

        Phase.DAY_SHERIFF_SPEECH -> UIWerewolfTurn(
            kind = UIWerewolfTurnKind.SPEECH,
            seat = humanSeat,
            title = if (game.sheriffRound > 1) "PK 发言" else "竞选发言",
            hint = "警下的人会按这几句投票",
        )

        Phase.DAY_SHERIFF_VOTE -> UIWerewolfTurn(
            kind = UIWerewolfTurnKind.VOTE,
            seat = humanSeat,
            title = "投票选警长",
            hint = if (game.sheriffRound > 1) "PK 后重投" else "只能投上警的人",
            targets = others.filter { it.seat in game.sheriffCandidates },
            canAbstain = true,
        )

        Phase.DAY_LAST_WORDS -> if (game.sheriffSeat == humanSeat) {
            // 警长的遗言要同时交代警徽。用 SEAT 面板：选一个人就是交给他，
            // 「撕掉警徽」走弃权那条路
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SEAT,
                seat = humanSeat,
                title = "遗言：警徽交给谁",
                hint = "交错了人，比撕掉更糟",
                targets = others,
                canAbstain = true,
                abstainLabel = "撕掉警徽",
            )
        } else {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SPEECH,
                seat = humanSeat,
                title = "说遗言",
                hint = "说给还活着的人听",
            )
        }

        Phase.DAY_DISCUSSION -> UIWerewolfTurn(
            kind = UIWerewolfTurnKind.SPEECH,
            seat = humanSeat,
            title = "轮到你发言",
            hint = "第 ${game.day} 天",
        )

        Phase.DAY_VOTE -> UIWerewolfTurn(
            kind = UIWerewolfTurnKind.VOTE,
            seat = humanSeat,
            title = if (game.voteRound > 1) "平票，再投一次" else "投票放逐",
            hint = if (game.voteCandidates != null) "只能投平票的人" else null,
            targets = if (game.voteCandidates != null) {
                others.filter { it.seat in game.voteCandidates }
            } else {
                // 翻牌的白痴不能再被投：投他只是浪费一次放逐，还会把白天拖成平票
                others.filter { it.seat in game.votingSeats }
            },
            canAbstain = true,
        )

        Phase.NIGHT_GUARD -> if (me.role == WerewolfRole.GUARD) {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SEAT,
                seat = humanSeat,
                title = "今晚守谁",
                hint = game.lastGuardedSeat?.let { "昨夜守了 $it 号，不能连守" },
                // 守卫可以守自己，所以候选里包含本人座位
                targets = game.alivePlayers
                    .filter { it.seat in game.guardTargets }
                    .map { UIWerewolfTarget(seat = it.seat, name = it.name) },
                canAbstain = true,
                abstainLabel = "这晚不守",
            )
        } else {
            null
        }

        // 狼队内部商量。人也要能开口：他这一句队友看得见，下一步定刀才谈得拢。
        // 没人说话也能过——真正的决定在下一个阶段
        Phase.NIGHT_WOLF_CHAT -> if (me.role == WerewolfRole.WEREWOLF) {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SPEECH,
                seat = humanSeat,
                title = "和队友商量今晚刀谁",
                hint = "只有狼看得到",
            )
        } else {
            null
        }

        Phase.NIGHT_WOLF -> if (me.role == WerewolfRole.WEREWOLF) {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SEAT,
                seat = humanSeat,
                title = if (game.wolfVoteRound > 1) "平票，重新定刀" else "今晚刀谁",
                targets = others.filter { target ->
                    game.players.firstOrNull { it.seat == target.seat }?.role != WerewolfRole.WEREWOLF
                },
            )
        } else {
            null
        }

        Phase.NIGHT_SEER -> if (me.role == WerewolfRole.SEER) {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SEAT,
                seat = humanSeat,
                title = "查验谁",
                hint = "只知道是好人还是狼",
                targets = others,
            )
        } else {
            null
        }

        Phase.NIGHT_WITCH -> if (me.role == WerewolfRole.WITCH) {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.WITCH,
                seat = humanSeat,
                title = "女巫用药",
                targets = others,
                canHeal = game.witchCanHeal,
                canPoison = game.witchCanPoison,
                healTarget = game.tonightVictim?.let { "$it 号" },
            )
        } else {
            null
        }

        Phase.HUNTER_SHOOT -> if (
            me.role == WerewolfRole.HUNTER && game.pendingHunterSeat == humanSeat
        ) {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SEAT,
                seat = humanSeat,
                title = "开枪带走一个人",
                hint = "也可以不开",
                targets = others,
                canAbstain = true,
                abstainLabel = "不开枪",
            )
        } else {
            null
        }

        Phase.FINISHED -> null
    }
}

/** 夜晚几个阶段算夜。猎人开枪归到白天播报里，所以不算。 */
/**
 * 这个阶段「谁在行动」是不是隐私。
 *
 * 白天发言、投票、猎人开枪都是公开的；夜里那些阶段一次只有一个座位能动，
 * 亮出来就等于把身份说穿。狼人下刀本来就是并发的，没有「当前是谁」，单独排除。
 */
private val Phase.hidesActor: Boolean
    get() = isNightPhase() && this != Phase.NIGHT_WOLF

private fun Phase.isNightPhase(): Boolean =
    this == Phase.NIGHT_GUARD || this == Phase.NIGHT_WOLF_CHAT ||
        this == Phase.NIGHT_WOLF || this == Phase.NIGHT_SEER || this == Phase.NIGHT_WITCH

private fun DeathCause.toFate(): UIWerewolfFate = when (this) {
    DeathCause.WOLF_KILL -> UIWerewolfFate.WOLF_KILL
    DeathCause.WITCH_POISON -> UIWerewolfFate.WITCH_POISON
    DeathCause.EXILE -> UIWerewolfFate.EXILE
    DeathCause.HUNTER_SHOOT -> UIWerewolfFate.HUNTER_SHOOT
    DeathCause.SELF_EXPLODE -> UIWerewolfFate.SELF_EXPLODE
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
