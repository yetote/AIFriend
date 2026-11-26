package com.ui.werewolf

// 对话流：发言人气泡、内心、密谈、上帝播报、流式行。

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.UIAvatar
import com.ui.common.UIMarkdownText
import com.ui.common.theme.UIShadowSoft
import com.ui.werewolf.data.UIWerewolfBallotCard
import com.ui.werewolf.data.UIWerewolfBubbleKind
import com.ui.werewolf.data.UIWerewolfBubbleStyle
import com.ui.werewolf.data.UIWerewolfLine
import com.ui.werewolf.data.UIWerewolfLineKind
import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfSeatLive
import com.ui.werewolf.data.UIWerewolfStreaming
import com.ui.werewolf.data.UIWerewolfWaiting
import com.ui.werewolf.data.tableBubbleStyle
import kotlinx.coroutines.delay

/**
 * 流式发言每长出这么多字就往下贴一次底。
 *
 * 太少会每个增量都滚一次（抖动），太多则一句长发言的后半截会跑出屏幕。
 */
internal const val STREAM_SCROLL_CHARS = 60

/**
 * 记录流。
 *
 * 三种东西混在一条时间线里，但排版必须分开，否则读不出层次：
 * 上帝播报是**事实**（居中、灰、无头像），公开发言是**某个人说的话**（带头像），
 * 私有信息是**只有你知道的事**（描边强调）。第三种如果和普通发言长得一样，
 * 玩家会以为别人也听见了——那是这局最致命的一种误读。
 */
/**
 * 决定牌桌末尾那一行显示哪段话。
 *
 * 正常情况直接信 [allowed]（喇叭空了、视角能看、没轮到自己操作）。
 * 出问题的是**一段话说到一半时喇叭状态会翻转**：上一句刚念完、这一句才开始吐字，
 * 朗读队列随即把上一条标成「正在念」，闸门一关，屏幕上的半句话就被撤掉了——
 * 用户看到的是「说到一半突然没了」，非要等整段生成完、落进记录，才又整块出现。
 *
 * 所以这里加一层「粘住」：已经露过面的那段继续显示，不再跟着喇叭状态撤销。
 * 防剧透的初衷不变——**新面孔**仍然要等喇叭空闲才会冒头，只是不允许把
 * 已经开口的人中途抹掉。两种糟糕的观感里，「文字抢跑」远好过「话说到一半消失」。
 *
 * 三种情况必须收手，否则会出现「上一句的鬼影」：
 * - 轮到人类操作 / 这一局停了；
 * - 这段还没吐字（只是思考阶段，交给三个点）；
 * - 它已经落进记录流（最后一条就是这个座位），该由正式气泡接手。
 */
internal fun resolveStreaming(
    live: UIWerewolfLive,
    allowed: UIWerewolfStreaming?,
    pinned: UIWerewolfStreaming?,
): UIWerewolfStreaming? {
    if (allowed != null) return allowed
    val held = pinned ?: return null
    // 局停了、报错了、轮到人类了：都不是「有人正在说」，不能留着半句话冒充现场
    if (!live.running || live.finished || live.error != null || live.turn != null) return null
    if (held.text.isBlank()) return null
    if (live.lines.lastOrNull()?.seat == held.seat) return null
    return held
}

internal fun resolveFeedStreaming(
    live: UIWerewolfLive,
    allowed: UIWerewolfStreaming?,
    pinned: UIWerewolfStreaming?,
): UIWerewolfStreaming? =
    // 深度思考阶段 streaming 已建立但 text 仍为空；此时交给 WaitingCard 展示秒表与微状态，
    // 直到正文真正吐出第一个字再无缝切入逐字 StreamingLine。
    resolveStreaming(live, allowed, pinned)
        ?.takeIf { it.text.isNotBlank() || live.waiting == null }

