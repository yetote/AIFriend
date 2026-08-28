package com.core.common.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
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
@Composable
fun AIFriendTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = CompactTypography,
        content = content
    )
}