package com.ui.werewolf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.theme.UIShadowSoft
import com.ui.werewolf.data.UIWerewolfBubbleStyle

/** 公开发言聊天气泡的非对称「说话口」圆角形态。 */
internal val SpeechBubbleShape = RoundedCornerShape(
    topStart = 5.dp,
    topEnd = 16.dp,
    bottomEnd = 16.dp,
    bottomStart = 16.dp,
)

/** 内心与投票气泡的紧凑圆角形态。 */
internal val InnerBubbleShape = RoundedCornerShape(
    topStart = 4.dp,
    topEnd = 12.dp,
    bottomEnd = 12.dp,
    bottomStart = 12.dp,
)

/**
 * 对话气泡。
 *
 * 复盘页和牌桌共用：同一个「密谈」在两处必须是同一种紫，
 * 不然用户在牌桌上建立的印象，到复盘就断了。
 *
 * 区分手段叠了好几层，色觉障碍的用户也能分辨：
 * 渐变底色 + 描边 + 顶部高光线 + 左侧渐变竖线 + 角标文字。
 */
@Composable
fun UIWerewolfBubble(
    style: UIWerewolfBubbleStyle,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    accentBar: Boolean = true,
    dashed: Boolean = false,
    shape: Shape = RoundedCornerShape(corner),
    elevation: Dp = if (dashed) 0.dp else 2.5.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .padding(vertical = 2.dp)
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = shape,
                        clip = false,
                        ambientColor = UIShadowSoft,
                        spotColor = UIShadowSoft,
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(style.fill, style.fillBottom)))
            .then(
                if (dashed) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = style.border,
                            style = Stroke(
                                width = 0.9.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(4.dp.toPx(), 4.dp.toPx())
                                )
                            ),
                            cornerRadius = CornerRadius(corner.toPx()),
                        )
                    }
                } else {
                    Modifier
                        .border(0.85.dp, style.border, shape)
                        .drawBehind {
                            if (style.highlight != Color.Transparent) {
                                val insetX = 12.dp.toPx().coerceAtMost(size.width * 0.25f)
                                val y = 0.85.dp.toPx()
                                drawLine(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, style.highlight, Color.Transparent),
                                        startX = insetX,
                                        endX = (size.width - insetX).coerceAtLeast(insetX + 1f),
                                    ),
                                    start = Offset(insetX, y),
                                    end = Offset((size.width - insetX).coerceAtLeast(insetX + 1f), y),
                                    strokeWidth = 0.9.dp.toPx(),
                                )
                            }
                        }
                }
            )
            .height(IntrinsicSize.Min)
    ) {
        if (accentBar) {
            Box(
                modifier = Modifier
                    .padding(start = 5.dp, top = 10.dp, bottom = 10.dp)
                    .width(2.5.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                style.accent.copy(alpha = 0.92f),
                                style.accent.copy(alpha = 0.42f),
                            )
                        )
                    )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (accentBar) 9.dp else 12.dp,
                    end = 12.dp,
                    top = 9.dp,
                    bottom = 9.dp,
                )
        ) {
            content()
        }
    }
}

/** 气泡角标：类别名，用强调色描一个细边小胶囊。 */
@Composable
fun BubbleTag(text: String, accent: Color) {
    Text(
        text = text,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp,
        color = accent,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.13f))
            .border(0.55.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(50))
            .padding(horizontal = 6.5.dp, vertical = 1.5.dp)
    )
}

/** 座位号徽标：紧贴昵称之前，方便长发言流里一眼定位几号在说话。 */
@Composable
internal fun SeatNumberBadge(
    seat: Int,
    accent: Color,
    night: Boolean,
) {
    Text(
        text = "${seat}号",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = accent,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = if (night) 0.16f else 0.11f))
            .border(0.55.dp, accent.copy(alpha = if (night) 0.32f else 0.24f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

/** 私密可见度小胶囊（如「仅你可见」「上帝视角」）。 */
@Composable
internal fun VisibilityPill(
    text: String,
    accent: Color,
    night: Boolean,
) {
    Text(
        text = text,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Medium,
        color = accent.copy(alpha = 0.9f),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = if (night) 0.14f else 0.09f))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}
