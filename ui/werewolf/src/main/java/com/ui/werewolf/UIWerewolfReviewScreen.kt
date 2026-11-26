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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import com.ui.common.theme.UIGlassBar
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIIsDarkTheme
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UISeparator
import com.ui.common.theme.uiCard
import com.ui.common.theme.uiHairlineBottom
import com.ui.werewolf.data.UIWerewolfBallotLine
import com.ui.werewolf.data.UIWerewolfCheckReport
import com.ui.werewolf.data.UIWerewolfDayReport
import com.ui.werewolf.data.UIWerewolfDialogueDay
import com.ui.werewolf.data.UIWerewolfGroupKind
import com.ui.werewolf.data.UIWerewolfBubbleKind
import com.ui.werewolf.data.UIWerewolfNightReport
import com.ui.werewolf.data.UIWerewolfNightDetail
import com.ui.werewolf.data.UIWerewolfReview
import com.ui.werewolf.data.UIWerewolfRevealedSeat
import com.ui.werewolf.data.UIWerewolfRoleGroup
import com.ui.werewolf.data.UIWerewolfSpeechLine
import com.ui.werewolf.data.bubbleStyle
import com.ui.werewolf.data.UITokenSeatTotal
import com.ui.werewolf.data.UITokenUsage

// ---------------------------------------------------------------------------
// 配色
//
// 复盘页是「白天的、安静的」：一局已经结束了，不需要再制造紧张感。
// 只有狼人的红和神职的蓝留着重色，其余一律灰——颜色只用来标记阵营，
// 不用来装饰。赢家横幅例外，那是整页唯一需要「一眼看到」的东西。
// ---------------------------------------------------------------------------

private val ReviewTitle: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0xFFF3ECE2) else Color(0xFF171411)
private val ReviewBody: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0xFFD6CAC0) else Color(0xFF3E362E)
private val ReviewDim: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0xFF9C8E80) else Color(0xFF7B7166)

private val WolfRed: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0xFFE86660) else Color(0xFFC2302B)
private val GodBlue: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0xFF8AB4F8) else Color(0xFF2F5DA8)
private val VillagerGray: Color
    @Composable @ReadOnlyComposable get() = if (UIIsDarkTheme) Color(0xFF9C8E80) else Color(0xFF7B7166)

