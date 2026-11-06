package com.ui.room

import com.ui.common.pageInsets
import com.ui.common.UIAvatar
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.room.data.UIRoomMember
import com.ui.room.data.UIRoomMessage
import com.ui.common.theme.UIChatAssistantBubble
import com.ui.common.theme.UIChatErrorBg
import com.ui.common.theme.UIChatInputBg
import com.ui.common.theme.UIChatSendDisabled
import com.ui.common.theme.UIChatUserBubble
import com.ui.common.theme.UIFriendBarGray
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarSubtitleColor
import com.ui.common.theme.UIFriendBarTagBgBlue
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun UIRoomChatPage(
    roomName: String,
    avatarPath: String?,
    members: List<UIRoomMember>,
    allFriends: List<UIRoomMember>,
    maxMembers: Int,
    messages: List<UIRoomMessage>,
    input: String,
    userAvatarPath: String?,
    pendingIds: Set<String>,
    errors: Map<String, String>,
    mentionIds: Set<String>,
    hasMoreMessages: Boolean,
    isLoadingMore: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onToggleMember: (String) -> Unit,
    onMentionSelect: (Set<String>) -> Unit,
    onStopReply: (String) -> Unit,
    onStopAll: () -> Unit,
    onOpenSettings: () -> Unit,
    onLoadMore: () -> Unit
) {
    var showMembers by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showMentionPicker by remember { mutableStateOf(false) }
    var mentionTriggerAt by remember { mutableStateOf<Int?>(null) }
    var actionMessage by remember { mutableStateOf<UIRoomMessage?>(null) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val memberNames = remember(members) { members.associate { it.id to it.name } }
    val listState = rememberLazyListState()
    val totalItems = messages.size + pendingIds.size + errors.size
    val topOffset = if (isLoadingMore) 1 else 0
    val lastItemIndex = topOffset + totalItems - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UIFriendBarGray)
            .pageInsets()
    ) {
        RoomTopBar(
            roomName = roomName,
            avatarPath = avatarPath,
            memberCount = members.size,
            onBack = onBack,
            onManageMembers = { showMembers = true },
            onSearch = { showSearch = true },
            onOpenSettings = onOpenSettings,
            showStop = pendingIds.isNotEmpty(),
            onStopAll = onStopAll
        )

        var didInitialScroll by remember { mutableStateOf(false) }
        var listHeight by remember { mutableIntStateOf(0) }

        LaunchedEffect(messages.lastOrNull()?.id, pendingIds.size, errors.size) {
            if (totalItems <= 0) return@LaunchedEffect
            if (!didInitialScroll) {
                listState.scrollToItem(lastItemIndex)
                didInitialScroll = true
            } else {
                listState.animateScrollToItem(lastItemIndex)
            }
        }

        // 键盘弹出/收起会连续改变列表高度：只有最后一条仍在可视区时才跟随，
        // 避免向上翻页加载旧消息时被拉回底部。
        LaunchedEffect(listHeight) {
            if (listHeight > 0 && totalItems > 0) {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                if (lastVisible != null && lastVisible >= lastItemIndex) {
                    listState.scrollToItem(lastItemIndex)
                }
            }
        }

        LaunchedEffect(listState, hasMoreMessages, isLoadingMore, didInitialScroll) {
            if (!didInitialScroll) return@LaunchedEffect
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { index ->
                    if (index <= 1 && hasMoreMessages && !isLoadingMore) {
                        onLoadMore()
                    }
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
                        text = "向房间里的 ${members.size} 位好友提问；点输入框左侧 @ 可以只问某位成员",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, start = 24.dp, end = 24.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = UIFriendBarSubtitleColor
                    )
                }
            }
            if (isLoadingMore) {
                item(key = "loading_more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = UIFriendBarTagTextBlue
                        )
                    }
                }
            }
            itemsIndexed(
                items = messages,
                key = { _, message -> message.id }
            ) { index, message ->
                val previous = messages.getOrNull(index - 1)
                val showDate = previous == null || !isSameDay(previous.createdAt, message.createdAt)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (showDate) {
                        RoomDateSeparator(timestamp = message.createdAt)
                    }
                    if (message.isSystem) {
                        RoomSystemMessage(text = message.content)
                    } else {
                        RoomMessageBubble(
                            message = message,
                            userAvatarPath = userAvatarPath,
                            onLongPress = { actionMessage = message }
                        )
                    }
                }
            }
            pendingIds.forEach { friendId ->
                item {
                    TypingRow(
                        name = memberNames[friendId] ?: "好友",
                        onStop = { onStopReply(friendId) }
                    )
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
            onSend = onSend,
            onAtTyped = { index ->
                mentionTriggerAt = index
                showMentionPicker = true
            }
        )
    }

    if (showMentionPicker) {
        MentionPickerDialog(
            members = members,
            selectedIds = mentionIds,
            onDismiss = {
                showMentionPicker = false
                mentionTriggerAt = null
            },
            onConfirm = { ids ->
                val insertAt = (mentionTriggerAt ?: input.length).coerceIn(0, input.length)
                val mentionText = members
                    .filter { it.id in ids }
                    .joinToString(" ") { "@${it.name}" }
                    .let { if (it.isBlank()) "" else "$it " }
                onInputChange(
                    input.substring(0, insertAt) + mentionText + input.substring(insertAt)
                )
                onMentionSelect(ids)
                mentionTriggerAt = null
                showMentionPicker = false
            }
        )
    }

    actionMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { actionMessage = null },
            title = {
                Text(text = if (target.isUser) "我" else (target.senderName ?: "已移除"))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = target.content,
                        fontSize = 14.sp,
                        color = UIFriendBarTitleColor,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(target.content))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            actionMessage = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "复制", color = UIFriendBarTagTextBlue)
                    }
                    TextButton(
                        onClick = {
                            val name = if (target.isUser) "我" else (target.senderName ?: "已移除")
                            val excerpt = target.content.take(80).let { text ->
                                if (target.content.length > 80) "$text…" else text
                            }
                            onInputChange("「$name：$excerpt」\n" + input)
                            actionMessage = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "引用", color = UIFriendBarTagTextBlue)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionMessage = null }) {
                    Text(text = "取消")
                }
            }
        )
    }

    if (showSearch) {
        RoomSearchDialog(
            messages = messages,
            onSelect = { target ->
                showSearch = false
                val index = messages.indexOfFirst { it.id == target.id }
                if (index >= 0) {
                    scope.launch { listState.scrollToItem(topOffset + index) }
                }
            },
            onDismiss = { showSearch = false }
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

@Composable
private fun RoomTopBar(
    roomName: String,
    avatarPath: String?,
    memberCount: Int,
    onBack: () -> Unit,
    onManageMembers: () -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    showStop: Boolean,
    onStopAll: () -> Unit
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
        UIAvatar(
            name = roomName.ifBlank { "房间" },
            avatarPath = avatarPath,
            size = 36.dp
        )
        Spacer(Modifier.width(10.dp))
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
        if (showStop) {
            TextButton(onClick = onStopAll) {
                Text(text = "停止", fontSize = 13.sp, color = UIFriendBarRed)
            }
        }
        IconButton(onClick = onSearch) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "搜索聊天记录",
                tint = UIFriendBarSubtitleColor
            )
        }
        TextButton(onClick = onManageMembers) {
            Text(text = "成员", fontSize = 13.sp, color = UIFriendBarTagTextBlue)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "房间设置",
                tint = UIFriendBarSubtitleColor
            )
        }
    }
}

