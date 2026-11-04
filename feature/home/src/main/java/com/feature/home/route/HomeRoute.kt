package com.feature.home.route

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feature.agent.contract.AgentScreen
import com.feature.agent.route.AgentRoute
import com.feature.room.contract.RoomScreen
import com.feature.room.route.RoomRoute
import com.feature.home.contract.HomeScreen
import com.feature.agent.model.Friend
import com.feature.home.vm.HomeViewModel
import com.ui.common.UIAvatarCropDialog
import com.ui.home.UIFriendPage
import com.ui.agent.data.UIFriendListData
import com.ui.room.data.UIRoomData
import com.ui.common.data.getRandomColor

@Composable
fun HomeRoute(viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var userCropUri by remember { mutableStateOf<Uri?>(null) }
    val userAvatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) userCropUri = uri
    }
    val pickUserAvatar: () -> Unit = {
        userAvatarPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val pendingUserCrop = userCropUri
    if (pendingUserCrop != null) {
        UIAvatarCropDialog(
            uri = pendingUserCrop,
            onCancel = { userCropUri = null },
            onConfirm = { bytes ->
                userCropUri = null
                viewModel.setUserAvatar(bytes)
            }
        )
    }

    BackHandler(enabled = state.screen != HomeScreen.List) {
        viewModel.back()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state.screen) {
            HomeScreen.List -> UIFriendPage(
                list = state.visibleFriends.map { it.toUiModel() },
                createdCount = state.friends.size,
                messageCount = state.messageCount,
                activeCount = state.activeCount,
                mostActive = state.mostActive,
                rooms = state.visibleRooms.map {
                    UIRoomData(
                        id = it.id,
                        name = it.name,
                        memberCount = it.memberIds.size,
                        pinned = it.pinned,
                        avatarPath = it.avatarPath
                    )
                },
                userAvatarPath = state.userAvatarPath,
                searchText = state.search,
                onSearchTextChange = viewModel::updateSearch,
                onCreateClick = viewModel::openCreate,
                onFriendClick = { viewModel.openChat(it.id) },
                onCreateRoom = viewModel::openRoomCreate,
                onRoomClick = { viewModel.openRoom(it.id) },
                onDeleteFriend = { viewModel.deleteFriend(it) },
                onDeleteRoom = { viewModel.deleteRoom(it) },
                onTogglePin = { viewModel.toggleRoomPin(it) },
                onEditFriend = { viewModel.startEditFriend(it) },
                onPickUserAvatar = pickUserAvatar
            )

            HomeScreen.Create -> AgentRoute(
                initial = AgentScreen.Create,
                onExit = {
                    viewModel.back()
                    viewModel.refresh()
                }
            )

            is HomeScreen.Chat -> AgentRoute(
                initial = AgentScreen.Chat((state.screen as HomeScreen.Chat).friendId),
                onExit = {
                    viewModel.back()
                    viewModel.refresh()
                }
            )

            is HomeScreen.EditFriend -> AgentRoute(
                initial = AgentScreen.Edit((state.screen as HomeScreen.EditFriend).friendId),
                onExit = {
                    viewModel.back()
                    viewModel.refresh()
                }
            )

            HomeScreen.RoomCreate -> RoomRoute(
                initial = RoomScreen.Create,
                onExit = {
                    viewModel.back()
                    viewModel.refresh()
                }
            )

            is HomeScreen.RoomChat -> RoomRoute(
                initial = RoomScreen.Chat((state.screen as HomeScreen.RoomChat).roomId),
                onExit = {
                    viewModel.back()
                    viewModel.refresh()
                }
            )
        }
    }
}

private fun Friend.toUiModel(): UIFriendListData = UIFriendListData(    id = id,
    name = name,
    modelTag = model,
    role = role.ifBlank { "AI 好友" },
    source = company.ifBlank { brand },
    usageCount = if (messages.isEmpty()) "尚未开始对话" else "${messages.size} 条消息",
    avatarBgColor = getRandomColor(name),
    avatarTextColor = Color(0xFF374151),
    isOnline = true,
    avatarPath = avatarPath
)
