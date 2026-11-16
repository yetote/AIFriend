package com.ui.werewolf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.UIAvatar
import com.ui.common.pageInsetsBottom
import com.ui.common.pageInsetsTop
import com.ui.common.theme.UIBlobBlue
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIFieldFill
import com.ui.common.theme.UIFriendBarCreateButtonText
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarSubtitleColor
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurface
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIHeroBrush
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UITopBar
import com.ui.common.theme.uiCard
import com.ui.common.theme.uiControl
import com.ui.werewolf.data.UIWerewolfFriend
import com.ui.werewolf.data.UIWerewolfMode
import com.ui.werewolf.data.UIWerewolfPreset
import com.ui.werewolf.data.UIWerewolfRecord
import com.ui.werewolf.data.UIWerewolfSeat

/**
 * 开局配置页。
 *
 * 和「新建聊天室」的区别在于这里要交代的不是一个名字，而是**这一局有几个位置、还差几个人**。
 * 所以主视觉是座位条而不是表单：一眼看清「8 个位置填了 5 个」比读「已选 5 位」直观。
 */
@Composable
fun UIWerewolfSetupScreen(
    mode: UIWerewolfMode,
    presets: List<UIWerewolfPreset>,
    presetKey: String,
    friends: List<UIWerewolfFriend>,
    selectedIds: Set<String>,
    seats: List<UIWerewolfSeat>,
    records: List<UIWerewolfRecord>,
    unfinished: UIWerewolfRecord?,
    error: String?,
    onModeChange: (UIWerewolfMode) -> Unit,
    onPresetChange: (String) -> Unit,
    onToggleFriend: (String) -> Unit,
    onResume: () -> Unit,
    onAbandon: () -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val visibleFriends = if (search.isBlank()) {
        friends
    } else {
        friends.filter { friend ->
            friend.name.contains(search, ignoreCase = true) ||
                friend.role.contains(search, ignoreCase = true) ||
                friend.model.contains(search, ignoreCase = true)
        }
    }

    // 需要几位好友：自己参与的局里有一个位置是自己的，不占好友名额。
    val preset = presets.firstOrNull { it.key == presetKey } ?: presets.firstOrNull()
    val needFriends = (preset?.playerCount ?: 0) - if (mode == UIWerewolfMode.PLAY) 1 else 0
    val missing = (needFriends - selectedIds.size).coerceAtLeast(0)
    val canStart = needFriends > 0 && missing == 0

    UIGradientBackground(modifier = Modifier.pageInsetsTop()) {
        Column(modifier = Modifier.fillMaxSize()) {
            UITopBar(title = "狼人杀", onBack = onBack) {
                Text(
                    text = if (canStart) "可以开局" else "差 $missing 位",
                    modifier = Modifier.padding(end = 12.dp),
                    fontSize = 13.sp,
                    color = if (canStart) UIFriendBarTagTextBlue else UIFriendBarItemSubtitleColor
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (unfinished != null) {
                    ResumeCard(
                        record = unfinished,
                        onResume = onResume,
                        onAbandon = onAbandon
                    )
                }

                if (error != null) {
                    ErrorBar(text = error, onDismiss = onClearError)
                }

                SectionTitle(
                    title = "怎么玩",
                    subtitle = "自己占一个座位，还是纯粹看 8 个 AI 自己打"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UIWerewolfMode.entries.forEach { item ->
                        ModeCard(
                            mode = item,
                            selected = item == mode,
                            modifier = Modifier.weight(1f),
                            onClick = { onModeChange(item) }
                        )
                    }
                }

                SectionTitle(title = "板子", subtitle = "身份构成固定，开局时随机发牌")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { item ->
                        PresetRow(
                            preset = item,
                            selected = item.key == presetKey,
                            onClick = { onPresetChange(item.key) }
                        )
                    }
                }

                // 座位条：这是整页的主视觉，把「还差几位」变成几个空位
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "本局 ${seats.size} 个位置",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UIFriendBarTitleColor
                        )
                        Text(
                            text = "座位号开局时随机",
                            fontSize = 11.sp,
                            color = UIFriendBarItemSubtitleColor
                        )
                    }
                    SeatStrip(seats = seats)
                }

                SectionTitle(
                    title = "选人",
                    subtitle = if (needFriends > 0) {
                        "已选 ${selectedIds.size} / $needFriends 位"
                    } else {
                        "还没有可选的板子"
                    }
                )

                SearchField(
                    value = search,
                    onValueChange = { search = it }
                )

                when {
                    friends.isEmpty() -> Text(
                        text = "还没有 AI 好友。先去首页创建一个，再来开局。",
                        fontSize = 13.sp,
                        color = UIFriendBarSubtitleColor
                    )

                    visibleFriends.isEmpty() -> Text(
                        text = "没有匹配的好友",
                        fontSize = 13.sp,
                        color = UIFriendBarSubtitleColor
                    )
                }

                if (visibleFriends.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiCard(shape = RoundedCornerShape(UIRadiusCard))
                            .padding(vertical = 4.dp)
                    ) {
                        visibleFriends.forEach { friend ->
                            val checked = friend.id in selectedIds
                            // 选满之后仍允许取消，否则想换人得先取消一个再找
                            val enabled = checked || selectedIds.size < needFriends
                            FriendRow(
                                friend = friend,
                                checked = checked,
                                enabled = enabled,
                                onClick = { onToggleFriend(friend.id) }
                            )
                        }
                    }
                }

                if (records.isNotEmpty()) {
                    SectionTitle(title = "最近对局", subtitle = "点一局可以说明当时打到哪")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiCard(shape = RoundedCornerShape(UIRadiusCard))
                            .padding(vertical = 4.dp)
                    ) {
                        records.forEach { record ->
                            RecordRow(record = record)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            StartBar(
                canStart = canStart,
                missing = missing,
                playMode = mode == UIWerewolfMode.PLAY,
                onStart = onStart
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 各区块
// ---------------------------------------------------------------------------

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = UIFriendBarTitleColor
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = UIFriendBarItemSubtitleColor
        )
    }
}

