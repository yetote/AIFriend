package com.ui.werewolf

// 「现在轮到我」面板：发言输入、投票选人、女巫用药。

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIBlobBlue
import com.ui.common.theme.UIFieldFill
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UIShadowSoft
import com.ui.werewolf.data.UIWerewolfSeatLive
import com.ui.werewolf.data.UIWerewolfTarget
import com.ui.werewolf.data.UIWerewolfTurn
import com.ui.werewolf.data.UIWerewolfDirection
import com.ui.werewolf.data.UIWerewolfTurnKind

/**
 * 轮到你了。
 *
 * 判断「现在该做什么」只看两件事：`phase` 和**本人身份**。不去解析提示词里写了什么——
 * 提示词是给人设和规则用的，随时会改，拿它做界面分支等于把文案当协议。
 */
@Composable
internal fun TurnPanel(
    turn: UIWerewolfTurn,
    day: Int = 0,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    surface: Color,
    /** 牌桌上的全部座位，给选人弹窗用（头像、存活、死因）。 */
    seats: List<UIWerewolfSeatLive> = emptyList(),
    onSpeech: (String) -> Unit,
    onVote: (Int?) -> Unit,
    onSeat: (Int) -> Unit,
    onWitch: (Boolean, Int?) -> Unit,
    onDirection: (UIWerewolfDirection) -> Unit = {},
    onWithdraw: (Boolean) -> Unit = {},
    onExplode: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val dockShape = RoundedCornerShape(topStart = UIRadiusCard, topEnd = UIRadiusCard)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = dockShape,
                clip = false,
                ambientColor = UIShadowSoft,
                spotColor = UIShadowSoft,
            )
            .clip(dockShape)
            .background(surface)
            .border(0.8.dp, if (night) NightStroke else DayStroke, dockShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 3.5.dp, height = 14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (night) Color(0xFFE5B869) else Color(0xFFC84B2A))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = turn.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
            turn.hint?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = dimColor
                )
            }
        }

        when (turn.kind) {
            UIWerewolfTurnKind.SPEECH -> SpeechInput(
                night = night,
                seats = seats,
                selfSeat = turn.seat,
                textColor = textColor,
                dimColor = dimColor,
                onSubmit = onSpeech
            )

            UIWerewolfTurnKind.SHERIFF_SIGNUP -> SheriffSignupRow(
                night = night,
                textColor = textColor,
                declineLabel = turn.abstainLabel ?: "不上警",
                onSignup = { onVote(turn.seat) },
                onDecline = { onVote(null) },
            )

            UIWerewolfTurnKind.VOTE -> PickTarget(
                turn = turn,
                day = day,
                seats = seats,
                textColor = textColor,
                dimColor = dimColor,
                night = night,
                onPick = onVote,
                onAbstain = if (turn.canAbstain) {
                    { onVote(null) }
                } else null,
            )

            UIWerewolfTurnKind.SEAT -> PickTarget(
                turn = turn,
                day = day,
                seats = seats,
                textColor = textColor,
                dimColor = dimColor,
                night = night,
                onPick = { onSeat(it) },
                onAbstain = if (turn.canAbstain) { { onVote(null) } } else null,
            )

            UIWerewolfTurnKind.WITCH -> WitchPanel(
                turn = turn,
                day = day,
                seats = seats,
                textColor = textColor,
                dimColor = dimColor,
                night = night,
                onWitch = onWitch,
            )

            UIWerewolfTurnKind.DIRECTION -> DirectionRow(
                night = night,
                textColor = textColor,
                dimColor = dimColor,
                hasDawnDeath = turn.hasDawnDeath,
                onPick = onDirection
            )

            UIWerewolfTurnKind.WITHDRAW -> WithdrawRow(
                night = night,
                textColor = textColor,
                onPick = onWithdraw
            )

            UIWerewolfTurnKind.EXPLODE -> ExplodeRow(
                textColor = textColor,
                onPick = onExplode,
            )
        }
    }
}

