package com.core.repository.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Base URL 的传输安全边界。
 *
 * 这条规则一旦被改坏，用户的 Bearer Key 就会明文上网，
 * 所以用测试把它钉死，避免以后「顺手放宽一下」。
 */
class BaseUrlValidationTest {

    @Test
    fun `接受公网 https 地址`() {
        assertNull(validateBaseUrl("https://api.siliconflow.cn/v1"))
        assertNull(validateBaseUrl("https://openrouter.ai/api/v1"))
        assertNull(validateBaseUrl("HTTPS://Example.COM/v1"))
    }

    @Test
    fun `拒绝公网 http 地址`() {
        // 这是最核心的一条：明文传输会让 Key 被中间人拿到
        assertNotNull(validateBaseUrl("http://api.example.com/v1"))
        assertNotNull(validateBaseUrl("http://123.45.67.89/v1"))
        assertNotNull(validateBaseUrl("HTTP://api.example.com"))
    }

    @Test
    fun `放行局域网 http 地址`() {
        // 自建 Ollama / One-API 走内网，流量出不了局域网
        assertNull(validateBaseUrl("http://localhost:11434/v1"))
        assertNull(validateBaseUrl("http://127.0.0.1:8080/v1"))
        assertNull(validateBaseUrl("http://192.168.1.10:3000/v1"))
        assertNull(validateBaseUrl("http://10.0.0.5:8000/v1"))
        assertNull(validateBaseUrl("http://10.0.2.2:8080/v1"))
        assertNull(validateBaseUrl("http://172.16.3.7:8080/v1"))
        assertNull(validateBaseUrl("http://172.31.0.1/v1"))
    }

    @Test
    fun `172 段只放行私有区间`() {
        // 172.15 和 172.32 是公网地址，不能被当成内网放过去
        assertNotNull(validateBaseUrl("http://172.15.0.1/v1"))
        assertNotNull(validateBaseUrl("http://172.32.0.1/v1"))
    }

    @Test
    fun `拒绝空值和缺协议`() {
        assertEquals("请填写 Base URL", validateBaseUrl(""))
        assertEquals("请填写 Base URL", validateBaseUrl("   "))
        assertNotNull(validateBaseUrl("api.example.com/v1"))
        assertNotNull(validateBaseUrl("ftp://example.com"))
    }

    @Test
    fun `拒绝末尾斜杠`() {
        // 直接拼接会得到 https://x/v1//chat/completions
        assertNotNull(validateBaseUrl("https://api.example.com/v1/"))
    }

    @Test
    fun `目录里的内置地址全部合法`() {
        AIProviderCatalog.all
            .filter { it.baseUrl.isNotBlank() }
            .forEach { provider ->
                assertNull(
                    "${provider.name} 的 baseUrl 不合规：${provider.baseUrl}",
                    validateBaseUrl(provider.baseUrl)
                )
            }
    }
}
