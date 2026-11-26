package com.core.common.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全站外观模式：跟随系统 / 浅色宣纸 / 深色曜石。
 */
enum class AppThemeMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色宣纸"),
    DARK("dark", "深色曜石");

    companion object {
        fun fromId(id: String?): AppThemeMode =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * 全站外观模式即时控制器：设置页切换时无需重建 Activity 即可实时生效。
 */
object AppThemeController {
    private val _mode = MutableStateFlow(AppThemeMode.SYSTEM)
    val mode: StateFlow<AppThemeMode> = _mode.asStateFlow()

    fun setMode(mode: AppThemeMode) {
        _mode.value = mode
    }
}

val CompactTypography = Typography(
    bodyLarge = TextStyle(
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    bodyMedium = TextStyle(
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
)

/**
 * 全站浅色基础色板：宣纸朱砂（Warm Editorial Atelier）。
 * 暖宣纸画布 + 浓墨黑棕正文 + 朱砂暖陶主强调 + 松石绿 / 赤印红语义色。
 */
private val AIFriendLightColorScheme = lightColorScheme(
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

/**
 * 全站深色基础色板：烛影曜石（Candlelit Obsidian Atelier）。
 * 深炭曜石画布 + 暖象牙正文 + 朱砂微光主强调 + 古铜金 / 松石绿语义色。
 */
private val AIFriendDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8704D),
    onPrimary = Color(0xFF1A0D09),
    primaryContainer = Color(0xFF3E2219),
    onPrimaryContainer = Color(0xFFFAD7CA),
    secondary = Color(0xFF4CB894),
    onSecondary = Color(0xFF0A241B),
    secondaryContainer = Color(0xFF162E25),
    onSecondaryContainer = Color(0xFFC5EFE0),
    tertiary = Color(0xFFE5B869),
    onTertiary = Color(0xFF281C06),
    background = Color(0xFF120F0D),
    onBackground = Color(0xFFF3ECE2),
    surface = Color(0xFF1D1815),
    onSurface = Color(0xFFF3ECE2),
    surfaceVariant = Color(0xFF2C251F),
    onSurfaceVariant = Color(0xFFC2B6A8),
    outline = Color(0xFF3D342D),
    error = Color(0xFFE86660),
    onError = Color(0xFF2B0B0A)
)

@Composable
fun AIFriendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AIFriendDarkColorScheme else AIFriendLightColorScheme,
        typography = CompactTypography,
        content = content
    )
}
