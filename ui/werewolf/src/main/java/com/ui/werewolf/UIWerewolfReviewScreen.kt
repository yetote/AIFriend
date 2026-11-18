package com.ui.werewolf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UISeparator
import com.ui.common.theme.uiCard
import com.ui.werewolf.data.UIWerewolfBallotLine
import com.ui.werewolf.data.UIWerewolfCheckReport
import com.ui.werewolf.data.UIWerewolfDayReport
import com.ui.werewolf.data.UIWerewolfDialogueDay
import com.ui.werewolf.data.UIWerewolfGroupKind
import com.ui.werewolf.data.UIWerewolfNightReport
import com.ui.werewolf.data.UIWerewolfReview
import com.ui.werewolf.data.UIWerewolfRevealedSeat
import com.ui.werewolf.data.UIWerewolfRoleGroup
import com.ui.werewolf.data.UIWerewolfSpeechLine

// ---------------------------------------------------------------------------
// 配色
//
// 复盘页是「白天的、安静的」：一局已经结束了，不需要再制造紧张感。
// 只有狼人的红和神职的蓝留着重色，其余一律灰——颜色只用来标记阵营，
// 不用来装饰。赢家横幅例外，那是整页唯一需要「一眼看到」的东西。
// ---------------------------------------------------------------------------

private val ReviewBackdrop = Color(0xFFF1F4FA)
private val ReviewTitle = Color(0xFF1B2230)
private val ReviewBody = Color(0xFF3A4152)
private val ReviewDim = Color(0xFF7C8496)

private val WolfRed = Color(0xFFB7312B)
private val GodBlue = Color(0xFF2B5FD9)
private val VillagerGray = Color(0xFF6B7280)

private val WolfBanner = listOf(Color(0xFF2A0E14), Color(0xFF6E1E29))
private val GoodBanner = listOf(Color(0xFF0D1B2E), Color(0xFF1D4272))

/**
 * 复盘页。
 *
 * 和牌桌是**两种阅读**：牌桌要求「现在」，复盘允许「倒回去看」。所以这一页没有动画、
 * 没有实时状态、不追新消息——它可以被慢慢翻，翻多慢都不会错过东西。
 *
 * 信息顺序按复盘时真实的疑问排：
 * 谁赢了 → 为什么赢 → 我是谁 → 谁是谁 → 哪一票 / 哪一刀起了作用。
 */
@Composable
fun UIWerewolfReviewScreen(
    review: UIWerewolfReview,
    onBack: () -> Unit,
    onRestart: (() -> Unit)? = null,
    /** 导出成 Markdown 文件。null 表示不提供入口（例如没有可分享的环境）。 */
    onExport: (() -> Unit)? = null,
) {
    // 「上帝视角」默认关闭：狼人夜聊、查验这些当时只有个别人看得到的内容，
    // 需要玩家主动打开。默认摊开的话，「原来那一夜他们商量的是这个」就没得发现了。
    var godView by rememberSaveable { mutableStateOf(false) }
    // 这一天在当前视角下有没有可读的内容。只有密谈的一天在关掉上帝视角时不占位置，
    // 否则会留下一张点开也空空的卡片
    val dialogueDays = review.dialogues.filter { godView || it.open.isNotEmpty() }
    val hasSecret = review.dialogues.any { it.hasSecret }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ReviewBackdrop, Color(0xFFE9EEF7))))
            .pageInsetsTop()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ReviewTopBar(onBack = onBack, onExport = onExport)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { WinnerBanner(review) }

                review.selfLine?.let { line ->
                    item { SelfStrip(line = line) }
                }

                item { SectionTitle(title = "底牌", hint = "全部公开") }
                review.groups.forEach { group ->
                    item(key = "group-${group.title}") { RoleGroupCard(group) }
                }

                if (review.days.isNotEmpty()) {
                    item { SectionTitle(title = "逐日票型", hint = "谁把谁送走的") }
                    review.days.forEach { day ->
                        item(key = "day-${day.day}") { DayCard(day) }
                    }
                }

                if (review.nights.isNotEmpty()) {
                    item { SectionTitle(title = "夜间行动", hint = "当时只有上帝知道") }
                    item { NightCard(review.nights) }
                }

                if (review.checks.isNotEmpty()) {
                    item { SectionTitle(title = "预言家查验", hint = "当时只有他自己知道") }
                    item { CheckCard(review.checks) }
                }

                if (review.dialogues.isNotEmpty()) {
                    item {
                        DialogueHeader(
                            godView = godView,
                            hasSecret = hasSecret,
                            onToggleGodView = { godView = it },
                        )
                    }
                    dialogueDays.forEach { day ->
                        item(key = "dialogue-${day.day}") {
                            DialogueDayCard(day = day, showSecret = godView)
                        }
                    }
                }

                if (review.incomplete) {
                    item { IncompleteNote() }
                }

                if (onRestart != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(UIRadiusControl))
                                .background(UIActionBrush)
                                .clickable { onRestart() }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "再来一局",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                item {
                    // 底部留白 + 系统栏，最后一屏不会贴着边缘
                    Spacer(modifier = Modifier.height(4.dp).pageInsetsBottom())
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 顶栏与横幅
// ---------------------------------------------------------------------------

@Composable
private fun ReviewTopBar(onBack: () -> Unit, onExport: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回牌桌",
                tint = ReviewTitle
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "复盘",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReviewTitle
            )
            Text(
                text = "这一局已经打完了，所有底牌都在这儿",
                fontSize = 11.sp,
                color = ReviewDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onExport != null) {
            // 文字按钮而不是图标：分享图标在这里语义太宽（分享这一局？分享一局战绩？），
            // 写「导出 MD」不用猜，也说明了导出的格式
            Text(
                text = "导出 MD",
                modifier = Modifier
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .clickable { onExport() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GodBlue
            )
        }
    }
}

