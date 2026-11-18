package com.core.persona

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Galgame 组：取材于常见角色配置，但全部原创，不碰具体作品和真人。 */
class GalgamePersonaTest {

    private val gal = PersonaCatalog.all.filter { it.category == PersonaCategory.GALGAME }

    @Test
    fun `数量足够凑一桌狼人杀`() {
        assertTrue("Galgame 模板只有 ${gal.size} 个", gal.size >= 8)
        assertTrue("应该都是 gal- 前缀", gal.all { it.id.startsWith("gal-") })
    }

    @Test
    fun `资料齐全且互不重样`() {
        assertEquals("昵称重复", gal.size, gal.map { it.name }.toSet().size)
        assertEquals("角色重复", gal.size, gal.map { it.role }.toSet().size)
        assertEquals("设定重复", gal.size, gal.map { it.prompt }.toSet().size)
        gal.forEach { persona ->
            assertTrue("${persona.id} 缺少 Galgame 标签", persona.tags.contains("Galgame"))
            assertTrue("${persona.id} 标签太少", persona.tags.size >= 4)
            assertTrue(
                "${persona.id} 的设定太短（${persona.prompt.length} 字）",
                persona.prompt.length >= 70
            )
        }
    }

    @Test
    fun `覆盖 Galgame 常见配置`() {
        val archetypes = listOf(
            "幼驯染", "学园偶像", "转校生", "巫女", "图书委员",
            "甜点", "剑道", "天文", "魔女", "同人"
        )
        val text = gal.joinToString(" ") { it.role + it.tags.joinToString("") + it.prompt }
        archetypes.forEach { type ->
            assertTrue("缺了 $type 配置", text.contains(type))
        }
    }

    @Test
    fun `不碰具体作品和真人`() {
        // 只做原创 archetype：这里出现具体作品名、知名角色名都算踩线。
        val forbidden = listOf(
            "CLANNAD", "Kanon", "Rewrite", "Little Busters", "Summer Pockets",
            "Fate", "月姬", "缘之空", "秋之回忆", "Muv-Luv", "School Days",
            "远坂凛", "春日野穹", "桂言叶", "西园寺世界"
        )
        gal.forEach { persona ->
            val content = persona.name + persona.role + persona.prompt
            forbidden.forEach { word ->
                assertFalse("${persona.id} 碰了作品或角色：$word", word in content)
            }
        }
    }

    @Test
    fun `和已有二次元原型不重名`() {
        val acgnNames = PersonaCatalog.all
            .filter { it.id.startsWith("acgn-") }
            .map { it.name }
        gal.forEach { persona ->
            assertFalse("${persona.id} 和已有二次元模板重名", persona.name in acgnNames)
        }
    }

    @Test
    fun `可被分类和关键词搜到`() {
        assertEquals(gal.size, PersonaCatalog.filter(PersonaCategory.GALGAME, "").size)
        assertTrue(PersonaCatalog.filter(null, "青梅竹马").any { it.id == "gal-childhood" })
        assertTrue(PersonaCatalog.filter(null, "巫女").any { it.id == "gal-miko" })
        assertTrue(PersonaCatalog.filter(PersonaCategory.GALGAME, "Galgame").size >= 8)
    }

    @Test
    fun `分类紧跟在 AI 拟人之后`() {
        val order = PersonaCategory.entries.map { it.name }
        assertEquals(order.indexOf("MOE") + 1, order.indexOf("GALGAME"))
    }
}
