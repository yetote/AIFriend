package com.feature.werewolf.prompt

import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.prompt.roles.WolfTactics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 打法菜单按板子裁。
 *
 * 菜单是**给狼的进攻方向**，写错方向的代价比不写更大：在没有警长的局里讲「吞警徽」，
 * 模型会去找一个本局不存在的警徽；在没有女巫的局里讲「骗解药」，它会去骗一瓶不存在的药。
 */
class WolfTacticsTest {

    private fun config(vararg roles: WerewolfRole) =
        WerewolfConfig(composition = roles.groupingBy { it }.eachCount())

    private val withSheriffAndWitch = config(
        WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF,
        WerewolfRole.SEER, WerewolfRole.WITCH, WerewolfRole.GUARD,
        WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
    ).copy(hasSheriff = true)

    private val noSheriffNoWitch = config(
        WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF,
        WerewolfRole.SEER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
    )

    @Test
    fun `常规局能拿到全部打法`() {
        listOf("悍跳预言家", "倒钩狼", "冲锋狼", "深水狼", "狼查杀狼").forEach {
            assertTrue(it, WolfTactics.has(withSheriffAndWitch, it))
        }
    }

    @Test
    fun `没有警长就不讲吞警徽`() {
        assertFalse("没警长哪来的徽", WolfTactics.has(noSheriffNoWitch, "双爆吞警徽"))
        assertFalse(WolfTactics.has(noSheriffNoWitch, "双狼悍跳"))
        assertTrue("悍跳本身跟警徽无关", WolfTactics.has(noSheriffNoWitch, "悍跳预言家"))
    }

    @Test
    fun `没有女巫就不讲骗解药和盗银水`() {
        assertFalse(WolfTactics.has(noSheriffNoWitch, "自刀骗解药"))
        assertTrue(WolfTactics.has(withSheriffAndWitch, "自刀骗解药"))
        assertFalse("没女巫就没有银水可盗", WolfTactics.has(noSheriffNoWitch, "悍跳女巫盗银水"))
        assertTrue(WolfTactics.has(withSheriffAndWitch, "悍跳女巫盗银水"))
    }

    @Test
    fun `反水做身份只在三狼以上给，双狼局赔不起`() {
        // 反水的代价是队友可能被推出去。双狼局只剩自己一只，等于白送一张狼牌。
        assertTrue("三狼局有反水做身份", WolfTactics.has(withSheriffAndWitch, "反水做身份"))
        assertFalse("双狼局不教这招", WolfTactics.has(noSheriffNoWitch, "反水做身份"))
        val king10 = WerewolfConfig(composition = WerewolfConfig.King10)
        assertTrue("10人狼王局共3狼（1狼王+2狼）也应包含反水做身份", WolfTactics.has(king10, "反水做身份"))
    }

    @Test
    fun `空刀和深水任何板子都成立`() {
        listOf(noSheriffNoWitch, withSheriffAndWitch).forEach { cfg ->
            assertTrue(WolfTactics.has(cfg, "空刀"))
            assertTrue(WolfTactics.has(cfg, "深水狼"))
        }
    }

    @Test
    fun `菜单不泄进好人或观战的提示词`() {
        val roles = listOf(
            WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF,
            WerewolfRole.SEER, WerewolfRole.WITCH, WerewolfRole.GUARD,
            WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
        )
        val game = WerewolfGame.withRoles(
            config = withSheriffAndWitch,
            roster = roles.indices.map { RosterEntry(id = "p${it + 1}", name = "${it + 1}号") },
            roles = roles,
        )

        val chatGame = game.copy(phase = com.feature.werewolf.game.Phase.NIGHT_WOLF_CHAT)
        val wolfChat = WerewolfPrompt.systemPrompt(chatGame, seat = 1)
        assertTrue("狼队夜聊要看得到菜单", wolfChat.contains("倒钩狼"))
        assertTrue("狼队夜聊要看得到反水战术", wolfChat.contains("反水做身份"))
        assertTrue("狼队夜聊要看得到盗银水战术", wolfChat.contains("悍跳女巫盗银水"))

        val wolfKnife = WerewolfPrompt.systemPrompt(game.copy(phase = com.feature.werewolf.game.Phase.NIGHT_WOLF), seat = 1)
        assertFalse("定刀阶段商议已结束，不再注入 11 套战术菜单省 token", wolfKnife.contains("【狼队打法菜单】"))

        val wolfDay = WerewolfPrompt.systemPrompt(game.copy(phase = com.feature.werewolf.game.Phase.DAY_DISCUSSION), seat = 1)
        assertFalse("白天也不再注入战术菜单", wolfDay.contains("【狼队打法菜单】"))

        // 好人看到「倒钩」就等于被告知狼队在分角色，这条必须在狼那一段里
        (4..10).forEach { seat ->
            val good = WerewolfPrompt.systemPrompt(chatGame, seat)
            assertFalse("$seat 号不该看到狼队打法", good.contains("倒钩狼"))
            assertFalse("$seat 号不该看到反水战术", good.contains("反水做身份"))
            assertFalse("$seat 号不该看到盗银水战术", good.contains("悍跳女巫盗银水"))
        }
    }
}
