package com.ui.werewolf

// 选目标弹窗：像摊开一手牌一样选座位，带头像与存活状态，完整适配昼夜视觉。

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
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UIRadiusSheet
import com.ui.common.theme.UIShadowSoft
import com.ui.werewolf.data.UIWerewolfSeatLive
import com.ui.werewolf.data.UIWerewolfVerdict

/**
 * 按牌桌座位摊开的目标选择弹窗。
 *
 * 做成「摊开一手牌」而不是表格：头像挑大、候选高亮、非候选淡出。
 * 验人结论压成头像角上的一个小色点——它只有预言家自己看得见，
 * 不该抢走「选谁」这件事的注意力，但在犹豫时瞄一眼就知道谁验过是狼。
 *
 * 弹窗底色与牌桌昼夜联动（夜晚走深蓝月夜玻璃卡，白天走纯白玻璃卡），
 * 避免夜里白底配浅字或刺眼跳白。
 */
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
    val dialogBg = if (night) Color(0xF51A1513) else UIGlassSurfaceStrong
    val dialogBorder = if (night) Color(0x30F5E8D8) else UIGlassStroke
    val dialogShape = RoundedCornerShape(UIRadiusSheet)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        )
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 24.dp,
                    shape = dialogShape,
                    clip = false,
                    ambientColor = if (night) Color(0x88000000) else UIShadowSoft,
                    spotColor = if (night) Color(0x88000000) else UIShadowSoft,
                )
                .clip(dialogShape)
                .background(dialogBg)
                .border(0.9.dp, dialogBorder, dialogShape)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                if (hint != null) {
                    Text(
                        text = hint,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = dimColor,
                    )
                }
            }

            val ordered = seats.sortedBy { it.seat }
            // 3 列网格：12 人 4 行，比 2 列 6 行矮一截，一屏能看完不用来回滚。
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
                        // 尾行补空位，保持网格对齐
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
    val ctrlFill = if (night) NightControlFill else DayControlFill
    val ctrlBorder = if (night) NightControlBorder else DayControlBorder
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onAbstain != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(shape)
                    .background(ctrlFill)
                    .border(0.8.dp, ctrlBorder, shape)
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
                    .height(42.dp)
                    .clip(shape)
                    .background(if (night) Color(0x339AA8C4) else Color(0xFF7D8A9E))
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
                    color = Color.White,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(shape)
                    .background(ctrlFill)
                    .border(0.8.dp, ctrlBorder, shape)
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

/**
 * 弹窗里的一张座位卡：圆形头像 + 左上角座位号 + 名字。
 *
 * 可选 = 淡蓝底 + 蓝边 + 按下加深；不可选 = 极淡底 + 头像压暗。
 * 出局用死因小字，验人结论用头像角上的小圆点——都只占一行，不把卡片撑高。
 */
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
            !enabled -> if (night) Color(0x0AFFFFFF) else Color(0x061B2B45)
            isPressed -> accent.copy(alpha = if (night) 0.34f else 0.22f)
            else -> accent.copy(alpha = if (night) 0.16f else 0.08f)
        },
        label = "seat-card-bg",
    )
    val borderColor by animateColorAsState(
        when {
            !enabled -> if (night) Color(0x14FFFFFF) else Color(0x101B2B45)
            isPressed -> accent
            else -> accent.copy(alpha = if (night) 0.48f else 0.30f)
        },
        label = "seat-card-border",
    )
    val rimColor = if (night) Color(0xFF1A1513) else Color(0xFFFFFDF9)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 11.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // 头像：可选的有蓝圈 + 微投影，像一枚能拿起的棋子
        Box(
            modifier = Modifier
                .size(46.dp)
                .then(
                    if (enabled) {
                        Modifier.shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = accent.copy(alpha = 0.4f),
                            spotColor = accent.copy(alpha = 0.4f),
                        )
                    } else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            UIAvatar(
                name = seat.name,
                avatarPath = seat.avatarPath,
                size = 44.dp,
                shape = CircleShape,
                modifier = Modifier
                    .alpha(if (seat.alive) 1f else 0.32f)
                    .then(
                        if (enabled) Modifier.border(2.dp, accent, CircleShape)
                        else Modifier
                    ),
            )
            // 座位号角标：压在头像左上角，一眼对得上牌桌
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(if (enabled) SelfBlue else Color(0xFF8A95A6))
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
            // 狼队友角标：本人是狼时在右上角标一下，选人时不容易误伤
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
            // 验人结论：头像右下角一个小圆点，只给看得见结论的人（映射层保证）
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
        // 出局挂死因小字；活着的留一行占位，避免网格高低不齐
        Text(
            text = seat.fate?.short ?: "",
            fontSize = FsBadge,
            fontWeight = FontWeight.Medium,
            color = FateRed,
        )
    }
}
