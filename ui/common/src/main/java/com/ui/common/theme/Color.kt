package com.ui.common.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 当前是否处于全站夜间（深色）模式。
 * 通过 [MaterialTheme.colorScheme.background] 亮度实时判定，
 * 所有 UI 模块零跨层依赖即可响应 `AIFriendTheme(darkTheme = ...)`。
 */
val UIIsDarkTheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
@ReadOnlyComposable
private fun themed(light: Color, dark: Color): Color = if (UIIsDarkTheme) dark else light

// 宣纸朱砂（白昼） × 烛影曜石（夜间）语义色体系
val UIFriendBarTitleColor: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF171411), Color(0xFFF3ECE2))
val UIFriendBarTitle2Color: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF8C8277), Color(0xFF9C8E80))
val UIFriendBarGray: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFEDE7DC), Color(0xFF28221D))
val UIFriendBarRed: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFC2302B), Color(0xFFE86660))
val UIFriendBarBlue: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF26201B), Color(0xFFF3ECE2))
val UIFriendBarHeaderTagGreenColor: Color
    @Composable @ReadOnlyComposable get() = Color(0xFFE5B869)
val UIFriendBarDividerLineColor: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF3A312A), Color(0xFF473C33))
val UIFriendBarDecorativeWaveColor: Color
    @Composable @ReadOnlyComposable get() = Color(0xFFE07855)

// 全站通用语义色
val UIFriendBarTitleColor2: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF171411), Color(0xFFF3ECE2))
val UIFriendBarSubtitleColor: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF5A524A), Color(0xFFC2B6A8))
val UIFriendBarCreateButtonBg: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFF9EAE3), Color(0xFF3A2118))
val UIFriendBarCreateButtonText: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFC84B2A), Color(0xFFE8704D))
val UIFriendBarSearchInputBg: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFFFFDF9), Color(0xFF1D1815))
val UIFriendBarTabSelectedBg: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF171411), Color(0xFFF3ECE2))
val UIFriendBarTabSelectedText: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFFFFDF9), Color(0xFF171411))
val UIFriendBarTabUnselectedBg: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFEDE7DC), Color(0xFF28221D))
val UIFriendBarTabUnselectedText: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF7B7166), Color(0xFFA6998B))
val UIFriendBarListCardBg: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFFFFDF9), Color(0xFF1D1815))
val UIFriendBarItemTitleColor: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF171411), Color(0xFFF3ECE2))
val UIFriendBarItemSubtitleColor: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF5A524A), Color(0xFFC2B6A8))
val UIFriendBarItemMetaColor: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF8C8277), Color(0xFF9C8E80))
val UIFriendBarOnlineGreen: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF1F7A5A), Color(0xFF4CB894))
val UIFriendBarTagBgBlue: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFF9EAE3), Color(0xFF3A2118))
val UIFriendBarTagTextBlue: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFC84B2A), Color(0xFFE8704D))
val UIFriendBarDividerColor: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFE4DDD0), Color(0xFF332B24))

// 额外扩展色：用于新版 Bento 与编辑排版层次
val UIAtelierInk: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF1C1815), Color(0xFF221C18))
val UIAtelierInkElevated: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF28221D), Color(0xFF2F2721))
val UIAtelierBrass: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFB87A1F), Color(0xFFE5B869))
val UIAtelierBrassBg: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFFBF1DF), Color(0xFF332614))
val UIAtelierJade: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF1F7A5A), Color(0xFF4CB894))
val UIAtelierJadeBg: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFE5F3ED), Color(0xFF162E25))
val UIAtelierSlate: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF364D7C), Color(0xFF8AB4F8))
val UIAtelierSlateBg: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFEAEFF7), Color(0xFF1C2538))
val UIAtelierSand: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFEFEAE0), Color(0xFF2A231E))

// 对话页
val UIChatUserBubble: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFF1E1A17), Color(0xFF9C381C))
val UIChatAssistantBubble: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFFFFDF9), Color(0xFF1E1916))
val UIChatInputBg: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFEFEAE0), Color(0xFF2A231E))
val UIChatErrorBg: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFFDF0EE), Color(0xFF381A18))
val UIChatSendDisabled: Color
    @Composable @ReadOnlyComposable get() = themed(Color(0xFFD2C9BC), Color(0xFF40372F))
