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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIFieldFill
import com.ui.common.theme.UIRadiusControl
import com.ui.werewolf.data.UIWerewolfTarget
import com.ui.werewolf.data.UIWerewolfTurn
import com.ui.werewolf.data.UIWerewolfTurnKind

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
 * 轮到你了。
 *
 * 判断「现在该做什么」只看两件事：`phase` 和**本人身份**。不去解析提示词里写了什么——
 * 提示词是给人设和规则用的，随时会改，拿它做界面分支等于把文案当协议。
 */
@Composable
internal fun TurnPanel(
    turn: UIWerewolfTurn,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    surface: Color,
    onSpeech: (String) -> Unit,
    onVote: (Int?) -> Unit,
    onSeat: (Int) -> Unit,
    onWitch: (Boolean, Int?) -> Unit,
    onDirection: (Boolean) -> Unit = {},
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
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            turn.hint?.let {
                Text(
                    text = "  $it",
                    fontSize = FsName,
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
                // 上警报名也走这个面板，但它的「不做」是「不上警」不是「弃票」。
                // 写死「弃票」的话，玩家会以为自己点的是放弃投票
                extraLabel = if (turn.canAbstain) turn.abstainLabel ?: "弃票" else null,
                onExtra = { onVote(null) }
            )

            UIWerewolfTurnKind.SEAT -> TargetRow(
                targets = turn.targets,
                textColor = textColor,
                dimColor = dimColor,
                onPick = { onSeat(it) },
                extraLabel = if (turn.canAbstain) turn.abstainLabel ?: "不做" else null,
                onExtra = { onVote(null) }
            )

            UIWerewolfTurnKind.WITCH -> WitchPanel(
                turn = turn,
                textColor = textColor,
                dimColor = dimColor,
                onWitch = onWitch
            )

            UIWerewolfTurnKind.DIRECTION -> DirectionRow(
                textColor = textColor,
                dimColor = dimColor,
                onPick = onDirection
            )
        }
    }
}

@Composable
internal fun SpeechInput(
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
                .padding(horizontal = 13.dp, vertical = 11.dp)
        ) {
            if (text.isEmpty()) {
                Text(
                    text = "说点什么，其他人会据此投票",
                    fontSize = 13.5.sp,
                    color = dimColor
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 15.sp, color = textColor),
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

/**
 * 警长选发言方向。两个按钮，没有目标可选——顺/逆就是全部答案。
 */
@Composable
internal fun DirectionRow(
    textColor: Color,
    dimColor: Color,
    onPick: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "顺时针",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(UIActionBrush)
                .clickable { onPick(true) }
                .padding(horizontal = 16.dp, vertical = 9.dp),
            fontSize = FsAction,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Text(
            text = "逆时针",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(UIFieldFill)
                .clickable { onPick(false) }
                .padding(horizontal = 16.dp, vertical = 9.dp),
            fontSize = FsAction,
            color = textColor
        )
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
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                fontSize = FsAction,
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
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                fontSize = FsAction,
                color = textColor
            )
        }
        Text(
            text = "这晚不用药",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(UIFieldFill)
                .clickable { onWitch(false, null) }
                .padding(horizontal = 15.dp, vertical = 9.dp),
            fontSize = FsAction,
            color = dimColor
        )
    }
}
