package com.core.repository.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import com.core.repository.storage.db.AppDatabase
import com.core.repository.storage.db.ProfileEntity
import com.core.repository.storage.db.RoomMemberEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * 一次性迁移：把旧版 DataStore 中的好友、房间、消息、用户头像搬到 Room。
 * 迁移成功后清掉旧 key，避免重复执行。
 */
object LegacyMigration {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val FRIENDS_KEY = stringPreferencesKey("friends_v1")
    private val ROOMS_KEY = stringPreferencesKey("rooms_v1")
    private val ROOM_MESSAGES_KEY = stringPreferencesKey("room_messages_v1")
    private val USER_AVATAR_KEY = stringPreferencesKey("user_avatar_v1")

    suspend fun migrateIfNeeded(context: Context) {
        val db = AppDatabase.get(context)
        if (db.friendDao().count() > 0 || db.roomDao().count() > 0) return

        val preferences = context.appPreferences.data.first()
        val friends = preferences[FRIENDS_KEY].decodeList<StoredFriend>()
        val rooms = preferences[ROOMS_KEY].decodeList<StoredRoom>()
        val messages = preferences[ROOM_MESSAGES_KEY].decodeList<StoredRoomMessage>()
        val avatarPath = preferences[USER_AVATAR_KEY]

        if (friends.isEmpty() && rooms.isEmpty() && messages.isEmpty() && avatarPath == null) {
            return
        }

        db.withTransaction {
            friends.forEach { friend ->
                db.friendDao().upsert(friend.toEntity())
                friend.messages.forEach { message ->
                    db.friendDao().insertMessage(message.toEntity(friend.id))
                }
            }
            rooms.forEach { room ->
                db.roomDao().upsertRoom(room.toEntity())
                db.roomDao().upsertMembers(
                    room.memberIds.map { friendId ->
                        RoomMemberEntity(
                            roomId = room.id,
                            friendId = friendId,
                            joinSeq = room.memberJoinSeq[friendId] ?: 0L
                        )
                    }
                )
            }
            messages.forEach { db.roomDao().insertMessage(it.toEntity()) }
            avatarPath?.let { db.profileDao().upsert(ProfileEntity(avatarPath = it)) }
        }

        context.appPreferences.edit { preferences ->
            preferences.remove(FRIENDS_KEY)
            preferences.remove(ROOMS_KEY)
            preferences.remove(ROOM_MESSAGES_KEY)
            preferences.remove(USER_AVATAR_KEY)
        }
    }

    private inline fun <reified T> String?.decodeList(): List<T> {
        if (this == null) return emptyList()
        return runCatching { json.decodeFromString<List<T>>(this) }.getOrDefault(emptyList())
    }
}
