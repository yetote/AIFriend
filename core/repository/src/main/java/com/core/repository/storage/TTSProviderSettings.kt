package com.core.repository.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.core.repository.settings.TTSProvider
import com.core.repository.settings.TTSProviderCatalog
import kotlinx.coroutines.flow.first

/**
 * 单个 TTS 服务商的凭证。
 *
 * 和 [AICredential] 分开存：chat 的 Key 和语音的 Key 是两回事
 * （MiniMax 的语音权限甚至要单独开通），共用一把会让用户困惑「为什么聊天能用、朗读不行」。
 * [baseUrl] 只在「自定义」时由用户填，其余用目录里的地址。
 */
data class TTSCredential(
    val providerId: String,
    val apiKey: String = "",
    val baseUrl: String = ""
) {
    fun resolvedBaseUrl(provider: TTSProvider): String =
        baseUrl.trim().ifBlank { provider.baseUrl }
}

/**
 * TTS 凭证的内存快照，供 [com.core.tts] 在同步代码里读取。
 * DataStore 是唯一持久化来源，这里只是避免每次朗读都挂起读盘。
 */
object TTSCredentials {
    @Volatile
    private var snapshot: Map<String, TTSCredential> = emptyMap()

    fun get(providerId: String): TTSCredential? = snapshot[providerId]

    fun apiKeyOf(providerId: String): String = snapshot[providerId]?.apiKey.orEmpty()

    fun baseUrlOf(provider: TTSProvider): String =
        snapshot[provider.id]?.resolvedBaseUrl(provider) ?: provider.baseUrl

    /** 有没有任意一家配置好了可用 Key（设置页/顶栏据此判断是否可用）。 */
    fun hasAny(): Boolean = snapshot.values.any { it.apiKey.isNotBlank() }

    internal fun replaceAll(credentials: List<TTSCredential>) {
        snapshot = credentials.associateBy { it.providerId }
    }

    internal fun put(credential: TTSCredential) {
        snapshot = snapshot + (credential.providerId to credential)
    }

    internal fun remove(providerId: String) {
        snapshot = snapshot - providerId
    }
}

/**
 * TTS 凭证持久化：和 [AIProviderSettings] 同一个 DataStore 文件，但用独立的 key 前缀。
 * Key 走 KeyStore 加密，加密失败时宁可不存也不写明文。
 */
class TTSProviderSettings(context: Context) {
    private val store = context.applicationContext.appPreferences

    suspend fun load(): List<TTSCredential> {
        val prefs = runCatching { store.data.first() }.getOrNull() ?: return emptyList()
        val credentials = TTSProviderCatalog.all.mapNotNull { provider ->
            val raw = prefs[keyName(provider.id)].orEmpty()
            val key = when {
                raw.isBlank() -> ""
                SecretCipher.isEncrypted(raw) -> SecretCipher.decrypt(raw).orEmpty()
                else -> raw // 理论上 TTS Key 没有明文历史版本，但按同样口径容错
            }
            val baseUrl = prefs[baseUrlName(provider.id)].orEmpty()
            if (key.isBlank() && baseUrl.isBlank()) {
                null
            } else {
                TTSCredential(providerId = provider.id, apiKey = key, baseUrl = baseUrl)
            }
        }
        TTSCredentials.replaceAll(credentials)
        return credentials
    }

    suspend fun save(credential: TTSCredential): Boolean {
        val encrypted = runCatching { SecretCipher.encrypt(credential.apiKey) }.getOrNull()
        if (credential.apiKey.isNotBlank() && encrypted == null) return false
        TTSCredentials.put(credential)
        return runCatching {
            store.edit { prefs ->
                if (encrypted != null) {
                    prefs[keyName(credential.providerId)] = encrypted
                } else {
                    prefs.remove(keyName(credential.providerId))
                }
                prefs[baseUrlName(credential.providerId)] = credential.baseUrl
            }
            true
        }.getOrDefault(false)
    }

    suspend fun clear(providerId: String) {
        TTSCredentials.remove(providerId)
        runCatching {
            store.edit { prefs ->
                prefs.remove(keyName(providerId))
                prefs.remove(baseUrlName(providerId))
            }
        }
    }

    // 独立前缀，绝不复用 ai_key_* / ai_base_url_*，避免污染 chat 凭证。
    private fun keyName(providerId: String) = stringPreferencesKey("tts_key_$providerId")
    private fun baseUrlName(providerId: String) = stringPreferencesKey("tts_base_url_$providerId")
}
