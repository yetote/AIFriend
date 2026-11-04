package com.core.repository.storage

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class FriendStoreSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `friends survive json round trip`() {
        val friends = listOf(
            StoredFriend(
                id = "1",
                name = "小谋",
                role = "产品策略顾问",
                prompt = "回答先给结论",
                brand = "Deepseek",
                company = "深度求索",
                model = "deepseek-flash",
                thinkingEnabled = true,
                messages = listOf(
                    StoredMessage(role = "user", content = "你好"),
                    StoredMessage(role = "assistant", content = "你好呀")
                )
            ),
            StoredFriend(
                id = "2",
                name = "豆豆",
                role = "",
                prompt = "",
                brand = "豆包",
                company = "字节跳动",
                model = "doubao-seed-evolving"
            )
        )

        val raw = json.encodeToString(friends)

        assertEquals(friends, json.decodeFromString<List<StoredFriend>>(raw))
    }

    @Test
    fun `unknown fields are tolerated for forward compatibility`() {
        val raw = """
            [{"id":"1","name":"小谋","role":"","prompt":"","brand":"Deepseek",
              "company":"深度求索","model":"deepseek-flash","futureField":123}]
        """.trimIndent()

        val decoded = json.decodeFromString<List<StoredFriend>>(raw)

        assertEquals("小谋", decoded.single().name)
    }
}
