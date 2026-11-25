package com.ui.werewolf

// 牌桌增强面板：点击座位档案 + 手动贴标签 + 只看 TA 发言、公开台账弹窗、关键事件戏剧横幅。

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ui.common.UIAvatar
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UIShadowSoft
import com.ui.werewolf.data.UIWerewolfLine
import com.ui.werewolf.data.UIWerewolfLineKind
import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfPlayerMark
import com.ui.werewolf.data.UIWerewolfSeatLive
import com.ui.werewolf.data.UIWerewolfVerdict
import com.ui.werewolf.data.shortModelLabel
import kotlinx.coroutines.delay

/**
 * 点击座位头像弹出的「玩家档案 + 贴身份标签 + 只看 TA 发言/票型」弹窗。
 *
 * 严格遵循映射层的信息边界：
 * - 所有底牌 / 死因 / 私密狼坑本字段直接读 [seat] 上由映射层按视角裁剪过的值，界面绝不越界推算；
 * - 历史发言与票型严格只从 [live.lines]（已经过视角与朗读进度双重过滤）里筛选；
 * - 手动贴身份标签仅在游玩模式（非观战、非终局、非回放）且非本人/非已知狼队友时提供。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeatDossierDialog(
    seat: UIWerewolfSeatLive,
    live: UIWerewolfLive,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    manualMark: UIWerewolfPlayerMark?,
    onSetMark: (UIWerewolfPlayerMark?) -> Unit,
    onDismiss: () -> Unit,
) {
    val surface = if (night) NightSurface else DaySurface
    val border = if (night) NightStroke else DayStroke
    val canMark = !live.spectating && !live.finished && live.replay == null && !seat.isSelf && !seat.isWolfMate

    // 从当前视角已经允许看到的 live.lines 中筛选该座位的发言、动作与公开票型
    val seatEntries = remember(live.lines, seat.seat) {
        buildList {
            live.lines.forEach { line ->
                when {
                    line.seat == seat.seat -> {
                        val tag = when (line.kind) {
                            UIWerewolfLineKind.SPEECH -> "发言"
                            UIWerewolfLineKind.PRIVATE -> "夜聊"
                            UIWerewolfLineKind.VOTE -> "投票"
                            UIWerewolfLineKind.SEER_RESULT -> "查验"
                            UIWerewolfLineKind.INNER -> line.innerLabel ?: "行动"
                            else -> "记录"
                        }
                        add(SeatHistoryEntry(day = line.day, tag = tag, text = line.text, secret = line.secret))
                    }
                    line.kind == UIWerewolfLineKind.BALLOT -> {
                        val card = line.ballot ?: return@forEach
                        val myBallot = card.ballots.firstOrNull { it.voterSeat == seat.seat } ?: return@forEach
                        val votedText = myBallot.targetSeat?.let { "投给 $it 号" } ?: "弃票"
                        add(SeatHistoryEntry(day = line.day, tag = card.title, text = votedText, secret = false))
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .shadow(16.dp, RoundedCornerShape(UIRadiusCard), clip = false, UIShadowSoft, UIShadowSoft)
                .clip(RoundedCornerShape(UIRadiusCard))
                .background(surface)
                .border(0.8.dp, border, RoundedCornerShape(UIRadiusCard))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 头部：头像 + 座位号/名字 + 状态/身份徽标 + 模型名
            Row(verticalAlignment = Alignment.CenterVertically) {
                UIAvatar(
                    name = seat.name,
                    avatarPath = seat.avatarPath,
                    size = 42.dp,
                    shape = CircleShape,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${seat.seat} 号 ${seat.name}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (seat.isSelf) {
                            DossierTag("你", if (night) SelfBlueNight else SelfBlue, night)
                        }
                        if (seat.sheriff) {
                            DossierTag("警长", SheriffGold, night)
                        } else if (seat.sheriffCandidate) {
                            DossierTag("警上", SheriffCandidateAmber, night)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val statusText = if (seat.alive) "存活" else (seat.fate?.full ?: "已出局")
                        Text(
                            text = statusText,
                            fontSize = FsMeta,
                            color = if (seat.alive) (if (night) SeerTealNight else SeerTeal) else FateRed,
                        )
                        seat.roleLabel?.let { role ->
                            DossierTag(role, if (seat.wolf) FateRed else if (night) SelfBlueNight else SelfBlue, night)
                        }
                        if (seat.isWolfMate && seat.roleLabel == null) {
                            DossierTag("狼队友", FateRed, night)
                        }
                        seat.seerVerdict?.let { v ->
                            DossierTag(
                                text = "验：${v.label}",
                                accent = if (v == UIWerewolfVerdict.WOLF) FateRed else if (night) SeerTealNight else SeerTeal,
                                night = night,
                            )
                        }
                    }
                    val modelLabel = shortModelLabel(seat.model)
                    if (modelLabel.isNotEmpty()) {
                        Text(
                            text = "模型：$modelLabel",
                            fontSize = 10.5.sp,
                            color = dimColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 上帝视角/终局专属：展示该 AI 实时写下的私密狼坑本（游玩模式下 beliefNote 恒为 null）
            seat.beliefNote?.let { note ->
                val accent = if (night) Color(0xFFE5B869) else Color(0xFF9A6712)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = if (night) 0.14f else 0.08f))
                        .border(0.7.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "AI 私密狼坑本（仅上帝视角可见）",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                    Text(
                        text = note,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = textColor,
                    )
                }
            }

            // 游玩模式专属：手动贴身份标签（纯本地笔记）
            if (canMark) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "我的身份记号",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "仅自己可见，挂在头像角标",
                            fontSize = 10.5.sp,
                            color = dimColor,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        UIWerewolfPlayerMark.entries.forEach { mark ->
                            val selected = manualMark == mark
                            val accent = if (mark.hostile) FateRed else if (night) SeerTealNight else SeerTeal
                            Text(
                                text = mark.label,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (selected) accent.copy(alpha = if (night) 0.32f else 0.18f)
                                        else if (night) NightControlFill else DayControlFill
                                    )
                                    .border(
                                        0.7.dp,
                                        if (selected) accent else if (night) NightControlBorder else DayControlBorder,
                                        RoundedCornerShape(50),
                                    )
                                    .clickable { onSetMark(if (selected) null else mark) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = FsMeta,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) accent else textColor,
                            )
                        }
                        if (manualMark != null) {
                            Text(
                                text = "清除",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (night) NightControlFill else DayControlFill)
                                    .clickable { onSetMark(null) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = FsMeta,
                                color = dimColor,
                            )
                        }
                    }
                }
            }

            // 该座位的全部可见发言与票型
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "TA 的发言与票型（${seatEntries.size} 条）",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
                if (seatEntries.isEmpty()) {
                    Text(
                        text = "截至目前还没有 TA 的可见发言或投票记录。",
                        fontSize = 12.sp,
                        color = dimColor,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        seatEntries.forEach { entry ->
                            val tagColor = when {
                                entry.secret -> if (night) Color(0xFFC8A6FF) else PoisonPurple
                                entry.tag.contains("投") -> SheriffGold
                                else -> if (night) SelfBlueNight else SelfBlue
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (night) NightControlFill else DayControlFill)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "第 ${entry.day} 天",
                                        fontSize = 10.5.sp,
                                        color = dimColor,
                                    )
                                    DossierTag(entry.tag, tagColor, night)
                                    if (entry.secret) {
                                        Text(
                                            text = "仅当前视角可见",
                                            fontSize = 10.sp,
                                            color = dimColor,
                                        )
                                    }
                                }
                                Text(
                                    text = entry.text,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = textColor,
                                )
                            }
                        }
                    }
                }
            }

            // 关闭按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .background(if (night) NightControlFill else DayControlFill)
                    .border(0.7.dp, if (night) NightControlBorder else DayControlBorder, RoundedCornerShape(UIRadiusControl))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "关闭",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                )
            }
        }
    }
}

private data class SeatHistoryEntry(
    val day: Int,
    val tag: String,
    val text: String,
    val secret: Boolean,
)

@Composable
private fun DossierTag(text: String, accent: Color, night: Boolean) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = if (night) 0.24f else 0.13f))
            .padding(horizontal = 7.dp, vertical = 1.5.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = accent,
    )
}

/**
 * 顶栏「台账」速查弹窗：展示全员可见的公开台账（对跳/验人/警徽流/银水/毒口/票型对账）。
 *
 * 狼队开局风向卡只在 [live.wolfStyleCard] 非空时出现（映射层已严格限制：仅上帝视角/终局/狼人可见，
 * 好人游玩模式绝不下发）。
 */
