package com.ui.werewolf

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.UIAvatar
import com.ui.common.pageInsetsBottom
import com.ui.common.pageInsetsTop
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIFieldFill
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.uiCard
import com.ui.werewolf.data.UIWerewolfLine
import com.ui.werewolf.data.UIWerewolfBubbleKind
import com.ui.werewolf.data.UIWerewolfLineKind
import com.ui.werewolf.data.bubbleStyle
import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfSeatLive
import com.ui.werewolf.data.UIWerewolfStreaming
import com.ui.werewolf.data.UIWerewolfTarget
import com.ui.werewolf.data.UIWerewolfTurn
import com.ui.werewolf.data.UIWerewolfTurnKind
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
import androidx.compose.animation.core.animateFloatAsState
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
private val FsSpeech = 14.sp
private val FsSpeechLine = 21.sp

/** 人名、按钮、面板标题。 */
private val FsName = 12.5.sp
private val FsAction = 14.sp

/** 座位号、天数、辅助说明这类「看一眼就够」的字。 */
private val FsMeta = 11.5.sp

/** 座位上那一行「3 阿明」，受列宽限制，只能比正文小一档。 */
private val FsSeatName = 11.sp

/** 身份/死因徽标。压在名字下面，再小就认不出「刀」「票」两个字了。 */
private val FsBadge = 9.5.sp

/**
 * 流式发言每长出这么多字就往下贴一次底。
 *
 * 太少会每个增量都滚一次（抖动），太多则一句长发言的后半截会跑出屏幕。
 */
private const val STREAM_SCROLL_CHARS = 60

/**
 * 思考气泡最高能顶到哪儿（距屏幕顶）。
 *
 * 再高就压到顶栏了——顶栏上有阶段进度和朗读开关，被盖住就点不到了。
 */
private val MIN_BUBBLE_TOP = 104.dp

// ---------------------------------------------------------------------------
// 配色
//
// 狼人杀的「夜」是这个游戏最重要的一层信息：夜里谁能行动、谁在闭眼，
// 决定了玩家能不能读懂第二天谁在说谎。所以到夜里整页换暗色，但不换页——
// 换页会把牌桌的连续感切碎，也让人以为进了另一个地方。
//
// 暗色只压在牌桌和记录上，顶栏和输入区跟着一起变，避免出现「上半截夜里、下半截白天」。
// ---------------------------------------------------------------------------

private val NightText = Color(0xFFE9EEF9)
private val NightDim = Color(0xFF97A3BC)
private val NightSurface = Color(0xFF1B2437)
private val NightStroke = Color(0x1FFFFFFF)

private val DaySurface = Color(0xFFFDFDFF)

// 背景。
//
// 白天的天色是浅蓝到暖沙，夜里的天色是墨蓝，而**山脊的形状两个时段完全一样**：
// 玩家白天记住的地形，到夜里还是那个轮廓，「天色变了、地方没变」。
// 两张互不相干的背景虽然更省事，但会让人觉得每回合换了一个场地。
private val DaySkyTop = Color(0xFFE6EFFC)
private val DaySkyMid = Color(0xFFF4F8FE)
private val DaySkyLow = Color(0xFFFBF6EC)
private val NightSkyTop = Color(0xFF060A12)
private val NightSkyLow = Color(0xFF121D31)

private val DaySun = Color(0xFFFFCE7A)
private val DayRidgeFar = Color(0xFF7E9484)
private val DayRidgeNear = Color(0xFF5E7466)
private val NightRidgeFar = Color(0xFF0A1018)
private val NightRidgeNear = Color(0xFF05080E)

private val MoonLight = Color(0xFFCBD6F5)
private val WindowGlow = Color(0xFFFFC069)

/** 自己：座位环上的描边、身份胶囊。 */
private val SelfBlue = Color(0xFF2B6BFF)

/** 出局：一律降饱和，再叠一个原因字。颜色 + 字双编码，色弱也分得清「刀」和「票」。 */
private val FateRed = Color(0xFFD9534F)
private val VoteRed = Color(0xFFE0564F)

/** 警徽的金色。和票数的红、自己的蓝都拉开，扫一眼不会认错。 */
private val SheriffGold = Color(0xFFC79A2E)

/**
 * 牌桌页。
 *
 * 一屏之内要同时交代四件事，优先级从上到下：
 * 我是谁（顶栏下一行）→ 场上有谁（左右两列）→ 刚刚发生了什么（中间对话）→ 现在要我做什么（底部）。
 * 没有 tab、没有二级页：狼人杀的信息是连续的，切走再切回来就读不懂局势了。
 *
 * 座位从环形改成左右两列：环形在 10 人以上会缩成一圈看不清的小头像，中央只剩一条窄缝，
 * 而左右两侧的空白完全用不上。竖排两列把宽度用满，头像和名字都能大一号，
 * 中间那条留给对话——真正需要读的东西。
 */
