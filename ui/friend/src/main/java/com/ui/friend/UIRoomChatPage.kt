package com.ui.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.friend.data.UIRoomMember
import com.ui.friend.data.UIRoomMessage
import com.ui.friend.theme.UIChatAssistantBubble
import com.ui.friend.theme.UIChatErrorBg
import com.ui.friend.theme.UIChatInputBg
import com.ui.friend.theme.UIChatSendDisabled
import com.ui.friend.theme.UIChatUserBubble
import com.ui.friend.theme.UIFriendBarGray
import com.ui.friend.theme.UIFriendBarItemMetaColor
import com.ui.friend.theme.UIFriendBarRed
import com.ui.friend.theme.UIFriendBarSubtitleColor
import com.ui.friend.theme.UIFriendBarTagBgBlue
import com.ui.friend.theme.UIFriendBarTagTextBlue
import com.ui.friend.theme.UIFriendBarTitleColor

@Composable
fun UIRoomChatPage(
    roomName: String,
    members: List<UIRoomMember>,
    allFriends: List<UIRoomMember>,
    messages: List<UIRoomMessage>,
    input: String,
    userAvatarPath: String?,
    pendingIds: Set<String>,
    errors: Map<String, String>,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onToggleMember: (String) -> Unit
) {
    var showMembers by remember { mutableStateOf(false) }
    val memberNames = remember(members) { members.associate { it.id to it.name } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UIFriendBarGray)
            .pageInsets()
    ) {
        RoomTopBar(
            roomName = roomName,
            memberCount = members.size,
            onBack = onBack,
            onManageMembers = { showMembers = true }
        )

        val listState = rememberLazyListState()
        val totalItems = messages.size + pendingIds.size + errors.size
        var didInitialScroll by remember { mutableStateOf(false) }
        var listHeight by remember { mutableIntStateOf(0) }

        LaunchedEffect(messages.size, pendingIds.size, errors.size) {
            if (totalItems <= 0) return@LaunchedEffect
            val target = totalItems - 1
            if (!didInitialScroll) {
                listState.scrollToItem(target)
                didInitialScroll = true
            } else {
                listState.animateScrollToItem(target)
            }
        }

        // 键盘弹出/收起会连续改变列表高度：直接跟随，避免每帧重启动画
        LaunchedEffect(listHeight) {
            if (listHeight > 0 && totalItems > 0) {
                listState.scrollToItem(totalItems - 1)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { listHeight = it.height },
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = "向房间里的 ${members.size} 位好友同时提问，每人都只看到你的消息和自己的回答",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, start = 24.dp, end = 24.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = UIFriendBarSubtitleColor
                    )
                }
            }
            items(messages) { message ->
                if (message.isSystem) {
                    RoomSystemMessage(text = message.content)
                } else {
                    RoomMessageBubble(message = message, userAvatarPath = userAvatarPath)
                }
            }
            pendingIds.forEach { friendId ->
                item {
                    TypingRow(name = memberNames[friendId] ?: "好友")
                }
            }
            errors.forEach { (friendId, message) ->
                item {
                    RoomErrorRow(
                        name = memberNames[friendId] ?: "好友",
                        message = message,
                        onRetry = { onRetry(friendId) }
                    )
                }
            }
        }

        RoomInputBar(
            input = input,
            onInputChange = onInputChange,
            onSend = onSend
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

@Composable
private fun RoomTopBar(
    roomName: String,
    memberCount: Int,
    onBack: () -> Unit,
    onManageMembers: () -> Unit
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
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = roomName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = UIFriendBarTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$memberCount 位好友",
                fontSize = 12.sp,
                color = UIFriendBarSubtitleColor
            )
        }
        TextButton(onClick = onManageMembers) {
            Text(text = "成员", fontSize = 13.sp, color = UIFriendBarTagTextBlue)
        }
    }
}

@Composable
private fun RoomSystemMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = UIFriendBarItemMetaColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun RoomMessageBubble(message: UIRoomMessage, userAvatarPath: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!message.isUser) {
            UIAvatar(
                name = message.senderName ?: "AI",
                avatarPath = message.avatarPath,
                size = 32.dp
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
        ) {
            if (!message.isUser) {
                Text(
                    text = message.senderName ?: "已移除",
                    fontSize = 11.sp,
                    color = UIFriendBarItemMetaColor,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomEnd = if (message.isUser) 4.dp else 16.dp,
                            bottomStart = if (message.isUser) 16.dp else 4.dp
                        )
                    )
                    .background(if (message.isUser) UIChatUserBubble else UIChatAssistantBubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = if (message.isUser) Color.White else UIFriendBarTitleColor
                )
            }
        }
        if (message.isUser) {
            Spacer(Modifier.width(8.dp))
            UIAvatar(
                name = "我",
                avatarPath = userAvatarPath,
                size = 32.dp,
                shape = CircleShape
            )
        }
    }
}

@Composable
private fun TypingRow(name: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = "$name 正在输入…",
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            color = UIFriendBarItemMetaColor
        )
    }
}

@Composable
private fun RoomErrorRow(name: String, message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(UIChatErrorBg)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$name：$message",
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = UIFriendBarRed
        )
        TextButton(onClick = onRetry) {
            Text(text = "重试", fontSize = 13.sp, color = UIFriendBarRed)
        }
    }
}

@Composable
private fun RoomInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val canSend = input.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(UIChatInputBg)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (input.isEmpty()) {
                Text(
                    text = "对所有人说点什么…",
                    fontSize = 15.sp,
                    color = UIFriendBarItemMetaColor
                )
            }
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 15.sp, color = UIFriendBarTitleColor),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() })
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (canSend) UIChatUserBubble else UIChatSendDisabled)
                .clickable(enabled = canSend) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RoomMembersDialog(
    allFriends: List<UIRoomMember>,
    memberIds: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "房间成员") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .height(320.dp)
            ) {
                if (allFriends.isEmpty()) {
                    Text(text = "还没有创建任何好友", fontSize = 13.sp, color = UIFriendBarSubtitleColor)
                }
                allFriends.forEach { friend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(friend.id) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = friend.id in memberIds,
                            onCheckedChange = { onToggle(friend.id) }
                        )
                        Spacer(Modifier.width(4.dp))
                        UIAvatar(
                            name = friend.name,
                            avatarPath = friend.avatarPath,
                            size = 28.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = friend.name,
                            fontSize = 14.sp,
                            color = UIFriendBarTitleColor
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "完成", color = UIFriendBarTagTextBlue)
            }
        }
    )
}
