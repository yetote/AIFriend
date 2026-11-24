package com.ui.werewolf

// 思考浮动框：挂在头像附近的漫画式对白框（固定尺寸 + 斜向尖角）。

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import com.ui.werewolf.data.UITokenUsage
import kotlinx.coroutines.delay

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
 * 思考气泡里显示的那几行。
 *
 * 这曾经是「按整行过滤 + takeLast(4)」：把超过 60 字的行当作
 * 「厂商把 system prompt 回显了」整行丢掉。问题是流式文本**一直在长**，
 * 正在生长的这一句迟早超过 60 字——那一刻它被删掉，`takeLast(4)` 的窗口
 * 整体退回**上一句**，等它写完换行又跳回来。看着就像气泡在自己往回翻页。
 *
 * 流式本身不是毛病，毛病在「用长度判断整行合格」这件事：正在长的行和不合格的长行
 * 长得一模一样。所以改成**先切段、再从尾部取**：文本变长时窗口只会向上滚，
 * 永远不会整体回退，也顺便保证「最新的想法」一定在气泡里。
 */
internal object ThinkingTail {

    /**
     * 一行放得下多少「全角字」。
     *
     * 气泡 196dp 宽、减去左右内边距剩 176dp；11sp 时一个汉字正好一个字宽，
     * 所以中文一行 16 个。英文按半个算，一行约 32 个字符——思考内容经常是英文，
     * 按字数硬切会把单词劈成两半，白扔半行地方。
     */
    private const val LINE_UNITS = 16

    /** 气泡能放几行。高度 104dp 扣掉「思考中」标签和留白，正好 4 行 15sp。 */
    private const val MAX_LINES = 4

    /** 优先在这些字符后面断句，别把「，」掰成两行。 */
    private const val BREAKS = "。！？；：，、.,!?;: "

    fun lines(reasoning: String): List<String> {
        val segments = ArrayList<String>()
        reasoning.replace("\r", "").lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            var start = 0
            while (start < line.length) {
                var end = start
                var used = 0.0
                while (end < line.length && used + unitOf(line[end]) <= LINE_UNITS) {
                    used += unitOf(line[end])
                    end++
                }
                // 一个全角字就超宽（理论上不会），硬推一个，免得原地打转
                if (end == start) end = start + 1
                if (end < line.length) {
                    // 只在后半段找断点：前半段就断会切出「我」「觉得」这种碎片
                    val cut = (end - 1 downTo start + (end - start) / 2)
                        .firstOrNull { line[it] in BREAKS }
                    if (cut != null) end = cut + 1
                }
                val piece = line.substring(start, end).trim()
                if (piece.isNotEmpty()) segments += piece
                start = end
            }
        }