@Composable
internal fun Feed(
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
    // - 还没有任何输出（投票、夜间决策、或深度思考尚未吐正文），由 WaitingCard 展示秒表、微状态或并发进度。
    //
    // 两种「正在发生」都必须等喇叭空下来（!speechBusy）：念 A 的时候引擎已经在生成 B 了，
    // 这时冒出 B 的名字或半句话，就是用户说的「下一个人对话框弹出来又消失」。
    //
    // 但**已经露过面**的那段不能这样撤掉，见 [resolveStreaming]。
    val allowed = if (live.running && live.turn == null && !live.speechBusy) live.streaming else null
    // 记住「最后一次真正显示出来的那段话」。按天数+阶段做 key：换阶段就该忘掉，
    // 否则上一阶段没落库的半句话会飘到新阶段的牌桌上。
    var pinned by remember(live.day, live.phaseLabel) { mutableStateOf<UIWerewolfStreaming?>(null) }
    SideEffect { if (allowed != null) pinned = allowed }
    val streaming = resolveFeedStreaming(live, allowed, pinned)
    val waiting = if (live.running && live.turn == null && streaming == null && !live.speechBusy) {
        live.waiting
    } else {
        null
    }
    // 起始时刻提升到 LazyColumn 之外：用户下拉消息列表查看历史时，
    // 位于底部的 WaitingCard 会离开可视区被 LazyColumn 回收/重建，
    // 把起始时刻固定在 Feed 组合节点上，滑回底部时秒表即可按真实耗时连续走字，不会归零。
    val waitingStartedAtMs = remember(waiting?.key) {
        if (waiting != null) System.currentTimeMillis() else 0L
    }
    val thinkingSeat = if (waiting == null && live.running && live.turn == null && streaming == null && !live.speechBusy) {
        live.seats.firstOrNull { it.active }
    } else {
        null
    }
    // 长发言会超出一屏，光靠「开始说话」滚一次不够。按字数分段滚：
    // 每 60 字贴一次底，既跟得上，又不会每个增量都触发一次滚动
    val streamBucket = (streaming?.text?.length ?: 0) / STREAM_SCROLL_CHARS
    LaunchedEffect(live.lines.size, waiting?.key, waiting?.done, thinkingSeat?.seat, streaming?.seat, streamBucket) {
        val extra = if (waiting != null || thinkingSeat != null || streaming != null) 1 else 0
        val last = live.lines.lastIndex + extra
        if (last >= 0) {
            // 贴底用瞬时滚动：流式每几秒就来一次，动画排起队来反而永远追不上
            listState.scrollToItem(last)
        }
    }

    if (live.lines.isEmpty() && waiting == null && thinkingSeat == null && streaming == null) {
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
        } else if (waiting != null) {
            item(key = "waiting") {
                WaitingCard(
                    waiting = waiting,
                    startedAtMs = waitingStartedAtMs,
                    night = night,
                    textColor = textColor,
                    dimColor = dimColor,
                )
            }
        } else if (thinkingSeat != null) {
            item(key = "thinking") {
                ThinkingLine(seat = thinkingSeat, night = night, dimColor = dimColor)
            }
        }
    }
}

/**
 * 升级版等待卡片：实时秒表 + 时间推进微状态 + 白天并发投票实时落票进度。
 *
 * 严格遵循映射层裁剪：
 * - [UIWerewolfWaiting.maskedNight] 为 true 时只显示夜间中性氛围文案与连续计时，绝不显示座位或人数；
 * - 白天并发阶段实时展示「已完成 X/Y」进度条与仍在纠结的座位号；
 * - 单人思考阶段展示头像、秒表与按秒数推进的微状态，缓解干等焦躁感。
 */
internal fun elapsedSecondsSince(
    startedAtMs: Long,
    nowMs: Long = System.currentTimeMillis(),
): Int = if (startedAtMs <= 0L) {
    0
} else {
    ((nowMs - startedAtMs).coerceAtLeast(0L) / 1000L).toInt()
}