@Composable
internal fun PublicLedgerDialog(
    live: UIWerewolfLive,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    onDismiss: () -> Unit,
) {
    val surface = if (night) NightSurface else DaySurface
    val border = if (night) NightStroke else DayStroke
    val cleanedLines = remember(live.publicLedger) {
        live.publicLedger.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("【公开台账】") && !it.startsWith("下面这些是从") }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .shadow(16.dp, RoundedCornerShape(UIRadiusCard), clip = false, UIShadowSoft, UIShadowSoft)
                .clip(RoundedCornerShape(UIRadiusCard))
                .background(surface)
                .border(0.8.dp, border, RoundedCornerShape(UIRadiusCard))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "公开台账",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "全桌公开发言与唱票的实时对账",
                    fontSize = 11.sp,
                    color = dimColor,
                )
            }

            live.wolfStyleCard?.let { card ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(FateRed.copy(alpha = if (night) 0.16f else 0.08f))
                        .border(0.7.dp, FateRed.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = if (live.spectating || live.finished) {
                            "本局狼队开局风向（上帝视角）"
                        } else {
                            "狼队开局风向（仅狼队可见）"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FateRed,
                    )
                    Text(
                        text = card,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = textColor,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (cleanedLines.isEmpty()) {
                    Text(
                        text = "还没有可记账的公开信息（如跳神、报验人、留警徽流、报银水或公开唱票）。",
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = dimColor,
                    )
                } else {
                    cleanedLines.forEach { row ->
                        val isWarning = row.contains("至少有一个在说谎") || row.contains("话和票对不上")
                        val accent = when {
                            isWarning -> FateRed
                            row.startsWith("· 预言家") -> if (night) SeerTealNight else SeerTeal
                            row.startsWith("· 女巫") -> if (night) Color(0xFFC8A6FF) else PoisonPurple
                            else -> textColor
                        }
                        Text(
                            text = row,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            fontWeight = if (isWarning) FontWeight.SemiBold else FontWeight.Normal,
                            color = accent,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .background(if (night) NightControlFill else DayControlFill)
                    .border(0.7.dp, if (night) NightControlBorder else DayControlBorder, RoundedCornerShape(UIRadiusControl))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "知道了",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                )
            }
        }
    }
}

/**
 * 关键公开事件短横幅（天亮死讯 / 平安夜 / 狼人自爆 / 猎人开枪 / 白痴翻牌 / 警长当选）。
 *
 * **信息边界保证**：只从已经过视角与朗读闸门过滤的 [UIWerewolfLive.lines] 中的公开 `DIVINE` 播报触发，
 * 绝不碰未公开的 `pendingDawn` 或真实死因；且完全不吃触控、2.2 秒自动收起。
 */
@Composable
internal fun KeyEventBanner(
    lines: List<UIWerewolfLine>,
    night: Boolean,
    modifier: Modifier = Modifier,
) {
    // 记录上次已看过的最后一条下标，首次进入/续档恢复时不把旧历史当新事件弹出来
    var seenMaxIndex by remember { mutableStateOf(lines.lastOrNull()?.index ?: -1) }
    var banner by remember { mutableStateOf<KeyEventBannerModel?>(null) }

    val latest = lines.lastOrNull()
    LaunchedEffect(latest?.index, latest?.text) {
        if (latest == null) {
            seenMaxIndex = -1
            return@LaunchedEffect
        }
        if (latest.index < seenMaxIndex) {
            // 换了新局或回放倒退，重置基线不弹旧消息
            seenMaxIndex = latest.index
            banner = null
            return@LaunchedEffect
        }
        if (latest.index == seenMaxIndex) return@LaunchedEffect
        seenMaxIndex = latest.index

        val candidate = parseKeyEventBanner(latest)
        if (candidate != null) {
            banner = candidate
            delay(2200)
            if (banner == candidate) banner = null
        }
    }

    AnimatedVisibility(
        visible = banner != null,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        val current = banner ?: return@AnimatedVisibility
        val bg = if (night) Color(0xEE1F1916) else Color(0xF5FFFDF8)
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .shadow(12.dp, RoundedCornerShape(50), clip = false, UIShadowSoft, UIShadowSoft)
                .clip(RoundedCornerShape(50))
                .background(bg)
                .border(1.dp, current.accent.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(current.accent)
            )
            Text(
                text = current.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (night) NightText else Color(0xFF1F1914),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal data class KeyEventBannerModel(
    val text: String,
    val accent: Color,
)

internal fun parseKeyEventBanner(line: UIWerewolfLine): KeyEventBannerModel? {
    if (line.kind != UIWerewolfLineKind.DIVINE || line.secret) return null
    val text = line.text.trim()
    return when {
        text.contains("平安夜") -> KeyEventBannerModel(text, HealGreen)
        text.contains("天亮：") && text.contains("出局") -> KeyEventBannerModel(text, FateRed)
        text.contains("自爆") -> KeyEventBannerModel(text, FateRed)
        text.contains("开枪带走") -> KeyEventBannerModel(text, FateRed)
        text.contains("翻开是白痴") -> KeyEventBannerModel(text, SelfBlue)
        text.contains("当选警长") || text.contains("警徽交给") -> KeyEventBannerModel(text, SheriffGold)
        else -> null
    }
}
