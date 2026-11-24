package com.core.repository.balance

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SHA-256 / HMAC-SHA256 的标准测试向量。
 *
 * 这段实现是手写的（原因见 [Sha256] 的注释：Android 单元测试里
 * `javax.crypto` 全是抛异常的桩），所以正确性必须由**公认的标准向量**来钉，
 * 不能靠「跑出来看着像」——签名的对错在服务端只会回一句 InvalidAccessKey。
 *
 * 向量来源：FIPS 180-4（SHA-256）与 RFC 4231（HMAC-SHA256）。
 */
class Sha256Test {

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test
    fun `空串的 SHA-256`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            hex(Sha256.digest(ByteArray(0))),
        )
    }

    @Test
    fun `abc 的 SHA-256`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            hex(Sha256.digest("abc".toByteArray(Charsets.UTF_8))),
        )
    }

    @Test
    fun `两块的 SHA-256`() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hex(
                Sha256.digest(
                    "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".toByteArray(Charsets.UTF_8)
                )
            ),
        )
    }

    @Test
    fun `HMAC RFC4231 用例 1`() {
        val key = ByteArray(20) { 0x0b }
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            hex(Sha256.hmac(key, "Hi There".toByteArray(Charsets.UTF_8))),
        )
    }

    @Test
    fun `HMAC RFC4231 用例 2`() {
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            hex(
                Sha256.hmac(
                    "Jefe".toByteArray(Charsets.UTF_8),
                    "what do ya want for nothing?".toByteArray(Charsets.UTF_8),
                )
            ),
        )
    }

    @Test
    fun `HMAC RFC4231 用例 3`() {
        val key = ByteArray(20) { 0xaa.toByte() }
        val data = ByteArray(50) { 0xdd.toByte() }
        assertEquals(
            "773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe",
            hex(Sha256.hmac(key, data)),
        )
    }

    @Test
    fun `HMAC 密钥比块长时要先哈希一次`() {
        val key = ByteArray(131) { 0xaa.toByte() }
        assertEquals(
            "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
            hex(
                Sha256.hmac(
                    key,
                    "Test Using Larger Than Block-Size Key - Hash Key First"
                        .toByteArray(Charsets.UTF_8),
                )
            ),
        )
    }
}
