package com.core.repository.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.core.repository.settings.AIProvider
import com.core.repository.settings.AIProviderCatalog
import com.core.repository.settings.CustomGroupCodec
import com.core.repository.settings.CustomNameDefaults
import com.core.repository.settings.RelayStationDetector
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * 同一家自定义中转站下的一个分组令牌（如「默认」「VIP」「Claude」）。
 *
 * 每个分组有独立的 API Key 与模型列表；共用外层 [CustomApiConfig.baseUrl]。
 */
@Serializable
data class CustomApiGroup(
    val id: String,
    val name: String,
    /** 内存里的明文；落盘前经 KeyStore 加密。 */
    val apiKey: String = "",
    /** 该分组上次拉到的原始模型列表（不含 `@groupId::` 前缀）。 */
    val models: List<String> = emptyList(),
)

/**
 * 用户自建的一条 OpenAI 兼容接口。
 *
 * [id] 一经生成不再变：好友和狼人杀存档里存的是品牌名，改名靠 [aliases]
 * 兜住老数据，而凭证、模型列表全都跟着 id 走。
 *
 * 同一家中转站可以包含多个 [groups]（不同分组 Key）；只有一个分组时与旧版完全等价。
 */
@Serializable
data class CustomApiConfig(
    val id: String,
    /** 当前展示名，也是新建好友时写进 brand 的字符串。 */
    val name: String,
    /** 主分组（或旧版单 Key）内存里的明文；落盘前经 KeyStore 加密，见 [StoredCustomApi.key]。 */
    val apiKey: String = "",
    val baseUrl: String = "",
    /** 汇总后的可选模型列表（多分组时带 `@groupId::` 前缀，断网时当兜底列表用）。 */
    val models: List<String> = emptyList(),
    val selectedModel: String = "",
    /** 改名前的名字，让老好友/老存档还能找到这条配置。 */
    val aliases: List<String> = emptyList(),
    /** 同一中转站下的多个分组令牌；空表示旧版单 Key 配置。 */
    val groups: List<CustomApiGroup> = emptyList(),
) {
    /** 规范化后的分组列表：旧版单 Key 自动视为单个「默认」分组。 */
    fun effectiveGroups(): List<CustomApiGroup> = if (groups.isNotEmpty()) {
        groups
    } else {
        listOf(
            CustomApiGroup(
                id = DEFAULT_GROUP_ID,
                name = DEFAULT_GROUP_NAME,
                apiKey = apiKey,
                models = models.map(CustomGroupCodec::rawModelId).distinct(),
            )
        )
    }

    /** 汇总全部可见模型 ID：仅 1 组时保留原始模型名；≥2 组时带 `@groupId::` 前缀以便精确选组。 */
    fun aggregatedModelIds(): List<String> {
        val active = effectiveGroups()
        if (active.size <= 1) {
            return active.firstOrNull()?.models.orEmpty().distinct()
        }
        return active.flatMap { group ->
            group.models.distinct().map { raw -> CustomGroupCodec.encode(group.id, raw) }
        }
    }

    /** 转成运行时凭证快照（含各分组 Key 与模型清单）。 */
    fun toCredential(): AICredential {
        val active = effectiveGroups()
        val primaryKey = active.firstOrNull { it.apiKey.isNotBlank() }?.apiKey ?: apiKey
        return AICredential(
            providerId = id,
            apiKey = primaryKey,
            baseUrl = baseUrl,
            groups = active.map { g ->
                AICredentialGroup(
                    id = g.id,
                    name = g.name,
                    apiKey = g.apiKey,
                    models = g.models,
                )
            },
        )
    }

    companion object {
        const val DEFAULT_GROUP_ID = "default"
        const val DEFAULT_GROUP_NAME = "默认"
    }
}