@Composable
internal fun WaitingCard(
    waiting: UIWerewolfWaiting,
    startedAtMs: Long = 0L,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    val anchorStartedAt = remember(waiting.key, startedAtMs) {
        if (startedAtMs > 0L) startedAtMs else System.currentTimeMillis()
    }
    var elapsedSeconds by remember(waiting.key, anchorStartedAt) {
        mutableIntStateOf(elapsedSecondsSince(anchorStartedAt))
    }
    LaunchedEffect(waiting.key, anchorStartedAt) {
        while (true) {
            elapsedSeconds = elapsedSecondsSince(anchorStartedAt)
            delay(500L)
        }
    }
    // 夜间遮罩态跨多个隐藏子阶段连续计时，不套用单步时限的 80% 警告变红，避免误报超时也不泄露步界
    val stressed = !waiting.maskedNight &&
        waiting.limitSeconds > 0 &&
        elapsedSeconds * 100 >= waiting.limitSeconds * 80
    val accent = if (night) Color(0xFFE5B869) else SelfBlue
    val timerColor = when {
        stressed && night -> Color(0xFFFFB4B0)
        stressed -> FateRed
        else -> accent
    }
    val timerLabel = if (waiting.limitSeconds > 0 && !waiting.maskedNight) {
        "${elapsedSeconds}s / ${waiting.limitSeconds}s"
    } else {
        "${elapsedSeconds}s"
    }

    val waitingShape = RoundedCornerShape(14.dp)
    val waitingBrush = if (night) {
        Brush.verticalGradient(listOf(Color(0x9E241C17), Color(0x881A1411)))
    } else {
        Brush.verticalGradient(listOf(Color(0xE8FFFEFA), Color(0xDCF7EFE2)))
    }
    val waitingBorder = if (night) Color(0x3BE5B869) else DayStroke
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.5.dp, waitingShape, clip = false, ambientColor = UIShadowSoft, spotColor = UIShadowSoft)
            .clip(waitingShape)
            .background(waitingBrush)
            .border(0.75.dp, waitingBorder, waitingShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (waiting.seat != null && !waiting.maskedNight) {
                UIAvatar(
                    name = waiting.speaker ?: "${waiting.seat}",
                    avatarPath = waiting.avatarPath,
                    size = 24.dp,
                    shape = CircleShape,
                    modifier = Modifier.alpha(0.9f),
                )
                Spacer(Modifier.width(8.dp))
            } else {
                ThinkingDot(color = accent)
                Spacer(Modifier.width(8.dp))
            }
            val title = when {
                waiting.maskedNight -> waiting.actionLabel
                waiting.seat != null -> "${waiting.seat} 号${waiting.actionLabel}"
                else -> waiting.actionLabel
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = FsName,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = timerLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = timerColor,
            )
        }

        // 并发阶段进度条 + 仍在纠结的座位名单（夜间遮罩时 total == 0、pendingSeats 为空，绝不显示）
        if (!waiting.maskedNight && waiting.seat == null && (waiting.total > 1 || waiting.pendingSeats.isNotEmpty())) {
            val fraction = (waiting.done.toFloat() / waiting.total.toFloat()).coerceIn(0f, 1f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (night) NightControlFill else DayControlFill),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(accent),
                    )
                }
                Text(
                    text = "${waiting.done}/${waiting.total}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = dimColor,
                )
            }
            if (waiting.pendingSeats.isNotEmpty()) {
                val preview = if (waiting.pendingSeats.size <= 5) {
                    waiting.pendingSeats.joinToString("、") { "${it}号" }
                } else {
                    waiting.pendingSeats.take(5).joinToString("、") { "${it}号" } + " 等 ${waiting.pendingSeats.size} 人"
                }
                Text(
                    text = "思考中：$preview",
                    fontSize = FsMeta,
                    color = dimColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            // 随等待时长自然推进的微状态文案（纯时间驱动，绝不读取任何私有角色或推理内容）
            val microStatus = waitingMicroStatus(waiting, elapsedSeconds)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = microStatus,
                    fontSize = FsMeta,
                    color = dimColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (waiting.seat != null && !waiting.maskedNight) {
                    ThinkingDot(color = accent)
                }
            }
        }
    }
}

