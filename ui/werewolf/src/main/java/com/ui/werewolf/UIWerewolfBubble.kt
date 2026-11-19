package com.ui.werewolf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.werewolf.data.UIWerewolfBubbleStyle

/**
 * 对话气泡。
 *
 * 复盘页和牌桌共用：同一个「密谈」在两处必须是同一种紫，
 * 不然用户在牌桌上建立的印象，到复盘就断了。
 *
 * 区分手段叠了好几层，色觉障碍的用户也能分辨：
 * 底色 + 描边 + 左侧竖线 + 角标文字。
 */
@Composable
fun UIWerewolfBubble(
    style: UIWerewolfBubbleStyle,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    accentBar: Boolean = true,
    dashed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .padding(vertical = 3.dp)
    ) {
        if (accentBar) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(style.accent)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(style.fill)
                .then(
                    if (dashed) {
                        Modifier.drawBehind {
                            drawRoundRect(
                                color = style.border,
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(5.dp.toPx(), 5.dp.toPx())
                                    )
                                ),
                                cornerRadius = CornerRadius(corner.toPx()),
                            )
                        }
                    } else {
                        Modifier.border(1.dp, style.border, shape)
                    }
                )
                .padding(horizontal = 10.dp, vertical = 7.dp)
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
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        color = accent,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}
