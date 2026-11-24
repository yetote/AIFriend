package com.core.common.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle


val CompactTypography = Typography(
    bodyLarge = TextStyle(
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    bodyMedium = TextStyle(
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
)

/** 全站基础色板：瓷白墨韵 + 暮靛星辉，清透浅底配深邃靛紫主色。 */
private val AIFriendColorScheme = lightColorScheme(
    primary = Color(0xFF4338CA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = Color(0xFF6366F1),
    onSecondary = Color.White,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEDF1F8),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFE11D48),
    onError = Color.White
)

@Composable
fun AIFriendTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AIFriendColorScheme,
        typography = CompactTypography,
        content = content
    )
}