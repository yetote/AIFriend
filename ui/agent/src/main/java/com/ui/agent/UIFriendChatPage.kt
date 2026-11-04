package com.ui.agent

import com.ui.common.pageInsets
import com.ui.common.UIAvatarCropDialog
import com.ui.common.UIAvatar
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.ai.model.ChatMessage
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

@Composable
fun UIFriendChatPage(
    friendName: String,
    role: String,
    model: String,
    avatarPath: String?,
    userAvatarPath: String?,
    messages: List<ChatMessage>,
    input: String,
    isSending: Boolean,
    error: String?,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onChangeAvatar: (ByteArray) -> Unit
) {
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UIFriendBarGray)
            .pageInsets()
    ) {
        ChatTopBar(
            friendName = friendName,
            role = role,
            model = model,
            avatarPath = avatarPath,
            onBack = onBack,
            onAvatarClick = {
                avatarPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )

        val listState = rememberLazyListState()
        val totalItems = messages.size + (if (isSending) 1 else 0) + (if (error != null) 1 else 0)
        var didInitialScroll by remember { mutableStateOf(false) }
        var listHeight by remember { mutableIntStateOf(0) }

        LaunchedEffect(messages.size, isSending, error) {
            if (totalItems <= 0) return@LaunchedEffect
            val target = totalItems - 1
            if (!didInitialScroll) {
                listState.scrollToItem(target)
                didInitialScroll = true
            } else {
                listState.animateScrollToItem(target)
            }
        }

        // 键盘弹出/收起会连续改变列表高度：直接跟随，不要每帧重启动画，否则会「顿」一下
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
                        text = "和「$friendName」打个招呼吧",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = UIFriendBarSubtitleColor
                    )
                }
            }
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    friendName = friendName,
                    avatarPath = avatarPath,
                    userAvatarPath = userAvatarPath
                )
            }
            if (isSending) {
                item { TypingIndicator(friendName) }
            }
            if (error != null) {
                item { ErrorBanner(message = error, onRetry = onRetry) }
            }
        }

        ChatInputBar(
            input = input,
            isSending = isSending,
            onInputChange = onInputChange,
            onSend = onSend
        )
    }

    val pendingCrop = cropUri
    if (pendingCrop != null) {
        UIAvatarCropDialog(
            uri = pendingCrop,
            onCancel = { cropUri = null },
            onConfirm = { bytes ->
                cropUri = null
                onChangeAvatar(bytes)
            }
        )
    }
}

@Composable
private fun ChatTopBar(
    friendName: String,
    role: String,
    model: String,
    avatarPath: String?,
    onBack: () -> Unit,
    onAvatarClick: () -> Unit
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
        UIAvatar(
            name = friendName,
            avatarPath = avatarPath,
            size = 36.dp,
            modifier = Modifier.clickable { onAvatarClick() }
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friendName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = UIFriendBarTitleColor
            )
            if (role.isNotBlank()) {
                Text(
                    text = role,
                    fontSize = 12.sp,
                    color = UIFriendBarSubtitleColor
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(UIFriendBarTagBgBlue)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = model, fontSize = 11.sp, color = UIFriendBarTagTextBlue)
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    friendName: String,
    avatarPath: String?,
    userAvatarPath: String?
) {
    val isUser = message.role == ChatMessage.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            UIAvatar(name = friendName, avatarPath = avatarPath, size = 32.dp)
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp
                    )
                )
                .background(if (isUser) UIChatUserBubble else UIChatAssistantBubble)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.content,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = if (isUser) Color.White else UIFriendBarTitleColor
            )
        }
        if (isUser) {
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
private fun TypingIndicator(friendName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = "$friendName 正在输入…",
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            color = UIFriendBarItemMetaColor
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(UIChatErrorBg)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
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
private fun ChatInputBar(
    input: String,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val canSend = input.isNotBlank() && !isSending

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
