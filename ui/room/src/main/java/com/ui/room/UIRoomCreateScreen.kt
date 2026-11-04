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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.room.data.UIRoomMember
import com.ui.common.theme.UIFriendBarCreateButtonBg
import com.ui.common.theme.UIFriendBarCreateButtonText
import com.ui.common.theme.UIFriendBarGray
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarSubtitleColor
import com.ui.common.theme.UIFriendBarTitleColor

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UIFriendBarGray)
            .pageInsetsTop()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = UIFriendBarTitleColor
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "新建聊天室",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = UIFriendBarTitleColor
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
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("聊天室名称（可选）") },
                placeholder = { Text("例如：产品评审团") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UIFriendBarCreateButtonText,
                    focusedLabelColor = UIFriendBarCreateButtonText,
                    cursorColor = UIFriendBarCreateButtonText
                )
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
                    color = UIFriendBarSubtitleColor
                )
                roomTemplates.forEach { template ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(UIFriendBarCreateButtonBg)
                            .clickable {
                                onPromptChange(template.prompt)
                                if (name.isBlank()) onNameChange(template.title)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = template.title,
                            fontSize = 12.sp,
                            color = UIFriendBarCreateButtonText
                        )
                    }
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("房间设定（可选）") },
                placeholder = { Text("例如：你们是评审团，各自给结论，不要互相重复") },
                minLines = 3,
                maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UIFriendBarCreateButtonText,
                    focusedLabelColor = UIFriendBarCreateButtonText,
                    cursorColor = UIFriendBarCreateButtonText
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "选择好友（2–$maxMembers 位）",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = UIFriendBarTitleColor
                )
                Text(
                    text = "已选 ${selectedIds.size} 位；同一条消息会发给每个人，默认各自独立回答",
                    fontSize = 12.sp,
                    color = UIFriendBarItemSubtitleColor
                )
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索昵称 / 角色 / 模型") },
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

            visibleFriends.forEach { friend ->
                val checked = friend.id in selectedIds
                val canToggle = checked || selectedIds.size < maxMembers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canToggle) { onToggleFriend(friend.id) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        enabled = canToggle,
                        onCheckedChange = { onToggleFriend(friend.id) }
                    )
                    Spacer(Modifier.width(4.dp))
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
                                color = UIFriendBarItemSubtitleColor
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .pageInsetsBottom()
                .padding(16.dp)
        ) {
            Button(
                onClick = onCreate,
                enabled = canCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UIFriendBarCreateButtonText
                )
            ) {
                Text(
                    text = if (canCreate) "创建并进入" else "至少选择 2 位好友",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
