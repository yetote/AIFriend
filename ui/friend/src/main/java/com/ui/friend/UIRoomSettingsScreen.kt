package com.ui.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.friend.data.UIRoomMember
import com.ui.friend.theme.UIFriendBarGray
import com.ui.friend.theme.UIFriendBarItemMetaColor
import com.ui.friend.theme.UIFriendBarSubtitleColor
import com.ui.friend.theme.UIFriendBarTagTextBlue
import com.ui.friend.theme.UIFriendBarTitleColor

/**
 * 聊天室设置：改名、房间级设定、成员管理入口。
 * 房间设定会拼在每位成员的 system prompt 前面。
 */
@Composable
fun UIRoomSettingsScreen(
    roomName: String,
    roomPrompt: String,
    members: List<UIRoomMember>,
    allFriends: List<UIRoomMember>,
    discussionEnabled: Boolean,
    discussionWindow: Int,
    sequentialSpeaking: Boolean,
    onSave: (
        name: String,
        prompt: String,
        discussionEnabled: Boolean,
        discussionWindow: Int,
        sequentialSpeaking: Boolean
    ) -> Unit,
    onToggleMember: (String) -> Unit,
    onBack: () -> Unit
) {
    var name by rememberSaveable(roomName) { mutableStateOf(roomName) }
    var prompt by rememberSaveable(roomPrompt) { mutableStateOf(roomPrompt) }
    var discussion by rememberSaveable(discussionEnabled) { mutableStateOf(discussionEnabled) }
    var window by rememberSaveable(discussionWindow) { mutableIntStateOf(discussionWindow.coerceIn(1, 5)) }
    var sequential by rememberSaveable(sequentialSpeaking) { mutableStateOf(sequentialSpeaking) }
    var showMembers by remember { mutableStateOf(false) }
    var pendingWindow by remember { mutableStateOf<Int?>(null) }
    var pendingDiscussion by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UIFriendBarGray)
            .pageInsets()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = UIFriendBarTitleColor
                )
            }
            Text(
                text = "聊天室设置",
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = UIFriendBarTitleColor
            )
            TextButton(
                onClick = { onSave(name, prompt, discussion, window, sequential) },
                enabled = name.isNotBlank()
            ) {
                Text(text = "保存", color = UIFriendBarTagTextBlue)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("房间名") },
                singleLine = true
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("房间设定（可选）") },
                placeholder = {
                    Text("例如：你们是一个评审团，先各自给结论，再给两条理由，不要互相重复。")
                },
                minLines = 4,
                maxLines = 8
            )
            Text(
                text = "这段设定会加在每位成员的 system prompt 前面，只影响之后的回答",
                fontSize = 12.sp,
                color = UIFriendBarSubtitleColor
            )

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "讨论模式",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIFriendBarTitleColor
                    )
                    Text(
                        text = "开启后每位 AI 能看到其他人最近的发言，token 会明显上涨",
                        fontSize = 12.sp,
                        color = UIFriendBarSubtitleColor
                    )
                }
                Switch(
                    checked = discussion,
                    onCheckedChange = { checked ->
                        if (checked) pendingDiscussion = true else discussion = false
                    }
                )
            }

            if (discussion) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "回看窗口",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = UIFriendBarTitleColor
                        )
                        Text(
                            text = "其他人的发言只保留最近几轮（1–5）",
                            fontSize = 12.sp,
                            color = UIFriendBarSubtitleColor
                        )
                    }
                    TextButton(
                        onClick = { window = (window - 1).coerceAtLeast(1) },
                        enabled = window > 1
                    ) {
                        Text(text = "−", fontSize = 16.sp)
                    }
                    Text(
                        text = "$window 轮",
                        fontSize = 14.sp,
                        color = UIFriendBarTitleColor
                    )
                    TextButton(
                        onClick = { if (window < 5) pendingWindow = window + 1 },
                        enabled = window < 5
                    ) {
                        Text(text = "＋", fontSize = 16.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "依次发言",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = UIFriendBarTitleColor
                        )
                        Text(
                            text = "后一位等前一位说完，能看到本轮前面的回复",
                            fontSize = 12.sp,
                            color = UIFriendBarSubtitleColor
                        )
                    }
                    Switch(
                        checked = sequential,
                        onCheckedChange = { sequential = it }
                    )
                }

                Text(
                    text = "窗口越大，其他人的发言占用的上下文和 token 越多；讨论模式每人上下文上限 4000 token。",
                    fontSize = 12.sp,
                    color = UIFriendBarSubtitleColor
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "成员（${members.size}）",
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = UIFriendBarTitleColor
                )
                TextButton(onClick = { showMembers = true }) {
                    Text(text = "管理", color = UIFriendBarTagTextBlue)
                }
            }
            if (members.isEmpty()) {
                Text(
                    text = "还没有成员",
                    fontSize = 13.sp,
                    color = UIFriendBarItemMetaColor
                )
            }
            members.forEach { member ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIAvatar(
                        name = member.name,
                        avatarPath = member.avatarPath,
                        size = 32.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = member.name,
                        fontSize = 14.sp,
                        color = UIFriendBarTitleColor
                    )
                }
            }
        }
    }

    if (pendingDiscussion) {
        AlertDialog(
            onDismissRequest = { pendingDiscussion = false },
            title = { Text(text = "开启讨论模式？") },
            text = {
                Text(
                    text = "开启后每位 AI 会额外看到其他人最近 $window 轮的发言，" +
                        "每一位的输入 token 都会上涨。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        discussion = true
                        pendingDiscussion = false
                    }
                ) {
                    Text(text = "开启", color = UIFriendBarTagTextBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscussion = false }) {
                    Text(text = "取消")
                }
            }
        )
    }

    pendingWindow?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingWindow = null },
            title = { Text(text = "调大回看窗口？") },
            text = {
                Text(
                    text = "从 $window 轮调到 $target 轮后，每位 AI 会多看到其他人的发言，" +
                        "token 会同步上涨。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        window = target
                        pendingWindow = null
                    }
                ) {
                    Text(text = "继续", color = UIFriendBarTagTextBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingWindow = null }) {
                    Text(text = "取消")
                }
            }
        )
    }

    if (showMembers) {
        RoomMembersDialog(
            allFriends = allFriends,
            memberIds = members.map { it.id }.toSet(),
            onToggle = onToggleMember,
            onDismiss = { showMembers = false }
        )
    }
}
