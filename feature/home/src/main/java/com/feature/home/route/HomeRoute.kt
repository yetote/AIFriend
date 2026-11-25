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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.ai.model.Friend
import com.core.ai.probe.ApiCheck
import com.core.ai.probe.ApiCheckState
import com.core.ai.probe.formatProbeElapsed
import com.feature.agent.contract.AgentScreen
import com.feature.agent.route.AgentRoute
import com.feature.home.contract.HomeScreen
import com.feature.home.vm.HomeViewModel
import com.feature.room.contract.RoomScreen
import com.feature.room.route.RoomRoute
import com.feature.werewolf.route.WerewolfRoute
import com.ui.agent.data.UIFriendListData
import com.ui.agent.data.UIFriendProbeState
import com.ui.common.UIAvatarCropDialog
import com.ui.common.UIGroupMember
import com.ui.common.data.getRandomColor
import com.ui.home.UIFriendPage
import com.ui.room.data.UIRoomData

@Composable
fun HomeRoute(viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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

    // 设置页有自己的两级返回逻辑（二级→首页→退出），这里让位给它，
    // 避免两个 BackHandler 都 enabled 时行为依赖注册顺序。
    BackHandler(enabled = state.screen != HomeScreen.List && state.screen != HomeScreen.Settings) {
        viewModel.back()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state.screen) {
            HomeScreen.List -> UIFriendPage(
                list = state.visibleFriends.map { friend ->
                    friend.toUiModel(state.apiChecks[friend.id])
                },
                createdCount = state.friends.size,
                messageCount = state.messageCount,
                activeCount = state.activeCount,
                mostActive = state.mostActive,
                rooms = state.visibleRooms.map { room ->
                    // 按成员 ID 查出好友，拿名字和头像拼群头像；
                    // 顺序跟着 memberIds 走，成员增删后拼图也跟着变。
                    val members = room.memberIds.mapNotNull { id ->
                        state.friends.firstOrNull { it.id == id }
                    }.map { UIGroupMember(name = it.name, avatarPath = it.avatarPath) }
                    UIRoomData(
                        id = room.id,
                        name = room.name,
                        memberCount = room.memberIds.size,
                        pinned = room.pinned,
                        avatarPath = room.avatarPath,
                        memberAvatars = members
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
                onPickUserAvatar = pickUserAvatar,
                onOpenSettings = viewModel::openSettings,
                onOpenWerewolf = viewModel::openWerewolf,
                isProbing = state.isProbing,
                probeCompleted = state.probeProgress.completed,
                probeTotal = state.probeProgress.total,
                probedCount = state.probedCount,
                aliveCount = state.aliveCount,
                failedCount = state.failedCount,
                onProbeAll = { viewModel.probeAllFriends(force = true) },
                onProbeFailed = viewModel::probeFailedFriends,
                onProbeFriend = viewModel::probeFriend,
                onCancelProbe = viewModel::cancelProbe,
            )

            HomeScreen.Create -> AgentRoute(
                initial = AgentScreen.Create,
                onExit = {
                    viewModel.back()
                    viewModel.refresh()
                },
                onOpenSettings = viewModel::openSettings
            )

            is HomeScreen.Chat -> AgentRoute(
                initial = AgentScreen.Chat((state.screen as HomeScreen.Chat).friendId),
                onExit = {
                    viewModel.back()
                    viewModel.refresh()
                },
                onOpenSettings = viewModel::openSettings
            )

            is HomeScreen.EditFriend -> AgentRoute(
                initial = AgentScreen.Edit((state.screen as HomeScreen.EditFriend).friendId),
                onExit = {
                    viewModel.back()
                    viewModel.refresh()
                },
                onOpenSettings = viewModel::openSettings
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

            HomeScreen.Settings -> SettingsRoute(onExit = viewModel::back)

            HomeScreen.Werewolf -> WerewolfRoute(
                onExit = {
                    viewModel.back()
                    viewModel.refresh()
                }
            )
        }
    }
}

private fun Friend.toUiModel(check: ApiCheck?): UIFriendListData {
    val probeState = when (check?.state) {
        ApiCheckState.OK -> UIFriendProbeState.OK
        ApiCheckState.CHECKING -> UIFriendProbeState.CHECKING
        ApiCheckState.FAILED -> UIFriendProbeState.FAILED
        null -> UIFriendProbeState.IDLE
    }
    return UIFriendListData(
        id = id,
        name = name,
        modelTag = model,
        role = role.ifBlank { "AI 好友" },
        source = company.ifBlank { brand },
        usageCount = if (messages.isEmpty()) "尚未开始对话" else "${messages.size} 条消息",
        avatarBgColor = getRandomColor(name),
        avatarTextColor = Color(0xFF374151),
        isOnline = probeState == UIFriendProbeState.OK,
        avatarPath = avatarPath,
        probeState = probeState,
        probeLatency = if (probeState == UIFriendProbeState.OK) {
            check?.elapsedMs?.let(::formatProbeElapsed)
        } else {
            null
        },
        probeError = if (probeState == UIFriendProbeState.FAILED) check?.message else null,
    )
}
