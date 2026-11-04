package com.ui.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.friend.data.UIRoomMember
import com.ui.friend.theme.UIFriendBarCreateButtonText
import com.ui.friend.theme.UIFriendBarGray
import com.ui.friend.theme.UIFriendBarItemSubtitleColor
import com.ui.friend.theme.UIFriendBarSubtitleColor
import com.ui.friend.theme.UIFriendBarTitleColor

@Composable
fun UIRoomCreateScreen(
    name: String,
    friends: List<UIRoomMember>,
    selectedIds: Set<String>,
    onNameChange: (String) -> Unit,
    onToggleFriend: (String) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit
) {
    val canCreate = selectedIds.size >= 2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UIFriendBarGray)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
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

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "选择好友（至少 2 位）",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = UIFriendBarTitleColor
                )
                Text(
                    text = "同一条消息会发给每个人，但每个人只看得到你的消息和自己的回答",
                    fontSize = 12.sp,
                    color = UIFriendBarItemSubtitleColor
                )
            }

            if (friends.isEmpty()) {
                Text(
                    text = "还没有 AI 好友，先创建一个再来建聊天室",
                    fontSize = 13.sp,
                    color = UIFriendBarSubtitleColor
                )
            }

            friends.forEach { friend ->
                val checked = friend.id in selectedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleFriend(friend.id) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onToggleFriend(friend.id) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = friend.name,
                        fontSize = 14.sp,
                        color = UIFriendBarTitleColor
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                )
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
