package com.core.repository.storage

import android.content.Context
import androidx.room.withTransaction
import com.core.repository.storage.db.AppDatabase
import com.core.repository.storage.db.RoomMemberEntity

class RoomRepository(context: Context) {

    private val db = AppDatabase.get(context)
    private val dao = db.roomDao()

    suspend fun loadRooms(): List<StoredRoom> = dao.loadAll().map { it.toStored() }

    suspend fun loadMessages(): List<StoredRoomMessage> = dao.loadMessages().map { it.toStored() }

    /** 整屋消息，按时间正序，供导出使用。 */
    suspend fun loadAllMessages(roomId: String): List<StoredRoomMessage> =
        dao.loadAllMessages(roomId).map { it.toStored() }

    /** 最近 [limit] 条消息，按时间正序返回。 */
    suspend fun loadRecentMessages(roomId: String, limit: Int): List<StoredRoomMessage> =
        dao.loadRecentMessages(roomId, limit)
            .map { it.toStored() }
            .sortedWith(compareBy({ it.createdAt }, { it.seq }))

    /** 早于指定消息的 [limit] 条，按时间正序返回。 */
    suspend fun loadMessagesBefore(
        roomId: String,
        beforeCreatedAt: Long,
        beforeSeq: Long,
        limit: Int
    ): List<StoredRoomMessage> =
        dao.loadMessagesBefore(roomId, beforeCreatedAt, beforeSeq, limit)
            .map { it.toStored() }
            .sortedWith(compareBy({ it.createdAt }, { it.seq }))

    suspend fun maxMessageSeq(): Long = dao.maxSeq() ?: 0L

    suspend fun countMessages(roomId: String): Int = dao.countMessages(roomId)

    /**
     * 房间元信息 + 成员关系一起覆盖写入。
     */
    suspend fun upsert(room: StoredRoom) {
        db.withTransaction {
            dao.upsertRoom(room.toEntity())
            dao.clearMembers(room.id)
            dao.upsertMembers(
                room.memberIds.map { friendId ->
                    RoomMemberEntity(
                        roomId = room.id,
                        friendId = friendId,
                        joinSeq = room.memberJoinSeq[friendId] ?: 0L
                    )
                }
            )
        }
    }

    suspend fun appendMessage(message: StoredRoomMessage) {
        dao.insertMessage(message.toEntity())
    }

    /** 删除好友时，清掉它在所有房间里的消息。 */
    suspend fun deleteMessagesBySender(friendId: String) {
        dao.deleteMessagesBySender(friendId)
    }

    /** 清空房间消息，同时把成员 joinSeq 重置为 0，整体一个事务。 */
    suspend fun clearMessages(room: StoredRoom) {
        db.withTransaction {
            dao.deleteMessagesForRoom(room.id)
            dao.upsertRoom(room.toEntity())
            dao.clearMembers(room.id)
            dao.upsertMembers(
                room.memberIds.map { friendId ->
                    RoomMemberEntity(
                        roomId = room.id,
                        friendId = friendId,
                        joinSeq = room.memberJoinSeq[friendId] ?: 0L
                    )
                }
            )
        }
    }

    /** 删除好友时，把它从所有房间成员里移除。 */
    suspend fun removeMemberEverywhere(friendId: String) {
        dao.removeMemberEverywhere(friendId)
    }

    /** 删除房间及其全部消息（成员表通过外键级联删除）。 */
    suspend fun delete(roomId: String) {
        db.withTransaction {
            dao.deleteMessagesForRoom(roomId)
            dao.deleteRoom(roomId)
        }
    }
}
