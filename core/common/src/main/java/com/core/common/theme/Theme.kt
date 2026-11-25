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

/**
 * 全站基础色板：宣纸朱砂（Warm Editorial Atelier）。
 * 暖宣纸画布 + 浓墨黑棕正文 + 朱砂暖陶主强调 + 松石绿 / 赤印红语义色。
 */
private val AIFriendColorScheme = lightColorScheme(
    primary = Color(0xFFC84B2A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF9EAE3),
    onPrimaryContainer = Color(0xFF7A2812),
    secondary = Color(0xFF1F7A5A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5F3ED),
    onSecondaryContainer = Color(0xFF114B36),
    tertiary = Color(0xFFB87A1F),
    onTertiary = Color.White,
    background = Color(0xFFF4EFE6),
    onBackground = Color(0xFF171411),
    surface = Color(0xFFFFFDF9),
    onSurface = Color(0xFF171411),
    surfaceVariant = Color(0xFFEDE7DC),
    onSurfaceVariant = Color(0xFF5A524A),
    outline = Color(0xFFDAD1C3),
    error = Color(0xFFC2302B),
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
