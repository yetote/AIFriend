package com.core.repository.balance

/**
 * SHA-256 与 HMAC-SHA256。
 *
 * **为什么不用 `java.security` 里的实现**：Android 单元测试跑在 mockable android.jar 上，
 * 那份 jar 里 `javax.crypto.Mac` 和 `java.security.MessageDigest` 都是抛异常的桩——
 * `Mac.getInstance("HMAC-SHA256")` 直接抛 `NoSuchAlgorithmException`。
 * 也就是说，用 JDK 实现的话，签名逻辑在单元测试里**根本跑不起来**，
 * 只能靠手点一次真机来验证签名对不对。
 *
 * 手写一份反而更好：纯函数、没有平台依赖、每一步都能用标准测试向量钉住
 * （见 `Sha256Test` 里的 RFC 6234 / RFC 4231 向量）。
 * 性能上完全够用——一个请求签一次名，几百微秒的事。
 */
internal object Sha256 {

    /**
     * 轮函数的 64 个常量。
     *
     * 全部显式 `toInt()`：超过 Int 范围的字面量 Kotlin 会推成 Long，
     * 不转的话 `intArrayOf` 直接编译不过。
     */
    private val K = intArrayOf(
        0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(),
        0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6.toInt(), 0x240ca1cc.toInt(),
        0x2de92c6f.toInt(), 0x4a7484aa.toInt(), 0x5cb0a9dc.toInt(), 0x76f988da.toInt(),
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351.toInt(), 0x14292967.toInt(),
        0x27b70a85.toInt(), 0x2e1b2138.toInt(), 0x4d2c6dfc.toInt(), 0x53380d13.toInt(),
        0x650a7354.toInt(), 0x766a0abb.toInt(), 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070.toInt(),
        0x19a4c116.toInt(), 0x1e376c08.toInt(), 0x2748774c.toInt(), 0x34b0bcb5.toInt(),
        0x391c0cb3.toInt(), 0x4ed8aa4a.toInt(), 0x5b9cca4f.toInt(), 0x682e6ff3.toInt(),
        0x748f82ee.toInt(), 0x78a5636f.toInt(), 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )

    /** 摘要长度（字节）：SHA-256 是 256 位。 */
    private const val DIGEST_LENGTH = 32

    /** 压缩函数的分块长度（字节）。 */
    private const val BLOCK_LENGTH = 64

    /** SHA-256 的初始向量（前 8 个自然数的小端平方根的分数部分）。 */
    private val INITIAL = intArrayOf(
        0x6a09e667.toInt(), 0xbb67ae85.toInt(), 0x3c6ef372.toInt(), 0xa54ff53a.toInt(),
        0x510e527f.toInt(), 0x9b05688c.toInt(), 0x1f83d9ab.toInt(), 0x5be0cd19.toInt(),
    )

    /** SHA-256 摘要。 */
    fun digest(message: ByteArray): ByteArray {
        val h = INITIAL.copyOf()
        val padded = pad(message)
        val w = IntArray(64)

        var offset = 0
        while (offset < padded.size) {
            for (i in 0 until 16) {
                val j = offset + i * 4
                w[i] = ((padded[j].toInt() and 0xFF) shl 24) or
                    ((padded[j + 1].toInt() and 0xFF) shl 16) or
                    ((padded[j + 2].toInt() and 0xFF) shl 8) or
                    (padded[j + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val x = w[i - 15]
                val y = w[i - 2]
                val s0 = rotr(x, 7) xor rotr(x, 18) xor (x ushr 3)
                val s1 = rotr(y, 17) xor rotr(y, 19) xor (y ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = h[0]
            var b = h[1]
            var c = h[2]
            var d = h[3]
            var e = h[4]
            var f = h[5]
            var g = h[6]
            var hh = h[7]

            for (i in 0 until 64) {
                val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = hh + s1 + ch + K[i] + w[i]
                val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj

                hh = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }

            h[0] += a
            h[1] += b
            h[2] += c
            h[3] += d
            h[4] += e
            h[5] += f
            h[6] += g
            h[7] += hh

            offset += BLOCK_LENGTH
        }

        val out = ByteArray(DIGEST_LENGTH)
        for (i in 0 until 8) {
            out[i * 4] = ((h[i] ushr 24) and 0xFF).toByte()
            out[i * 4 + 1] = ((h[i] ushr 16) and 0xFF).toByte()
            out[i * 4 + 2] = ((h[i] ushr 8) and 0xFF).toByte()
            out[i * 4 + 3] = (h[i] and 0xFF).toByte()
        }
        return out
    }

    /**
     * HMAC-SHA256。
     *
     * `H((K' xor opad) || H((K' xor ipad) || m))`，
     * 密钥超过块长度就先用 SHA-256 压一遍，再补零到 64 字节。
     */
    fun hmac(key: ByteArray, message: ByteArray): ByteArray {
        var normalizedKey = key
        if (normalizedKey.size > BLOCK_LENGTH) {
            normalizedKey = digest(normalizedKey)
        }
        val paddedKey = ByteArray(BLOCK_LENGTH)
        normalizedKey.copyInto(paddedKey)

        val inner = ByteArray(BLOCK_LENGTH + message.size)
        val outer = ByteArray(BLOCK_LENGTH + DIGEST_LENGTH)
        for (i in 0 until BLOCK_LENGTH) {
            inner[i] = (paddedKey[i].toInt() xor 0x36).toByte()
            outer[i] = (paddedKey[i].toInt() xor 0x5c).toByte()
        }
        message.copyInto(inner, BLOCK_LENGTH)

        val innerHash = digest(inner)
        innerHash.copyInto(outer, BLOCK_LENGTH)
        return digest(outer)
    }

    /**
     * 补齐到块长度的倍数。
     *
     * 末尾先放一个 1，再补 0，最后 8 字节是**原始长度（比特）的大端表示**。
     */
    private fun pad(message: ByteArray): ByteArray {
        val bitLength = message.size.toLong() * 8
        val paddedSize = ((message.size + 9 + BLOCK_LENGTH - 1) / BLOCK_LENGTH) * BLOCK_LENGTH
        val padded = ByteArray(paddedSize)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[paddedSize - 1 - i] = ((bitLength ushr (8 * i)) and 0xFF).toByte()
        }
        return padded
    }

    private fun rotr(value: Int, bits: Int): Int =
        (value ushr bits) or (value shl (32 - bits))
}
