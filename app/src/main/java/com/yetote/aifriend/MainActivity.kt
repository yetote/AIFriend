package com.yetote.aifriend

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.core.common.theme.AIFriendTheme
import com.core.common.theme.AppThemeController
import com.core.common.theme.AppThemeMode
import com.core.repository.storage.ThemeSettings
import com.feature.home.route.HomeRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            )
        }
        val themeSettings = ThemeSettings(applicationContext)
        setContent {
            LaunchedEffect(Unit) {
                val modeId = withContext(Dispatchers.IO) {
                    runCatching { themeSettings.loadModeId() }.getOrDefault(AppThemeMode.SYSTEM.id)
                }
                AppThemeController.setMode(AppThemeMode.fromId(modeId))
            }
            val mode by AppThemeController.mode.collectAsStateWithLifecycle()
            val darkTheme = when (mode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                SideEffect {
                    val style = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(
                        statusBarStyle = style,
                        navigationBarStyle = style
                    )
                }
            }
            AIFriendTheme(darkTheme = darkTheme) {
                GlobalUiEventObserver()
                HomeRoute()
            }
        }
    }
}
