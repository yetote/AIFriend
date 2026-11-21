package com.ui.werewolf

// 昼夜背景绘制：天空、太阳月亮、山脊、村庄。纯 Canvas，不参与布局。

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp

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

// 背景。
//
// 白天的天色是浅蓝到暖沙，夜里的天色是墨蓝，而**山脊的形状两个时段完全一样**：
// 玩家白天记住的地形，到夜里还是那个轮廓，「天色变了、地方没变」。
// 两张互不相干的背景虽然更省事，但会让人觉得每回合换了一个场地。
internal val DaySkyTop = Color(0xFFE6EFFC)

internal val DaySkyMid = Color(0xFFF4F8FE)

internal val DaySkyLow = Color(0xFFFBF6EC)

internal val NightSkyTop = Color(0xFF060A12)

internal val NightSkyLow = Color(0xFF121D31)

internal val DaySun = Color(0xFFFFCE7A)

internal val DayRidgeFar = Color(0xFF7E9484)

internal val DayRidgeNear = Color(0xFF5E7466)

internal val NightRidgeFar = Color(0xFF0A1018)

internal val NightRidgeNear = Color(0xFF05080E)

internal val MoonLight = Color(0xFFCBD6F5)

internal val WindowGlow = Color(0xFFFFC069)

internal val SunCore = Color(0xFFFFC94D)

internal val SunGlow = Color(0xFFFFB020)

internal val MoonCore = Color(0xFFF3EAD3)

internal val MoonGlow = Color(0xFFCBD6F5)

/**
 * 星星的位置：横向比例、纵向比例、半径（dp）。
 *
 * 写死不随机：随机会让每次入夜的星空都不一样，虽然不是错误，
 * 但同一局里反复出现的「一张熟悉的星空」更像一个地方。
 */
internal val STARS = listOf(
    Triple(0.10f, 0.12f, 1.6f), Triple(0.22f, 0.07f, 1.1f),
    Triple(0.34f, 0.16f, 1.9f), Triple(0.46f, 0.06f, 1.0f),
    Triple(0.58f, 0.14f, 1.5f), Triple(0.70f, 0.08f, 1.2f),
    Triple(0.82f, 0.17f, 2.0f), Triple(0.92f, 0.10f, 1.1f),
    Triple(0.16f, 0.24f, 1.0f), Triple(0.40f, 0.27f, 1.3f),
    Triple(0.66f, 0.25f, 1.0f), Triple(0.88f, 0.30f, 1.4f),
)

/**
 * 星星的横向/纵向比例与半径（dp）。低差异序列（R2）排布：比随机均匀，且每次一致。
 *
 * 横纵系数**不能互补**。原来横向用 1/φ=0.6180、纵向用 1/φ²=0.3820，
 * 两者相加恰好等于 1——取模后 fx + fy/0.58 恒为常数，26 颗星全排到两条平行斜线上。
 * 换成 plastic 常数 ρ≈1.3247 的 1/ρ、1/ρ²：因为 ρ 是 x³=x+1 的根，
 * 1、1/ρ、1/ρ² 在有理数上线性无关，点会铺满天空，不会共振成线。
 */
internal val NIGHT_STARS: List<Triple<Float, Float, Float>> = List(26) { i ->
    val fx = ((i + 0.5f) * 0.7548776662f) % 1f
    val fy = (((i + 0.5f) * 0.5698402909f) % 1f) * 0.58f
    Triple(fx, fy, 0.9f + (i % 4) * 0.35f)
}

/** 薄云：横向比例、纵向比例、大小比例。 */
internal val DAY_CLOUDS = listOf(
    Triple(0.14f, 0.155f, 1.00f),
    Triple(0.48f, 0.095f, 0.72f),
    Triple(0.78f, 0.205f, 0.86f),
)

/**
 * 山脚下的几户人家：横向中心、宽度、高度（都是屏幕比例）。
 *
 * 只在两侧各放三户，中间留空——中间那一片到夜里是最暗的地方，
 * 放房子等于把灯开在用户正在读字的位置上。
 */
internal val VILLAGE_HOUSES = listOf(
    floatArrayOf(0.075f, 0.070f, 0.052f),
    floatArrayOf(0.155f, 0.052f, 0.040f),
    floatArrayOf(0.245f, 0.060f, 0.046f),
    floatArrayOf(0.760f, 0.058f, 0.044f),
    floatArrayOf(0.845f, 0.072f, 0.056f),
    floatArrayOf(0.925f, 0.050f, 0.038f),
)

