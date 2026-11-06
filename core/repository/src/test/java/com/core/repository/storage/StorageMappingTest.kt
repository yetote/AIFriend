package com.core.repository.storage

import com.core.repository.storage.db.FriendMessageEntity
import com.core.repository.storage.db.FriendWithMessages
import com.core.repository.storage.db.RoomMemberEntity
import com.core.repository.storage.db.RoomWithMembers
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageMappingTest {

    @Test
    fun `friend entity round trip keeps message order`() {
        val friend = StoredFriend(
            id = "f1",
            name = "小谋",
            role = "顾问",
            prompt = "设定",
            brand = "Deepseek",
            company = "深度求索",
            model = "deepseek-flash",
            thinkingEnabled = true,
            avatarPath = "/avatars/f1_1.jpg",
            messages = listOf(
                StoredMessage(role = "user", content = "第一条"),
                StoredMessage(role = "assistant", content = "第二条")
            )
        )

        val withMessages = FriendWithMessages(
            friend = friend.toEntity(),
            messages = listOf(
                FriendMessageEntity(id = 1, friendId = "f1", role = "user", content = "第一条"),
                FriendMessageEntity(id = 2, friendId = "f1", role = "assistant", content = "第二条")
            )
        )

        assertEquals(friend, withMessages.toStored())
    }

    @Test
    fun `room members map back to memberIds and joinSeq`() {
        val room = StoredRoom(
            id = "r1",
            name = "评审团",
            memberIds = listOf("a", "b"),
            createdAt = 100L,
            prompt = "你们是评审团，各自给结论",
            discussionEnabled = true,
            discussionWindow = 2,
            pinned = true,
            avatarPath = "/avatars/r1_1.jpg",
            autoChatEnabled = true,
            autoChatTurns = 10,
            memberJoinSeq = mapOf("a" to 0L, "b" to 5L)
        )

        val withMembers = RoomWithMembers(
            room = room.toEntity(),
            members = listOf(
                RoomMemberEntity(roomId = "r1", friendId = "a", joinSeq = 0L),
                RoomMemberEntity(roomId = "r1", friendId = "b", joinSeq = 5L)
            )
        )

        assertEquals(room, withMembers.toStored())
    }
}
