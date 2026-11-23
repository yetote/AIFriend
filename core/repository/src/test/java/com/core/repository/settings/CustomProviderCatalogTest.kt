package com.core.repository.settings

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户自建接口注入目录后的查找行为。
 *
 * 这些接口是运行时从磁盘读出来的，不能像内置厂商那样写死在 companion 里；
 * 一旦注入漏了，设置页看得到、创建页却选不了，或者老好友改名后失联。
 */
class CustomProviderCatalogTest {

    @After
    fun tearDown() {
        // 目录是进程级的单例，测试之间必须还原，否则会影响同模块其他测试。
        AIProviderCatalog.installCustomGateways(emptyList())
    }

    @Test
    fun `注册后同时出现在聚合分组和全部服务商里`() {
        val first = custom("custom_a1b2c3d4", "家里的 Ollama")
        val second = custom("custom_e5f6a7b8", "中转站")
        AIProviderCatalog.installCustomGateways(listOf(first, second))

        assertTrue(AIProviderCatalog.gateways.containsAll(listOf(first, second)))
        assertTrue(AIProviderCatalog.all.containsAll(listOf(first, second)))
        assertEquals("家里的 Ollama", AIProviderCatalog.byId("custom_a1b2c3d4")?.name)
        assertEquals("custom_e5f6a7b8", AIProviderCatalog.byName("中转站")?.id)
    }

    @Test
    fun `改名后旧名字靠别名仍找到同一个接口`() {
        val renamed = custom(
            id = "custom_deadbeef",
            name = "新名字",
            aliases = listOf("老名字", "更早的名字")
        )
        AIProviderCatalog.installCustomGateways(listOf(renamed))

        assertEquals("custom_deadbeef", AIProviderCatalog.byName("老名字")?.id)
        assertEquals("custom_deadbeef", AIProviderCatalog.byName("更早的名字")?.id)
        assertEquals("自定义 OpenAI 兼容接口 · http://localhost:11434/v1", renamed.description)
    }

    @Test
    fun `只有自定义 id 才被认作自定义`() {
        assertTrue(AIProviderCatalog.isCustom(AIProviderCatalog.LEGACY_CUSTOM_ID))
        assertTrue(AIProviderCatalog.isCustom("custom_a1b2c3d4"))
        assertFalse(AIProviderCatalog.isCustom("siliconflow"))
        assertFalse(AIProviderCatalog.isCustom(null))
    }

    @Test
    fun `注入会替换上一批，不会越积越多`() {
        AIProviderCatalog.installCustomGateways(listOf(custom("custom_1", "第一条")))
        AIProviderCatalog.installCustomGateways(listOf(custom("custom_2", "第二条")))

        assertEquals(1, AIProviderCatalog.gateways.count { AIProviderCatalog.isCustom(it.id) })
        assertEquals(null, AIProviderCatalog.byId("custom_1"))
        assertEquals("第二条", AIProviderCatalog.byId("custom_2")?.name)
    }

    private fun custom(id: String, name: String, aliases: List<String> = emptyList()) = AIProvider(
        id = id,
        name = name,
        description = "自定义 OpenAI 兼容接口 · http://localhost:11434/v1",
        baseUrl = "http://localhost:11434/v1",
        consoleUrl = "",
        requiresKey = false,
        isGateway = true,
        thinkingSwitchable = false,
        aliases = aliases,
    )
}