private val WolfBanner = listOf(Color(0xFF17100E), Color(0xFF5A1D19))
private val GoodBanner = listOf(Color(0xFF171412), Color(0xFF283E66))

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
    /**
     * 演出这一局：把存档当成正在发生重放一遍（座位、昼夜、气泡、朗读）。
     *
     * 参数是「当前是不是上帝视角」——演出要按同一个视角放，否则用户在复盘里
     * 打开了上帝视角、演出里又变回只看得见公开发言，会以为丢了一段。
     */
    onPlay: ((godView: Boolean) -> Unit)? = null,
    /**
     * 导出「对局文件」：一份能再导入回 App 的完整存档，换台手机也能接着重看、重演。
     *
     * 和 [onExport] 并列而不是替代它：Markdown 是给人读的，对局文件是给 App 读的。
     * 两个都给的时候，顶栏那个「导出」会变成一个二选一的小弹窗。
     */
    onExportGame: (() -> Unit)? = null,
) {
    // 「导出」点开之后的选择框。只有一种导出方式时不弹，直接执行——
    // 一个只有一项的菜单比按钮本身更麻烦。
    var exportChoice by rememberSaveable { mutableStateOf(false) }
    // 「上帝视角」默认关闭：狼人夜聊、查验这些当时只有个别人看得到的内容，
    // 需要玩家主动打开。默认摊开的话，「原来那一夜他们商量的是这个」就没得发现了。
    var godView by rememberSaveable { mutableStateOf(false) }
    // 对话区按座位筛选（null = 全部；非空 = 只看该座位的发言与行动）
    var filterSeat by rememberSaveable { mutableStateOf<Int?>(null) }
    val allSeats = review.groups.flatMap { it.members }.sortedBy { it.seat }
    // 这一天在当前视角+筛选下有没有可读的内容。
    val dialogueDays = review.dialogues.mapNotNull { day ->
        val base = if (godView) day.lines else day.open
        val filtered = if (filterSeat == null) base else base.filter { it.seat == filterSeat }
        if (filtered.isEmpty()) null else day to filtered
    }
    val hasSecret = review.dialogues.any { it.hasSecret }

    UIGradientBackground(modifier = Modifier.pageInsetsTop()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val bothExports = onExport != null && onExportGame != null
            ReviewTopBar(
                onBack = onBack,
                onExport = when {
                    bothExports -> ({ exportChoice = true })
                    else -> onExport ?: onExportGame
                },
                exportLabel = if (bothExports || onExportGame != null) "导出" else "导出 MD",
                onPlay = onPlay?.let { play -> { play(godView) } }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { WinnerBanner(review) }

                // token 汇总：有人关心「这局烧了多少」，但不想为它占一整屏，
                // 默认折起来只露总量，展开才按人排开。
                if (review.tokenBySeat.isNotEmpty()) {
                    item {
                        TokenSummaryCard(rows = review.tokenBySeat, total = review.tokenTotal)
                    }
                }

                review.selfLine?.let { line ->
                    item { SelfStrip(line = line) }
                }

                // 这一局按什么设定打的。改过设置之后旧局在设置页里就查不到了，
                // 而「AI 今天怎么这么打」的第一嫌疑对象就是它。
                review.customPrompt?.let { prompt ->
                    item { PromptCard(prompt) }
                }

                // 第 1 夜抽到的狼队开局风向卡（终局复盘全部公开）
                review.wolfStyleCard?.let { card ->
                    item { WolfStyleReviewCard(card) }
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

                if (review.nightDetails.isNotEmpty()) {
                    item { SectionTitle(title = "夜间明细", hint = "谁做了什么、为什么") }
                    review.nightDetails.forEach { detail ->
                        item(key = "night-detail-${detail.day}") { NightDetailCard(detail) }
                    }
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
                    if (allSeats.isNotEmpty()) {
                        item {
                            SeatFilterRow(
                                seats = allSeats,
                                selectedSeat = filterSeat,
                                onSelect = { filterSeat = it },
                            )
                        }
                    }
                    dialogueDays.forEach { (day, filteredLines) ->
                        item(key = "dialogue-${day.day}-${filterSeat ?: 0}") {
                            DialogueDayCard(
                                day = day,
                                lines = filteredLines,
                                showSecret = godView,
                                defaultExpanded = filterSeat != null,
                            )
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

        if (exportChoice) {
            UIWerewolfExportDialog(
                onDismiss = { exportChoice = false },
                onMarkdown = onExport?.let { action ->
                    {
                        exportChoice = false
                        action()
                    }
                },
                onGame = onExportGame?.let { action ->
                    {
                        exportChoice = false
                        action()
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 顶栏与横幅
// ---------------------------------------------------------------------------

@Composable
private fun ReviewTopBar(
    onBack: () -> Unit,
    onExport: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
    exportLabel: String = "导出 MD",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UIGlassBar)
            .uiHairlineBottom(UIGlassStroke)
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
        if (onPlay != null) {
            // 演出放在导出左边、用实心按钮：它是这一页真正的「再看一遍」，
            // 导出是事后动作。文字写「演出」而不是「回放」——这一页已经是复盘，
            // 再叫「回放」会分不清是「回看记录」还是「重演一遍」。
            Text(
                text = "演出",
                modifier = Modifier
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .background(UIActionBrush)
                    .clickable { onPlay() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(Modifier.width(6.dp))
        }
        if (onExport != null) {
            // 文字按钮而不是图标：分享图标在这里语义太宽（分享这一局？分享一局战绩？），
            // 写「导出 MD」不用猜，也说明了导出的格式；两种导出都在时写「导出」，点开再选
            Text(
                text = exportLabel,
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
    val bannerShape = RoundedCornerShape(UIRadiusCard)
    val glowColor = if (review.wolfWon) Color(0x40FF6B6B) else Color(0x406EA8FF)
    val shadowTint = if (review.wolfWon) Color(0x386E1E29) else Color(0x381D4272)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = bannerShape,
                clip = false,
                ambientColor = shadowTint,
                spotColor = shadowTint,
            )
            .clip(bannerShape)
            .background(Brush.linearGradient(if (review.wolfWon) WolfBanner else GoodBanner))
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.16f),
                        radius = size.width * 0.55f,
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.88f, size.height * 0.16f),
                )
            }
            .border(0.8.dp, Color(0x2EFFFFFF), bannerShape)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Text(
            text = if (review.wolfWon) "终局战报 · 狼人阵营" else "终局战报 · 好人阵营",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x24FFFFFF))
                .padding(horizontal = 9.dp, vertical = 3.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.88f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = review.winnerLabel,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            color = Color.White
        )
        Text(
            text = review.reason,
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = Color.White.copy(alpha = 0.85f)
        )
        Text(
            text = review.facts,
            modifier = Modifier.padding(top = 12.dp),
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.62f)
        )
    }
}

/**
 * token 消耗汇总。
 *
 * 默认只露整局总量——复盘的第一屏还是胜负和底牌，账单不是主角。
 * 展开后按座位降序：谁说得最多、谁把推理写成了小作文，一眼能看出来。
 * 只要有一段是估算，整行就带「约」并注明不能拿去对账。
 */
@Composable
private fun TokenSummaryCard(rows: List<UITokenSeatTotal>, total: UITokenUsage) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 6.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Token 消耗",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReviewTitle
            )
            Spacer(Modifier.weight(1f))
            Text(text = total.fullLabel, fontSize = 11.sp, color = ReviewDim)
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (expanded) "收起" else "展开",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (expanded) ReviewDim else GodBlue
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.displayLabel,
                        fontSize = 11.sp,
                        color = ReviewBody,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = row.usage.fullLabel, fontSize = 10.sp, color = ReviewDim)
                }
            }
            if (total.estimated) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "部分数字为本地估算，实际消耗以平台账单为准。",
                    fontSize = 10.sp,
                    color = ReviewDim
                )
            }
        }
    }
}

