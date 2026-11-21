package com.feature.werewolf.prompt

import com.feature.werewolf.game.WerewolfConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 牌手风格画像的测试。
 *
 * 重点是两件事：
 * 1. 分配的**确定性**——续档时 buildAgents 重算，同一个好友必须拿到同一个风格，
 *    否则一暂停一继续，AI 的打牌性格会莫名其妙换了个人；
 * 2. **同桌尽量不重样**——八个人挤在两种风格里，碰撞感就没了。
 */
class PlayerStylesTest {

    private val ids = (1..8).map { "friend-$it" }

    @Test
    fun `空输入返回空映射`() {
        assertTrue(assignPlayerStyles(emptyList()).isEmpty())
    }

    @Test
    fun `同一个好友列表分配结果稳定`() {
        val first = assignPlayerStyles(ids)
        val second = assignPlayerStyles(ids)
        assertEquals(first, second)
    }

    @Test
    fun `好友顺序变了，个人的风格不变`() {
        val first = assignPlayerStyles(ids)
        val shuffled = assignPlayerStyles(ids.shuffled())
        ids.forEach { assertEquals("好友 $it 的风格不该随座位顺序变", first[it], shuffled[it]) }
    }

    @Test
    fun `人数不超过风格数时同桌不重复`() {
        val result = assignPlayerStyles(ids)
        assertEquals(ids.size, result.values.toSet().size)
        assertEquals(ids.toSet(), result.keys)
    }

    @Test
    fun `人数超过风格数时循环使用但不崩`() {
        val many = (1..20).map { "friend-$it" }
        val result = assignPlayerStyles(many)
        assertEquals(many.toSet(), result.keys)
        // 只有 8 种风格，20 人必然重复，但每种都要用到
        assertEquals(PlayerStyle.entries.toSet(), result.values.toSet())
    }

    @Test
    fun `手动指定优先于自动分配`() {
        val pinned = mapOf("friend-1" to PlayerStyle.AGGRESSIVE.id)
        val result = assignPlayerStyles(ids, pinned)
        assertEquals(PlayerStyle.AGGRESSIVE, result["friend-1"])
    }

    @Test
    fun `手动指定的非法 id 被忽略，退回自动`() {
        val result = assignPlayerStyles(ids, mapOf("friend-1" to "no-such-style"))
        assertTrue(result["friend-1"] != null)
        assertEquals(ids.size, result.values.toSet().size)
    }

    @Test
    fun `手动指定也参与不重复`() {
        // friend-1 被钉成 ANALYTICAL，其余人不能也分到 ANALYTICAL
        val pinned = mapOf("friend-1" to PlayerStyle.ANALYTICAL.id)
        val result = assignPlayerStyles(ids, pinned)
        val others = result.filterKeys { it != "friend-1" }.values
        assertNull("其他人不该再分到 ANALYTICAL", others.firstOrNull { it == PlayerStyle.ANALYTICAL })
    }

    @Test
    fun `按 id 查风格`() {
        PlayerStyle.entries.forEach { assertEquals(it, PlayerStyle.fromId(it.id)) }
        assertNull(PlayerStyle.fromId(null))
        assertNull(PlayerStyle.fromId("bad"))
    }

    @Test
    fun `null 不注入，画像正文含决策气质与读人习惯两段`() {
        assertNull(playerStyleBrief(null, WerewolfConfig()))
        val brief = playerStyleBrief(PlayerStyle.CAUTIOUS, WerewolfConfig())!!
        assertTrue(brief.contains("谨慎求证"))
        assertTrue(brief.contains("看人时"))
        assertTrue(brief.contains("优先于上面那段日常性格"))
    }
}
