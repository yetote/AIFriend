package com.ui.werewolf

// 座位环：左右两列头像、警徽/票数/狼标、发言高亮。

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.UIAvatar
import com.ui.common.theme.UIFieldFill
import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfPlayerMark
import com.ui.werewolf.data.UIWerewolfSeatLive
import com.ui.werewolf.data.UIWerewolfVerdict
import com.ui.werewolf.data.shortModelLabel
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Offset

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
internal fun SeatColumns(
    live: UIWerewolfLive,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    surface: Color,
    /** 座位头像在窗口里的位置。思考气泡要挂在它头顶，得知道它在哪。 */
    anchors: MutableMap<Int, Offset>,
    /** 游玩模式下玩家手动给座位贴的私密记号；上帝观战不展示。 */
    manualMarks: Map<Int, UIWerewolfPlayerMark> = emptyMap(),
    /** 点击座位头像查看该座位档案与历史发言。 */
    onSeatClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 座位号升序排一次再分栏。引擎给的是按座位排好的，但这里不依赖那个隐式约定
    val ordered = remember(live.seats) { live.seats.sortedBy { it.seat } }
    // 左列先排满再排右列：ceil 而不是 size/2，奇数人数多出来的那个留在左边，
    // 这样「1 号在左上、最后一位在右下」，顺序上没有断口
    val half = (ordered.size + 1) / 2
    val left = remember(ordered) { ordered.take(half) }
    val right = remember(ordered) { ordered.drop(half) }
    val activeMarks = if (!live.spectating && !live.finished && live.replay == null) manualMarks else emptyMap()

    Row(modifier = modifier.fillMaxWidth()) {
        SeatColumn(
            seats = left,
            night = night,
            textColor = textColor,
            dimColor = dimColor,
            anchors = anchors,
            manualMarks = activeMarks,
            onSeatClick = onSeatClick,
            modifier = Modifier.width(COLUMN_WIDTH).fillMaxHeight()
        )

        // 这里原来还有一条 StatusStrip（「第 3 天 · 发言 · 等 5 号」），和顶栏副标题
        // 说的是同一句话——两处都在报「现在到谁」。合并进顶栏之后，夜里还少了一个隐患：
        // 那条用的是「正在闪的座位」，而夜里的座位号本身就是身份。
        Feed(
            live = live,
            night = night,
            textColor = textColor,
            dimColor = dimColor,
            surface = surface,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        SeatColumn(
            seats = right,
            night = night,
            textColor = textColor,
            dimColor = dimColor,
            anchors = anchors,
            manualMarks = activeMarks,
            onSeatClick = onSeatClick,
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
internal val COLUMN_WIDTH = 72.dp

/**
 * 一列玩家。
 *
 * 每人占 `weight(1f)` 的一格，内容在格内居中：人数少的时候（6 人局只有 3 行）
 * 自然铺开显得像「桌子两侧坐着人」，人数多的时候每格一起变矮，绝不会把最后一个人
 * 挤出屏幕——`SpaceEvenly` 做不到这一点，它在内容超高时只会一路排到屏幕外面去。
 */
@Composable
internal fun SeatColumn(
    seats: List<UIWerewolfSeatLive>,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    anchors: MutableMap<Int, Offset>,
    manualMarks: Map<Int, UIWerewolfPlayerMark> = emptyMap(),
    onSeatClick: ((Int) -> Unit)? = null,
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
                    manualMark = manualMarks[seat.seat],
                    onClick = onSeatClick?.let { cb -> { cb(seat.seat) } },
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
internal fun SeatTile(
    seat: UIWerewolfSeatLive,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    manualMark: UIWerewolfPlayerMark? = null,
    onClick: (() -> Unit)? = null,
    /** 头像中心在窗口里的位置。思考气泡挂在它头顶。 */
    onAnchor: (Offset) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(COLUMN_WIDTH)
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
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
            val activeGlowColor = if (night) Color(0xFFE5B869) else Color(0xFFC84B2A)
            if (seat.active) {
                // 正在发言/正在行动的人要有动静，静止的牌桌看不出轮到谁。
                // 两层：贴着头像的呼吸光说「是这个人」，往外扩的涟漪说「他正在出声」。
                // 只有呼吸的话，一个正在思考的人和一个正在说话的人长得一样；
                // 只有涟漪的话，安静时看不出重心在哪。两个一起才读得出来。
                val transition = rememberInfiniteTransition(label = "active")
                val scale by transition.animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.06f,
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
                            // 扩到 1.48 倍（约 56dp），72dp 的列里放得下，不会压到隔壁
                            val grow = 1f + 0.48f * ripple
                            scaleX = grow
                            scaleY = grow
                            // 扩出去的同时淡掉：一圈硬边扩到底再消失会像故障
                            alpha = (1f - ripple) * 0.85f
                        }
                        .border(1.5.dp, activeGlowColor, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .background(activeGlowColor.copy(alpha = if (night) 0.28f else 0.20f), CircleShape)
                )
            }

            val ring = when {
                seat.isSelf -> if (night) SelfBlueNight else SelfBlue
                seat.active -> activeGlowColor
                seat.sheriff && seat.alive -> SheriffGold.copy(alpha = 0.85f)
                seat.sheriffCandidate && seat.alive -> SheriffCandidateAmber.copy(alpha = 0.82f)
                seat.alive -> if (night) Color(0x38FFFFFF) else Color(0x22FFFFFF)
                else -> Color.Transparent
            }
            val ringWidth = when {
                seat.isSelf || seat.active -> 2.dp
                (seat.sheriff || seat.sheriffCandidate) && seat.alive -> 1.5.dp
                else -> 1.dp
            }
            val badgeRim = if (night) NightSurface else Color.White
            UIAvatar(
                name = seat.name,
                avatarPath = seat.avatarPath,
                size = 34.dp,
                shape = CircleShape,
                modifier = Modifier
                    .alpha(if (seat.alive) 1f else 0.34f)
                    .then(
                        if (ring != Color.Transparent) {
                            Modifier.border(ringWidth, ring, CircleShape)
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
                        .size(18.dp)
                        .background(VoteRed, CircleShape)
                        .border(1.2.dp, badgeRim, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = votes.toString(),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // 警徽 / 警上标志：戴在头像左上角，两者互斥（警长选出后警上标志自动消失）。
            if (seat.sheriff) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(16.dp)
                        .background(SheriffGold, CircleShape)
                        .border(1.2.dp, badgeRim, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "警",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else if (seat.sheriffCandidate) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-4).dp, y = (-2).dp)
                        .clip(RoundedCornerShape(50))
                        .background(SheriffCandidateAmber)
                        .border(1.2.dp, badgeRim, RoundedCornerShape(50))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "警上",
                        fontSize = 7.5.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        softWrap = false,
                        maxLines = 1,
                    )
                }
            }

            // 狼队友：戴在头像右上角，和左上的警徽、右下的票数各占一角。
            // 只在本人是狼时出现——那是已经知道的事，不标反而要靠猜。
            // 非狼队友位置空着，正好用来挂游玩模式下玩家手动贴的身份记号（纯本地笔记）。
            if (seat.isWolfMate) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .background(FateRed, CircleShape)
                        .border(1.2.dp, badgeRim, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "狼",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else if (manualMark != null) {
                val markBg = if (manualMark.hostile) FateRed else if (night) SeerTealNight else SeerTeal
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .background(markBg, CircleShape)
                        .border(1.2.dp, badgeRim, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = manualMark.short,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Text(
            text = "${seat.seat} ${seat.name}",
            modifier = Modifier.padding(top = 1.dp),
            fontSize = FsSeatName,
            fontWeight = if (seat.isSelf || seat.active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = when {
                !seat.alive -> dimColor.copy(alpha = 0.58f)
                seat.isSelf -> if (night) SelfBlueNight else SelfBlue
                else -> textColor
            }
        )

        // 这一格是「身份徽标」位：上帝视角/结束时挂身份名，参与局里挂出局原因，
        // 预言家本人看到的**查验结论**也长在这里——「3 号是狼」必须直接挂在 3 号
        // 头像下面，白天报验人时才不用回头翻记录流。
        //
        // 三种内容互斥（结论只给预言家、身份只给公开视角），优先级：
        // 查验结论 > 出局原因 > 身份名。
        // 结论排第一是因为它是预言家唯一的价值所在，且只有他一个人看得见；
        // 出局原因排第二是因为它对所有人都重要（谁死了、怎么死的）。
        val verdict = seat.seerVerdict
        val verdictColor = when (verdict) {
            UIWerewolfVerdict.WOLF -> FateRed
            UIWerewolfVerdict.GOOD -> if (night) SeerTealNight else SeerTeal
            null -> null
        }
        val badge = verdict?.label ?: seat.fate?.short ?: seat.roleLabel
        if (badge != null) {
            val badgeAccent = verdictColor ?: when {
                seat.fate != null -> FateRed
                seat.wolf -> FateRed
                else -> if (night) SelfBlueNight else SelfBlue
            }
            Text(
                text = badge,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .clip(RoundedCornerShape(50))
                    .background(badgeAccent.copy(alpha = if (night) 0.22f else 0.13f))
                    .border(0.6.dp, badgeAccent.copy(alpha = if (night) 0.40f else 0.26f), RoundedCornerShape(50))
                    .padding(horizontal = 5.5.dp, vertical = 0.5.dp),
                fontSize = FsBadge,
                fontWeight = FontWeight.SemiBold,
                color = badgeAccent,
            )
        }

        // 座位最底下一行：这个 AI 背后跑的模型。
        // 不显示的情况：人类座位、老存档没记模型、模型名缩写后为空。
        // 字号比徽标再小一档、颜色压到近无关紧要——它是排查问题时才看的东西，
        // 不能抢走名字和身份的注意力。仍然单行省略，绝不让这一行把 12 人局撑出屏。
        val modelLabel = shortModelLabel(seat.model)
        if (modelLabel.isNotEmpty()) {
            Text(
                text = modelLabel,
                modifier = Modifier.padding(top = 0.5.dp),
                fontSize = FsModel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    !seat.alive -> dimColor.copy(alpha = 0.5f)
                    else -> dimColor
                }
            )
        }
    }
}

/** 小胶囊标签。夜里换半透明深色底，白底在暗色上会刺眼。 */
@Composable
internal fun Tag(text: String, night: Boolean, color: Color) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (night) NightControlFill else DayControlFill)
            .border(0.6.dp, if (night) NightControlBorder else DayControlBorder, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        fontSize = FsMeta,
        fontWeight = FontWeight.Medium,
        color = color
    )
}

/** 等待模型时的一枚呼吸点。静止的界面在几十秒的等待里会被当成卡死。 */
@Composable
internal fun ThinkingDot(color: Color) {
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
