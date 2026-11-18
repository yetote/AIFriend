package com.feature.werewolf.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 牌桌音色分配。
 *
 * 规则只有三条（本人配过的最优先、没配的挑没被占用的、挑不到就回落），
 * 但每一条错了都很难在界面上看出来：八个人同声，用户只会觉得「这游戏声音怪」，
 * 不会想到是分配逻辑把默认音色发给了所有人。
 */
class SeatVoicesTest {

    private val seats = (1..8).toList()

    @Test
    fun `每个没配音色的座位都拿到不同的音色`() {
        val voices = SeatVoices.assign(
            seats = seats,
            humanSeat = null,
            explicit = emptyMap(),
            providerOf = { "minimax" },
        )

        val assigned = seats.mapNotNull { voices[it] }
        assertEquals("八个座位都该分到音色", 8, assigned.size)
        assertEquals("声音不能重复", assigned.size, assigned.toSet().size)
    }

    @Test
    fun `本人配过的音色优先，不会被游戏改掉`() {
        val voices = SeatVoices.assign(
            seats = seats,
            humanSeat = null,
            explicit = mapOf(3 to "female-yujie"),
            providerOf = { "minimax" },
        )

        assertEquals("用户自己选的必须保留", "female-yujie", voices[3])
        assertEquals(
            "别人不能分到同一个",
            seats.count { voices[it] == "female-yujie" },
            1,
        )
    }

    @Test
    fun `人类座位不占音色`() {
        val voices = SeatVoices.assign(
            seats = seats,
            humanSeat = 5,
            explicit = emptyMap(),
            providerOf = { "minimax" },
        )

        assertNull("自己不朗读，不需要音色", voices[5])
        val others = seats.filter { it != 5 }.mapNotNull { voices[it] }
        assertEquals(7, others.size)
        assertEquals("剩下的仍然各不相同", others.size, others.toSet().size)
    }

    @Test
    fun `不同服务商各自分配，不会把 A 家的音色给 B 家`() {
        // 2、4、6 号的好友配的是腾讯云，其余是 MiniMax
        val tencentSeats = setOf(2, 4, 6)
        val voices = SeatVoices.assign(
            seats = seats,
            humanSeat = null,
            explicit = emptyMap(),
            providerOf = { seat -> if (seat in tencentSeats) "tencent" else "minimax" },
        )

        tencentSeats.forEach { seat ->
            val voice = voices[seat]
            assertNotNull("腾讯云座位应该有音色", voice)
            assertTrue("$seat 号拿到的是别家的音色：$voice", voice!!.all { it.isDigit() })
        }
        (seats - tencentSeats).forEach { seat ->
            val voice = voices[seat]
            assertNotNull(voice)
            assertTrue("$seat 号拿到了腾讯云的数字音色：$voice", voice!!.any { !it.isDigit() })
        }
    }

    @Test
    fun `音色分完就回落默认，不会编造 ID`() {
        // 讯飞在线合成目前只列了 8 个发音人，坐满 10 个人就必然分不够。
        // 这时多出来的座位给 null（引擎走默认音色）——编一个不存在的 vcn 会让合成直接失败
        val many = (1..10).toList()
        val voices = SeatVoices.assign(
            seats = many,
            humanSeat = null,
            explicit = emptyMap(),
            providerOf = { "iflytek" },
        )

        val assigned = many.mapNotNull { voices[it] }
        assertEquals("能分的都分了", 8, assigned.size)
        assertEquals("分出去的不重复", assigned.size, assigned.toSet().size)
        assertEquals("其余走默认音色", 2, many.count { voices[it] == null })
    }
}
