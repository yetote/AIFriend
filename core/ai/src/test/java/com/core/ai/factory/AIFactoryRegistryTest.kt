package com.core.ai.factory

import com.core.ai.entity.AIGateway
import com.core.repository.settings.AIProvider
import com.core.repository.settings.AIProviderCatalog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 服务商目录与工厂注册表的一致性。
 *
 * 这两份数据是分开维护的：目录（AIProviderCatalog）决定设置页展示哪几家、
 * 凭证按哪个 id 存取；工厂（[AIFactory.aiMap]）决定按品牌名能不能真的
 * new 出一个实例。加了一家却忘了另一头，编译期完全看不出来 ——
 * 要等用户真的选中那个品牌，createAI 返回 null，才会在聊天页炸。
 */
class AIFactoryRegistryTest {

    @Test
    fun `每个官方厂商都能被工厂创建`() {
        val missing = AIProviderCatalog.official.filter { provider ->
            AIFactory.getAIClassByName(provider.name) == null
        }

        assertTrue(
            "这些厂商在目录里有，但 AIFactory.aiMap 没注册：${missing.map { it.name }}",
            missing.isEmpty()
        )
    }

    @Test
    fun `品牌名能创建出实例且公司名对得上`() {
        val factory = AIFactory()
        AIProviderCatalog.official.forEach { provider ->
            val ai = factory.createAI(provider.name)
            assertNotNull("createAI(\"${provider.name}\") 返回了 null", ai)
            assertEquals(provider.name, ai!!.brandName)
        }
    }

    @Test
    fun `id 和品牌名都不重复`() {
        val ids = AIProviderCatalog.all.map { it.id }
        val names = AIProviderCatalog.all.map { it.name }

        assertEquals("服务商 id 有重复：$ids", ids.size, ids.distinct().size)
        assertEquals("服务商品牌名有重复：$names", names.size, names.distinct().size)
    }

    @After
    fun tearDown() {
        AIProviderCatalog.installCustomGateways(emptyList())
    }

    @Test
    fun `运行时注册的自定义接口能按品牌名和旧名字创建`() {
        val custom = AIProvider(
            id = "custom_deadbeef",
            name = "我的中转",
            description = "自定义接口",
            baseUrl = "https://example.com/v1",
            consoleUrl = "",
            requiresKey = false,
            isGateway = true,
            aliases = listOf("旧中转"),
        )
        AIProviderCatalog.installCustomGateways(listOf(custom))
        val factory = AIFactory()

        val current = factory.createAI("我的中转")
        assertTrue(current is AIGateway)
        assertEquals("我的中转", current!!.brandName)

        // 老好友/老存档还存着改名前的品牌名，必须也能创建。
        assertTrue(factory.createAI("旧中转") is AIGateway)
        assertTrue("旧名字不该混进创建页品牌列表", "旧中转" !in AIFactory.brandNames())
    }

    @Test
    fun `工厂里没有目录之外的多余品牌`() {
        val known = AIProviderCatalog.all.map { it.name }.toSet()
        val extra = AIFactory.brandNames().filterNot { it in known }

        assertTrue("aiMap 注册了目录里没有的品牌：$extra", extra.isEmpty())
    }
}
