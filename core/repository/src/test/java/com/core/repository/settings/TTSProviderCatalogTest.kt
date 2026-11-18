package com.core.repository.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TTS 服务商目录的完整性：加新家时这些断言能挡住漏配凭证槽/协议分支。 */
class TTSProviderCatalogTest {

    @Test
    fun `目录覆盖主流五家加自定义`() {
        val ids = TTSProviderCatalog.all.map { it.id }
        assertTrue(ids.containsAll(listOf("minimax", "iflytek", "volcengine", "alibaba", "tencent")))
        assertTrue(ids.contains("custom"))
        assertEquals(6, ids.size)
    }

    @Test
    fun `id 唯一`() {
        val ids = TTSProviderCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `每家都声明了协议和默认音色`() {
        TTSProviderCatalog.all.forEach { p ->
            assertNotNull("${p.id} 缺默认音色", p.defaultVoice.takeIf { it.isNotBlank() })
            assertTrue("${p.id} 缺 consoleUrl", p.consoleUrl.isNotBlank() || p.id == "custom")
        }
    }

    @Test
    fun `凭证槽位与协议匹配`() {
        // 讯飞、火山要 AppID；腾讯云要 SecretKey；其余只要主 Key。
        assertNotNull(TTSProviderCatalog.Iflytek.appIdLabel)
        assertNotNull(TTSProviderCatalog.Volcengine.appIdLabel)
        assertNotNull(TTSProviderCatalog.Tencent.secretLabel)
        assertEquals(null, TTSProviderCatalog.MiniMax.secretLabel)
        assertEquals(null, TTSProviderCatalog.MiniMax.appIdLabel)
    }

    @Test
    fun `五家各有独立的协议实现`() {
        val protocols = listOf(
            TTSProviderCatalog.MiniMax,
            TTSProviderCatalog.Iflytek,
            TTSProviderCatalog.Volcengine,
            TTSProviderCatalog.Alibaba,
            TTSProviderCatalog.Tencent,
        ).map { it.protocol }.toSet()
        assertEquals(5, protocols.size)
    }

    @Test
    fun `按 id 查得到，默认回落 MiniMax`() {
        assertEquals(TTSProviderCatalog.MiniMax, TTSProviderCatalog.byId("minimax"))
        assertEquals(null, TTSProviderCatalog.byId("nope"))
        assertEquals(TTSProviderCatalog.MiniMax, TTSProviderCatalog.default())
    }
}
