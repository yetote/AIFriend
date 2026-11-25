package com.ui.werewolf

// 选目标弹窗：暖羊皮白昼 × 烛影曜石黑夜的桌面牌卡美学，按行动语义自适应配色，支持两次点击防误触确认。

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ui.common.UIAvatar
import com.ui.common.theme.UIAtelierSand
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UIShadowSoft
import com.ui.werewolf.data.UIWerewolfPlayerMark
import com.ui.werewolf.data.UIWerewolfSeatLive
import com.ui.werewolf.data.UIWerewolfVerdict

/** 选目标弹窗的两次点击确认状态转移：首次点选加框、换点别人切框、再点同一座位才确认。 */
internal sealed interface TargetTapOutcome {
    data class Selected(val seat: Int) : TargetTapOutcome
    data class Confirmed(val seat: Int) : TargetTapOutcome
}

internal fun resolveTargetTap(currentSelected: Int?, tappedSeat: Int): TargetTapOutcome =
    if (currentSelected == tappedSeat) {
        TargetTapOutcome.Confirmed(tappedSeat)
    } else {
        TargetTapOutcome.Selected(tappedSeat)
    }

/** 按人数选择最工整的列数：6/9 人用 3 列，8/12 人用 4 列，避免出现参差尾行和过高弹窗。 */
internal fun targetGridColumns(seatCount: Int): Int = when {
    seatCount <= 4 -> 2
    seatCount <= 6 || seatCount == 9 -> 3
    else -> 4
}

/** 仅依据弹窗已公开展示的标题文案映射主题色与行动标签，不触碰任何私密状态。 */
internal data class TargetActionTheme(
    val badge: String,
    val accent: Color,
    val accentEnd: Color,
    val onAccent: Color,
)

internal fun resolveTargetActionTheme(title: String, night: Boolean): TargetActionTheme = when {
    title.contains("毒") -> TargetActionTheme(
        badge = "毒药",
        accent = if (night) Color(0xFFB78DFF) else PoisonPurple,
        accentEnd = if (night) Color(0xFF8C5CE6) else Color(0xFF6B32B8),
        onAccent = if (night) Color(0xFF1A0F2E) else Color.White,
    )
    title.contains("验") -> TargetActionTheme(
        badge = "查验",
        accent = if (night) SeerTealNight else SeerTeal,
        accentEnd = if (night) Color(0xFF34A893) else Color(0xFF145E50),
        onAccent = if (night) Color(0xFF0A241F) else Color.White,
    )
    title.contains("守") -> TargetActionTheme(
        badge = "守护",
        accent = if (night) SelfBlueNight else SelfBlue,
        accentEnd = if (night) Color(0xFF5A8BE0) else Color(0xFF1F4280),
        onAccent = if (night) Color(0xFF0C1C38) else Color.White,
    )
    title.contains("警") -> TargetActionTheme(
        badge = "警徽",
        accent = if (night) Color(0xFFF0C66E) else SheriffGold,
        accentEnd = if (night) Color(0xFFC99330) else Color(0xFFA97518),
        onAccent = if (night) Color(0xFF241806) else Color.White,
    )
    title.contains("刀") || title.contains("袭") || title.contains("杀") ||
        title.contains("枪") || title.contains("带走") -> TargetActionTheme(
        badge = "目标",
        accent = if (night) Color(0xFFFF7D75) else FateRed,
        accentEnd = if (night) Color(0xFFD64B43) else Color(0xFF9F2622),
        onAccent = if (night) Color(0xFF2A0B09) else Color.White,
    )
    else -> TargetActionTheme(
        badge = "投票",
        accent = if (night) Color(0xFFE5B869) else Color(0xFFC84B2A),
        accentEnd = if (night) Color(0xFFC48F3A) else Color(0xFFA63419),
        onAccent = if (night) Color(0xFF241708) else Color.White,
    )
}

