package com.core.persona

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaCatalogTest {

    @Test
    fun `ids are unique and fields are usable`() {
        val ids = PersonaCatalog.all.map { it.id }
        assertEquals("模板 id 不能重复", ids.size, ids.toSet().size)
        PersonaCatalog.all.forEach { persona ->
            assertTrue("${persona.id} 缺少昵称", persona.name.isNotBlank())
            assertTrue("${persona.id} 缺少角色", persona.role.isNotBlank())
            assertTrue("${persona.id} 缺少设定", persona.prompt.isNotBlank())
            assertTrue("${persona.id} 缺少标签", persona.tags.isNotEmpty())
        }
    }

    @Test
    fun `every category has presets`() {
        PersonaCategory.entries.forEach { category ->
            assertTrue(
                "分类 ${category.label} 没有模板",
                PersonaCatalog.all.any { it.category == category }
            )
        }
    }

    @Test
    fun `filter narrows by category`() {
        val chats = PersonaCatalog.filter(PersonaCategory.CHAT, "")
        assertTrue(chats.isNotEmpty())
        assertTrue(chats.all { it.category == PersonaCategory.CHAT })
        assertTrue(chats.size < PersonaCatalog.all.size)
    }

    @Test
    fun `filter matches name role and tag`() {
        assertEquals(listOf("pro-code"), PersonaCatalog.filter(null, "老码").map { it.id })
        assertTrue(PersonaCatalog.filter(null, "翻译").any { it.id == "pro-translate" })
        assertTrue(PersonaCatalog.filter(null, "治愈").all { "治愈" in it.tags })
    }

    @Test
    fun `blank keyword returns everything`() {
        assertEquals(PersonaCatalog.all.size, PersonaCatalog.filter(null, "   ").size)
    }

    @Test
    fun `unknown keyword returns empty`() {
        assertTrue(PersonaCatalog.filter(null, "不存在的关键词zzz").isEmpty())
    }

    @Test
    fun `find returns preset by id`() {
        assertNotNull(PersonaCatalog.find("pro-product"))
        assertEquals(null, PersonaCatalog.find("nope"))
    }
}