/**
 * 赢家横幅。
 *
 * 只回答两件事：谁赢、为什么。想加更多（天数、人数）就只能挤成一行小字，
 * 那行小字放在这里没人读，所以留给下面的票型和夜间行动去讲。
 */
@Composable
private fun WinnerBanner(review: UIWerewolfReview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusCard))
            .background(Brush.linearGradient(if (review.wolfWon) WolfBanner else GoodBanner))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = review.winnerLabel,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = review.reason,
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = Color.White.copy(alpha = 0.82f)
        )
        Text(
            text = review.facts,
            modifier = Modifier.padding(top = 10.dp),
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
    }
}

/** 你在这局里是谁。观战的局没有这一条。 */
@Composable
private fun SelfStrip(line: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(Color(0x142B5FD9))
            .border(0.5.dp, Color(0x1F2B5FD9), RoundedCornerShape(UIRadiusControl))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "你",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(GodBlue)
                .padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = line,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = ReviewBody
        )
    }
}

@Composable
private fun SectionTitle(title: String, hint: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 13.dp)
                .background(ReviewTitle, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ReviewTitle
        )
        if (hint != null) {
            Text(
                text = "  $hint",
                modifier = Modifier.weight(1f),
                fontSize = 10.sp,
                color = ReviewDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 底牌
// ---------------------------------------------------------------------------

@Composable
private fun RoleGroupCard(group: UIWerewolfRoleGroup) {
    val accent = when (group.kind) {
        UIWerewolfGroupKind.WOLF -> WolfRed
        UIWerewolfGroupKind.GOD -> GodBlue
        UIWerewolfGroupKind.VILLAGER -> VillagerGray
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 0.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(accent, CircleShape)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = group.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${group.members.size} 人",
                fontSize = 10.sp,
                color = ReviewDim
            )
        }

        group.members.forEachIndexed { index, seat ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .height(0.5.dp)
                        .background(UISeparator)
                )
            } else {
                Spacer(Modifier.height(6.dp))
            }
            RevealedSeatRow(seat = seat, accent = accent)
        }
    }
}

@Composable
private fun RevealedSeatRow(seat: UIWerewolfRevealedSeat, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UIAvatar(
            name = seat.name,
            avatarPath = seat.avatarPath,
            size = 28.dp,
            shape = CircleShape
        )
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${seat.seat} 号",
                    fontSize = 10.sp,
                    color = ReviewDim
                )
                Text(
                    text = "  ${seat.name}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (seat.isSelf) GodBlue else ReviewBody,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (seat.isSelf) {
                    Text(
                        text = "  你",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GodBlue
                    )
                }
            }
            Text(
                text = buildString {
                    append(seat.roleLabel)
                    seat.fate?.let { append(" · ").append(it) }
                },
                modifier = Modifier.padding(top = 1.dp),
                fontSize = 10.sp,
                color = if (seat.alive) ReviewDim else ReviewDim.copy(alpha = 0.8f)
            )
        }
        if (seat.votes > 0) {
            Text(
                text = "${seat.votes} 票",
                fontSize = 10.sp,
                color = WolfRed.copy(alpha = 0.85f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 票型
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayCard(day: UIWerewolfDayReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 0.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "第 ${day.day} 天",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReviewTitle
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = day.result,
                fontSize = 11.sp,
                color = if (day.exiled) WolfRed else ReviewDim
            )
            Spacer(Modifier.weight(1f))
            if (day.extraRounds > 0) {
                Text(
                    text = "平票加投 ${day.extraRounds} 次",
                    fontSize = 9.sp,
                    color = ReviewDim
                )
            }
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            day.ballots.forEach { ballot -> BallotChip(ballot) }
        }
    }
}