@Composable
private fun RoomDateSeparator(timestamp: Long) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatRoomDate(timestamp),
            fontSize = 11.sp,
            color = UIFriendBarItemMetaColor,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(UIChatInputBg)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoomMessageBubble(
    message: UIRoomMessage,
    userAvatarPath: String?,
    onLongPress: () -> Unit
) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = message.senderName ?: "已移除",
                        fontSize = 11.sp,
                        color = UIFriendBarItemMetaColor
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatRoomTime(message.createdAt),
                        fontSize = 10.sp,
                        color = UIFriendBarItemMetaColor
                    )
                }
            } else {
                Text(
                    text = formatRoomTime(message.createdAt),
                    fontSize = 10.sp,
                    color = UIFriendBarItemMetaColor,
                    modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)
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
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
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
private fun TypingRow(name: String, onStop: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$name 正在输入…",
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            color = UIFriendBarItemMetaColor
        )
        TextButton(onClick = onStop) {
            Text(text = "停止", fontSize = 12.sp, color = UIFriendBarRed)
        }
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
private fun MentionPickerDialog(
    members: List<UIRoomMember>,
    selectedIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var selected by remember { mutableStateOf(selectedIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "选择 @ 对象") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                members.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (member.id in selected) {
                                    selected - member.id
                                } else {
                                    selected + member.id
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = member.id in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) {
                                    selected + member.id
                                } else {
                                    selected - member.id
                                }
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        UIAvatar(
                            name = member.name,
                            avatarPath = member.avatarPath,
                            size = 32.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = member.name,
                            fontSize = 15.sp,
                            color = UIFriendBarTitleColor
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(text = "确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        }
    )
}

@Composable
private fun RoomInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAtTyped: (Int) -> Unit
) {
    val canSend = input.isNotBlank()
    var value by remember { mutableStateOf(TextFieldValue(input)) }

    LaunchedEffect(input) {
        if (value.text != input) {
            value = value.copy(text = input, selection = TextRange(input.length))
        }
    }

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
                    text = "说点什么…",
                    fontSize = 15.sp,
                    color = UIFriendBarItemMetaColor
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { new ->
                    val addedAt = new.text.length > value.text.length &&
                        new.text.count { it == '@' } > value.text.count { it == '@' }
                    if (addedAt) {
                        val index = (new.selection.start - 1)
                            .coerceIn(0, new.text.lastIndex.coerceAtLeast(0))
                        if (new.text.getOrNull(index) == '@') {
                            val stripped = new.text.removeRange(index, index + 1)
                            value = new.copy(text = stripped, selection = TextRange(index))
                            onInputChange(stripped)
                            onAtTyped(index)
                        } else {
                            value = new
                            onInputChange(new.text)
                        }
                    } else {
                        value = new
                        onInputChange(new.text)
                    }
                },
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
private fun RoomSearchDialog(
    messages: List<UIRoomMessage>,
    onSelect: (UIRoomMessage) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, messages) {
        if (query.isBlank()) {
            emptyList()
        } else {
            messages
                .filter { !it.isSystem && it.content.contains(query, ignoreCase = true) }
                .takeLast(50)
                .asReversed()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "搜索聊天记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入关键词") },
                    singleLine = true
                )
                Text(
                    text = "仅搜索已加载的消息（最近 ${messages.size} 条）",
                    fontSize = 11.sp,
                    color = UIFriendBarItemMetaColor
                )
                Column(
                    modifier = Modifier
                        .height(260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when {
                        query.isBlank() -> Text(
                            text = "输入关键词开始搜索",
                            fontSize = 13.sp,
                            color = UIFriendBarSubtitleColor
                        )

                        results.isEmpty() -> Text(
                            text = "没有匹配的消息",
                            fontSize = 13.sp,
                            color = UIFriendBarSubtitleColor
                        )

                        else -> results.forEach { message ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(message) }
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (message.isUser) {
                                        "我"
                                    } else {
                                        message.senderName ?: "已移除"
                                    },
                                    fontSize = 11.sp,
                                    color = UIFriendBarItemMetaColor
                                )
                                Text(
                                    text = message.content,
                                    fontSize = 13.sp,
                                    color = UIFriendBarTitleColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatRoomTime(message.createdAt),
                                    fontSize = 10.sp,
                                    color = UIFriendBarItemMetaColor
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "关闭")
            }
        }
    )
}

