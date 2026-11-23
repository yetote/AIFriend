package com.feature.werewolf.engine

import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自爆的记账。
 *
 * 三个入口共用 [recordExplode]：AI 写标记、AI 被点名后插爆、人类点牌桌上的按钮。
 * 人类那条提交的是一句没有正文的标记，所以这里最容易出的事故是
 * 「牌桌上出现一条 3 号：后面什么都没有」的空发言。
 */
class WerewolfExplodeRecordTest {

    private val roles = listOf(
        WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF,
        WerewolfRole.SEER, WerewolfRole.WITCH,
        WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
    )

    private fun game(): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(composition = roles.groupingBy { it }.eachCount()),
        roster = List(roles.size) { RosterEntry("p${it + 1}", "${it + 1}号") },
        roles = roles,
    )

    private fun day(): WerewolfGame = game().copy(day = 1, phase = Phase.DAY_DISCUSSION)

    @Test
    fun `没留正文的自爆要补一句我自爆，不能是空发言`() {
        val exploded = WerewolfTable(day()).recordExplode(1)

        val said = exploded.transcript.single { it.seat == 1 }
        assertEquals(MessageKind.SPEECH, said.kind)
        assertEquals("我自爆。", said.text)
    }

    @Test
    fun `狼自己写了话就保留，不被那句默认文案顶掉`() {
        val exploded = WerewolfTable(day()).recordExplode(1, text = " 别投 2 号了，我爆。 ")

        assertEquals("别投 2 号了，我爆。", exploded.transcript.single { it.seat == 1 }.text)
    }

    @Test
    fun `自爆之后牌桌直接是黑夜，并且补上了上帝播报`() {
        val exploded = WerewolfTable(day()).recordExplode(1)

        assertFalse(exploded.game.playerAt(1).alive)
        assertEquals("自爆就是直接进夜", Phase.NIGHT_WOLF_CHAT, exploded.game.phase)
        assertEquals("天数要往前走一夜", 2, exploded.game.day)
        assertTrue(
            "播报必须说明「直接进入黑夜」，否则用户以为漏了投票",
            exploded.transcript.any { it.kind == MessageKind.DIVINE && it.text.contains("自爆") },
        )
        assertFalse(
            "这一天不该再出现放逐投票",
            exploded.transcript.any { it.kind == MessageKind.BALLOT },
        )
    }

    @Test
    fun `自爆这笔账要留在复盘里`() {
        // 插爆判断关着深度思考，没有思维链可留。收益和理由走 reasoning 落档，
        // 复盘点开那条发言的「思考」才能看到「他为什么选在那一刻爆」。
        val exploded = WerewolfTable(day()).recordExplode(
            seat = 1,
            reasoning = "自爆收益：换掉今天的投票\n判断：队友要被冲出去了",
        )

        val said = exploded.transcript.single { it.seat == 1 }
        assertTrue("收益要跟着发言走", said.reasoning.contains("自爆收益"))
        assertTrue("不能只剩一句「我自爆」", said.reasoning.contains("队友要被冲出去了"))
    }

    @Test
    fun `好人和死人点不动自爆`() {
        // 3 号是预言家，wolfExplode 会拒绝；recordExplode 里没有兜底，
        // 所以调用方（控制器）必须先问 canExplode——这条测的是那个前提
        assertFalse(day().canExplode(3))

        val dead = day().copy(
            players = day().players.map { if (it.seat == 1) it.copy(alive = false) else it },
        )
        assertFalse("出局的人不能再爆一次", dead.canExplode(1))
        assertFalse("夜里没有「打断白天」这回事", game().canExplode(1))
    }
}
