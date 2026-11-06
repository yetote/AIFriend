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
import com.ui.common.theme.UIGradientBackground
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
            // 直接按目标 id 取房间，不依赖 openRoom() 是否已经跑过，
            // 否则第一帧 activeRoom 还是空的，会先白一下再出内容。
            val room = state.rooms.firstOrNull { it.id == current.roomId }
            val membersPending =
                room != null && room.memberIds.isNotEmpty() && !state.friendsLoaded
            // 首屏消息没读完就渲染会先闪一下空状态页，等读完再画
            val messagesPending = room != null && room.id !in state.roomMessagesLoaded
            if (!state.loaded || membersPending || messagesPending) {
                UIGradientBackground(content = {})
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
                    messages = state.roomMessages
                        .filter { it.roomId == room.id }
                        .sortedWith(compareBy({ it.createdAt }, { it.seq }))
                        .map { message ->
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
                    mentionIds = state.roomMentionIds,
                    hasMoreMessages = room.id in state.roomHasMore,
                    isLoadingMore = room.id in state.roomLoadingMore,
                    autoChatActive = state.autoChatRoomId == room.id,
                    autoChatCompleted = if (state.autoChatRoomId == room.id) {
                        state.autoChatCompleted
                    } else {
                        0
                    },
                    autoChatTotal = if (state.autoChatRoomId == room.id) state.autoChatTotal else 0,
                    onInputChange = viewModel::updateRoomInput,
                    onSend = viewModel::sendRoomMessage,
                    onBack = onExit,
                    onRetry = viewModel::retryRoomReply,
                    onToggleMember = viewModel::toggleRoomMember,
                    onMentionSelect = viewModel::selectMentions,
                    onStopReply = viewModel::stopRoomReply,
                    onStopAll = viewModel::stopAllRoomReplies,
                    onStopAutoChat = { viewModel.stopAutoChat(room.id) },
                    onOpenSettings = { screen = RoomScreen.Settings(room.id) },
                    onLoadMore = { viewModel.loadOlderRoomMessages(room.id) }
                )
            }
        }

        is RoomScreen.Settings -> {
            val room = state.rooms.firstOrNull { it.id == current.roomId }
            val membersPending =
                room != null && room.memberIds.isNotEmpty() && !state.friendsLoaded
            if (!state.loaded || membersPending) {
                UIGradientBackground(content = {})
            } else if (room == null) {
                LaunchedEffect(Unit) { onExit() }
            } else {
                val roomMessages = state.roomMessages.filter { it.roomId == room.id }
                val names = state.friends.associate { it.id to it.name }
                val estimatedTargets = state.roomMentionIds
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
                    autoChatEnabled = room.autoChatEnabled,
                    autoChatTurns = room.autoChatTurns,
                    messageCount = roomMessages.size,
                    estimatedContextTokens = estimatedTokens,
                    estimatedRequestCount = estimatedTargets.size,
                    onSave = { name, prompt, discussion, window, autoChat, turns ->
                        viewModel.saveRoomSettings(
                            room.id,
                            name,
                            prompt,
                            discussion,
                            window,
                            autoChat,
                            turns
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
