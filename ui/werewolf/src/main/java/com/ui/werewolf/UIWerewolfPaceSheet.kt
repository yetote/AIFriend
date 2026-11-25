package com.ui.werewolf

// 对局设置面板：思考时限 + 每个座位的耗时账本与思考开关。

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.UIAvatar
import com.ui.common.data.getRandomColor
import com.ui.common.theme.UIDialogButtons
import com.ui.common.theme.UIFieldFill
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarOnlineGreen
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIIosDialog
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UIRadiusSheet
import com.ui.common.theme.UISeparator
import com.ui.common.theme.UIShadowSoft
import com.ui.werewolf.data.UIWerewolfPace
import com.ui.werewolf.data.UIWerewolfRecastCandidate
import com.ui.werewolf.data.UIWerewolfSeatPace
import com.ui.werewolf.data.UIWerewolfTurnLimit

/**
 * 对局设置面板。
 *
 * 为什么做成一层面板而不是另开一页：牌局是在跑的，离开牌桌那几秒里 AI 会接着说，
 * 回来时已经错过了好几条——读不懂局势的人只能重开。面板压在牌桌上，
 * 遮住的只是座位那一块，中间的记录流还在下面继续。
 *
 * 面板里有三件事：
 * 1. **朗读开关**——对局里唯一的朗读入口（顶栏不再放喇叭），用文字把「让不让它出声」写清楚；
 * 2. **思考时限**——这一步最多等多久，超时就兜底并关掉这个座位的深度思考；
 * 3. **每个座位的时间账本**——谁想得最久、超了几次。用户真正想问的
 *    「是不是某个模型拖慢了整局」，答案在这里，而不是在一句笼统的「AI 思考中」。
 */
