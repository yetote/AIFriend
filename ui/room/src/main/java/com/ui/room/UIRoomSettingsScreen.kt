package com.ui.room

import com.ui.common.pageInsets
import com.ui.common.UIAvatarCropDialog
import com.ui.common.UIAvatar
import com.ui.common.UIGroupAvatar
import com.ui.common.UIGroupMember
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.room.data.UIRoomMember
import com.ui.common.theme.UIDialogButtons
import com.ui.common.theme.UIIosDialog
import com.ui.common.theme.UIGlassSurface
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UISeparator
import com.ui.common.theme.UITopBar
import com.ui.common.theme.uiCard
import com.ui.common.theme.uiControl
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarSubtitleColor
import com.ui.common.theme.UIFriendBarTagBgBlue
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor

/**
 * 聊天室设置：改名、房间级设定、成员管理入口。
 * 房间设定会拼在每位成员的 system prompt 前面。
 */
@Composable
fun UIRoomSettingsScreen(
    roomName: String,
    roomPrompt: String,
    avatarPath: String?,
    members: List<UIRoomMember>,
    allFriends: List<UIRoomMember>,
    maxMembers: Int,
    discussionEnabled: Boolean,
    discussionWindow: Int,
    autoChatEnabled: Boolean,
    autoChatTurns: Int,
    messageCount: Int,
    estimatedContextTokens: Int,
    estimatedRequestCount: Int,
    onSave: (
        name: String,
        prompt: String,
        discussionEnabled: Boolean,
        discussionWindow: Int,
        autoChatEnabled: Boolean,
        autoChatTurns: Int
    ) -> Unit,
    onToggleMember: (String) -> Unit,
    onClearMessages: () -> Unit,
    onExport: () -> Unit,
    onChangeAvatar: (ByteArray) -> Unit,
    onBack: () -> Unit
) {
    var name by rememberSaveable(roomName) { mutableStateOf(roomName) }
    var prompt by rememberSaveable(roomPrompt) { mutableStateOf(roomPrompt) }
    var discussion by rememberSaveable(discussionEnabled) { mutableStateOf(discussionEnabled) }
    var window by rememberSaveable(discussionWindow) { mutableIntStateOf(discussionWindow.coerceIn(1, 5)) }
    var autoChat by rememberSaveable(autoChatEnabled) { mutableStateOf(autoChatEnabled) }
    var autoTurns by rememberSaveable(autoChatTurns) { mutableIntStateOf(autoChatTurns.coerceIn(1, 20)) }
    var showMembers by remember { mutableStateOf(false) }
    var pendingWindow by remember { mutableStateOf<Int?>(null) }
    var pendingDiscussion by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) cropUri = uri
    }

    // 有没有还没保存的改动：保存即返回，所以返回前必须先问一句，否则改了半天一按返回全丢
    val savedWindow = discussionWindow.coerceIn(1, 5)
    val savedTurns = autoChatTurns.coerceIn(1, 20)
    val savedAutoChat = autoChatEnabled && discussionEnabled
    val dirty = name != roomName ||
        prompt != roomPrompt ||
        discussion != discussionEnabled ||
        window != savedWindow ||
        (autoChat && discussion) != savedAutoChat ||
        autoTurns != savedTurns

    val requestBack = {
        if (dirty) showDiscardConfirm = true else onBack()
    }

    BackHandler(enabled = true) { requestBack() }

    UIGradientBackground(modifier = Modifier.pageInsets()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            UITopBar(title = "聊天室设置", onBack = requestBack) {
                Text(
                    text = "保存",
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (name.isNotBlank()) UIFriendBarTagTextBlue else UIGlassSurface
                        )
                        .clickable(enabled = name.isNotBlank()) {
                            onSave(name, prompt, discussion, window, autoChat, autoTurns)
                        }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .uiCard(shape = RoundedCornerShape(UIRadiusCard))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 没设头像时显示成员拼图，和首页 / 聊天页顶栏保持一致，
                    // 不然这三处同一房间长得不一样。
                    if (avatarPath != null) {
                        UIAvatar(
                            name = name.ifBlank { "房间" },
                            avatarPath = avatarPath,
                            size = 64.dp,
                            modifier = Modifier.clickable {
                                avatarPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    } else {
                        UIGroupAvatar(
                            members = members.map {
                                UIGroupMember(name = it.name, avatarPath = it.avatarPath)
                            },
                            size = 64.dp,
                            modifier = Modifier.clickable {
                                avatarPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "房间头像",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = UIFriendBarTitleColor
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "点击更换，可选",
                            fontSize = 12.sp,
                            color = UIFriendBarItemMetaColor
                        )
                    }
                }

                SettingsField(
                    label = "房间名",
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true
                )

                SettingsField(
                    label = "房间设定（可选）",
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = "例如：你们是一个评审团，先各自给结论，再给两条理由，不要互相重复。",
                    minLines = 4
                )
                Text(
                    text = "这段设定会加在每位成员的 system prompt 前面，只影响之后的回答",
                    fontSize = 12.sp,
                    color = UIFriendBarItemMetaColor,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                SettingsCard {
                    SettingSwitchRow(
                        title = "讨论模式",
                        subtitle = "开启后每位 AI 能看到其他人最近的发言，token 会明显上涨",
                        checked = discussion,
                        onCheckedChange = { checked ->
                            if (checked) {
                                pendingDiscussion = true
                            } else {
                                discussion = false
                                // 自由对话依赖讨论模式，关掉讨论就一起关掉，避免留下无效开关
                                autoChat = false
                            }
                        }
                    )

                    if (discussion) {
                        SettingsDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "回看窗口",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = UIFriendBarTitleColor
                                )
                                Text(
                                    text = "其他人的发言只保留最近几轮（1–5）",
                                    fontSize = 12.sp,
                                    color = UIFriendBarItemMetaColor
                                )
                            }
                            StepperButton(
                                text = "−",
                                enabled = window > 1,
                                onClick = { window = (window - 1).coerceAtLeast(1) }
                            )
                            Text(
                                text = "$window",
                                modifier = Modifier.padding(horizontal = 12.dp),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UIFriendBarTitleColor
                            )
                            StepperButton(
                                text = "＋",
                                enabled = window < 5,
                                onClick = { if (window < 5) pendingWindow = window + 1 }
                            )
                        }
                    }

                    SettingsDivider()
                    SettingSwitchRow(
                        title = "自由对话",
                        subtitle = if (discussion) {
                            "你说完之后，成员会围绕同一个话题接着聊，直到用完接话次数"
                        } else {
                            "需要先开启讨论模式：成员看不到别人的发言就没法接话"
                        },
                        checked = autoChat && discussion,
                        enabled = discussion,
                        onCheckedChange = { if (discussion) autoChat = it }
                    )

                    if (discussion && autoChat) {
                        SettingsDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "接话次数",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = UIFriendBarTitleColor
                                )
                                Text(
                                    text = "一次接话算一轮，用完就停（1–20）",
                                    fontSize = 12.sp,
                                    color = UIFriendBarItemMetaColor
                                )
                            }
                            StepperButton(
                                text = "−",
                                enabled = autoTurns > 1,
                                onClick = { autoTurns = (autoTurns - 1).coerceAtLeast(1) }
                            )
                            Text(
                                text = "$autoTurns",
                                modifier = Modifier.padding(horizontal = 12.dp),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UIFriendBarTitleColor
                            )
                            StepperButton(
                                text = "＋",
                                enabled = autoTurns < 20,
                                onClick = { autoTurns = (autoTurns + 1).coerceAtMost(20) }
                            )
                        }

                        if (window < 2) {
                            Text(
                                text = "回看窗口只有 1 轮，成员只看得到紧挨着的那句，话题很容易越传越偏；自由对话会自动按 2 轮处理",
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = UIFriendBarRed,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "窗口越大，其他人的发言占用的上下文和 token 越多；讨论模式每人上下文上限 4000 token，自由对话每一轮 2000 token。",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = UIFriendBarItemMetaColor,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "成员（${members.size}/$maxMembers）",
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UIFriendBarTitleColor
                    )
                    Text(
                        text = "管理",
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(UIFriendBarTagBgBlue)
                            .clickable { showMembers = true }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = UIFriendBarTagTextBlue
                    )
                }

                if (members.isEmpty()) {
                    Text(
                        text = "还没有成员",
                        fontSize = 13.sp,
                        color = UIFriendBarItemMetaColor
                    )
                } else {
                    SettingsCard {
                        members.forEachIndexed { index, member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UIAvatar(
                                    name = member.name,
                                    avatarPath = member.avatarPath,
                                    size = 36.dp,
                                    shape = CircleShape
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = UIFriendBarTitleColor
                                    )
                                    val meta = listOf(member.role, member.model)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                    if (meta.isNotBlank()) {
                                        Text(
                                            text = meta,
                                            fontSize = 11.sp,
                                            color = UIFriendBarItemMetaColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (index < members.size - 1) SettingsDivider()
                        }
                    }
                }

                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "本轮约 $estimatedRequestCount 次请求",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = UIFriendBarTitleColor
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "单个 AI 上下文约 $estimatedContextTokens tokens",
                            fontSize = 12.sp,
                            color = UIFriendBarItemMetaColor
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "按字符估算，仅作参考，不等同于实际账单",
                            fontSize = 11.sp,
                            color = UIFriendBarItemMetaColor.copy(alpha = 0.8f)
                        )
                    }
                }

                SettingsCard {
                    SettingsActionRow(
                        title = "导出聊天记录",
                        subtitle = "把整屋聊天记录生成 Markdown，通过系统分享发送",
                        actionText = "导出",
                        actionColor = UIFriendBarTagTextBlue,
                        onClick = onExport
                    )
                }

                SettingsCard {
                    SettingsActionRow(
                        title = "清空聊天记录",
                        subtitle = "删除本房间全部消息，房间、成员和设置保留，不可恢复",
                        actionText = if (messageCount > 0) "清空（$messageCount）" else "清空",
                        actionColor = if (messageCount > 0) UIFriendBarRed else UIFriendBarItemMetaColor,
                        enabled = messageCount > 0,
                        onClick = { showClearConfirm = true }
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (pendingDiscussion) {
        UIIosDialog(
            title = "开启讨论模式？",
            subtitle = "开启后每位 AI 会额外看到其他人最近 $window 轮的发言，每一位的输入 token 都会上涨。",
            onDismiss = { pendingDiscussion = false },
            actions = {
                UIDialogButtons(
                    confirmText = "开启",
                    onConfirm = {
                        discussion = true
                        pendingDiscussion = false
                    },
                    onCancel = { pendingDiscussion = false }
                )
            }
        )
    }

    pendingWindow?.let { target ->
        UIIosDialog(
            title = "调大回看窗口？",
            subtitle = "从 $window 轮调到 $target 轮后，每位 AI 会多看到其他人的发言，token 会同步上涨。",
            onDismiss = { pendingWindow = null },
            actions = {
                UIDialogButtons(
                    confirmText = "继续",
                    onConfirm = {
                        window = target
                        pendingWindow = null
                    },
                    onCancel = { pendingWindow = null }
                )
            }
        )
    }

    if (showDiscardConfirm) {
        UIIosDialog(
            title = "还有改动没保存",
            subtitle = "直接返回会丢掉刚才改的内容。",
            onDismiss = { showDiscardConfirm = false },
            actions = {
                UIDialogButtons(
                    confirmText = "保存并返回",
                    confirmEnabled = name.isNotBlank(),
                    onConfirm = {
                        showDiscardConfirm = false
                        onSave(name, prompt, discussion, window, autoChat, autoTurns)
                    },
                    cancelText = "放弃修改",
                    onCancel = {
                        showDiscardConfirm = false
                        onBack()
                    }
                )
            }
        )
    }

    if (showClearConfirm) {
        UIIosDialog(
            title = "清空聊天记录？",
            subtitle = "会删除本房间的全部 $messageCount 条消息，房间、成员和设置保留。不可恢复。",
            onDismiss = { showClearConfirm = false },
            actions = {
                UIDialogButtons(
                    confirmText = "清空",
                    confirmColor = UIFriendBarRed,
                    onConfirm = {
                        showClearConfirm = false
                        onClearMessages()
                    },
                    onCancel = { showClearConfirm = false }
                )
            }
        )
    }

    cropUri?.let { uri ->
        UIAvatarCropDialog(
            uri = uri,
            onCancel = { cropUri = null },
            onConfirm = { bytes ->
                cropUri = null
                onChangeAvatar(bytes)
            }
        )
    }

    if (showMembers) {
        RoomMembersDialog(
            allFriends = allFriends,
            memberIds = members.map { it.id }.toSet(),
            maxMembers = maxMembers,
            onToggle = onToggleMember,
            onDismiss = { showMembers = false }
        )
    }
}

/** iOS 分组卡片容器。 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard)),
        content = content
    )
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(UISeparator)
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) UIFriendBarTitleColor else UIFriendBarItemMetaColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = UIFriendBarItemMetaColor
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = UIFriendBarTagTextBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFD7DCE5),
                uncheckedBorderColor = Color.Transparent,
                disabledCheckedTrackColor = Color(0xFFB9C6DD),
                disabledUncheckedTrackColor = Color(0xFFE3E7EE)
            )
        )
    }
}

@Composable
private fun StepperButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (enabled) UIFriendBarTagBgBlue else UIGlassSurface)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) UIFriendBarTagTextBlue else UIFriendBarItemMetaColor
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    actionText: String,
    actionColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = UIFriendBarTitleColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = UIFriendBarItemMetaColor
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = actionText,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(actionColor.copy(alpha = if (enabled) 0.10f else 0.05f))
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) actionColor else actionColor.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = UIFriendBarItemMetaColor,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .uiControl(
                    shape = RoundedCornerShape(if (singleLine) 50.dp else UIRadiusControl),
                    color = UIGlassSurfaceStrong
                )
                .padding(horizontal = 16.dp, vertical = if (singleLine) 13.dp else 12.dp)
        ) {
            if (value.isEmpty() && placeholder != null) {
                Text(
                    text = placeholder,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = UIFriendBarItemMetaColor.copy(alpha = 0.7f)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = UIFriendBarTitleColor
                ),
                singleLine = singleLine,
                minLines = minLines,
                cursorBrush = SolidColor(UIFriendBarTagTextBlue)
            )
        }
    }
}