/**
 * 上警报名：直接给「上警 / 不上警」两个等宽主次按钮，无需打开 12 人选人弹窗。
 */
@Composable
private fun SheriffSignupRow(
    night: Boolean,
    textColor: Color,
    declineLabel: String,
    onSignup: () -> Unit,
    onDecline: () -> Unit,
) {
    val shape = RoundedCornerShape(UIRadiusControl)
    val ctrlFill = if (night) NightControlFill else DayControlFill
    val ctrlBorder = if (night) NightControlBorder else DayControlBorder
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .shadow(
                    elevation = 6.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = UIBlobBlue,
                    spotColor = UIBlobBlue,
                )
                .clip(shape)
                .background(UIActionBrush)
                .clickable { onSignup() }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "上警",
                fontSize = FsAction,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(shape)
                .background(ctrlFill)
                .border(0.9.dp, ctrlBorder, shape)
                .clickable { onDecline() }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = declineLabel,
                fontSize = FsAction,
                fontWeight = FontWeight.Medium,
                color = textColor,
            )
        }
    }
}

/**
 * 选一个座位的入口：一个显眼的「从牌桌选」按钮，点开按牌桌分布的 dialog。
 *
 * 底部原有的横向 Chip 不再显示——验人、守护这类决定要对着牌桌想一圈，
 * 一串名字看不出谁坐哪、谁还活着。dialog 里座位和牌桌是同一套视觉（头像、存活、死因）。
 */
@Composable
private fun PickTarget(
    turn: UIWerewolfTurn,
    day: Int,
    seats: List<UIWerewolfSeatLive>,
    textColor: Color,
    dimColor: Color,
    night: Boolean,
    onPick: (Int) -> Unit,
    onAbstain: (() -> Unit)?,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val pickable = turn.targets.filter { it.selectable }.map { it.seat }.toSet()
    // 提交后这一步就结束了；若引擎已经切到下一回合，残留的 showPicker 会把旧 dialog
    // 又弹出来（弹的是旧目标，玩家可能误点）。turn 一变就关掉。
    androidx.compose.runtime.LaunchedEffect(day, turn.title, turn.kind, turn.targets) { showPicker = false }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(UIRadiusControl),
                    clip = false,
                    ambientColor = UIBlobBlue,
                    spotColor = UIBlobBlue,
                )
                .clip(RoundedCornerShape(UIRadiusControl))
                .background(UIActionBrush)
                .clickable { showPicker = true }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "从牌桌选人 →",
                fontSize = FsAction,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
        if (onAbstain != null) {
            val ctrlFill = if (night) NightControlFill else DayControlFill
            val ctrlBorder = if (night) NightControlBorder else DayControlBorder
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .background(ctrlFill)
                    .border(0.8.dp, ctrlBorder, RoundedCornerShape(UIRadiusControl))
                    .clickable { onAbstain() }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = turn.abstainLabel ?: "不做",
                    fontSize = FsAction,
                    fontWeight = FontWeight.Medium,
                    color = dimColor,
                )
            }
        }
    }

    if (showPicker) {
        TargetPickDialog(
            title = turn.title,
            hint = turn.hint,
            seats = seats,
            pickable = pickable,
            textColor = textColor,
            dimColor = dimColor,
            night = night,
            onPick = onPick,
            abstainLabel = if (onAbstain != null) turn.abstainLabel ?: "不做" else null,
            onAbstain = onAbstain,
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
internal fun SpeechInput(
    night: Boolean,
    seats: List<UIWerewolfSeatLive> = emptyList(),
    selfSeat: Int? = null,
    textColor: Color,
    dimColor: Color,
    onSubmit: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val fieldFill = if (night) NightControlFill else DayControlFill
    val fieldBorder = if (night) NightControlBorder else DayControlBorder
    val quickSeats = seats.filter { it.alive && it.seat != selfSeat }.sortedBy { it.seat }
    val quickWords = if (night) {
        listOf("今晚刀", "我跳预言家", "我打冲锋", "我打倒钩", "别分票")
    } else {
        listOf("金水", "查杀", "银水", "警徽流", "站边", "归票", "今天出", "认民", "过")
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 快捷选号 + 高频术语胶囊：纯公开信息（存活座位号 + 通用术语），手机少敲一半字
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            quickSeats.forEach { s ->
                QuickInsertChip(
                    label = "${s.seat}号",
                    night = night,
                    textColor = textColor,
                    onClick = { text = appendQuickToken(text, "${s.seat}号") },
                )
            }
            quickWords.forEach { word ->
                QuickInsertChip(
                    label = word,
                    night = night,
                    textColor = dimColor,
                    onClick = { text = appendQuickToken(text, word) },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .background(fieldFill)
                    .border(0.8.dp, fieldBorder, RoundedCornerShape(UIRadiusControl))
                    .padding(horizontal = 13.dp, vertical = 11.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "说点什么，其他人会据此投票",
                        fontSize = 13.5.sp,
                        color = dimColor.copy(alpha = 0.8f)
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 15.sp, color = textColor),
                    cursorBrush = SolidColor(if (night) SelfBlueNight else SelfBlue),
                    maxLines = 4
                )
            }
            Spacer(Modifier.width(8.dp))
            val enabled = text.isNotBlank()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .background(if (enabled) UIActionBrush else Brush.linearGradient(listOf(
                        fieldFill, fieldFill
                    )))
                    .then(
                        if (!enabled) Modifier.border(0.8.dp, fieldBorder, RoundedCornerShape(UIRadiusControl))
                        else Modifier
                    )
                    .clickable(enabled = enabled) {
                        onSubmit(text.trim())
                        text = ""
                    }
                    .padding(horizontal = 18.dp, vertical = 11.dp)
            ) {
                Text(
                    text = "发言",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color.White else dimColor
                )
            }
        }
    }
}