@Composable
fun UIWerewolfPaceSheet(
    pace: UIWerewolfPace,
    onLimitChange: (UIWerewolfTurnLimit) -> Unit,
    onToggleThinking: (Int, Boolean) -> Unit,
    /** 本局要不要把发言念出来。和开局页是同一个值。 */
    speechOn: Boolean,
    /** 引擎能不能出声；不能时开关仍可点，点开等同于重试一次绑定。 */
    speechReady: Boolean,
    onSpeechChange: (Boolean) -> Unit,
    /** 全局：要不要显示 AI 的思考气泡。和顶栏的思考胶囊是同一个值。 */
    bubbleOn: Boolean,
    onBubbleChange: (Boolean) -> Unit,
    /** 单独开/关某个座位的思考气泡（覆盖全局）。 */
    onSeatBubbleChange: (Int, Boolean) -> Unit,
    /** 局中热换人：点某存活 AI 座位的「换人」按钮时触发。传 null 不显示入口。 */
    onRequestSwapSeat: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    // 遮罩直接吃掉点击：面板外面那层牌桌此刻不该被操作——正在等语音、正在投票，
    // 误触一次就是一句发言或者一张票
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(onClick = onDismiss),
    ) {
        val sheetShape = RoundedCornerShape(topStart = UIRadiusSheet, topEnd = UIRadiusSheet)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(sheetShape)
                .background(Color(0xFFF6F8FC))
                .border(
                    width = 1.dp,
                    color = UIGlassStroke,
                    shape = sheetShape,
                )
                // 滚动交给整个面板：座位列表不再自己滚，手指落在标题、开关还是座位上，
                // 都是同一块面板在滑动，不会出现「在列表里滑不动、要挪到空白才滚」的别扭。
                // verticalScroll 放在圆角/背景内层，背景和圆角固定在面板上不随内容滚走。
                .verticalScroll(rememberScrollState())
                // 面板自己吃掉点击，别让点内容也触发关闭。
                // 必须用 enabled 的 clickable：`clickable(enabled = false)` 不消费触摸，
                // 点座位列表会一路穿到后面的遮罩上，面板当场消失。
                // 放在 verticalScroll 内层：点击照吃，滚动手势照传（tap 不拦 drag）。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            // 顶部拖拽指示条
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0x261B2B45))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "对局设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIFriendBarTitleColor,
                    )
                    Text(
                        text = if (pace.running) "改完立刻生效，不用重开这一局" else "这一局已经结束，设置只作记录",
                        fontSize = 11.5.sp,
                        color = UIFriendBarItemSubtitleColor,
                    )
                }
                Text(
                    text = "完成",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UIFriendBarTagTextBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(UIFriendBarTagTextBlue.copy(alpha = 0.10f))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            // 全局开关收进同一张白色分组卡，和下面的时限卡、座位账本形成清晰层次
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(UIGlassSurfaceStrong)
                    .border(0.7.dp, UIGlassStroke, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                // 朗读开关放在这里：顶栏不再放喇叭，设置面板是对局里唯一的朗读入口。
                // 和开局页改的是同一个值（ViewModel 的 autoSpeak，落在 DataStore 里），不会各说各话。
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "朗读发言",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UIFriendBarTitleColor,
                        )
                        Text(
                            text = if (speechReady) {
                                "只念说出口的话，思考和内心活动不念；音色在各座位上配"
                            } else {
                                "语音引擎还没就绪，打开时会重试一次绑定"
                            },
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = UIFriendBarItemSubtitleColor,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = speechOn,
                        onCheckedChange = onSpeechChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = UIFriendBarTagTextBlue,
                        ),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .height(0.6.dp)
                        .background(UISeparator)
                )

                // 思考气泡的全局开关：和顶栏胶囊同一个值，想找「文字开关」的人在这里读得到。
                // 单个座位的开关在下面的座位行里，长按气泡也能藏掉它。
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "思考气泡",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UIFriendBarTitleColor,
                        )
                        Text(
                            text = "显示 AI 的内心活动；参与局里打开等于作弊。" +
                                "长按某个气泡可以单独藏掉它",
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = UIFriendBarItemSubtitleColor,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = bubbleOn,
                        onCheckedChange = onBubbleChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            // 和座位的「思考气泡」开关同色：绿色＝显示控制，蓝色＝朗读/深度思考。
                            checkedTrackColor = UIFriendBarOnlineGreen,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(UIGlassSurfaceStrong)
                    .border(0.7.dp, UIGlassStroke, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "思考时限",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UIFriendBarTitleColor,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (pace.totalTimeouts > 0) {
                        Text(
                            text = "本局已超时 ${pace.totalTimeouts} 次",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = UIFriendBarRed,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(UIFriendBarRed.copy(alpha = 0.10f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UIWerewolfTurnLimit.entries.forEach { limit ->
                        LimitChip(
                            limit = limit,
                            selected = limit == pace.limit,
                            enabled = pace.running,
                            modifier = Modifier.weight(1f),
                            onClick = { onLimitChange(limit) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = pace.limit.consequence,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = UIFriendBarItemSubtitleColor,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "每个人的用时",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = UIFriendBarTitleColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "每个座位有两个开关：蓝色「深度思考」管 AI 想得深不深，" +
                    "绿色「思考气泡」管你看不看得见它想的内容——这是两回事，别弄混。",
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = UIFriendBarItemSubtitleColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "超时的那一步会按兜底继续；同一座位连续两次超时会自动关闭本局深度思考。" +
                    "想改回来就打开蓝色开关。",
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = UIFriendBarItemSubtitleColor,
            )

            Spacer(Modifier.height(10.dp))
            // 座位列表不再自己滚：滚动已经上移到整个面板，这里只管把卡片排开。
            // 12 个座位全展开也没关系，面板的 max 高度会把超出的部分交给父容器滚。
            Column(
                modifier = Modifier.fillMaxWidth(),
                // 每行是独立卡片，留一点缝把它们分开
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pace.seats.forEach { seat ->
                    SeatPaceRow(
                        seat = seat,
                        enabled = pace.running,
                        onToggle = { on -> onToggleThinking(seat.seat, on) },
                        onSeatBubble = { on -> onSeatBubbleChange(seat.seat, on) },
                        onSwap = if (pace.running && !seat.eliminated && !seat.isHuman && onRequestSwapSeat != null) {
                            { onRequestSwapSeat(seat.seat) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LimitChip(
    limit: UIWerewolfTurnLimit,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(if (selected) UIFriendBarTagTextBlue.copy(alpha = 0.14f) else UIFieldFill)
            .border(
                width = if (selected) 1.2.dp else 1.dp,
                color = if (selected) UIFriendBarTagTextBlue else UIGlassStroke,
                shape = RoundedCornerShape(UIRadiusControl),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = limit.label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) UIFriendBarTagTextBlue else UIFriendBarTitleColor,
        )
        Text(
            text = limit.hint,
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = UIFriendBarItemSubtitleColor,
        )
    }
}

@Composable
private fun SeatPaceRow(
    seat: UIWerewolfSeatPace,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    /** 单独开/关这个座位的思考气泡。覆盖全局开关；想跟全局就拨到和全局一样。 */
    onSeatBubble: (Boolean) -> Unit,
    /** 局中热换人入口；非 null 时显示「换人」胶囊。 */
    onSwap: (() -> Unit)? = null,
) {
    // 用和牌桌头像兜底色同一个函数取色：设置页这一抹颜色，正是牌桌上
    // 这个 AI 的头像色，用户能对上号——不是凭空造一套和牌桌无关的配色。
    val seatColor = getRandomColor(seat.name.ifBlank { "?" })
    val dim = if (seat.eliminated) 0.5f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 统一白底卡片 + 左侧座位身份色条：既锚定「这是哪个 AI」，又避免整屏大色块显杂乱
            .clip(RoundedCornerShape(14.dp))
            .background(UIGlassSurfaceStrong)
            .border(0.8.dp, seatColor.copy(alpha = 0.24f * dim), RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧竖色条：即使头像有图片、兜底色不可见，这条色也在，锚定这一行属于谁。
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(seatColor.copy(alpha = dim))
                .alpha(dim),
        )
        Spacer(Modifier.width(9.dp))

        // 身份锚点：圆形色环 + 头像。色环用的是牌桌同款 getRandomColor(name)，
        // 扫一眼就能和牌桌上的那个 AI 对上号；座位号写在名字下方那行。
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(seatColor.copy(alpha = dim))
                .alpha(dim),
            contentAlignment = Alignment.Center,
        ) {
            UIAvatar(
                name = seat.name,
                avatarPath = seat.avatarPath,
                size = 30.dp,
                shape = CircleShape,
                // 兜底色也强制成座位色，没头像时圆内就是这一色
                fallbackColor = seatColor,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = seat.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (seat.eliminated) UIFriendBarItemSubtitleColor else UIFriendBarTitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (seat.eliminated) {
                    Spacer(Modifier.width(6.dp))
                    Text(text = "已出局", fontSize = 10.sp, color = UIFriendBarItemSubtitleColor)
                } else if (onSwap != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "换人",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = UIFriendBarTagTextBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(UIFriendBarTagTextBlue.copy(alpha = 0.10f))
                            .clickable(onClick = onSwap)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append(seat.seat)
                        append(" 号 · 最近 ")
                        append(seat.lastLabel ?: "—")
                        if (seat.averageLabel != null) {
                            append(" · 平均 ")
                            append(seat.averageLabel)
                        }
                    },
                    fontSize = 11.sp,
                    color = UIFriendBarItemSubtitleColor,
                    maxLines = 1,
                )
                if (seat.timeoutCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "超时 ${seat.timeoutCount} 次",
                        fontSize = 11.sp,
                        color = UIFriendBarRed,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        // 两个开关都把标签放在自己的那一行，而不是挤在正下方：
        // 以前是两个一模一样的蓝开关上下叠、标签各在下面，扫一眼根本分不清
        // 哪个是「AI 想得深不深」、哪个是「你看不看得见它想什么」。
        // 现在每个开关是一个「标签 + 开关」的横向单元，再用颜色区分：
        // 蓝色＝深度思考（改 AI 的行为），绿色＝思考气泡（改你的显示）。
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 深度思考：如果这个角色的模型本来就没开思考能力，那这行就没开关可拨，
            // 直接写明原因，免得用户对着一个不存在的功能纳闷。
            if (seat.thinkingAvailable) {
                SwitchLabelRow(
                    label = "深度思考",
                    labelColor = UIFriendBarTagTextBlue,
                    checked = seat.thinkingOn,
                    trackColor = UIFriendBarTagTextBlue,
                    enabled = enabled,
                    onCheckedChange = { on -> if (enabled) onToggle(on) },
                )
            } else {
                Text(
                    text = "未开思考",
                    fontSize = 10.5.sp,
                    color = UIFriendBarItemSubtitleColor,
                )
            }
            SwitchLabelRow(
                label = "思考气泡",
                labelColor = UIFriendBarOnlineGreen,
                checked = seat.bubbleVisible,
                trackColor = UIFriendBarOnlineGreen,
                enabled = true,
                onCheckedChange = onSeatBubble,
            )
        }
    }
}

/** 一个开关 + 它自己的文字标签，排在同一行。标签放左边，颜色用来区分两种开关。 */
@Composable
private fun SwitchLabelRow(
    label: String,
    labelColor: Color,
    checked: Boolean,
    trackColor: Color,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor,
        )
        Spacer(Modifier.width(5.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = trackColor,
            ),
        )
    }
}

/**
 * 局中热换人弹窗：在不退出牌桌的情况下，把单个 AI 座位替换成另一位不在场上的好友。
 */
@Composable
fun UIWerewolfSwapSeatDialog(
    seat: Int,
    oldName: String,
    oldModel: String,
    candidates: List<UIWerewolfRecastCandidate>,
    onConfirm: (friendId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember(seat, candidates) {
        mutableStateOf(candidates.firstOrNull { it.reachable }?.id)
    }
    UIIosDialog(
        title = "更换 $seat 号位 AI",
        subtitle = buildString {
            append("原 $oldName")
            if (oldModel.isNotBlank()) append("（$oldModel）")
            append("。换人后身份、死活、历史进度全部保留，并自动接着打。")
        },
        onDismiss = onDismiss,
        actions = {
            UIDialogButtons(
                confirmText = if (picked != null) "换人并继续" else "请选择好友",
                confirmEnabled = picked != null,
                onConfirm = { picked?.let(onConfirm) },
                onCancel = onDismiss,
            )
        },
    ) {
        if (candidates.isEmpty()) {
            Text(
                text = "当前没有不在场上的备用好友可换。可以先回好友页再建一位好友。",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = UIFriendBarRed,
            )
        } else {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                candidates.forEach { candidate ->
                    val selected = picked == candidate.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(UIRadiusControl))
                            .background(
                                if (selected) UIFriendBarTagTextBlue.copy(alpha = 0.10f)
                                else UIFieldFill
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) UIFriendBarTagTextBlue else UIGlassStroke,
                                shape = RoundedCornerShape(UIRadiusControl),
                            )
                            .clickable { picked = candidate.id }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = candidate.name,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = UIFriendBarTitleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = candidate.model,
                                fontSize = 11.sp,
                                color = UIFriendBarItemMetaColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (candidate.reachable) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "推荐",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UIFriendBarTagTextBlue,
                            )
                        }
                    }
                }
            }
        }
    }
}
