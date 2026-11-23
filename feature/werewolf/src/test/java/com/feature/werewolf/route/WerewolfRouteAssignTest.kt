package com.feature.werewolf.route

import com.feature.werewolf.game.SELF_PLAYER_ID
import com.feature.werewolf.game.WerewolfRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 开局页派单 → 引擎发牌参数的映射。
 *
 * 这一层决定「谁的身份被指定了」，错的方式都很安静：既不会崩，也不会报错，
 * 只会在开局后发成一副跟用户选择不一样的牌——所以他选了狼、拿了预言家，
 * 还以为是随机。所以三种模式 × 指定/不指定都要钉住。
 */
class WerewolfRouteAssignTest {

    private val none = emptyMap<String, String>()

    @Test
    fun `参与局指定自己就是那一个身份，其余随机`() {
        val assignment = roleAssignmentOf(
            playing = true,
            assignRoles = false,
            roleByFriend = none,
            myRole = WerewolfRole.SEER.name,
        )
        assertEquals("只动自己这一席", mapOf(SELF_PLAYER_ID to WerewolfRole.SEER), assignment)
        assertNull("预言家不是头狼", alphaIdOf(true, false, none, WerewolfRole.SEER.name))
    }

    @Test
    fun `参与局选头狼时自己是狼，且被指定为头狼`() {
        val assignment = roleAssignmentOf(true, false, none, ALPHA_WOLF_KEY)
        assertEquals("头狼在引擎里就是狼", mapOf(SELF_PLAYER_ID to WerewolfRole.WEREWOLF), assignment)
        assertEquals("头狼必须单独交代", SELF_PLAYER_ID, alphaIdOf(true, false, none, ALPHA_WOLF_KEY))
    }

    @Test
    fun `参与局不选身份时什么都不指定，照常随机`() {
        assertTrue(roleAssignmentOf(true, false, none, null).isEmpty())
        assertNull(alphaIdOf(true, false, none, null))
    }

    @Test
    fun `观战局忽略我的身份，走导演派单`() {
        // 切模式时 route 会清掉 myRole，这里再兜一道：残留值不能影响观战局
        val assignment = roleAssignmentOf(false, false, none, WerewolfRole.SEER.name)
        assertTrue("没开导演模式就不派单", assignment.isEmpty())

        val directed = roleAssignmentOf(
            playing = false,
            assignRoles = true,
            roleByFriend = mapOf("f1" to WerewolfRole.WEREWOLF.name),
            myRole = null,
        )
        assertEquals(mapOf("f1" to WerewolfRole.WEREWOLF), directed)
    }

    @Test
    fun `认不出的身份名回落到随机，不卡死开局`() {
        assertTrue(roleAssignmentOf(true, false, none, "通灵师").isEmpty())
    }
}
