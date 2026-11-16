package com.feature.werewolf.route

import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.game.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 显示闸门的映射测试：文字跟着朗读一条条放出来，且不能因私有消息被过滤而错位。
 *
 * 这里是纯映射，不跑引擎。构造的消息刻意把「仅某人可见」夹在公开发言中间，
 * 因为这正是下标最容易出错的地方——先过滤再截断就会在这里翻车。
 */
class SpeechRevealTest {

    private fun public(seat: Int, text: String) = WerewolfMessage(
        day = 1, phase = Phase.DAY_DISCUSSION, seat = seat, kind = MessageKind.SPEECH, text = text,
    )

    private fun privateTo(seat: Int, text: String, audience: Set<Int>) = WerewolfMessage(
        day = 1, phase = Phase.NIGHT_WOLF_CHAT, seat = seat, kind = MessageKind.WOLF_CHAT,
        text = text, audience = audience,
    )

    @Test
    fun `闸门只放出已消费的那几条`() {
        val transcript = listOf(public(1, "一"), public(2, "二"), public(3, "三"))

        assertEquals(0, emergedVisible(transcript, revealed = -1, humanSeat = null).size)
        assertEquals(listOf("一"), emergedVisible(transcript, 0, null).map { it.text })
        assertEquals(listOf("一", "二"), emergedVisible(transcript, 1, null).map { it.text })
        assertEquals("阅读全文", 3, emergedVisible(transcript, Int.MAX_VALUE, null).size)
    }

    @Test
    fun `先截断再过滤 私有消息不会让显示错位`() {
        // 原始下标：0=公开发言 1=仅狼人可见 2=公开发言 3=仅狼人可见
        val transcript = listOf(
            public(1, "公开一"),
            privateTo(2, "狼人私密", setOf(2, 5)),
            public(3, "公开二"),
            privateTo(4, "另一条私密", setOf(4)),
        )

        // 1 号是好人：私有两条都不该看见。闸门到下标 2 时，
        // 应放出「公开一、公开二」——即使中间夹了一条他看不到的消息。
        val asGood = emergedVisible(transcript, revealed = 2, humanSeat = 1)
        assertEquals(listOf("公开一", "公开二"), asGood.map { it.text })

        // 5 号是狼：能看见下标 1 那条，下标 3 那条看不见。
        val asWolf = emergedVisible(transcript, revealed = 3, humanSeat = 5)
        assertEquals(listOf("公开一", "狼人私密", "公开二"), asWolf.map { it.text })
    }

    @Test
    fun `无闸门时观战看到全部消息 参与只看到有权限的`() {
        val transcript = listOf(
            public(1, "公开"),
            privateTo(2, "私密", setOf(2)),
        )
        val all = Int.MAX_VALUE

        assertEquals(2, emergedVisible(transcript, all, null).size)
        assertEquals(listOf("公开"), emergedVisible(transcript, all, 1).map { it.text })
        assertEquals(2, emergedVisible(transcript, all, 2).size)
    }

    @Test
    fun `空消息流任何进度下都是空`() {
        assertTrue(emergedVisible(emptyList(), -1, null).isEmpty())
        assertTrue(emergedVisible(emptyList(), Int.MAX_VALUE, 1).isEmpty())
    }
}
