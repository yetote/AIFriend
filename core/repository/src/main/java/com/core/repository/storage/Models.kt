package com.core.repository.storage

import com.core.repository.storage.db.FriendEntity
import com.core.repository.storage.db.FriendMessageEntity
import com.core.repository.storage.db.FriendWithMessages
import com.core.repository.storage.db.RoomEntity
import com.core.repository.storage.db.RoomMemberEntity
import com.core.repository.storage.db.RoomMessageEntity
import com.core.repository.storage.db.RoomWithMembers
import kotlinx.serialization.Serializable

/**
 * 业务侧使用的数据模型（同时用于旧 DataStore 数据迁移）。
 * Room 实体在 [com.core.repository.storage.db] 里，两者通过下面的扩展函数互转。
 */
@Serializable
data class StoredMessage(
    val role: String,
    val content: String,
    /** 深度思考的思维链，只有 AI 回复可能有。@Serializable 默认值兼容旧 JSON。 */
    val reasoning: String? = null
)

@Serializable
data class StoredFriend(
    val id: String,
    val name: String,
    val role: String,
    val prompt: String,
    val brand: String,
    val company: String,
    val model: String,
    val thinkingEnabled: Boolean = false,
    val avatarPath: String? = null,
    /** 朗读音色（MiniMax voice_id）。null = 引擎默认；@Serializable 默认值兼容旧 JSON。 */
    val voiceId: String? = null,
    /** 朗读用哪套 TTS 配置（TTSProvider.id）。null = 默认配置。 */
    val ttsConfigId: String? = null,
    val messages: List<StoredMessage> = emptyList()
)

@Serializable
data class StoredRoom(
    val id: String,
    val name: String,
    val memberIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val prompt: String = "",
    val discussionEnabled: Boolean = false,
    val discussionWindow: Int = 1,
    val pinned: Boolean = false,
    val avatarPath: String? = null,
    val autoChatEnabled: Boolean = false,
    val autoChatTurns: Int = 3,
    val memberJoinSeq: Map<String, Long> = emptyMap()
)

@Serializable
data class StoredRoomMessage(
    val id: String,
    val roomId: String,
    val senderType: String,
    val senderId: String? = null,
    val content: String,
    val createdAt: Long = 0L,
    val seq: Long = 0L,
    val targetIds: Set<String>? = null,
    /** 这一条 AI 发言的思维链，可空。 */
    val reasoning: String? = null
)

internal fun StoredFriend.toEntity(): FriendEntity = FriendEntity(
    id = id,
    name = name,
    role = role,
    prompt = prompt,
    brand = brand,
    company = company,
    model = model,
    thinkingEnabled = thinkingEnabled,
    avatarPath = avatarPath,
    voiceId = voiceId,
    ttsConfigId = ttsConfigId
)

internal fun StoredMessage.toEntity(friendId: String): FriendMessageEntity = FriendMessageEntity(
    friendId = friendId,
    role = role,
    content = content,
    reasoning = reasoning
)

internal fun FriendWithMessages.toStored(): StoredFriend = StoredFriend(
    id = friend.id,
    name = friend.name,
    role = friend.role,
    prompt = friend.prompt,
    brand = friend.brand,
    company = friend.company,
    model = friend.model,
    thinkingEnabled = friend.thinkingEnabled,
    avatarPath = friend.avatarPath,
    voiceId = friend.voiceId,
    ttsConfigId = friend.ttsConfigId,
    messages = messages.sortedBy { it.id }.map {
        StoredMessage(role = it.role, content = it.content, reasoning = it.reasoning)
    }
)

internal fun StoredRoom.toEntity(): RoomEntity = RoomEntity(
    id = id,
    name = name,
    createdAt = createdAt,
    prompt = prompt,
    discussionEnabled = discussionEnabled,
    discussionWindow = discussionWindow,
    pinned = pinned,
    avatarPath = avatarPath,
    autoChatEnabled = autoChatEnabled,
    autoChatTurns = autoChatTurns
)

internal fun RoomWithMembers.toStored(): StoredRoom = StoredRoom(
    id = room.id,
    name = room.name,
    memberIds = members.sortedWith(compareBy({ it.joinSeq }, { it.friendId }))
        .map { it.friendId },
    createdAt = room.createdAt,
    prompt = room.prompt,
    discussionEnabled = room.discussionEnabled,
    discussionWindow = room.discussionWindow,
    pinned = room.pinned,
    avatarPath = room.avatarPath,
    autoChatEnabled = room.autoChatEnabled,
    autoChatTurns = room.autoChatTurns,
    memberJoinSeq = members.associate { it.friendId to it.joinSeq }
)

internal fun StoredRoomMessage.toEntity(): RoomMessageEntity = RoomMessageEntity(
    id = id,
    roomId = roomId,
    senderType = senderType,
    senderId = senderId,
    content = content,
    createdAt = createdAt,
    seq = seq,
    targetIds = targetIds?.joinToString(","),
    reasoning = reasoning
)

internal fun RoomMessageEntity.toStored(): StoredRoomMessage = StoredRoomMessage(
    id = id,
    roomId = roomId,
    senderType = senderType,
    senderId = senderId,
    content = content,
    createdAt = createdAt,
    seq = seq,
    targetIds = targetIds?.split(",")?.filter { it.isNotBlank() }?.toSet(),
    reasoning = reasoning
)