/** 你在这局里是谁。观战的局没有这一条。 */
@Composable
private fun SelfStrip(line: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(Color(0x142F5DA8))
            .border(0.5.dp, Color(0x262F5DA8), RoundedCornerShape(UIRadiusControl))
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

/**
 * 这一局生效的玩家设定（提示词）。
 *
 * 默认只显示三行，点开看全文：提示词经常是几百字，全摊开会把底牌挤到屏幕外，
 * 而它多半是「AI 今天怎么这么打」时的查证材料，不是要逐字读完的东西。
 *
 * 只读。想改就去设置页——那里改的是之后所有局，这一局的记录不该被覆盖。
 */
@Composable
private fun PromptCard(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusCard))
            .background(UIGlassSurfaceStrong)
            .border(1.dp, UIGlassStroke, RoundedCornerShape(UIRadiusCard))
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "这一局用的提示词",
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReviewTitle
            )
            Text(
                text = if (expanded) "收起" else "展开",
                fontSize = 11.sp,
                color = GodBlue
            )
        }
        Text(
            text = text.trim(),
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 11.sp,
            lineHeight = 17.sp,
            color = ReviewBody,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
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
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 6.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(accent, CircleShape)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = group.title,
                fontSize = 12.5.sp,
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
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 6.dp)
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

/**
 * 一夜的明细卡。
 *
 * 上面四行是摘要（狼队定刀 / 守卫 / 预言家 / 女巫），不空才显示；
 * 下面折起来的「夜里他们说了什么」是原话，默认收起——
 * 一局十几夜，每夜好几句，全摊开复盘页就翻不动了。
 */
