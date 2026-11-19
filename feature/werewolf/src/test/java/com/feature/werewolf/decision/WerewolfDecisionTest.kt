package com.feature.werewolf.decision

import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 决策规格的测试。
 *
 * 规格里的 `validate` 是喂给状态机之前的最后一道关。它漏判的后果不是「AI 说了句怪话」，
 * 而是 `WerewolfGame` 抛异常、整局中断——所以每条非法路径都要单独断一次。
 */
class WerewolfDecisionTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry(id = "p$it", name = "玩家$it") }

    /** 1、2 狼；3 预言家；4 女巫；5 猎人；6、7、8 平民。 */
    private fun game(): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(),
        roster = roster(8),
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.SEER,
            WerewolfRole.WITCH,
            WerewolfRole.HUNTER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
        ),
    )

    // ------------------------------------------------------------------
    // 狼刀
    // ------------------------------------------------------------------

    @Test
    fun `狼刀可以指向自己、队友，也可以空刀`() {
        val spec = WerewolfDecisions.wolfKill(game(), seat = 1)
        // 自刀骗解药、刀队友做身份都是正经花板子，v12 起引擎也放开了
        assertTrue("可以自刀", spec.validate(VoteChoice(1)))
        assertTrue("可以刀队友", spec.validate(VoteChoice(2)))
        assertTrue("可以刀好人", spec.validate(VoteChoice(6)))
        assertTrue("可以空刀", spec.validate(VoteChoice(null)))
    }

    @Test
    fun `狼刀不能指向已出局的玩家`() {
        val g = game()
        val withDead = g.copy(players = g.players.map { if (it.seat == 6) it.copy(alive = false) else it })
        val spec = WerewolfDecisions.wolfKill(withDead, seat = 1)
        assertFalse("6 号已出局", spec.validate(VoteChoice(6)))
        assertTrue(spec.validate(VoteChoice(7)))
    }

    @Test
    fun `狼刀的说明里列出了全部可选目标`() {
        val instruction = WerewolfDecisions.wolfKill(game(), seat = 1).instruction
        assertTrue(instruction.contains("3 号"))
        assertTrue(instruction.contains("8 号"))
        assertTrue("自己也要在候选里", instruction.contains("1 号"))
        assertTrue("要说清能空刀", instruction.contains("空刀"))
    }

    // ------------------------------------------------------------------
    // 查验
    // ------------------------------------------------------------------

    @Test
    fun `预言家不能查验自己`() {
        val spec = WerewolfDecisions.seerCheck(game(), seat = 3)
        assertFalse(spec.validate(SeatChoice(3)))
        assertTrue(spec.validate(SeatChoice(1)))
    }

    // ------------------------------------------------------------------
    // 女巫
    // ------------------------------------------------------------------

    @Test
    fun `今晚有人被刀时可以救，但毒药不能指向自己`() {
        val g = game().copy(phase = Phase.NIGHT_WITCH, tonightVictim = 6)
        val spec = WerewolfDecisions.witch(g, seat = 4)

        assertTrue("救别人合法", spec.validate(WitchChoice(heal = true)))
        assertFalse("不能毒自己", spec.validate(WitchChoice(poison = 4)))
        assertFalse("同一夜不能双药", spec.validate(WitchChoice(heal = true, poison = 6)))
        assertTrue("什么都不做也合法", spec.validate(WitchChoice()))
    }

    @Test
    fun `无人被刀的夜晚不能用药`() {
        val g = game().copy(phase = Phase.NIGHT_WITCH, tonightVictim = null)
        val spec = WerewolfDecisions.witch(g, seat = 4)
        assertFalse("没人被刀，解药无从下手", spec.validate(WitchChoice(heal = true)))
        assertTrue(spec.validate(WitchChoice()))
    }

    @Test
    fun `女巫第一夜可以自救，第二夜不行`() {
        val firstNight = game().copy(phase = Phase.NIGHT_WITCH, day = 1, tonightVictim = 4)
        assertTrue(
            "配置允许第一夜自救",
            WerewolfDecisions.witch(firstNight, seat = 4).validate(WitchChoice(heal = true)),
        )

        val secondNight = firstNight.copy(day = 2)
        assertFalse(
            "第二夜之后不能自救",
            WerewolfDecisions.witch(secondNight, seat = 4).validate(WitchChoice(heal = true)),
        )
    }

    @Test
    fun `解药用完之后不能再救`() {
        val g = game().copy(
            phase = Phase.NIGHT_WITCH,
            tonightVictim = 6,
            witch = com.feature.werewolf.game.WitchStock(antidoteAvailable = false),
        )
        assertFalse(WerewolfDecisions.witch(g, seat = 4).validate(WitchChoice(heal = true)))
    }

    @Test
    fun `不能毒已经出局的人`() {
        val g = game().copy(phase = Phase.NIGHT_WITCH, tonightVictim = 6)
        val spec = WerewolfDecisions.witch(g, seat = 4)
        assertFalse("9 号根本不存在", spec.validate(WitchChoice(poison = 9)))
    }

    @Test
    fun `毒药候选名单里没有女巫自己`() {
        val g = game().copy(phase = Phase.NIGHT_WITCH, tonightVictim = 6)
        val text = WerewolfDecisions.witch(g, seat = 4).instruction
        assertTrue("要给候选，否则模型只能瞎猜", text.contains("只能从 1 号、2 号、3 号、5 号"))
        assertTrue(text.contains("不能毒自己"))
    }

    @Test
    fun `女巫自己中刀且救不了时，提示词说明这是最后一次用毒`() {
        // 第 2 夜、解药已尽、自己被刀：天一亮就出局，毒药不用就作废。
        val g = game().copy(
            phase = Phase.NIGHT_WITCH,
            day = 2,
            tonightVictim = 4,
            witch = com.feature.werewolf.game.WitchStock(antidoteAvailable = false),
        )
        val text = WerewolfDecisions.witch(g, seat = 4).instruction
        assertTrue("要说明中刀的是自己", text.contains("今晚被狼人袭击的是你自己（4 号）"))
        assertTrue("解药的禁用原因要说清", text.contains("（救不了你自己）"))
        assertTrue("必须点明这是唯一机会", text.contains("你今晚必死"))
    }

    @Test
    fun `无人被刀的夜晚，提示词不说解药不能用`() {
        // 「没人被刀」和「药没了」是两件事，混成一句会让女巫以为自己漏了信息
        val g = game().copy(phase = Phase.NIGHT_WITCH, tonightVictim = null)
        val text = WerewolfDecisions.witch(g, seat = 4).instruction
        assertTrue(text.contains("今晚没有人被狼人袭击"))
        assertTrue(text.contains("今晚没人被刀，用不上"))
    }

    // ------------------------------------------------------------------
    // 投票与开枪
    // ------------------------------------------------------------------

    @Test
    fun `投票允许弃票，但只能投候选里的活人`() {
        val g = game().copy(phase = Phase.DAY_VOTE, voteCandidates = listOf(1, 6, 7))
        val spec = WerewolfDecisions.vote(g, candidates = listOf(1, 6, 7))

        assertTrue("弃票合法", spec.validate(VoteChoice(target = null)))
        assertTrue(spec.validate(VoteChoice(target = 6)))
        assertFalse("不在加投候选里", spec.validate(VoteChoice(target = 8)))
    }

    @Test
    fun `猎人可以选择不开枪`() {
        val spec = WerewolfDecisions.hunterShoot(game())
        assertTrue(spec.validate(VoteChoice(target = null)))
        assertFalse(spec.validate(VoteChoice(target = 99)))
    }

    // ------------------------------------------------------------------
    // 兜底
    // ------------------------------------------------------------------

    @Test
    fun `狼刀兜底永远是合法动作`() {
        repeat(50) { seed ->
            val g = game()
            val choice = WerewolfDecisions.fallbackWolfKill(g, seat = 1, random = Random(seed))
            val target = choice.target
            assertTrue("兜底刀了 ${target} 号", target != null && target in g.aliveSeats)
            assertNotEquals("不能兜底刀自己", 1, target)
            // 兜底刻意不选自刀、不选空刀：那是要全队配合的战术，模型失灵时用不得
            assertFalse("不能兜底刀队友", target?.let { g.isWerewolf(it) } == true)
        }
    }

    @Test
    fun `预言家兜底优先验没查过的人`() {
        var g = game()
        // 把 1、2、5、6、7、8 都查过，只剩 4 号没查
        listOf(1, 2, 5, 6, 7, 8).forEach { target ->
            g = g.copy(
                seerChecks = g.seerChecks + com.feature.werewolf.game.SeerCheck(1, target, false)
            )
        }
        repeat(20) { seed ->
            val choice = WerewolfDecisions.fallbackSeerCheck(g, seat = 3, random = Random(seed))
            assertEquals("只剩 4 号没验过", 4, choice.target)
        }
    }

    @Test
    fun `投票兜底尽量不投自己`() {
        val g = game()
        repeat(20) { seed ->
            val choice = WerewolfDecisions.fallbackVote(g, seat = 1, candidates = g.aliveSeats, random = Random(seed))
            assertNotNull(choice.target)
            assertNotEquals(1, choice.target)
        }
    }

    @Test
    fun `候选为空时兜底返回 null 而不是抛异常`() {
        assertNull(WerewolfDecisions.pick(emptyList<Int>(), Random(1)))
        assertEquals(3, WerewolfDecisions.pick(listOf(3), Random(1)))
    }
}
