package com.feature.home.vm

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.core.ai.model.ModelPriceRefresher
import com.core.ai.model.ModelPrices
import com.core.ai.model.OpenAIModelResponse
import com.core.ai.model.toSelectableChatModelsWithPrices
import com.core.common.BaseViewModel
import com.core.repository.ApiConfig
import com.core.repository.WebRepository
import com.core.repository.settings.AIProvider
import com.core.repository.settings.AIProviderCatalog
import com.core.repository.settings.validateBaseUrl
import com.core.repository.storage.AICredential
import com.core.repository.storage.AIProviderSettings
import com.feature.agent.data.toUITone
import com.ui.home.UIProviderOption
import com.ui.home.UIModelSettingsState
import com.ui.home.UISettingsModel
import com.ui.home.UIVerifyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 模型与密钥设置页的状态管理。
 *
 * 「测试并保存」不是两个按钮：先拿用户刚粘贴的 Key 真的发一次 `GET /models`，
 * 通得过才写进 DataStore。这样用户不会存下一个错的 Key，
 * 然后在聊天页面上撞到 401 才知道要回来改。
 */
class SettingsViewModel(application: Application) : BaseViewModel(application) {

    private val settings = AIProviderSettings(application)

    private val priceRefresher = ModelPriceRefresher(application)

    private val _state = MutableStateFlow(UIModelSettingsState())
    val state: StateFlow<UIModelSettingsState> = _state.asStateFlow()

    /** 每个服务商已保存的凭证，切换服务商时回填输入框。 */
    private var saved: Map<String, AICredential> = emptyMap()

    /** 每个服务商缓存的模型列表，避免来回切换重复请求。 */
    private val modelCache = mutableMapOf<String, List<String>>()

    /** 带价格的模型明细缓存，切回已保存过的服务商时直接复用。 */
    private val pricedCache = mutableMapOf<String, List<UISettingsModel>>()

    init {
        viewModelScope.launch {
            load()
            refreshPriceIfStale()
        }
    }

    /**
     * 进来时看看今天抓过没有，没抓过就抓一次。
     *
     * 放在 [load] 之后：抓取要联网，断网时要等超时才有结果，
     * 先渲染再悄悄更新，用户不会对着空列表等十几秒。
     */
    private suspend fun refreshPriceIfStale() {
        val updated = runCatching { priceRefresher.refreshIfNeeded() }.getOrDefault(false)
        if (!updated) return
        val providerId = _state.value.selectedProviderId
        pricedCache[providerId]?.let { pricedCache[providerId] = repriced(it, providerId) }
        _state.value = _state.value.copy(
            pricedModels = pricedCache[providerId].orEmpty(),
            priceSnapshotDate = ModelPrices.snapshotDate(providerId)
        )
    }

    private suspend fun load() {
        // 先把磁盘上抓到的价装进内存，否则第一帧显示的是内置快照里那些过期的价，
        // 等一下再跳成正确的数，看着像自己变了。只读磁盘，不发请求，很快。
        runCatching { priceRefresher.restore() }
        val credentials = withContext(Dispatchers.IO) { settings.load() }
        saved = credentials.associateBy { it.providerId }

        // 默认停在用户已经配过的那个服务商上。
        // Key 现在全部由用户自己填，一个都没配时停在目录默认值（硅基流动）——
        // 那是新用户最可能要去的入口；但已经配了 Deepseek 的人
        // 每次进来都看到硅基流动的空 Key 框，会以为 Key 丢了。
        val provider = AIProviderCatalog.all.firstOrNull { saved[it.id]?.apiKey?.isNotBlank() == true }
            ?: AIProviderCatalog.default()
        val models = withContext(Dispatchers.IO) { settings.selectedModel(provider.id) }
        // 价格可能刚被刷过，缓存里的明细要按最新的价格重算一遍
        pricedCache[provider.id]?.let { pricedCache[provider.id] = repriced(it, provider.id) }
        _state.value = UIModelSettingsState(
            providers = AIProviderCatalog.all.map { it.toOption() },
            selectedProviderId = provider.id,
            models = modelCache[provider.id].orEmpty(),
            pricedModels = pricedCache[provider.id].orEmpty(),
            selectedModel = models.orEmpty(),
            priceSnapshotDate = ModelPrices.snapshotDate(provider.id),
            canRefreshPrice = ModelPrices.canRefresh(provider.id)
        )
    }

