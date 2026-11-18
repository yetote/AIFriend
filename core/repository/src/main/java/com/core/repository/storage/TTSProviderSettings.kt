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
 * 各家鉴权字段不一样，用三个槽统一：
 * - [apiKey]：主凭证（MiniMax Key / 火山 Access Token / 腾讯 SecretId / 讯飞 APIKey / 阿里 DashScope Key）
 * - [apiSecret]：第二凭证（只有腾讯云 SecretKey 用）
 * - [appId]：应用标识（讯飞 AppID / 火山 AppID）
 *
 * 和 [AICredential] 分开存：chat 的 Key 和语音的 Key 是两回事
 * （MiniMax 的语音权限甚至要单独开通），共用一把会让用户困惑「为什么聊天能用、朗读不行」。
 * [baseUrl] 只在「自定义」时由用户填，其余用目录里的地址。
 */
data class TTSCredential(
    val providerId: String,
    val apiKey: String = "",
    val baseUrl: String = "",
    val apiSecret: String = "",
    val appId: String = "",
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
 * Key/Secret 走 KeyStore 加密，加密失败时宁可不存也不写明文。
 */
class TTSProviderSettings(context: Context) {
    private val store = context.applicationContext.appPreferences

    suspend fun load(): List<TTSCredential> {
        val prefs = runCatching { store.data.first() }.getOrNull() ?: return emptyList()
        val credentials = TTSProviderCatalog.all.mapNotNull { provider ->
            val key = decrypt(prefs[keyName(provider.id)].orEmpty())
            val secret = decrypt(prefs[secretName(provider.id)].orEmpty())
            val appId = prefs[appIdName(provider.id)].orEmpty()
            val baseUrl = prefs[baseUrlName(provider.id)].orEmpty()
            if (key.isBlank() && secret.isBlank() && appId.isBlank() && baseUrl.isBlank()) {
                null
            } else {
                TTSCredential(
                    providerId = provider.id,
                    apiKey = key,
                    apiSecret = secret,
                    appId = appId,
                    baseUrl = baseUrl,
                )
            }
        }
        TTSCredentials.replaceAll(credentials)
        return credentials
    }

    suspend fun save(credential: TTSCredential): Boolean {
        val encryptedKey = runCatching { SecretCipher.encrypt(credential.apiKey) }.getOrNull()
        val encryptedSecret = runCatching { SecretCipher.encrypt(credential.apiSecret) }.getOrNull()
        if (credential.apiKey.isNotBlank() && encryptedKey == null) return false
        if (credential.apiSecret.isNotBlank() && encryptedSecret == null) return false
        TTSCredentials.put(credential)
        return runCatching {
            store.edit { prefs ->
                writeEncrypted(prefs, keyName(credential.providerId), encryptedKey)
                writeEncrypted(prefs, secretName(credential.providerId), encryptedSecret)
                if (credential.appId.isNotBlank()) {
                    prefs[appIdName(credential.providerId)] = credential.appId
                } else {
                    prefs.remove(appIdName(credential.providerId))
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
                prefs.remove(secretName(providerId))
                prefs.remove(appIdName(providerId))
                prefs.remove(baseUrlName(providerId))
            }
        }
    }

    private fun decrypt(raw: String): String = when {
        raw.isBlank() -> ""
        SecretCipher.isEncrypted(raw) -> SecretCipher.decrypt(raw).orEmpty()
        else -> raw // 容错：理论上是明文历史数据也照读
    }

    private fun writeEncrypted(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        encrypted: String?,
    ) {
        if (encrypted != null) prefs[key] = encrypted else prefs.remove(key)
    }

    // 独立前缀，绝不复用 ai_key_* / ai_base_url_*，避免污染 chat 凭证。
    private fun keyName(providerId: String) = stringPreferencesKey("tts_key_$providerId")
    private fun secretName(providerId: String) = stringPreferencesKey("tts_secret_$providerId")
    private fun appIdName(providerId: String) = stringPreferencesKey("tts_app_id_$providerId")
    private fun baseUrlName(providerId: String) = stringPreferencesKey("tts_base_url_$providerId")
}
