package com.feature.werewolf.game

import com.feature.werewolf.game.WerewolfRole.GUARD
import com.feature.werewolf.game.WerewolfRole.HUNTER
import com.feature.werewolf.game.WerewolfRole.SEER
import com.feature.werewolf.game.WerewolfRole.VILLAGER
import com.feature.werewolf.game.WerewolfRole.WEREWOLF
import com.feature.werewolf.game.WerewolfRole.WITCH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 夜间行动只写状态、结算统一到 DAWN_SETTLE（R2）。
 *
 * 这是三路并发的前提：守卫 / 狼人 / 预言家各自行动时都不结算，死亡等到天亮
 * 读齐所有字段才算——否则并发下谁先完成谁就先结算，会读到不完整的守护/刀口。
 */
class WerewolfDawnSettleTest {

    private val roles = listOf(
        WEREWOLF, WEREWOLF, HUNTER, SEER, WITCH, GUARD, VILLAGER, VILLAGER,
    )

    private fun game() = WerewolfGame.withRoles(
        config = WerewolfConfig(composition = roles.groupingBy { it }.eachCount()),
        roster = roles.indices.map { RosterEntry(id = "p${it + 1}", name = "${it + 1}号") },
        roles = roles,
    )

    @Test
    fun `女巫行动后只到结算节点，未结算前没人死`() {
        val start = game().copy(phase = Phase.NIGHT_WITCH, day = 1, tonightVictim = 7)
        // 女巫不用药
        val afterWitch = start.witchAct(heal = false)

        assertEquals("女巫行动后停在结算节点，没有立即结算", Phase.DAWN_SETTLE, afterWitch.phase)
        assertTrue("结算前 7 号还活着", afterWitch.playerAt(7).alive)
        assertTrue("结算前没有天亮播报", afterWitch.history.none { it is GameEvent.Dawn })

        // 天亮节点统一结算
        val dawn = afterWitch.settlePendingNight()
        assertFalse("结算后 7 号出局", dawn.playerAt(7).alive)
        assertEquals(DeathCause.WOLF_KILL, dawn.playerAt(7).deathCause)
        assertTrue("结算后有天亮播报", dawn.history.any { it is GameEvent.Dawn })
    }

    @Test
    fun `女巫用药决定暂存到库存，结算时才生效`() {
        val start = game().copy(phase = Phase.NIGHT_WITCH, day = 2, tonightVictim = 7)
        val afterWitch = start.witchAct(heal = true)

        assertEquals("用药决定暂存在库存里", true, afterWitch.witch.nightHeal)
        assertFalse("解药库存已扣", afterWitch.witch.antidoteAvailable)
        assertTrue("结算前 7 号还没被判活", afterWitch.playerAt(7).alive)

        val dawn = afterWitch.settlePendingNight()
        assertTrue("救了 7 号，天亮还活着", dawn.playerAt(7).alive)
        assertTrue("复盘记下这一夜用了药", dawn.nights.last().healed)
    }

    @Test
    fun `结算读齐守卫守护与狼刀，守中挡刀判定正确`() {
        val start = game().copy(phase = Phase.NIGHT_GUARD, day = 2)
        // 守卫守 7 号 → 狼刀 7 号 → 女巫不用药 → 结算
        val guarded = start.guardProtect(7)
        assertEquals("守护只写字段不结算", 7, guarded.tonightGuardedSeat)
        assertTrue("守护阶段后没结算", guarded.playerAt(7).alive)

        val voted = guarded.beginWolfVote().wolfVote(mapOf(1 to 7, 2 to 7))
        assertEquals("狼定刀后进入预言家，没结算", Phase.NIGHT_SEER, voted.phase)
        assertTrue("定刀后还没结算", voted.playerAt(7).alive)

        val afterWitch = voted.seerCheck(1).witchAct(heal = false)
        val dawn = afterWitch.settlePendingNight()
        assertTrue("守中了 7 号，天亮活着", dawn.playerAt(7).alive)
        assertTrue("复盘记下守护对象", dawn.nights.last().guarded == 7)
    }

    @Test
    fun `无女巫的局在预言家后直接到结算节点`() {
        val noWitchRoles = listOf(WEREWOLF, WEREWOLF, HUNTER, SEER, GUARD, VILLAGER, VILLAGER, VILLAGER)
        val g = WerewolfGame.withRoles(
            config = WerewolfConfig(composition = noWitchRoles.groupingBy { it }.eachCount()),
            roster = noWitchRoles.indices.map { RosterEntry(id = "p${it + 1}", name = "${it + 1}号") },
            roles = noWitchRoles,
        )
        val voted = g.copy(phase = Phase.NIGHT_GUARD, day = 2)
            .guardProtect(null).beginWolfVote().wolfVote(mapOf(1 to 4, 2 to 4))
        assertEquals(Phase.NIGHT_SEER, voted.phase)

        // 预言家查验后：无女巫 → 直接到结算节点，而不是在这里就结算
        val afterSeer = voted.seerCheck(1)
        assertEquals("无女巫局也到结算节点才结算", Phase.DAWN_SETTLE, afterSeer.phase)
        assertTrue("结算前 4 号还活着", afterSeer.playerAt(4).alive)

        val dawn = afterSeer.settlePendingNight()
        assertFalse("结算后 4 号（预言家）出局", dawn.playerAt(4).alive)
        assertEquals(DeathCause.WOLF_KILL, dawn.playerAt(4).deathCause)
    }
}