@Composable
private fun NightDetailCard(detail: UIWerewolfNightDetail) {
    var expanded by rememberSaveable(detail.day) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 6.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = "第 ${detail.day} 夜",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = ReviewTitle
        )
        listOfNotNull(
            detail.wolfPlan?.let { "狼队" to it },
            detail.guardPlan?.let { "守卫" to it },
            detail.seerPlan?.let { "预言家" to it },
            detail.witchPlan?.let { "女巫" to it },
        ).forEach { (who, what) ->
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = who,
                    modifier = Modifier.width(40.dp),
                    fontSize = 10.sp,
                    color = ReviewDim
                )
                Text(
                    text = what,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = ReviewBody
                )
            }
        }

        if (detail.lines.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (expanded) "收起夜里的话" else "夜里他们说了什么（${detail.lines.size} 条）",
                modifier = Modifier.clickable { expanded = !expanded },
                fontSize = 10.sp,
                color = GodBlue
            )
            if (expanded) {
                detail.lines.forEach { line ->
                    val style = bubbleStyle(line.kind, night = UIIsDarkTheme)
                    UIWerewolfBubble(
                        style = style,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp),
                        corner = 8.dp,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = buildString {
                                    append(line.speaker)
                                    line.roleLabel?.let { append("（").append(it).append("）") }
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = style.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(5.dp))
                            BubbleTag(text = line.label, accent = style.accent)
                        }
                        Text(
                            text = line.text,
                            modifier = Modifier.padding(top = 2.dp),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = ReviewBody
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NightCard(nights: List<UIWerewolfNightReport>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 6.dp)
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
                    if (night.mutualProtection) {
                        Text(
                            text = "守救同归：他被守卫守到又被女巫救了，按规则同归于尽",
                            modifier = Modifier.padding(top = 2.dp),
                            fontSize = 10.sp,
                            color = WolfRed
                        )
                    }
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
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 6.dp)
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
 * 一天的对话。默认收起；按座位筛选时自动展开，方便连贯查看某位玩家全场的逻辑。
 */
@Composable
private fun DialogueDayCard(
    day: UIWerewolfDialogueDay,
    lines: List<UIWerewolfSpeechLine>,
    showSecret: Boolean,
    defaultExpanded: Boolean = false,
) {
    var expanded by rememberSaveable(day.day, defaultExpanded) { mutableStateOf(defaultExpanded) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 6.dp)
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
            if (day.tokenTotal.total > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = day.tokenTotal.shortLabel,
                    fontSize = 10.sp,
                    color = ReviewDim
                )
            }
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

@Composable
private fun WolfStyleReviewCard(card: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 4.dp)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 12.dp)
                    .background(WolfRed, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "本局狼队开局风向卡",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WolfRed,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "首夜随机启发",
                fontSize = 10.sp,
                color = ReviewDim,
            )
        }
        Text(
            text = card,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = ReviewBody,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeatFilterRow(
    seats: List<UIWerewolfRevealedSeat>,
    selectedSeat: Int?,
    onSelect: (Int?) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SeatFilterPill(
            label = "全部",
            selected = selectedSeat == null,
            onClick = { onSelect(null) },
        )
        seats.forEach { seat ->
            SeatFilterPill(
                label = "${seat.seat}号",
                selected = selectedSeat == seat.seat,
                onClick = { onSelect(if (selectedSeat == seat.seat) null else seat.seat) },
            )
        }
    }
}

@Composable
private fun SeatFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color(0x1A2F5DA8) else UIFieldFill)
            .border(
                0.6.dp,
                if (selected) GodBlue.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) GodBlue else ReviewDim,
    )
}

/**
 * 一句话。
 *
 * 每条都套一个气泡，颜色和样式按**性质**分：
 * 公开=中性、投票=琥珀、密谈=紫、播报=居中分隔线、思考=虚线框。
 *
 * 分这么细是有必要的：复盘里最要紧的区分就是「这句是场上说的，还是夜里悄悄说的」，
 * 而夜里的话解释了白天的一切。混在一列里读，三句就分不清了。
 */
@Composable
private fun DialogueLine(line: UIWerewolfSpeechLine) {
    val kind = line.kind
    if (kind == UIWerewolfBubbleKind.BROADCAST || line.seat == null) {
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

    val style = bubbleStyle(kind, night = UIIsDarkTheme)
    val bubbleShape = if (kind == UIWerewolfBubbleKind.SPEECH) {
        SpeechBubbleShape
    } else {
        RoundedCornerShape(10.dp)
    }
    // 头像收进气泡头部，气泡铺满整行；和对局里的发言卡保持同一套排版。
    UIWerewolfBubble(
        style = style,
        modifier = Modifier.fillMaxWidth(),
        shape = bubbleShape,
        elevation = 1.5.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            UIAvatar(
                name = line.speaker,
                avatarPath = line.avatarPath,
                size = 20.dp,
                shape = CircleShape
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = line.speaker,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = style.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            line.roleLabel?.let { role ->
                RoleTag(text = role, kind = line.roleKind)
            }
            Spacer(Modifier.width(5.dp))
            BubbleTag(text = line.label, accent = style.accent)
        }
        Text(
            text = line.text,
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = ReviewBody
        )
        if (line.usage != null && line.usage.total > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = line.usage.shortLabel,
                fontSize = 9.sp,
                color = ReviewDim
            )
        }
        if (line.reasoning.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            ReviewThought(reasoning = line.reasoning)
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
    val style = bubbleStyle(UIWerewolfBubbleKind.THOUGHT, night = UIIsDarkTheme)

    // 虚线框：这是「他说出口的话之外的东西」，实线框会和发言气泡混在一起
    UIWerewolfBubble(
        style = style,
        modifier = Modifier.fillMaxWidth(),
        corner = 8.dp,
        accentBar = false,
        dashed = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            BubbleTag(text = style.tag, accent = style.accent)
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (expanded) "收起" else "展开",
                modifier = Modifier.weight(1f),
                fontSize = 9.sp,
                color = ReviewDim
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
