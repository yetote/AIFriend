package com.ui.friend

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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.ai.model.ChatMessage
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
    onChangeAvatar: (Uri) -> Unit
) {
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onChangeAvatar(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UIFriendBarGray)
            // 只处理顶部和横向的系统栏，底部（导航栏 / 软键盘）交给输入栏自己处理，
            // 避免和窗口的 adjustResize 叠加成两倍高度。
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
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
        val itemCount = messages.size + (if (isSending) 1 else 0) + (if (error != null) 1 else 0)
        LaunchedEffect(itemCount) {
            if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
            )
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