/**
 * 一张票。
 *
 * 投给最后出局那个人的票会被标红——复盘真正要看的就是这些票：它们决定了这一天。
 * 所以颜色不是「谁投得凶」，而是「哪几票算数」。
 */
@Composable
private fun BallotChip(ballot: UIWerewolfBallotLine) {
    val text = if (ballot.target == null) {
        "${ballot.voter} 弃票"
    } else {
        "${ballot.voter} → ${ballot.target}"
    }
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (ballot.key) Color(0x14B7312B) else UIFieldFill)
            .border(
                0.5.dp,
                if (ballot.key) Color(0x33B7312B) else Color.Transparent,
                RoundedCornerShape(50)
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
        fontSize = 10.sp,
        fontWeight = if (ballot.key) FontWeight.Medium else FontWeight.Normal,
        color = if (ballot.key) WolfRed else ReviewDim
    )
}

// ---------------------------------------------------------------------------
// 夜间
// ---------------------------------------------------------------------------

@Composable
private fun NightCard(nights: List<UIWerewolfNightReport>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 0.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        nights.forEachIndexed { index, night ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(UISeparator)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "第 ${night.day} 夜",
                    modifier = Modifier.width(48.dp),
                    fontSize = 10.sp,
                    color = ReviewDim
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = listOfNotNull(night.wolfLine, night.guardLine, night.witchLine)
                            .joinToString(" · "),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = ReviewBody
                    )
                    Text(
                        text = night.deathLine,
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 10.sp,
                        color = ReviewDim
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckCard(checks: List<UIWerewolfCheckReport>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 0.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        checks.forEachIndexed { index, check ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(UISeparator)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "第 ${check.day} 夜",
                    modifier = Modifier.width(48.dp),
                    fontSize = 10.sp,
                    color = ReviewDim
                )
                Text(
                    text = "验了 ${check.target} 号",
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = ReviewBody
                )
                Text(
                    text = if (check.isWolf) "狼人" else "好人",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (check.isWolf) Color(0x14B7312B) else Color(0x142B5FD9))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (check.isWolf) WolfRed else GodBlue
                )
            }
        }
    }
}

/**
 * 逐日对话的标题行。
 *
 * 单开一个组件而不是复用 [SectionTitle]，是因为这一行右边要多一个开关。
 * 没有密谈的局（比如人机教学局）不显示开关——一个永远没有内容的按钮比没有按钮更糟。
 */
@Composable
private fun DialogueHeader(
    godView: Boolean,
    hasSecret: Boolean,
    onToggleGodView: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 13.dp)
                .background(ReviewTitle, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = "逐日对话",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ReviewTitle
        )
        Text(
            text = "  当时的原话",
            modifier = Modifier.weight(1f),
            fontSize = 10.sp,
            color = ReviewDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (hasSecret) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (godView) Color(0x142B5FD9) else UIFieldFill)
                    .border(
                        0.5.dp,
                        if (godView) Color(0x442B5FD9) else Color.Transparent,
                        RoundedCornerShape(50)
                    )
                    .clickable { onToggleGodView(!godView) }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (godView) GodBlue else ReviewDim, CircleShape)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "上帝视角",
                    fontSize = 10.sp,
                    fontWeight = if (godView) FontWeight.Medium else FontWeight.Normal,
                    color = if (godView) GodBlue else ReviewDim
                )
            }
        }
    }
}

/**
 * 一天的对话。默认收起。
 *
 * 一局十二人局有几十上百句，全摊开会让复盘页长得没法翻；而对话又是「想起来再去看」的东西，
 * 不是第一眼要看的结论。收起时把条数写在标题上，玩家能判断值不值得点开。
 */