@Composable
fun UIWerewolfTableScreen(
    live: UIWerewolfLive,
    onExit: () -> Unit,
    onSpeech: (String) -> Unit = {},
    onVote: (Int?) -> Unit = {},
    onSeat: (Int) -> Unit = {},
    onWitch: (heal: Boolean, poison: Int?) -> Unit = { _, _ -> },
    onReview: (() -> Unit)? = null,
    onToggleSpeak: () -> Unit = {},
) {
    val night = live.night && !live.finished
    val textColor by animateColorAsState(
        targetValue = if (night) NightText else UIFriendBarTitleColor,
        animationSpec = tween(DAY_NIGHT_MS / 2),
        label = "text"
    )
    val dimColor by animateColorAsState(
        targetValue = if (night) NightDim else UIFriendBarItemSubtitleColor,
        animationSpec = tween(DAY_NIGHT_MS / 2),
        label = "dim"
    )
    val surface by animateColorAsState(
        targetValue = if (night) NightSurface else DaySurface,
        animationSpec = tween(DAY_NIGHT_MS / 2),
        label = "surface"
    )

    // 背景整体按昼夜插值：白天图层乘 dayness、夜里图层乘 (1-dayness)。
    // 用的是和文字、卡片同一段时长，天一黑是整片一起黑，不会出现
    // 「字已经白了、天还亮着」那种半截状态。
    val dayness by animateFloatAsState(
        targetValue = if (night) 0f else 1f,
        animationSpec = tween(DAY_NIGHT_MS / 2),
        label = "dayness"
    )

    // 座位头像在窗口里的位置。思考气泡要挂在头像头顶，得知道它在哪。
    val seatAnchors = remember { mutableStateMapOf<Int, Offset>() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景铺满整屏（含状态栏后面），内容再按安全区往里缩
        TableBackdrop(dayness = dayness)

        Column(modifier = Modifier.fillMaxSize().pageInsetsTop()) {
            TableTopBar(
                title = if (live.finished) "对局结束" else if (night) "第 ${live.day} 夜" else "第 ${live.day} 天",
                subtitle = live.progressLabel,
                night = night,
                textColor = textColor,
                dimColor = dimColor,
                spectating = live.spectating,
                autoSpeak = live.autoSpeak,
                speechReady = live.speechReady,
                onToggleSpeak = onToggleSpeak,
                onBack = onExit
            )

            SelfBar(
                live = live,
                night = night,
                textColor = textColor,
                dimColor = dimColor
            )

            if (live.error != null) {
                ErrorStrip(message = live.error, night = night)
            }

            // 左右两列玩家夹住中间的对话流。中间拿 weight，屏幕再窄也优先保住说话的地方——
            // 身份和出局状态挤一挤还能读，话被裁掉就完全看不懂局势了。
            SeatColumns(
                live = live,
                night = night,
                textColor = textColor,
                dimColor = dimColor,
                surface = surface,
                anchors = seatAnchors,
                modifier = Modifier.weight(1f)
            )

            val turn = live.turn
            when {
                // 结束了就只留一个出口。原本这里是输入区，输入区已经没有任何意义，
                // 但空着会让人以为界面坏了——空位必须被「下一步做什么」填上。
                live.finished && onReview != null -> ReviewBar(
                    surface = surface,
                    textColor = textColor,
                    dimColor = dimColor,
                    onReview = onReview,
                    modifier = Modifier.pageInsetsBottom()
                )

                turn != null -> TurnPanel(
                    turn = turn,
                    night = night,
                    textColor = textColor,
                    dimColor = dimColor,
                    surface = surface,
                    onSpeech = onSpeech,
                    onVote = onVote,
                    onSeat = onSeat,
                    onWitch = onWitch,
                    modifier = Modifier.pageInsetsBottom()
                )

                else -> {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.pageInsetsBottom())
                }
            }
        }

        // 思考气泡浮在最上层：它要盖在座位和对话流上面，
        // 画在别处会被对话流压住。
        val stream = live.streaming
        val anchor = stream?.let { seatAnchors[it.seat] }
        if (stream != null && anchor != null && stream.reasoning.isNotBlank()) {
            ThinkingBubble(reasoning = stream.reasoning, anchor = anchor, night = night)
            BubbleTail(anchor = anchor, night = night)
        }

        // 过场画在最上层，但不吃触摸：1.4 秒里用户可能正要点「发言」，
        // 拦一下只会让人觉得界面卡了
        DayNightTransition(
            night = live.night,
            finished = live.finished
        )
    }
}

// ---------------------------------------------------------------------------
// 顶栏与身份
// ---------------------------------------------------------------------------

/**
 * 自绘顶栏而不是用 `UITopBar`：全局那个组件写死了浅色文字，夜里会直接看不见。
 * 为了一个页面去改公共组件不划算，也不该让公共组件背上主题分支。
 */
@Composable
private fun TableTopBar(
    title: String,
    subtitle: String?,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    spectating: Boolean,
    autoSpeak: Boolean,
    speechReady: Boolean,
    onToggleSpeak: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = textColor
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = FsName,
                    color = dimColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (spectating) {
            Tag(text = "上帝视角", night = night, color = dimColor)
            Spacer(Modifier.width(12.dp))
        }
        IconButton(
            onClick = onToggleSpeak,
            // 不可用时不禁用：点一下会重试绑定引擎（国产 ROM 的 init 会偶发失败，
            // 一旦失败不重建就永远灰着）。禁用反而断了唯一的恢复入口。
        ) {
            Icon(
                imageVector = if (autoSpeak) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                contentDescription = if (!speechReady) {
                    "语音暂不可用，点此重试"
                } else if (autoSpeak) {
                    "关闭自动朗读"
                } else {
                    "开启自动朗读"
                },
                // 不可用时用暗色，但仍可点——点它会重试，而不是没反应。
                tint = if (speechReady) textColor else dimColor,
            )
        }
    }
}

/**
 * 我是谁。
 *
 * 这一行必须常驻：狼人杀里「我几号、我什么身份」是读每一句话的前提，
 * 藏进抽屉或弹窗都会让人反复去翻。观战则换成一句「不参与，只看」。
 */
