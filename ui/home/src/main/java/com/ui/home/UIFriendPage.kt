@file:OptIn(ExperimentalFoundationApi::class)

package com.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.agent.UIFriendBar
import com.ui.agent.UIFriendCreate
import com.ui.agent.UIFriendCrewList
import com.ui.agent.UIFriendCrewStatusCard
import com.ui.agent.UIFriendSearchBar
import com.ui.agent.data.UIFriendListData
import com.ui.common.UIAvatar
import com.ui.common.UIGroupAvatar
import com.ui.common.pageInsetsBottom
import com.ui.common.pageInsetsTop
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIActionSheet
import com.ui.common.theme.UIActionSheetItem
import com.ui.common.theme.UIAtelierBrass
import com.ui.common.theme.UIAtelierBrassBg
import com.ui.common.theme.UIAtelierJade
import com.ui.common.theme.UIAtelierJadeBg
import com.ui.common.theme.UIAtelierSand
import com.ui.common.theme.UIBlobBlue
import com.ui.common.theme.UIDialogButtons
import com.ui.common.theme.UIFriendBarCreateButtonBg
import com.ui.common.theme.UIFriendBarCreateButtonText
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarSubtitleColor
import com.ui.common.theme.UIFriendBarTitleColor2
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIIosDialog
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UIWerewolfHeroBrush
import com.ui.common.theme.uiCard
import com.ui.room.data.UIRoomData

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
    onPickUserAvatar: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenWerewolf: () -> Unit = {},
    isProbing: Boolean = false,
    probeCompleted: Int = 0,
    probeTotal: Int = 0,
    probedCount: Int = 0,
    aliveCount: Int = 0,
    failedCount: Int = 0,
    onProbeAll: () -> Unit = {},
    onProbeFailed: () -> Unit = {},
    onProbeFriend: (String) -> Unit = {},
    onCancelProbe: () -> Unit = {},
) {
    var pendingFriendAction by remember { mutableStateOf<UIFriendListData?>(null) }
    var pendingRoomAction by remember { mutableStateOf<UIRoomData?>(null) }
    var pendingRoomDelete by remember { mutableStateOf<UIRoomData?>(null) }

    UIGradientBackground(modifier = Modifier.pageInsetsTop()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .pageInsetsBottom()
        ) {
            UIFriendBar(
                isNotification = false,
                userAvatarPath = userAvatarPath,
                onAvatarClick = onPickUserAvatar,
                onSettingsClick = onOpenSettings
            )

            // 搜索框置顶：打开首页第一眼就能搜好友 / 聊天室 / 模型
            UIFriendSearchBar(
                value = searchText,
                onValueChange = onSearchTextChange,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // 搜索为空时展示 Bento 指令台与脉搏条；正在搜索时自动收起，直达搜索结果
            if (searchText.isBlank()) {
                Spacer(Modifier.height(14.dp))
                BentoCommandDeck(
                    createdCount = createdCount,
                    roomCount = rooms.size,
                    onOpenWerewolf = onOpenWerewolf,
                    onCreateFriend = onCreateClick,
                    onCreateRoom = onCreateRoom
                )
                Spacer(Modifier.height(10.dp))
                UIFriendCrewStatusCard(
                    onlineCount = aliveCount,
                    title = createdCount,
                    activeCount = activeCount,
                    messageCount = messageCount,
                    mostActive = mostActive,
                    probedCount = probedCount,
                    aliveCount = aliveCount,
                    failedCount = failedCount,
                )
            }

            Spacer(Modifier.height(18.dp))
            RoomSection(
                rooms = rooms,
                hasSearch = searchText.isNotBlank(),
                onCreateRoom = onCreateRoom,
                onRoomClick = onRoomClick,
                onRoomLongClick = { pendingRoomAction = it }
            )

            Spacer(Modifier.height(18.dp))
            UIFriendCreate(
                createdCount = createdCount,
                searchText = searchText,
                onSearchTextChange = onSearchTextChange,
                onCreateClick = onCreateClick,
                showSearchBar = false,
                isProbing = isProbing,
                probeCompleted = probeCompleted,
                probeTotal = probeTotal,
                probedCount = probedCount,
                aliveCount = aliveCount,
                failedCount = failedCount,
                onProbeClick = onProbeAll,
                onCancelProbe = onCancelProbe,
                onRetryFailedClick = onProbeFailed,
            )
            Spacer(Modifier.height(4.dp))
            if (list.isEmpty()) {
                EmptyFriends(hasSearch = searchText.isNotBlank(), onCreateClick = onCreateClick)
            } else {
                UIFriendCrewList(
                    data = list,
                    onItemClick = onFriendClick,
                    onItemLongClick = { pendingFriendAction = it },
                    isProbing = isProbing,
                    onProbeFriend = { onProbeFriend(it.id) },
                    onProbeAll = onProbeAll,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "点右侧状态标签可单独探活 · 长按好友可探活、编辑或删除",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 11.5.sp,
                color = UIFriendBarItemMetaColor
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingFriendAction?.let { friend ->
        FriendActionsDialog(
            friendName = friend.name,
            onProbe = {
                pendingFriendAction = null
                onProbeFriend(friend.id)
            },
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
        UIIosDialog(
            title = "删除聊天室",
            subtitle = "会删除「${room.name}」以及里面的全部消息，该操作不可恢复。",
            onDismiss = { pendingRoomDelete = null },
            actions = {
                UIDialogButtons(
                    confirmText = "删除",
                    confirmColor = UIFriendBarRed,
                    onConfirm = {
                        pendingRoomDelete = null
                        onDeleteRoom(room.id)
                    },
                    onCancel = { pendingRoomDelete = null }
                )
            }
        )
    }
}

/**
 * 首页非对称双栏 Bento 指令台：
 * 左侧 58% 烛影曜石「狼人杀剧场」主卡 + 右侧 42% 叠放「新建好友 / 新建群聊」快捷砖。
 */
@Composable
private fun BentoCommandDeck(
    createdCount: Int,
    roomCount: Int,
    onOpenWerewolf: () -> Unit,
    onCreateFriend: () -> Unit,
    onCreateRoom: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WerewolfBentoCard(
            onClick = onOpenWerewolf,
            modifier = Modifier
                .weight(1.25f)
                .fillMaxHeight()
        )
        Column(
            modifier = Modifier.weight(0.95f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickBentoTile(
                icon = Icons.Default.Add,
                iconBg = UIFriendBarCreateButtonBg,
                iconTint = UIFriendBarCreateButtonText,
                title = "新建好友",
                subtitle = "已有 $createdCount 位角色",
                onClick = onCreateFriend,
                modifier = Modifier.fillMaxWidth()
            )
            QuickBentoTile(
                icon = Icons.Default.Group,
                iconBg = UIAtelierJadeBg,
                iconTint = UIAtelierJade,
                title = "新建群聊",
                subtitle = if (roomCount > 0) "$roomCount 间讨论室" else "多人同席畅聊",
                onClick = onCreateRoom,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WerewolfBentoCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(UIRadiusCard)
    Box(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0x382A1614),
                spotColor = Color(0x382A1614)
            )
            .clip(shape)
            .background(UIWerewolfHeroBrush)
            .border(1.dp, Color(0x4DE5B869), shape)
            .clickable { onClick() }
    ) {
        // 右上角绘一弯古铜金月牙与暗红烛辉，与牌桌夜幕美学一脉相承
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(110.dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x38E5B869), Color(0x18C84B2A), Color.Transparent),
                    center = Offset(size.width * 0.72f, size.height * 0.28f),
                    radius = size.width * 0.65f
                ),
                radius = size.width * 0.65f,
                center = Offset(size.width * 0.72f, size.height * 0.28f)
            )
            val moonCenter = Offset(size.width * 0.74f, size.height * 0.28f)
            val r = 13.dp.toPx()
            val disc = Path().apply { addOval(Rect(moonCenter, r)) }
            val bite = Path().apply {
                addOval(Rect(Offset(moonCenter.x + r * 0.52f, moonCenter.y - r * 0.36f), r * 0.92f))
            }
            disc.op(disc, bite, PathOperation.Difference)
            drawPath(disc, color = Color(0xCCFFECC6))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 15.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x28E5B869))
                        .border(0.6.dp, Color(0x4DE5B869), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE5B869))
                    )
                    Text(
                        text = "6–12 人局",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        color = Color(0xFFF6D99C)
                    )
                }
                Text(
                    text = "狼人杀剧场",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFFBF4)
                )
                Text(
                    text = "AI 围桌推理与博弈\n支持观战或亲身入局",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = Color(0xFFDCD0C4)
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFFFFDF9))
                    .padding(start = 11.dp, end = 7.dp, top = 5.5.dp, bottom = 5.5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "进入剧场",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A1614)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFFC84B2A),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickBentoTile(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .uiCard(shape = RoundedCornerShape(18.dp), elevation = 2.dp)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = UIFriendBarTitleColor2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = UIFriendBarItemMetaColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "聊天室",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = UIFriendBarTitleColor2
                )
                if (rooms.isNotEmpty()) {
                    Text(
                        text = "${rooms.size}",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(UIAtelierSand)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIFriendBarItemMetaColor
                    )
                }
            }
            Text(
                text = "+ 新建",
                modifier = Modifier
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .background(UIFriendBarCreateButtonBg)
                    .clickable { onCreateRoom() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = UIFriendBarCreateButtonText
            )
        }
        if (rooms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp)
                    .clickable(enabled = !hasSearch) { onCreateRoom() }
                    .padding(horizontal = 15.dp, vertical = 14.dp)
            ) {
                Text(
                    text = if (hasSearch) {
                        "没有匹配的聊天室"
                    } else {
                        "拉多位 AI 好友进同一间房，支持独立作答、讨论接话与共享纪要"
                    },
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = UIFriendBarItemMetaColor
                )
            }
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rooms.forEach { room ->
                    Row(
                        modifier = Modifier
                            .widthIn(min = 166.dp, max = 224.dp)
                            .uiCard(
                                shape = RoundedCornerShape(16.dp),
                                color = UIGlassSurfaceStrong,
                                borderColor = if (room.pinned) UIAtelierBrass.copy(alpha = 0.45f) else UIGlassStroke,
                                elevation = 2.dp
                            )
                            .combinedClickable(
                                onClick = { onRoomClick(room) },
                                onLongClick = { onRoomLongClick(room) }
                            )
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (room.avatarPath != null) {
                            UIAvatar(
                                name = room.name,
                                avatarPath = room.avatarPath,
                                size = 40.dp,
                                shape = RoundedCornerShape(12.dp)
                            )
                        } else {
                            UIGroupAvatar(
                                members = room.memberAvatars,
                                size = 40.dp,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = room.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = UIFriendBarTitleColor2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (room.pinned) {
                                    Text(
                                        text = "置顶",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = UIAtelierBrass,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(UIAtelierBrassBg)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "${room.memberCount} 位成员",
                                fontSize = 11.sp,
                                color = UIFriendBarItemMetaColor,
                                maxLines = 1
                            )
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
    onProbe: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    UIActionSheet(
        title = friendName,
        onDismiss = onDismiss,
        items = listOf(
            UIActionSheetItem(label = "探活检测", onClick = onProbe),
            UIActionSheetItem(label = "编辑资料", onClick = onEdit),
            UIActionSheetItem(label = "删除好友", destructive = true, onClick = onDelete)
        )
    )
}

@Composable
private fun RoomActionsDialog(
    roomName: String,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    UIActionSheet(
        title = roomName,
        onDismiss = onDismiss,
        items = listOf(
            UIActionSheetItem(
                label = if (pinned) "取消置顶" else "置顶聊天室",
                onClick = onTogglePin
            ),
            UIActionSheetItem(label = "删除聊天室", destructive = true, onClick = onDelete)
        )
    )
}

@Composable
private fun EmptyFriends(hasSearch: Boolean, onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 2.dp)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (hasSearch) "没有找到匹配的智能体" else "工坊里还没有 AI 好友",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = UIFriendBarTitleColor2
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasSearch) {
                "换个关键词试试，或清空顶部搜索栏"
            } else {
                "为它选一个模型、写一段人设，即可随时开启 1 对 1 对话、多人群聊或狼人杀对局"
            },
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            color = UIFriendBarSubtitleColor,
            textAlign = TextAlign.Center
        )
        if (!hasSearch) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "+ 创建第一位 AI 好友",
                modifier = Modifier
                    .shadow(6.dp, RoundedCornerShape(UIRadiusControl), clip = false, ambientColor = UIBlobBlue, spotColor = UIBlobBlue)
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .background(UIActionBrush)
                    .clickable { onCreateClick() }
                    .padding(horizontal = 22.dp, vertical = 11.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
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
                "OpenAI",
                "本月 3,841 次",
                Color(0xFFEED9B7),
                Color(0xFF4B5563)
            ),
            UIFriendListData(
                "Mir",
                "Qwen-Max",
                "创意灵感助手",
                "阿里云",
                "本月 2,970 次",
                Color(0xFFC8E0D4),
                Color(0xFF1E40AF)
            )
        )
    }
    UIFriendPage(agentList)
}
