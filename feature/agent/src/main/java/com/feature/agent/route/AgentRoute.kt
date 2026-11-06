package com.feature.agent.route

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.persona.PersonaCatalog
import com.core.ai.model.ModelPrices
import com.core.repository.settings.AIProviderCatalog
import com.feature.agent.contract.AgentScreen
import com.feature.agent.data.toUIModelGroups
import com.feature.agent.vm.AgentViewModel
import com.ui.agent.UIFriendChatPage
import com.ui.agent.UIFriendCreateScreen
import com.ui.agent.data.UIProvider

@Composable
fun AgentRoute(
    initial: AgentScreen,
    onExit: () -> Unit,
    viewModel: AgentViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var screen by remember(initial) { mutableStateOf(initial) }

    LaunchedEffect(Unit) {
        if (state.loaded) viewModel.refresh()
    }

    LaunchedEffect(screen) {
        val current = screen
        if (current is AgentScreen.Chat) {
            viewModel.openChat(current.friendId)
        }
    }

    BackHandler { onExit() }

    when (val current = screen) {
        AgentScreen.Create -> {
            UIFriendCreateScreen(
                providers = state.providers.map {
                    UIProvider(
                        brand = it.brand,
                        company = it.company,
                        thinkingSwitchable = it.thinkingSwitchable,
                        isGateway = it.isGateway
                    )
                },
                selectedBrand = state.selectedBrand,
                onBrandSelect = viewModel::selectBrand,
                models = state.models,
                selectedModel = state.selectedModel,
                isLoadingModels = state.isLoadingModels,
                onModelSelect = viewModel::selectModel,
                initialThinking = state.thinkingEnabled,
                onCancel = onExit,
                personas = PersonaCatalog.all,
                isGateway = state.isGateway,
                modelGroups = state.gatewayModels.toUIModelGroups(state.priceCurrency),
                priceIsSnapshot = state.priceIsSnapshot,
                priceSnapshotDate = state.priceSnapshotDate,
                // 只有能抓定价页的平台才显示刷新按钮（硅基流动），
                // OpenRouter 的价格跟着模型列表一起回来，没有可刷的东西
                canRefreshPrice = state.isGateway && state.selectedProvider?.let { provider ->
                    AIProviderCatalog.all.firstOrNull { it.name == provider.brand }
                        ?.let { ModelPrices.canRefresh(it.id) } == true
                } == true,
                isRefreshingPrice = state.isRefreshingPrice,
                onRefreshPrice = viewModel::refreshPrice,
                onSubmit = { name, role, prompt, avatarBytes, thinking ->
                    val friend = viewModel.createFriend(
                        name = name,
                        role = role,
                        prompt = prompt,
                        avatarBytes = avatarBytes,
                        thinkingEnabled = thinking
                    )
                    if (friend != null) {
                        screen = AgentScreen.Chat(friend.id)
                    }
                }
            )
        }

        is AgentScreen.Chat -> {
            val friend = state.activeFriend
            if (!state.loaded) {
                // 等好友列表加载完成，避免刚进入就被误判为「好友已删除」
            } else if (friend == null) {
                LaunchedEffect(Unit) { onExit() }
            } else {
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
                    onBack = onExit,
                    onRetry = viewModel::retry,
                    onChangeAvatar = { bytes -> viewModel.setFriendAvatar(friend.id, bytes) }
                )
            }
        }

        is AgentScreen.Edit -> {
            val friend = state.friends.firstOrNull { it.id == current.friendId }
            if (!state.loaded) {
                // 等好友列表加载完成
            } else if (friend == null) {
                LaunchedEffect(Unit) { onExit() }
            } else {
                val provider = state.providers.firstOrNull { it.brand == friend.brand }
                UIFriendCreateScreen(
                    providers = state.providers.map {
                        UIProvider(
                            brand = it.brand,
                            company = it.company,
                            thinkingSwitchable = it.thinkingSwitchable,
                            isGateway = it.isGateway
                        )
                    },
                    selectedBrand = friend.brand,
                    onBrandSelect = {},
                    models = state.models,
                    selectedModel = friend.model,
                    isLoadingModels = false,
                    onModelSelect = {},
                    onCancel = onExit,
                    onSubmit = { name, role, prompt, avatarBytes, thinking ->
                        viewModel.saveFriendEdits(
                            friendId = friend.id,
                            name = name,
                            role = role,
                            prompt = prompt,
                            avatarBytes = avatarBytes,
                            thinkingEnabled = thinking
                        )
                        onExit()
                    },
                    title = "编辑 AI 好友",
                    submitLabel = "保存",
                    initialName = friend.name,
                    initialRole = friend.role,
                    initialPrompt = friend.prompt,
                    initialAvatarPath = friend.avatarPath,
                    initialThinking = friend.thinkingEnabled,
                    editing = true,
                    modelLabel = "${friend.brand} · ${friend.model}",
                    thinkingSwitchable = provider?.thinkingSwitchable ?: true,
                    personas = PersonaCatalog.all,
                    personaToggleLabel = "换成内置模板"
                )
            }
        }
    }
}
