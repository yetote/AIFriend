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
