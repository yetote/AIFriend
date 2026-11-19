package com.core.repository.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 狼人杀的少量用户设置。
 *
 * 只有自动朗读开关一项，但没放进 AIProviderSettings——那个类负责「模型服务凭证」，
 * 朗读是界面偏好，跟 Key 毫无关系。全 App 仍共用同一个 DataStore 文件，
 * 所以不会多出一个实例，也不会踩「同名文件多个 DataStore」的坑。
 */
class WerewolfSettings(context: Context) {
    private val store = context.applicationContext.appPreferences

    val autoSpeak: Flow<Boolean> = store.data
        .catch { emit(emptyPreferences()) }
        .map { it[AUTO_SPEAK] ?: true }

    suspend fun setAutoSpeak(enabled: Boolean) {
        runCatching { store.edit { it[AUTO_SPEAK] = enabled } }
    }

    /**
     * 要不要在对局里把 AI 的思考过程显示出来。
     *
     * 默认关：参与局看到别人的思考等于读心，那会把「猜谁在想什么」变成主要玩法。
     * 观战和对局结束不受这个开关限制——那两个视角本来就能看复盘。
     */
    val showThinking: Flow<Boolean> = store.data
        .catch { emit(emptyPreferences()) }
        .map { it[SHOW_THINKING] ?: false }

    suspend fun setShowThinking(enabled: Boolean) {
        runCatching { store.edit { it[SHOW_THINKING] = enabled } }
    }

    private companion object {
        val AUTO_SPEAK = booleanPreferencesKey("werewolf_auto_speak")
        val SHOW_THINKING = booleanPreferencesKey("werewolf_show_thinking")
    }
}
