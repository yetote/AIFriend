package com.ui.werewolf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.pageInsetsBottom
import com.ui.common.pageInsetsTop
import com.ui.common.theme.UIBlobBlue
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarSubtitleColor
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIIosDialog
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIHeroBrush
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UITopBar
import com.ui.common.theme.uiCard
import com.ui.werewolf.data.UIWerewolfNotice
import com.ui.werewolf.data.UIWerewolfRecast
import com.ui.werewolf.data.UIWerewolfRecastCandidate
import com.ui.werewolf.data.UIWerewolfRecastSeat
import com.ui.werewolf.data.UIWerewolfRecord

/**
 * 历史对局页，狼人杀的入口。
 *
 * 为什么要有这一页：上一版是「进来就自动回到上次的牌桌」。这在一部只玩一局的手机上
 * 说得通，但代价是——想开新局得先找到「退出当前局」，想看看上周那局打得怎么样完全没入口，
 * 而且隔了三天再点进来，人会先愣一下「我什么时候坐到桌上的」。
 *
 * 所以入口先摆事实：有哪几局没打完、哪几局已经结束。看完再决定接着打还是重开。
 */
@Composable
fun UIWerewolfHistoryScreen(
    records: List<UIWerewolfRecord>,
    /** 点了「接着打」、正在探活的那一局。探活可能要几秒，按钮得有反应。 */
    resumingId: String?,
    error: String?,
    onClearError: () -> Unit,
    onResume: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
    onDiscard: (String) -> Unit,
    /**
     * 导出这一局的对局文件（`.wwg`）。
     *
     * 任何一局都能导，包括还没打完的：导出来是当前进度，换台手机导进去可以接着打。
     * 放在列表而不是牌桌上——用户想带走一局时，人通常在列表里挑，不在牌桌上。
     */
    onExportGame: (String) -> Unit = {},
    /**
     * 导出复盘 Markdown。只对**已结束**的局提供。
     *
     * 复盘得有「谁赢了」，没打完的局生不出这么一份文档；硬导只会得到一份
     * 没有结局的战报，比不给更让人困惑。
     */
    onExportMarkdown: (String) -> Unit = {},
    onClearFinished: () -> Unit,
    onNewGame: () -> Unit,
    onBack: () -> Unit,
    /** 导入对局文件。null 表示这个环境没有文件选择器（例如预览）。 */
    onImport: (() -> Unit)? = null,
    /** 导入结果这类「做成了 / 没做成」的提示，和 [error] 分开：它是蓝的，不是红的。 */
    notice: UIWerewolfNotice? = null,
    onClearNotice: () -> Unit = {},
    /** 续档连不上时的换人面板数据。null = 没有要换的人。 */
    recast: UIWerewolfRecast? = null,
    /** 选好每个座位的替身后提交。参数是「座位号 → 新好友 id」。 */
    onSwapSeats: (Map<Int, String>) -> Unit = {},
    onDismissRecast: () -> Unit = {},
) {
    // 未打完的排前面：一类是「接着打」，一类是「翻旧账」，混在一起排会看不出区别
    val unfinished = remember(records) { records.filter { !it.finished } }
    val finished = remember(records) { records.filter { it.finished } }

    // 放弃是不可逆的（存档直接删），所以只在这一页做二次确认，不在列表行里直接删
    var pendingDiscard by remember { mutableStateOf<UIWerewolfRecord?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    // 导出：已结束的局弹「Markdown / 对局文件」二选一；没打完的局没有复盘文档，
    // 点一下直接导存档，不弹只有一个选项的框。
    var exportTarget by remember { mutableStateOf<UIWerewolfRecord?>(null) }

    // 换人面板：错误条上的「换人」打开它。选择按座位存，提交时摊成 Map。
    var recastOpen by remember { mutableStateOf(false) }
    var picks by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val onOpenRecast: () -> Unit = {
        picks = emptyMap()
        recastOpen = true
    }

    UIGradientBackground(modifier = Modifier.pageInsetsTop()) {
        Column(modifier = Modifier.fillMaxSize()) {
            UITopBar(title = "狼人杀", onBack = onBack) {
                // 导入放在「几局」前面：它是个动作，不是统计。列表为空时也在，
                // 否则第一台新手机没有任何存档，用户根本找不到导入的入口
                if (onImport != null) {
                    Text(
                        text = "导入",
                        modifier = Modifier
                            .clip(RoundedCornerShape(UIRadiusControl))
                            .clickable { onImport() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = UIFriendBarTagTextBlue
                    )
                }
                if (records.isNotEmpty()) {
                    Text(
                        text = "${records.size} 局",
                        modifier = Modifier.padding(end = 12.dp),
                        fontSize = 13.sp,
                        color = UIFriendBarItemSubtitleColor
                    )
                }
            }

            // 读档失败、续档时连通不上之类都落在这条上。列表页也要能说出来，
            // 否则点「接着打」之后什么都没发生，用户只会以为按钮坏了
            if (error != null) {
                HistoryBar(
                    text = error,
                    tint = UIFriendBarRed,
                    onDismiss = onClearError,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    actionLabel = if (recast != null) "换人" else null,
                    onAction = onOpenRecast,
                )
            }
            if (notice != null) {
                HistoryBar(
                    text = notice.text,
                    tint = if (notice.error) UIFriendBarRed else UIFriendBarTagTextBlue,
                    onDismiss = onClearNotice,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (records.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (unfinished.isNotEmpty()) {
                        item(key = "unfinished-title") {
                            SectionHeader(
                                title = "没打完的",
                                hint = "接着上次停下的阶段继续"
                            )
                        }
                        items(unfinished, key = { it.id }) { record ->
                            ResumeCard(
                                record = record,
                                resuming = record.id == resumingId,
                                onResume = { onResume(record.id) },
                                // 没打完：没有复盘可导，直接给存档（点开就是系统分享面板）
                                onExport = { onExportGame(record.id) },
                                onDiscard = { pendingDiscard = record }
                            )
                        }
                    }

                    if (finished.isNotEmpty()) {
                        item(key = "finished-title") {
                            SectionHeader(
                                modifier = Modifier.padding(top = if (unfinished.isEmpty()) 0.dp else 8.dp),
                                title = "已结束的",
                                hint = "点开看那一局所有人的底牌",
                                // 只有攒到三局以上才提「清空」：刚打完一局就催人删记录，
                                // 会让人以为这些数据是负担
                                action = "清空".takeIf { finished.size >= 3 },
                                onAction = { confirmClear = true }
                            )
                        }
                        item(key = "finished-list") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .uiCard(shape = RoundedCornerShape(UIRadiusCard))
                                    .padding(vertical = 4.dp)
                            ) {
                                finished.forEach { record ->
                                    FinishedRow(
                                        record = record,
                                        onClick = { onOpenRecord(record.id) },
                                        onExport = { exportTarget = record },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            NewGameBar(onClick = onNewGame)
        }
    }

    if (recastOpen && recast != null) {
        RecastDialog(
            data = recast,
            picks = picks,
            onPick = { seat, friendId -> picks = picks + (seat to friendId) },
            onDismiss = {
                recastOpen = false
                onDismissRecast()
            },
            onConfirm = {
                recastOpen = false
                onSwapSeats(picks)
            },
        )
    }

    exportTarget?.let { record ->
        UIWerewolfExportDialog(
            onDismiss = { exportTarget = null },
            onMarkdown = {
                exportTarget = null
                onExportMarkdown(record.id)
            },
            onGame = {
                exportTarget = null
                onExportGame(record.id)
            },
        )
    }

    pendingDiscard?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDiscard = null },
            title = { Text("放弃这一局？", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "${record.dayLabel} · ${record.phaseLabel}。" +
                        "删掉之后只能重新开一局，这一局的记录看不回来了。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = UIFriendBarSubtitleColor
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDiscard(record.id)
                    pendingDiscard = null
                }) {
                    Text("删除这一局", color = UIFriendBarRed, fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscard = null }) {
                    Text("留着", color = UIFriendBarItemSubtitleColor, fontSize = 14.sp)
                }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空已结束的对局？", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "${finished.size} 局的记录和复盘都会被删掉，没打完的那几局不受影响。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = UIFriendBarSubtitleColor
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearFinished()
                    confirmClear = false
                }) {
                    Text("清空", color = UIFriendBarRed, fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("算了", color = UIFriendBarItemSubtitleColor, fontSize = 14.sp)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// 各区块
// ---------------------------------------------------------------------------

/**
 * 列表页顶部的一条横条。
 *
 * 失败和成功共用一套骨架，只换颜色：两条横条各写一份的话，
 * 圆角、内边距、「知道了」这三个细节迟早会长歪，看起来像两个不同的控件。
 */
@Composable
private fun HistoryBar(
    text: String,
    tint: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(tint.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            color = tint
        )
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tint.copy(alpha = 0.16f))
                    .clickable { onAction() }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
            Spacer(Modifier.padding(horizontal = 2.dp))
        }
        Text(
            text = "知道了",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { onDismiss() }
                .padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = tint
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = UIFriendBarTitleColor
            )
            Text(
                text = hint,
                fontSize = 12.sp,
                color = UIFriendBarItemSubtitleColor
            )
        }
        if (action != null) {
            Text(
                text = action,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onAction() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = UIFriendBarItemSubtitleColor
            )
        }
    }
}

/**
 * 没打完的那一局。
 *
 * 用深色卡片，和下面的浅色列表拉开：它是**唯一一个能延续的动作**，
 * 其余都是资料。一屏里最重的颜色应该落在「你最可能想点的那个」上面。
 */
@Composable
private fun ResumeCard(
    record: UIWerewolfRecord,
    resuming: Boolean,
    onResume: () -> Unit,
    onExport: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(UIRadiusCard),
                clip = false,
                ambientColor = UIBlobBlue,
                spotColor = UIBlobBlue
            )
            .clip(RoundedCornerShape(UIRadiusCard))
            .background(UIHeroBrush)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${record.dayLabel} · ${record.phaseLabel}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${record.metaLabel} · ${record.timeLabel}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }
            // 导出放在右上角：它是「把这一局带走」，和「接着打 / 放弃」不是一类动作，
            // 混在下面那排按钮里会被当成第三个选项
            IconButton(onClick = onExport, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Filled.IosShare,
                    contentDescription = "导出这一局",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f))
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (resuming) 0.7f else 1f))
                    // 探活还没回来时禁用：否则连点几次会叠出好几轮探活
                    .clickable(enabled = !resuming) { onResume() }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (resuming) "连接中…" else "接着打",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UIFriendBarTitleColor
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .clickable(enabled = !resuming) { onDiscard() }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "放弃这一局",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

