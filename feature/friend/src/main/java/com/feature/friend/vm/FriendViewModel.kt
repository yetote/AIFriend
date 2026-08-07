package com.feature.friend.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.core.ai.entity.IAI
import com.core.ai.factory.AIFactory
import kotlinx.coroutines.launch

class FriendViewModel : ViewModel() {
    val aiFactory = AIFactory()
    var friend: IAI? = null
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
        friend?.chat(message, friend?.modelSet?.first() ?: "")
    }
}