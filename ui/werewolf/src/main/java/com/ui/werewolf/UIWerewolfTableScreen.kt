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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
 * 我是谁（顶栏下一行）→ 场上有谁（座位环）→ 刚刚发生了什么（记录流）→ 现在要我做什么（底部）。
 * 没有 tab、没有二级页：狼人杀的信息是连续的，切走再切回来就读不懂局势了。
 */
@Composable
fun UIWerewolfTableScreen(
    live: UIWerewolfLive,
    onExit: () -> Unit,
    onSpeech: (String) -> Unit = {},
    onVote: (Int?) -> Unit = {},
    onSeat: (Int) -> Unit = {},
    onWitch: (heal: Boolean, poison: Int?) -> Unit = { _, _ -> },
) {
    val night = live.night && !live.finished
    val backdrop by animateColorAsState(
        targetValue = if (night) NightBackdrop else DayBackdrop,
        animationSpec = tween(650),
        label = "backdrop"
    )
    val textColor by animateColorAsState(
        targetValue = if (night) NightText else UIFriendBarTitleColor,
        animationSpec = tween(650),
        label = "text"
    )
    val dimColor by animateColorAsState(
        targetValue = if (night) NightDim else UIFriendBarItemSubtitleColor,
        animationSpec = tween(650),
        label = "dim"
    )
    val surface by animateColorAsState(
        targetValue = if (night) NightSurface else DaySurface,
        animationSpec = tween(650),
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

            TableStage(
                live = live,
                night = night,
                textColor = textColor,
                dimColor = dimColor
            )

            if (live.error != null) {
                ErrorStrip(message = live.error, night = night)
            }

            Feed(
                live = live,
                night = night,
                textColor = textColor,
                dimColor = dimColor,
                surface = surface,
                modifier = Modifier.weight(1f)
            )

            val turn = live.turn
            if (turn != null && !live.finished) {
                TurnPanel(
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
            } else {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.pageInsetsBottom())
            }
        }
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
 * 座位环。
 *
 * 座位按椭圆排布而不是列表：狼人杀的空间关系本身就是信息——谁是邻座、
 * 谁发言在你前面，都在座次上。做成列表就把这层信息丢了。
 *
 * 中央留白给「第几天、什么阶段、正在等谁」，那是等待几十秒时唯一能看的东西。
 */
@Composable
private fun TableStage(
    live: UIWerewolfLive,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(284.dp)
            .padding(horizontal = 6.dp)
    ) {
        // 桌面：一块比座位环略小的椭圆，白天是白瓷，夜里是深蓝绒布
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.82f)
                .fillMaxHeight(0.76f)
                .clip(CircleShape)
                .background(if (night) Color(0xFF1A2334) else Color(0xFFEDF1FA))
        )

        val seats = live.seats
        if (seats.isNotEmpty()) {
            val rx = maxWidth / 2 - 30.dp
            val ry = maxHeight / 2 - 34.dp
            val cx = maxWidth / 2
            val cy = maxHeight / 2
            val step = 2 * PI / seats.size
            seats.forEachIndexed { index, seat ->
                // 从正上方开始顺时针排：1 号在最上，和线下围桌的习惯一致
                val angle = -PI / 2 + step * index
                val x = cx + rx * cos(angle).toFloat()
                val y = cy + ry * sin(angle).toFloat()
                SeatChip(
                    seat = seat,
                    night = night,
                    dimColor = dimColor,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = x - 30.dp, y = y - 32.dp)
                )
            }
        }

        CenterStatus(
            live = live,
            night = night,
            textColor = textColor,
            dimColor = dimColor,
            modifier = Modifier
                .align(Alignment.Center)
                .width(168.dp)
        )
    }
}

/**
 * 一枚座位。
 *
 * 出局的人不删、不灰到底、也不移出环：狼人杀的「谁死了」是和「谁还活着」一样重要的信息，
 * 移走之后就没法回头对照「他生前说了什么」。所以留着、压暗、再挂一个字说明怎么死的。
 */
@Composable
private fun SeatChip(
    seat: UIWerewolfSeatLive,
    night: Boolean,
    dimColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (seat.active) {
                val transition = rememberInfiniteTransition(label = "active")
                val scale by transition.animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                Box(
                    modifier = Modifier
                        .size(46.dp)
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
                size = 36.dp,
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
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        Text(
            text = seat.name,
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = when {
                !seat.alive -> dimColor.copy(alpha = 0.65f)
                seat.isSelf -> if (night) NightText else SelfBlue
                else -> if (night) NightText else UIFriendBarTitleColor
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

/** 桌面中央：第几天、什么阶段、正在等谁。 */
@Composable
private fun CenterStatus(
    live: UIWerewolfLive,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (live.finished) {
            Text(
                text = live.winnerLabel ?: "对局结束",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Text(
                text = "全部身份已公开",
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 11.sp,
                color = dimColor
            )
            return@Column
        }

        Text(
            text = if (night) "第 ${live.day} 夜" else "第 ${live.day} 天",
            fontSize = 11.sp,
            color = dimColor
        )
        Text(
            text = live.phaseLabel,
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1
        )
        val acting = live.seats.firstOrNull { it.active }
        if (acting != null) {
            Text(
                text = "正在等 ${acting.seat} 号",
                modifier = Modifier.padding(top = 3.dp),
                fontSize = 11.sp,
                color = if (night) Color(0xFF8FB0FF) else SelfBlue
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
    LaunchedEffect(live.lines.size) {
        if (live.lines.isNotEmpty()) {
            // 新消息贴底：中途抬头看「刚刚谁说了什么」必须落在最后一条
            listState.animateScrollToItem(live.lines.lastIndex)
        }
    }

    if (live.lines.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "还没开始，等第一位开口",
                fontSize = 12.sp,
                color = dimColor
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
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
                fontSize = 11.sp,
                color = dimColor,
                lineHeight = 16.sp
            )
        }

        UIWerewolfLineKind.VOTE -> Text(
            text = buildString {
                line.seat?.let { append("$it 号") }
                line.seat?.let { append(" " ) }
                append(line.text)
            },
            modifier = Modifier.padding(start = 40.dp),
            fontSize = 11.sp,
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
            size = 30.dp,
            shape = CircleShape
        )
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = line.speaker ?: "未知",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
                line.seat?.let {
                    Text(
                        text = " $it 号",
                        fontSize = 10.sp,
                        color = dimColor
                    )
                }
            }
            Text(
                text = line.text,
                modifier = Modifier.padding(top = 3.dp),
                fontSize = 13.sp,
                lineHeight = 20.sp,
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
            .padding(start = 20.dp)
            .uiCard(
                shape = RoundedCornerShape(UIRadiusControl),
                color = if (night) Color(0xFF221D2E) else Color(0xFFFBF7EC),
                borderColor = if (night) Color(0x33C8A24A) else Color(0x33B58B2A),
                elevation = 0.dp
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "仅你可见",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (night) Color(0xFFD9B563) else Color(0xFF9A7420)
            )
            val who = listOfNotNull(line.seat?.let { "$it 号" }, line.speaker)
                .joinToString(" ")
            if (who.isNotBlank()) {
                Text(
                    text = " · $who",
                    fontSize = 10.sp,
                    color = dimColor
                )
            }
        }
        Text(
            text = line.text,
            modifier = Modifier.padding(top = 3.dp),
            fontSize = 12.sp,
            lineHeight = 18.sp,
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
                extraLabel = if (turn.canAbstain) "不开枪" else null,
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
