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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.UIAvatar
import com.ui.common.UIMarkdownText
import com.ui.werewolf.data.UIWerewolfBallotCard
import com.ui.werewolf.data.UIWerewolfLine
import com.ui.werewolf.data.UIWerewolfBubbleKind
import com.ui.werewolf.data.UIWerewolfLineKind
import com.ui.werewolf.data.bubbleStyle
import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfSeatLive
import com.ui.werewolf.data.UIWerewolfStreaming

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
internal fun StreamingLine(
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
                // 光标走 trailing 单独追加，不参与 MD 解析——否则半句里的 `**` 会被当标记吃掉
                UIMarkdownText(
                    text = stream.text,
                    trailing = "▌",
                    modifier = Modifier.padding(top = 2.dp),
                    baseSize = FsSpeech,
                    baseLineHeight = FsSpeechLine,
                    color = textColor.copy(alpha = 0.92f),
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
internal fun ThinkingLine(seat: UIWerewolfSeatLive, dimColor: Color) {
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
internal fun SpeechRow(
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
            UIMarkdownText(
                text = line.text,
                modifier = Modifier.padding(top = 2.dp),
                baseSize = FsSpeech,
                baseLineHeight = FsSpeechLine,
                color = textColor.copy(alpha = 0.92f),
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
internal fun InnerRow(
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
        UIMarkdownText(
            text = line.text,
            modifier = Modifier.padding(top = 2.dp),
            baseSize = FsName,
            baseLineHeight = 18.sp,
            color = dimColor,
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
        UIMarkdownText(
            text = line.text,
            modifier = Modifier.padding(top = 3.dp),
            baseSize = 13.sp,
            baseLineHeight = 19.sp,
            color = textColor.copy(alpha = 0.9f),
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
    val stroke = if (night) Color(0x2FFFFFFF) else Color(0x1A000000)
    val cardBg = if (night) Color(0x14FFFFFF) else Color(0x06000000)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(1.dp, stroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 标题条：投票类型 + 已投票数。弃票也算投了，所以分母是 totalVotes。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = card.title,
                fontSize = FsName,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "· ${card.totalVotes} 人投票",
                fontSize = FsMeta,
                color = dimColor,
            )
        }

        // 按目标分组：一行一个「被投的人」，下面挂着投给他的人——
        // 和网易狼人杀的票型一致，扫一眼就知道票集中在哪里，不用在箭头流里找。
        // 排序：得票多的在前，弃票沉底；同票数按座位号，稳定好读。
        val votesBySeat = card.tally.associate { it.seat to it.votes }
        val groups = card.ballots.groupBy({ it.targetSeat }, { it.voterSeat })
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
                textColor = textColor,
                dimColor = dimColor,
            )
        }
    }
}

/**
 * 一组票：第一行是被投的目标 + 得票数，第二行是投给他的人。
 * 弃票的目标为空，整组灰掉、票数不显示，只列投票人。
 */
@Composable
private fun TargetVotesRow(
    target: Int?,
    voters: List<Int>,
    votes: Double,
    textColor: Color,
    dimColor: Color,
) {
    val abstain = target == null
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (abstain) "弃票" else "$target 号",
                fontSize = FsName,
                fontWeight = FontWeight.SemiBold,
                color = if (abstain) dimColor else VoteRed,
            )
            if (!abstain) {
                Spacer(Modifier.width(6.dp))
                val label = if (votes % 1.0 == 0.0) {
                    votes.toInt().toString()
                } else {
                    String.format(java.util.Locale.US, "%.1f", votes)
                }
                Text(text = "$label 票", fontSize = FsMeta, color = dimColor)
            }
        }
        Spacer(Modifier.height(2.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            voters.forEach { voter ->
                Text(
                    text = "${voter}号",
                    fontSize = FsMeta,
                    color = if (abstain) dimColor.copy(alpha = 0.8f) else textColor,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 输入区
// ---------------------------------------------------------------------------
