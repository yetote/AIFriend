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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.ui.common.theme.UIFieldFill
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.werewolf.data.UIWerewolfPace
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
 * 1. **朗读开关**——顶栏喇叭图标的展开版，想找「让不让它出声」的人在这里能读到文字；
 * 2. **思考时限**——这一步最多等多久，超时就兜底并关掉这个座位的深度思考；
 * 3. **每个座位的时间账本**——谁想得最久、超了几次。用户真正想问的
 *    「是不是某个模型拖慢了整局」，答案在这里，而不是在一句笼统的「AI 思考中」。
 */
@Composable
fun UIWerewolfPaceSheet(
    pace: UIWerewolfPace,
    onLimitChange: (UIWerewolfTurnLimit) -> Unit,
    onToggleThinking: (Int, Boolean) -> Unit,
    /** 本局要不要把发言念出来。和顶栏的喇叭是同一个值。 */
    speechOn: Boolean,
    /** 引擎能不能出声；不能时开关仍可点，点开等同于重试一次绑定。 */
    speechReady: Boolean,
    onSpeechChange: (Boolean) -> Unit,
    /** 全局：要不要显示 AI 的思考气泡。和顶栏的思考胶囊是同一个值。 */
    bubbleOn: Boolean,
    onBubbleChange: (Boolean) -> Unit,
    /** 单独开/关某个座位的思考气泡（覆盖全局）。 */
    onSeatBubbleChange: (Int, Boolean) -> Unit,
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
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(topStart = UIRadiusCard, topEnd = UIRadiusCard))
                .background(UIGlassSurfaceStrong)
                .border(
                    width = 1.dp,
                    color = UIGlassStroke,
                    shape = RoundedCornerShape(topStart = UIRadiusCard, topEnd = UIRadiusCard),
                )
                // 面板自己吃掉点击，别让点内容也触发关闭。
                // 必须用 enabled 的 clickable：`clickable(enabled = false)` 不消费触摸，
                // 点座位列表会一路穿到后面的遮罩上，面板当场消失
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "对局设置",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
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
                    fontWeight = FontWeight.Medium,
                    color = UIFriendBarTagTextBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // 朗读开关也放进面板：顶栏那个喇叭图标太小，用户翻「设置」时想找的是文字。
            // 两处改的是同一个值（ViewModel 的 autoSpeak，落在 DataStore 里），不会各说各话。
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "朗读发言",
                        fontSize = 13.sp,
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

            Spacer(Modifier.height(18.dp))

            // 思考气泡的全局开关：和顶栏胶囊同一个值，想找「文字开关」的人在这里读得到。
            // 单个座位的开关在下面的座位行里，长按气泡也能藏掉它。
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "思考气泡",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UIFriendBarTitleColor,
                    )
                    Text(
                        text = "显示 AI 的内心活动；参与局里打开等于作弊。" +
                            "长按某个气泡可以单独藏掉它",
                        fontSize = 11.5.sp,
                        color = UIFriendBarItemSubtitleColor,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = bubbleOn,
                    onCheckedChange = onBubbleChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = UIFriendBarTagTextBlue,
                    ),
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "思考时限",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UIFriendBarTitleColor,
                )
                Spacer(Modifier.width(8.dp))
                if (pace.totalTimeouts > 0) {
                    Text(
                        text = "本局已超时 ${pace.totalTimeouts} 次",
                        fontSize = 11.5.sp,
                        color = UIFriendBarRed,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
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

            Spacer(Modifier.height(18.dp))
            Text(
                text = "每个人的用时",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = UIFriendBarTitleColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "超时的那一步已经按兜底算完，并且这一局不再给它开深度思考。" +
                    "想改回来就打开下面的开关。",
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = UIFriendBarItemSubtitleColor,
            )

            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 不给 weight：外层是 wrap content 的卡片，weight 在这种父级里
                    // 拿不到确定的剩余高度，列表会塌成 0。给一个明确的滚动上限更稳
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                pace.seats.forEach { seat ->
                    SeatPaceRow(
                        seat = seat,
                        enabled = pace.running,
                        onToggle = { on -> onToggleThinking(seat.seat, on) },
                        onSeatBubble = { on -> onSeatBubbleChange(seat.seat, on) },
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UIAvatar(
            name = seat.name,
            avatarPath = seat.avatarPath,
            size = 30.dp,
            modifier = Modifier.then(if (seat.eliminated) Modifier.alpha(0.45f) else Modifier),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${seat.seat} 号 ${seat.name}",
                    fontSize = 13.sp,
                    color = if (seat.eliminated) UIFriendBarItemSubtitleColor else UIFriendBarTitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (seat.eliminated) {
                    Spacer(Modifier.width(6.dp))
                    Text(text = "已出局", fontSize = 10.sp, color = UIFriendBarItemSubtitleColor)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append("最近 ")
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
        Column(horizontalAlignment = Alignment.End) {
            if (seat.thinkingAvailable) {
                Switch(
                    checked = seat.thinkingOn,
                    onCheckedChange = { on -> if (enabled) onToggle(on) },
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = UIFriendBarTagTextBlue,
                    ),
                )
                Text(
                    text = "深度思考",
                    fontSize = 9.5.sp,
                    color = UIFriendBarItemSubtitleColor,
                )
            } else {
                Text(
                    text = "未开思考",
                    fontSize = 10.5.sp,
                    color = UIFriendBarItemSubtitleColor,
                )
            }
            Spacer(Modifier.height(4.dp))
            Switch(
                checked = seat.bubbleVisible,
                onCheckedChange = onSeatBubble,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = UIFriendBarTagTextBlue,
                ),
            )
            Text(
                text = if (seat.bubbleVisible) "气泡显示" else "气泡隐藏",
                fontSize = 9.5.sp,
                color = UIFriendBarItemSubtitleColor,
            )
        }
    }
}
