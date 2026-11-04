package com.core.repository.storage

import android.content.Context
import com.core.repository.storage.db.AppDatabase

class FriendRepository(context: Context) {

    private val dao = AppDatabase.get(context).friendDao()

    suspend fun load(): List<StoredFriend> = dao.loadAll().map { it.toStored() }

    suspend fun upsert(friend: StoredFriend) {
        dao.upsert(friend.toEntity())
    }

    suspend fun appendMessage(friendId: String, message: StoredMessage) {
        dao.insertMessage(message.toEntity(friendId))
    }

    suspend fun updateAvatar(friendId: String, path: String) {
        dao.updateAvatar(friendId, path)
    }

    suspend fun delete(friendId: String) {
        dao.delete(friendId)
    }
}