/** 已结束的一局：一行信息，点开是复盘；右边那个图标是导出。 */
@Composable
private fun FinishedRow(
    record: UIWerewolfRecord,
    onClick: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "${record.dayLabel} · ${record.winnerLabel ?: "已结束"}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = UIFriendBarTitleColor
            )
            Text(
                text = "${record.metaLabel} · ${record.timeLabel}",
                fontSize = 12.5.sp,
                color = UIFriendBarItemSubtitleColor
            )
        }
        // 导出图标点击是独立的：整行点开复盘，点这里只导出，不进复盘页
        IconButton(onClick = onExport, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Filled.IosShare,
                contentDescription = "导出这一局",
                tint = UIFriendBarItemMetaColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "看复盘",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = UIFriendBarTagTextBlue
        )
    }
}

/**
 * 一次都没有时的空白页。
 *
 * 不留一整页空白：第一次进来的人不知道这里能干什么，
 * 而「开一局」的按钮就在下面，得给一句能对上按钮的话。
 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "还没有对局",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = UIFriendBarTitleColor
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "挑几个好友凑一桌，你也能坐进去。打完的每一局都会留在这里，随时能翻回来看底牌。",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = UIFriendBarItemSubtitleColor,
            textAlign = TextAlign.Center
        )
    }
}

/** 底部「新开一局」。这一页唯一的主行动。 */
@Composable
private fun NewGameBar(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 12.dp)
            .pageInsetsBottom()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(UIRadiusControl),
                    clip = false,
                    ambientColor = UIBlobBlue,
                    spotColor = UIBlobBlue
                )
                .clip(RoundedCornerShape(UIRadiusControl))
                .background(UIActionBrush)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "新开一局",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