@Composable
private fun SelfBar(
    live: UIWerewolfLive,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (live.spectating) {
            Tag(text = "观战", night = night, color = dimColor)
            Text(
                text = "八个人各说各话，你只负责看",
                fontSize = FsName,
                color = dimColor
            )
            return@Row
        }

        val seat = live.selfSeat
        if (seat == null) return@Row

        Text(
            text = "$seat 号",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SelfBlue)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        live.selfRoleLabel?.let { role ->
            Text(
                text = role,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
        live.selfFactionLabel?.let { faction ->
            Text(
                text = faction,
                fontSize = FsName,
                color = dimColor
            )
        }
        Spacer(Modifier.weight(1f))
        if (live.running) ThinkingDot(color = if (night) NightText else SelfBlue)
    }
}

/** 小胶囊标签。夜里换半透明深色底，白底在暗色上会刺眼。 */
@Composable
private fun Tag(text: String, night: Boolean, color: Color) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (night) NightStroke else UIFieldFill)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        fontSize = FsMeta,
        color = color
    )
}

/** 等待模型时的一枚呼吸点。静止的界面在几十秒的等待里会被当成卡死。 */
@Composable
private fun ThinkingDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color.copy(alpha = alpha), CircleShape)
    )
}

// ---------------------------------------------------------------------------
// 牌桌
// ---------------------------------------------------------------------------

/**
 * 座位分列。
 *
 * 从环改成两列，是因为环在 10 人以上会挤成一圈小点：中央留给「第几天」的地方被压扁，
 * 头像缩到认不出人，而两侧大片空白根本用不上。竖排两列把同一屏的宽度用满，
 * 头像反而能大一号。
 *
 * 分法是**前一半在左、后一半在右**：6 人局左列 1-3、右列 4-6，8 人局左 1-4、右 5-8。
 * 奇数人数时左边多坐一个（9 人就是 5/4），因为读的顺序本来就是左列读完再读右列。
 */
@Composable
private fun SeatColumns(
    live: UIWerewolfLive,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    surface: Color,
    /** 座位头像在窗口里的位置。思考气泡要挂在它头顶，得知道它在哪。 */
    anchors: MutableMap<Int, Offset>,
    modifier: Modifier = Modifier,
) {
    // 座位号升序排一次再分栏。引擎给的是按座位排好的，但这里不依赖那个隐式约定
    val ordered = remember(live.seats) { live.seats.sortedBy { it.seat } }
    // 左列先排满再排右列：ceil 而不是 size/2，奇数人数多出来的那个留在左边，
    // 这样「1 号在左上、最后一位在右下」，顺序上没有断口
    val half = (ordered.size + 1) / 2
    val left = remember(ordered) { ordered.take(half) }
    val right = remember(ordered) { ordered.drop(half) }

    Row(modifier = modifier.fillMaxWidth()) {
        SeatColumn(
            seats = left,
            night = night,
            textColor = textColor,
            dimColor = dimColor,
            anchors = anchors,
            modifier = Modifier.width(COLUMN_WIDTH).fillMaxHeight()
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            StatusStrip(live = live, night = night, textColor = textColor, dimColor = dimColor)
            Feed(
                live = live,
                night = night,
                textColor = textColor,
                dimColor = dimColor,
                surface = surface,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }

        SeatColumn(
            seats = right,
            night = night,
            textColor = textColor,
            dimColor = dimColor,
            anchors = anchors,
            modifier = Modifier.width(COLUMN_WIDTH).fillMaxHeight()
        )
    }
}

/**
 * 一列侧栏的宽度。
 *
 * 72dp：34dp 头像 + 两侧留白，名字一行能放「12 阿明」这种四五个汉字。
 * 再宽就要从中间那列抢地方——中间的对话是这页唯一需要逐字读的东西。
 */
private val COLUMN_WIDTH = 72.dp

/**
 * 一列玩家。
 *
 * 每人占 `weight(1f)` 的一格，内容在格内居中：人数少的时候（6 人局只有 3 行）
 * 自然铺开显得像「桌子两侧坐着人」，人数多的时候每格一起变矮，绝不会把最后一个人
 * 挤出屏幕——`SpaceEvenly` 做不到这一点，它在内容超高时只会一路排到屏幕外面去。
 */
@Composable
private fun SeatColumn(
    seats: List<UIWerewolfSeatLive>,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    anchors: MutableMap<Int, Offset>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        seats.forEach { seat ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                SeatTile(
                    seat = seat,
                    night = night,
                    textColor = textColor,
                    dimColor = dimColor,
                    onAnchor = { offset -> anchors[seat.seat] = offset }
                )
            }
        }
    }
}

/**
 * 一枚座位。
 *
 * 出局的人不删、不灰到底：狼人杀的「谁死了」和「谁还活着」一样重要，
 * 移走之后就没办法回头对照「他生前说了什么」。所以留着、压暗、再挂一个字说明怎么死的。
 *
 * 尺寸受 12 人局限制：一列 6 个人，一格只有七十多 dp 高，
 * 头像 34 是「认得出这张脸」和「放得下第六个人」之间的那条线。
 */
