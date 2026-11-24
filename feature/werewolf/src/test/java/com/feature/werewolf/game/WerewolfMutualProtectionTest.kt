package com.feature.werewolf.game

import com.feature.werewolf.game.WerewolfRole.HUNTER
import com.feature.werewolf.game.WerewolfRole.SEER
import com.feature.werewolf.game.WerewolfRole.VILLAGER
import com.feature.werewolf.game.WerewolfRole.WEREWOLF
import com.feature.werewolf.game.WerewolfRole.WITCH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「守救同归 + 猎人开枪」的组合。
 *
 * 真机案例：猎人兼警长在第 2 夜被守救同归而死，然后开枪带走 5 号。
 * 复盘底牌里这位猎人显示的是「第 2 夜被枪带走」——他自己的死因被枪覆盖了。
 * 死因决定复盘怎么写、决定他能不能开枪，必须一个都不能错。
 */
class WerewolfMutualProtectionTest {

    private val roles = listOf(
        WEREWOLF,        // 1 号 狼
        WEREWOLF,        // 2 号 狼
        HUNTER,          // 3 号 猎人
        SEER,            // 4 号 预言家
        WITCH,           // 5 号 女巫
        WerewolfRole.GUARD, // 6 号 守卫
        VILLAGER,        // 7 号
        VILLAGER,        // 8 号
    )

    private fun game() = WerewolfGame.withRoles(
        config = WerewolfConfig(composition = roles.groupingBy { it }.eachCount()),
        roster = roles.indices.map { RosterEntry(id = "p${it + 1}", name = "${it + 1}号") },
        roles = roles,
    )

    @Test
    fun `守救同归的猎人死因是被刀，开枪带走别人的死因是枪`() {
        val start = game().copy(phase = Phase.NIGHT_GUARD, day = 2)
        // 守卫守 3 号（猎人），狼刀 3 号，女巫也救 3 号 → 守救同归
        val guarded = start.guardProtect(3)
        val voted = guarded.beginWolfVote().wolfVote(mapOf(1 to 3, 2 to 3))
        val morning = voted.seerCheck(7).witchActSettled(heal = true)

        assertTrue("守救同归要死", !morning.playerAt(3).alive)
        assertEquals(
            "猎人应该是被刀死的",
            DeathCause.WOLF_KILL,
            morning.playerAt(3).deathCause,
        )
        assertEquals("猎人被狼刀死，有开枪权", 3, morning.pendingHunterSeat)

        val shot = morning.hunterShoot(8)

        assertTrue("被枪带走的人要死", !shot.playerAt(8).alive)
        assertEquals(
            "被带走的是被枪死的",
            DeathCause.HUNTER_SHOOT,
            shot.playerAt(8).deathCause,
        )
        assertEquals(
            "开枪的人自己的死因不能被枪覆盖",
            DeathCause.WOLF_KILL,
            shot.playerAt(3).deathCause,
        )
    }
}
