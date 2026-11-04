package com.ui.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.friend.data.UIFriendListData
import com.ui.friend.data.UIRoomData
import com.ui.friend.theme.UIFriendBarCreateButtonBg
import com.ui.friend.theme.UIFriendBarCreateButtonText
import com.ui.friend.theme.UIFriendBarGray
import com.ui.friend.theme.UIFriendBarSubtitleColor
import com.ui.friend.theme.UIFriendBarTitleColor2

@Composable
fun UIFriendPage(
    list: List<UIFriendListData>,
    createdCount: Int = list.size,
    messageCount: Int = 0,
    activeCount: Int = 0,
    mostActive: String = "-",
    rooms: List<UIRoomData> = emptyList(),
    userAvatarPath: String? = null,
    searchText: String = "",
    onSearchTextChange: (String) -> Unit = {},
    onCreateClick: () -> Unit = {},
    onFriendClick: (UIFriendListData) -> Unit = {},
    onCreateRoom: () -> Unit = {},
    onRoomClick: (UIRoomData) -> Unit = {},
    onPickUserAvatar: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = UIFriendBarGray)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
            .verticalScroll(rememberScrollState())
            // 底部安全区作为滚动内容的一部分，键盘弹出时只增加一次底部空间
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
            )
    ) {
        UIFriendBar(
            isNotification = true,
            userAvatarPath = userAvatarPath,
            onAvatarClick = onPickUserAvatar
        )
        Spacer(Modifier.height(16.dp))
        UIFriendCrewStatusCard(
            onlineCount = createdCount,
            title = createdCount,
            activeCount = activeCount,
            messageCount = messageCount,
            mostActive = mostActive
        )
        Spacer(Modifier.height(16.dp))
        RoomSection(
            rooms = rooms,
            onCreateRoom = onCreateRoom,
            onRoomClick = onRoomClick
        )
        Spacer(Modifier.height(16.dp))
        UIFriendCreate(
            createdCount = createdCount,
            searchText = searchText,
            onSearchTextChange = onSearchTextChange,
            onCreateClick = onCreateClick
        )
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) {
            EmptyFriends(hasSearch = searchText.isNotBlank(), onCreateClick = onCreateClick)
        } else {
            UIFriendCrewList(list, onItemClick = onFriendClick)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RoomSection(
    rooms: List<UIRoomData>,
    onCreateRoom: () -> Unit,
    onRoomClick: (UIRoomData) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "聊天室",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = UIFriendBarTitleColor2
            )
            Text(
                text = "+ 新建",
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(UIFriendBarCreateButtonBg)
                    .clickable { onCreateRoom() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = UIFriendBarCreateButtonText
            )
        }
        if (rooms.isEmpty()) {
            Text(
                text = "把多个好友拉进同一个房间，一次提问、各自回答",
                fontSize = 13.sp,
                color = UIFriendBarSubtitleColor
            )
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rooms.forEach { room ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .clickable { onRoomClick(room) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = room.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = UIFriendBarTitleColor2
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${room.memberCount} 位好友",
                            fontSize = 11.sp,
                            color = UIFriendBarSubtitleColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFriends(hasSearch: Boolean, onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (hasSearch) "没有找到匹配的智能体" else "还没有 AI 好友",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = UIFriendBarTitleColor2
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasSearch) {
                "换个关键词试试"
            } else {
                "创建一个好友，给它一个名字、一个角色，选一个模型，然后开始聊天"
            },
            fontSize = 13.sp,
            color = UIFriendBarSubtitleColor,
            textAlign = TextAlign.Center
        )
        if (!hasSearch) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "+ 创建 AI 好友",
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(UIFriendBarCreateButtonBg)
                    .clickable { onCreateClick() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = UIFriendBarCreateButtonText
            )
        }
    }
}

@Preview
@Composable
private fun PUIFriendPage() {
    val agentList = remember {
        listOf(
            UIFriendListData(
                "Echo",
                "GPT-4o",
                "产品策略顾问",
                "墨刀",
                "本月 3,841 次",
                Color(0xFFF3F4F6),
                Color(0xFF4B5563)
            ),
            UIFriendListData(
                "Mir",
                "Qwen-Max",
                "创意灵感助手",
                "阿里云",
                "本月 2,970 次",
                Color(0xFFDBEAFE),
                Color(0xFF1E40AF)
            ),
            UIFriendListData(
                "Nova",
                "GPT-4o",
                "用户研究分析师",
                "OpenAI",
                "本月 2,412 次",
                Color(0xFFD1FAE5),
                Color(0xFF065F46)
            ),
            UIFriendListData(
                "Kora",
                "Gemini 1.5",
                "品牌文案专家",
                "Google",
                "本月 1,901 次",
                Color(0xFFECFDF5),
                Color(0xFF047857)
            ),
            UIFriendListData(
                "Atlas",
                "Claude 3.5",
                "项目执行搭档",
                "Anthropic",
                "本月 1,286 次",
                Color(0xFFFEF3C7),
                Color(0xFF92400E)
            ),
            UIFriendListData(
                "Lumen",
                "Doubao",
                "内容校对助手",
                "字节跳动",
                "尚未启用",
                Color(0xFFE5E7EB),
                Color(0xFF374151)
            )
        )
    }
    UIFriendPage(agentList)
}
