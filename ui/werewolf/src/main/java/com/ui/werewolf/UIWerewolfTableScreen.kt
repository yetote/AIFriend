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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.ui.werewolf.data.UIWerewolfLineKind
import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfSeatLive
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

// ---------------------------------------------------------------------------
// 配色
//
// 狼人杀的「夜」是这个游戏最重要的一层信息：夜里谁能行动、谁在闭眼，
// 决定了玩家能不能读懂第二天谁在说谎。所以到夜里整页换暗色，但不换页——
// 换页会把牌桌的连续感切碎，也让人以为进了另一个地方。
//
// 暗色只压在牌桌和记录上，顶栏和输入区跟着一起变，避免出现「上半截夜里、下半截白天」。
// ---------------------------------------------------------------------------

private val NightBackdrop = Color(0xFF101725)
private val NightBackdropDeep = Color(0xFF0B111B)
private val NightText = Color(0xFFE9EEF9)
private val NightDim = Color(0xFF97A3BC)
private val NightSurface = Color(0xFF1B2437)
private val NightStroke = Color(0x1FFFFFFF)

private val DayBackdrop = Color(0xFFF3F6FC)
private val DaySurface = Color(0xFFFDFDFF)

/** 自己：座位环上的描边、身份胶囊。 */
private val SelfBlue = Color(0xFF2B6BFF)

