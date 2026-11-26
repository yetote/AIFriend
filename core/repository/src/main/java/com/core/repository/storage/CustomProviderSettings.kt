package com.core.repository.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.core.repository.settings.AIProvider
import com.core.repository.settings.AIProviderCatalog
import com.core.repository.settings.CustomNameDefaults
import com.core.repository.settings.RelayStationDetector
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * 用户自建的一条 OpenAI 兼容接口。
 *
 * [id] 一经生成不再变：好友和狼人杀存档里存的是品牌名，改名靠 [aliases]
 * 兜住老数据，而凭证、模型列表全都跟着 id 走。
 */
@Serializable
data class CustomApiConfig(
    val id: String,
    /** 当前展示名，也是新建好友时写进 brand 的字符串。 */
    val name: String,
    /** 内存里的明文；落盘前经 KeyStore 加密，见 [StoredCustomApi.key]。 */
    val apiKey: String = "",
    val baseUrl: String = "",
    /** 上次「测试并保存」拉到的模型，断网时当兜底列表用。 */
    val models: List<String> = emptyList(),
    val selectedModel: String = "",
    /** 改名前的名字，让老好友/老存档还能找到这条配置。 */
    val aliases: List<String> = emptyList(),
)

/** 落盘形状：Key 必须是密文，绝不写明文。 */
@Serializable
private data class StoredCustomApi(
    val id: String,
    val name: String,
    val key: String = "",
    val baseUrl: String = "",
    val models: List<String> = emptyList(),
    val selectedModel: String = "",
    val aliases: List<String> = emptyList(),
) {
    fun toConfig(): CustomApiConfig = CustomApiConfig(
        id = id,
        name = name,
        // 换机 / 清了 keystore 后解不开，当没配过，不把异常抛到界面上。
        apiKey = key.takeIf { it.isNotBlank() }?.let { SecretCipher.decrypt(it) }.orEmpty(),
        baseUrl = baseUrl,
        models = models,
        selectedModel = selectedModel,
        aliases = aliases,
    )
}

/**
 * 多个自定义接口的存取。
 *
 * 不塞进 [AIProviderCatalog] 的静态目录：那些是随 APK 分发的固定配置，
 * 而这里的条目是用户运行时增删的，且带自己的 Key / Base URL / 模型列表。
 * 读盘后把每条转成一个 [AIProvider] 注入目录，请求层和创建页就都能按普通
 * 服务商对待它，不需要到处写「如果是自定义就……」的分支。
 */
class CustomProviderSettings(context: Context) {

    private val store = context.applicationContext.appPreferences
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Volatile
    private var configs: List<CustomApiConfig> = emptyList()

    fun all(): List<CustomApiConfig> = configs

    fun byId(id: String): CustomApiConfig? = configs.firstOrNull { it.id == id }

    fun modelsOf(id: String): List<String> = byId(id)?.models.orEmpty()