/**
 * 「继续上一局」用深色卡片。
 *
 * 它和下面的浅色区块不是同一类东西：下面在配置新局，这里在提醒一件已经发生的事。
 * 用重色块把它从流程里拎出来，才不会被当成又一个设置项划过去。
 */
@Composable
private fun ResumeCard(
    record: UIWerewolfRecord,
    onResume: () -> Unit,
    onAbandon: () -> Unit,
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "有一局还没打完",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = "第 ${record.day} 天 · ${record.phaseLabel} · ${record.timeLabel}",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.78f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable { onResume() }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "接着打",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UIFriendBarTitleColor
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .clickable { onAbandon() }
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

@Composable
private fun ErrorBar(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(UIFriendBarRed.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            color = UIFriendBarRed
        )
        Text(
            text = "知道了",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { onDismiss() }
                .padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = UIFriendBarRed
        )
    }
}

@Composable
private fun ModeCard(
    mode: UIWerewolfMode,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(if (selected) UIFriendBarCreateButtonText.copy(alpha = 0.08f) else UIGlassSurfaceStrong)
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = if (selected) UIFriendBarCreateButtonText.copy(alpha = 0.45f) else UIGlassStroke,
                shape = RoundedCornerShape(UIRadiusControl)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = mode.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) UIFriendBarCreateButtonText else UIFriendBarTitleColor
        )
        Text(
            text = mode.hint,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = UIFriendBarItemSubtitleColor
        )
    }
}

