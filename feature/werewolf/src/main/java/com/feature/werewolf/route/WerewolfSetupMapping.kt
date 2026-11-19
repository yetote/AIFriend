package com.feature.werewolf.route

// 配置页 / 历史列表 → UI 的映射：板子预设、席位、好友、探活结果、列表行。

import com.feature.werewolf.game.Faction
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.store.WerewolfGameSummary
import com.feature.werewolf.vm.ApiCheck
import com.feature.werewolf.vm.ApiCheckState
import com.feature.werewolf.vm.WerewolfMode
import com.ui.werewolf.data.UIWerewolfApiCheck
import com.ui.werewolf.data.UIWerewolfApiState
import com.ui.werewolf.data.UIWerewolfFriend
import com.ui.werewolf.data.UIWerewolfMode
import com.ui.werewolf.data.UIWerewolfRecord
import com.ui.werewolf.data.UIWerewolfSeat

/**
 * 狼人杀的入口。
 *
 * 只做三件事：把领域数据翻成展示模型、把界面操作翻回 ViewModel、在「列表 / 配置 / 对局」
 * 之间切换。规则、请求调度、存档都在更下层，这一层不出现任何判定逻辑。
 */
internal fun configOf(presetKey: String): WerewolfConfig = WerewolfBoards.configOf(presetKey)

/**
 * 身份构成文案，例如「4 狼 · 预女猎守 · 4 民」。
 *
 * 神职合成一组而不是逐个报数：一个 12 人板有六个身份，逐个写「1 预言家 · 1 女巫 · 1 猎人 ·
 * 1 守卫 · 4 平民」在卡片里会折成三行，而玩家扫一眼要的其实就是「几狼几神几民」。
 *
 * 神职直接问 [WerewolfRole.isGod]，不在这里另抄一份名单：抄出来的那份不会随新身份一起更新，
 * 也不会编译报错，只会安静地在卡片上少显示一个「守」或「白」。
 */
internal fun compositionText(composition: Map<WerewolfRole, Int>): String = buildList {
    val wolves = composition.filterKeys { it.isWerewolf }.values.sum()
    if (wolves > 0) add("$wolves 狼")
    val gods = WerewolfRole.entries
        .filter { it.isGod }
        .mapNotNull { role -> if ((composition[role] ?: 0) > 0) role.shortName else null }
    if (gods.isNotEmpty()) add(gods.joinToString(""))
    val villagers = composition[WerewolfRole.VILLAGER] ?: 0
    if (villagers > 0) add("$villagers 民")
}.joinToString(" · ")

internal fun UIWerewolfMode.toMode(): WerewolfMode = when (this) {
    UIWerewolfMode.PLAY -> WerewolfMode.PLAY
    UIWerewolfMode.SPECTATE -> WerewolfMode.SPECTATE
}

internal fun com.core.ai.model.Friend.toUiFriend() = UIWerewolfFriend(
    id = id,
    name = name,
    avatarPath = avatarPath,
    role = role,
    model = model,
)

internal fun ApiCheck.toUiCheck() = UIWerewolfApiCheck(
    state = when (state) {
        ApiCheckState.CHECKING -> UIWerewolfApiState.CHECKING
        ApiCheckState.OK -> UIWerewolfApiState.OK
        ApiCheckState.FAILED -> UIWerewolfApiState.FAILED
    },
    message = message,
    elapsedMs = elapsedMs,
)

internal fun buildSeats(
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

internal fun WerewolfGameSummary.toRecord(): UIWerewolfRecord = UIWerewolfRecord(
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
internal fun relativeTime(at: Long): String {
    val diff = (System.currentTimeMillis() - at).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        else -> "${diff / 86_400_000L} 天前"
    }
}
