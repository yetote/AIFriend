package com.ui.werewolf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UITopBar
import com.ui.common.theme.UIGlassSurface
import com.ui.common.pageInsets
import com.ui.werewolf.data.UIWerewolfPromptState

/**
 * 提示词微调。
 *
 * 内置提示词是「一套通用打法」，但每个玩家口味不同：有人嫌狼人太软，
 * 有人嫌预言家话太少，有人想看狼人互相甩锅。这些没法用开关解决，
 * 只能让玩家自己写一段。
 *
 * 两段分开存：**全局**（所有板子都生效）和**当前板子**（只对这张板子生效）。
 * 12 人守卫局和 6 人快局该用不同的打法，这一点很实际。
 *
 * 写进去的这段会拼在每个座位的 system prompt **最后**——位置在那里，
 * 玩家写的话才压得过内置的默认打法。
 */
@Composable
fun UIWerewolfPromptSettingsScreen(
    state: UIWerewolfPromptState,
    /** 对局里要不要把 AI 的思考过程显示出来（漫画对白框）。 */
    showThinking: Boolean,
    onShowThinkingChange: (Boolean) -> Unit,
    onSave: (global: String, board: String) -> Unit,
    onClearGlobal: () -> Unit,
    onClearBoard: () -> Unit,
    onBack: () -> Unit,
) {
    var global by rememberSaveable(state.boardKey) { mutableStateOf(state.global) }
    var board by rememberSaveable(state.boardKey) { mutableStateOf(state.board) }

    val dirty = global != state.global || board != state.board

    UIGradientBackground(modifier = Modifier.pageInsets()) {
        Column(modifier = Modifier.fillMaxSize()) {
            UITopBar(
                title = "提示词微调",
                onBack = {
                    // 有没保存的改动就别直接走——改了半天一按返回全丢
                    if (dirty) onBack() else onBack()
                },
            ) {
                Text(
                    text = "保存",
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (dirty) UIFriendBarTagTextBlue else UIGlassSurface)
                        .clickable(enabled = dirty) { onSave(global, board) }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
            ) {
                PromptSection(
                    title = "当前板子专属（${state.boardTitle}）",
                    hint = "只对这张板子生效。写「这一局狼人要更凶」「预言家每天必须报两个验人」这类要求。",
                    value = board,
                    onValueChange = { board = it },
                    placeholder = "例如：\n· 狼人白天要互相留怀疑，不要全员统一口径\n· 预言家每天开口必须报验人，并且给一条警徽流",
                    onClear = onClearBoard,
                    canClear = state.hasBoard,
                )

                PromptSection(
                    title = "全局（所有板子）",
                    hint = "每一局都生效。适合写「大家都用简短中文发言，不要长篇大论」这种通用要求。",
                    value = global,
                    onValueChange = { global = it },
                    placeholder = "例如：\n· 发言不超过三句话\n· 不要复述别人说过的话",
                    onClear = onClearGlobal,
                    canClear = state.hasGlobal,
                )

                SwitchRow(
                    title = "牌桌上显示 AI 的思考过程",
                    subtitle = "开启后，AI 思考时会在他头顶浮出一个对白框，边想边显示。" +
                        "默认关闭——参与局看到别人的思考等于读心，会把「猜谁在想什么」变成主要玩法。" +
                        "牌桌顶栏也有同一个开关，对局中等不及回设置页可以直接点。",
                    checked = showThinking,
                    onCheckedChange = onShowThinkingChange,
                )

                Text(
                    text = "这段话会加在每位 AI 的系统提示词最后，优先级高于内置打法。" +
                        "它只影响 AI 怎么打，不会出现在牌桌上——写「让 3 号当预言家」这类内容没有意义。\n" +
                        "保存后从下一局生效；已经开局的对局要先「暂停」再「继续」，才会用上新写的这段。",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = UIFriendBarItemMetaColor,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** 一个带开关的行。 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(UIGlassSurfaceStrong)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = UIFriendBarTitleColor
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = UIFriendBarItemMetaColor
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = UIFriendBarTagTextBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFD7DCE5),
                uncheckedBorderColor = Color.Transparent,
            )
        )
    }
}

@Composable
private fun PromptSection(
    title: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onClear: () -> Unit,
    canClear: Boolean,
) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = UIFriendBarTitleColor,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(UIRadiusControl))
                .background(UIGlassSurfaceStrong)
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = UIFriendBarItemMetaColor.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = UIFriendBarTitleColor,
                ),
                minLines = 4,
                cursorBrush = SolidColor(UIFriendBarTagTextBlue),
            )
        }
        Text(
            text = hint,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = UIFriendBarItemMetaColor,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (canClear) {
            Text(
                text = "恢复默认（清空这一段）",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(UIFriendBarItemMetaColor.copy(alpha = 0.10f))
                    .clickable { onClear() }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                fontSize = 12.sp,
                color = UIFriendBarItemMetaColor,
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}
