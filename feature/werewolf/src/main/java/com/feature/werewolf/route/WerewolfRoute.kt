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
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPlayer
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.store.WerewolfGameSummary
import com.feature.werewolf.vm.WerewolfMode
import com.feature.werewolf.vm.WerewolfViewModel
import com.ui.werewolf.UIWerewolfSetupScreen
import com.ui.werewolf.UIWerewolfTableScreen
import com.ui.werewolf.data.UIWerewolfFate
import com.ui.werewolf.data.UIWerewolfFriend
import com.ui.werewolf.data.UIWerewolfLine
import com.ui.werewolf.data.UIWerewolfLineKind
import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfMode
import com.ui.werewolf.data.UIWerewolfPreset
import com.ui.werewolf.data.UIWerewolfRecord
import com.ui.werewolf.data.UIWerewolfSeat
import com.ui.werewolf.data.UIWerewolfSeatLive
import com.ui.werewolf.data.UIWerewolfTarget
import com.ui.werewolf.data.UIWerewolfTurn
import com.ui.werewolf.data.UIWerewolfTurnKind

/**
 * 狼人杀的入口。
 *
 * 只做三件事：把领域数据翻成展示模型、把界面操作翻回 ViewModel、在「配置」和「对局」
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

    var screen by remember { mutableStateOf<WerewolfScreen>(WerewolfScreen.Setup) }
    var mode by remember { mutableStateOf(UIWerewolfMode.PLAY) }
    var presetKey by remember { mutableStateOf(PRESET_STANDARD_8) }
    var selectedIds by rememberSaveable(saver = SelectedIdsSaver) { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // 开局（或续档）之后局面就出现了，直接进对局页。
    // 用 table 而不是 running 判断：断点续玩恢复出来的局面 running 还是 false。
    // 已经分出胜负的局不切过去——重进页面时应该看到配置页，而不是上一局的结束画面。
    LaunchedEffect(state.table, state.finished) {
        if (state.table != null && !state.finished) screen = WerewolfScreen.Table
    }

    BackHandler { onExit() }

    val uiFriends = remember(friends) { friends.map { it.toUiFriend() } }
    // 座位 → 好友头像。牌桌上认人主要靠头像，名字在 60dp 里会被截断。
    val avatars = remember(friends) { friends.associate { it.id to it.avatarPath } }
    val records = remember(summaries) { summaries.take(5).map { it.toRecord() } }
    val unfinished = records.firstOrNull { !it.finished }
    val preset = PRESETS.firstOrNull { it.key == presetKey } ?: PRESETS.first()

    when (screen) {
        WerewolfScreen.Setup -> UIWerewolfSetupScreen(
            mode = mode,
            presets = PRESETS,
            presetKey = presetKey,
            friends = uiFriends,
            selectedIds = selectedIds,
            seats = buildSeats(uiFriends, selectedIds, mode, preset.playerCount),
            records = records,
            unfinished = unfinished,
            error = error,
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
            },
            onResume = {
                viewModel.resumeLatest()
            },
            onAbandon = {
                viewModel.abandon()
            },
            onClearError = viewModel::clearError,
            onBack = onExit,
            onStart = {
                viewModel.startGame(
                    mode = mode.toMode(),
                    friendIds = selectedIds.toList(),
                    config = configOf(presetKey),
                )
            },
        )

        WerewolfScreen.Table -> UIWerewolfTableScreen(
            live = state.toLive(humanSeat = humanSeat, turn = humanTurn, avatars = avatars),
            onExit = onExit,
            onSpeech = viewModel::submitSpeech,
            onVote = { viewModel.submitTarget(it) },
            onSeat = { viewModel.submitSeat(it) },
            onWitch = { heal, poison -> viewModel.submitWitch(heal, poison) },
        )
    }
}

// ---------------------------------------------------------------------------
// 展示模型映射
// ---------------------------------------------------------------------------

private const val PRESET_STANDARD_8 = "standard8"
private const val PRESET_STANDARD_9 = "standard9"

/**
 * 板子列表。
 *
 * 身份构成文案由这里根据 `WerewolfConfig` 现拼，不在 UI 里再写一份——
 * 规则改了文案会自动跟着变，不会出现「界面写着 3 神、引擎发 2 神」。
 */
private val PRESETS = listOf(
    UIWerewolfPreset(
        key = PRESET_STANDARD_8,
        title = "8 人标准局",
        playerCount = WerewolfConfig.Standard8.values.sum(),
        composition = compositionText(WerewolfConfig.Standard8),
    ),
    UIWerewolfPreset(
        key = PRESET_STANDARD_9,
        title = "9 人标准局",
        playerCount = WerewolfConfig.Standard9.values.sum(),
        composition = compositionText(WerewolfConfig.Standard9),
    ),
)

private fun configOf(presetKey: String): WerewolfConfig = when (presetKey) {
    PRESET_STANDARD_9 -> WerewolfConfig(composition = WerewolfConfig.Standard9)
    else -> WerewolfConfig()
}

private fun compositionText(composition: Map<WerewolfRole, Int>): String =
    listOf(
        WerewolfRole.WEREWOLF to "狼",
        WerewolfRole.SEER to "预言家",
        WerewolfRole.WITCH to "女巫",
        WerewolfRole.HUNTER to "猎人",
        WerewolfRole.VILLAGER to "民",
    ).mapNotNull { (role, label) ->
        composition[role]?.takeIf { it > 0 }?.let { "$it $label" }
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
): UIWerewolfLive {
    val table = table
    val game = table?.game
    val reveal = humanSeat == null || finished
    val voteCounts = game?.ballots
        ?.mapNotNull { it.targetSeat }
        ?.groupingBy { it }
        ?.eachCount()
        .orEmpty()

    val seats = game?.players?.map { player ->
        UIWerewolfSeatLive(
            seat = player.seat,
            name = player.name,
            avatarPath = avatars[player.id],
            isSelf = player.seat == humanSeat,
            alive = player.alive,
            fate = player.deathCause?.toFate(),
            roleLabel = if (reveal) player.role.displayName else null,
            wolf = player.role == WerewolfRole.WEREWOLF,
            active = progress?.seat == player.seat,
            votes = voteCounts[player.seat],
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

    val me = game?.players?.firstOrNull { it.seat == humanSeat }

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
                others
            },
            canAbstain = true,
        )

        Phase.NIGHT_WOLF -> if (me.role == WerewolfRole.WEREWOLF) {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SEAT,
                seat = humanSeat,
                title = if (game.wolfVoteRound > 1) "重新商议：今晚刀谁" else "今晚刀谁",
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
            )
        } else {
            null
        }

        Phase.FINISHED -> null
    }
}

/** 夜晚三段算夜。猎人开枪归到白天播报里，所以不算。 */
private fun Phase.isNightPhase(): Boolean =
    this == Phase.NIGHT_WOLF || this == Phase.NIGHT_SEER || this == Phase.NIGHT_WITCH

private fun DeathCause.toFate(): UIWerewolfFate = when (this) {
    DeathCause.WOLF_KILL -> UIWerewolfFate.WOLF_KILL
    DeathCause.WITCH_POISON -> UIWerewolfFate.WITCH_POISON
    DeathCause.EXILE -> UIWerewolfFate.EXILE
    DeathCause.HUNTER_SHOOT -> UIWerewolfFate.HUNTER_SHOOT
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