@Composable
private fun PresetRow(preset: UIWerewolfPreset, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .uiControl(
                shape = RoundedCornerShape(UIRadiusControl),
                color = if (selected) UIFriendBarCreateButtonText.copy(alpha = 0.07f) else UIGlassSurfaceStrong,
                borderColor = if (selected) UIFriendBarCreateButtonText.copy(alpha = 0.4f) else UIGlassStroke
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = preset.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) UIFriendBarCreateButtonText else UIFriendBarTitleColor
            )
            Text(
                text = preset.composition,
                fontSize = 11.sp,
                color = UIFriendBarItemSubtitleColor
            )
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.background(UIActionBrush)
                    } else {
                        Modifier.border(1.5.dp, UIGlassStroke, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * 座位条。
 *
 * 空位用虚线圆而不是浅灰实心圆：实心会被误读成「已有一个没头像的人」，
 * 虚线才有「这里还空着，等人来」的意思。
 */
@Composable
private fun SeatStrip(seats: List<UIWerewolfSeat>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        seats.forEach { seat ->
            if (seat.name == null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .drawBehind {
                            drawCircle(
                                color = UIFriendBarItemSubtitleColor.copy(alpha = 0.35f),
                                radius = size.minDimension / 2f - 1.dp.toPx(),
                                style = Stroke(
                                    width = 1.2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(4.dp.toPx(), 4.dp.toPx())
                                    )
                                )
                            )
                        }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .then(
                            if (seat.isSelf) {
                                Modifier.border(2.dp, UIFriendBarCreateButtonText, CircleShape)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    UIAvatar(
                        name = seat.name,
                        avatarPath = seat.avatarPath,
                        size = if (seat.isSelf) 26.dp else 30.dp,
                        shape = CircleShape
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .uiControl(shape = RoundedCornerShape(50.dp))
            .background(UIFieldFill)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = "搜索昵称 / 角色 / 模型",
                fontSize = 13.sp,
                color = UIFriendBarItemSubtitleColor.copy(alpha = 0.7f)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 13.sp,
                color = UIFriendBarTitleColor
            ),
            cursorBrush = SolidColor(UIFriendBarCreateButtonText)
        )
    }
}

@Composable
private fun FriendRow(
    friend: UIWerewolfFriend,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UIAvatar(
            name = friend.name,
            avatarPath = friend.avatarPath,
            size = 40.dp,
            shape = CircleShape
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled || checked) UIFriendBarTitleColor else UIFriendBarTitleColor.copy(alpha = 0.4f)
            )
            val meta = listOf(friend.role, friend.model).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    fontSize = 12.sp,
                    color = UIFriendBarItemSubtitleColor.copy(alpha = if (enabled || checked) 1f else 0.5f)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .then(
                    when {
                        checked -> Modifier.background(UIActionBrush)
                        else -> Modifier.border(1.5.dp, UIGlassStroke, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun RecordRow(record: UIWerewolfRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = if (record.finished) {
                    "第 ${record.day} 天 · ${record.winnerLabel ?: "已结束"}"
                } else {
                    "第 ${record.day} 天 · ${record.phaseLabel}"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = UIFriendBarTitleColor
            )
            Text(
                text = record.timeLabel,
                fontSize = 11.sp,
                color = UIFriendBarItemSubtitleColor
            )
        }
        Text(
            text = if (record.finished) "已结束" else "未完成",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (record.finished) UIFriendBarItemSubtitleColor else UIFriendBarTagTextBlue
        )
    }
}

@Composable
private fun StartBar(canStart: Boolean, missing: Int, playMode: Boolean, onStart: () -> Unit) {
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
                    elevation = if (canStart) 10.dp else 0.dp,
                    shape = RoundedCornerShape(UIRadiusControl),
                    clip = false,
                    ambientColor = UIBlobBlue,
                    spotColor = UIBlobBlue
                )
                .clip(RoundedCornerShape(UIRadiusControl))
                .then(
                    if (canStart) {
                        Modifier.background(UIActionBrush)
                    } else {
                        Modifier.background(UIGlassSurface)
                    }
                )
                .clickable(enabled = canStart) { onStart() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    canStart && playMode -> "开局，我参与"
                    canStart -> "开局，看它们打"
                    else -> "还差 $missing 位好友"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (canStart) Color.White else UIFriendBarItemSubtitleColor
            )
        }
    }
}
