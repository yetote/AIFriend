package com.core.repository.storage

import android.content.Context
import com.core.repository.storage.db.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FriendRepository(context: Context) {

    private val dao = AppDatabase.get(context).friendDao()

    suspend fun load(): List<StoredFriend> = dao.loadAll().map { it.toStored() }

    /** 响应式版本：数据库里好友或消息一变就推新值，首页据此自动刷新。 */
    fun observe(): Flow<List<StoredFriend>> =
        dao.observeAll().map { list -> list.map { it.toStored() } }

    /**
     * 保存好友资料：已存在就只更新字段，不存在才插入。
     *
     * 这里刻意不用「REPLACE 一步到位」：REPLACE 会删掉原行，
     * 连带触发 friend_messages 的级联删除，编辑一次资料聊天记录就没了。
     */
    suspend fun upsert(friend: StoredFriend) {
        val entity = friend.toEntity()
        val updated = dao.updateProfile(
            id = entity.id,
            name = entity.name,
            role = entity.role,
            prompt = entity.prompt,
            brand = entity.brand,
            company = entity.company,
            model = entity.model,
            thinkingEnabled = entity.thinkingEnabled
        )
        if (updated == 0) {
            dao.insertFriend(entity)
        }
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