    /**
     * 手动刷新价格。
     *
     * 抓的是定价页，和用户填的 Key 无关，所以没配 Key 也能刷。
     * 抓成功后把已有的模型列表按新价格重算一遍，用户不用重新「测试并保存」。
     */
    fun refreshPrice() {
        val provider = AIProviderCatalog.byId(_state.value.selectedProviderId) ?: return
        if (!ModelPrices.canRefresh(provider.id) || _state.value.isRefreshingPrice) return

        _state.value = _state.value.copy(
            isRefreshingPrice = true,
            priceRefreshHint = null,
            error = null
        )

        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { priceRefresher.refresh() }.getOrDefault(false) }
            if (ok) {
                pricedCache[provider.id]?.let { pricedCache[provider.id] = repriced(it, provider.id) }
                _state.value = _state.value.copy(
                    isRefreshingPrice = false,
                    pricedModels = pricedCache[provider.id].orEmpty(),
                    priceSnapshotDate = ModelPrices.snapshotDate(provider.id),
                    priceRefreshHint = "已更新为最新价格"
                )
            } else {
                // 失败不清空旧价格：列表继续显示上次的价，只提示这次没刷成。
                _state.value = _state.value.copy(
                    isRefreshingPrice = false,
                    priceRefreshHint = "刷新失败，仍在显示上次的价格",
                    priceSnapshotDate = ModelPrices.snapshotDate(provider.id)
                )
            }
            // 结果提示是一次性的，几秒后自己消失，
            // 否则它会一直挂在标题下面，看着像是当前状态。
            delay(HINT_DURATION_MS)
            if (!_state.value.isRefreshingPrice) {
                _state.value = _state.value.copy(priceRefreshHint = null)
            }
        }
    }

    /** 按当前生效的价格重算一遍展示文本和档位。 */
    private fun repriced(models: List<UISettingsModel>, providerId: String): List<UISettingsModel> {
        val currency = ModelPrices.currencyOf(providerId)
        return models.map { model ->
            val price = ModelPrices.lookup(providerId, model.id)
            model.copy(
                price = price?.format(currency),
                tone = price?.tier(currency)?.toUITone(),
                bands = price?.bandLabels(currency).orEmpty()
            )
        }
    }

    fun selectProvider(providerId: String) {
        val provider = AIProviderCatalog.byId(providerId) ?: return
        val credential = saved[providerId]
        val cached = pricedCache[providerId].orEmpty()
        _state.value = _state.value.copy(
            selectedProviderId = providerId,
            keyInput = "",
            baseUrlInput = credential?.baseUrl.orEmpty().ifBlank { provider.baseUrl },
            models = modelCache[providerId].orEmpty(),
            pricedModels = cached,
            selectedModel = modelCache[providerId]?.firstOrNull().orEmpty(),
            priceIsSnapshot = cached.any { it.price != null } &&
                ModelPrices.isSnapshot(providerId),
            priceSnapshotDate = ModelPrices.snapshotDate(providerId),
            canRefreshPrice = ModelPrices.canRefresh(providerId),
            priceRefreshHint = null,
            verifyResult = null,
            error = null
        )
    }

    fun setKeyInput(value: String) {
        _state.value = _state.value.copy(keyInput = value.trim(), verifyResult = null, error = null)
    }

    fun setBaseUrlInput(value: String) {
        _state.value = _state.value.copy(baseUrlInput = value.trim(), verifyResult = null)
    }

    fun selectModel(model: String) {
        _state.value = _state.value.copy(selectedModel = model)
    }

    fun clearKey() {
        val providerId = _state.value.selectedProviderId
        viewModelScope.launch {
            withContext(Dispatchers.IO) { settings.clear(providerId) }
            saved = saved - providerId
            modelCache.remove(providerId)
            pricedCache.remove(providerId)
            _state.value = _state.value.copy(
                providers = AIProviderCatalog.all.map { it.toOption() },
                keyInput = "",
                models = emptyList(),
                pricedModels = emptyList(),
                selectedModel = "",
                priceIsSnapshot = false,
                verifyResult = null
            )
        }
    }

    /**
     * 测试连接并保存。
     *
     * 用户如果没改 Key，就用已存的那把去测 —— 这样「只想换个模型」的场景
     * 不需要重新粘贴一次 Key。
     */
    fun verifyAndSave() {
        val snapshot = _state.value
        val provider = AIProviderCatalog.byId(snapshot.selectedProviderId) ?: return

        val apiKey = snapshot.keyInput.ifBlank { saved[provider.id]?.apiKey.orEmpty() }
        if (provider.requiresKey && apiKey.isBlank()) {
            _state.value = snapshot.copy(error = "请先填写 API Key")
            return
        }

        val baseUrl = snapshot.baseUrlInput.trim()
            .ifBlank { saved[provider.id]?.baseUrl.orEmpty() }
            .ifBlank { provider.baseUrl }
            .trimEnd('/')
        if (baseUrl.isBlank()) {
            _state.value = snapshot.copy(error = "请填写 Base URL")
            return
        }
        // 只许 https（内网地址除外）：Key 是随请求明文发出去的，
        // 走 http 等于送给同网段的人。
        validateBaseUrl(baseUrl)?.let { problem ->
            _state.value = snapshot.copy(error = problem)
            return
        }

        _state.value = snapshot.copy(
            isVerifying = true,
            isLoadingModels = true,
            verifyResult = null,
            error = null
        )

        viewModelScope.launch {
            val config = ApiConfig(
                baseUrl = baseUrl,
                chatUrl = "$baseUrl/chat/completions",
                apiKey = apiKey,
                headers = if (apiKey.isBlank()) {
                    emptyMap()
                } else {
                    mapOf("Authorization" to "Bearer $apiKey")
                }
            )

            // 没有 /models 的厂商（目前是星火）验证不了 Key，只能直接存下来：
            // 模型名走目录里的兜底列表，等真聊一次才知道 Key 对不对。
            if (!provider.hasModelList) {
                saveCredential(provider, apiKey, baseUrl, provider.fallbackModels)
                return@launch
            }

            val result = WebRepository.instance.getModel<OpenAIModelResponse>(config)

            result.onSuccess { response ->
                // 聚合平台的价格分两路：OpenRouter 在模型上自带 pricing，
                // 硅基流动没有，靠内置快照补。查不到的留 null，不编价。
                val priced = response.modelList
                    .toSelectableChatModelsWithPrices()
                    .map { model ->
                        val price = model.price ?: ModelPrices.lookup(provider.id, model.id)
                        val currency = ModelPrices.currencyOf(provider.id)
                        UISettingsModel(
                            id = model.id,
                            price = price?.format(currency),
                            tone = price?.tier(currency)?.toUITone(),
                            bands = price?.bandLabels(currency).orEmpty()
                        )
                    }

                val models = priced.map { it.id }.ifEmpty { provider.fallbackModels }

                if (models.isEmpty()) {
                    _state.value = _state.value.copy(
                        isVerifying = false,
                        isLoadingModels = false,
                        verifyResult = UIVerifyResult.Failure(
                            "连接通了，但没取到可用模型。请确认 Base URL 是否正确。"
                        )
                    )
                    return@launch
                }

                saveCredential(provider, apiKey, baseUrl, models, priced)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isVerifying = false,
                    isLoadingModels = false,
                    verifyResult = UIVerifyResult.Failure(readableError(error, provider))
                )
            }
        }
    }

    /** 把验证通过的凭证落盘，并更新列表与缓存。 */
    private suspend fun saveCredential(
        provider: com.core.repository.settings.AIProvider,
        apiKey: String,
        baseUrl: String,
        models: List<String>,
        priced: List<UISettingsModel> = emptyList()
    ) {
        val credential = AICredential(
            providerId = provider.id,
            apiKey = apiKey,
            // 只有自定义服务商的地址是用户填的，其余用目录里的固定地址，
            // 存下来的话用户以后改目录也改不动它。
            baseUrl = if (provider.id == AIProviderCatalog.Custom.id) baseUrl else ""
        )
        val stored = withContext(Dispatchers.IO) {
            val ok = settings.save(credential)
            if (ok) {
                // 默认选一个：优先沿用上次的，否则取列表第一个
                val previous = settings.selectedModel(provider.id)
                val chosen = previous?.takeIf { it in models } ?: models.firstOrNull()
                if (chosen != null) settings.saveSelectedModel(provider.id, chosen)
            }
            ok
        }
        if (!stored) {
            // 系统密钥库不可用时不能写明文，宁可报错让用户重试。
            _state.value = _state.value.copy(
                isVerifying = false,
                isLoadingModels = false,
                verifyResult = UIVerifyResult.Failure(
                    "连接成功，但 Key 保存失败（系统密钥库不可用），请重试。"
                )
            )
            return
        }
        saved = saved + (provider.id to credential)
        modelCache[provider.id] = models
        pricedCache[provider.id] = priced

        val current = _state.value
        // 保存期间用户可能已经切到别的服务商了，别把模型列表写到人家头上
        val visible = current.selectedProviderId == provider.id
        _state.value = current.copy(
            providers = AIProviderCatalog.all.map { it.toOption() },
            isVerifying = false,
            isLoadingModels = false,
            keyInput = "",
            models = if (visible) models else current.models,
            pricedModels = if (visible) priced else current.pricedModels,
            selectedModel = if (visible) {
                models.firstOrNull { it == current.selectedModel } ?: models.firstOrNull().orEmpty()
            } else {
                current.selectedModel
            },
            priceIsSnapshot = if (visible) false else current.priceIsSnapshot,
            priceSnapshotDate = ModelPrices.snapshotDate(provider.id),
            canRefreshPrice = ModelPrices.canRefresh(provider.id),
            verifyResult = UIVerifyResult.Success(models.size),
            error = null
        )
    }

    /**
     * 把网络异常翻译成用户能照着做的提示。
     *
     * 401/403 是最常见的（Key 错或没开通），单独点明；
     * 其余走统一的错误转换。
     */
    private fun readableError(throwable: Throwable, provider: AIProvider): String {
        var message = ""
        throwable.handleError { message = it }
        val lower = message.lowercase()
        return when {
            lower.contains("401") || lower.contains("unauthorized") || lower.contains("403") ->
                "Key 无效或没有权限。请到 ${provider.name} 控制台确认 Key 是否正确、是否已开通该服务。"
            lower.contains("404") ->
                "接口地址不对。请检查 Base URL 是否以 /v1 结尾。"
            lower.contains("timeout") || lower.contains("超时") ->
                "连接超时。检查网络，或者该服务在当前网络下不可达。"
            else -> message.ifBlank { "连接失败，请稍后重试" }
        }
    }

    private fun AIProvider.toOption(): UIProviderOption = UIProviderOption(
        id = id,
        name = name,
        description = description,
        maskedKey = maskKey(saved[id]?.apiKey.orEmpty()),
        hasKey = saved[id]?.apiKey?.isNotBlank() == true,
        editableBaseUrl = this === AIProviderCatalog.Custom,
        baseUrl = saved[id]?.baseUrl.orEmpty(),
        isGateway = isGateway
    )

    /** Key 只露头尾各 4 位，够用户认出是哪把，又不至于被旁人瞥去。 */
    private fun maskKey(key: String): String {
        if (key.isBlank()) return ""
        if (key.length <= 10) return "••••"
        return "${key.take(4)}••••${key.takeLast(4)}"
    }

    private companion object {
        /** 刷新结果提示停留时长。 */
        const val HINT_DURATION_MS = 3_000L
    }
}
