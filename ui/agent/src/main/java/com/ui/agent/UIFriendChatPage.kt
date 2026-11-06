package com.ui.agent

import com.ui.common.pageInsets
import com.ui.common.UIAvatarCropDialog
import com.ui.common.UIAvatar
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.ai.model.ChatMessage
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIBlobBlue
import com.ui.common.theme.UIBubbleUserBrush
import com.ui.common.theme.UIChatErrorBg
import com.ui.common.theme.UIChatSendDisabled
import com.ui.common.theme.UIComposerHeight
import com.ui.common.theme.UIFieldFill
import com.ui.common.theme.UIGlassBar
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIGradientBackground
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

    UIGradientBackground(modifier = Modifier.pageInsets()) {
        Column(
            modifier = Modifier.fillMaxSize()
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 56.dp),
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
                                UIAvatar(
                                    name = friendName,
                                    avatarPath = avatarPath,
                                    size = 56.dp,
                                    shape = CircleShape
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "和「$friendName」打个招呼吧",
                                textAlign = TextAlign.Center,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UIFriendBarTitleColor
                            )
                            if (role.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = role,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    color = UIFriendBarItemMetaColor
                                )
                            }
                        }
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
            // 顶栏不再铺白底：通栏直角白带贴边最突兀，直接让渐变透上来，
            // 顶栏与聊天区连成一片，只靠一条 hairline 交代内容起点。
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
            name = friendName,
            avatarPath = avatarPath,
            size = 40.dp,
            shape = CircleShape,
            modifier = Modifier.clickable { onAvatarClick() }
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friendName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = UIFriendBarTitleColor
            )
            if (role.isNotBlank()) {
                Text(
                    text = role,
                    fontSize = 12.sp,
                    color = UIFriendBarItemMetaColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(50))
                .background(UIFriendBarTagBgBlue)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = model,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = UIFriendBarTagTextBlue
            )
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
            UIAvatar(
                name = friendName,
                avatarPath = avatarPath,
                size = 34.dp,
                shape = CircleShape
            )
            Spacer(Modifier.width(8.dp))
        }
        // 形状只算一次：shadow / clip / border 共用同一个，避免某一处漏掉「尾巴角」
        val bubbleShape = RoundedCornerShape(
            topStart = UIRadiusBubble,
            topEnd = UIRadiusBubble,
            bottomEnd = if (isUser) UIRadiusBubbleTail else UIRadiusBubble,
            bottomStart = if (isUser) UIRadiusBubble else UIRadiusBubbleTail
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
                .then(
                    if (isUser) {
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
                color = if (isUser) Color.White else UIFriendBarTitleColor
            )
        }
        if (isUser) {
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
private fun TypingIndicator(friendName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            modifier = Modifier
                .uiControl(shape = RoundedCornerShape(50), color = UIGlassSurfaceStrong)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = UIFriendBarTagTextBlue
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$friendName 正在输入…",
                fontSize = 13.sp,
                color = UIFriendBarItemMetaColor
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(UIChatErrorBg)
            .border(0.5.dp, UIFriendBarRed.copy(alpha = 0.12f), RoundedCornerShape(UIRadiusControl))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = UIFriendBarRed
        )
        TextButton(onClick = onRetry) {
            Text(
                text = "重试",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = UIFriendBarRed
            )
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
        // 不再用整条通栏白底：改成浮在渐变上的圆角玻璃条，和页面里其他卡片用同一套语言
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
                // 外层已经是白卡，内层改成浅灰凹陷胶囊，输入区才和卡片分得开
                .clip(RoundedCornerShape(UIRadiusBubble))
                .background(UIFieldFill)
                .border(0.5.dp, UIGlassStroke, RoundedCornerShape(UIRadiusBubble))
                // 与发送按钮共用基准高度：单行时严格等高
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
