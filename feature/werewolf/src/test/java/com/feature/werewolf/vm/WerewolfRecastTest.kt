package com.feature.werewolf.vm

import com.core.ai.model.Friend
import com.feature.werewolf.game.WerewolfPlayer
import com.feature.werewolf.game.WerewolfRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「换人续档」的纯逻辑测试。
 *
 * 这里写错不会有编译错误，只会在真机上表现为「换人之后身份乱了 / 历史说话人对不上」
 * 这种很难查的问题，所以逐条钉死。
 */
class WerewolfRecastTest {

    private fun friend(id: String, name: String = id) = Friend(
        id = id,
        name = name,
        role = "玩家",
        prompt = "",
        brand = "Deepseek",
        company = "深度求索",
        model = "deepseek-chat",
    )

    private fun player(seat: Int, id: String, name: String = id) = WerewolfPlayer(
        seat = seat,
        id = id,
        name = name,
        role = WerewolfRole.VILLAGER,
    )

    // ------------------------------------------------------------------
    // 候选筛选
    // ------------------------------------------------------------------

    @Test
    fun `候选排除本局在座的人，连原座位自己也不重复出现`() {
        // 3 号连不上，候选里不能再列出 1/2/3——同一个好友坐两个位子，牌桌就乱了
        val players = listOf(player(1, "a"), player(2, "b"), player(3, "c"))
        val friends = listOf(friend("a"), friend("b"), friend("c"), friend("d"), friend("e"))
        val result = recastCandidates(listOf(3), players, friends, emptyMap())

        assertEquals(listOf("d", "e"), result.getValue(3).map { it.id })
    }

    @Test
    fun `候选里探活绿的排前面，红的排最后`() {
        val players = listOf(player(1, "a"))
        val friends = listOf(friend("red"), friend("green"), friend("unknown"))
        val checks = mapOf(
            "red" to ApiCheck(ApiCheckState.FAILED),
            "green" to ApiCheck(ApiCheckState.OK),
        )
        val result = recastCandidates(listOf(1), players, friends, checks).getValue(1)

        assertEquals(listOf("green", "unknown", "red"), result.map { it.id })
        assertTrue("绿的标 reachable", result.first { it.id == "green" }.reachable)
        assertFalse("红的 reachable=false", result.first { it.id == "red" }.reachable)
    }

    @Test
    fun `没有失败座位时返回空映射`() {
        assertTrue(recastCandidates(emptyList(), listOf(player(1, "a")), listOf(friend("b")), emptyMap()).isEmpty())
    }

    @Test
    fun `候选池为空时该座位拿到空列表，界面据此提示去加好友`() {
        // 本局就坐满了全部好友：没有备用的人可换
        val players = listOf(player(1, "a"), player(2, "b"))
        val result = recastCandidates(listOf(2), players, listOf(friend("a"), friend("b")), emptyMap())
        assertTrue(result.getValue(2).isEmpty())
    }

    // ------------------------------------------------------------------
    // 座位替换
    // ------------------------------------------------------------------

    @Test
    fun `换人只改 id 名字模型，身份死活天数全保留`() {
        val dead = player(3, "old", "旧人").copy(
            alive = false,
            deathCause = com.feature.werewolf.game.DeathCause.WOLF_KILL,
            deathDay = 2,
            role = WerewolfRole.WITCH,
        )
        val players = listOf(player(1, "a"), dead)
        val friendsById = mapOf("new" to friend("new", "新人"))
        val result = applyRecast(players, friendsById, mapOf(3 to "new"))

        val swapped = result.first { it.seat == 3 }
        assertEquals("new", swapped.id)
        assertEquals("新人", swapped.name)
        assertEquals("deepseek-chat", swapped.model)
        assertEquals("身份不变", WerewolfRole.WITCH, swapped.role)
        assertFalse("死活不变", swapped.alive)
        assertEquals("死因不变", com.feature.werewolf.game.DeathCause.WOLF_KILL, swapped.deathCause)
        assertEquals("死亡日不变", 2, swapped.deathDay)
        assertEquals("没被换的座位原样", "a", result.first { it.seat == 1 }.id)
    }

