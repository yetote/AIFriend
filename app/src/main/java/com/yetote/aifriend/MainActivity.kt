package com.yetote.aifriend

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.core.common.theme.AIFriendTheme
import com.feature.home.route.HomeRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // API 30 以下没有可靠的 IME insets，保持系统默认 fit + adjustResize，
        // 让窗口自己为键盘让出空间。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            enableEdgeToEdge()
        }
        setContent {
            AIFriendTheme {
                GlobalUiEventObserver()
                HomeRoute()
            }
        }
    }
}
