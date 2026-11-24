package com.ui.common.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 全站圆角体系：大气柔和的瓷白玻璃比例。 */
val UIRadiusCard = 22.dp
val UIRadiusSheet = 26.dp
val UIRadiusBubble = 20.dp
val UIRadiusBubbleTail = 6.dp
val UIRadiusControl = 14.dp
val UIRadiusTab = 14.dp

/** 玻璃层：通透瓷白，叠在极光渐变画布上具备细腻的空气感与边界。 */
val UIGlassBar = Color(0xEEFFFFFF)
val UIGlassSurface = Color(0xC8FFFFFF)
val UIGlassSurfaceStrong = Color(0xF8FFFFFF)
val UIGlassStroke = Color(0x160F172A)
val UIGlassHighlight = Color(0xB3FFFFFF)

/** 输入框内胆：轻微下沉的冷雾底色，清晰区分可输入区域与卡片本体。 */
val UIFieldFill = Color(0x0A0F172A)

/**
 * 输入条的基准高度：输入框和发送按钮共用。
 *
 * 两边都按内容自然撑开时，输入框会被字号和 padding 撑到 42dp 左右，
 * 而按钮是写死的另一个值，差一两 dp 就会看起来「差一点对不齐」。
 * 定一个常量让两边引用，才不会各算各的。
 *
 * 40dp 是单行输入条比较收的高度：再大整条输入区就显笨重，
 * 尤其外面还有卡片内边距和页面留白各一层。
 */
val UIComposerHeight = 40.dp

val UIShadowSoft = Color(0x160F172A)
val UISeparator = Color(0x140F172A)

val UIGradientTop = Color(0xFFF8F9FD)
val UIGradientMid = Color(0xFFF1F4FA)
val UIGradientBottom = Color(0xFFE9EEF8)

val UIBlobBlue = Color(0x284F46E5)
val UIBlobViolet = Color(0x207C3AED)
private val UIBlobSky = Color(0x1C0EA5E9)

/** 用户气泡渐变：暮靛星辉。 */
val UIBubbleUserBrush = Brush.linearGradient(
    listOf(Color(0xFF5B5CE6), Color(0xFF4338CA))
)

/** 主按钮渐变：暮靛星辉。 */
val UIActionBrush = Brush.linearGradient(
    listOf(Color(0xFF5B5CE6), Color(0xFF4338CA))
)

/** Hero 卡片渐变：深邃黑曜石到暮夜靛蓝，自带丝绒光感。 */
val UIHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF0F172A), Color(0xFF1E294E), Color(0xFF312E81))
)

/** 狼人杀特色入口渐变：暗夜绛紫到猩红月影，与日常助手卡拉开氛围。 */
val UIWerewolfHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF151226), Color(0xFF2B1633), Color(0xFF4E1930))
)

/**
 * 全局极光画布：清透瓷白渐变 + 三团柔和弥散光晕。
 * 底色铺满整屏（含状态栏区域），传入的 modifier（通常是 insets）只作用于内容。
 * API 31 以下 blur 降级为无模糊，低透明度光晕依然柔和自然。
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
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-100).dp, y = (-50).dp)
                .blur(100.dp, BlurredEdgeTreatment.Unbounded)
                .background(UIBlobBlue, CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(220.dp)
                .offset(x = 80.dp, y = 40.dp)
                .blur(95.dp, BlurredEdgeTreatment.Unbounded)
                .background(UIBlobSky, CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(320.dp)
                .offset(x = 95.dp, y = 115.dp)
                .blur(105.dp, BlurredEdgeTreatment.Unbounded)
                .background(UIBlobViolet, CircleShape)
        )
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}

/**
 * 统一的卡片皮肤：外柔和投影 + 圆角裁切 + 瓷白底 + 精细描边。
 *
 * `.clip(shape)` 紧跟在 `shadow(..., clip = false)` 之后，既保住外发散投影，
 * 又让后续 `.clickable` 水波纹与内部色块严丝合缝地裁切在圆角之内。
 */
fun Modifier.uiCard(
    shape: Shape = RoundedCornerShape(UIRadiusCard),
    color: Color = UIGlassSurfaceStrong,
    borderColor: Color = UIGlassStroke,
    elevation: Dp = 10.dp
): Modifier = this
    .shadow(elevation, shape, clip = false, ambientColor = UIShadowSoft, spotColor = UIShadowSoft)
    .clip(shape)
    .background(color, shape)
    .border(0.75.dp, borderColor, shape)

/** 控件级皮肤：更轻的投影，用于输入框、胶囊按钮与可点芯片。 */
fun Modifier.uiControl(
    shape: Shape = RoundedCornerShape(UIRadiusControl),
    color: Color = UIGlassSurfaceStrong,
    borderColor: Color = UIGlassStroke
): Modifier = this
    .shadow(4.dp, shape, clip = false, ambientColor = UIShadowSoft, spotColor = UIShadowSoft)
    .clip(shape)
    .background(color, shape)
    .border(0.75.dp, borderColor, shape)

/**
 * 贴边栏的底部分割线。
 *
 * 通栏顶栏如果只是一块白底直接切到内容，边缘看起来像「被裁掉」而不是「到此为止」；
 * 补一条 0.5dp 极细线，边界就变成有意为之。用 drawBehind 画，不占布局高度。
 */
fun Modifier.uiHairlineBottom(color: Color = UIGlassStroke): Modifier = drawBehind {
    val stroke = 0.5.dp.toPx()
    drawLine(
        color = color,
        start = Offset(0f, size.height - stroke / 2f),
        end = Offset(size.width, size.height - stroke / 2f),
        strokeWidth = stroke
    )
}
