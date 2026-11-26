package com.core.repository.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * 全站外观模式持久化（跟随系统 / 浅色宣纸 / 深色曜石）。
 */
class ThemeSettings(context: Context) {

    private val store = context.appPreferences

    suspend fun loadModeId(): String =
        store.data.first()[KEY_THEME_MODE] ?: DEFAULT_MODE_ID

    suspend fun saveModeId(modeId: String) {
        store.edit { prefs ->
            prefs[KEY_THEME_MODE] = modeId
        }
    }

    private companion object {
        const val DEFAULT_MODE_ID = "system"
        val KEY_THEME_MODE = stringPreferencesKey("app_theme_mode")
    }
}
