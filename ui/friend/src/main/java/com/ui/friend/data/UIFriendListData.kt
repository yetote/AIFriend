package com.ui.friend.data

import androidx.compose.ui.graphics.Color
import com.core.ai.entity.IAI

data class UIFriendListData(
    val name: String,
    val modelTag: String,
    val role: String,
    val source: String,
    val usageCount: String,
    val avatarBgColor: Color,
    val avatarTextColor: Color,
    val isOnline: Boolean = true,
    var aiData: IAI? = null
)