internal fun waitingMicroStatus(waiting: UIWerewolfWaiting, elapsedSeconds: Int): String {
    if (waiting.maskedNight) {
        return when {
            elapsedSeconds < 8 -> "天黑请闭眼，静待黎明…"
            elapsedSeconds < 20 -> "夜色沉静，暗流正在涌动…"
            else -> "深思熟虑中，很快即将天亮…"
        }
    }
    return when {
        waiting.actionLabel.contains("密谈") -> when {
            elapsedSeconds < 8 -> "正在评估刀口与白天分工…"
            else -> "正在权衡狼队战术…"
        }
        waiting.actionLabel.contains("开枪") -> "正在权衡最后一枪带走谁…"
        waiting.actionLabel.contains("发言顺序") -> "正在斟酌警左/警右/死左/死右…"
        elapsedSeconds < 5 -> "正在梳理场上信息…"
        elapsedSeconds < 12 -> "正在核对发言与票型…"
        elapsedSeconds < 22 -> "正在推演身份与狼坑…"
        else -> "正在深度推演发言策略…"
    }
}

/**
 * 公开发言和流式发言共用的气泡头部：头像描边环 + 座位号徽标 + 昵称 + 右侧角标 + 细分隔线。
 * 两者复用同一布局与间距，流式结束切换成正式记录时零跳动。
 */
@Composable
private fun SpeechBubbleHeader(
    speaker: String?,
    seat: Int?,
    avatarPath: String?,
    night: Boolean,
    style: UIWerewolfBubbleStyle,
    textColor: Color,
    tagText: String,
    isStreaming: Boolean = false,
) {
    val avatarRingColor = style.accent.copy(alpha = if (isStreaming) 0.72f else if (night) 0.42f else 0.34f)
    val hairlineColor = if (night) Color(0x1CF5E8D8) else Color(0x14241C14)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .border(if (isStreaming) 1.3.dp else 0.9.dp, avatarRingColor, CircleShape)
                    .padding(1.dp)
            ) {
                UIAvatar(
                    name = speaker ?: "?",
                    avatarPath = avatarPath,
                    size = 26.dp,
                    shape = CircleShape,
                )
            }
            Spacer(Modifier.width(7.dp))
            seat?.let {
                SeatNumberBadge(seat = it, accent = style.accent, night = night)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = speaker ?: "未知",
                fontSize = FsName,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            BubbleTag(text = tagText, accent = style.accent)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(hairlineColor)
        )
    }
}

/**
 * 「某人正在说」的一行，逐字长出来。
 *
 * 排版和落定后的发言卡**一模一样**（气泡头部的头像 + 名字 + 正文），只多末尾一个光标。
 * 这样这句话说完变成正式记录时不会「跳一下」——包括头像的位置也得一致。
 */