@Composable
fun RoomMembersDialog(
    allFriends: List<UIRoomMember>,
    memberIds: Set<String>,
    maxMembers: Int,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingRemove by remember { mutableStateOf<UIRoomMember?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "房间成员（${memberIds.size}/$maxMembers）") },
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
                    val checked = friend.id in memberIds
                    val canToggle = if (checked) memberIds.size > 1 else memberIds.size < maxMembers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = canToggle) {
                                if (checked) pendingRemove = friend else onToggle(friend.id)
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            enabled = canToggle,
                            onCheckedChange = {
                                if (checked) pendingRemove = friend else onToggle(friend.id)
                            }
                        )
                        Spacer(Modifier.width(4.dp))
                        UIAvatar(
                            name = friend.name,
                            avatarPath = friend.avatarPath,
                            size = 28.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = friend.name,
                                fontSize = 14.sp,
                                color = UIFriendBarTitleColor
                            )
                            val meta = listOf(friend.role, friend.model)
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
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "完成", color = UIFriendBarTagTextBlue)
            }
        }
    )

    pendingRemove?.let { friend ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(text = "移除成员？") },
            text = {
                Text(text = "将「${friend.name}」移出房间；它已经发过的消息会保留。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemove = null
                        onToggle(friend.id)
                    }
                ) {
                    Text(text = "移除", color = UIFriendBarRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text(text = "取消")
                }
            }
        )
    }
}

private fun isSameDay(first: Long, second: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = first }
    val b = Calendar.getInstance().apply { timeInMillis = second }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private fun formatRoomDate(timestamp: Long): String = when {
    DateUtils.isToday(timestamp) -> "今天"
    DateUtils.isToday(timestamp + 24L * 60 * 60 * 1000) -> "昨天"
    else -> SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date(timestamp))
}

private fun formatRoomTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
