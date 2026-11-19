package com.feature.werewolf.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WerewolfBoards.keyOf] 是续档用的反查：存档里只有 config，而没有 key 就取不到
 * 玩家给这张板子单独写的提示词。
 *
 * 这里守的是**宁可认不出**这条底线：认错等于把别张板子的要求套到这一局上，
 * 比只按全局设置打更糟。所以只要配置有一个字段对不上，就回空串。
 */
class WerewolfBoardsTest {

    @Test
    fun `每张板子都能按自己的配置反查回 key`() {
        WerewolfBoards.all.forEach { board ->
            assertEquals(board.title + " 反查失败", board.key, WerewolfBoards.keyOf(board.config))
        }
    }

    @Test
    fun `默认配置就是标准 8 人局`() {
        assertEquals(WerewolfBoards.STANDARD_8, WerewolfBoards.keyOf(WerewolfConfig()))
    }

    @Test
    fun `开了警长的板子按警长版认`() {
        assertEquals(WerewolfBoards.GUARD_12, WerewolfBoards.keyOf(WerewolfBoards.configOf(WerewolfBoards.GUARD_12)))
        // 同一套身份、不开警长，不是任何一张 12 人板子——认不出，别硬套
        assertEquals("", WerewolfBoards.keyOf(WerewolfConfig(composition = WerewolfConfig.Guard12)))
    }

    @Test
    fun `差一个开关就认不出`() {
        val guard10 = WerewolfBoards.configOf(WerewolfBoards.GUARD_10)
        assertEquals("", WerewolfBoards.keyOf(guard10.copy(hasSheriff = true)))
        assertEquals("", WerewolfBoards.keyOf(guard10.copy(maxVoteRounds = 2)))
        assertEquals("", WerewolfBoards.keyOf(guard10.copy(winRule = WinRule.SLAY_ALL)))
        assertEquals("", WerewolfBoards.keyOf(guard10.copy(witchCanSelfHealOnFirstNight = false)))
    }

    @Test
    fun `身份构成改了也不是原板子`() {
        val standard8 = WerewolfConfig()
        val composition = standard8.composition.toMutableMap()
        composition[WerewolfRole.VILLAGER] = (composition[WerewolfRole.VILLAGER] ?: 0) + 1
        assertEquals("", WerewolfBoards.keyOf(standard8.copy(composition = composition)))
    }

    @Test
    fun `认不出的配置回空串而不是回落默认板子`() {
        val unknown = WerewolfConfig(composition = WerewolfConfig.Standard9, hasSheriff = true)
        assertEquals("", WerewolfBoards.keyOf(unknown))
        // 和 configOf 的回落行为正好相反：取配置可以给默认值，取存储不能
        assertTrue(WerewolfBoards.configOf("").composition.isNotEmpty())
    }
}
