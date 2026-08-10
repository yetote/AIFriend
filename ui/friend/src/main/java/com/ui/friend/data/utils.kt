package com.ui.friend.data

import androidx.compose.ui.graphics.Color

val RandomCardColors = listOf(
    Color(0xFFFFE082), // 柔黄
    Color(0xFF80CBC4), // 青绿
    Color(0xFF9FA8DA), // 靛蓝
    Color(0xFFEF9A9A), // 柔红
    Color(0xFFCE93D8), // 浅紫
    Color(0xFF81D4FA), // 天蓝
    Color(0xFFFFAB91), // 橙粉
    Color(0xFFA5D6A7)  // 浅绿
)
fun getRandomColor(key: String): Color {
    val index = kotlin.math.abs(key.hashCode()) % RandomCardColors.size
    return RandomCardColors[index]
}