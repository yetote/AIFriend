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
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.Faction
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.prompt.WerewolfPrompt
import com.feature.werewolf.store.WerewolfGameSummary
import com.feature.werewolf.vm.WerewolfMode
import com.feature.werewolf.vm.WerewolfViewModel
import com.ui.werewolf.UIWerewolfLiveScreen
import com.ui.werewolf.UIWerewolfSetupScreen
import com.ui.werewolf.data.UIWerewolfFriend
import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfMode
import com.ui.werewolf.data.UIWerewolfPreset
import com.ui.werewolf.data.UIWerewolfRecord
import com.ui.werewolf.data.UIWerewolfSeat

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

        WerewolfScreen.Table -> UIWerewolfLiveScreen(
            live = state.toLive(),
            onExit = onExit,
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

private fun WerewolfUiState.toLive(): UIWerewolfLive {
    val game = table?.game
    return UIWerewolfLive(
        day = game?.day ?: 1,
        phaseLabel = game?.phase?.displayName ?: "准备中",
        running = running,
        finished = finished,
        winnerLabel = game?.winner?.let { "${it.displayName}胜" },
        progressLabel = progress?.label,
        // 并发阶段（狼人商议、投票）没有确定的「第几个」，那时宁可不画进度
        progressFraction = progress?.let { p ->
            if (p.step > 0 && p.total > 0) p.step.toFloat() / p.total else null
        },
        broadcasts = game?.history?.map { WerewolfPrompt.describe(it) }.orEmpty(),
        error = error,
    )
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
