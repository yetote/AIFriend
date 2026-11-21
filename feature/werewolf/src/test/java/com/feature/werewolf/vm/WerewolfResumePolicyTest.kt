package com.feature.werewolf.vm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 后台续跑的接回判定。
 *
 * 核心不变量：只要局还在后台跑（detached + 引擎活着 + 同一局 + 没打完），
 * 点「接着打」就必须直接贴回牌桌，不能走重读档——那会打断正在进行的思考。
 * 任何一个条件不满足，则必须走常规续档，不能贴回一个已经停掉或别的局面。
 */
class WerewolfResumePolicyTest {

    private fun attach(
        detached: Boolean = true,
        running: Boolean = true,
        currentId: String? = "g1",
        requestedId: String = "g1",
        finished: Boolean = false,
    ) = shouldAttachDetachedGame(detached, running, currentId, requestedId, finished)

    @Test
    fun `后台还在跑的同一局直接接回`() {
        assertTrue(attach())
    }

    @Test
    fun `不是脱离状态走常规续档`() {
        // 没退到列表（人还在牌桌，或从未开始）——不该触发接回
        assertFalse(attach(detached = false))
    }

    @Test
    fun `引擎已停走常规续档`() {
        // playJob 不在跑：局已经被 stop/自己结束了，贴回去只会看到死界面
        assertFalse(attach(running = false))
    }

    @Test
    fun `点的是另一局时不接回`() {
        assertFalse(attach(currentId = "g1", requestedId = "g2"))
    }

    @Test
    fun `没有当前局时不接回`() {
        assertFalse(attach(currentId = null))
    }

    @Test
    fun `已结束的局不接回`() {
        assertFalse(attach(finished = true))
    }
}
