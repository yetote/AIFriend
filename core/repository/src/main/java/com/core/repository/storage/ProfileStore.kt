package com.core.repository.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * 用户自己的资料（目前只有头像），和好友数据分开存。
 */
class ProfileStore(private val context: Context) {

    suspend fun loadAvatarPath(): String? {
        val preferences = context.appPreferences.data.first()
        return preferences[USER_AVATAR_KEY]
    }

    suspend fun saveAvatarPath(path: String) {
        context.appPreferences.edit { preferences ->
            preferences[USER_AVATAR_KEY] = path
        }
    }

    private companion object {
        val USER_AVATAR_KEY = stringPreferencesKey("user_avatar_v1")
    }
}
