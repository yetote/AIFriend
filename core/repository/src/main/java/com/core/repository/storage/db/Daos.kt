package com.core.repository.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {

    @Transaction
    @Query("SELECT * FROM friends ORDER BY rowid ASC")
    suspend fun loadAll(): List<FriendWithMessages>

    /**
     * 列表页用的响应式版本。
     *
     * 之前首页靠「退出时手工调一次 refresh()」来同步，
     * 只要有一条返回路径没走到那个回调（改完头像、改完资料、系统返回键），
     * 首页就还是旧数据。改成监听 friends / friend_messages 两张表的变更，
     * 任何写入（换头像、编辑资料、发消息）都会自动把新列表推过来。
     */
    @Transaction
    @Query("SELECT * FROM friends ORDER BY rowid ASC")
    fun observeAll(): Flow<List<FriendWithMessages>>

    /**
     * 只更新好友自身的资料字段，绝不碰 friend_messages。
     *
     * 不能用 @Insert(onConflict = REPLACE)：SQLite 的 REPLACE 是「先 DELETE 再 INSERT」，
     * 而 friend_messages 对 friends 是 ON DELETE CASCADE，
     * 于是每次编辑资料都会把该好友的聊天记录整条连带删掉。
     * 这里显式写成 UPDATE，行还在、外键不触发级联，消息自然留着。
     */
    @Query(
        "UPDATE friends SET name = :name, role = :role, prompt = :prompt, " +
            "brand = :brand, company = :company, model = :model, " +
            "thinkingEnabled = :thinkingEnabled WHERE id = :id"
    )
    suspend fun updateProfile(
        id: String,
        name: String,
        role: String,
        prompt: String,
        brand: String,
        company: String,
        model: String,
        thinkingEnabled: Boolean
    ): Int

    /** 新建好友时才走插入。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFriend(friend: FriendEntity)

    @Insert
    suspend fun insertMessage(message: FriendMessageEntity)

    @Query("UPDATE friends SET avatarPath = :path WHERE id = :id")
    suspend fun updateAvatar(id: String, path: String)

    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM friends")
    suspend fun count(): Int
}

@Dao
interface RoomDao {

    @Transaction
    @Query("SELECT * FROM rooms ORDER BY createdAt ASC")
    suspend fun loadAll(): List<RoomWithMembers>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoom(room: RoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<RoomMemberEntity>)

    @Query("DELETE FROM room_members WHERE roomId = :roomId")
    suspend fun clearMembers(roomId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: RoomMessageEntity)

    @Query("SELECT * FROM room_messages")
    suspend fun loadMessages(): List<RoomMessageEntity>

    @Query(
        "SELECT * FROM room_messages WHERE roomId = :roomId " +
            "ORDER BY createdAt ASC, seq ASC"
    )
    suspend fun loadAllMessages(roomId: String): List<RoomMessageEntity>

    @Query(
        "SELECT * FROM room_messages WHERE roomId = :roomId " +
            "ORDER BY createdAt DESC, seq DESC LIMIT :limit"
    )
    suspend fun loadRecentMessages(roomId: String, limit: Int): List<RoomMessageEntity>

    @Query(
        "SELECT * FROM room_messages WHERE roomId = :roomId " +
            "AND (createdAt < :beforeCreatedAt OR (createdAt = :beforeCreatedAt AND seq < :beforeSeq)) " +
            "ORDER BY createdAt DESC, seq DESC LIMIT :limit"
    )
    suspend fun loadMessagesBefore(
        roomId: String,
        beforeCreatedAt: Long,
        beforeSeq: Long,
        limit: Int
    ): List<RoomMessageEntity>

    @Query("SELECT MAX(seq) FROM room_messages")
    suspend fun maxSeq(): Long?

    @Query("SELECT COUNT(*) FROM room_messages WHERE roomId = :roomId")
    suspend fun countMessages(roomId: String): Int

    @Query("DELETE FROM room_messages WHERE senderType = 'ai' AND senderId = :friendId")
    suspend fun deleteMessagesBySender(friendId: String)

    @Query("DELETE FROM room_members WHERE friendId = :friendId")
    suspend fun removeMemberEverywhere(friendId: String)

    @Query("DELETE FROM room_messages WHERE roomId = :roomId")
    suspend fun deleteMessagesForRoom(roomId: String)

    @Query("DELETE FROM rooms WHERE id = :roomId")
    suspend fun deleteRoom(roomId: String)

    @Query("SELECT COUNT(*) FROM rooms")
    suspend fun count(): Int
}

@Dao
interface ProfileDao {

    @Query("SELECT avatarPath FROM profile WHERE id = 1")
    suspend fun avatarPath(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)
}