@Composable
private fun SeatTile(
    seat: UIWerewolfSeatLive,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    /** 头像中心在窗口里的位置。思考气泡挂在它头顶。 */
    onAnchor: (Offset) -> Unit,
) {
    Column(
        modifier = Modifier.width(COLUMN_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .onGloballyPositioned { coords ->
                    // 头像中心：气泡的尖角要指到这儿
                    onAnchor(
                        Offset(
                            coords.positionInRoot().x + coords.size.width / 2f,
                            coords.positionInRoot().y + coords.size.height / 2f,
                        )
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (seat.active) {
                // 正在发言/正在行动的人要有动静，静止的牌桌看不出轮到谁。
                // 两层：贴着头像的呼吸光说「是这个人」，往外扩的涟漪说「他正在出声」。
                // 只有呼吸的话，一个正在思考的人和一个正在说话的人长得一样；
                // 只有涟漪的话，安静时看不出重心在哪。两个一起才读得出来。
                val transition = rememberInfiniteTransition(label = "active")
                val scale by transition.animateFloat(
                    initialValue = 0.90f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                // 涟漪周期 1.6s：比呼吸（1.6s 一个来回）慢半拍，两圈不会叠成一个节拍器
                val ripple by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1600, easing = LinearEasing)
                    ),
                    label = "ripple"
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer {
                            // 扩到 1.5 倍（约 57dp），72dp 的列里放得下，不会压到隔壁
                            val grow = 1f + 0.5f * ripple
                            scaleX = grow
                            scaleY = grow
                            // 扩出去的同时淡掉：一圈硬边扩到底再消失会像故障
                            alpha = (1f - ripple) * 0.9f
                        }
                        .border(1.5.dp, Color(0xFF6C9BFF), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .background(Color(0x332B6BFF), CircleShape)
                )
            }

            val ring = when {
                seat.isSelf -> SelfBlue
                seat.active -> Color(0xFF6C9BFF)
                else -> Color.Transparent
            }
            UIAvatar(
                name = seat.name,
                avatarPath = seat.avatarPath,
                size = 34.dp,
                shape = CircleShape,
                modifier = Modifier
                    .alpha(if (seat.alive) 1f else 0.34f)
                    .then(
                        if (ring != Color.Transparent) {
                            Modifier.border(2.dp, ring, CircleShape)
                        } else {
                            Modifier
                        }
                    )
            )

            // 票数：投票阶段最需要一眼看出的数字，压在头像右下角
            val votes = seat.votes
            if (votes != null && votes > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(17.dp)
                        .background(VoteRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = votes.toString(),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            // 警徽：戴在头像左上角。它和右下角的票数不会打架，
            // 而「1.5 票」这件事只有靠这个标记才能当场读出来
            if (seat.sheriff) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(15.dp)
                        .background(SheriffGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "警",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        Text(
            text = "${seat.seat} ${seat.name}",
            modifier = Modifier.padding(top = 1.dp),
            fontSize = FsSeatName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = when {
                !seat.alive -> dimColor.copy(alpha = 0.6f)
                seat.isSelf -> if (night) NightText else SelfBlue
                else -> textColor
            }
        )

        // 身份只在公开时出现；否则用出局原因占这个位置，两者不会同时出现
        val badge = seat.fate?.short ?: seat.roleLabel
        if (badge != null) {
            Text(
                text = badge,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        when {
                            seat.fate != null -> FateRed.copy(alpha = 0.14f)
                            seat.wolf -> Color(0x1FD9534F)
                            else -> Color(0x142B6BFF)
                        }
                    )
                    .padding(horizontal = 5.dp, vertical = 0.5.dp),
                fontSize = FsBadge,
                fontWeight = FontWeight.Medium,
                color = when {
                    seat.fate != null -> FateRed
                    seat.wolf -> FateRed
                    else -> if (night) NightDim else SelfBlue
                }
            )
        }
    }
}

/**
 * 中间栏的顶条：第几天、什么阶段、正在等谁。
 *
 * 原来这些字摆在牌桌正中央，现在中央让给了对话，它们退成一条窄带。
 * 但不能删：等待模型的那几十秒里，「现在到谁了」是唯一能确认程序没卡死的东西。
 */
@Composable
private fun StatusStrip(
    live: UIWerewolfLive,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (live.finished) {
            Text(
                text = live.winnerLabel ?: "对局结束",
                fontSize = FsAction,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1
            )
            return@Row
        }

        Text(
            text = if (night) "第 ${live.day} 夜" else "第 ${live.day} 天",
            fontSize = 12.sp,
            color = dimColor
        )
        Text(
            text = live.phaseLabel,
            fontSize = FsAction,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val acting = live.seats.firstOrNull { it.active }
        if (acting != null) {
            Text(
                text = "· 等 ${acting.seat} 号",
                fontSize = 12.sp,
                color = if (night) Color(0xFF8FB0FF) else SelfBlue,
                maxLines = 1
            )
        }
    }
}


/**
 * 思考气泡：漫画对白框，挂在说话人头顶。
 *
 * 为什么浮在头像上而不是塞进对话流：
 * 深度思考动辄几百上千字，全塞进对话流能把整页拉长到没法看；
 * 而完全不显示又让人干等——不知道是在想还是在卡住。
 * 挂在头顶正好：看得见，又不占对话流的篇幅，说完就消失。
 *
 * 内容只给**最后几行**：思考是流式追加的，头部是最早的结论，
 * 尾部才是「他此刻在想什么」。
 */
@Composable
private fun ThinkingBubble(
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
            .takeLast(3)
    }
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }

    // 半透明：气泡会盖住一小块牌桌，压暗一点才不至于挡住下面的发言
    val fill = if (night) Color(0xD9221D2E) else Color(0xE6FFFFFF)
    val border = if (night) Color(0x59A894FF) else Color(0x336B4BD9)
    val textColor = if (night) Color(0xFFE6EAF3) else Color(0xFF3A4152)

    Box(
        modifier = Modifier
            // **不能**用 fillMaxSize：offset 只挪位置、不改尺寸，
            // 气泡会被撑成全屏大，把整个牌桌盖住。
            // 尺寸由下面的 width 定死，offset 负责把它搬到头像上方。
            .offset {
                // 往**屏幕内侧**展开：座位列在两边，气泡朝中间长，
                // 盖住的是对话流的一小条，而不是同列的其他人的头像。
                // 尖角始终在气泡靠头像的那一端（尖角自己按 anchor 定位）。
                val onLeft = anchor.x < screenWidth.value / 2f
                val edge = if (onLeft) {
                    anchor.x - with(density) { 4.dp.toPx() }
                } else {
                    anchor.x + with(density) { 4.dp.toPx() } - bubbleSize.width
                }
                val x = edge.coerceIn(8f, (screenWidth.value - bubbleSize.width - 8f).coerceAtLeast(8f))
                // 头顶放不下就放到身下：漫画对白框可以斜着、也可以在身侧，
                // 但绝不能盖住说话人自己的脸，也不能盖住顶栏。
                val gap = with(density) { 26.dp.toPx() }
                val above = anchor.y - bubbleSize.height - gap
                val minTop = with(density) { MIN_BUBBLE_TOP.toPx() }
                val y = if (above >= minTop) {
                    above
                } else {
                    (anchor.y + gap).coerceAtMost(
                        with(density) { screenHeight.value.dp.toPx() } - bubbleSize.height - 8f
                    )
                }
                IntOffset(x.toInt(), y.toInt())
            }
            .onSizeChanged { bubbleSize = it }
            .width(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp)
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
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 对白框下面那个小尖角：指着说话人的头像。
 *
 * 用 Canvas 画一个等腰三角形，比用旋转的方块稳——方块转 45 度之后
 * 四个角会露出来，看着像个菱形贴片。
 */
@Composable
private fun BubbleTail(
    anchor: Offset,
    night: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val border = if (night) Color(0x59A894FF) else Color(0x336B4BD9)
    Canvas(
        modifier = modifier
            .offset {
                val y = (anchor.y - with(density) { 26.dp.toPx() })
                IntOffset(
                    (anchor.x - with(density) { 6.dp.toPx() }).toInt(),
                    y.toInt()
                )
            }
            .size(12.dp, 7.dp)
    ) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        }
        drawPath(path, color = if (night) Color(0xD9221D2E) else Color(0xE6FFFFFF))
        drawPath(
            path,
            color = border,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        )
    }
}

// ---------------------------------------------------------------------------
// 记录流
// ---------------------------------------------------------------------------

/**
 * 记录流。
 *
 * 三种东西混在一条时间线里，但排版必须分开，否则读不出层次：
 * 上帝播报是**事实**（居中、灰、无头像），公开发言是**某个人说的话**（带头像），
 * 私有信息是**只有你知道的事**（描边强调）。第三种如果和普通发言长得一样，
 * 玩家会以为别人也听见了——那是这局最致命的一种误读。
 */
@Composable
private fun Feed(
    live: UIWerewolfLive,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    surface: Color,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // 轮到某位 AI 时：
    // - 他已经在开口，就让半句话本身占着末尾（StreamingLine）；
    // - 还没有任何输出（投票、夜间决策这种返回 JSON 的步骤），退回三个点。
    // 别看三个点不起眼：一轮八个人要等一分多钟，中间没有任何东西在动的话，
    // 界面看着就是卡死的
    //
    // 两种「正在发生」都必须等喇叭空下来（!speechBusy）：念 A 的时候引擎已经在生成 B 了，
    // 这时冒出 B 的名字或半句话，就是用户说的「下一个人对话框弹出来又消失」。
    val streaming = if (live.running && live.turn == null && !live.speechBusy) live.streaming else null
    val thinkingSeat = if (live.running && live.turn == null && streaming == null && !live.speechBusy) {
        live.seats.firstOrNull { it.active }
    } else {
        null
    }
    // 长发言会超出一屏，光靠「开始说话」滚一次不够。按字数分段滚：
    // 每 60 字贴一次底，既跟得上，又不会每个增量都触发一次滚动
    val streamBucket = (streaming?.text?.length ?: 0) / STREAM_SCROLL_CHARS
    LaunchedEffect(live.lines.size, thinkingSeat?.seat, streaming?.seat, streamBucket) {
        val extra = if (thinkingSeat != null || streaming != null) 1 else 0
        val last = live.lines.lastIndex + extra
        if (last >= 0) {
            // 贴底用瞬时滚动：流式每几秒就来一次，动画排起队来反而永远追不上
            listState.scrollToItem(last)
        }
    }

    if (live.lines.isEmpty() && thinkingSeat == null && streaming == null) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "还没开始，\n等第一位开口",
                modifier = Modifier.padding(horizontal = 10.dp),
                fontSize = FsName,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                color = dimColor
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(live.lines, key = { it.index }) { line ->
            LineRow(
                line = line,
                night = night,
                textColor = textColor,
                dimColor = dimColor,
                surface = surface
            )
        }
        if (streaming != null) {
            item(key = "streaming") {
                StreamingLine(
                    stream = streaming,
                    night = night,
                    textColor = textColor,
                    dimColor = dimColor
                )
            }
        } else if (thinkingSeat != null) {
            item(key = "thinking") {
                ThinkingLine(seat = thinkingSeat, dimColor = dimColor)
            }
        }
    }
}

/**
 * 「某人正在说」的一行，逐字长出来。
 *
 * 长得和一条普通发言一模一样（头像 + 名字 + 正文），只多了末尾一个光标、语气淡一点。
 * 刻意不做卡片、不做边框——等这句话说完，它会变成上面那条普通记录，两者长得一样才不会「跳一下」。
 */
@Composable
private fun StreamingLine(
    stream: UIWerewolfStreaming,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        UIAvatar(
            name = stream.speaker ?: "?",
            avatarPath = stream.avatarPath,
            size = 30.dp,
            shape = CircleShape
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stream.speaker ?: "未知",
                    fontSize = FsName,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = " ${stream.seat} 号",
                    fontSize = 12.sp,
                    color = dimColor,
                    maxLines = 1
                )
            }
            if (stream.text.isNotBlank()) {
                Text(
                    text = stream.text + "▌",
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = FsSpeech,
                    lineHeight = FsSpeechLine,
                    color = textColor.copy(alpha = 0.92f)
                )
            } else {
                // 还在思考、一个字没说：只显示「正在组织语言」，不把思考流放出来——
                // 对局里看 AI 推理会把「读心」变成主要玩法，思考留给复盘
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "正在组织语言", fontSize = FsName, color = dimColor)
                    Spacer(Modifier.width(6.dp))
                    ThinkingDot(color = dimColor)
                }
            }
        }
    }
}

