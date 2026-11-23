package com.feature.werewolf.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/**
 * 导演模式的发牌：指定身份 + 随机补齐。
 *
 * 最容易错的三处都锁在这里：
 * 1. 指定的人数超过板子构成时必须报错（错误文案会直接显示在开局页，要说人话）；
 * 2. 没指定的人随机补齐后，整局构成必须和板子一字不差；
 * 3. 座位照常洗——指定的是「谁当狼」，不是「谁坐 1 号」。
 */
class WerewolfDealAssignTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry("p$it", "玩家$it") }

    @Test
    fun `指定身份生效，其余随机补齐且构成不变`() {
        val config = WerewolfConfig()
        val assignment = mapOf(
            "p3" to WerewolfRole.WEREWOLF,
            "p5" to WerewolfRole.SEER,
            "p7" to WerewolfRole.VILLAGER,
        )
        val game = WerewolfGame.deal(config, roster(8), Random(42), assignment)

        assertEquals("指定的狼就是狼", WerewolfRole.WEREWOLF, game.players.first { it.id == "p3" }.role)
        assertEquals("指定的预言家就是预言家", WerewolfRole.SEER, game.players.first { it.id == "p5" }.role)
        assertEquals("指定的平民就是平民", WerewolfRole.VILLAGER, game.players.first { it.id == "p7" }.role)
        // 没指定的人随机补，但整局构成必须和板子一致——补错了整局规则都是坏的
        assertEquals("构成不能变", config.composition, game.players.groupingBy { it.role }.eachCount())
    }

    @Test
    fun `人类座位也能被指定身份`() {
        // 参与局的「我的身份」走的就是这条路：只指定自己一席，其余随机补。
        val config = WerewolfConfig()
        val roster = roster(7) + RosterEntry(SELF_PLAYER_ID, "你", model = "human")
        val game = WerewolfGame.deal(
            config, roster, Random(11),
            assignment = mapOf(SELF_PLAYER_ID to WerewolfRole.WEREWOLF),
        )

        assertEquals(
            "自己选的身份要落到自己头上",
            WerewolfRole.WEREWOLF,
            game.players.first { it.id == SELF_PLAYER_ID }.role,
        )
        assertEquals("构成不能变", config.composition, game.players.groupingBy { it.role }.eachCount())
    }

    @Test
    fun `全部指定时就是一场纯剧本局`() {
        val config = WerewolfConfig()
        val roles = listOf(
            WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF,
            WerewolfRole.SEER, WerewolfRole.WITCH, WerewolfRole.HUNTER,
            WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
        )
        val assignment = (1..8).associate { "p$it" to roles[it - 1] }
        val game = WerewolfGame.deal(config, roster(8), Random(7), assignment)

        roles.forEachIndexed { index, role ->
            assertEquals(role, game.players.first { it.id == "p${index + 1}" }.role)
        }
    }

    @Test
    fun `指定人数超过板子构成时报错，文案要说人话`() {
        // 标准板只有 2 只狼：指定 3 只必须拦下来，不能让一张对不上的牌上桌
        val assignment = mapOf(
            "p1" to WerewolfRole.WEREWOLF,
            "p2" to WerewolfRole.WEREWOLF,
            "p3" to WerewolfRole.WEREWOLF,
        )
        try {
            WerewolfGame.deal(WerewolfConfig(), roster(8), Random(1), assignment)
            fail("超额指定不该发牌成功")
        } catch (e: IllegalArgumentException) {
            assertTrue("错误要提到身份名，让用户知道改哪里：${e.message}", e.message!!.contains("狼"))
        }
    }

    @Test
    fun `指定了不在名单里的玩家时报错`() {
        val assignment = mapOf("ghost" to WerewolfRole.WEREWOLF)
        try {
            WerewolfGame.deal(WerewolfConfig(), roster(8), Random(1), assignment)
            fail("名单外的指定不该发牌成功")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("名单"))
        }
    }

    @Test
    fun `同一种子同一份指定能复现同一局`() {
        val config = WerewolfConfig()
        val assignment = mapOf("p2" to WerewolfRole.WITCH)
        val first = WerewolfGame.deal(config, roster(8), Random(99), assignment)
        val second = WerewolfGame.deal(config, roster(8), Random(99), assignment)
        assertEquals(
            first.players.map { it.id to it.role },
            second.players.map { it.id to it.role },
        )
    }

    @Test
    fun `指定头狼：自动派成狼并钉在 alphaWolfSeat 上`() {
        val config = WerewolfConfig()
        // p6 没被指身份：指定他当头狼等于把一个狼名额派给他，其余照旧随机
        val game = WerewolfGame.deal(config, roster(8), Random(42), alphaId = "p6")

        val alpha = game.players.first { it.id == "p6" }
        assertEquals("头狼必须是狼", WerewolfRole.WEREWOLF, alpha.role)
        assertEquals("头狼要钉在局面上，每晚都用他", alpha.seat, game.alphaWolfSeat)
        assertEquals("占了狼名额之后整局构成不能变", config.composition, game.players.groupingBy { it.role }.eachCount())
    }

    @Test
    fun `头狼和身份派单可以同时用，指的就是那只狼`() {
        val config = WerewolfConfig()
        val assignment = mapOf("p2" to WerewolfRole.WEREWOLF, "p5" to WerewolfRole.SEER)
        val game = WerewolfGame.deal(config, roster(8), Random(3), assignment, alphaId = "p2")

        assertEquals(WerewolfRole.WEREWOLF, game.players.first { it.id == "p2" }.role)
        assertEquals(game.players.first { it.id == "p2" }.seat, game.alphaWolfSeat)
    }

    @Test
    fun `头狼被指成了别的身份时报错`() {
        val assignment = mapOf("p3" to WerewolfRole.SEER)
        try {
            WerewolfGame.deal(WerewolfConfig(), roster(8), Random(1), assignment, alphaId = "p3")
            fail("头狼指成预言家不该发牌成功")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("头狼"))
        }
    }

    @Test
    fun `头狼不在名单里时报错`() {
        try {
            WerewolfGame.deal(WerewolfConfig(), roster(8), Random(1), alphaId = "ghost")
            fail("名单外的头狼不该发牌成功")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("名单"))
        }
    }

    @Test
    fun `标准档的上下文预算与旧常量一致`() {
        // 旧实现写死 3200；预算改成随发言档位缩放后，标准档不能悄悄变大——
        // 那会让老玩家的上下文突然多塞一截，请求变贵还可能撞模型上限
        assertEquals(3200, WerewolfSpeechLength.STANDARD.contextBudget)
        // 不限档按公式会塌到 800 字，但发言不裁了，原文反而更占地方——
        // 预算必须放大而不是缩小，不然不限档当天的发言会被大量丢掉
        assertEquals(8000, WerewolfSpeechLength.UNLIMITED.contextBudget)
    }
}
