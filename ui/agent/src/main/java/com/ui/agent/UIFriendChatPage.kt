package com.ui.agent

import com.ui.common.pageInsets
import com.ui.common.UIAvatar
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
import com.core.ai.model.TokenUsage
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

/**
 * 聊天顶栏里模型标签的宽度上限。
 *
 * 顶栏比列表项更挤：返回键 + 头像 + 名字 + 角色 + 标签挤在同一行，
 * 而标签是「补充信息」，名字才是主体。120dp 够放
 * `DeepSeek-V3.1-Terminus` 这种真实 id 的主要部分，再多就把角色名挤没了。
 */
private val CHAT_TAG_MAX_WIDTH = 120.dp

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
    /**
     * 正在流式接收的回复。它不在 [messages] 里——还没落库的临时消息不该混进历史，
     * 否则「重试」和上下文裁剪都会把它当成一次完整回答。
     */
    streaming: ChatMessage?,
    error: String?,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAvatarClick: () -> Unit
) {
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
                // 顶栏头像只负责「打开信息页」，相册留在信息页那一层触发
                onAvatarClick = onAvatarClick
            )

            val listState = rememberLazyListState()
            // 流式期间没有「新消息」，只有同一条在变长：用可见条目数算目标位置会停在原地，
            // 所以下面还要跟着 streaming 的长度滚
            val streamingLength = streaming?.content?.length ?: 0
            val totalItems = messages.size +
                (if (streaming != null || isSending) 1 else 0) +
                (if (error != null) 1 else 0)
            var didInitialScroll by remember { mutableStateOf(false) }
            var listHeight by remember { mutableIntStateOf(0) }

            LaunchedEffect(messages.size, isSending, error, streamingLength) {
                if (totalItems <= 0) return@LaunchedEffect
                val target = totalItems - 1
                when {
                    !didInitialScroll -> {
                        listState.scrollToItem(target)
                        didInitialScroll = true
                    }
                    // 流式期间每 60ms 就来一次。这里必须瞬时跟随：
                    // 用动画的话每次更新都会把上一段动画打断重来，看起来像卡住不动
                    streamingLength > 0 -> listState.scrollToItem(target)
                    else -> listState.animateScrollToItem(target)
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
                if (streaming != null) {
                    item {
                        MessageBubble(
                            message = streaming,
                            friendName = friendName,
                            avatarPath = avatarPath,
                            userAvatarPath = userAvatarPath,
                            streaming = true
                        )
                    }
                } else if (isSending) {
                    // 还没有第一个字：这时候只能显示「正在输入」。
                    // 一旦开始吐字就换成真正的气泡，两者不能同时出现，
                    // 否则用户会以为对方派了两个人回话
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
                onSend = onSend,
                onStop = onStop
            )
        }
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
            .uiHairlineBottom()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .uiControl(shape = CircleShape, color = UIGlassSurfaceStrong)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = UIFriendBarTitleColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        UIAvatar(
            name = friendName,
            avatarPath = avatarPath,
            size = 40.dp,
            shape = CircleShape,
            modifier = Modifier.clickable { onAvatarClick() }
        )
        Spacer(Modifier.width(10.dp))
        // 名字 + 角色。weight 表示「标签量完之后剩下的宽度都给你」，
        // 标签自己有上限，所以这里不会被挤到 0；再加上 maxLines=1 + 省略号，
        // 长名字是收尾打点，不是被拉变形。
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friendName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = UIFriendBarTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
        // 模型标签：和列表一样只取最后一段，并限宽。
        // 顶栏比列表更吃紧（头像 + 返回 + 名字 + 角色），
        // 聚合平台的 id 原样摆进来会把角色名挤没。
        val tag = shortModelTag(model)
        if (tag.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .widthIn(max = CHAT_TAG_MAX_WIDTH)
                    .clip(RoundedCornerShape(50))
                    .background(UIFriendBarTagBgBlue)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = tag,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = UIFriendBarTagTextBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    friendName: String,
    avatarPath: String?,
    userAvatarPath: String?,
    /** 这条消息还在流式接收：末尾要跟一个光标，正文不完整是正常的。 */
    streaming: Boolean = false
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
            Column {
                // 思考过程放在正文上方，折叠起来。展开时是灰字，一眼能看出
                // 「这是他想的」而不是「他对我说的」
                message.reasoning?.takeIf { it.isNotBlank() }?.let { reasoning ->
                    ReasoningBlock(
                        reasoning = reasoning,
                        // 只想了还没开始说的时候默认展开：这时候用户除了思考
                        // 没有别的可看，收起来就只剩一个空气泡
                        expandedByDefault = streaming && message.content.isBlank(),
                        thinking = streaming && message.content.isBlank(),
                        usage = message.usage,
                    )
                    if (message.content.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (message.content.isNotBlank() || !streaming) {
                    Text(
                        text = if (streaming) message.content + "▌" else message.content,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = if (isUser) Color.White else UIFriendBarTitleColor
                    )
                } else if (message.reasoning.isNullOrBlank()) {
                    // 既没正文也还没收到思考内容：至少让气泡里有点东西在动
                    Text(
                        text = "…",
                        fontSize = 15.sp,
                        color = UIFriendBarItemMetaColor
                    )
                }
            }
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
private fun ReasoningBlock(
    reasoning: String,
    expandedByDefault: Boolean,
    thinking: Boolean,
    usage: TokenUsage? = null,
) {
    // 用 remember 而不是 rememberSaveable：这是纯展示的临时展开状态。
    // 存下来的话，下次进来说过的每一句都会带着一块展开的思考过程，历史会长得没法看。
    var expanded by remember { mutableStateOf(expandedByDefault) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(UIFieldFill)
            .border(0.6.dp, UIGlassStroke, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (thinking) "正在思考…" else "思考过程",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = UIFriendBarItemMetaColor
            )
            Spacer(Modifier.weight(1f))
            if (usage != null && usage.total > 0) {
                Text(
                    text = TokenUsage.format(usage),
                    fontSize = 10.sp,
                    color = UIFriendBarTagTextBlue
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = if (expanded) "收起" else "展开",
                fontSize = 10.sp,
                color = UIFriendBarTagTextBlue
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = reasoning,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = UIFriendBarItemMetaColor
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
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val canSend = input.isNotBlank() && !isSending
    // 生成中，发送键变成停止键。流式回复可能很长，没有这个按钮用户只能干等
    val showStop = isSending
    val buttonEnabled = showStop || canSend

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
                    elevation = if (buttonEnabled) 8.dp else 0.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = UIBlobBlue,
                    spotColor = UIBlobBlue
                )
                .clip(CircleShape)
                .then(
                    when {
                        showStop -> Modifier.background(UIFriendBarRed)
                        canSend -> Modifier.background(UIActionBrush)
                        else -> Modifier.background(UIChatSendDisabled.copy(alpha = 0.6f))
                    }
                )
                .clickable(enabled = buttonEnabled) { if (showStop) onStop() else onSend() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (showStop) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                contentDescription = if (showStop) "停止生成" else "发送",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