/**
 * 「某人正在想」的一行。
 *
 * 刻意长得像一条普通发言的头（头像 + 座位号），而不是一个居中的加载圈：
 * 它是记录流的最后一项，用户往下看的时候，视线自然接在那个人身上。
 */
@Composable
private fun ThinkingLine(seat: UIWerewolfSeatLive, dimColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UIAvatar(
            name = seat.name,
            avatarPath = seat.avatarPath,
            size = 26.dp,
            shape = CircleShape,
            modifier = Modifier.alpha(0.7f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${seat.seat} 号正在组织语言",
            fontSize = FsName,
            color = dimColor
        )
        Spacer(Modifier.width(6.dp))
        ThinkingDot(color = dimColor)
    }
}

@Composable
private fun LineRow(
    line: UIWerewolfLine,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    surface: Color,
) {
    when (line.kind) {
        UIWerewolfLineKind.DIVINE -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = line.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (night) Color(0x14FFFFFF) else Color(0x0D000000))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = FsName,
                color = dimColor,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
        }

        UIWerewolfLineKind.INNER -> InnerRow(
            line = line,
            night = night,
            dimColor = dimColor
        )

        UIWerewolfLineKind.SPEECH -> SpeechRow(
            line = line,
            night = night,
            textColor = textColor,
            dimColor = dimColor
        )

        UIWerewolfLineKind.PRIVATE -> PrivateRow(
            line = line,
            night = night,
            textColor = textColor,
            dimColor = dimColor,
            surface = surface
        )
    }
}