    @Test
    fun `找不到新好友时该座位原样保留，不塞假人`() {
        val players = listOf(player(1, "a"))
        val result = applyRecast(players, emptyMap(), mapOf(1 to "ghost"))
        assertEquals("a", result.single().id)
    }

    @Test
    fun `换人映射里没提到的座位不受影响`() {
        val players = listOf(player(1, "a"), player(2, "b"))
        val result = applyRecast(players, mapOf("x" to friend("x")), mapOf(2 to "x"))
        assertEquals("a", result.first { it.seat == 1 }.id)
        assertEquals("x", result.first { it.seat == 2 }.id)
    }

    // ------------------------------------------------------------------
    // 缺人检测：存档引用了已经不在的好友
    // ------------------------------------------------------------------

    @Test
    fun `好友列表里查无此人的座位要挑出来`() {
        // 3 号的小咪被删了：存档里还指着它，但好友列表里已经没有这个人了
        val players = listOf(player(1, "a"), player(2, "b"), player(3, "gone"))
        val friendsById = mapOf("a" to friend("a"), "b" to friend("b"))

        val missing = missingFriendSeats(players, friendsById, humanSeat = null)

        assertEquals(listOf(3), missing.map { it.seat })
    }

    @Test
    fun `人类座位不算缺人`() {
        // 人类本来就不在好友列表里，他不是「被删了的好友」
        val players = listOf(player(1, "__self__"), player(2, "a"))
        val friendsById = mapOf("a" to friend("a"))

        val missing = missingFriendSeats(players, friendsById, humanSeat = 1)

        assertTrue("人类座位不能被当成缺人", missing.isEmpty())
    }

    @Test
    fun `好友都在时没有缺人`() {
        val players = listOf(player(1, "a"), player(2, "b"))
        val friendsById = mapOf("a" to friend("a"), "b" to friend("b"))

        assertTrue(missingFriendSeats(players, friendsById, humanSeat = null).isEmpty())
    }

    // ------------------------------------------------------------------
    // 原因文案：两种坏法必须分开说
    // ------------------------------------------------------------------

    @Test
    fun `好友不在了要说已经不在了`() {
        val reason = recastReason(
            missing = listOf(player(3, "gone", "小咪")),
            failedNames = emptyList(),
            failedDetail = "",
            searchStyle = false,
        )

        assertTrue("要说清楚是好友没了：$reason", reason.contains("已经不在了"))
        assertTrue("要点名是几号：$reason", reason.contains("3 号小咪"))
    }

    @Test
    fun `连不上和不在了分成两句话`() {
        val reason = recastReason(
            missing = listOf(player(3, "gone", "小咪")),
            failedNames = listOf("阿明"),
            failedDetail = "Key 无效",
            searchStyle = false,
        )

        assertTrue("两种坏法都要说：$reason", reason.contains("已经不在了"))
        assertTrue("连不上的也要说：$reason", reason.contains("连不上"))
        assertTrue("探活原因要带上：$reason", reason.contains("Key 无效"))
    }

    @Test
    fun `搜索式下说成做不到联网而不是连不上`() {
        val reason = recastReason(
            missing = emptyList(),
            failedNames = listOf("阿明"),
            failedDetail = "不会联网",
            searchStyle = true,
        )

        assertTrue("搜索式的说法要分开：$reason", reason.contains("联网检索"))
        assertFalse("搜索式下不说连不上：$reason", reason.contains("连不上"))
    }

    @Test
    fun `两种都没问题时不该拼出空话`() {
        val reason = recastReason(
            missing = emptyList(),
            failedNames = emptyList(),
            failedDetail = "",
            searchStyle = false,
        )

        assertEquals("", reason)
    }
}