    /** 读盘、迁移老配置、注入目录，返回供 [AICredentials] 合并的凭证。 */
    suspend fun load(): List<AICredential> {
        val prefs = runCatching { store.data.first() }.getOrNull()
        val raw = prefs?.get(KEY)?.orEmpty().orEmpty()
        val stored = if (raw.isBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<StoredCustomApi>>(raw) }
                .getOrDefault(emptyList())
        }
        configs = stored.map { it.toConfig() }
        migrateLegacy(prefs)
        installToCatalog()
        return configs.map { AICredential(it.id, it.apiKey, it.baseUrl) }
    }

    /** 旧版本只有一条 id="custom" 的配置，透明升级成列表里的第一项。 */
    private suspend fun migrateLegacy(prefs: Preferences?) {
        if (prefs == null || configs.any { it.id == AIProviderCatalog.LEGACY_CUSTOM_ID }) return
        val oldKey = prefs[LEGACY_KEY]?.orEmpty().orEmpty()
        val oldUrl = prefs[LEGACY_BASE_URL]?.orEmpty().orEmpty()
        val oldModel = prefs[LEGACY_MODEL]?.orEmpty().orEmpty()
        if (oldKey.isBlank() && oldUrl.isBlank() && oldModel.isBlank()) return

        val plainKey = when {
            oldKey.isBlank() -> ""
            SecretCipher.isEncrypted(oldKey) -> SecretCipher.decrypt(oldKey).orEmpty()
            else -> oldKey
        }
        val migrated = configs + CustomApiConfig(
            id = AIProviderCatalog.LEGACY_CUSTOM_ID,
            name = "自定义",
            apiKey = plainKey,
            baseUrl = oldUrl,
            models = if (oldModel.isBlank()) emptyList() else listOf(oldModel),
            selectedModel = oldModel,
        )
        // 写新格式成功才删老字段，失败就留着，下次启动还能再试。
        if (persist(migrated)) {
            store.edit { prefsNow ->
                prefsNow.remove(LEGACY_KEY)
                prefsNow.remove(LEGACY_BASE_URL)
                prefsNow.remove(LEGACY_MODEL)
            }
        }
    }

    suspend fun add(name: String = CustomNameDefaults.DEFAULT_NAME): CustomApiConfig? {
        val base = name.trim().ifBlank { CustomNameDefaults.DEFAULT_NAME }
        val occupied = configs.map { it.name }.toSet()
        val unique = RelayStationDetector.uniqueName(
            desired = base,
            occupiedNames = occupied,
            builtinNames = AIProviderCatalog.builtin.map { it.name }.toSet(),
        )
        val config = CustomApiConfig(
            id = "${AIProviderCatalog.LEGACY_CUSTOM_ID}_${UUID.randomUUID().toString().take(8)}",
            name = unique,
        )
        return if (persist(configs + config)) config else null
    }

    /**
     * 改名。旧名字默认记进 [CustomApiConfig.aliases]，老好友不用跟着改数据库。
     * 首次连接测试自动命名时可传 `recordAlias = false`，避免未用过的默认占位名污染别名表。
     * @return 旧名字；失败（撞名 / 写盘失败）返回 null。
     */
    suspend fun rename(id: String, newName: String, recordAlias: Boolean = true): String? {
        val current = byId(id) ?: return null
        val name = newName.trim().ifBlank { return null }
        if (name == current.name) return current.name

        val next = configs.map { config ->
            if (config.id == id) {
                val updatedAliases = if (recordAlias) {
                    (config.aliases + config.name).distinct()
                } else {
                    config.aliases
                }
                config.copy(name = name, aliases = updatedAliases)
            } else {
                config
            }
        }
        // 两条自定义接口同名会让「按品牌名找厂商」产生歧义。
        if (next.map { it.name }.distinct().size != next.size) return null
        return if (persist(next)) current.name else null
    }

    suspend fun remove(id: String): Boolean {
        val next = configs.filterNot { it.id == id }
        if (next.size == configs.size) return false
        if (!persist(next)) return false
        AICredentials.remove(id)
        return true
    }

    /** 保存 Key / 地址。UI 上的「测试并保存」走这里。 */
    suspend fun saveCredential(credential: AICredential): Boolean {
        if (byId(credential.providerId) == null) return false
        val next = configs.map { config ->
            if (config.id == credential.providerId) {
                config.copy(apiKey = credential.apiKey, baseUrl = credential.baseUrl)
            } else {
                config
            }
        }
        if (!persist(next)) return false
        AICredentials.put(credential)
        return true
    }

    /** 只清 Key，保留这条接口和它的地址 / 模型列表。 */
    suspend fun clearCredential(id: String) {
        val next = configs.map { config ->
            if (config.id == id) config.copy(apiKey = "", baseUrl = "") else config
        }
        if (persist(next)) AICredentials.remove(id)
    }

    suspend fun saveModels(id: String, models: List<String>) {
        persist(configs.map { config ->
            if (config.id == id) config.copy(models = models) else config
        })
    }

    suspend fun saveSelectedModel(id: String, model: String) {
        persist(configs.map { config ->
            if (config.id == id) config.copy(selectedModel = model) else config
        })
    }

    /** 写盘 + 刷新目录。Key 加密失败时整体失败，绝不退回去写明文。 */
    private suspend fun persist(next: List<CustomApiConfig>): Boolean {
        val encrypted = next.map { config ->
            val key = if (config.apiKey.isBlank()) {
                ""
            } else {
                runCatching { SecretCipher.encrypt(config.apiKey) }.getOrNull() ?: return false
            }
            StoredCustomApi(
                id = config.id,
                name = config.name,
                key = key,
                baseUrl = config.baseUrl,
                models = config.models,
                selectedModel = config.selectedModel,
                aliases = config.aliases,
            )
        }
        val written = runCatching {
            store.edit { it[KEY] = json.encodeToString(encrypted) }
        }.isSuccess
        if (!written) return false
        configs = next
        installToCatalog()
        return true
    }

    private fun installToCatalog() {
        AIProviderCatalog.installCustomGateways(configs.map { config ->
            AIProvider(
                id = config.id,
                name = config.name,
                description = "自定义 OpenAI 兼容接口 · ${
                    config.baseUrl.ifBlank { "未填写地址" }
                }",
                baseUrl = config.baseUrl,
                consoleUrl = "",
                requiresKey = false,
                fallbackModels = config.models,
                isGateway = true,
                // 网关后面的模型什么都可能有，不猜它支不支持思考开关。
                thinkingSwitchable = false,
                aliases = config.aliases,
            )
        })
    }

    private companion object {
        val KEY = stringPreferencesKey("custom_apis_v1")
        val LEGACY_KEY = stringPreferencesKey("ai_key_custom")
        val LEGACY_BASE_URL = stringPreferencesKey("ai_base_url_custom")
        val LEGACY_MODEL = stringPreferencesKey("ai_model_custom")
    }
}