@Composable
internal fun TargetPickDialog(
    title: String,
    hint: String?,
    seats: List<UIWerewolfSeatLive>,
    pickable: Set<Int>,
    textColor: Color,
    dimColor: Color,
    night: Boolean,
    manualMarks: Map<Int, UIWerewolfPlayerMark> = emptyMap(),
    onPick: (Int) -> Unit,
    abstainLabel: String?,
    onAbstain: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    // 两次点击防误触：第一次点选高亮加粗边框，第二次点同一座位（或点下方确认主按钮）才真正提交
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val theme = resolveTargetActionTheme(title, night)
    val dialogShape = RoundedCornerShape(24.dp)
    val dialogGradient = if (night) {
        Brush.verticalGradient(listOf(Color(0xFA231C19), Color(0xFA16110F)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFFDF9), Color(0xFFF6EFE4)))
    }
    val dialogBorder = if (night) Color(0x38F5E8D8) else Color(0x24241C14)
    val trayBg = if (night) Color(0x4A0C0908) else Color(0x70EAE0D0)
    val trayBorder = if (night) Color(0x1CF5E8D8) else Color(0x16241C14)

    val ordered = seats.sortedBy { it.seat }
    val selectedSeat = selected?.let { s -> ordered.firstOrNull { it.seat == s && it.seat in pickable } }
    val columns = targetGridColumns(ordered.size)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .widthIn(max = 432.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 22.dp,
                    shape = dialogShape,
                    clip = false,
                    ambientColor = if (night) Color(0xAA000000) else UIShadowSoft,
                    spotColor = if (night) Color(0xAA000000) else UIShadowSoft,
                )
                .clip(dialogShape)
                .background(dialogGradient)
                .border(1.dp, dialogBorder, dialogShape),
        ) {
            // 顶部行动氛围头：行动胶囊 + 标题 + 右侧实时状态徽标
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                theme.accent.copy(alpha = if (night) 0.16f else 0.09f),
                                Color.Transparent,
                            )
                        )
                    )
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = theme.badge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.accent.copy(alpha = if (night) 0.24f else 0.14f))
                            .border(0.7.dp, theme.accent.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.accent,
                    )
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val pillBg by animateColorAsState(
                        if (selectedSeat != null) {
                            theme.accent.copy(alpha = if (night) 0.28f else 0.16f)
                        } else if (night) {
                            NightControlFill
                        } else {
                            Color(0x14241C14)
                        },
                        label = "header-pill-bg",
                    )
                    Text(
                        text = if (selectedSeat != null) "已选 ${selectedSeat.seat} 号" else "可选 ${pickable.size} 人",
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(pillBg)
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedSeat != null) theme.accent else dimColor,
                    )
                }
                if (!hint.isNullOrBlank()) {
                    Text(
                        text = hint,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = dimColor,
                    )
                }
            }

            // 中段：微凹桌面托盘 + 自适应 3/4 列牌桌头像网格（小屏可顺滑滚动）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(trayBg)
                    .border(0.75.dp, trayBorder, RoundedCornerShape(18.dp))
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                val rows = ordered.chunked(columns)
                val hGap = if (columns >= 4) 7.dp else 9.dp
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(hGap),
                    ) {
                        row.forEach { seat ->
                            TargetSeatCard(
                                seat = seat,
                                enabled = seat.seat in pickable,
                                isSelected = selectedSeat?.seat == seat.seat,
                                theme = theme,
                                manualMark = manualMarks[seat.seat],
                                compact = columns >= 4,
                                textColor = textColor,
                                dimColor = dimColor,
                                night = night,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when (val outcome = resolveTargetTap(selected, seat.seat)) {
                                        is TargetTapOutcome.Selected -> selected = outcome.seat
                                        is TargetTapOutcome.Confirmed -> {
                                            onPick(outcome.seat)
                                            onDismiss()
                                        }
                                    }
                                },
                            )
                        }
                        repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }

            // 底段：当前目标预览胶囊 + 层级分明的操作栏
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SelectionHintBanner(
                    selectedSeat = selectedSeat,
                    theme = theme,
                    night = night,
                    textColor = textColor,
                    dimColor = dimColor,
                )

                TargetDialogActions(
                    selectedSeat = selectedSeat,
                    theme = theme,
                    night = night,
                    textColor = textColor,
                    dimColor = dimColor,
                    abstainLabel = abstainLabel,
                    onConfirm = { seat ->
                        onPick(seat)
                        onDismiss()
                    },
                    onAbstain = onAbstain,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun SelectionHintBanner(
    selectedSeat: UIWerewolfSeatLive?,
    theme: TargetActionTheme,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    val bannerBg by animateColorAsState(
        when {
            selectedSeat != null -> theme.accent.copy(alpha = if (night) 0.16f else 0.10f)
            night -> Color(0x12F5E8D8)
            else -> Color(0x0D241C14)
        },
        label = "hint-banner-bg",
    )
    val bannerBorder by animateColorAsState(
        when {
            selectedSeat != null -> theme.accent.copy(alpha = if (night) 0.42f else 0.34f)
            night -> Color(0x18F5E8D8)
            else -> Color(0x12241C14)
        },
        label = "hint-banner-border",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bannerBg)
            .border(0.8.dp, bannerBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selectedSeat != null) {
            UIAvatar(
                name = selectedSeat.name,
                avatarPath = selectedSeat.avatarPath,
                size = 20.dp,
                shape = CircleShape,
                modifier = Modifier.border(1.dp, theme.accent, CircleShape),
            )
            Text(
                text = "已选 ${selectedSeat.seat} 号「${selectedSeat.name}」，再点头像或点确认提交",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(theme.accent.copy(alpha = 0.7f))
            )
            Text(
                text = "点选一位目标 · 再点同一座位或点确认生效",
                fontSize = 11.5.sp,
                color = dimColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TargetDialogActions(
    selectedSeat: UIWerewolfSeatLive?,
    theme: TargetActionTheme,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    abstainLabel: String?,
    onConfirm: (Int) -> Unit,
    onAbstain: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(UIRadiusControl)
    val ctrlFill = if (night) NightControlFill else UIAtelierSand
    val ctrlBorder = if (night) NightControlBorder else UIGlassStroke
    val confirmReady = selectedSeat != null
    val confirmBrush = if (confirmReady) {
        Brush.linearGradient(listOf(theme.accent, theme.accentEnd))
    } else {
        Brush.linearGradient(listOf(ctrlFill, ctrlFill))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onAbstain != null) {
            // 取消
            Box(
                modifier = Modifier
                    .weight(0.82f)
                    .height(44.dp)
                    .clip(shape)
                    .background(ctrlFill)
                    .border(0.85.dp, ctrlBorder, shape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "取消",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = dimColor,
                )
            }
            // 弃票 / 不开枪 / 这晚不守（次级轮廓按钮，不再喧宾夺主）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(shape)
                    .background(if (night) Color(0x1CF5E8D8) else Color(0xFFFFFDF9))
                    .border(0.9.dp, if (night) Color(0x3CF5E8D8) else Color(0x2E241C14), shape)
                    .clickable {
                        onAbstain()
                        onDismiss()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = abstainLabel ?: "不做",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(shape)
                    .background(ctrlFill)
                    .border(0.85.dp, ctrlBorder, shape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = abstainLabel ?: "取消",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = dimColor,
                )
            }
        }

        // 主确认按钮：未选中时安静灰置，选中目标后亮起行动语义渐变
        Box(
            modifier = Modifier
                .weight(if (onAbstain != null) 1.38f else 1.45f)
                .height(44.dp)
                .then(
                    if (confirmReady) {
                        Modifier.shadow(
                            elevation = 8.dp,
                            shape = shape,
                            clip = false,
                            ambientColor = theme.accent,
                            spotColor = theme.accent,
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(shape)
                .background(confirmBrush)
                .then(
                    if (!confirmReady) Modifier.border(0.85.dp, ctrlBorder, shape)
                    else Modifier
                )
                .clickable(enabled = confirmReady) {
                    selectedSeat?.let { onConfirm(it.seat) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (selectedSeat != null) "确认 ${selectedSeat.seat} 号" else "确认目标",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (confirmReady) theme.onAccent else dimColor.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TargetSeatCard(
    seat: UIWerewolfSeatLive,
    enabled: Boolean,
    isSelected: Boolean,
    theme: TargetActionTheme,
    manualMark: UIWerewolfPlayerMark?,
    compact: Boolean,
    textColor: Color,
    dimColor: Color,
    night: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "seat-card-scale",
    )
    val cardBg by animateColorAsState(
        when {
            !enabled -> if (night) Color(0x0CF5E8D8) else Color(0x66F5EFE4)
            isSelected -> theme.accent.copy(alpha = if (night) 0.24f else 0.13f)
            else -> if (night) Color(0x1CF5E8D8) else Color(0xFFFFFDF9)
        },
        label = "seat-card-bg",
    )
    val borderColor by animateColorAsState(
        when {
            !enabled -> if (night) Color(0x14F5E8D8) else Color(0x12241C14)
            isSelected -> theme.accent
            else -> if (night) Color(0x28F5E8D8) else Color(0x1D241C14)
        },
        label = "seat-card-border",
    )
    val borderWidth = if (isSelected) 2.dp else 0.8.dp
    val rimColor = if (night) Color(0xFF1F1815) else Color(0xFFFFFDF9)
    val cardShape = RoundedCornerShape(16.dp)
    val avatarSize = if (compact) 40.dp else 44.dp

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isSelected) {
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = cardShape,
                        clip = false,
                        ambientColor = theme.accent,
                        spotColor = theme.accent,
                    )
                } else {
                    Modifier
                }
            )
            .clip(cardShape)
            .background(cardBg)
            .border(borderWidth, borderColor, cardShape)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 圆形头像徽章区：底居中座号胶囊，左上警徽，右上选中对勾 / 狼队友 / 玩家自定义记号
        Box(
            modifier = Modifier.size(width = avatarSize + 14.dp, height = avatarSize + 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val avatarRing = when {
                isSelected -> theme.accent
                seat.sheriff && seat.alive -> SheriffGold.copy(alpha = 0.85f)
                seat.sheriffCandidate && seat.alive -> SheriffCandidateAmber.copy(alpha = 0.80f)
                enabled -> if (night) Color(0x38F5E8D8) else Color(0x22241C14)
                else -> Color.Transparent
            }
            UIAvatar(
                name = seat.name,
                avatarPath = seat.avatarPath,
                size = avatarSize,
                shape = CircleShape,
                modifier = Modifier
                    .alpha(if (seat.alive) (if (enabled) 1f else 0.62f) else 0.28f)
                    .then(
                        if (avatarRing != Color.Transparent) {
                            Modifier.border(if (isSelected) 2.dp else 1.dp, avatarRing, CircleShape)
                        } else {
                            Modifier
                        }
                    ),
            )

            // 座号药丸：居中压在头像底边，干净利落不遮挡脸部也不撞角标
            val seatPillBg = when {
                isSelected -> theme.accent
                enabled -> if (night) Color(0xFF3E342D) else Color(0xFF332922)
                else -> if (night) Color(0xFF2B2420) else Color(0xFF9C9286)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(50))
                    .background(seatPillBg)
                    .border(1.2.dp, rimColor, RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${seat.seat}号",
                    fontSize = 8.5.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) theme.onAccent else Color(0xFFFFFBF5),
                    maxLines = 1,
                    softWrap = false,
                )
            }

            // 左上角：警徽 / 警上
            if (seat.sheriff || seat.sheriffCandidate) {
                val badgeBg = if (seat.sheriff) SheriffGold else SheriffCandidateAmber
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(50))
                        .background(badgeBg)
                        .border(1.2.dp, rimColor, RoundedCornerShape(50))
                        .padding(horizontal = if (seat.sheriff) 4.dp else 3.5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (seat.sheriff) "警" else "警上",
                        fontSize = 7.5.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        softWrap = false,
                        maxLines = 1,
                    )
                }
            }

            // 右上角：选中对勾优先；其次狼队友标；其次玩家自己的手动贴纸记号
            when {
                isSelected -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(theme.accent)
                            .border(1.3.dp, rimColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✓",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = theme.onAccent,
                        )
                    }
                }
                seat.isWolfMate -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(17.dp)
                            .clip(CircleShape)
                            .background(FateRed)
                            .border(1.2.dp, rimColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "狼",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
                manualMark != null && !seat.isSelf -> {
                    val markBg = if (manualMark.hostile) FateRed else if (night) SeerTealNight else SeerTeal
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(17.dp)
                            .clip(CircleShape)
                            .background(markBg)
                            .border(1.2.dp, rimColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = manualMark.short,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!manualMark.hostile && night) Color(0xFF0A241F) else Color.White,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        Text(
            text = seat.name,
            fontSize = if (compact) 11.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = when {
                isSelected -> FontWeight.Bold
                enabled -> FontWeight.SemiBold
                else -> FontWeight.Normal
            },
            color = when {
                !enabled -> dimColor.copy(alpha = 0.55f)
                isSelected -> theme.accent
                !seat.alive -> dimColor.copy(alpha = 0.65f)
                else -> textColor
            },
        )

        Spacer(Modifier.height(3.dp))

        // 固定高度状态槽：确保整排卡片高度齐平，不晃动不拥挤
        Box(
            modifier = Modifier.height(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isSelected -> SeatMiniTag(
                    text = "再点确认",
                    color = theme.accent,
                    bg = theme.accent.copy(alpha = if (night) 0.24f else 0.14f),
                    bold = true,
                )
                !seat.alive -> SeatMiniTag(
                    text = seat.fate?.short?.let { "已出局·$it" } ?: "已出局",
                    color = FateRed.copy(alpha = 0.85f),
                    bg = FateRed.copy(alpha = if (night) 0.16f else 0.09f),
                )
                seat.seerVerdict != null -> {
                    val vColor = if (seat.seerVerdict == UIWerewolfVerdict.WOLF) {
                        FateRed
                    } else if (night) {
                        SeerTealNight
                    } else {
                        SeerTeal
                    }
                    SeatMiniTag(
                        text = "验·${seat.seerVerdict.label}",
                        color = vColor,
                        bg = vColor.copy(alpha = if (night) 0.20f else 0.12f),
                        bold = true,
                    )
                }
                seat.isSelf -> SeatMiniTag(
                    text = "自己",
                    color = dimColor,
                    bg = if (night) NightControlFill else DayControlFill,
                )
                seat.votes != null && seat.votes > 0 -> SeatMiniTag(
                    text = "${seat.votes} 票",
                    color = VoteRed,
                    bg = VoteRed.copy(alpha = if (night) 0.20f else 0.11f),
                    bold = true,
                )
                !enabled -> Text(
                    text = "不可选",
                    fontSize = 9.sp,
                    color = dimColor.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SeatMiniTag(
    text: String,
    color: Color,
    bg: Color,
    bold: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 5.5.dp, vertical = 1.dp),
        fontSize = 8.5.sp,
        lineHeight = 11.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}
