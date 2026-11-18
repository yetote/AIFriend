package com.feature.werewolf.route

import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.game.Phase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 舞台时间（背景 / 第几天 / 白天黑夜）必须跟朗读走，不能跟引擎走。
 *
 * 引擎一口气跑到天亮，语音还在念夜里的话；这时舞台要是直接读 `game.phase`，
 * 就会出现「声音还在夜里，背景已经泛白」。这里把这个映射钉死，改坏会立刻红。
 */
class StageMomentTest {

    private val nightChat = WerewolfMessage(
        day = 1, phase = Phase.NIGHT_WOLF_CHAT, seat = 2, kind = MessageKind.WOLF_CHAT,
        text = "今晚刀 3 号", audience = setOf(2),
    )
    private val wolfVote = WerewolfMessage(
        day = 1, phase = Phase.NIGHT_WOLF, seat = 2, kind = MessageKind.NIGHT_ACT,
        text = "2 号选择 3 号", audience = setOf(2),
    )
    private val dawn = WerewolfMessage(
        day = 2, phase = Phase.DAY_DISCUSSION, seat = null, kind = MessageKind.DIVINE,
        text = "第2天 天亮：3 号倒牌",
    )
    private val transcript = listOf(nightChat, wolfVote, dawn)

    @Test
    fun `朗读没追平时 舞台停在已播到的那条消息的阶段`() {
        // 引擎已经走到第 2 天白天，但第 1 夜的第一句才念完。
        val moment = stageMoment(
            transcript = transcript,
            revealed = 0,
            gated = true,
            fallbackDay = 2,
            fallbackPhase = Phase.DAY_DISCUSSION,
        )

        assertEquals(1, moment.day)
        assertEquals(Phase.NIGHT_WOLF_CHAT, moment.phase)
    }

    @Test
    fun `一条都没播完时给开头那条 而不是引擎的当前阶段`() {
        val moment = stageMoment(transcript, revealed = -1, gated = true, fallbackDay = 2, fallbackPhase = Phase.DAY_DISCUSSION)

        assertEquals(1, moment.day)
        assertEquals(Phase.NIGHT_WOLF_CHAT, moment.phase)
    }

    @Test
    fun `朗读追平后回到实时阶段 否则界面会停在上一阶段不动`() {
        val moment = stageMoment(
            transcript = transcript,
            revealed = transcript.lastIndex,
            gated = true,
            fallbackDay = 2,
            fallbackPhase = Phase.DAY_VOTE,
        )

        assertEquals(2, moment.day)
        assertEquals(Phase.DAY_VOTE, moment.phase)
    }

    @Test
    fun `没开朗读或朗读不可用时 舞台就是实时阶段`() {
        val moment = stageMoment(transcript, revealed = 0, gated = false, fallbackDay = 2, fallbackPhase = Phase.DAY_DISCUSSION)

        assertEquals(2, moment.day)
        assertEquals(Phase.DAY_DISCUSSION, moment.phase)
    }

    @Test
    fun `最后一条正在念的时候 舞台还停在它上面`() {
        // 投票结束后的遗言最容易撞上这条：`revealed` 是「取出即前进」，
        // 念最后一条遗言时它已经等于 lastIndex，看着像追平了；但引擎早在
        // 遗言生成完的那一刻就把状态推到了入夜。只比 lastIndex 的话，
        // 遗言还没念完，背景已经黑掉——用户看到的就是「投票结束先切黑夜」。
        val moment = stageMoment(
            transcript = transcript,
            revealed = transcript.lastIndex,
            gated = true,
            fallbackDay = 2,
            fallbackPhase = Phase.NIGHT_WOLF,
            speechBusy = true,
        )

        assertEquals(2, moment.day)
        assertEquals(Phase.DAY_DISCUSSION, moment.phase)
    }

    @Test
    fun `喇叭忙但进度越界时 夹到最后一条而不是崩`() {
        val moment = stageMoment(
            transcript = transcript,
            revealed = Int.MAX_VALUE,
            gated = true,
            fallbackDay = 2,
            fallbackPhase = Phase.NIGHT_WOLF,
            speechBusy = true,
        )

        assertEquals(2, moment.day)
        assertEquals(Phase.DAY_DISCUSSION, moment.phase)
    }

    @Test
    fun `空 transcript 时用实时阶段 而不是假装有过去`() {
        val moment = stageMoment(emptyList(), revealed = -1, gated = true, fallbackDay = 1, fallbackPhase = null)

        assertEquals(1, moment.day)
        assertEquals(null, moment.phase)
    }

    @Test
    fun `无闸门标记 MAX_VALUE 也不会越界`() {
        val moment = stageMoment(transcript, revealed = Int.MAX_VALUE, gated = true, fallbackDay = 3, fallbackPhase = Phase.DAY_DISCUSSION)

        assertEquals(3, moment.day)
        assertEquals(Phase.DAY_DISCUSSION, moment.phase)
    }
}