@Composable
private fun SpeechRow(
    line: UIWerewolfLine,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    val style = bubbleStyle(UIWerewolfBubbleKind.SPEECH, night)
    Row(modifier = Modifier.fillMaxWidth()) {
        UIAvatar(
            name = line.speaker ?: "?",
            avatarPath = line.avatarPath,
            size = 30.dp,
            shape = CircleShape
        )
        Spacer(Modifier.width(8.dp))
        UIWerewolfBubble(style = style, modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = line.speaker ?: "未知",
                    fontSize = FsName,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                line.seat?.let {
                    Text(
                        text = " $it 号",
                        fontSize = 12.sp,
                        color = dimColor,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(6.dp))
                BubbleTag(text = style.tag, accent = style.accent)
            }
            Text(
                text = line.text,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = FsSpeech,
                lineHeight = FsSpeechLine,
                color = textColor.copy(alpha = 0.92f)
            )
        }
    }
}

/**
 * 内心活动：投票的理由、夜里查验与用药的念头。
 *
 * 气泡，但**刻意做得比发言更淡更小、没有头像**：这些字没有人听见，
 * 做成发言的样子，用户会以为「他刚才在桌上说了这句」。
 * 朗读也一并关掉了（`MessageKind.isInnerThought`），否则一圈投票就要念十几段独白。
 */
@Composable
private fun InnerRow(
    line: UIWerewolfLine,
    night: Boolean,
    dimColor: Color,
) {
    val style = bubbleStyle(UIWerewolfBubbleKind.INNER, night)
    UIWerewolfBubble(
        style = style,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 38.dp, end = 6.dp),
        corner = 8.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BubbleTag(text = line.innerLabel ?: style.tag, accent = style.accent)
            val who = listOfNotNull(line.seat?.let { "$it 号" }, line.speaker)
                .joinToString(" ")
            if (who.isNotBlank()) {
                Text(
                    text = " $who",
                    fontSize = FsMeta,
                    color = dimColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (line.secret) {
                Text(
                    text = " · 仅你可见",
                    fontSize = FsMeta,
                    color = dimColor.copy(alpha = 0.55f)
                )
            }
        }
        Text(
            text = line.text,
            modifier = Modifier.padding(top = 2.dp),
            fontSize = FsName,
            lineHeight = 18.sp,
            color = dimColor
        )
    }
}

/** 只有这个视角看得见的消息。描边 + 「仅你可见」，怕的就是玩家误以为别人也听见了。 */
@Composable
private fun PrivateRow(
    line: UIWerewolfLine,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    surface: Color,
) {
    val style = bubbleStyle(UIWerewolfBubbleKind.SECRET, night)
    UIWerewolfBubble(
        style = style,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BubbleTag(text = style.tag, accent = style.accent)
            val who = listOfNotNull(line.seat?.let { "$it 号" }, line.speaker)
                .joinToString(" ")
            if (who.isNotBlank()) {
                Text(
                    text = " · $who",
                    fontSize = FsMeta,
                    color = dimColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "仅你可见",
                fontSize = FsMeta,
                color = dimColor.copy(alpha = 0.6f)
            )
        }
        Text(
            text = line.text,
            modifier = Modifier.padding(top = 3.dp),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = textColor.copy(alpha = 0.9f)
        )
    }
}

