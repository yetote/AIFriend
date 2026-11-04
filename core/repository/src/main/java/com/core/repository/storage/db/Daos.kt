package com.core.repository.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface FriendDao {

    @Transaction
    @Query("SELECT * FROM friends ORDER BY rowid ASC")
    suspend fun loadAll(): List<FriendWithMessages>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(friend: FriendEntity)

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
