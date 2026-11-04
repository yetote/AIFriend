package com.feature.friend.route

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
import com.feature.friend.contract.FriendScreen
import com.feature.friend.model.Friend
import com.feature.friend.model.RoomSender
import com.feature.friend.vm.FriendViewModel
import com.ui.friend.UIAvatarCropDialog
import com.ui.friend.UIFriendChatPage
import com.ui.friend.UIFriendCreateScreen
import com.ui.friend.UIFriendPage
import com.ui.friend.UIRoomChatPage
import com.ui.friend.UIRoomCreateScreen
import com.ui.friend.data.UIFriendListData
import com.ui.friend.data.UIProvider
import com.ui.friend.data.UIRoomData
import com.ui.friend.data.UIRoomMember
import com.ui.friend.data.UIRoomMessage
import com.ui.friend.data.getRandomColor

@Composable
fun FriendRoute(viewModel: FriendViewModel = viewModel()) {
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

    BackHandler(enabled = state.screen != FriendScreen.List) {
        viewModel.back()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state.screen) {
            FriendScreen.List -> UIFriendPage(
                list = state.visibleFriends.map { it.toUiModel() },
                createdCount = state.friends.size,
                messageCount = state.messageCount,
                activeCount = state.activeCount,
                mostActive = state.mostActive,
                rooms = state.rooms.map { UIRoomData(id = it.id, name = it.name, memberCount = it.memberIds.size) },
                userAvatarPath = state.userAvatarPath,
                searchText = state.search,
                onSearchTextChange = viewModel::updateSearch,
                onCreateClick = viewModel::openCreate,
                onFriendClick = { viewModel.openChat(it.id) },
                onCreateRoom = viewModel::openRoomCreate,
                onRoomClick = { viewModel.openRoom(it.id) },
                onPickUserAvatar = pickUserAvatar
            )

            FriendScreen.Create -> UIFriendCreateScreen(
                providers = state.providers.map {
                    UIProvider(
                        brand = it.brand,
                        company = it.company,
                        thinkingSwitchable = it.thinkingSwitchable
                    )
                },
                selectedBrand = state.selectedBrand,
                onBrandSelect = viewModel::selectBrand,
                models = state.models,
                selectedModel = state.selectedModel,
                isLoadingModels = state.isLoadingModels,
                onModelSelect = viewModel::selectModel,
                thinkingEnabled = state.thinkingEnabled,
                onThinkingChange = viewModel::setThinkingEnabled,
                onCancel = viewModel::back,
                onCreate = viewModel::createFriend
            )

            is FriendScreen.Chat -> {
                val friend = state.activeFriend
                if (friend != null) {
                    UIFriendChatPage(
                        friendName = friend.name,
                        role = friend.role,
                        model = friend.model,
                        avatarPath = friend.avatarPath,
                        userAvatarPath = state.userAvatarPath,
                        messages = friend.messages,
                        input = state.input,
                        isSending = state.isSending,
                        error = state.error,
                        onInputChange = viewModel::updateInput,
                        onSend = viewModel::send,
                        onBack = viewModel::back,
                        onRetry = viewModel::retry,
                        onChangeAvatar = { bytes -> viewModel.setFriendAvatar(friend.id, bytes) }
                    )
                }
            }

            FriendScreen.RoomCreate -> UIRoomCreateScreen(
                name = state.roomDraftName,
                friends = state.friends.map {
                    UIRoomMember(id = it.id, name = it.name, avatarPath = it.avatarPath)
                },
                selectedIds = state.roomDraftMemberIds,
                onNameChange = viewModel::updateRoomDraftName,
                onToggleFriend = viewModel::toggleRoomDraftMember,
                onCancel = viewModel::back,
                onCreate = viewModel::createRoom
            )

            is FriendScreen.RoomChat -> {
                val room = state.activeRoom
                if (room != null) {
                    val members = room.memberIds.mapNotNull { memberId ->
                        state.friends.firstOrNull { it.id == memberId }
                            ?.let {
                                UIRoomMember(
                                    id = it.id,
                                    name = it.name,
                                    avatarPath = it.avatarPath
                                )
                            }
                    }
                    UIRoomChatPage(
                        roomName = room.name,
                        members = members,
                        allFriends = state.friends.map {
                            UIRoomMember(id = it.id, name = it.name, avatarPath = it.avatarPath)
                        },
                        messages = state.activeRoomMessages.map { message ->
                            val aiId = (message.sender as? RoomSender.Ai)?.friendId
                            UIRoomMessage(
                                id = message.id,
                                isUser = message.sender == RoomSender.User,
                                isSystem = message.sender == RoomSender.System,
                                senderId = aiId,
                                senderName = aiId?.let { id ->
                                    state.friends.firstOrNull { it.id == id }?.name ?: "已移除"
                                },
                                avatarPath = aiId?.let { id ->
                                    state.friends.firstOrNull { it.id == id }?.avatarPath
                                },
                                content = message.content
                            )
                        },
                        input = state.roomInput,
                        userAvatarPath = state.userAvatarPath,
                        pendingIds = state.roomPending,
                        errors = state.roomErrors,
                        onInputChange = viewModel::updateRoomInput,
                        onSend = viewModel::sendRoomMessage,
                        onBack = viewModel::back,
                        onRetry = viewModel::retryRoomReply,
                        onToggleMember = viewModel::toggleRoomMember
                    )
                }
            }
        }
    }
}

private fun Friend.toUiModel(): UIFriendListData = UIFriendListData(
    id = id,
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