// ---------------------------------------------------------------------------
// 输入区
// ---------------------------------------------------------------------------

/**
 * 轮到你了。
 *
 * 判断「现在该做什么」只看两件事：`phase` 和**本人身份**。不去解析提示词里写了什么——
 * 提示词是给人设和规则用的，随时会改，拿它做界面分支等于把文案当协议。
 */
@Composable
private fun TurnPanel(
    turn: UIWerewolfTurn,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    surface: Color,
    onSpeech: (String) -> Unit,
    onVote: (Int?) -> Unit,
    onSeat: (Int) -> Unit,
    onWitch: (Boolean, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = turn.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            turn.hint?.let {
                Text(
                    text = "  $it",
                    fontSize = FsName,
                    color = dimColor
                )
            }
        }

        when (turn.kind) {
            UIWerewolfTurnKind.SPEECH -> SpeechInput(
                textColor = textColor,
                dimColor = dimColor,
                onSubmit = onSpeech
            )

            UIWerewolfTurnKind.VOTE -> TargetRow(
                targets = turn.targets,
                textColor = textColor,
                dimColor = dimColor,
                onPick = onVote,
                // 上警报名也走这个面板，但它的「不做」是「不上警」不是「弃票」。
                // 写死「弃票」的话，玩家会以为自己点的是放弃投票
                extraLabel = if (turn.canAbstain) turn.abstainLabel ?: "弃票" else null,
                onExtra = { onVote(null) }
            )

            UIWerewolfTurnKind.SEAT -> TargetRow(
                targets = turn.targets,
                textColor = textColor,
                dimColor = dimColor,
                onPick = { onSeat(it) },
                extraLabel = if (turn.canAbstain) turn.abstainLabel ?: "不做" else null,
                onExtra = { onVote(null) }
            )

            UIWerewolfTurnKind.WITCH -> WitchPanel(
                turn = turn,
                textColor = textColor,
                dimColor = dimColor,
                onWitch = onWitch
            )
        }
    }
}

@Composable
private fun SpeechInput(
    textColor: Color,
    dimColor: Color,
    onSubmit: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(UIRadiusControl))
                .background(UIFieldFill)
                .padding(horizontal = 13.dp, vertical = 11.dp)
        ) {
            if (text.isEmpty()) {
                Text(
                    text = "说点什么，其他人会据此投票",
                    fontSize = 13.5.sp,
                    color = dimColor
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 15.sp, color = textColor),
                maxLines = 4
            )
        }
        Spacer(Modifier.width(8.dp))
        val enabled = text.isNotBlank()
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(UIRadiusControl))
                .background(if (enabled) UIActionBrush else Brush.linearGradient(listOf(
                    UIFieldFill, UIFieldFill
                )))
                .clickable(enabled = enabled) {
                    onSubmit(text.trim())
                    text = ""
                }
                .padding(horizontal = 18.dp, vertical = 11.dp)
        ) {
            Text(
                text = "发言",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.White else dimColor
            )
        }
    }
}

@Composable
private fun TargetRow(
    targets: List<UIWerewolfTarget>,
    textColor: Color,
    dimColor: Color,
    onPick: (Int) -> Unit,
    extraLabel: String? = null,
    onExtra: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        targets.forEach { target ->
            val clickable = target.selectable
            Text(
                text = "${target.seat} ${target.name}",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (clickable) UIFieldFill else Color.Transparent)
                    .border(
                        1.dp,
                        if (clickable) Color(0x142B6BFF) else Color(0x0F000000),
                        RoundedCornerShape(50)
                    )
                    .clickable(enabled = clickable) { onPick(target.seat) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                fontSize = FsAction,
                color = if (clickable) textColor else dimColor.copy(alpha = 0.5f),
                maxLines = 1
            )
        }
        if (extraLabel != null && onExtra != null) {
            Text(
                text = extraLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x0F000000))
                    .clickable { onExtra() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                fontSize = FsAction,
                color = dimColor
            )
        }
    }
}

/**
 * 女巫面板。
 *
 * 用药是这一局唯一「一次选两件事」的操作（用不用、对谁用），所以先给三个动作，
 * 选「毒」再展开目标。直接铺一屏人让人挑，会分不清自己在选解药还是毒药。
 */
