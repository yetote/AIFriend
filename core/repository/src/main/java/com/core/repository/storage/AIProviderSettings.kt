package com.core.repository.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.core.repository.settings.AIProvider
import com.core.repository.settings.AIProviderCatalog
import kotlinx.coroutines.flow.first

/**
 * 单个服务商的凭证。
 *
 * [baseUrl] 只在「自定义」服务商下由用户填写；其余服务商用目录里写好的地址。
 * [apiKey] 由用户自己申请并填写，不随 APK 分发 —— 这样既能随便换模型，
 * 也不会有内置 Key 被反编译盗刷的问题。
 */
data class AICredential(
    val providerId: String,
    val apiKey: String = "",
    val baseUrl: String = ""
) {
    /** 实际请求用的 baseUrl：自定义优先用户填写，其余回落到目录。 */
    fun resolvedBaseUrl(provider: AIProvider): String =
        baseUrl.trim().ifBlank { provider.baseUrl }
}

/**
 * 凭证的内存快照。
 *
 * 网络请求发生在 [com.core.ai] 的同步代码里（构造 [com.core.repository.ApiConfig] 时），
 * 那里没有挂起上下文，所以这里留一份 volatile 快照供同步读取；
 * DataStore 仍然是唯一的持久化来源，读写都经由 [AIProviderSettings]。
 */
object AICredentials {

    @Volatile
    private var snapshot: Map<String, AICredential> = emptyMap()

    fun get(providerId: String): AICredential? = snapshot[providerId]

    fun apiKeyOf(providerId: String): String = snapshot[providerId]?.apiKey.orEmpty()

    fun baseUrlOf(provider: AIProvider): String =
        snapshot[provider.id]?.resolvedBaseUrl(provider) ?: provider.baseUrl

    internal fun replaceAll(credentials: List<AICredential>) {
        snapshot = credentials.associateBy { it.providerId }
    }

    internal fun put(credential: AICredential) {
        snapshot = snapshot + (credential.providerId to credential)
    }

    internal fun remove(providerId: String) {
        snapshot = snapshot - providerId
    }
}

/**
 * 用户自填的模型服务凭证，存在全 App 共用的 Preferences DataStore 里。
 *
 * 一个服务商一条记录：Key 和该服务商上次选中的模型分别存，
 * 换服务商时各自的配置不会互相覆盖。
 */
class AIProviderSettings(context: Context) {

    private val store = context.applicationContext.appPreferences

    /** 从磁盘读一次，刷新内存快照。 */
    suspend fun load(): List<AICredential> {
        val prefs = runCatching { store.data.first() }.getOrNull() ?: return emptyList()
        val migrated = mutableListOf<Pair<String, String>>()
        val credentials = AIProviderCatalog.all.mapNotNull { provider ->
            val raw = prefs[keyName(provider.id)].orEmpty()
            // 历史版本直接存明文，这里透明地升级成密文。
            // 解不开（换机 / 清了 keystore）就当没配过，避免让用户看到一串乱码。
            val key = when {
                raw.isBlank() -> ""
                SecretCipher.isEncrypted(raw) -> SecretCipher.decrypt(raw).orEmpty()
                else -> {
                    migrated += provider.id to raw
                    raw
                }
            }
            val baseUrl = prefs[baseUrlName(provider.id)].orEmpty()
            if (key.isBlank() && baseUrl.isBlank()) {
                null
            } else {
                AICredential(providerId = provider.id, apiKey = key, baseUrl = baseUrl)
            }
        }
        AICredentials.replaceAll(credentials)
        // 顺手把明文改写成密文；失败也不影响这次使用，下次启动再试。
        if (migrated.isNotEmpty()) {
            runCatching {
                store.edit { prefs ->
                    migrated.forEach { (providerId, plain) ->
                        prefs[keyName(providerId)] = SecretCipher.encrypt(plain)
                    }
                }
            }
        }
        return credentials
    }

    /**
     * 保存凭证。Key 经 KeyStore 加密后再落盘；
     * 加密失败说明系统密钥库不可用，此时宁可不保存也不写明文。
     */
    suspend fun save(credential: AICredential): Boolean {
        val encrypted = runCatching { SecretCipher.encrypt(credential.apiKey) }.getOrNull()
        if (credential.apiKey.isNotBlank() && encrypted == null) return false
        AICredentials.put(credential)
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
        AICredentials.remove(providerId)
        runCatching {
            store.edit { prefs ->
                prefs.remove(keyName(providerId))
                prefs.remove(baseUrlName(providerId))
            }
        }
    }

    /** 当前生效的模型：按服务商记，换回来时还是上次那个。 */
    suspend fun selectedModel(providerId: String): String? {
        val prefs = runCatching { store.data.first() }.getOrNull() ?: return null
        return prefs[modelName(providerId)]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveSelectedModel(providerId: String, model: String) {
        runCatching {
            store.edit { prefs -> prefs[modelName(providerId)] = model }
        }
    }

    private fun keyName(providerId: String) = stringPreferencesKey("ai_key_$providerId")
    private fun baseUrlName(providerId: String) = stringPreferencesKey("ai_base_url_$providerId")
    private fun modelName(providerId: String) = stringPreferencesKey("ai_model_$providerId")
}
