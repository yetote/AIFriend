package com.core.repository.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * 全 App 共用同一个 Preferences DataStore 文件。
 * 同一个文件不能创建多个 DataStore 实例，所以好友、聊天室都通过这个扩展访问。
 */
internal val Context.appPreferences: DataStore<Preferences> by preferencesDataStore(name = "aifriend")