/** 出局：一律降饱和，再叠一个原因字。颜色 + 字双编码，色弱也分得清「刀」和「票」。 */
private val FateRed = Color(0xFFD9534F)
private val VoteRed = Color(0xFFE0564F)

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
) {
    val night = live.night && !live.finished
    val backdrop by animateColorAsState(
        targetValue = if (night) NightBackdrop else DayBackdrop,
        animationSpec = tween(DAY_NIGHT_MS / 2),
        label = "backdrop"
    )
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (night) {
                    Brush.verticalGradient(listOf(NightBackdropDeep, NightBackdrop))
                } else {
                    Brush.verticalGradient(listOf(DayBackdrop, DayBackdrop))
                }
            )
            .pageInsetsTop()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TableTopBar(
                title = if (live.finished) "对局结束" else if (night) "第 ${live.day} 夜" else "第 ${live.day} 天",
                subtitle = live.progressLabel,
                night = night,
                textColor = textColor,
                dimColor = dimColor,
                spectating = live.spectating,
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
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
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
                fontSize = 12.sp,
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
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        live.selfRoleLabel?.let { role ->
            Text(
                text = role,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
        live.selfFactionLabel?.let { faction ->
            Text(
                text = faction,
                fontSize = 11.sp,
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
        fontSize = 11.sp,
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
            modifier = Modifier.width(COLUMN_WIDTH).fillMaxHeight()
        )
    }
}

/** 一列侧栏的宽度。62dp 放得下 4 个汉字的名字，再多中间就不够说话了。 */
private val COLUMN_WIDTH = 62.dp

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
                    dimColor = dimColor
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
 */
@Composable
private fun SeatTile(
    seat: UIWerewolfSeatLive,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    Column(
        modifier = Modifier.width(COLUMN_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 12 人局一列要放 6 个，一格里每一像素都在抢高度：头像 30 是认得出人的下限
        Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            if (seat.active) {
                // 正在发言/正在行动的人要有动静，静止的牌桌看不出轮到谁
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                size = 30.dp,
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
                        .size(15.dp)
                        .background(VoteRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = votes.toString(),
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
            fontSize = 9.sp,
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
                    .padding(horizontal = 4.dp, vertical = 0.5.dp),
                fontSize = 8.sp,
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
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1
            )
            return@Row
        }

        Text(
            text = if (night) "第 ${live.day} 夜" else "第 ${live.day} 天",
            fontSize = 10.sp,
            color = dimColor
        )
        Text(
            text = live.phaseLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val acting = live.seats.firstOrNull { it.active }
        if (acting != null) {
            Text(
                text = "· 等 ${acting.seat} 号",
                fontSize = 10.sp,
                color = if (night) Color(0xFF8FB0FF) else SelfBlue,
                maxLines = 1
            )
        }
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
    // 轮到某位 AI 时在末尾挂一条「正在组织语言」。别看只是三个点：
    // 一轮八个人要等一分多钟，中间没有任何东西在动的话，界面看着就是卡死的
    val thinkingSeat = if (live.running && live.turn == null) {
        live.seats.firstOrNull { it.active }
    } else {
        null
    }
    LaunchedEffect(live.lines.size, thinkingSeat?.seat) {
        val last = live.lines.lastIndex + if (thinkingSeat != null) 1 else 0
        if (last >= 0) {
            // 新消息贴底：中途抬头看「刚刚谁说了什么」必须落在最后一条
            listState.animateScrollToItem(last)
        }
    }

    if (live.lines.isEmpty() && thinkingSeat == null) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "还没开始，\n等第一位开口",
                modifier = Modifier.padding(horizontal = 10.dp),
                fontSize = 11.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                color = dimColor
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
        if (thinkingSeat != null) {
            item(key = "thinking") {
                ThinkingLine(seat = thinkingSeat, dimColor = dimColor)
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
            size = 22.dp,
            shape = CircleShape,
            modifier = Modifier.alpha(0.7f)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = "${seat.seat} 号正在组织语言",
            fontSize = 11.sp,
            color = dimColor
        )
        Spacer(Modifier.width(5.dp))
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
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                fontSize = 10.sp,
                color = dimColor,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center
            )
        }

        UIWerewolfLineKind.VOTE -> Text(
            text = buildString {
                line.seat?.let { append("$it 号") }
                line.seat?.let { append(" " ) }
                append(line.text)
            },
            modifier = Modifier.padding(start = 34.dp),
            fontSize = 10.sp,
            color = dimColor
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
    Row(modifier = Modifier.fillMaxWidth()) {
        UIAvatar(
            name = line.speaker ?: "?",
            avatarPath = line.avatarPath,
            size = 26.dp,
            shape = CircleShape
        )
        Spacer(Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = line.speaker ?: "未知",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                line.seat?.let {
                    Text(
                        text = " $it 号",
                        fontSize = 9.sp,
                        color = dimColor,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = line.text,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = textColor.copy(alpha = 0.92f)
            )
        }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp)
            .uiCard(
                shape = RoundedCornerShape(UIRadiusControl),
                color = if (night) Color(0xFF221D2E) else Color(0xFFFBF7EC),
                borderColor = if (night) Color(0x33C8A24A) else Color(0x33B58B2A),
                elevation = 0.dp
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "仅你可见",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (night) Color(0xFFD9B563) else Color(0xFF9A7420)
            )
            val who = listOfNotNull(line.seat?.let { "$it 号" }, line.speaker)
                .joinToString(" ")
            if (who.isNotBlank()) {
                Text(
                    text = " · $who",
                    fontSize = 9.sp,
                    color = dimColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = line.text,
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 11.sp,
            lineHeight = 17.sp,
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
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            turn.hint?.let {
                Text(
                    text = "  $it",
                    fontSize = 11.sp,
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
                extraLabel = if (turn.canAbstain) "弃票" else null,
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
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (text.isEmpty()) {
                Text(
                    text = "说点什么，其他人会据此投票",
                    fontSize = 12.sp,
                    color = dimColor
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 13.sp, color = textColor),
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
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "发言",
                fontSize = 13.sp,
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
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                fontSize = 12.sp,
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
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                fontSize = 12.sp,
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
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                fontSize = 12.sp,
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
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                fontSize = 12.sp,
                color = textColor
            )
        }
        Text(
            text = "这晚不用药",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(UIFieldFill)
                .clickable { onWitch(false, null) }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 12.sp,
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
            fontSize = 10.sp,
            color = dimColor
        )
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(UIRadiusControl))
                .background(UIActionBrush)
                .clickable { onReview() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "看复盘",
                fontSize = 14.sp,
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
            fontSize = 11.sp,
            lineHeight = 16.sp,
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
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            // 拉开字距，像一句宣告而不是一条提示
            letterSpacing = 4.sp,
            color = if (showingNight) Color(0xFFDCE4F5) else Color(0xFF8A6A2F)
        )
    }
}
