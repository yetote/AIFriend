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

/** 全站基础色板：蓝色主色，配 iOS 风格的浅底深字。 */
private val AIFriendColorScheme = lightColorScheme(
    primary = Color(0xFF2B6BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0x1A2B6BFF),
    onPrimaryContainer = Color(0xFF1B4ED8),
    secondary = Color(0xFF5B93FF),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFD),
    onBackground = Color(0xFF1B2434),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B2434),
    surfaceVariant = Color(0xFFF2F5FA),
    onSurfaceVariant = Color(0xFF667085),
    outline = Color(0xFFD0D7E2),
    error = Color(0xFFE5484D),
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