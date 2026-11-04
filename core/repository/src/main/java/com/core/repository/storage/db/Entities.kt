package com.core.repository.storage.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,
    val prompt: String,
    val brand: String,
    val company: String,
    val model: String,
    val thinkingEnabled: Boolean,
    val avatarPath: String?
)

@Entity(
    tableName = "friend_messages",
    foreignKeys = [
        ForeignKey(
            entity = FriendEntity::class,
            parentColumns = ["id"],
            childColumns = ["friendId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("friendId")]
)
data class FriendMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val friendId: String,
    val role: String,
    val content: String
)

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val prompt: String = "",
    val discussionEnabled: Boolean = false,
    val discussionWindow: Int = 1,
    val sequentialSpeaking: Boolean = false
)

@Entity(
    tableName = "room_members",
    primaryKeys = ["roomId", "friendId"],
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("friendId")]
)
data class RoomMemberEntity(
    val roomId: String,
    val friendId: String,
    val joinSeq: Long
)

@Entity(tableName = "room_messages", indices = [Index("roomId")])
data class RoomMessageEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val senderType: String,
    val senderId: String?,
    val content: String,
    val createdAt: Long,
    val seq: Long
)

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val avatarPath: String?
)

data class FriendWithMessages(
    @Embedded val friend: FriendEntity,
    @Relation(parentColumn = "id", entityColumn = "friendId")
    val messages: List<FriendMessageEntity>
)

data class RoomWithMembers(
    @Embedded val room: RoomEntity,
    @Relation(parentColumn = "id", entityColumn = "roomId")
    val members: List<RoomMemberEntity>
)