@Composable
internal fun StreamingLine(
    stream: UIWerewolfStreaming,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    val style = tableBubbleStyle(UIWerewolfBubbleKind.SPEECH, night)
    UIWerewolfBubble(
        style = style,
        modifier = Modifier.fillMaxWidth(),
        shape = SpeechBubbleShape,
        elevation = 3.dp,
    ) {
        SpeechBubbleHeader(
            speaker = stream.speaker,
            seat = stream.seat,
            avatarPath = stream.avatarPath,
            night = night,
            style = style,
            textColor = textColor,
            tagText = "发言中",
            isStreaming = true,
        )
        if (stream.text.isNotBlank()) {
            // 光标走 trailing 单独追加，不参与 MD 解析——否则半句里的 `**` 会被当标记吃掉
            UIMarkdownText(
                text = stream.text,
                trailing = "▌",
                modifier = Modifier.padding(top = 6.dp),
                baseSize = FsSpeech,
                baseLineHeight = FsSpeechLine,
                color = textColor.copy(alpha = 0.94f),
            )
        } else {
            // 还在思考、一个字没说：只显示「正在组织语言」，不把思考流放出来——
            // 对局里看 AI 推理会把「读心」变成主要玩法，思考留给复盘
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "正在组织语言", fontSize = FsName, color = dimColor)
                Spacer(Modifier.width(6.dp))
                ThinkingDot(color = dimColor)
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
internal fun ThinkingLine(seat: UIWerewolfSeatLive, night: Boolean, dimColor: Color) {
    val shape = SpeechBubbleShape
    val brush = if (night) {
        Brush.verticalGradient(listOf(Color(0x96231B17), Color(0x801A1411)))
    } else {
        Brush.verticalGradient(listOf(Color(0xE0FFFEFA), Color(0xD4F6EFE2)))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.5.dp, shape, clip = false, ambientColor = UIShadowSoft, spotColor = UIShadowSoft)
            .clip(shape)
            .background(brush)
            .border(0.75.dp, if (night) Color(0x38E5B869) else DayStroke, shape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UIAvatar(
            name = seat.name,
            avatarPath = seat.avatarPath,
            size = 24.dp,
            shape = CircleShape,
            modifier = Modifier.alpha(0.88f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${seat.seat} 号正在组织语言",
            fontSize = FsName,
            color = dimColor
        )
        Spacer(Modifier.width(7.dp))
        ThinkingDot(color = if (night) SelfBlueNight else SelfBlue)
    }
}

@Composable
internal fun LineRow(
    line: UIWerewolfLine,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    surface: Color,
) {
    when (line.kind) {
        UIWerewolfLineKind.BALLOT -> line.ballot?.let { card ->
            BallotCardRow(card = card, night = night, textColor = textColor, dimColor = dimColor)
        } ?: Unit

        UIWerewolfLineKind.SHERIFF_ROSTER -> line.sheriffRoster?.let { roster ->
            SheriffRosterCardRow(
                roster = roster,
                night = night,
                textColor = textColor,
                dimColor = dimColor,
            )
        } ?: Unit

        UIWerewolfLineKind.DIVINE -> {
            // 播报是居中的系统宣告：两侧渐变细线 + 菱点装饰 + 磨砂圆角卡
            val style = tableBubbleStyle(UIWerewolfBubbleKind.BROADCAST, night)
            val dividerColor = if (night) Color(0x4AE5B869) else Color(0x2E241C14)
            val dotColor = if (night) Color(0xFFE5B869) else Color(0xFF9C7B56)
            val pillShape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(0.8.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, dividerColor)))
                )
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(3.5.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.65f))
                )
                Text(
                    text = line.text,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = 8.dp)
                        .shadow(1.dp, pillShape, clip = false, ambientColor = UIShadowSoft, spotColor = UIShadowSoft)
                        .clip(pillShape)
                        .background(Brush.verticalGradient(listOf(style.fill, style.fillBottom)))
                        .border(0.7.dp, style.border, pillShape)
                        .padding(horizontal = 13.dp, vertical = 6.dp),
                    fontSize = FsMeta,
                    fontWeight = FontWeight.Medium,
                    color = if (night) Color(0xFFE5DACB) else dimColor,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(3.5.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.65f))
                )
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(0.8.dp)
                        .background(Brush.horizontalGradient(listOf(dividerColor, Color.Transparent)))
                )
            }
        }

        UIWerewolfLineKind.INNER -> InnerRow(
            line = line,
            night = night,
            dimColor = dimColor
        )

        // 投票和夜里那些念头共用一套排版，但底色不同：
        // 投票是会被当场执行的表态，念头的底色必须压得比它低
        UIWerewolfLineKind.VOTE -> InnerRow(
            line = line,
            night = night,
            dimColor = dimColor,
            kind = UIWerewolfBubbleKind.VOTE
        )

        UIWerewolfLineKind.SPEECH -> SpeechRow(
            line = line,
            night = night,
            textColor = textColor,
            dimColor = dimColor
        )

        // 查验结论：预言家唯一的价值所在，做成「结论卡」而不是一行心里话
        UIWerewolfLineKind.SEER_RESULT -> SeerResultRow(
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
internal fun SpeechRow(
    line: UIWerewolfLine,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    val style = tableBubbleStyle(UIWerewolfBubbleKind.SPEECH, night)
    // 头像放在气泡头部里，而不是和气泡并排占掉一整列：气泡因此能铺满整行，
    // 长发言不再被挤成窄条。「谁在说」由头部第一行承担，视觉上更像一条完整的卡片。
    UIWerewolfBubble(
        style = style,
        modifier = Modifier.fillMaxWidth(),
        shape = SpeechBubbleShape,
        elevation = 2.5.dp,
    ) {
        SpeechBubbleHeader(
            speaker = line.speaker,
            seat = line.seat,
            avatarPath = line.avatarPath,
            night = night,
            style = style,
            textColor = textColor,
            tagText = style.tag,
        )
        UIMarkdownText(
            text = line.text,
            modifier = Modifier.padding(top = 6.dp),
            baseSize = FsSpeech,
            baseLineHeight = FsSpeechLine,
            color = textColor.copy(alpha = 0.94f),
        )
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
internal fun InnerRow(
    line: UIWerewolfLine,
    night: Boolean,
    dimColor: Color,
    kind: UIWerewolfBubbleKind = UIWerewolfBubbleKind.INNER,
) {
    val style = tableBubbleStyle(kind, night)
    val isThought = kind == UIWerewolfBubbleKind.INNER
    UIWerewolfBubble(
        style = style,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 34.dp, end = 4.dp),
        corner = 10.dp,
        shape = InnerBubbleShape,
        elevation = if (isThought) 0.dp else 1.5.dp,
        dashed = isThought,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BubbleTag(text = line.innerLabel ?: style.tag, accent = style.accent)
            val who = listOfNotNull(line.seat?.let { "$it 号" }, line.speaker)
                .joinToString(" ")
            if (who.isNotBlank()) {
                Text(
                    text = " $who",
                    fontSize = FsMeta,
                    fontWeight = FontWeight.Medium,
                    color = dimColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (line.secret) {
                Spacer(Modifier.weight(1f))
                VisibilityPill(text = "仅你可见", accent = style.accent, night = night)
            }
        }
        UIMarkdownText(
            text = line.text,
            modifier = Modifier.padding(top = 4.dp),
            baseSize = FsName,
            baseLineHeight = 18.sp,
            color = dimColor,
        )
    }
}

/**
 * 预言家的查验结论。
 *
 * 单独一档排版，因为它是**这局分量最重的一条私信**：
 * 预言家白天要拿它去带票、去跟人对账，做成灰色小字就等于把它埋了。
 *
 * 所以给它正常字号 + 强调色 + 一句「仅你可见」。
 * 「仅你可见」不是装饰：这条消息只有预言家本人收得到，
 * 但观战视角看到的是同一条记录——那不是「仅你可见」，是上帝视角。
 */
@Composable
internal fun SeerResultRow(
    line: UIWerewolfLine,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    val style = tableBubbleStyle(UIWerewolfBubbleKind.SEER_RESULT, night)
    UIWerewolfBubble(
        style = style,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        elevation = 2.5.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BubbleTag(text = style.tag, accent = style.accent)
            val who = listOfNotNull(line.seat?.let { "$it 号" }, line.speaker)
                .joinToString(" ")
            if (who.isNotBlank()) {
                Text(
                    text = " · $who",
                    fontSize = FsMeta,
                    fontWeight = FontWeight.Medium,
                    color = dimColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            VisibilityPill(
                text = if (line.secret) "仅你可见" else "上帝视角",
                accent = style.accent,
                night = night,
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(style.accent.copy(alpha = 0.22f))
        )
        UIMarkdownText(
            text = line.text,
            modifier = Modifier.padding(top = 5.dp),
            baseSize = 13.5.sp,
            baseLineHeight = 20.sp,
            color = textColor.copy(alpha = 0.96f),
        )
    }
}

/** 只有这个视角看得见的消息。描边 + 「仅你可见」，怕的就是玩家误以为别人也听见了。 */
@Composable
internal fun PrivateRow(
    line: UIWerewolfLine,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    surface: Color,
) {
    val style = tableBubbleStyle(UIWerewolfBubbleKind.SECRET, night)
    UIWerewolfBubble(
        style = style,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        elevation = 2.5.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BubbleTag(text = style.tag, accent = style.accent)
            val who = listOfNotNull(line.seat?.let { "$it 号" }, line.speaker)
                .joinToString(" ")
            if (who.isNotBlank()) {
                Text(
                    text = " · $who",
                    fontSize = FsMeta,
                    fontWeight = FontWeight.Medium,
                    color = dimColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            VisibilityPill(text = "仅你可见", accent = style.accent, night = night)
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(style.accent.copy(alpha = 0.22f))
        )
        UIMarkdownText(
            text = line.text,
            modifier = Modifier.padding(top = 5.dp),
            baseSize = 13.sp,
            baseLineHeight = 19.sp,
            color = textColor.copy(alpha = 0.92f),
        )
    }
}

// ---------------------------------------------------------------------------
// 唱票卡
// ---------------------------------------------------------------------------

/**
 * 一轮投票的公开票型。
 *
 * 刻意做成一张**卡片**而不是一句播报：投票最想知道的是「谁和谁投在一起」，
 * 一行「3→5、5→5」的箭头流再加一条得票统计，扫一眼就能对上。
 *
 * 时刻意和发言气泡拉开——它有标题、有边框、没有头像，一眼看出是「结果」不是「谁说的话」。
 * 不朗读（[com.feature.werewolf.engine.MessageKind.neverSpoken]），否则观战时会被念成
 * 一串「三到五」。
 */
@Composable
internal fun BallotCardRow(
    card: UIWerewolfBallotCard,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    // 记票卡走青蓝：和投票的琥珀分开——「我投了谁」和「全场投了谁」是两件事
    val style = tableBubbleStyle(UIWerewolfBubbleKind.BALLOT, night)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .shadow(2.dp, shape, clip = false, ambientColor = UIShadowSoft, spotColor = UIShadowSoft)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(style.fill, style.fillBottom)))
            .border(0.9.dp, style.border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 标题条：角标 + 投票类型 + 已投票数。弃票也算投了，所以分母是 totalVotes。
        Row(verticalAlignment = Alignment.CenterVertically) {
            BubbleTag(text = style.tag, accent = style.accent)
            Spacer(Modifier.width(6.dp))
            Text(
                text = card.title,
                fontSize = FsName,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${card.totalVotes} 票",
                fontSize = FsMeta,
                color = dimColor,
            )
        }

        // 按目标分组：一行一个「被投的人」，下面挂着投给他的人——
        // 和网易狼人杀的票型一致，扫一眼就知道票集中在哪里，不用在箭头流里找。
        // 排序：得票多的在前，弃票沉底；同票数按座位号，稳定好读。
        val votesBySeat = card.tally.associate { it.seat to it.votes }
        val groups = card.ballots.groupBy({ it.targetSeat }, { it.voterSeat })
        val maxVotes = (votesBySeat.values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val orderedTargets = groups.keys.sortedWith(
            compareByDescending<Int?> { votesBySeat[it] ?: 0.0 }
                .thenBy { it == null } // null = 弃票，排最后
                .thenBy { it ?: Int.MAX_VALUE }
        )
        orderedTargets.forEach { target ->
            val voters = groups.getValue(target)
            TargetVotesRow(
                target = target,
                voters = voters,
                votes = votesBySeat[target] ?: voters.size.toDouble(),
                maxVotes = maxVotes,
                night = night,
                textColor = textColor,
                dimColor = dimColor,
            )
        }
    }
}

/**
 * 一组票：第一行是被投的目标 + 票数胶囊 + 比例微条，第二行是投给他的座位号微胶囊。
 * 弃票的目标为空，整组灰掉、票数不显示，只列投票人。
 */
@Composable
private fun TargetVotesRow(
    target: Int?,
    voters: List<Int>,
    votes: Double,
    maxVotes: Double,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    val abstain = target == null
    val ratio = if (abstain) 0f else (votes / maxVotes).toFloat().coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (night) Color(0x14FFFFFF) else Color(0x88FFFFFF))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (abstain) "弃票" else "$target 号",
                fontSize = FsName,
                fontWeight = FontWeight.Bold,
                color = if (abstain) dimColor else VoteRed,
            )
            if (!abstain) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (night) Color(0x1FFFFFFF) else Color(0x14E0564F))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(VoteRed.copy(alpha = if (night) 0.85f else 0.78f))
                    )
                }
                Spacer(Modifier.width(8.dp))
                val label = if (votes % 1.0 == 0.0) {
                    votes.toInt().toString()
                } else {
                    String.format(java.util.Locale.US, "%.1f", votes)
                }
                Text(
                    text = "$label 票",
                    fontSize = FsMeta,
                    fontWeight = FontWeight.SemiBold,
                    color = VoteRed,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            voters.forEach { voter ->
                Text(
                    text = "${voter}号",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (night) Color(0x1EFFFFFF) else Color(0x121B2B45))
                        .padding(horizontal = 6.dp, vertical = 1.5.dp),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (abstain) dimColor.copy(alpha = 0.85f) else textColor,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 上警名单卡
// ---------------------------------------------------------------------------

/**
 * 警长竞选名单卡：与唱票卡同系的结构化卡片，把「谁在警上竞选 / 谁在警下握有警长票」
 * 做成一目了然的座位气泡列表，替代过去一整屏十几条「我上警」废话或挤成一团的灰字。
 */
@Composable
internal fun SheriffRosterCardRow(
    roster: com.ui.werewolf.data.UIWerewolfSheriffRoster,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    val style = tableBubbleStyle(UIWerewolfBubbleKind.SHERIFF_ROSTER, night)
    val shape = RoundedCornerShape(14.dp)
    val sectionBg = if (night) Color(0x14FFFFFF) else Color(0x8CFFFFFF)
    val runnerChipBg = if (night) Color(0x36D4722A) else Color(0x1FD4722A)
    val runnerChipBorder = if (night) Color(0x66F0B45A) else Color(0x4DD4722A)
    val runnerChipText = if (night) Color(0xFFF7D59C) else Color(0xFF9C4710)
    val selfAccent = if (night) SelfBlueNight else SelfBlue
    val offChipBg = if (night) Color(0x18FFFFFF) else Color(0x10241C14)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .shadow(2.dp, shape, clip = false, ambientColor = UIShadowSoft, spotColor = UIShadowSoft)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(style.fill, style.fillBottom)))
            .border(0.9.dp, style.border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BubbleTag(text = style.tag, accent = style.accent)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "警长竞选名单",
                fontSize = FsName,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${roster.runners.size} 人上警",
                fontSize = FsMeta,
                fontWeight = FontWeight.Medium,
                color = style.accent,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(sectionBg)
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "警上竞选（${roster.runners.size}）",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = style.accent,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                roster.runners.forEach { seat ->
                    val chipShape = RoundedCornerShape(50)
                    val bg = if (seat.isSelf) selfAccent.copy(alpha = if (night) 0.28f else 0.14f) else runnerChipBg
                    val bd = if (seat.isSelf) selfAccent.copy(alpha = 0.65f) else runnerChipBorder
                    val fg = if (seat.isSelf) selfAccent else runnerChipText
                    Text(
                        text = "${seat.seat}号 ${seat.name}",
                        modifier = Modifier
                            .clip(chipShape)
                            .background(bg)
                            .border(0.75.dp, bd, chipShape)
                            .padding(horizontal = 7.dp, vertical = 2.5.dp),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (roster.offPodium.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(sectionBg)
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "警下投票（${roster.offPodium.size}）",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = dimColor,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    roster.offPodium.forEach { seat ->
                        val chipShape = RoundedCornerShape(50)
                        val bg = if (seat.isSelf) selfAccent.copy(alpha = if (night) 0.24f else 0.12f) else offChipBg
                        val fg = if (seat.isSelf) selfAccent else dimColor
                        Text(
                            text = "${seat.seat}号 ${seat.name}",
                            modifier = Modifier
                                .clip(chipShape)
                                .background(bg)
                                .then(
                                    if (seat.isSelf) {
                                        Modifier.border(0.75.dp, selfAccent.copy(alpha = 0.5f), chipShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            fontSize = 10.5.sp,
                            fontWeight = if (seat.isSelf) FontWeight.SemiBold else FontWeight.Medium,
                            color = fg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 输入区
// ---------------------------------------------------------------------------
