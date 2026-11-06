package com.core.repository.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 AndroidKeyStore 里的 AES-256-GCM 密钥加解密用户填的 API Key。
 *
 * 为什么要这么绕：DataStore 存的是 protobuf 明文，root 过的机器、
 * `adb backup` 导出的包、云端备份文件都能直接把 Key 读出来。
 * 交给 KeyStore 之后，密钥本身躺在系统密钥库里，
 * 应用只能「请求它代加解密」，拿不到密钥材料，也就没法被拷走。
 *
 * 只用来保护密钥这种短文本，不要拿它加密聊天记录 ——
 * 每次加解密都要过一遍 keystore（部分机型走 TEE），不适合高频调用。
 *
 * 换机、清数据、卸载重装后 keystore 里的别名会消失，
 * 这时 [decrypt] 返回 null，调用方应当把它当成「没配过」让用户重新填，
 * 不要把异常抛到界面上。
 */
internal object SecretCipher {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "aifriend.secret.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** GCM 的 IV 固定 12 字节，随机生成，每次加密都不一样。 */
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    /** 密文格式带版本前缀，将来换算法时能认出老数据。 */
    private const val PREFIX = "v1"

    /** 这份密文是不是本类产出的（而不是历史遗留的明文）。 */
    fun isEncrypted(value: String): Boolean = value.startsWith("$PREFIX:")

    /**
     * 加密。失败时抛异常 —— 调用方宁可让用户重填，
     * 也不要把明文写回磁盘。
     */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        return "$PREFIX:${iv.b64()}:${cipherText.b64()}"
    }

    /** 解密。任何异常（包括换机后密钥消失）都返回 null，交由上层降级处理。 */
    fun decrypt(stored: String): String? {
        if (!isEncrypted(stored)) return null
        val parts = stored.split(':')
        if (parts.size != 3) return null
        return runCatching {
            val iv = parts[1].unB64() ?: return null
            val cipherText = parts[2].unB64() ?: return null
            if (iv.size != IV_LENGTH) return null
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            }
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrNull()
    }

    /**
     * 取（必要时创建）密钥。
     *
     * 加锁是必须的：首次保存时界面可能同时触发「校验」和「写盘」，
     * 两个线程都发现别名不存在就会各自生成一个，后写的那个把先写的密钥覆盖掉，
     * 先前加密的密文就再也解不开了。
     */
    @Synchronized
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // 不要求用户认证：设置页随时要读 Key，
                // 每次拉模型都弹指纹会让人没法用。
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.unB64(): ByteArray? =
        runCatching { Base64.decode(this, Base64.NO_WRAP) }.getOrNull()
}
