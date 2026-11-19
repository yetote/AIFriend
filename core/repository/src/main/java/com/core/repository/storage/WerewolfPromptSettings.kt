package com.core.repository.storage

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 狼人杀的**提示词自定义**。
 *
 * 分两层：
 * - **全局**：所有板子都生效的一段补充设定；
 * - **按板子**：只对某一张板子生效（用板子的稳定 key，比如 `standard8`）。
 *
 * 为什么按板子存而不是按 `WerewolfConfig` 存：那块板子配置里 `composition` 是 Map，
 * 没有稳定的字符串标识，拿它当 key 迟早对不上（存进去的读不出来）。
 * 板子的 key 是固定常量，见 `WerewolfBoards`。
 *
 * **只存在 DataStore，不进存档**：提示词是「这局怎么打」的偏好，
 * 不是局面的一部分。续档时用当前设置接着打，比把一大段文本塞进存档更省事。
 */
class WerewolfPromptSettings(context: Context) {
    private val store = context.applicationContext.appPreferences

    /** 全局补充设定。所有板子都会带上。 */
    val global: Flow<String> = store.data
        .catch { emit(emptyPreferences()) }
        .map { it[GLOBAL].orEmpty() }

    /** 某个板子的补充设定。没配过就是空串。 */
    fun board(boardKey: String): Flow<String> = store.data
        .catch { emit(emptyPreferences()) }
        .map { it[boardKeyOf(boardKey)].orEmpty() }

    suspend fun setGlobal(text: String) {
        runCatching { store.edit { it[GLOBAL] = text } }
    }

    suspend fun setBoard(boardKey: String, text: String) {
        runCatching { store.edit { it[boardKeyOf(boardKey)] = text } }
    }

    /** 清掉某个板子的自定义，回到「完全用内置提示词」。 */
    suspend fun clearBoard(boardKey: String) {
        runCatching { store.edit { it.remove(boardKeyOf(boardKey)) } }
    }

    suspend fun clearGlobal() {
        runCatching { store.edit { it.remove(GLOBAL) } }
    }

    private companion object {
        val GLOBAL = stringPreferencesKey("werewolf_prompt_global")

        /** 板子的 key 拼进 preference key 里。 */
        fun boardKeyOf(boardKey: String) =
            stringPreferencesKey("werewolf_prompt_board_$boardKey")
    }
}