/**
 * 续档有人用不了时的换人面板。
 *
 * 两种坏法都在这个面板里解决：
 * - **好友已经不在了**（被删了 / 换设备没同步）：这个座位连探活都做不了，只能换；
 * - **连不上**（Key 过期、模型下线）：换上去一样能继续。
 *
 * 每个坏座位一张卡，从候选好友里挑一个顶上；探活是绿的标「推荐」。
 * 全部座位都选了才能提交——漏一个就等于那一局还是打不下去，
 * 与其让他点完才发现卡住，不如把按钮先按住。
 */
@Composable
private fun RecastDialog(
    data: UIWerewolfRecast,
    picks: Map<Int, String>,
    onPick: (seat: Int, friendId: String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val allPicked = data.seats.all { picks[it.seat] != null }
    // 同一个好友不能顶两个座位（牌桌会出现两个同名同模型的人）
    val unique = picks.values.toSet().size == picks.values.size
    val canSubmit = allPicked && unique
    UIIosDialog(
        title = "换人继续这一局",
        subtitle = if (data.reason.isNotBlank()) {
            "${data.reason}。换人后身份、历史、进度都保留。"
        } else {
            "原座位有人用不了了。换人后身份、历史、进度都保留。"
        },
        onDismiss = onDismiss,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消", color = UIFriendBarItemSubtitleColor, fontSize = 14.sp)
                }
                TextButton(
                    onClick = onConfirm,
                    enabled = canSubmit,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = when {
                            !allPicked -> "请为每个座位选人"
                            !unique -> "别选重复的好友"
                            else -> "换人并继续"
                        },
                        color = if (canSubmit) UIFriendBarTagTextBlue else UIFriendBarItemMetaColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            data.seats.forEach { seat ->
                RecastSeatCard(
                    seat = seat,
                    picked = picks[seat.seat],
                    onPick = { onPick(seat.seat, it) },
                )
            }
        }
    }
}

