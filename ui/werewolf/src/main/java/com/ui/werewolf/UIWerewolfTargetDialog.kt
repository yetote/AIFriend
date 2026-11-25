package com.ui.werewolf

// 选目标弹窗：与全站宣纸弹窗共用同一套标题头、细分割线与 12dp 方圆按钮语言，并完整适配昼夜视觉。

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ui.common.UIAvatar
import com.ui.common.theme.AtelierDialogHeader
import com.ui.common.theme.UIAtelierInk
import com.ui.common.theme.UIAtelierSand
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UIRadiusSheet
import com.ui.common.theme.UISeparator
import com.ui.common.theme.UIShadowSoft
import com.ui.werewolf.data.UIWerewolfSeatLive
import com.ui.werewolf.data.UIWerewolfVerdict

@Composable
internal fun TargetPickDialog(
    title: String,
    hint: String?,
    seats: List<UIWerewolfSeatLive>,
    pickable: Set<Int>,
    textColor: Color,
    dimColor: Color,
    night: Boolean,
    onPick: (Int) -> Unit,
    abstainLabel: String?,
    onAbstain: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var pressed by rememberSaveable { mutableStateOf<Int?>(null) }
    val dialogBg = if (night) Color(0xF71A1513) else UIGlassSurfaceStrong
    val dialogBorder = if (night) Color(0x30F5E8D8) else UIGlassStroke
    val separator = if (night) Color(0x22F5E8D8) else UISeparator
    val accent = if (night) Color(0xFFE5B869) else Color(0xFFC84B2A)
    val dialogShape = RoundedCornerShape(UIRadiusSheet)

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
                .padding(horizontal = 18.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 18.dp,
                    shape = dialogShape,
                    clip = false,
                    ambientColor = if (night) Color(0x88000000) else UIShadowSoft,
                    spotColor = if (night) Color(0x88000000) else UIShadowSoft,
                )
                .clip(dialogShape)
                .background(dialogBg)
                .border(1.dp, dialogBorder, dialogShape)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AtelierDialogHeader(
                title = title,
                subtitle = hint,
                accentColor = accent,
                titleColor = textColor,
                subtitleColor = dimColor,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.75.dp)
                    .background(separator)
            )

            val ordered = seats.sortedBy { it.seat }
            val rows = ordered.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        row.forEach { seat ->
                            TargetSeatCard(
                                seat = seat,
                                enabled = seat.seat in pickable,
                                isPressed = pressed == seat.seat,
                                textColor = textColor,
                                dimColor = dimColor,
                                night = night,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    pressed = seat.seat
                                    onPick(seat.seat)
                                    onDismiss()
                                },
                            )
                        }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }

            TargetDialogActions(
                night = night,
                textColor = textColor,
                dimColor = dimColor,
                abstainLabel = abstainLabel,
                onAbstain = onAbstain,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun TargetDialogActions(
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    abstainLabel: String?,
    onAbstain: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(UIRadiusControl)
    val ctrlFill = if (night) NightControlFill else UIAtelierSand
    val ctrlBorder = if (night) NightControlBorder else UIGlassStroke
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onAbstain != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(shape)
                    .background(ctrlFill)
                    .border(0.9.dp, ctrlBorder, shape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "取消",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = dimColor,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(shape)
                    .background(if (night) Color(0x38E5B869) else UIAtelierInk)
                    .border(
                        0.9.dp,
                        if (night) Color(0x55E5B869) else UIAtelierInk,
                        shape
                    )
                    .clickable {
                        onAbstain()
                        onDismiss()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = abstainLabel ?: "不做",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (night) Color(0xFFF7E4BC) else Color(0xFFFFFDF9),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(shape)
                    .background(ctrlFill)
                    .border(0.9.dp, ctrlBorder, shape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = abstainLabel ?: "取消",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun TargetSeatCard(
    seat: UIWerewolfSeatLive,
    enabled: Boolean,
    isPressed: Boolean,
    textColor: Color,
    dimColor: Color,
    night: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = if (night) SelfBlueNight else SelfBlue
    val cardBg by animateColorAsState(
        when {
            !enabled -> if (night) Color(0x0CF5E8D8) else UIAtelierSand.copy(alpha = 0.55f)
            isPressed -> accent.copy(alpha = if (night) 0.34f else 0.22f)
            else -> accent.copy(alpha = if (night) 0.16f else 0.08f)
        },
        label = "seat-card-bg",
    )
    val borderColor by animateColorAsState(
        when {
            !enabled -> if (night) Color(0x18F5E8D8) else UIGlassStroke
            isPressed -> accent
            else -> accent.copy(alpha = if (night) 0.50f else 0.34f)
        },
        label = "seat-card-border",
    )
    val rimColor = if (night) Color(0xFF1A1513) else Color(0xFFFFFDF9)
    val cardShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(cardBg)
            .border(1.dp, borderColor, cardShape)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center,
        ) {
            val avatarShape = RoundedCornerShape(14.dp)
            UIAvatar(
                name = seat.name,
                avatarPath = seat.avatarPath,
                size = 42.dp,
                shape = avatarShape,
                modifier = Modifier
                    .alpha(if (seat.alive) 1f else 0.32f)
                    .then(
                        if (enabled) Modifier.border(1.8.dp, accent, avatarShape)
                        else Modifier.border(0.8.dp, borderColor, avatarShape)
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(if (enabled) SelfBlue else Color(0xFF8C8277))
                    .border(1.2.dp, rimColor, CircleShape)
                    .alpha(if (seat.alive) 1f else 0.55f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = seat.seat.toString(),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            if (seat.isWolfMate) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
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
            if (seat.sheriff || seat.sheriffCandidate) {
                val badgeBg = if (seat.sheriff) SheriffGold else SheriffCandidateAmber
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
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
            seat.seerVerdict?.let { verdict ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when (verdict) {
                                UIWerewolfVerdict.WOLF -> FateRed
                                UIWerewolfVerdict.GOOD -> if (night) SeerTealNight else SeerTeal
                            }
                        )
                        .border(1.5.dp, rimColor, CircleShape),
                )
            }
        }

        Text(
            text = seat.name,
            fontSize = FsName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                !enabled -> dimColor.copy(alpha = 0.52f)
                !seat.alive -> dimColor.copy(alpha = 0.68f)
                else -> textColor
            },
        )
        Text(
            text = seat.fate?.short ?: "",
            fontSize = FsBadge,
            fontWeight = FontWeight.Medium,
            color = FateRed,
        )
    }
}
