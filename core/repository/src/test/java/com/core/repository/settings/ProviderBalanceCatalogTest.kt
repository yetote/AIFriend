package com.core.repository.settings

import com.core.repository.balance.BalanceMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 服务商目录里的余量查询配置。
 *
 * 这些配置是各家余额能不能查到的唯一依据，配错了不会编译报错，
 * 只会在用户点「刷新」时看到一句莫名其妙的失败——所以要在测试里钉住。
 */
class ProviderBalanceCatalogTest {

    @Test
    fun `智谱的额度接口要试裸 Key`() {
        val spec = AIProviderCatalog.Zhipu.balance
        assertNotNull("智谱应当支持额度查询", spec)
        assertTrue(
            "智谱的额度接口不认 Bearer 前缀，裸 Key 必须排在第一个",
            spec!!.authHeaderValues.firstOrNull() == "{key}",
        )
    }

    @Test
    fun `火山走签名 POST`() {
        val spec = AIProviderCatalog.Doubao.balance
        assertNotNull("火山应当支持查询", spec)
        assertEquals(BalanceMethod.SIGNED_POST, spec!!.method)
        assertEquals("ListModelActivations", spec.action)
        assertEquals("Access Key ID", AIProviderCatalog.Doubao.accessKeyLabel)
    }

    @Test
    fun `百炼没有公开的余额接口就别配`() {
        // 配一个半真半假的地址过去，查出来是 404，比明说「没这个接口」更让人困惑
        assertNull(AIProviderCatalog.Qianwen.balance)
    }

    @Test
    fun `配了余额接口的厂商地址都得是 https`() {
        AIProviderCatalog.all.forEach { provider ->
            provider.balance?.let { spec ->
                assertTrue(
                    "${provider.name} 的余额接口必须是 https",
                    spec.url.startsWith("https://"),
                )
                assertTrue(
                    "${provider.name} 的余额接口要带鉴权方式",
                    spec.authHeaderValues.isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun `只有火山要第二对凭证`() {
        val withAccessKey = AIProviderCatalog.all.filter { it.accessKeyLabel != null }
        assertEquals(1, withAccessKey.size)
        assertEquals("豆包", withAccessKey.first().name)
    }
}