        // 从尾部往前取：宁可少显示开头，也不能把正在写的这一句挤掉。
        // 段数也要卡：每段之间是硬换行，5 个短段一样会占 5 行。
        val budget = LINE_UNITS * MAX_LINES
        val picked = ArrayList<String>()
        var used = 0.0
        for (i in segments.indices.reversed()) {
            if (picked.size >= MAX_LINES) break
            val s = segments[i]
            val u = unitsOf(s) + 1
            // 至少留一段：万一单段就超预算，也比空白好
            if (picked.isNotEmpty() && used + u > budget) break
            picked += s
            used += u
        }
        return picked.asReversed()
    }

    /** 半角 0.5、全角 1。够用了：这里只要「这一行塞不塞得下」，不做排版。 */
    private fun unitOf(c: Char): Double = if (c.code < 0x80) 0.5 else 1.0

    private fun unitsOf(s: CharSequence): Double {
        var u = 0.0
        for (c in s) u += unitOf(c)
        return u
    }
}

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
    /** 是谁在想。换人时计时重新开始——不然下一位一开口就顶着上一位的秒数。 */
    seat: Int = 0,
    /** 正文已经开始进发言列表。发言段秒表重新起算：说的和想的是两个时钟。 */
    speechStarted: Boolean = false,
    /** 当前段的上限，0 表示不设限。有值时才显示「/ 60s」。 */
    limitSeconds: Int = 0,
    /** 长按气泡时回调：本局隐藏这个座位的思考气泡。null = 不响应长按。 */
    onHide: (() -> Unit)? = null,
    /** 到现在的 token 用量；没有就留空（不支持统计的旧模型）。 */
    usage: UITokenUsage? = null,
) {
    if (reasoning.isBlank()) return
    // 坐标还没算出来（座位刚布局完）或者算坏了，就先别画：
    // 一个飘在半空的框比没有框更糟
    if (!anchor.x.isFinite() || !anchor.y.isFinite()) return

    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // 只留「像人话」的最后几段：有些厂商会把 system prompt 里的指令原样回显到思考里
    // （"Must give judgment: who I suspect…" 这种），而切段取尾之后就只剩最新的一小截，
    // 既难看又挤地方的问题自然被预算挡住了。
    val lines = remember(reasoning) { ThinkingTail.lines(reasoning) }

    // 思考气泡不透底：框里是成段推理，背后就是座位和山影，
    // 半透明会把字吃掉，还显得脏。牌桌其他气泡透出去是为了「浮在桌上」，
    // 这一个的职责是「读得清」，两码事。
    val fill = if (night) Color(0xFF221D2E) else Color(0xFFFFFEFB)
    val border = if (night) Color(0x4DA894FF) else Color(0x336B4BD9)
    val textColor = if (night) Color(0xFFE6EAF3) else Color(0xFF3A4152)

    // 秒表在界面这侧跑，不跟引擎走：引擎只在有数据到达时推一次，
    // 而「模型一声不吭地干等」恰恰是最需要看到秒数的时刻——那一整段时间引擎是静默的。
    // 它回答的是用户最常问的那句话：这个模型到底慢在哪一步、慢了多久。
    // 思考段和发言段各算各的：正文开始吐的那一刻，时钟归零重新数说话的时间。
    var elapsedSeconds by remember(seat, speechStarted) { mutableIntStateOf(0) }
    LaunchedEffect(seat, speechStarted) {
        val started = System.currentTimeMillis()
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - started) / 1000L).toInt()
            delay(500L)
        }
    }
    // 快到上限时变色：这时候还看不出来，下一秒就是兜底了
    val stressed = limitSeconds > 0 && elapsedSeconds * 100 >= limitSeconds * 80
    val spentColor = when {
        stressed && night -> Color(0xFFFFB4B0)
        stressed -> Color(0xFFC0392B)
        night -> Color(0xFFA894FF)
        else -> Color(0xFF6B4BD9)
    }

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

        val bubbleShape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .offset { IntOffset(bubbleLeft.toInt(), bubbleTop.toInt()) }
                .size(BUBBLE_WIDTH, BUBBLE_HEIGHT)
                .shadow(
                    elevation = if (night) 12.dp else 10.dp,
                    shape = bubbleShape,
                    clip = false,
                    ambientColor = if (night) Color(0x66000000) else Color(0x286B4BD9),
                    spotColor = if (night) Color(0x66000000) else Color(0x286B4BD9),
                )
                .clip(bubbleShape)
                .background(fill)
                .border(1.dp, border, bubbleShape)
                // 长按 = 本局藏掉这个座位的内心戏：看着累、或者不想被剧透，
                // 不用为一它把全场的思考都关了。对局设置面板里可以再打开。
                .let { m ->
                    if (onHide != null) {
                        m.pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onHide() })
                        }
                    } else {
                        m
                    }
                }
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Column {
                val accent = if (night) Color(0xFFA894FF) else Color(0xFF6B4BD9)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "思考中",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Spacer(Modifier.weight(1f))
                    if (usage != null && usage.total > 0) {
                        Text(
                            text = usage.shortLabel,
                            fontSize = 8.sp,
                            color = accent.copy(alpha = 0.72f),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = if (limitSeconds > 0) {
                            "${elapsedSeconds}s / ${limitSeconds}s"
                        } else {
                            "${elapsedSeconds}s"
                        },
                        fontSize = 9.sp,
                        fontWeight = if (stressed) FontWeight.SemiBold else FontWeight.Medium,
                        color = spentColor,
                    )
                }
                Text(
                    text = lines.joinToString("\n"),
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = textColor.copy(alpha = 0.88f),
                    // 比预算多留一行：中文混英文时偶尔会多折一行，别让最末尾那句被裁掉
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 记录流
// ---------------------------------------------------------------------------