/**
 * 白天/夜晚切换时的天体过场。
 *
 * 只在**真的换了昼夜**时播放：首帧进入、旋转屏幕重组、对局结束（`finished`）都不播。
 * 否则「结束」会被当成天亮，放完日出停在一张输掉的牌桌上，很怪。
 *
 * 全部用 Canvas 画，不引入图片资源：一个圆、一条线、一个减掉一角的路径就够，
 * 换成 PNG 会带来几张密度的图和多一份内存，收益为零。
 */
@Composable
internal fun DayNightTransition(
    night: Boolean,
    finished: Boolean,
    modifier: Modifier = Modifier,
) {
    // 上一次的昼夜。首帧等于当前值，所以刚进牌桌不会莫名其妙放一段过场
    var lastNight by remember { mutableStateOf(night) }
    var playing by remember { mutableStateOf(false) }
    // 播的是哪一次切换：动画中途状态被改写时，画面跟着它，不会画到一半换天体
    var showingNight by remember { mutableStateOf(night) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(night, finished) {
        if (finished || night == lastNight) {
            lastNight = night
            return@LaunchedEffect
        }
        lastNight = night
        showingNight = night
        playing = true
        progress.snapTo(0f)
        try {
            progress.animateTo(1f, tween(DAY_NIGHT_MS, easing = LinearEasing))
        } finally {
            // 取消（退出页面）也要把层收掉，否则下一次组合它会停在半透明状态
            playing = false
        }
    }

    if (!playing) return

    val p = progress.value
    // 先起后落：遮罩和天体一起淡入淡出，避免头尾硬切
    val veil = sin(p * PI).toFloat()
    // 升起只占前 55%，剩下的时间让它停在半空、慢慢淡掉
    val rise = FastOutSlowInEasing.transform((p / 0.55f).coerceIn(0f, 1f))

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val anchor = maxHeight * 0.30f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = 30.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height * 0.30f + (1f - rise) * size.height * 0.22f
            val center = Offset(cx, cy)

            if (showingNight) {
                // 入夜：深蓝压下来，星星一起亮
                drawRect(Color(0xFF050A14).copy(alpha = 0.5f * veil))
                STARS.forEach { (fx, fy, density) ->
                    drawCircle(
                        color = Color(0xFFFFF6DA).copy(alpha = 0.8f * veil),
                        // 半径带一点差异，不然十二个点大小一样像噪点
                        radius = density.dp.toPx(),
                        center = Offset(size.width * fx, size.height * fy)
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(MoonGlow.copy(alpha = 0.32f * veil), Color.Transparent),
                        center = center,
                        radius = radius * 3.4f
                    ),
                    radius = radius * 3.4f,
                    center = center
                )
                // 月牙 = 整圆减去右上偏移的圆。用路径求差而不是盖一个背景色圆：
                // 背景是渐变，盖不干净，边上会留一圈色差
                val disc = Path().apply { addOval(Rect(center, radius)) }
                val bite = Path().apply {
                    addOval(Rect(Offset(cx + radius * 0.55f, cy - radius * 0.45f), radius * 0.96f))
                }
                disc.op(disc, bite, PathOperation.Difference)
                drawPath(disc, color = MoonCore.copy(alpha = veil))
            } else {
                // 天亮：暖光铺开，太阳从下往上顶
                drawRect(Color(0xFFFFF6E0).copy(alpha = 0.55f * veil))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(SunGlow.copy(alpha = 0.5f * veil), Color.Transparent),
                        center = center,
                        radius = radius * 4.4f
                    ),
                    radius = radius * 4.4f,
                    center = center
                )
                // 十二道光芒，整体随进度慢转，看起来像在升起时晃了一下
                repeat(12) { i ->
                    val angle = (i / 12f) * 2f * PI.toFloat() + p * 0.4f
                    val inner = radius * 1.3f
                    val outer = radius * (1.55f + 0.12f * sin((i * 1.7f + p * 3f).toDouble()).toFloat())
                    drawLine(
                        color = SunCore.copy(alpha = 0.5f * veil),
                        start = Offset(cx + inner * cos(angle), cy + inner * sin(angle)),
                        end = Offset(cx + outer * cos(angle), cy + outer * sin(angle)),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFF8E4), SunCore),
                        center = Offset(cx - radius * 0.25f, cy - radius * 0.25f),
                        radius = radius * 1.4f
                    ),
                    radius = radius,
                    center = center
                )
            }
        }

        Text(
            text = if (showingNight) "天黑请闭眼" else "天亮了",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = anchor + 58.dp)
                .alpha(veil),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            // 拉开字距，像一句宣告而不是一条提示
            letterSpacing = 4.sp,
            color = if (showingNight) Color(0xFFDCE4F5) else Color(0xFF8A6A2F)
        )
    }
}

