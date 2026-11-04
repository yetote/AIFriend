package com.core.repository.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredRoom(
    val id: String,
    val name: String,
    val memberIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val memberJoinSeq: Map<String, Long> = emptyMap()
)

/**
 * 聊天室消息。senderType 取值 "user" / "ai"，senderId 仅 AI 消息有值。
 */
@Serializable
data class StoredRoomMessage(
    val id: String,
    val roomId: String,
    val senderType: String,
    val senderId: String? = null,
    val content: String,
    val createdAt: Long = 0L,
    val seq: Long = 0L
)

/**
 * 聊天室与房间消息的本地存储，和好友数据共用同一个 DataStore 文件，但使用不同的 key。
 */
class RoomStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun loadRooms(): List<StoredRoom> {
        val preferences = context.appPreferences.data.first()
        val raw = preferences[ROOMS_KEY] ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredRoom>>(raw)
        }.getOrElse { emptyList() }
    }

    suspend fun saveRooms(rooms: List<StoredRoom>) {
        val raw = json.encodeToString(rooms)
        context.appPreferences.edit { preferences ->
            preferences[ROOMS_KEY] = raw
        }
    }

    suspend fun loadMessages(): List<StoredRoomMessage> {
        val preferences = context.appPreferences.data.first()
        val raw = preferences[ROOM_MESSAGES_KEY] ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredRoomMessage>>(raw)
        }.getOrElse { emptyList() }
    }

    suspend fun saveMessages(messages: List<StoredRoomMessage>) {
        val raw = json.encodeToString(messages)
        context.appPreferences.edit { preferences ->
            preferences[ROOM_MESSAGES_KEY] = raw
        }
    }

    private companion object {
        val ROOMS_KEY = stringPreferencesKey("rooms_v1")
        val ROOM_MESSAGES_KEY = stringPreferencesKey("room_messages_v1")
    }
}
