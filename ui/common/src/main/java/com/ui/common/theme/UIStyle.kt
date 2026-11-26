package com.ui.common.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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

/** 宣纸层与结构描边：实感纸面/曜石卡片 + 清晰 1dp 细线。 */
val UIGlassBar: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0xF5171310) else Color(0xF7FAF6EE)
val UIGlassSurface: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0xFF251F1A) else Color(0xFFF1ECE1)
val UIGlassSurfaceStrong: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0xFF1D1815) else Color(0xFFFFFDF9)
val UIGlassStroke: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0x2BF5E8D8) else Color(0x21241C14)
val UIGlassHighlight: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0x24F5E8D8) else Color(0xCCFFFDF9)

/** 输入框与内嵌区域底色：浅色为暖砂，深色为炭棕内胆。 */
val UIFieldFill: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0x16F5E8D8) else Color(0x0E241C14)

/** 输入条的基准高度：输入框和发送按钮严格等高对齐。 */
val UIComposerHeight = 42.dp

val UIShadowSoft: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0x52000000) else Color(0x161F170F)
val UISeparator: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0x1CF5E8D8) else Color(0x14241C14)

val UIGradientTop: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0xFF151210) else Color(0xFFF8F5EE)
val UIGradientMid: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0xFF110E0C) else Color(0xFFF4EFE6)
val UIGradientBottom: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0xFF0C0A08) else Color(0xFFECE5D8)

/** 暖陶色投影与微光（保留原常量名兼容各模块引用）。 */
val UIBlobBlue: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0x24E06B47) else Color(0x30C84B2A)
val UIBlobViolet: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0x1CE5B869) else Color(0x1FD4953B)
private val UIWarmWash = Color(0x18D87B56)
private val UIAmberWash = Color(0x14D4A048)

/** 用户气泡渐变：白昼浓墨炭棕，夜间温润朱砂赤陶，与助手卡清晰区分。 */
val UIBubbleUserBrush: Brush
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) {
        Brush.linearGradient(listOf(Color(0xFFB44324), Color(0xFF8C2F16)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF28231E), Color(0xFF191512)))
    }

/** 主按钮渐变：朱砂暖陶，克制而有温度的主行动锚点。 */
val UIActionBrush = Brush.linearGradient(
    listOf(Color(0xFFD65836), Color(0xFFB83E1E))
)

/** Hero / 暗色锚点卡渐变：深炭黑曜石到暖胡桃墨棕。 */
val UIHeroBrush: Brush
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) {
        Brush.linearGradient(listOf(Color(0xFF211C18), Color(0xFF2C241E), Color(0xFF382C23)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF191613), Color(0xFF251F1A), Color(0xFF2E241D)))
    }

/** 狼人杀特色入口渐变：烛影曜石到绛红暗木，与古铜金月辉呼应。 */
val UIWerewolfHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF151110), Color(0xFF2A1614), Color(0xFF4A1D18))
)

/**
 * 全局宣纸暖陶 / 曜石暗夜画布。
 */
@Composable
fun UIGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val top = UIGradientTop
    val mid = UIGradientMid
    val bottom = UIGradientBottom
    val warmWash = if (UIIsDarkTheme) Color(0x18D87B56) else UIWarmWash
    val amberWash = if (UIIsDarkTheme) Color(0x14E5B869) else UIAmberWash
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, mid, bottom)))
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(warmWash, Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.06f),
                        radius = size.width * 0.72f
                    ),
                    radius = size.width * 0.72f,
                    center = Offset(size.width * 0.92f, size.height * 0.06f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(amberWash, Color.Transparent),
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
 * 统一的纸面/曜石卡片皮肤：轻投影 + 利落圆角 + 实底 + 精细描边。
 */
@Composable
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
@Composable
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
@Composable
fun Modifier.uiHairlineBottom(color: Color = UIGlassStroke): Modifier = drawBehind {
    val stroke = 0.75.dp.toPx()
    drawLine(
        color = color,
        start = Offset(0f, size.height - stroke / 2f),
        end = Offset(size.width, size.height - stroke / 2f),
        strokeWidth = stroke
    )
}
