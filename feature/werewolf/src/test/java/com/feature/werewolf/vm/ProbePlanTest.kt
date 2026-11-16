package com.feature.werewolf.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「这一轮要发几次请求」的测试。
 *
 * 这段逻辑决定了开局前的等待时间有多长，也决定了会不会拿同一把 Key 反复验十几次。
 * 它没有网络、没有协程，纯函数，所以值得逐条钉死。
 */
class ProbePlanTest {

    private fun target(
        id: String,
        brand: String = "Deepseek",
        model: String = "deepseek-chat",
    ) = ProbeTarget(friendId = id, brand = brand, model = model)

    private fun ok(at: Long) = ApiCheck(state = ApiCheckState.OK, at = at)

    @Test
    fun `同厂商同模型只发一次请求，结果归属整组`() {
        // 12 人局按座位各测一次，等于拿同一把 Key 验十一遍：
        // 钱不多，但用户要干等十一倍的时间，而且结论必然一样
        val tasks = planProbes(
            targets = listOf(target("a"), target("b"), target("c")),
            known = emptyMap(),
            force = false,
            now = 1_000L,
        )

        assertEquals(1, tasks.size)
        assertEquals("组里每个座位都要拿到结果", listOf("a", "b", "c"), tasks.single().friendIds)
    }

    @Test
    fun `同厂商不同模型分别测`() {
        // 写错模型名是最常见的配置错误，所以模型名必须进 key
        val tasks = planProbes(
            targets = listOf(
                target("a", model = "deepseek-chat"),
                target("b", model = "deepseek-reasoner"),
            ),
            known = emptyMap(),
            force = false,
            now = 1_000L,
        )

        assertEquals(2, tasks.size)
        assertEquals(setOf("deepseek-chat", "deepseek-reasoner"), tasks.map { it.model }.toSet())
    }

    @Test
    fun `不同厂商的同一模型名也算两组`() {
        val tasks = planProbes(
            targets = listOf(
                target("a", brand = "Deepseek", model = "chat"),
                target("b", brand = "Kimi", model = "chat"),
            ),
            known = emptyMap(),
            force = false,
            now = 1_000L,
        )

        assertEquals(2, tasks.size)
    }

    @Test
    fun `刚测通过的跳过，不重复花钱`() {
        val tasks = planProbes(
            targets = listOf(target("a")),
            known = mapOf("a" to ok(1_000L)),
            force = false,
            now = 1_000L + 60_000L,
        )

        assertTrue("十分钟内的通过结果应该复用", tasks.isEmpty())
    }

    @Test
    fun `超过有效期要重测`() {
        // 用户可能中途去设置页换了 Key，早先的「已连通」不能当成现在也通
        val tasks = planProbes(
            targets = listOf(target("a")),
            known = mapOf("a" to ok(0L)),
            force = false,
            now = API_CHECK_TTL_MS + 1,
        )

        assertEquals(1, tasks.size)
    }

    @Test
    fun `失败的结果从不复用`() {
        // 用户再点一次检测，多半就是因为刚才红了；直接返回旧结论等于按钮点不动
        val tasks = planProbes(
            targets = listOf(target("a")),
            known = mapOf("a" to ApiCheck(state = ApiCheckState.FAILED, message = "Key 无效", at = 1_000L)),
            force = false,
            now = 1_000L,
        )

        assertEquals(1, tasks.size)
    }

    @Test
    fun `点按钮强制重测时，绿的和红的都重来`() {
        val tasks = planProbes(
            targets = listOf(target("a"), target("b", model = "other")),
            known = mapOf(
                "a" to ok(1_000L),
                "b" to ApiCheck(state = ApiCheckState.FAILED, at = 1_000L),
            ),
            force = true,
            now = 1_000L,
        )

        assertEquals(2, tasks.size)
    }

    @Test
    fun `组里有人没有有效结果时整组要测`() {
        // 同组共享结果，正常不会出现半绿半空；但真出现了必须重测，
        // 不能因为「组里有一个绿的」就把另一个座位漏过去
        val tasks = planProbes(
            targets = listOf(target("a"), target("b")),
            known = mapOf("a" to ok(1_000L)),
            force = false,
            now = 1_000L,
        )

        assertEquals(1, tasks.size)
        assertEquals(listOf("a", "b"), tasks.single().friendIds)
    }

    @Test
    fun `空输入不产生任何请求`() {
        assertTrue(planProbes(emptyList(), emptyMap(), force = false, now = 0L).isEmpty())
    }
}
