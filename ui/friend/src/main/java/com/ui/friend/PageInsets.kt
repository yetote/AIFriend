package com.ui.friend

import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Android 11（API 30）开始才有可靠的 IME insets，并且只有 edge-to-edge 时才会下发。
 * API 30 以下退回系统默认的 adjustResize（窗口整体缩放），此时不要再叠加 Compose inset，
 * 否则会出现「只顶起导航栏高度、键盘盖住输入框」的问题。
 */
private val edgeToEdgeInsetsSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

@Composable
fun Modifier.pageInsets(): Modifier =
    if (edgeToEdgeInsetsSupported) {
        windowInsetsPadding(WindowInsets.safeDrawing)
    } else {
        this
    }

@Composable
fun Modifier.pageInsetsTop(): Modifier =
    if (edgeToEdgeInsetsSupported) {
        windowInsetsPadding(
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
            )
        )
    } else {
        this
    }

@Composable
fun Modifier.pageInsetsBottom(): Modifier =
    if (edgeToEdgeInsetsSupported) {
        windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
        )
    } else {
        this
    }