@Composable
private fun DialogueDayCard(day: UIWerewolfDialogueDay, showSecret: Boolean) {
    var expanded by rememberSaveable(day.day) { mutableStateOf(false) }
    val lines = if (showSecret) day.lines else day.open

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 0.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "第 ${day.day} 天",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReviewTitle
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${lines.size} 条",
                fontSize = 10.sp,
                color = ReviewDim
            )
            if (day.hasSecret && !showSecret) {
                Text(
                    text = " · 另有密谈",
                    fontSize = 10.sp,
                    color = ReviewDim.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (expanded) "收起" else "展开",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (expanded) ReviewDim else GodBlue
            )
        }

        if (expanded) {
            Spacer(Modifier.height(4.dp))
            lines.forEach { line -> DialogueLine(line) }
        }
    }
}

/**
 * 一句话。
 *
 * 上帝播报没有头像也没有座位，居中排一行，当作「这一天的分隔」用；
 * 密谈（狼人夜聊、查验、用药）铺一层极淡的红底并把性质标红，免得它和公开发言读串。
 */
@Composable
private fun DialogueLine(line: UIWerewolfSpeechLine) {
    if (line.seat == null) {
        Text(
            text = line.text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp, horizontal = 4.dp),
            fontSize = 10.sp,
            lineHeight = 15.sp,
            color = ReviewDim,
            textAlign = TextAlign.Center
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(
                if (line.secret) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x0FB7312B))
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                } else {
                    Modifier.padding(horizontal = 2.dp, vertical = 5.dp)
                }
            ),
        verticalAlignment = Alignment.Top
    ) {
        UIAvatar(
            name = line.speaker,
            avatarPath = line.avatarPath,
            size = 22.dp,
            shape = CircleShape
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = line.speaker,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (line.secret) WolfRed else ReviewBody,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                line.roleLabel?.let { role ->
                    RoleTag(text = role, kind = line.roleKind)
                }
                Text(
                    text = line.label,
                    modifier = Modifier.padding(start = 5.dp),
                    fontSize = 9.sp,
                    color = if (line.secret) WolfRed.copy(alpha = 0.8f) else ReviewDim
                )
            }
            Text(
                text = line.text,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = ReviewBody
            )
            if (line.reasoning.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                ReviewThought(reasoning = line.reasoning)
            }
        }
    }
}

/**
 * 复盘里的思考过程。
 *
 * 思考在**对局中一律不显示**（看 AI 推理会变成读心玩法），复盘是它唯一的舞台。
 * 默认收起：几十条发言每条都摊着几千字的推理，复盘页就翻不动了；
 * 想看的人自己点开——多半是想验证「他嘴上说一套、心里想的是另一套」。
 */
@Composable
private fun ReviewThought(reasoning: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(UIGlassSurfaceStrong)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "思考过程",
                modifier = Modifier.weight(1f),
                fontSize = 9.sp,
                color = ReviewDim
            )
            Text(
                text = if (expanded) "收起" else "展开",
                fontSize = 9.sp,
                color = GodBlue
            )
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = reasoning,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = ReviewDim
            )
        }
    }
}

/**
 * 对话行里的身份小标签。
 *
 * 底色只给 10% 透明度：它要能被一眼扫到，又不能抢过发言正文——复盘是拿来读的，
 * 不是拿来看配色的。三种颜色沿用底牌区的划分，同一个身份在两处不能两个颜色。
 */
@Composable
private fun RoleTag(text: String, kind: UIWerewolfGroupKind?) {
    val color = when (kind) {
        UIWerewolfGroupKind.WOLF -> WolfRed
        UIWerewolfGroupKind.GOD -> GodBlue
        else -> VillagerGray
    }
    Text(
        text = text,
        modifier = Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        fontSize = 9.sp,
        lineHeight = 12.sp,
        color = color,
        maxLines = 1
    )
}

/**
 * 数据缺失说明。
 *
 * 旧版存档续下来的局，没有夜间行动和票型。这里明说缺什么，不用「暂无记录」糊过去——
 * 前者让人知道是版本问题，后者让人以为那几夜真的什么都没发生。
 */
@Composable
private fun IncompleteNote() {
    Text(
        text = "这一局是从旧版存档续下来的，当时还没记夜间行动、票型和逐条发言，所以上面少了那几段。" +
            "下一局开始就会有了。",
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(UIGlassSurfaceStrong)
            .border(0.5.dp, UISeparator, RoundedCornerShape(UIRadiusControl))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = ReviewDim
    )
}
