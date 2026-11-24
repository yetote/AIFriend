package com.ui.werewolf

// 选目标弹窗：像摊开一手牌一样选座位，带头像与存活状态。

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.UIAvatar
import com.ui.common.theme.UIDialogButtons
import com.ui.common.theme.UIIosDialog
import com.ui.werewolf.data.UIWerewolfSeatLive
import com.ui.werewolf.data.UIWerewolfVerdict

/**
 * 按牌桌座位摊开的目标选择弹窗。
 *
 * 做成「摊开一手牌」而不是表格：头像挑大、候选高亮、非候选淡出。
 * 验人结论压成头像角上的一个小色点——它只有预言家自己看得见，
 * 不该抢走「选谁」这件事的注意力，但在犹豫时瞄一眼就知道谁验过是狼。
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
    // 选谁先存本地：点一下高亮、再点确认才提交，避免手滑直接把决定交出去。
    // 但狼人杀是快节奏，「点谁就是谁」更顺手——所以这里点一下直接提交，
    // selected 只用于按下瞬间的视觉反馈（按下即高亮然后弹窗关闭）。
    var pressed by rememberSaveable { mutableStateOf<Int?>(null) }

    UIIosDialog(
        title = title,
        subtitle = hint,
        onDismiss = onDismiss,
        maxWidth = 420.dp,
        actions = {
            if (onAbstain != null) {
                UIDialogButtons(
                    confirmText = abstainLabel ?: "不做",
                    confirmColor = Color(0xFF9AA4B2),
                    onConfirm = { onAbstain(); onDismiss() },
                    onCancel = onDismiss,
                )
            } else {
                UIDialogButtons(
                    confirmText = abstainLabel ?: "取消",
                    confirmColor = Color(0xFF9AA4B2),
                    onConfirm = onDismiss,
                    cancelText = null,
                )
            }
        },
    ) {
        val ordered = seats.sortedBy { it.seat }
        // 3 列网格：12 人 4 行，比 2 列 6 行矮一截，一屏能看完不用来回滚。
        val rows = ordered.chunked(3)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
    }
}

/**
 * 弹窗里的一张座位卡：圆形头像 + 左上角座位号 + 名字。
 *
 * 可选 = 淡蓝底 + 蓝边 + 按下加深；不可选 = 全透明 + 头像压暗。
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
    val cardBg by animateColorAsState(
        when {
            !enabled -> Color.Transparent
            isPressed -> Color(0x332B6BFF)
            else -> Color(0x0F2B6BFF)
        },
        label = "seat-card-bg",
    )
    val borderColor by animateColorAsState(
        when {
            !enabled -> Color(0x14000000)
            isPressed -> SelfBlue
            else -> Color(0x332B6BFF)
        },
        label = "seat-card-border",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 头像：可选的有蓝圈 + 微投影，像一枚能拿起的棋子
        Box(
            modifier = Modifier
                .size(46.dp)
                .then(
                    if (enabled) {
                        Modifier.shadow(6.dp, CircleShape, clip = false)
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
                    .alpha(if (seat.alive) 1f else 0.3f)
                    .then(
                        if (enabled) Modifier.border(2.dp, SelfBlue, CircleShape)
                        else Modifier
                    ),
            )
            // 座位号角标：压在头像左上角，一眼对得上牌桌
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (enabled) SelfBlue else Color(0xFFB8C0CC))
                    .alpha(if (seat.alive) 1f else 0.5f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = seat.seat.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
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
                        .border(1.5.dp, Color.White, CircleShape),
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
                !enabled -> dimColor.copy(alpha = 0.55f)
                !seat.alive -> dimColor.copy(alpha = 0.7f)
                else -> textColor
            },
        )
        // 出局挂死因小字；活着的留一行占位，避免网格高低不齐
        Text(
            text = seat.fate?.short ?: "",
            fontSize = FsBadge,
            color = FateRed,
        )
    }
}
