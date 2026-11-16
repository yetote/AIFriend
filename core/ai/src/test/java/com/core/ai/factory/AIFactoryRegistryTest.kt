package com.core.ai.factory

import com.core.repository.settings.AIProviderCatalog
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

    @Test
    fun `工厂里没有目录之外的多余品牌`() {
        val known = AIProviderCatalog.all.map { it.name }.toSet()
        val extra = AIFactory.brandNames().filterNot { it in known }

        assertTrue("aiMap 注册了目录里没有的品牌：$extra", extra.isEmpty())
    }
}
