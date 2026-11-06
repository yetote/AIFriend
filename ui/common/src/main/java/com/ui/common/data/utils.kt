package com.ui.common.data

import androidx.compose.ui.graphics.Color

val RandomCardColors = listOf(
    Color(0xFFFFE082), // 柔黄
    Color(0xFF80CBC4), // 青绿
    Color(0xFF9FA8DA), // 靛蓝
    Color(0xFFEF9A9A), // 柔红
    Color(0xFFCE93D8), // 浅紫
    Color(0xFF81D4FA), // 天蓝
    Color(0xFFFFAB91), // 橙粉
    Color(0xFFA5D6A7), // 浅绿
    Color(0xFFFFCC80), // 杏橙
    Color(0xFFB39DDB), // 中紫
    Color(0xFF90CAF9), // 湖蓝
    Color(0xFFBCAAA4)  // 灰褐
)

/**
 * 按名字取一个稳定的配色。
 *
 * 之前只用了 `hashCode()`：一是 8 个颜色太少，「旅行 / 工作 / 家人」这类常用名
 * 很容易撞到同一格；二是 `abs(Int.MIN_VALUE)` 仍然是负数，会直接下标越界。
 * 这里先把哈希打散再取模，颜色也扩到 12 个，撞色概率明显下降。
 */
fun getRandomColor(key: String): Color {
    val raw = key.hashCode()
    // 混入一次 xorshift，让相邻字符串的哈希在取模后也落得开
    val mixed = raw xor (raw ushr 16)
    val index = (mixed.toLong() and 0x7FFFFFFF).mod(RandomCardColors.size.toLong()).toInt()
    return RandomCardColors[index]
}
