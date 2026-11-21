package com.core.repository.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
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

    /**
     * 提示词风格：存枚举名字符串（"GUIDED"/"FREE"），不存布尔。
     *
     * 这个模块不能依赖 feature:werewolf，所以这里只做字符串搬运，
     * 解析成枚举在 ViewModel 里做。用名字而不是 true/false，是将来加第三档不用改 key。
     * 默认 GUIDED——老用户和已有存档的行为完全不变。
     */
    val promptStyle: Flow<String> = store.data
        .catch { emit(emptyPreferences()) }
        .map { it[PROMPT_STYLE] ?: "GUIDED" }

    suspend fun setPromptStyle(name: String) {
        runCatching { store.edit { it[PROMPT_STYLE] = name } }
    }

    /**
     * 要不要给每个 AI 注入「牌手风格画像」（决策气质 + 读人习惯）。
     *
     * 默认开：这是让 AI 显得会博弈的主要手段。关掉就退回纯陪伴人设的行为，
     * 给想自己用自定义提示词完全接管的人留个出口。
     */
    val playerStyleOn: Flow<Boolean> = store.data
        .catch { emit(emptyPreferences()) }
        .map { it[PLAYER_STYLE_ON] ?: true }

    suspend fun setPlayerStyleOn(enabled: Boolean) {
        runCatching { store.edit { it[PLAYER_STYLE_ON] = enabled } }
    }

    private companion object {
        val AUTO_SPEAK = booleanPreferencesKey("werewolf_auto_speak")
        val SHOW_THINKING = booleanPreferencesKey("werewolf_show_thinking")
        val PROMPT_STYLE = stringPreferencesKey("werewolf_prompt_style")
        val PLAYER_STYLE_ON = booleanPreferencesKey("werewolf_player_style_on")
    }
}
