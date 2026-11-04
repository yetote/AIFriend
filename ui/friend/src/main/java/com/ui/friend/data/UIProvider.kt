package com.ui.friend.data

/**
 * UI 层的厂商选项，避免 ui 模块反向依赖 feature 层。
 */
data class UIProvider(
    val brand: String,
    val company: String,
    val thinkingSwitchable: Boolean = true
)
