package com.feature.werewolf.decision

import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.GameEvent
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

    /** 造一个「今天天亮有人倒牌」的局面：补 Dawn 事件，并把那几个人标成出局。 */
    private fun withDead(game: WerewolfGame, vararg seats: Int): WerewolfGame = game.copy(
        history = game.history + GameEvent.Dawn(game.day, seats.toList()),
        players = game.players.map {
            if (it.seat in seats) {
                it.copy(alive = false, deathCause = DeathCause.WOLF_KILL, deathDay = game.day)
            } else {
                it
            }
        },
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
    fun `解药已尽时，决策指令不再披露刀口（含自己被刀）`() {
        // 解药一空，夜里零信息：不报今晚谁被刀，也不报「你今晚必死」——
        // 毒药作废是用完解药的代价。只说明她剩下什么可做。
        val g = game().copy(
            phase = Phase.NIGHT_WITCH,
            day = 2,
            tonightVictim = 4,
            witch = com.feature.werewolf.game.WitchStock(antidoteAvailable = false),
        )
        val text = WerewolfDecisions.witch(g, seat = 4).instruction
        assertFalse("不许报自己被刀", text.contains("被狼人袭击"))
        assertFalse("必死通知一并撤掉", text.contains("你今晚必死"))
        assertTrue("要说明解药只能填 false", text.contains("只能填 false"))
        assertTrue("毒药的抉择还在", text.contains("poison"))
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
    fun `投票指令要求真投，并要求与发言一致`() {
        // 这两句是「AI 不会玩」的重要来源：旧指令把「不想投任何人就填 null」
        // 摆在最显眼的位置，模型拿不准就弃票，一天下来没人出局。
        val g = game().copy(phase = Phase.DAY_VOTE, voteCandidates = listOf(1, 6, 7))
        val text = WerewolfDecisions.vote(g, candidates = listOf(1, 6, 7)).instruction

        assertTrue("必须投出去", text.contains("这一票必须投出去"))
        assertTrue("弃票要有前提", text.contains("确认的好人"))
        assertTrue("不能发言说 A 投票投 B", text.contains("票要和你今天说过的话一致"))
        assertTrue("理由要有依据", text.contains("别只写「感觉」「像狼」"))
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
    fun `狼的投票兜底不会砸在队友身上`() {
        // 1、2 号是狼：随机数不知道这件事，一票砸在队友身上等于白送一张牌，
        // 而且从票型里看不出是失误还是叛变。
        val g = game()
        val wolf = g.aliveSeats.filter { g.isWerewolf(it) }.toSet()
        repeat(30) { seed ->
            val choice = WerewolfDecisions.fallbackVote(g, seat = 1, candidates = g.aliveSeats, random = Random(seed))
            assertFalse("兜底投了队友 ${choice.target}", choice.target in wolf)
        }
    }

    @Test
    fun `候选为空时兜底返回 null 而不是抛异常`() {
        assertNull(WerewolfDecisions.pick(emptyList<Int>(), Random(1)))
        assertEquals(3, WerewolfDecisions.pick(listOf(3), Random(1)))
    }

    // ------------------------------------------------------------------
    // 警长定发言顺序
    // ------------------------------------------------------------------

    @Test
    fun `警长定顺序：四个方向都摆出来，第一个发言人算好`() {
        val dead = withDead(game().copy(phase = Phase.DAY_DISCUSSION, sheriffSeat = 2), 5)
        val spec = WerewolfDecisions.speechDirection(dead, seat = 2)

        assertTrue(spec.instruction.contains("警左"))
        assertTrue(spec.instruction.contains("死右"))
        // 只给四个词模型只能瞎猜，必须把「从谁起」算出来
        assertTrue("死左要从 6 号起", spec.instruction.contains("从 6 号开始"))
        assertTrue(spec.validate(DirectionChoice(direction = "死右")))
        assertTrue("兜底值本身合法", spec.validate(DirectionChoice()))
    }

    @Test
    fun `平安夜只给警左警右，死左被判非法`() {
        val peaceful = game().copy(phase = Phase.DAY_DISCUSSION, sheriffSeat = 2)
        val spec = WerewolfDecisions.speechDirection(peaceful, seat = 2)

        assertFalse("没有死者，不能把死左死右摆出来", spec.instruction.contains("死左"))
        assertFalse(spec.validate(DirectionChoice(direction = "死左")))
        assertTrue(spec.validate(DirectionChoice(direction = "警右")))
        assertFalse("瞎填的标签也不认", spec.validate(DirectionChoice(direction = "斜着来")))
    }
}
