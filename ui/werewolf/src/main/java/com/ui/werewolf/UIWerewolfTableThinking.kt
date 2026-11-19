package com.ui.werewolf

// 思考浮动框：挂在头像附近的漫画式对白框（固定尺寸 + 斜向尖角）。

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

// ---------------------------------------------------------------------------
// 字号
//
// 这页是要连着盯十几分钟的：一屏里同时有人物、发言、倒计时和操作按钮，
// 以前为了塞下 12 人的座位表，正文压到 12sp、名字压到 9sp——字是塞下了，
// 但一屏字全是一样细的小灰块，读两轮眼睛就累。
//
// 所以这里整体往上抬一档：正文 14sp、名字 11sp、身份徽标 9.5sp，
// 座位列从 62dp 放宽到 72dp 给头像留位置，中间那列宁可一行少两个字。
// 唯一的硬约束还是 12 人局一列 6 个人（高度），所以头像只到 34dp。
// ---------------------------------------------------------------------------

/** 发言正文。整页最该被读的东西，字号也最大。 */

/**
 * 思考气泡最高能顶到哪儿（距屏幕顶）。
 *
 * 再高就压到顶栏了——顶栏上有阶段进度和朗读开关，被盖住就点不到了。
 */
internal val MIN_BUBBLE_TOP = 104.dp

/** 思考气泡的固定尺寸。高度必须固定：高度乱跳最伤眼睛。 */
internal val BUBBLE_WIDTH = 196.dp

internal val BUBBLE_HEIGHT = 104.dp

// ---------------------------------------------------------------------------
// 配色
//
// 狼人杀的「夜」是这个游戏最重要的一层信息：夜里谁能行动、谁在闭眼，
// 决定了玩家能不能读懂第二天谁在说谎。所以到夜里整页换暗色，但不换页——
// 换页会把牌桌的连续感切碎，也让人以为进了另一个地方。
//
// 暗色只压在牌桌和记录上，顶栏和输入区跟着一起变，避免出现「上半截夜里、下半截白天」。
// ---------------------------------------------------------------------------

/**
 * 思考气泡：漫画对白框。
 *
 * 挂在说话人附近，但**不贴着他**：贴着边缘看着局促，飘在中间又不像是谁在说话。
 * 所以按屏幕分成两侧——左边的人，气泡落在**中间偏左**；右边的人，落在**中间偏右**，
 * 尖角斜着指过去。漫画里的对白框就是这么画的。
 *
 * **高度固定**。之前高度跟着内容变，一行到五行来回跳，看着眼晕；
 * 现在永远那么高，内容从上往下填，位置纹丝不动。
 *
 * 只给**最后几行**：思考是流式追加的，头部是最早的结论，
 * 尾部才是「他此刻在想什么」。
 */
@Composable
internal fun ThinkingBubble(
    reasoning: String,
    anchor: Offset,
    night: Boolean,
) {
    if (reasoning.isBlank()) return
    // 坐标还没算出来（座位刚布局完）或者算坏了，就先别画：
    // 一个飘在半空的框比没有框更糟
    if (!anchor.x.isFinite() || !anchor.y.isFinite()) return

    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // 只留「像人话」的短行：有些厂商会把 system prompt 里的指令原样回显到思考里
    // （"Must give judgment: who I suspect…" 这种），一整行几百个字符，
    // 塞进气泡里既难看又挤地方。超过 60 个字的行基本都不是人话。
    val lines = remember(reasoning) {
        reasoning.trim()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 60 }
            .takeLast(4)
    }

    val fill = if (night) Color(0xF0221D2E) else Color(0xF5FFFFFF)
    val border = if (night) Color(0x59A894FF) else Color(0x336B4BD9)
    val textColor = if (night) Color(0xFFE6EAF3) else Color(0xFF3A4152)

    Box(modifier = Modifier.fillMaxSize()) {
        // 气泡的位置在下面算，这里先画尖角：它贴着气泡靠头像的那一端
        val bubbleWidthPx = with(density) { BUBBLE_WIDTH.toPx() }
        val bubbleHeightPx = with(density) { BUBBLE_HEIGHT.toPx() }
        val screenWidthPx = with(density) { screenWidth.toPx() }
        val onLeft = anchor.x < screenWidthPx / 2f

        // 左侧：气泡左边缘在屏宽 9% 处；右侧：右边缘在屏宽 91% 处
        val bubbleLeft = if (onLeft) {
            screenWidthPx * 0.09f
        } else {
            screenWidthPx * 0.91f - bubbleWidthPx
        }.coerceIn(8f, (screenWidthPx - bubbleWidthPx - 8f).coerceAtLeast(8f))

        val gap = with(density) { 24.dp.toPx() }
        val minTop = with(density) { MIN_BUBBLE_TOP.toPx() }
        val bubbleTop = (anchor.y - bubbleHeightPx - gap).coerceAtLeast(minTop)

        // 尖角：从气泡靠头像的那一端出发，**斜着**指过去。
        // 气泡飘到中间偏左/偏右之后，尖角要是还竖直向下就指空了——
        // 漫画里的对白框尖角永远指着说话人，角度是算出来的。
        val tailHalf = with(density) { 6.dp.toPx() }
        val tailBaseX = if (onLeft) {
            bubbleLeft + with(density) { 14.dp.toPx() }
        } else {
            bubbleLeft + bubbleWidthPx - with(density) { 26.dp.toPx() }
        }
        val tailBaseY = bubbleTop + bubbleHeightPx - with(density) { 2.dp.toPx() }
        // 三角形默认尖端朝下（+y），转到「指向头像」的角度要再减 90°
        val tailAngle = Math.toDegrees(
            kotlin.math.atan2(
                (anchor.y - tailBaseY).toDouble(),
                (anchor.x - tailBaseX).toDouble(),
            )
        ).toFloat() - 90f

        Canvas(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (tailBaseX - tailHalf).toInt(),
                        (tailBaseY - with(density) { 2.dp.toPx() }).toInt()
                    )
                }
                // 旋转中心放在三角形的底边中点，不然它会绕自己的中心转，尖角指不到人
                .graphicsLayer {
                    // 旋转中心放在三角形的底边中点，不然它绕自己的中心转，尖角指不到人
                    rotationZ = tailAngle
                    transformOrigin = TransformOrigin(tailHalf / size.width, 0f)
                }
                .size(12.dp, 8.dp)
        ) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path, color = fill)
            drawPath(
                path,
                color = border,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(bubbleLeft.toInt(), bubbleTop.toInt()) }
                .size(BUBBLE_WIDTH, BUBBLE_HEIGHT)
                .clip(RoundedCornerShape(12.dp))
                .background(fill)
                .border(1.dp, border, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Column {
                Text(
                    text = "思考中",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (night) Color(0xFFA894FF) else Color(0xFF6B4BD9)
                )
                Text(
                    text = lines.joinToString("\n"),
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = textColor.copy(alpha = 0.85f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 记录流
// ---------------------------------------------------------------------------
