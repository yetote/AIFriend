package com.feature.friend.route

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feature.friend.contract.FriendScreen
import com.feature.friend.model.Friend
import com.feature.friend.vm.FriendViewModel
import com.ui.friend.UIFriendChatPage
import com.ui.friend.UIFriendCreateScreen
import com.ui.friend.UIFriendPage
import com.ui.friend.data.UIFriendListData
import com.ui.friend.data.getRandomColor

@Composable
fun FriendRoute(viewModel: FriendViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                searchText = state.search,
                onSearchTextChange = viewModel::updateSearch,
                onCreateClick = viewModel::openCreate,
                onFriendClick = { viewModel.openChat(it.id) }
            )

            FriendScreen.Create -> UIFriendCreateScreen(
                models = state.models,
                selectedModel = state.selectedModel,
                isLoadingModels = state.isLoadingModels,
                onModelSelect = viewModel::selectModel,
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
                        messages = friend.messages,
                        input = state.input,
                        isSending = state.isSending,
                        error = state.error,
                        onInputChange = viewModel::updateInput,
                        onSend = viewModel::send,
                        onBack = viewModel::back,
                        onRetry = viewModel::retry
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
    source = brand,
    usageCount = if (messages.isEmpty()) "尚未开始对话" else "${messages.size} 条消息",
    avatarBgColor = getRandomColor(name),
    avatarTextColor = Color(0xFF374151),
    isOnline = true
)
