package com.ui.werewolf

// 选目标弹窗：按牌桌两列布局摊开座位，带头像与存活样式。
// 预言家验人 / 守卫守护 / 女巫毒人 / 投票放逐共用。

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.UIAvatar
import com.ui.common.theme.UIDialogButtons
import com.ui.common.theme.UIIosDialog
import com.ui.werewolf.data.UIWerewolfSeatLive

/**
 * 按牌桌座位分布摊开的目标选择弹窗。
 *
 * 和底部那排横向胶囊比，这里能一眼看到「谁还活着、坐在哪」——验人、守护、毒人
 * 这种要对着牌桌想一圈的决定，光看一串名字很别扭。视觉配方跟牌桌座位一致
 * （头像压暗表示出局、死因字数双编码），所以弹窗和牌桌是同一套语言。
 *
 * @param pickable 这一轮可以选的座位号；不在其中的（自己、已出局、非候选）灰掉不可点。
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
    UIIosDialog(
        title = title,
        subtitle = hint,
        onDismiss = onDismiss,
        actions = {
            if (onAbstain != null) {
                // 左「取消」关掉弹窗，右「不做 / 弃票 / 这晚不守」才是弃权动作
                UIDialogButtons(
                    confirmText = abstainLabel ?: "不做",
                    confirmColor = Color(0xFF8A94A6),
                    onConfirm = { onAbstain(); onDismiss() },
                    onCancel = onDismiss,
                )
            } else {
                // 没有弃权动作的场合（毒目标）只留一个按钮关掉弹窗，
                // 文案用调用方给的（女巫毒人是「算了」），没给就用「取消」
                UIDialogButtons(
                    confirmText = abstainLabel ?: "取消",
                    confirmColor = Color(0xFF8A94A6),
                    onConfirm = onDismiss,
                    cancelText = null,
                )
            }
        },
    ) {
        val ordered = seats.sortedBy { it.seat }
        val half = (ordered.size + 1) / 2
        // 和牌桌一致：左右两列，每列自上而下排座位。不是「两行横排」——
        // 12 人横排每行 6 个会挤到 43dp，头像 36dp 直接溢出、名字全截断。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(ordered.take(half), ordered.drop(half)).forEach { column ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    column.forEach { seat ->
                        TargetSeat(
                            seat = seat,
                            enabled = seat.seat in pickable,
                            textColor = textColor,
                            dimColor = dimColor,
                            night = night,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onPick(seat.seat); onDismiss() },
                        )
                    }
                }
            }
        }
    }
}

/** 弹窗里的一枚座位：头像 + 名字 + 死因，复用牌桌的存活视觉。 */
@Composable
private fun TargetSeat(
    seat: UIWerewolfSeatLive,
    enabled: Boolean,
    textColor: Color,
    dimColor: Color,
    night: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ring = when {
        !enabled -> Color.Transparent
        seat.isSelf -> SelfBlue
        else -> Color(0xFF6C9BFF)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .background(if (enabled) Color(0x142B6BFF) else Color.Transparent)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            UIAvatar(
                name = seat.name,
                avatarPath = seat.avatarPath,
                size = 36.dp,
                shape = CircleShape,
                modifier = Modifier
                    .alpha(if (seat.alive) 1f else 0.34f)
                    .then(
                        if (ring != Color.Transparent) {
                            Modifier.border(2.dp, ring, CircleShape)
                        } else Modifier
                    )
            )
        }
        Text(
            text = "${seat.seat} ${seat.name}",
            fontSize = FsName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = when {
                !enabled -> dimColor.copy(alpha = 0.5f)
                !seat.alive -> dimColor.copy(alpha = 0.6f)
                else -> textColor
            },
        )
        // 出局挂死因字（参与局映射层已把刀/毒降级成中性「夜」），复用牌桌的双编码
        seat.fate?.let { fate ->
            Text(
                text = fate.short,
                fontSize = FsBadge,
                color = FateRed,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(FateRed.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        // 验人结论长在被验座位下——和牌桌一致，预言家对着弹窗就能想起谁的验人结果
        seat.seerVerdict?.let { verdict ->
            Text(
                text = verdict.label,
                fontSize = FsBadge,
                color = if (night) SeerTealNight else SeerTeal,
            )
        }
    }
}
