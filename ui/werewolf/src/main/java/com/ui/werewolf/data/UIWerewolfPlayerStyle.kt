package com.ui.werewolf.data

/**
 * 牌手风格在界面上的展示项。
 *
 * id 和引擎侧 `PlayerStyle.id` 一一对齐（cautious / aggressive / …），
 * route 层直接把 id 传给 ViewModel，不做转换。ui 模块不依赖 feature，
 * 所以这里只放字符串和文案，引擎侧枚举是另一份——两边靠 id 这个稳定字符串对接。
 */
data class UIWerewolfPlayerStyle(
    val id: String,
    val label: String,
    val tagline: String,
)

/** 风格列表，顺序即选单顺序。和引擎侧 PlayerStyle 的声明顺序一致。 */
val UI_PLAYER_STYLES = listOf(
    UIWerewolfPlayerStyle("cautious", "谨慎求证", "不见实锤不下结论，宁可慢一拍"),
    UIWerewolfPlayerStyle("aggressive", "强攻压迫", "主动带节奏，敢下重手"),
    UIWerewolfPlayerStyle("analytical", "逻辑推演", "盘链条、算票型，像解题一样打牌"),
    UIWerewolfPlayerStyle("intuitive", "直觉洞察", "抓语气和心态破绽，敢跟感觉下注"),
    UIWerewolfPlayerStyle("deep", "深水潜伏", "前期不显山露水，关键处才出手"),
    UIWerewolfPlayerStyle("stubborn", "执拗较真", "认准了就不回头，逼人给个说法"),
    UIWerewolfPlayerStyle("adaptive", "随机应变", "顺风不浪、逆风不慌，随时改口"),
    UIWerewolfPlayerStyle("warm", "温和引导", "不撕咬，靠把道理讲明白带票"),
)