// ---------------------------------------------------------------------------
// 背景
//
// 之前牌的背景就是一块纯色（白天 #F3F6FC、夜里深蓝），干净但空：
// 一屏文字压在什么都没有的底上，看起来像还没做完的界面。
//
// 这里画一个村子：白天是蓝天、薄云、远山，夜里是星空、月亮、同一道山脊上
// 几户亮着灯的人家。所有元素都不用图片——几个路径和圆就够，
// 省掉三四套密度图和一份额外的内存。
//
// 三条自我约束：
// 1. 一切都很淡。背景是背景，中央那列对话才是要读的东西，
//    任何一处 0.3 以上的不透明度都会开始抢字。
// 2. 山和房子固定在底部：那里要么是操作面板（有卡片底），要么是整屏最后几行，
//    再怎么也不会压在中段正在读的发言上。
// 3. 星星和云的位置写死。随机位置每开一局都换个星空，
//    同一局里回头看一眼天际线都会觉得「刚才不是这样」。
// ---------------------------------------------------------------------------

/**
 * 牌桌背景。
 *
 * [dayness] 1 = 白天，0 = 夜晚，中间的连续值用于过场：两层同时画，
 * 各自乘自己的权重，所以天色是慢慢退成夜色的，不是切一张图。
 */
@Composable
internal fun TableBackdrop(dayness: Float, modifier: Modifier = Modifier) {
    // 星空呼吸。整层共用一个相位，各星按序号错开，
    // 拿 State 而不是读出来的值：这样每帧只重绘，不重组整个牌桌
    val twinkle = rememberInfiniteTransition(label = "backdrop")
    val phaseState = twinkle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "twinkle"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val nightAmount = 1f - dayness

        // 天色本身也跟着权重插值，两层的接缝才不会露出来
        drawRect(
            Brush.verticalGradient(
                0f to lerp(DaySkyTop, NightSkyTop, nightAmount),
                0.42f to lerp(DaySkyMid, NightSkyTop, nightAmount),
                1f to lerp(DaySkyLow, NightSkyLow, nightAmount),
            )
        )

        if (dayness > 0.01f) drawDaySky(w, h, dayness)

        val far = ridgeFar(w, h)
        val near = ridgeNear(w, h)

        if (nightAmount > 0.01f) {
            drawNightSky(w, h, nightAmount, phaseState.value)
            drawPath(far, NightRidgeFar.copy(alpha = 0.92f * nightAmount))
            drawPath(near, NightRidgeNear)
            drawVillage(
                w = w,
                h = h,
                color = NightRidgeNear,
                windowColor = WindowGlow.copy(alpha = 0.62f * nightAmount)
            )
        }

        if (dayness > 0.01f) {
            drawPath(far, DayRidgeFar.copy(alpha = 0.15f * dayness))
            drawPath(near, DayRidgeNear.copy(alpha = 0.11f * dayness))
            drawVillage(
                w = w,
                h = h,
                color = DayRidgeNear.copy(alpha = 0.13f * dayness),
                windowColor = null
            )
        }
    }
}

