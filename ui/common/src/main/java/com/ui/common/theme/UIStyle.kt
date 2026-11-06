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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** iOS 风格圆角体系。 */
val UIRadiusCard = 20.dp
val UIRadiusSheet = 24.dp
val UIRadiusBubble = 20.dp
val UIRadiusBubbleTail = 6.dp
val UIRadiusControl = 14.dp
val UIRadiusTab = 12.dp

/** 玻璃层：半透明白，叠在渐变背景上有毛玻璃观感。 */
val UIGlassBar = Color(0xE6FFFFFF)
val UIGlassSurface = Color(0xB3FFFFFF)
val UIGlassSurfaceStrong = Color(0xFFFDFDFF)
val UIGlassStroke = Color(0x14000000)
val UIGlassHighlight = Color(0x80FFFFFF)

val UIShadowSoft = Color(0x1A1B2B45)
val UISeparator = Color(0x12000000)

val UIGradientTop = Color(0xFFF8FAFD)
val UIGradientMid = Color(0xFFF2F5FA)
val UIGradientBottom = Color(0xFFEBEFF7)

val UIBlobBlue = Color(0x332B6BFF)
val UIBlobViolet = Color(0x269C6BFF)

/** 用户气泡渐变。 */
val UIBubbleUserBrush = Brush.linearGradient(
    listOf(Color(0xFF5B93FF), Color(0xFF2B6BFF))
)

/** 主按钮渐变。 */
val UIActionBrush = Brush.linearGradient(
    listOf(Color(0xFF5B93FF), Color(0xFF2B6BFF))
)

/** Hero 卡片渐变：深蓝到靛紫，带一点光感。 */
val UIHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF3A72F5), Color(0xFF2A54D8), Color(0xFF3C3BC4))
)

/**
 * 全局渐变底色：浅灰蓝渐变 + 两团虚化色光，模拟 iOS 的柔和层次。
 * 底色铺满整屏（含状态栏区域），传入的 modifier（通常是 insets）只作用于内容。
 * API 31 以下 blur 降级为无模糊，仍然只是低透明度色块，不会突兀。
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
                .size(280.dp)
                .offset(x = (-90).dp, y = (-40).dp)
                .blur(90.dp, BlurredEdgeTreatment.Unbounded)
                .background(UIBlobBlue, CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(300.dp)
                .offset(x = 90.dp, y = 110.dp)
                .blur(95.dp, BlurredEdgeTreatment.Unbounded)
                .background(UIBlobViolet, CircleShape)
        )
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}

/** 统一的卡片皮肤：柔和投影 + 大圆角 + 极细描边。 */
fun Modifier.uiCard(
    shape: Shape = RoundedCornerShape(UIRadiusCard),
    color: Color = UIGlassSurfaceStrong,
    borderColor: Color = UIGlassStroke,
    elevation: Dp = 10.dp
): Modifier = this
    .shadow(elevation, shape, clip = false, ambientColor = UIShadowSoft, spotColor = UIShadowSoft)
    .background(color, shape)
    .border(0.5.dp, borderColor, shape)

/** 控件级皮肤：更轻的投影，用于输入框、胶囊按钮。 */
fun Modifier.uiControl(
    shape: Shape = RoundedCornerShape(UIRadiusControl),
    color: Color = UIGlassSurfaceStrong,
    borderColor: Color = UIGlassStroke
): Modifier = this
    .shadow(4.dp, shape, clip = false, ambientColor = UIShadowSoft, spotColor = UIShadowSoft)
    .background(color, shape)
    .border(0.5.dp, borderColor, shape)