@Serializable
private data class StoredCustomApiGroup(
    val id: String,
    val name: String,
    val key: String = "",
    val models: List<String> = emptyList(),
) {
    fun toGroup(): CustomApiGroup = CustomApiGroup(
        id = id,
        name = name,
        apiKey = key.takeIf { it.isNotBlank() }?.let { SecretCipher.decrypt(it) }.orEmpty(),
        models = models,
    )
}

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
    val groups: List<StoredCustomApiGroup> = emptyList(),
) {
    fun toConfig(): CustomApiConfig {
        val plainPrimary = key.takeIf { it.isNotBlank() }?.let { SecretCipher.decrypt(it) }.orEmpty()
        val decodedGroups = groups.map { it.toGroup() }
        val base = CustomApiConfig(
            id = id,
            name = name,
            apiKey = decodedGroups.firstOrNull { it.apiKey.isNotBlank() }?.apiKey ?: plainPrimary,
            baseUrl = baseUrl,
            models = models,
            selectedModel = selectedModel,
            aliases = aliases,
            groups = decodedGroups,
        )
        return if (decodedGroups.isNotEmpty()) {
            base.copy(models = base.aggregatedModelIds())
        } else {
            base
        }
    }
}

/** 合并同地址自定义接口的结果。 */
data class CustomMergeResult(
    val targetId: String,
    val targetName: String,
    val addedGroup: CustomApiGroup,
    val removedSourceName: String,
    val removedSourceAliases: List<String>,
)

