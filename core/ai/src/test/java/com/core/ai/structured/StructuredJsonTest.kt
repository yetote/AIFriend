package com.core.ai.structured

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 括号扫描的边界情况。
 *
 * 这里每一条都对应一个真实的模型行为：写代码块、加前言、在字符串里提到座位号、
 * 吐一个嵌套结构、一次给出两个方案。扫描写错就会把 JSON 切碎，
 * 表现是「明明回复里有 JSON 却解析失败」，很难从日志上看出来。
 */
class StructuredJsonTest {

    @Test
    fun `干净的 JSON`() {
        val objects = StructuredJson.balancedObjects("""{"target": 3}""")
        assertEquals(listOf("""{"target": 3}"""), objects)
    }

    @Test
    fun `markdown 代码块包裹`() {
        val objects = StructuredJson.balancedObjects("```json\n{\"target\": 3}\n```")
        assertEquals(listOf("""{"target": 3}"""), objects)
    }

    @Test
    fun `字符串里的花括号不参与配对`() {
        val raw = """{"reason": "我投 {3} 号", "target": 3}"""
        val objects = StructuredJson.balancedObjects(raw)
        assertEquals(1, objects.size)
        assertEquals(raw, objects.single())
    }

    @Test
    fun `字符串里的转义引号不会截断`() {
        val raw = """{"reason": "他说\"就投3号\"", "target": 3}"""
        val objects = StructuredJson.balancedObjects(raw)
        assertEquals(1, objects.size)
        assertEquals(raw, objects.single())
    }

    @Test
    fun `嵌套对象只返回最外层`() {
        val raw = """{"target": {"seat": 3}}"""
        val objects = StructuredJson.balancedObjects(raw)
        assertEquals(1, objects.size)
        assertEquals(raw, objects.single())
    }

    @Test
    fun `多个顶层对象按出现顺序返回`() {
        val objects = StructuredJson.balancedObjects("""方案A {"target": 3} 方案B {"target": 5}""")
        assertEquals(listOf("""{"target": 3}""", """{"target": 5}"""), objects)
    }

    @Test
    fun `没有 JSON 时返回空`() {
        assertTrue(StructuredJson.balancedObjects("我觉得应该投 3 号").isEmpty())
    }

    @Test
    fun `括号没闭合时返回空`() {
        assertTrue(StructuredJson.balancedObjects("""{"target": 3""").isEmpty())
    }
}
