package com.feature.friend.vm

import androidx.lifecycle.viewModelScope
import com.core.ai.entity.IAI
import com.core.ai.factory.AIFactory
import com.core.common.BaseViewModel
import com.feature.friend.contract.FriendUiState
import com.ui.friend.data.UIFriendListData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FriendViewModel : BaseViewModel() {
    val aiFactory = AIFactory()
    var friend: IAI? = null
    private val _uiState = MutableStateFlow(FriendUiState())
    val uiState: StateFlow<FriendUiState> = _uiState.asStateFlow()

    private val _friendList = MutableStateFlow<List<UIFriendListData>>(emptyList())
    val friendList: StateFlow<List<UIFriendListData>> = _friendList.asStateFlow()

    fun createFriend(aiName: String) = viewModelScope.launch {
        val aiClass = AIFactory.getAIClassByName(aiName)
        if (aiClass != null) {
            friend = aiFactory.createAI(aiClass)
        }
        requestModel()
    }

    fun requestModel() = viewModelScope.launch {
        friend?.requestModel()
        chat("你好呀,你是谁？")
    }

    fun chat(message: String) = viewModelScope.launch {
        val chatMessage = friend?.chat(message, friend?.modelSet?.first() ?: "") ?: ""
        _uiState.update { it.copy(chatText = chatMessage) }
        _friendList.update { currentList ->
            currentList + UIFriendListData(
                nickname = "小深度",
                aiName = "DeepSeek-V3",
                logo = "deepseek",
                model = "DeepSeek-V3",
                company = "深度求索"
            )
        }
    }
}