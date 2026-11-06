package com.ui.room

import com.ui.common.pageInsets
import com.ui.common.UIAvatar
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIBlobBlue
import com.ui.common.theme.UIBubbleUserBrush
import com.ui.common.theme.UIChatErrorBg
import com.ui.common.theme.UIChatSendDisabled
import com.ui.common.theme.UIDialogButtons
import com.ui.common.theme.UIComposerHeight
import com.ui.common.theme.UIFieldFill
import com.ui.common.theme.UIGlassBar
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurface
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIIosDialog
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusBubble
import com.ui.common.theme.UIRadiusBubbleTail
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UIShadowSoft
import com.ui.common.theme.uiCard
import com.ui.common.theme.uiControl
import com.ui.common.theme.uiHairlineBottom
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
    autoChatActive: Boolean = false,
    autoChatCompleted: Int = 0,
    autoChatTotal: Int = 0,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onToggleMember: (String) -> Unit,
    onMentionSelect: (Set<String>) -> Unit,
    onStopReply: (String) -> Unit,
    onStopAll: () -> Unit,
    onStopAutoChat: () -> Unit = {},
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

    UIGradientBackground(modifier = Modifier.pageInsets()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            RoomTopBar(
                roomName = roomName,
                avatarPath = avatarPath,
                memberCount = members.size,
                onBack = onBack,
                onManageMembers = { showMembers = true },
                onSearch = { showSearch = true },
                onOpenSettings = onOpenSettings,
                showStop = pendingIds.isNotEmpty() || autoChatActive,
                onStopAll = if (autoChatActive) onStopAutoChat else onStopAll,
                autoChatActive = autoChatActive,
                autoChatCompleted = autoChatCompleted,
                autoChatTotal = autoChatTotal
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 56.dp, start = 24.dp, end = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(UIGlassSurfaceStrong)
                                    .border(0.5.dp, UIGlassStroke, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "@", fontSize = 26.sp, color = UIFriendBarTagTextBlue)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "开始和 ${members.size} 位好友聊天",
                                textAlign = TextAlign.Center,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UIFriendBarTitleColor
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "输入 @ 可以只问某位成员，不 @ 则全员回答",
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = UIFriendBarItemMetaColor
                            )
                        }
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
        UIIosDialog(
            title = if (target.isUser) "我" else (target.senderName ?: "已移除"),
            subtitle = formatRoomTime(target.createdAt),
            onDismiss = { actionMessage = null },
            actions = {
                UIDialogButtons(
                    confirmText = "关闭",
                    onConfirm = { actionMessage = null },
                    cancelText = null
                )
            }
        ) {
            Text(
                text = target.content,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = UIFriendBarTitleColor,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DialogPillAction(
                    text = "复制",
                    modifier = Modifier.weight(1f)
                ) {
                    clipboard.setText(AnnotatedString(target.content))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    actionMessage = null
                }
                DialogPillAction(
                    text = "引用",
                    modifier = Modifier.weight(1f)
                ) {
                    val name = if (target.isUser) "我" else (target.senderName ?: "已移除")
                    val excerpt = target.content.take(80).let { text ->
                        if (target.content.length > 80) "$text…" else text
                    }
                    onInputChange("「$name：$excerpt」\n" + input)
                    actionMessage = null
                }
            }
        }
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
    onStopAll: () -> Unit,
    autoChatActive: Boolean = false,
    autoChatCompleted: Int = 0,
    autoChatTotal: Int = 0
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 顶栏不再铺白底：通栏直角白带是整页唯一一块贴边的平面白，
            // 和圆角卡片体系放在一起必然突兀。这里直接留空、让渐变透上来，
            // 顶栏就和聊天区连成一片，只靠一条 hairline 交代内容的起点。
            .uiHairlineBottom()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = UIFriendBarTitleColor
            )
        }
        UIAvatar(
            name = roomName.ifBlank { "房间" },
            avatarPath = avatarPath,
            size = 38.dp
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = roomName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = UIFriendBarTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (autoChatActive) {
                    val total = if (autoChatTotal > 0) "/$autoChatTotal" else ""
                    "自由对话中 $autoChatCompleted$total"
                } else {
                    "$memberCount 位好友"
                },
                fontSize = 12.sp,
                color = if (autoChatActive) UIFriendBarTagTextBlue else UIFriendBarItemMetaColor
            )
        }
        if (autoChatActive) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(UIFriendBarTagBgBlue)
                    .clickable(onClick = onStopAll)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "结束自由对话",
                        tint = UIFriendBarTagTextBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "结束",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = UIFriendBarTagTextBlue
                    )
                }
            }
        } else if (showStop) {
            IconButton(onClick = onStopAll) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "全部停止",
                    tint = UIFriendBarRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        IconButton(onClick = onSearch) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "搜索聊天记录",
                tint = UIFriendBarSubtitleColor,
                modifier = Modifier.size(21.dp)
            )
        }
        IconButton(onClick = onManageMembers) {
            Icon(
                imageVector = Icons.Filled.Group,
                contentDescription = "房间成员",
                tint = UIFriendBarSubtitleColor,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "房间设置",
                tint = UIFriendBarSubtitleColor,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun RoomDateSeparator(timestamp: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatRoomDate(timestamp),
            fontSize = 11.sp,
            color = UIFriendBarItemMetaColor,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(UIGlassSurface)
                .padding(horizontal = 12.dp, vertical = 4.dp)
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
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(UIGlassSurface)
                .padding(horizontal = 14.dp, vertical = 6.dp)
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
                size = 34.dp,
                shape = CircleShape
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
        ) {
            if (!message.isUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = message.senderName ?: "已移除",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = UIFriendBarSubtitleColor
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
                    modifier = Modifier.padding(end = 6.dp, bottom = 3.dp)
                )
            }
            // 气泡形状只在这里算一次。原来 shadow / clip / border 各自手写一遍圆角，
            // 结果 border 那处漏了「尾巴角」，按四角 20dp 画，裁切却是 6dp，
            // 于是在气泡内侧左下角多出一条悬空的弧线。收成一个变量就不会再漏。
            val bubbleShape = RoundedCornerShape(
                topStart = UIRadiusBubble,
                topEnd = UIRadiusBubble,
                bottomEnd = if (message.isUser) UIRadiusBubbleTail else UIRadiusBubble,
                bottomStart = if (message.isUser) UIRadiusBubble else UIRadiusBubbleTail
            )
            Box(
                modifier = Modifier
                    .widthIn(max = 268.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = bubbleShape,
                        clip = false,
                        ambientColor = UIShadowSoft,
                        spotColor = UIShadowSoft
                    )
                    .clip(bubbleShape)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
                    .then(
                        if (message.isUser) {
                            Modifier.background(UIBubbleUserBrush)
                        } else {
                            Modifier
                                .background(UIGlassSurfaceStrong)
                                .border(0.5.dp, UIGlassStroke, bubbleShape)
                        }
                    )
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
                size = 34.dp,
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
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(UIGlassSurfaceStrong)
                .border(0.5.dp, UIGlassStroke, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = UIFriendBarTagTextBlue
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$name 正在输入…",
                fontSize = 13.sp,
                color = UIFriendBarSubtitleColor
            )
        }
        Spacer(Modifier.weight(1f))
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
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(UIChatErrorBg)
            .border(0.5.dp, UIFriendBarRed.copy(alpha = 0.12f), RoundedCornerShape(UIRadiusControl))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
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

    UIIosDialog(
        title = "选择 @ 对象",
        subtitle = "只让选中的人回答这一条，其他人看不到它",
        onDismiss = onDismiss,
        actions = {
            UIDialogButtons(
                confirmText = if (selected.isEmpty()) "全员回答" else "确定（${selected.size}）",
                onConfirm = { onConfirm(selected) },
                onCancel = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            members.forEach { member ->
                val checked = member.id in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(UIRadiusControl))
                        .clickable {
                            selected = if (checked) {
                                selected - member.id
                            } else {
                                selected + member.id
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIAvatar(
                        name = member.name,
                        avatarPath = member.avatarPath,
                        size = 34.dp,
                        shape = CircleShape
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = member.name,
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = UIFriendBarTitleColor
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .then(
                                if (checked) {
                                    Modifier.background(UIActionBrush)
                                } else {
                                    Modifier.border(1.5.dp, UIGlassStroke, CircleShape)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (checked) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
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
        // 不再用整条通栏白底：那是一块和聊天气泡、卡片体系无关的直角白墙。
        // 改成浮在渐变上的圆角玻璃条，和页面里其他卡片用同一套语言（圆角 + 描边 + 柔和投影）。
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .uiCard(
                shape = RoundedCornerShape(UIRadiusCard),
                color = UIGlassBar,
                elevation = 12.dp
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                // 外层已经是白卡，内层再压一块纯白 + 投影会糊成一片；
                // 改成浅灰底的凹陷胶囊，输入区才和卡片分得开
                .clip(RoundedCornerShape(UIRadiusBubble))
                .background(UIFieldFill)
                .border(0.5.dp, UIGlassStroke, RoundedCornerShape(UIRadiusBubble))
                // 和发送按钮共用同一个基准高度：单行时两者严格等高，
                // 多行时输入框自己长高，按钮仍对齐底部
                .heightIn(min = UIComposerHeight)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
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
                // 与输入框共用基准高度，单行时严格等高
                .size(UIComposerHeight)
                .shadow(
                    elevation = if (canSend) 8.dp else 0.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = UIBlobBlue,
                    spotColor = UIBlobBlue
                )
                .clip(CircleShape)
                .then(
                    if (canSend) {
                        Modifier.background(UIActionBrush)
                    } else {
                        Modifier.background(UIChatSendDisabled.copy(alpha = 0.6f))
                    }
                )
                .clickable(enabled = canSend) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
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

    UIIosDialog(
        title = "搜索聊天记录",
        subtitle = "仅搜索已加载的消息（最近 ${messages.size} 条）",
        onDismiss = onDismiss,
        actions = {
            UIDialogButtons(
                confirmText = "关闭",
                onConfirm = onDismiss
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .uiControl(shape = RoundedCornerShape(50), color = UIGlassSurface)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "输入关键词",
                    fontSize = 14.sp,
                    color = UIFriendBarItemMetaColor.copy(alpha = 0.7f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = UIFriendBarTitleColor),
                cursorBrush = SolidColor(UIFriendBarTagTextBlue)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
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
                            .clip(RoundedCornerShape(UIRadiusControl))
                            .clickable { onSelect(message) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (message.isUser) {
                                    "我"
                                } else {
                                    message.senderName ?: "已移除"
                                },
                                modifier = Modifier.weight(1f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = UIFriendBarTagTextBlue
                            )
                            Text(
                                text = formatRoomTime(message.createdAt),
                                fontSize = 10.sp,
                                color = UIFriendBarItemMetaColor
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = message.content,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = UIFriendBarTitleColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
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

    UIIosDialog(
        title = "房间成员",
        subtitle = "已选 ${memberIds.size} / $maxMembers 位",
        onDismiss = onDismiss,
        actions = {
            UIDialogButtons(
                confirmText = "完成",
                onConfirm = onDismiss,
                cancelText = null
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (allFriends.isEmpty()) {
                Text(
                    text = "还没有创建任何好友",
                    fontSize = 13.sp,
                    color = UIFriendBarSubtitleColor
                )
            }
            allFriends.forEach { friend ->
                val checked = friend.id in memberIds
                val canToggle = if (checked) memberIds.size > 1 else memberIds.size < maxMembers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(UIRadiusControl))
                        .clickable(enabled = canToggle) {
                            if (checked) pendingRemove = friend else onToggle(friend.id)
                        }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIAvatar(
                        name = friend.name,
                        avatarPath = friend.avatarPath,
                        size = 34.dp,
                        shape = CircleShape
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = friend.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
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
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .then(
                                if (checked) {
                                    Modifier.background(UIActionBrush)
                                } else {
                                    Modifier.border(1.5.dp, UIGlassStroke, CircleShape)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (checked) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    pendingRemove?.let { friend ->
        UIIosDialog(
            title = "移除成员？",
            subtitle = "将「${friend.name}」移出房间；它已经发过的消息会保留。",
            onDismiss = { pendingRemove = null },
            actions = {
                UIDialogButtons(
                    confirmText = "移除",
                    confirmColor = UIFriendBarRed,
                    onConfirm = {
                        pendingRemove = null
                        onToggle(friend.id)
                    },
                    onCancel = { pendingRemove = null }
                )
            }
        )
    }
}

/** 弹窗里的胶囊动作按钮（复制 / 引用）。 */
@Composable
private fun DialogPillAction(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(UIFriendBarTagBgBlue)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = UIFriendBarTagTextBlue
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
