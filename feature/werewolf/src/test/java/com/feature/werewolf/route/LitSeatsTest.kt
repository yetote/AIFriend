package com.feature.werewolf.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「此刻谁在说话」的点亮规则。
 *
 * 这条规则存在的理由：引擎比朗读跑得快。引擎已经推进到问 5 号投票了，
 * 喇叭还在念 3 号刚才的发言——牌桌上该亮的是 3 号，因为那是用户此刻
 * 真正在听的人。只亮引擎的 `progress.seat` 就会出现「亮着的人没出声，
 * 出声的人没亮」。
 */
class LitSeatsTest {

    @Test
    fun `朗读的那个人要亮起来`() {
        assertEquals(setOf(3), litSeats(actorSeat = null, speakingSeat = 3, running = true))
    }

    @Test
    fun `引擎在问的人和喇叭在念的人同时亮`() {
        // 3 号的话还在念，5 号已经在组织自己的回答
        assertEquals(setOf(3, 5), litSeats(actorSeat = 5, speakingSeat = 3, running = true))
    }

    @Test
    fun `同一个人不会重复`() {
        assertEquals(setOf(4), litSeats(actorSeat = 4, speakingSeat = 4, running = true))
    }

    @Test
    fun `没在跑的时候一个都不亮`() {
        // 复盘页上留一个还在呼吸的头像，会让人以为这局又开始了
        assertTrue(litSeats(actorSeat = 2, speakingSeat = 3, running = false).isEmpty())
    }

    @Test
    fun `两个都为 null 时是空集`() {
        assertTrue(litSeats(null, null, running = true).isEmpty())
    }
}
