package com.feature.agent.data
import com.core.ai.model.ChatMessage
import com.feature.agent.model.Friend
import org.junit.Assert.assertEquals
import org.junit.Test

class FriendMapperTest {

    @Test
    fun `friend survives storage mapping round trip`() {
        val friend = Friend(
            id = "friend-1",
            name = "小谋",
            role = "产品策略顾问",
            prompt = "回答先给结论，再给理由。",
            brand = "Deepseek",
            company = "深度求索",
            model = "deepseek-flash",
            thinkingEnabled = true,
            avatarPath = "/data/user/0/com.yetote.aifriend/files/avatars/friend-1_1.jpg",
            messages = listOf(
                ChatMessage.user("帮我起个名字"),
                ChatMessage.assistant("就叫「小谋」吧")
            )
        )

        assertEquals(friend, friend.toStored().toFriend())
    }
}