@Composable
private fun QuickInsertChip(
    label: String,
    night: Boolean,
    textColor: Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (night) NightControlFill else DayControlFill)
            .border(0.6.dp, if (night) NightControlBorder else DayControlBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        fontSize = FsMeta,
        fontWeight = FontWeight.Medium,
        color = textColor,
        maxLines = 1,
    )
}

private fun appendQuickToken(current: String, token: String): String {
    if (current.isBlank()) return token
    return current + token
}

@Composable
internal fun TargetRow(
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
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                fontSize = FsAction,
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
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                fontSize = FsAction,
                color = dimColor
            )
        }
    }
}

/** 竞选发言之后的退水：两个按钮，没有别的可选项。 */
@Composable
internal fun WithdrawRow(
    night: Boolean,
    textColor: Color,
    onPick: (Boolean) -> Unit,
) {
    val ctrlFill = if (night) NightControlFill else DayControlFill
    val ctrlBorder = if (night) NightControlBorder else DayControlBorder
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 不退水放前面、用高亮色：那是默认动作，退水是要付代价的那一个
        listOf(false to "不退水", true to "退水").forEach { (withdraw, label) ->
            val shape = RoundedCornerShape(UIRadiusControl)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    // background 的 Color / Brush 是两个重载，混在一个 if 里会挑不出来
                    .background(
                        if (withdraw) {
                            Brush.linearGradient(listOf(ctrlFill, ctrlFill))
                        } else {
                            UIActionBrush
                        }
                    )
                    .then(if (withdraw) Modifier.border(0.8.dp, ctrlBorder, shape) else Modifier)
                    .clickable { onPick(withdraw) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = FsAction,
                    fontWeight = if (!withdraw) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (!withdraw) Color.White else textColor
                )
            }
        }
    }
}

/**
 * 别人发言后的「要不要现在插爆」。不是阶段，是一次性即时判断。
 *
 * 不爆放前面、高亮：那是默认（继续忍着）；自爆是有代价的那一个，用红色。
 */
