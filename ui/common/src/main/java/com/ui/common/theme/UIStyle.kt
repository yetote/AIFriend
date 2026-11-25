package com.ui.common.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 全站建筑感圆角体系：利落的方圆比例，取代臃肿的软糖大圆角。 */
val UIRadiusCard = 20.dp
val UIRadiusSheet = 24.dp
val UIRadiusBubble = 18.dp
val UIRadiusBubbleTail = 5.dp
val UIRadiusControl = 12.dp
val UIRadiusTab = 12.dp

/** 宣纸层与结构描边：实感纸面卡片 + 清晰 1dp 暖石细线，抛弃模糊灰白玻璃。 */
val UIGlassBar = Color(0xF7FAF6EE)
val UIGlassSurface = Color(0xFFF1ECE1)
val UIGlassSurfaceStrong = Color(0xFFFFFDF9)
val UIGlassStroke = Color(0x21241C14)
val UIGlassHighlight = Color(0xCCFFFDF9)

/** 输入框与内嵌区域底色：温润下沉的暖砂色调，与象牙卡片自然分层。 */
val UIFieldFill = Color(0x0E241C14)

/** 输入条的基准高度：输入框和发送按钮严格等高对齐。 */
val UIComposerHeight = 42.dp

val UIShadowSoft = Color(0x161F170F)
val UISeparator = Color(0x14241C14)

val UIGradientTop = Color(0xFFF8F5EE)
val UIGradientMid = Color(0xFFF4EFE6)
val UIGradientBottom = Color(0xFFECE5D8)

/** 暖陶色投影与微光（保留原常量名兼容各模块引用）。 */
val UIBlobBlue = Color(0x30C84B2A)
val UIBlobViolet = Color(0x1FD4953B)
private val UIWarmWash = Color(0x18D87B56)
private val UIAmberWash = Color(0x14D4A048)

/** 用户气泡渐变：浓墨炭棕，与暖象牙 AI 卡片形成杂志级高对比。 */
val UIBubbleUserBrush = Brush.linearGradient(
    listOf(Color(0xFF28231E), Color(0xFF191512))
)

/** 主按钮渐变：朱砂暖陶，克制而有温度的主行动锚点。 */
val UIActionBrush = Brush.linearGradient(
    listOf(Color(0xFFD65836), Color(0xFFB83E1E))
)

/** Hero / 暗色锚点卡渐变：深炭黑曜石到暖胡桃墨棕。 */
val UIHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF191613), Color(0xFF251F1A), Color(0xFF2E241D))
)

/** 狼人杀特色入口渐变：烛影曜石到绛红暗木，与古铜金月辉呼应。 */
val UIWerewolfHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF151110), Color(0xFF2A1614), Color(0xFF4A1D18))
)

/**
 * 全局宣纸暖陶画布：干净温润的纸面渐变 + 极克制的双角暖光晕。
 * 不再使用高糊大色块，让卡片边界、排版本身成为主角。
 */
@Composable
fun UIGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(UIGradientTop, UIGradientMid, UIGradientBottom)))
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(UIWarmWash, Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.06f),
                        radius = size.width * 0.72f
                    ),
                    radius = size.width * 0.72f,
                    center = Offset(size.width * 0.92f, size.height * 0.06f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(UIAmberWash, Color.Transparent),
                        center = Offset(size.width * 0.08f, size.height * 0.94f),
                        radius = size.width * 0.65f
                    ),
                    radius = size.width * 0.65f,
                    center = Offset(size.width * 0.08f, size.height * 0.94f)
                )
            }
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}

/**
 * 统一的纸面卡片皮肤：轻投影 + 利落圆角 + 暖象牙实底 + 精细暖石描边。
 */
fun Modifier.uiCard(
    shape: Shape = RoundedCornerShape(UIRadiusCard),
    color: Color = UIGlassSurfaceStrong,
    borderColor: Color = UIGlassStroke,
    elevation: Dp = 3.dp
): Modifier = this
    .shadow(elevation, shape, clip = false, ambientColor = UIShadowSoft, spotColor = UIShadowSoft)
    .clip(shape)
    .background(color, shape)
    .border(1.dp, borderColor, shape)

/** 控件级皮肤：用于输入框、方圆按钮与筛选芯片，触感紧凑利落。 */
fun Modifier.uiControl(
    shape: Shape = RoundedCornerShape(UIRadiusControl),
    color: Color = UIGlassSurfaceStrong,
    borderColor: Color = UIGlassStroke
): Modifier = this
    .shadow(1.5.dp, shape, clip = false, ambientColor = UIShadowSoft, spotColor = UIShadowSoft)
    .clip(shape)
    .background(color, shape)
    .border(1.dp, borderColor, shape)

/** 贴边栏的底部 0.75dp 建筑细线，用 drawBehind 绘制不占布局高度。 */
fun Modifier.uiHairlineBottom(color: Color = UIGlassStroke): Modifier = drawBehind {
    val stroke = 0.75.dp.toPx()
    drawLine(
        color = color,
        start = Offset(0f, size.height - stroke / 2f),
        end = Offset(size.width, size.height - stroke / 2f),
        strokeWidth = stroke
    )
}
