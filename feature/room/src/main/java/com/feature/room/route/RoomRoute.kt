package com.feature.room.route

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feature.agent.data.estimateTokens
import com.feature.agent.data.trimToBudget
import com.feature.room.contract.RoomScreen
import com.feature.room.data.buildRoomContext
import com.feature.room.model.MAX_ROOM_MEMBERS
import com.feature.room.vm.RoomViewModel
import com.ui.room.UIRoomChatPage
import com.ui.room.UIRoomCreateScreen
import com.ui.room.UIRoomSettingsScreen
import com.ui.room.data.UIRoomMember
import com.ui.room.data.UIRoomMessage

@Composable
fun RoomRoute(
    initial: RoomScreen,
    onExit: () -> Unit,
    viewModel: RoomViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var screen by remember(initial) { mutableStateOf(initial) }

    LaunchedEffect(Unit) {
        if (state.loaded) viewModel.refresh()
    }

    LaunchedEffect(screen) {
        val current = screen
        if (current is RoomScreen.Chat) {
            viewModel.openRoom(current.roomId)
        }
    }

    BackHandler {
        val current = screen
        if (current is RoomScreen.Settings) {
            screen = RoomScreen.Chat(current.roomId)
        } else {
            onExit()
        }
    }

    when (val current = screen) {
        RoomScreen.Create -> {
            UIRoomCreateScreen(
                name = state.roomDraftName,
                prompt = state.roomDraftPrompt,
                friends = state.friends.map {
                    UIRoomMember(
                        id = it.id,
                        name = it.name,
                        avatarPath = it.avatarPath,
                        role = it.role,
                        model = it.model
                    )
                },
                selectedIds = state.roomDraftMemberIds,
                maxMembers = MAX_ROOM_MEMBERS,
                onNameChange = viewModel::updateRoomDraftName,
                onPromptChange = viewModel::updateRoomDraftPrompt,
                onToggleFriend = viewModel::toggleRoomDraftMember,
                onCancel = onExit,
                onCreate = {
                    val room = viewModel.createRoom()
                    if (room != null) {
                        screen = RoomScreen.Chat(room.id)
                    }
                }
            )
        }

        is RoomScreen.Chat -> {
            val room = state.activeRoom
            if (!state.loaded) {
                // 等房间和好友加载完成
            } else if (room == null) {
                LaunchedEffect(Unit) { onExit() }
            } else {
                val members = room.memberIds.mapNotNull { memberId ->
                    state.friends.firstOrNull { it.id == memberId }
                        ?.let {
                            UIRoomMember(
                                id = it.id,
                                name = it.name,
                                avatarPath = it.avatarPath,
                                role = it.role,
                                model = it.model
                            )
                        }
                }
                UIRoomChatPage(
                    roomName = room.name,
                    avatarPath = room.avatarPath,
                    members = members,
                    allFriends = state.friends.map {
                        UIRoomMember(
                            id = it.id,
                            name = it.name,
                            avatarPath = it.avatarPath,
                            role = it.role,
                            model = it.model
                        )
                    },
                    maxMembers = MAX_ROOM_MEMBERS,
                    messages = state.activeRoomMessages.map { message ->
                        val aiId = (message.sender as? com.feature.room.model.RoomSender.Ai)?.friendId
                        UIRoomMessage(
                            id = message.id,
                            isUser = message.sender == com.feature.room.model.RoomSender.User,
                            isSystem = message.sender == com.feature.room.model.RoomSender.System,
                            senderId = aiId,
                            senderName = aiId?.let { id ->
                                state.friends.firstOrNull { it.id == id }?.name ?: "已移除"
                            },
                            avatarPath = aiId?.let { id ->
                                state.friends.firstOrNull { it.id == id }?.avatarPath
                            },
                            content = message.content,
                            createdAt = message.createdAt
                        )
                    },
                    input = state.roomInput,
                    userAvatarPath = state.userAvatarPath,
                    pendingIds = state.roomPending,
                    errors = state.roomErrors,
                    targetIds = state.roomTargetIds,
                    hasMoreMessages = room.id in state.roomHasMore,
                    isLoadingMore = room.id in state.roomLoadingMore,
                    onInputChange = viewModel::updateRoomInput,
                    onSend = viewModel::sendRoomMessage,
                    onBack = onExit,
                    onRetry = viewModel::retryRoomReply,
                    onToggleMember = viewModel::toggleRoomMember,
                    onToggleTarget = viewModel::toggleRoomTarget,
                    onSelectAllTargets = viewModel::selectAllRoomTargets,
                    onStopReply = viewModel::stopRoomReply,
                    onStopAll = viewModel::stopAllRoomReplies,
                    onOpenSettings = { screen = RoomScreen.Settings(room.id) },
                    onLoadMore = { viewModel.loadOlderRoomMessages(room.id) }
                )
            }
        }

        is RoomScreen.Settings -> {
            val room = state.rooms.firstOrNull { it.id == current.roomId }
            if (!state.loaded) {
                // 等数据加载完成
            } else if (room == null) {
                LaunchedEffect(Unit) { onExit() }
            } else {
                val roomMessages = state.roomMessages.filter { it.roomId == room.id }
                val names = state.friends.associate { it.id to it.name }
                val estimatedTargets = state.roomTargetIds
                    .intersect(room.memberIds.toSet())
                    .ifEmpty { room.memberIds.toSet() }
                val estimatedTokens = estimatedTargets.maxOfOrNull { friendId ->
                    buildRoomContext(
                        messages = roomMessages,
                        friendId = friendId,
                        joinedSeq = room.memberJoinSeq[friendId] ?: 0L,
                        otherWindowRounds = if (room.discussionEnabled) {
                            room.discussionWindow
                        } else {
                            null
                        },
                        friendNames = names
                    ).trimToBudget(if (room.discussionEnabled) 4000 else 6000)
                        .sumOf { estimateTokens(it.content) }
                } ?: 0

                UIRoomSettingsScreen(
                    roomName = room.name,
                    roomPrompt = room.prompt,
                    avatarPath = room.avatarPath,
                    members = room.memberIds.mapNotNull { memberId ->
                        state.friends.firstOrNull { it.id == memberId }
                            ?.let {
                                UIRoomMember(
                                    id = it.id,
                                    name = it.name,
                                    avatarPath = it.avatarPath,
                                    role = it.role,
                                    model = it.model
                                )
                            }
                    },
                    allFriends = state.friends.map {
                        UIRoomMember(
                            id = it.id,
                            name = it.name,
                            avatarPath = it.avatarPath,
                            role = it.role,
                            model = it.model
                        )
                    },
                    maxMembers = MAX_ROOM_MEMBERS,
                    discussionEnabled = room.discussionEnabled,
                    discussionWindow = room.discussionWindow,
                    sequentialSpeaking = room.sequentialSpeaking,
                    messageCount = roomMessages.size,
                    estimatedContextTokens = estimatedTokens,
                    estimatedRequestCount = estimatedTargets.size,
                    onSave = { name, prompt, discussion, window, sequential ->
                        viewModel.saveRoomSettings(
                            room.id,
                            name,
                            prompt,
                            discussion,
                            window,
                            sequential
                        )
                        screen = RoomScreen.Chat(room.id)
                    },
                    onToggleMember = viewModel::toggleRoomMember,
                    onClearMessages = {
                        viewModel.clearRoomMessages(room.id)
                        screen = RoomScreen.Chat(room.id)
                    },
                    onExport = {
                        viewModel.exportRoomMarkdown(room.id) { markdown ->
                            shareRoomMarkdown(context, room.name, markdown)
                        }
                    },
                    onChangeAvatar = { bytes -> viewModel.setRoomAvatar(room.id, bytes) },
                    onBack = { screen = RoomScreen.Chat(room.id) }
                )
            }
        }
    }
}

private fun shareRoomMarkdown(context: Context, roomName: String, markdown: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "$roomName 聊天记录")
        putExtra(Intent.EXTRA_TEXT, markdown)
    }
    context.startActivity(Intent.createChooser(intent, "导出聊天记录"))
}
