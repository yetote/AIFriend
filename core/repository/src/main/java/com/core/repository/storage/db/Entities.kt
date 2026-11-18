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
    val avatarPath: String?,
    /** 朗读音色（MiniMax voice_id）；可空列，旧库 migration 直接加列。 */
    val voiceId: String? = null,
    /** 朗读用哪套 TTS 配置（TTSProvider.id）；可空列。 */
    val ttsConfigId: String? = null
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
    val content: String,
    /**
     * 深度思考的思维链，只有 assistant 消息可能有。
     *
     * 可空列：老数据、不思考的模型、用户自己的发言都没有这一段，
     * 空字符串会让界面上多一块空的「思考过程」标题。
     */
    val reasoning: String? = null
)

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val prompt: String = "",
    val discussionEnabled: Boolean = false,
    val discussionWindow: Int = 1,
    /** 旧字段，已废弃（发言顺序改为客户端随机），保留列以兼容历史库。 */
    val sequentialSpeaking: Boolean = false,
    val pinned: Boolean = false,
    val avatarPath: String? = null,
    /** 用户发言后，是否让成员继续自由对话。 */
    val autoChatEnabled: Boolean = false,
    /** 自由对话接话次数上限（一次接话 = 一轮）。 */
    val autoChatTurns: Int = 3
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
    val seq: Long,
    /** 逗号分隔的 @ 成员 ID；null 表示全员可见。 */
    val targetIds: String? = null,
    /** 这一条 AI 发言的思维链，可空。和 1:1 的消息表保持同一套语义。 */
    val reasoning: String? = null
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
