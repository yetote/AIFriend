package com.core.repository.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 持久化用的数据模型，故意和 UI / 业务模型解耦：
 * 以后换 Room 或调整字段时，存储结构可以独立演进。
 */
@Serializable
data class StoredMessage(
    val role: String,
    val content: String
)

@Serializable
data class StoredFriend(
    val id: String,
    val name: String,
    val role: String,
    val prompt: String,
    val brand: String,
    val company: String,
    val model: String,
    val thinkingEnabled: Boolean = false,
    val messages: List<StoredMessage> = emptyList()
)

/**
 * 好友 + 1:1 聊天记录的本地存储。
 * 当前数据量小，采用「整份 JSON 快照」写入 Preferences DataStore；
 * 等消息量大了再换成 Room（按 friendId 建表 + 分页）。
 */
class FriendStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun load(): List<StoredFriend> {
        val preferences = context.appPreferences.data.first()
        val raw = preferences[FRIENDS_KEY] ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredFriend>>(raw)
        }.getOrElse { emptyList() }
    }

    suspend fun save(friends: List<StoredFriend>) {
        val raw = json.encodeToString(friends)
        context.appPreferences.edit { preferences ->
            preferences[FRIENDS_KEY] = raw
        }
    }

    private companion object {
        val FRIENDS_KEY = stringPreferencesKey("friends_v1")
    }
}