/** 白天：一团暖光当太阳，再加几条薄云。不画实心太阳——它会变成第二个焦点，抢「第几天」。 */
internal fun DrawScope.drawDaySky(w: Float, h: Float, a: Float) {
    val sun = Offset(w * 0.84f, h * 0.09f)
    val halo = h * 0.36f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(DaySun.copy(alpha = 0.30f * a), Color.Transparent),
            center = sun,
            radius = halo
        ),
        radius = halo,
        center = sun
    )

    DAY_CLOUDS.forEach { (fx, fy, scale) ->
        val cx = w * fx
        val cy = h * fy
        val r = h * 0.026f * scale
        val color = Color.White.copy(alpha = 0.62f * a)
        // 三个扁椭圆叠一朵：单个椭圆看着像块污渍
        drawOval(color, Offset(cx - r * 2.3f, cy - r * 0.55f), Size(r * 4.6f, r * 1.10f))
        drawOval(color, Offset(cx - r * 1.30f, cy - r * 1.05f), Size(r * 2.60f, r * 1.25f))
        drawOval(color, Offset(cx + r * 0.10f, cy - r * 1.30f), Size(r * 2.10f, r * 1.35f))
    }
}

/** 夜里：星、月，以及一层压在天际线上的暗。 */
internal fun DrawScope.drawNightSky(w: Float, h: Float, a: Float, phase: Float) {
    NIGHT_STARS.forEachIndexed { i, (fx, fy, r) ->
        // 亮度按相位起伏，各星错开 0.13 个周期，看起来像缓慢的闪烁而不是整片一起呼吸
        val flicker = 0.45f + 0.35f * sin((phase + i * 0.13f) * 2f * PI).toFloat()
        drawCircle(
            color = Color(0xFFFFF6DA).copy(alpha = flicker * a),
            radius = r.dp.toPx(),
            center = Offset(w * fx, h * fy)
        )
    }

    // 月亮放在右上角，跟过场里正中升起的那颗避开：过场是「刚升起来」，
    // 稳定态是「已经挂在那个位置上了」
    val moon = Offset(w * 0.87f, h * 0.105f)
    val r = h * 0.020f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(MoonLight.copy(alpha = 0.22f * a), Color.Transparent),
            center = moon,
            radius = r * 5.0f
        ),
        radius = r * 5.0f,
        center = moon
    )
    // 月牙：整圆减掉偏右上的圆。用路径求差而不是盖一个天色圆，
    // 天色是渐变，盖上去会留一圈色差
    val disc = Path().apply { addOval(Rect(moon, r)) }
    val bite = Path().apply {
        addOval(Rect(Offset(moon.x + r * 0.55f, moon.y - r * 0.45f), r * 0.96f))
    }
    disc.op(disc, bite, PathOperation.Difference)
    drawPath(disc, MoonLight.copy(alpha = 0.55f * a))
}

/** 远山。曲线固定，白天黑夜共用同一道轮廓。 */
internal fun ridgeFar(w: Float, h: Float): Path = Path().apply {
    moveTo(0f, h * 0.760f)
    quadraticTo(w * 0.16f, h * 0.700f, w * 0.33f, h * 0.744f)
    quadraticTo(w * 0.50f, h * 0.786f, w * 0.66f, h * 0.728f)
    quadraticTo(w * 0.83f, h * 0.684f, w, h * 0.752f)
    lineTo(w, h)
    lineTo(0f, h)
    close()
}

/** 近处的一道缓坡，山脚那几户人家站在它上面。 */
internal fun ridgeNear(w: Float, h: Float): Path = Path().apply {
    moveTo(0f, h * 0.872f)
    quadraticTo(w * 0.23f, h * 0.818f, w * 0.46f, h * 0.864f)
    quadraticTo(w * 0.71f, h * 0.912f, w, h * 0.846f)
    lineTo(w, h)
    lineTo(0f, h)
    close()
}

/** 屋顶天际线：墙 + 出檐的三角屋顶，夜里再点一盏窗灯。 */
internal fun DrawScope.drawVillage(w: Float, h: Float, color: Color, windowColor: Color?) {
    val ground = h * 0.998f
    VILLAGE_HOUSES.forEach { house ->
        val cx = w * house[0]
        val half = w * house[1] / 2f
        val body = h * house[2]
        val top = ground - body

        drawRect(color, Offset(cx - half, top), Size(half * 2f, body))
        drawPath(
            Path().apply {
                moveTo(cx - half * 1.24f, top + 1f)
                lineTo(cx, top - body * 0.48f)
                lineTo(cx + half * 1.24f, top + 1f)
                close()
            },
            color
        )

        if (windowColor != null) {
            drawCircle(
                color = windowColor,
                radius = minOf(half * 0.30f, body * 0.16f),
                center = Offset(cx, top + body * 0.45f)
            )
        }
    }
}

internal const val DAY_NIGHT_MS = 1400
