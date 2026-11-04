package com.ui.friend.data

import androidx.compose.ui.graphics.Color

data class UIFriendListData(
    val name: String,
    val modelTag: String,
    val role: String,
    val source: String,
    val usageCount: String,
    val avatarBgColor: Color,
    val avatarTextColor: Color,
    val isOnline: Boolean = true,
    val id: String = ""
)
