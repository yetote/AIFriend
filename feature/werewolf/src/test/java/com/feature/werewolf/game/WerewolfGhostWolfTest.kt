package com.feature.werewolf.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自爆之后的「还没走」：算死，但当夜狼队环节仍然参加，定刀结束才离场。
 *
 * 座位与身份（6 人局）：1/2 狼 · 3 预言家 · 4 女巫 · 5/6 平民。
 * 这条规则是本项目独有的，所以「死了」和「离场」必须分开验：
 * 生存、胜负、投票权当天就没了；夜里的商议和定刀还在。
 */
class WerewolfGhostWolfTest {

    private val roles = listOf(
        WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF,
        WerewolfRole.SEER, WerewolfRole.WITCH,
        WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
    )

    private fun roster(size: Int) = List(size) { RosterEntry("p${it + 1}", "${it + 1}号") }

    private fun game(): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(composition = roles.groupingBy { it }.eachCount()),
        roster = roster(roles.size),
        roles = roles,
    )

    private fun day(): WerewolfGame = game().copy(day = 1, phase = Phase.DAY_DISCUSSION)

    @Test
    fun `自爆当场算出局，但当夜还在狼队名单里`() {
        val exploded = day().wolfExplode(1)

        assertFalse("自爆就是出局，不能还活着", exploded.playerAt(1).alive)
        assertEquals(DeathCause.SELF_EXPLODE, exploded.playerAt(1).deathCause)
        assertEquals("还没离场", setOf(1), exploded.ghostWolves)
        assertEquals("清单里得有他", listOf(1, 2), exploded.nightWolfSeats())
        assertEquals("玩家对象也要给得出来", 1, exploded.nightWolves().first { it.seat == 1 }.seat)
    }

    @Test
    fun `自爆狼的票算数，定刀结束才真正离场`() {
        val exploded = day().wolfExplode(1)
        val voting = exploded.beginWolfVote()

        // 两个人都要表态：名单是「今晚在桌上的」，不是「还活着的」
        val settled = voting.wolfVote(mapOf(1 to 3, 2 to 3))

        assertEquals("刀口照常生效", 3, settled.tonightVictim)
        assertTrue("定刀结束就该走了", settled.ghostWolves.isEmpty())
        assertEquals("下一夜只剩活着的狼", listOf(2), settled.nightWolfSeats())
    }

    @Test
    fun `缺了自爆狼的表态要报错，不能静默少一票`() {
        val voting = day().wolfExplode(1).beginWolfVote()

        val error = runCatching { voting.wolfVote(mapOf(2 to 3)) }.exceptionOrNull()

        assertTrue("名录对不上必须拦下来", error is IllegalArgumentException)
    }

    @Test
    fun `警长竞选里自爆仍然中止选举`() {
        val exploded = day().copy(phase = Phase.DAY_SHERIFF_SPEECH, sheriffCandidates = listOf(1, 2))
            .wolfExplode(1)

        assertTrue("选举就此作废", exploded.sheriffElectionDone)
        assertTrue("候选人清空", exploded.sheriffCandidates.isEmpty())
        assertTrue(
            "要留下「没有警长」这条播报",
            exploded.history.any { it is GameEvent.SheriffVacant },
        )
    }
}
