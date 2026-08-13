package com.ui.friend.data

import com.core.ai.entity.IAI

data class UIFriendListData(
    val nickname: String,
    var aiBrand: String,
    val logo: String,
    val model: String,
    var aiData: IAI? = null
)