@Composable
private fun WitchPanel(
    turn: UIWerewolfTurn,
    textColor: Color,
    dimColor: Color,
    onWitch: (Boolean, Int?) -> Unit,
) {
    var poisoning by rememberSaveable { mutableStateOf(false) }

    if (poisoning) {
        TargetRow(
            targets = turn.targets,
            textColor = textColor,
            dimColor = dimColor,
            onPick = { seat -> onWitch(false, seat) },
            extraLabel = "算了",
            onExtra = { poisoning = false }
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (turn.canHeal) {
            Text(
                text = "用解药救 ${turn.healTarget ?: "他"}",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(UIActionBrush)
                    .clickable { onWitch(true, null) }
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                fontSize = FsAction,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        if (turn.canPoison) {
            Text(
                text = "用毒药",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(UIFieldFill)
                    .clickable { poisoning = true }
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                fontSize = FsAction,
                color = textColor
            )
        }
        Text(
            text = "这晚不用药",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(UIFieldFill)
                .clickable { onWitch(false, null) }
                .padding(horizontal = 15.dp, vertical = 9.dp),
            fontSize = FsAction,
            color = dimColor
        )
    }
}

/**
 * 结束后的底部条。
 *
 * 只放一个动作：看复盘。不做「再来一局」——刚被投出去的人未必想立刻重开，
 * 而重开的入口在配置页，从复盘页回去比在这里塞两个按钮清楚。
 */
@Composable
private fun ReviewBar(
    surface: Color,
    textColor: Color,
    dimColor: Color,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(surface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = "全部身份已公开 · 这一局已经存档",
            fontSize = FsMeta,
            color = dimColor
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(UIRadiusControl))
                .background(UIActionBrush)
                .clickable { onReview() }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "看复盘",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

/** 出错条：局面还在，只是这一步没走通。不要做成弹窗，弹窗会挡住牌桌。 */
@Composable
private fun ErrorStrip(message: String, night: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .background(
                UIFriendBarRed.copy(alpha = if (night) 0.24f else 0.08f),
                RoundedCornerShape(UIRadiusControl)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = "$message\n这一局的进度已经存档，下次进来可以接着打。",
            fontSize = FsName,
            lineHeight = 19.sp,
            color = if (night) Color(0xFFFFB4B0) else UIFriendBarRed
        )
    }
}

// ---------------------------------------------------------------------------
// 昼夜过场
//
// 原来只是整页颜色渐变：上一秒还是浅蓝，一秒后全黑，中间的 650ms 里
// 界面既不像白天也不像夜晚，看着像卡了一下。加一段天体升落的过场，
// 变化就有了「原因」——太阳落下去、月亮升起来，而不是屏幕自己变黑。
//
// 长度定在 1.4 秒：短于这个数看不清月亮是月牙；长于这个数，
// 每轮都要等一次，十几轮下来非常烦。
// ---------------------------------------------------------------------------

private const val DAY_NIGHT_MS = 1400

private val SunCore = Color(0xFFFFC94D)
private val SunGlow = Color(0xFFFFB020)
private val MoonCore = Color(0xFFF3EAD3)
private val MoonGlow = Color(0xFFCBD6F5)

/**
 * 星星的位置：横向比例、纵向比例、半径（dp）。
 *
 * 写死不随机：随机会让每次入夜的星空都不一样，虽然不是错误，
 * 但同一局里反复出现的「一张熟悉的星空」更像一个地方。
 */
private val STARS = listOf(
    Triple(0.10f, 0.12f, 1.6f), Triple(0.22f, 0.07f, 1.1f),
    Triple(0.34f, 0.16f, 1.9f), Triple(0.46f, 0.06f, 1.0f),
    Triple(0.58f, 0.14f, 1.5f), Triple(0.70f, 0.08f, 1.2f),
    Triple(0.82f, 0.17f, 2.0f), Triple(0.92f, 0.10f, 1.1f),
    Triple(0.16f, 0.24f, 1.0f), Triple(0.40f, 0.27f, 1.3f),
    Triple(0.66f, 0.25f, 1.0f), Triple(0.88f, 0.30f, 1.4f),
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
private fun DayNightTransition(
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

/** 星星的横向/纵向比例与半径（dp）。黄金比取模排布，比随机均匀，且每次一致。 */
private val NIGHT_STARS: List<Triple<Float, Float, Float>> = List(26) { i ->
    val fx = (0.05f + i * 0.6180339887f) % 1f
    val fy = ((0.03f + i * 0.3819660113f) % 1f) * 0.58f
    Triple(fx, fy, 0.9f + (i % 4) * 0.35f)
}

/** 薄云：横向比例、纵向比例、大小比例。 */
private val DAY_CLOUDS = listOf(
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
private val VILLAGE_HOUSES = listOf(
    floatArrayOf(0.075f, 0.070f, 0.052f),
    floatArrayOf(0.155f, 0.052f, 0.040f),
    floatArrayOf(0.245f, 0.060f, 0.046f),
    floatArrayOf(0.760f, 0.058f, 0.044f),
    floatArrayOf(0.845f, 0.072f, 0.056f),
    floatArrayOf(0.925f, 0.050f, 0.038f),
)

/**
 * 牌桌背景。
 *
 * [dayness] 1 = 白天，0 = 夜晚，中间的连续值用于过场：两层同时画，
 * 各自乘自己的权重，所以天色是慢慢退成夜色的，不是切一张图。
 */
@Composable
private fun TableBackdrop(dayness: Float, modifier: Modifier = Modifier) {
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
private fun DrawScope.drawDaySky(w: Float, h: Float, a: Float) {
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
private fun DrawScope.drawNightSky(w: Float, h: Float, a: Float, phase: Float) {
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
private fun ridgeFar(w: Float, h: Float): Path = Path().apply {
    moveTo(0f, h * 0.760f)
    quadraticTo(w * 0.16f, h * 0.700f, w * 0.33f, h * 0.744f)
    quadraticTo(w * 0.50f, h * 0.786f, w * 0.66f, h * 0.728f)
    quadraticTo(w * 0.83f, h * 0.684f, w, h * 0.752f)
    lineTo(w, h)
    lineTo(0f, h)
    close()
}

/** 近处的一道缓坡，山脚那几户人家站在它上面。 */
private fun ridgeNear(w: Float, h: Float): Path = Path().apply {
    moveTo(0f, h * 0.872f)
    quadraticTo(w * 0.23f, h * 0.818f, w * 0.46f, h * 0.864f)
    quadraticTo(w * 0.71f, h * 0.912f, w, h * 0.846f)
    lineTo(w, h)
    lineTo(0f, h)
    close()
}

/** 屋顶天际线：墙 + 出檐的三角屋顶，夜里再点一盏窗灯。 */
private fun DrawScope.drawVillage(w: Float, h: Float, color: Color, windowColor: Color?) {
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