/**
 * 多个自定义接口的存取。
 *
 * 不塞进 [AIProviderCatalog] 的静态目录：那些是随 APK 分发的固定配置，
 * 而这里的条目是用户运行时增删的，且带自己的 Key / Base URL / 模型列表 / 分组令牌。
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

    fun modelsOf(id: String): List<String> = byId(id)?.aggregatedModelIds().orEmpty()

    fun groupsOf(id: String): List<CustomApiGroup> = byId(id)?.effectiveGroups().orEmpty()

    /** 查找已保存了相同归一化 Base URL 的另一条自定义接口，用于自动合并同站分组。 */
    fun findByBaseUrl(baseUrl: String, excludeId: String? = null): CustomApiConfig? {
        val normalized = CustomGroupCodec.normalizeBaseUrl(baseUrl)
        if (normalized.isEmpty()) return null
        return configs.firstOrNull { config ->
            config.id != excludeId &&
                CustomGroupCodec.normalizeBaseUrl(config.baseUrl) == normalized
        }
    }

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
        return configs.map { it.toCredential() }
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

    /** 在指定自定义接口下新增一个空白分组（共享同一 Base URL，使用独立 API Key）。 */
    suspend fun addGroup(providerId: String, preferredName: String? = null): CustomApiGroup? {
        val current = byId(providerId) ?: return null
        val existing = current.effectiveGroups()
        val groupName = uniqueGroupName(preferredName, existing)
        val created = CustomApiGroup(
            id = newGroupId(),
            name = groupName,
        )
        val updated = normalizeConfig(current.copy(groups = existing + created))
        val next = configs.map { if (it.id == providerId) updated else it }
        if (!persist(next)) return null
        AICredentials.put(updated.toCredential())
        return created
    }

    /** 修改某个分组的展示名；分组 ID 不变，已创建的好友完全不受影响。 */
    suspend fun renameGroup(providerId: String, groupId: String, newName: String): Boolean {
        val current = byId(providerId) ?: return false
        val trimmed = newName.trim().take(20)
        if (trimmed.isEmpty()) return false
        val existing = current.effectiveGroups()
        if (existing.none { it.id == groupId }) return false
        if (existing.any { it.id != groupId && it.name.equals(trimmed, ignoreCase = true) }) return false

        val updatedGroups = existing.map { g -> if (g.id == groupId) g.copy(name = trimmed) else g }
        val updated = normalizeConfig(current.copy(groups = updatedGroups))
        val next = configs.map { if (it.id == providerId) updated else it }
        if (!persist(next)) return false
        AICredentials.put(updated.toCredential())
        return true
    }

    /** 删除一个分组（至少保留 1 个分组）。 */
    suspend fun removeGroup(providerId: String, groupId: String): Boolean {
        val current = byId(providerId) ?: return false
        val existing = current.effectiveGroups()
        if (existing.size <= 1) return false
        val remaining = existing.filterNot { it.id == groupId }
        if (remaining.size == existing.size) return false

        val updated = normalizeConfig(current.copy(groups = remaining))
        val next = configs.map { if (it.id == providerId) updated else it }
        if (!persist(next)) return false
        AICredentials.put(updated.toCredential())
        return true
    }

    /** 保存某自定义接口中指定分组的 Key、模型列表以及共享 Base URL。 */
    suspend fun saveGroupCredential(
        providerId: String,
        groupId: String,
        baseUrl: String,
        apiKey: String,
        models: List<String>,
        groupName: String? = null,
    ): Boolean {
        val current = byId(providerId) ?: return false
        val existing = current.effectiveGroups()
        val rawModels = models.map(CustomGroupCodec::rawModelId).distinct()
        val updatedGroups = if (existing.any { it.id == groupId }) {
            existing.map { g ->
                if (g.id == groupId) {
                    val nextName = groupName?.trim()?.takeIf { it.isNotEmpty() } ?: g.name
                    g.copy(name = nextName, apiKey = apiKey, models = rawModels)
                } else {
                    g
                }
            }
        } else {
            existing + CustomApiGroup(
                id = groupId,
                name = uniqueGroupName(groupName, existing),
                apiKey = apiKey,
                models = rawModels,
            )
        }
        val updated = normalizeConfig(
            current.copy(
                baseUrl = baseUrl,
                groups = updatedGroups,
            )
        )
        val next = configs.map { if (it.id == providerId) updated else it }
        if (!persist(next)) return false
        AICredentials.put(updated.toCredential())
        return true
    }

    /**
     * 把同一 Base URL 的临时/重复自定义接口 [sourceId] 作为新分组并入 [targetId]，并删除 [sourceId]。
     *
     * 如果 [targetId] 里已经存在同一把 [apiKey] 的分组，则直接更新该分组的模型列表，避免重复分组。
     */
    suspend fun mergeInto(
        sourceId: String,
        targetId: String,
        apiKey: String,
        models: List<String>,
        preferredGroupName: String? = null,
        recordSourceAlias: Boolean = false,
    ): CustomMergeResult? {
        if (sourceId == targetId) return null
        val source = byId(sourceId) ?: return null
        val target = byId(targetId) ?: return null
        val rawModels = models.map(CustomGroupCodec::rawModelId).distinct()
        val existing = target.effectiveGroups()

        val duplicateByKey = apiKey.isNotBlank() && existing.any { it.apiKey == apiKey }
        val targetGroup: CustomApiGroup
        val nextGroups: List<CustomApiGroup>
        if (duplicateByKey) {
            val matched = existing.first { it.apiKey == apiKey }
            targetGroup = matched.copy(models = rawModels)
            nextGroups = existing.map { if (it.id == matched.id) targetGroup else it }
        } else {
            val groupName = uniqueGroupName(preferredGroupName, existing)
            targetGroup = CustomApiGroup(
                id = newGroupId(),
                name = groupName,
                apiKey = apiKey,
                models = rawModels,
            )
            nextGroups = existing + targetGroup
        }

        val nextAliases = if (recordSourceAlias) {
            (target.aliases + source.aliases + source.name)
                .filterNot { it == target.name }
                .distinct()
        } else {
            target.aliases
        }
        val updatedTarget = normalizeConfig(
            target.copy(
                groups = nextGroups,
                aliases = nextAliases,
            )
        )
        val next = configs
            .filterNot { it.id == sourceId }
            .map { if (it.id == targetId) updatedTarget else it }
        if (!persist(next)) return null
        AICredentials.remove(sourceId)
        AICredentials.put(updatedTarget.toCredential())
        return CustomMergeResult(
            targetId = updatedTarget.id,
            targetName = updatedTarget.name,
            addedGroup = targetGroup,
            removedSourceName = source.name,
            removedSourceAliases = source.aliases,
        )
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

    /** 保存单分组 / 默认分组的 Key 与地址。UI 上的「测试并保存」走这里或 [saveGroupCredential]。 */
    suspend fun saveCredential(credential: AICredential): Boolean {
        val current = byId(credential.providerId) ?: return false
        val existing = current.effectiveGroups()
        val firstId = existing.first().id
        val updatedGroups = existing.map { g ->
            if (g.id == firstId) g.copy(apiKey = credential.apiKey) else g
        }
        val updated = normalizeConfig(
            current.copy(
                apiKey = credential.apiKey,
                baseUrl = credential.baseUrl,
                groups = updatedGroups,
            )
        )
        val next = configs.map { if (it.id == credential.providerId) updated else it }
        if (!persist(next)) return false
        AICredentials.put(updated.toCredential())
        return true
    }

    /** 清空某个分组的 Key；如果是唯一分组则同时清空顶层 Key/Base URL（与旧版兼容）。 */
    suspend fun clearGroupKey(providerId: String, groupId: String) {
        val current = byId(providerId) ?: return
        val existing = current.effectiveGroups()
        if (existing.size <= 1) {
            clearCredential(providerId)
            return
        }
        val updatedGroups = existing.map { g ->
            if (g.id == groupId) g.copy(apiKey = "", models = emptyList()) else g
        }
        val updated = normalizeConfig(current.copy(groups = updatedGroups))
        val next = configs.map { if (it.id == providerId) updated else it }
        if (persist(next)) AICredentials.put(updated.toCredential())
    }

    /** 只清 Key，保留这条接口和它的条目。 */
    suspend fun clearCredential(id: String) {
        val next = configs.map { config ->
            if (config.id == id) {
                config.copy(
                    apiKey = "",
                    baseUrl = "",
                    models = emptyList(),
                    groups = emptyList(),
                )
            } else {
                config
            }
        }
        if (persist(next)) AICredentials.remove(id)
    }

    suspend fun saveModels(id: String, models: List<String>) {
        val current = byId(id) ?: return
        val existing = current.effectiveGroups()
        val firstId = existing.first().id
        val raw = models.map(CustomGroupCodec::rawModelId).distinct()
        val updatedGroups = existing.map { g -> if (g.id == firstId) g.copy(models = raw) else g }
        val updated = normalizeConfig(current.copy(groups = updatedGroups))
        if (persist(configs.map { if (it.id == id) updated else it })) {
            AICredentials.put(updated.toCredential())
        }
    }

    suspend fun saveSelectedModel(id: String, model: String) {
        persist(configs.map { config ->
            if (config.id == id) config.copy(selectedModel = model) else config
        })
    }

    private fun normalizeConfig(config: CustomApiConfig): CustomApiConfig {
        val active = config.effectiveGroups()
        val primaryKey = active.firstOrNull { it.apiKey.isNotBlank() }?.apiKey.orEmpty()
        return config.copy(
            apiKey = primaryKey,
            groups = active,
            models = config.copy(groups = active).aggregatedModelIds(),
        )
    }

    private fun uniqueGroupName(preferred: String?, existing: List<CustomApiGroup>): String {
        val occupied = existing.map { it.name.lowercase() }.toSet()
        val clean = preferred?.trim()?.take(20)?.takeIf {
            it.isNotEmpty() && !CustomNameDefaults.isDefaultName(it)
        }
        if (clean != null && clean.lowercase() !in occupied) return clean
        val base = clean ?: "分组"
        var idx = if (clean == null) existing.size + 1 else 2
        while (true) {
            val candidate = "$base $idx"
            if (candidate.lowercase() !in occupied) return candidate
            idx++
        }
    }

    private fun newGroupId(): String = "g_${UUID.randomUUID().toString().take(6)}"

    /** 写盘 + 刷新目录。Key 加密失败时整体失败，绝不退回去写明文。 */
    private suspend fun persist(next: List<CustomApiConfig>): Boolean {
        val encrypted = next.map { config ->
            val key = if (config.apiKey.isBlank()) {
                ""
            } else {
                runCatching { SecretCipher.encrypt(config.apiKey) }.getOrNull() ?: return false
            }
            val storedGroups = config.groups.map { group ->
                val groupKey = if (group.apiKey.isBlank()) {
                    ""
                } else {
                    runCatching { SecretCipher.encrypt(group.apiKey) }.getOrNull() ?: return false
                }
                StoredCustomApiGroup(
                    id = group.id,
                    name = group.name,
                    key = groupKey,
                    models = group.models,
                )
            }
            StoredCustomApi(
                id = config.id,
                name = config.name,
                key = key,
                baseUrl = config.baseUrl,
                models = config.models,
                selectedModel = config.selectedModel,
                aliases = config.aliases,
                groups = storedGroups,
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
            val groupCount = config.effectiveGroups().size
            val groupHint = if (groupCount > 1) " · ${groupCount}个分组" else ""
            AIProvider(
                id = config.id,
                name = config.name,
                description = "自定义 OpenAI 兼容接口$groupHint · ${
                    config.baseUrl.ifBlank { "未填写地址" }
                }",
                baseUrl = config.baseUrl,
                consoleUrl = "",
                requiresKey = false,
                fallbackModels = config.aggregatedModelIds(),
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
