package com.ui.room

import com.ui.common.pageInsetsBottom
import com.ui.common.pageInsetsTop
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.room.data.UIRoomMember
import com.ui.common.UIAvatar
import com.ui.common.theme.UIBlobBlue
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurface
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UIShadowSoft
import com.ui.common.theme.UITopBar
import com.ui.common.theme.uiCard
import com.ui.common.theme.uiControl
import com.ui.common.theme.UIFriendBarCreateButtonBg
import com.ui.common.theme.UIFriendBarCreateButtonText
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarSubtitleColor
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow

@Composable
fun UIRoomCreateScreen(
    name: String,
    prompt: String,
    friends: List<UIRoomMember>,
    selectedIds: Set<String>,
    maxMembers: Int,
    onNameChange: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onToggleFriend: (String) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit
) {
    val canCreate = selectedIds.size in 2..maxMembers
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

    UIGradientBackground(modifier = Modifier.pageInsetsTop()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            UITopBar(title = "新建聊天室", onBack = onCancel) {
                Text(
                    text = "已选 ${selectedIds.size}",
                    modifier = Modifier.padding(end = 12.dp),
                    fontSize = 13.sp,
                    color = UIFriendBarTagTextBlue
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LabeledField(
                    label = "聊天室名称（可选）",
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = "例如：产品评审团",
                    singleLine = true
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "模板",
                        fontSize = 12.sp,
                        color = UIFriendBarItemSubtitleColor
                    )
                    roomTemplates.forEach { template ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(UIGlassSurfaceStrong)
                                .border(
                                    0.5.dp,
                                    UIFriendBarCreateButtonText.copy(alpha = 0.18f),
                                    RoundedCornerShape(50)
                                )
                                .clickable {
                                    onPromptChange(template.prompt)
                                    if (name.isBlank()) onNameChange(template.title)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = template.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = UIFriendBarCreateButtonText
                            )
                        }
                    }
                }

                LabeledField(
                    label = "房间设定（可选）",
                    value = prompt,
                    onValueChange = onPromptChange,
                    placeholder = "例如：你们是评审团，各自给结论，不要互相重复",
                    minLines = 3,
                    maxLines = 6
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "选择好友（2–$maxMembers 位）",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UIFriendBarTitleColor
                    )
                    Text(
                        text = "已选 ${selectedIds.size} 位；同一条消息会发给每个人，默认各自独立回答",
                        fontSize = 12.sp,
                        color = UIFriendBarItemSubtitleColor
                    )
                }

                LabeledField(
                    label = "搜索昵称 / 角色 / 模型",
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "输入关键词过滤",
                    singleLine = true
                )

                if (friends.isEmpty()) {
                    Text(
                        text = "还没有 AI 好友，先创建一个再来建聊天室",
                        fontSize = 13.sp,
                        color = UIFriendBarSubtitleColor
                    )
                } else if (visibleFriends.isEmpty()) {
                    Text(
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
                            val canToggle = checked || selectedIds.size < maxMembers
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = canToggle) { onToggleFriend(friend.id) }
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
                                        color = UIFriendBarTitleColor
                                    )
                                    val meta = listOf(friend.role, friend.model)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                    if (meta.isNotBlank()) {
                                        Text(
                                            text = meta,
                                            fontSize = 12.sp,
                                            color = UIFriendBarItemSubtitleColor
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (checked) {
                                                Modifier.background(UIActionBrush)
                                            } else {
                                                Modifier.border(
                                                    1.5.dp,
                                                    UIGlassStroke,
                                                    CircleShape
                                                )
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
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 底部按钮栏不用通栏白条，改成浮起的圆角卡片
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
                            elevation = if (canCreate) 10.dp else 0.dp,
                            shape = RoundedCornerShape(UIRadiusControl),
                            clip = false,
                            ambientColor = UIBlobBlue,
                            spotColor = UIBlobBlue
                        )
                        .clip(RoundedCornerShape(UIRadiusControl))
                        .then(
                            if (canCreate) {
                                Modifier.background(UIActionBrush)
                            } else {
                                Modifier.background(UIGlassSurface)
                            }
                        )
                        .clickable(enabled = canCreate) { onCreate() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (canCreate) "创建并进入" else "至少选择 2 位好友",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canCreate) Color.White else UIFriendBarItemSubtitleColor
                    )
                }
            }
        }
    }
}

/** iOS 风格的胶囊输入框，避免 Material 描边输入框的视觉割裂。 */
@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = UIFriendBarItemSubtitleColor
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .uiControl(
                    shape = RoundedCornerShape(if (singleLine) 50.dp else UIRadiusControl),
                    color = UIGlassSurfaceStrong
                )
                .padding(horizontal = 14.dp, vertical = if (singleLine) 12.dp else 10.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 14.sp,
                    color = UIFriendBarItemSubtitleColor.copy(alpha = 0.7f)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 14.sp, color = UIFriendBarTitleColor),
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                cursorBrush = SolidColor(UIFriendBarCreateButtonText),
                keyboardOptions = KeyboardOptions(
                    imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
                )
            )
        }
    }
}

private data class RoomTemplate(val title: String, val prompt: String)

private val roomTemplates = listOf(
    RoomTemplate(
        title = "评审团",
        prompt = "你们是一个评审团。针对用户的问题，每人先给出独立结论，再给 2 条理由；不要重复其他人的观点。"
    ),
    RoomTemplate(
        title = "头脑风暴",
        prompt = "你们在开头脑风暴会。每个人提出 2–3 个不同的想法，鼓励发散，不要只附和别人。"
    ),
    RoomTemplate(
        title = "角色扮演",
        prompt = "你们在演一场即兴情景剧。保持各自人设，用对话推进剧情，每次回复不超过 100 字。"
    )
)
