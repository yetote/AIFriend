package com.core.repository.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.core.repository.settings.AIProvider
import com.core.repository.settings.AIProviderCatalog
import com.core.repository.settings.CustomGroupCodec
import kotlinx.coroutines.flow.first

/** 同一自定义中转站下的单个分组令牌快照（只用于运行时路由与模型拉取）。 */
data class AICredentialGroup(
    val id: String,
    val name: String,
    val apiKey: String = "",
    val models: List<String> = emptyList(),
)

/**
 * 单个服务商的凭证。
 *
 * [baseUrl] 只在「自定义」服务商下由用户填写；其余服务商用目录里写好的地址。
 * [apiKey] 由用户自己申请并填写，不随 APK 分发 —— 这样既能随便换模型，
 * 也不会有内置 Key 被反编译盗刷的问题。
 *
 * [accessKeyId] / [accessKeySecret] 是**第二对凭证**，目前只有火山方舟要：
 * 它的管控面接口（查模型开通状态、查推理限额）不认聊天用的 API Key，
 * 只认 Access Key ID + Secret Access Key。
 */
data class AICredential(
    val providerId: String,
    val apiKey: String = "",
    val baseUrl: String = "",
    /** 火山的 Access Key ID。 */
    val accessKeyId: String = "",
    /** 火山的 Secret Access Key。 */
    val accessKeySecret: String = "",
    /** 自定义中转站的分组令牌列表；空表示单 Key 模式。 */
    val groups: List<AICredentialGroup> = emptyList(),
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

    fun apiKeyOf(providerId: String): String {
        val cred = snapshot[providerId] ?: return ""
        return cred.apiKey.ifBlank { cred.groups.firstOrNull { it.apiKey.isNotBlank() }?.apiKey.orEmpty() }
    }

    fun groupsOf(providerId: String): List<AICredentialGroup> = snapshot[providerId]?.groups.orEmpty()

    /**
     * 按模型解析实际要用的 API Key：
     * 1. 若模型带 `@groupId::` 前缀，精确命中对应分组的 Key；
     * 2. 若无前缀（单分组或老好友），先找包含该模型的分组，找不到再回落到主 Key。
     */
    fun apiKeyFor(providerId: String, model: String): String {
        val cred = snapshot[providerId] ?: return ""
        if (cred.groups.isEmpty()) return cred.apiKey
        val decoded = CustomGroupCodec.decode(model)
        if (decoded.groupId != null) {
            cred.groups.firstOrNull { it.id == decoded.groupId }?.let { return it.apiKey }
        }
        cred.groups.firstOrNull { decoded.rawModelId in it.models && it.apiKey.isNotBlank() }
            ?.let { return it.apiKey }
        return cred.groups.firstOrNull { it.apiKey.isNotBlank() }?.apiKey ?: cred.apiKey
    }

    fun baseUrlOf(provider: AIProvider): String =
        snapshot[provider.id]?.resolvedBaseUrl(provider) ?: provider.baseUrl

    fun replaceAll(credentials: List<AICredential>) {
        snapshot = credentials.associateBy { it.providerId }
    }

    fun put(credential: AICredential) {
        snapshot = snapshot + (credential.providerId to credential)
    }

    fun remove(providerId: String) {
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

    /** 自定义接口单独存一份列表；内置服务商仍按 id 一条条存。 */
    val custom = CustomProviderSettings(context)

    /** 从磁盘读一次，刷新内存快照。 */
    suspend fun load(): List<AICredential> {
        val prefs = runCatching { store.data.first() }.getOrNull() ?: return emptyList()
        // 明文待升级的项：键名 -> 明文。历史版本直接存明文，这里透明地升级成密文。
        val migrated = mutableListOf<Pair<String, String>>()

        /**
         * 读一个可能加密的值。
         *
         * 解不开（换机 / 清了 keystore）就当没配过，避免让用户看到一串乱码。
         */
        fun readSecret(key: androidx.datastore.preferences.core.Preferences.Key<String>): String {
            val raw = prefs[key].orEmpty()
            if (raw.isBlank()) return ""
            if (SecretCipher.isEncrypted(raw)) return SecretCipher.decrypt(raw).orEmpty()
            migrated += key.name to raw
            return raw
        }

        // 只读内置项：自定义接口的 Key 在 CustomProviderSettings 自己的列表里，
        // 跟着 all 再读一次会拿到空值，把真 Key 覆盖掉。
        val credentials = AIProviderCatalog.builtin.mapNotNull { provider ->
            val key = readSecret(keyName(provider.id))
            val baseUrl = prefs[baseUrlName(provider.id)].orEmpty()
            val accessKeyId = readSecret(accessKeyIdName(provider.id))
            val accessKeySecret = readSecret(accessKeySecretName(provider.id))
            if (key.isBlank() && baseUrl.isBlank() && accessKeyId.isBlank() && accessKeySecret.isBlank()) {
                null
            } else {
                AICredential(
                    providerId = provider.id,
                    apiKey = key,
                    baseUrl = baseUrl,
                    accessKeyId = accessKeyId,
                    accessKeySecret = accessKeySecret,
                )
            }
        }
        val customCredentials = custom.load()
        AICredentials.replaceAll(credentials + customCredentials)
        // 顺手把明文改写成密文；失败也不影响这次使用，下次启动再试。
        if (migrated.isNotEmpty()) {
            runCatching {
                store.edit { prefs ->
                    migrated.forEach { (name, plain) ->
                        prefs[stringPreferencesKey(name)] = SecretCipher.encrypt(plain)
                    }
                }
            }
        }
        return credentials + customCredentials
    }

    /**
     * 保存凭证。Key 经 KeyStore 加密后再落盘；
     * 加密失败说明系统密钥库不可用，此时宁可不保存也不写明文。
     */
    suspend fun save(credential: AICredential): Boolean {
        if (AIProviderCatalog.isCustom(credential.providerId)) {
            return custom.saveCredential(credential)
        }
        val encrypted = runCatching { SecretCipher.encrypt(credential.apiKey) }.getOrNull()
        if (credential.apiKey.isNotBlank() && encrypted == null) return false
        val encryptedId = runCatching { SecretCipher.encrypt(credential.accessKeyId) }.getOrNull()
        if (credential.accessKeyId.isNotBlank() && encryptedId == null) return false
        val encryptedSecret = runCatching { SecretCipher.encrypt(credential.accessKeySecret) }.getOrNull()
        if (credential.accessKeySecret.isNotBlank() && encryptedSecret == null) return false
        AICredentials.put(credential)
        return runCatching {
            store.edit { prefs ->
                if (encrypted != null) {
                    prefs[keyName(credential.providerId)] = encrypted
                } else {
                    prefs.remove(keyName(credential.providerId))
                }
                if (encryptedId != null) {
                    prefs[accessKeyIdName(credential.providerId)] = encryptedId
                } else {
                    prefs.remove(accessKeyIdName(credential.providerId))
                }
                if (encryptedSecret != null) {
                    prefs[accessKeySecretName(credential.providerId)] = encryptedSecret
                } else {
                    prefs.remove(accessKeySecretName(credential.providerId))
                }
                prefs[baseUrlName(credential.providerId)] = credential.baseUrl
            }
            true
        }.getOrDefault(false)
    }

    suspend fun clear(providerId: String) {
        if (AIProviderCatalog.isCustom(providerId)) {
            custom.clearCredential(providerId)
            return
        }
        AICredentials.remove(providerId)
        runCatching {
            store.edit { prefs ->
                prefs.remove(keyName(providerId))
                prefs.remove(baseUrlName(providerId))
                prefs.remove(modelName(providerId))
                prefs.remove(accessKeyIdName(providerId))
                prefs.remove(accessKeySecretName(providerId))
            }
        }
    }

    /** 当前生效的模型：按服务商记，换回来时还是上次那个。 */
    suspend fun selectedModel(providerId: String): String? {
        if (AIProviderCatalog.isCustom(providerId)) return custom.byId(providerId)?.selectedModel
        val prefs = runCatching { store.data.first() }.getOrNull() ?: return null
        return prefs[modelName(providerId)]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveSelectedModel(providerId: String, model: String) {
        if (AIProviderCatalog.isCustom(providerId)) {
            custom.saveSelectedModel(providerId, model)
            return
        }
        runCatching {
            store.edit { prefs -> prefs[modelName(providerId)] = model }
        }
    }

    private fun keyName(providerId: String) = stringPreferencesKey("ai_key_$providerId")
    private fun baseUrlName(providerId: String) = stringPreferencesKey("ai_base_url_$providerId")
    private fun modelName(providerId: String) = stringPreferencesKey("ai_model_$providerId")

    /** 火山 Access Key ID。 */
    private fun accessKeyIdName(providerId: String) = stringPreferencesKey("ai_ak_id_$providerId")

    /** 火山 Secret Access Key。 */
    private fun accessKeySecretName(providerId: String) =
        stringPreferencesKey("ai_ak_secret_$providerId")
}
