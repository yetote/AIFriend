package com.ui.common.data

import androidx.compose.ui.graphics.Color

/** 矿物与植物低饱和复古色卡：取代廉价高饱和马卡龙色，和暖宣纸底与浓墨字高度融合。 */
val RandomCardColors = listOf(
    Color(0xFFEED9B7), // 暖麦赭
    Color(0xFFC8E0D4), // 青瓷绿
    Color(0xFFD6DCE8), // 雾靛灰
    Color(0xFFEEC8BE), // 陶釉粉
    Color(0xFFDFD1E5), // 紫藤灰
    Color(0xFFC9DFEA), // 霁蓝雾
    Color(0xFFF0D0B8), // 杏陶橙
    Color(0xFFD2E2C7), // 竹青绿
    Color(0xFFEAD7A4), // 琥珀黄
    Color(0xFFDAD5E8), // 烟霞紫
    Color(0xFFC8D7E5), // 远山蓝
    Color(0xFFDDD1C5)  // 温石褐
)

/**
 * 按名字取一个稳定的配色。
 */
fun getRandomColor(key: String): Color {
    val raw = key.hashCode()
    val mixed = raw xor (raw ushr 16)
    val index = (mixed.toLong() and 0x7FFFFFFF).mod(RandomCardColors.size.toLong()).toInt()
    return RandomCardColors[index]
}
