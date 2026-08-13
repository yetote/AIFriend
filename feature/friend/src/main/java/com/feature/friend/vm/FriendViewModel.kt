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
    var aiBrand: IAI? = null
    private val _uiState = MutableStateFlow(FriendUiState())
    val uiState: StateFlow<FriendUiState> = _uiState.asStateFlow()

    private val _friendList = MutableStateFlow<List<UIFriendListData>>(emptyList())
    val friendList: StateFlow<List<UIFriendListData>> = _friendList.asStateFlow()

    private val _aiList = MutableStateFlow<List<String>>(emptyList())
    val aiList: StateFlow<List<String>> = _aiList.asStateFlow()

    private val _aiModelList = MutableStateFlow<List<String>>(emptyList())
    val aiModelList: StateFlow<List<String>> = _aiModelList.asStateFlow()


    init {
        getai()
    }

    fun createFriend(aiFriend: UIFriendListData) = viewModelScope.launch {
        _friendList.update { currentList ->
            currentList + aiFriend
        }
    }

    fun requestModel(aiName: String) = viewModelScope.launch {
        if (aiBrand == null) {
            val aiClass = AIFactory.getAIClassByName(aiName)
            aiClass?.let {
                aiBrand = AIFactory().createAI(aiClass)
            }
        }
        if (aiBrand?.modelSet.isNullOrEmpty()) {
            aiBrand?.requestModel()
        }
        aiBrand?.modelSet?.let { models ->
            _aiModelList.update {
                models.toList()
            }
        }
    }

    fun chat(message: String) = viewModelScope.launch {
//        val chatMessage = friend?.chat(message, friend?.modelSet?.first() ?: "") ?: ""
//        _uiState.update { it.copy(chatText = chatMessage) }

    }

    fun getai() {
        _aiList.update { list ->
            list + listOf("Deepseek","ChatGPT","Gemini")
        }
    }
}