@Composable
internal fun ExplodeRow(
    textColor: Color,
    onPick: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(UIRadiusControl)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(shape)
                .background(UIActionBrush)
                .clickable { onPick(false) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "继续听",
                fontSize = FsAction,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFE84A44), com.ui.common.theme.UIFriendBarRed)
                    )
                )
                .clickable { onPick(true) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "现在自爆",
                fontSize = FsAction,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

/**
 * 警长选发言顺序：警左 / 警右 / 死左 / 死右，四个按钮，没有目标可选。
 *
 * 平安夜没人倒牌时死左 / 死右灰掉——留着可点会让玩家以为引擎会把顺序排错。
 */
@Composable
internal fun DirectionRow(
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    hasDawnDeath: Boolean,
    onPick: (UIWerewolfDirection) -> Unit,
) {
    val ctrlFill = if (night) NightControlFill else DayControlFill
    val ctrlBorder = if (night) NightControlBorder else DayControlBorder
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UIWerewolfDirection.entries.forEach { direction ->
            val enabled = hasDawnDeath || !direction.anchoredOnDeath
            val shape = RoundedCornerShape(UIRadiusControl)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (enabled) ctrlFill else ctrlFill.copy(alpha = ctrlFill.alpha * 0.45f))
                    .border(
                        0.8.dp,
                        if (enabled) ctrlBorder else ctrlBorder.copy(alpha = ctrlBorder.alpha * 0.4f),
                        shape,
                    )
                    .clickable(enabled = enabled) { onPick(direction) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = direction.label,
                    fontSize = FsAction,
                    fontWeight = if (enabled) FontWeight.Medium else FontWeight.Normal,
                    color = if (enabled) textColor else dimColor.copy(alpha = 0.45f)
                )
            }
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
internal fun WitchPanel(
    turn: UIWerewolfTurn,
    day: Int,
    seats: List<UIWerewolfSeatLive>,
    textColor: Color,
    dimColor: Color,
    night: Boolean,
    onWitch: (Boolean, Int?) -> Unit,
) {
    var poisoning by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(day, turn.title, turn.canPoison, turn.canHeal) { poisoning = false }

    if (poisoning) {
        // 毒目标也走牌桌式 dialog——和验人、守护一致，别在底部糊一串名字
        TargetPickDialog(
            title = "毒谁",
            hint = "毒药无视守护，但只能用一次",
            seats = seats,
            pickable = turn.targets.filter { it.selectable }.map { it.seat }.toSet(),
            textColor = textColor,
            dimColor = dimColor,
            night = night,
            onPick = { seat -> onWitch(false, seat) },
            abstainLabel = "算了",
            onAbstain = null,
            onDismiss = { poisoning = false },
        )
        return
    }

    val ctrlFill = if (night) NightControlFill else DayControlFill
    val ctrlBorder = if (night) NightControlBorder else DayControlBorder
    val shape = RoundedCornerShape(UIRadiusControl)
    val healBrush = Brush.linearGradient(listOf(Color(0xFF28B98D), HealGreen))
    val poisonAccent = if (night) Color(0xFFB58CFF) else PoisonPurple

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (turn.canHeal) {
            Text(
                text = "解药救 ${turn.healTarget ?: "他"}",
                modifier = Modifier
                    .clip(shape)
                    .background(healBrush)
                    .clickable { onWitch(true, null) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                fontSize = FsAction,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        if (turn.canPoison) {
            Text(
                text = "用毒药",
                modifier = Modifier
                    .clip(shape)
                    .background(PoisonPurple.copy(alpha = if (night) 0.28f else 0.12f))
                    .border(0.9.dp, poisonAccent.copy(alpha = 0.55f), shape)
                    .clickable { poisoning = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                fontSize = FsAction,
                fontWeight = FontWeight.SemiBold,
                color = poisonAccent
            )
        }
        Text(
            text = "这晚不用药",
            modifier = Modifier
                .clip(shape)
                .background(ctrlFill)
                .border(0.8.dp, ctrlBorder, shape)
                .clickable { onWitch(false, null) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            fontSize = FsAction,
            fontWeight = FontWeight.Medium,
            color = dimColor
        )
    }
}
