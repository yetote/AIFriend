package com.core.repository.storage

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomStoreSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `rooms and messages survive json round trip`() {
        val rooms = listOf(
            StoredRoom(
                id = "r1",
                name = "产品评审团",
                memberIds = listOf("a", "b"),
                createdAt = 100L,
                memberJoinSeq = mapOf("a" to 0L, "b" to 42L)
            )
        )
        val messages = listOf(
            StoredRoomMessage(
                id = "m1",
                roomId = "r1",
                senderType = "user",
                content = "大家怎么看？",
                createdAt = 101L,
                seq = 1L
            ),
            StoredRoomMessage(
                id = "m2",
                roomId = "r1",
                senderType = "ai",
                senderId = "a",
                content = "我觉得可行",
                createdAt = 102L,
                seq = 2L
            )
        )

        assertEquals(rooms, json.decodeFromString<List<StoredRoom>>(json.encodeToString(rooms)))
        assertEquals(messages, json.decodeFromString<List<StoredRoomMessage>>(json.encodeToString(messages)))
    }

    @Test
    fun `unknown fields are tolerated`() {
        val raw = """
            [{"id":"r1","name":"x","memberIds":["a"],"createdAt":1,"futureField":true}]
        """.trimIndent()

        assertEquals("x", json.decodeFromString<List<StoredRoom>>(raw).single().name)
    }
}
