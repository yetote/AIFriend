@file:OptIn(ExperimentalFoundationApi::class)

package com.ui.home
import com.ui.agent.UIFriendCrewList
import com.ui.agent.UIFriendCrewStatusCard
import com.ui.agent.UIFriendCreate
import com.ui.agent.UIFriendBar
import com.ui.common.pageInsetsBottom
import com.ui.common.pageInsetsTop
import com.ui.common.UIAvatar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ui.agent.data.UIFriendListData
import com.ui.room.data.UIRoomData
import com.ui.common.theme.UIFriendBarCreateButtonBg
import com.ui.common.theme.UIFriendBarCreateButtonText
import com.ui.common.theme.UIFriendBarGray
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarSubtitleColor
import com.ui.common.theme.UIFriendBarTitleColor2

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
    onEditFriend: (String) -> Unit = {},
    onDeleteFriend: (String) -> Unit = {},
    onDeleteRoom: (String) -> Unit = {},
    onTogglePin: (String) -> Unit = {},
    onPickUserAvatar: () -> Unit = {}
) {
    var pendingFriendAction by remember { mutableStateOf<UIFriendListData?>(null) }
    var pendingRoomAction by remember { mutableStateOf<UIRoomData?>(null) }
    var pendingRoomDelete by remember { mutableStateOf<UIRoomData?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = UIFriendBarGray)
            .pageInsetsTop()
            .verticalScroll(rememberScrollState())
            // 底部安全区作为滚动内容的一部分，键盘弹出时只增加一次底部空间
            .pageInsetsBottom()
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
            hasSearch = searchText.isNotBlank(),
            onCreateRoom = onCreateRoom,
            onRoomClick = onRoomClick,
            onRoomLongClick = { pendingRoomAction = it }
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
            UIFriendCrewList(
                data = list,
                onItemClick = onFriendClick,
                onItemLongClick = { pendingFriendAction = it }
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "长按好友可编辑或删除，长按聊天室可删除",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = UIFriendBarSubtitleColor
        )
        Spacer(Modifier.height(24.dp))
    }

    pendingFriendAction?.let { friend ->
        FriendActionsDialog(
            friendName = friend.name,
            onEdit = {
                pendingFriendAction = null
                onEditFriend(friend.id)
            },
            onDelete = {
                pendingFriendAction = null
                onDeleteFriend(friend.id)
            },
            onDismiss = { pendingFriendAction = null }
        )
    }

    pendingRoomAction?.let { room ->
        RoomActionsDialog(
            roomName = room.name,
            pinned = room.pinned,
            onTogglePin = {
                pendingRoomAction = null
                onTogglePin(room.id)
            },
            onDelete = {
                pendingRoomAction = null
                pendingRoomDelete = room
            },
            onDismiss = { pendingRoomAction = null }
        )
    }

    pendingRoomDelete?.let { room ->
        AlertDialog(
            onDismissRequest = { pendingRoomDelete = null },
            title = { Text(text = "删除聊天室") },
            text = {
                Text(text = "会删除「${room.name}」以及里面的全部消息，该操作不可恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRoomDelete = null
                        onDeleteRoom(room.id)
                    }
                ) {
                    Text(text = "删除", color = UIFriendBarRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRoomDelete = null }) {
                    Text(text = "取消")
                }
            }
        )
    }
}

@Composable
private fun RoomSection(
    rooms: List<UIRoomData>,
    hasSearch: Boolean,
    onCreateRoom: () -> Unit,
    onRoomClick: (UIRoomData) -> Unit,
    onRoomLongClick: (UIRoomData) -> Unit
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
                text = if (hasSearch) {
                    "没有匹配的聊天室"
                } else {
                    "把多个好友拉进同一个房间，一次提问、各自回答"
                },
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
                            .combinedClickable(
                                onClick = { onRoomClick(room) },
                                onLongClick = { onRoomLongClick(room) }
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UIAvatar(
                                name = room.name,
                                avatarPath = room.avatarPath,
                                size = 28.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = room.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = UIFriendBarTitleColor2
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (room.pinned) {
                                        "置顶 · ${room.memberCount} 位好友"
                                    } else {
                                        "${room.memberCount} 位好友"
                                    },
                                    fontSize = 11.sp,
                                    color = UIFriendBarSubtitleColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendActionsDialog(
    friendName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = friendName,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = UIFriendBarTitleColor2
                )
                TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "编辑资料", color = UIFriendBarTitleColor2)
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "删除好友", color = UIFriendBarRed)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "取消", color = UIFriendBarSubtitleColor)
                }
            }
        }
    }
}

@Composable
private fun RoomActionsDialog(
    roomName: String,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = roomName,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = UIFriendBarTitleColor2
                )
                TextButton(onClick = onTogglePin, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (pinned) "取消置顶" else "置顶聊天室",
                        color = UIFriendBarTitleColor2
                    )
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "删除聊天室", color = UIFriendBarRed)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "取消", color = UIFriendBarSubtitleColor)
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
