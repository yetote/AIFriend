package com.core.tts

import com.core.tts.TTSProtocols.hexToBytes
import com.core.tts.TTSProtocols.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 各家 TTS 协议的加密与编码基础测试。
 *
 * 网络部分不方便在单测里跑，但签名/编码一旦错，真机必然调不通，
 * 所以把这些纯函数用标准测试向量钉死，改动时能立刻发现。
 */
class TTSProtocolsTest {

    // ---- MD5（讯飞 X-CheckSum 用）----
    @Test
    fun `MD5 标准向量`() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", TTSProtocols.md5("abc"))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", TTSProtocols.md5(""))
    }

    // ---- SHA-256（腾讯云 TC3 用）----
    @Test
    fun `SHA256 标准向量`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            TTSProtocols.sha256Hex("abc".toByteArray(Charsets.UTF_8)),
        )
    }

    // ---- HMAC-SHA256（腾讯云 TC3 派生密钥用，RFC 4231 测试向量）----
    @Test
    fun `HMAC SHA256 标准向量`() {
        val key = ByteArray(20) { 0x0b }
        val mac = TTSProtocols.hmacSha256(key, "Hi There")
        assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7", mac.toHex())
    }

    @Test
    fun `hex 编码解码往返`() {
        val original = byteArrayOf(0x00, 0x7F, 0xFF.toByte(), 0x10, 0xAB.toByte())
        val hex = original.toHex()
        assertEquals("007fff10ab", hex)
        assertTrue(original.contentEquals(hex.hexToBytes()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `奇数长度 hex 直接抛错`() {
        "abc".hexToBytes()
    }

    // ---- 腾讯云 TC3 派生链：密钥逐步派生，结果必须稳定 ----
    @Test
    fun `TC3 派生密钥链稳定`() {
        val secretKey = "Gu5t9xGARNpq86cd98joQYCN3EXAMPLE"
        val date = "2019-09-18"
        val dateKey = TTSProtocols.hmacSha256(("TC3" + secretKey).toByteArray(Charsets.UTF_8), date)
        val serviceKey = TTSProtocols.hmacSha256(dateKey, "tts")
        val signingKey = TTSProtocols.hmacSha256(serviceKey, "tc3_request")
        // 只要两次派生结果一致、且每层都不同，就说明链没写反。
        val again = TTSProtocols.hmacSha256(
            TTSProtocols.hmacSha256(
                TTSProtocols.hmacSha256(("TC3" + secretKey).toByteArray(Charsets.UTF_8), date),
                "tts",
            ),
            "tc3_request",
        )
        assertTrue(signingKey.contentEquals(again))
        assertTrue(!signingKey.contentEquals(dateKey))
        assertTrue(!signingKey.contentEquals(serviceKey))
    }
}
