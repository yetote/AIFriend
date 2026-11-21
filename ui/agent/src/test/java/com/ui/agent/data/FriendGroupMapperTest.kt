package com.ui.agent.data

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 首页好友列表按厂商分组。
 *
 * 重点是顺序和兜底：好友多的组在前、组内顺序不被打乱、空来源归到「其他」。
 */
class FriendGroupMapperTest {

    private fun friend(name: String, source: String) = UIFriendListData(
        name = name,
        modelTag = "model",
        role = "角色",
        source = source,
        usageCount = "0 条消息",
        avatarBgColor = Color(0xFFF3F4F6),
        avatarTextColor = Color(0xFF374151)
    )

    @Test
    fun `好友多的厂商排在前面`() {
        val groups = listOf(
            friend("A", "Deepseek"),
            friend("B", "豆包"),
            friend("C", "Deepseek"),
            friend("D", "豆包"),
            friend("E", "豆包")
        ).groupByVendor()

        assertEquals(listOf("豆包", "Deepseek"), groups.map { it.name })
        assertEquals(listOf(3, 2), groups.map { it.friends.size })
    }

    @Test
    fun `组内保持原列表顺序`() {
        val groups = listOf(
            friend("第一个", "Deepseek"),
            friend("其他厂", "豆包"),
            friend("第二个", "Deepseek"),
            friend("第三个", "Deepseek")
        ).groupByVendor()

        val ds = groups.first { it.name == "Deepseek" }
        assertEquals(listOf("第一个", "第二个", "第三个"), ds.friends.map { it.name })
    }

    @Test
    fun `空来源归到其他且不算作独立厂商`() {
        val groups = listOf(
            friend("A", ""),
            friend("B", "   "),
            friend("C", "Deepseek")
        ).groupByVendor()

        assertEquals(listOf("其他", "Deepseek"), groups.map { it.name })
        assertEquals(listOf("A", "B"), groups.first { it.name == "其他" }.friends.map { it.name })
    }

    @Test
    fun `同数量时按厂商名排序保证稳定`() {
        val groups = listOf(
            friend("A", "豆包"),
            friend("B", "Deepseek")
        ).groupByVendor()

        // 都是 1 个，按名字比：Deepseek < 豆包
        assertEquals(listOf("Deepseek", "豆包"), groups.map { it.name })
    }

    @Test
    fun `空列表返回空分组`() {
        assertEquals(emptyList<UIFriendGroup>(), emptyList<UIFriendListData>().groupByVendor())
    }
}