/** 一个要换人的座位：原来是谁 + 候选好友单选。 */
@Composable
private fun RecastSeatCard(
    seat: UIWerewolfRecastSeat,
    picked: String?,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (seat.gone) {
                "${seat.seat} 号 · 原 ${seat.oldName}（${seat.oldModel}）已经不在了"
            } else {
                "${seat.seat} 号 · 原 ${seat.oldName}（${seat.oldModel}）连不上"
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = UIFriendBarTitleColor,
        )
        if (seat.candidates.isEmpty()) {
            Text(
                text = "没有可换的好友，去好友页加一个能连上的再来",
                fontSize = 12.sp,
                color = UIFriendBarRed,
            )
        } else {
            seat.candidates.forEach { candidate -> RecastCandidateRow(candidate, picked, onPick) }
        }
    }
}

/** 候选好友的一行：点一下即选中，当前选中的描蓝边。 */
@Composable
private fun RecastCandidateRow(
    candidate: UIWerewolfRecastCandidate,
    picked: String?,
    onPick: (String) -> Unit,
) {
    val selected = picked == candidate.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(
                if (selected) UIFriendBarTagTextBlue.copy(alpha = 0.10f)
                else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (selected) UIFriendBarTagTextBlue else UIFriendBarItemMetaColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(UIRadiusControl),
            )
            .clickable { onPick(candidate.id) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = candidate.name, fontSize = 13.sp, color = UIFriendBarTitleColor)
            Text(
                text = candidate.model,
                fontSize = 11.sp,
                color = UIFriendBarItemMetaColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (candidate.reachable) {
            Text(
                text = "推荐",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = UIFriendBarTagTextBlue,
            )
        }
    }
}
