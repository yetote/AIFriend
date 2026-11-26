package com.feature.home.vm

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.core.ai.model.ModelPriceRefresher
import com.core.ai.model.ModelPrices
import com.core.ai.model.OpenAIModelResponse
import com.core.ai.model.VoiceCatalog
import com.core.ai.model.toSelectableChatModelsWithPrices
import com.core.ai.probe.FriendProbeStore
import com.core.common.BaseViewModel
import com.core.repository.ApiConfig
import com.core.repository.WebRepository
import com.core.repository.settings.AIProvider
import com.core.repository.settings.AIProviderCatalog
import com.core.repository.settings.CustomNameDefaults
import com.core.repository.settings.RelayStationDetector
import com.core.repository.settings.TTSProviderCatalog
import com.core.repository.settings.validateBaseUrl
import com.core.repository.storage.AICredential
import com.core.repository.storage.AIProviderSettings
import com.core.repository.storage.FriendRepository
import com.core.repository.storage.TTSCredential
import com.core.repository.storage.TTSProviderSettings
import com.core.tts.TTSPreviewPlayer
import com.feature.agent.data.toUITone
import com.ui.home.UIProviderOption
import com.ui.home.UIModelSettingsState
import com.ui.home.UISettingsModel
import com.ui.home.UITTSTestResult
import com.ui.home.UITTSProviderOption
import com.ui.home.UITTSState
import com.ui.home.UIVerifyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 模型与密钥设置页的状态管理。
 *
 * 「测试并保存」不是两个按钮：先拿用户刚粘贴的 Key 真的发一次 `GET /models`，
 * 通得过才写进 DataStore。这样用户不会存下一个错的 Key，
 * 然后在聊天页面上撞到 401 才知道要回来改。
 */
class SettingsViewModel(application: Application) : BaseViewModel(application) {

    private val settings = AIProviderSettings(application)

    /** 自定义接口改名时要同步已建好友的 brand，否则好友会变成「暂不支持的厂商」。 */
    private val friendRepository = FriendRepository(application)

    /** TTS 凭证独立存储，和模型 Key 完全分开。 */
    private val ttsSettings = TTSProviderSettings(application)
    private var savedTts: Map<String, TTSCredential> = emptyMap()

    /** 设置页试听：和新凭证一起传进去，不依赖狼人杀那边读到的快照。 */
    private val previewPlayer = TTSPreviewPlayer(application)

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
            loadTts()
            refreshPriceIfStale()
        }
    }

    override fun onCleared() {
        previewPlayer.stop()
        super.onCleared()
    }

    /** 读 TTS 凭证，填进 TTS 区块。默认停在已配置的那家，否则 MiniMax。 */
    private suspend fun loadTts() {
        val credentials = withContext(Dispatchers.IO) { ttsSettings.load() }
        savedTts = credentials.associateBy { it.providerId }
        val selected = TTSProviderCatalog.all.firstOrNull {
            savedTts[it.id]?.apiKey?.isNotBlank() == true
        } ?: TTSProviderCatalog.default()
        _state.value = _state.value.copy(
            tts = UITTSState(
                providers = TTSProviderCatalog.all.map { it.toTtsOption() },
                selectedId = selected.id,
                keyInput = "",
                secretInput = "",
                appIdInput = savedTts[selected.id]?.appId.orEmpty(),
                baseUrlInput = savedTts[selected.id]?.baseUrl.orEmpty(),
            )
        )
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
        val provider = AIProviderCatalog.all.firstOrNull { p ->
            saved[p.id]?.apiKey?.isNotBlank() == true ||
                // 本地部署的自定义接口可以不填 Key，填了地址就算配过。
                (AIProviderCatalog.isCustom(p.id) && saved[p.id]?.baseUrl?.isNotBlank() == true)
        } ?: AIProviderCatalog.default()
        val models = withContext(Dispatchers.IO) { settings.selectedModel(provider.id) }
        // 价格可能刚被刷过，缓存里的明细要按最新的价格重算一遍
        pricedCache[provider.id]?.let { pricedCache[provider.id] = repriced(it, provider.id) }
        _state.value = UIModelSettingsState(
            providers = AIProviderCatalog.all.map { it.toOption() },
            selectedProviderId = provider.id,
            customNameInput = provider.name.takeIf { AIProviderCatalog.isCustom(provider.id) }.orEmpty(),
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
            accessKeyIdInput = "",
            accessKeySecretInput = "",
            baseUrlInput = credential?.baseUrl.orEmpty().ifBlank { provider.baseUrl },
            customNameInput = provider.name.takeIf { AIProviderCatalog.isCustom(providerId) }.orEmpty(),
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

    /** 第二对凭证（目前只有火山方舟要）。 */
    fun setAccessKeyIdInput(value: String) {
        _state.value = _state.value.copy(accessKeyIdInput = value.trim(), error = null)
    }

    fun setAccessKeySecretInput(value: String) {
        _state.value = _state.value.copy(accessKeySecretInput = value.trim(), error = null)
    }

    fun setBaseUrlInput(value: String) {
        _state.value = _state.value.copy(baseUrlInput = value.trim(), verifyResult = null)
    }

    fun setCustomNameInput(value: String) {
        _state.value = _state.value.copy(customNameInput = value, error = null)
    }

    /** 新建一个自定义接口，建好即选中，用户接着填地址和 Key。 */
    fun addCustom() {
        viewModelScope.launch {
            val created = withContext(Dispatchers.IO) { settings.custom.add("自定义 API") }
            if (created == null) {
                _state.value = _state.value.copy(error = "新增失败，请重试")
                return@launch
            }
            refreshProviderOptions()
            selectProvider(created.id)
        }
    }

    /**
     * 给当前自定义接口改名。
     *
     * 好友表里存的是品牌名字符串，所以这里把用它建的好友一起改掉。
     * 老狼人杀存档没法逐条改，靠 [AIProvider.aliases] 继续认得旧名字。
     */
    fun renameCustom() {
        val current = _state.value
        val id = current.selectedProvider?.customId ?: return
        val name = current.customNameInput.trim()
        if (name.isBlank()) {
            _state.value = current.copy(error = "名称不能为空")
            return
        }
        viewModelScope.launch {
            val oldName = withContext(Dispatchers.IO) { settings.custom.rename(id, name) }
            if (oldName == null) {
                _state.value = _state.value.copy(error = "改名失败：名字已被占用，或保存失败")
                return@launch
            }
            val previousNames = AIProviderCatalog.byId(id)?.aliases.orEmpty() + oldName
            FriendProbeStore.invalidateBrands(previousNames + name)
            val friends = withContext(Dispatchers.IO) { friendRepository.load() }
            friends.filter { it.brand in previousNames }.forEach { friend ->
                withContext(Dispatchers.IO) {
                    friendRepository.upsert(
                        friend.copy(brand = name, company = name)
                    )
                }
            }
            refreshProviderOptions()
            selectProvider(id)
        }
    }

    /** 删除自定义接口；已保存的 Key 一起清掉。 */
    fun deleteCustom(id: String) {
        viewModelScope.launch {
            val provider = AIProviderCatalog.byId(id)
            val ok = withContext(Dispatchers.IO) { settings.custom.remove(id) }
            if (!ok) {
                _state.value = _state.value.copy(error = "删除失败，请重试")
                return@launch
            }
            if (provider != null) {
                FriendProbeStore.invalidateBrands(listOf(provider.name) + provider.aliases)
            }
            saved = saved - id
            modelCache.remove(id)
            pricedCache.remove(id)
            if (_state.value.selectedProviderId == id) {
                val first = AIProviderCatalog.all.firstOrNull()
                    ?: return@launch
                selectProvider(first.id)
            }
            refreshProviderOptions()
        }
    }

    private fun refreshProviderOptions() {
        _state.value = _state.value.copy(
            providers = AIProviderCatalog.all.map { it.toOption() }
        )
    }

    fun selectModel(model: String) {
        _state.value = _state.value.copy(selectedModel = model)
    }

    fun clearKey() {
        val providerId = _state.value.selectedProviderId
        val provider = AIProviderCatalog.byId(providerId)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { settings.clear(providerId) }
            if (provider != null) {
                FriendProbeStore.invalidateBrands(listOf(provider.name) + provider.aliases)
            }
            saved = saved - providerId
            modelCache.remove(providerId)
            pricedCache.remove(providerId)
            _state.value = _state.value.copy(
                providers = AIProviderCatalog.all.map { it.toOption() },
                keyInput = "",
                accessKeyIdInput = "",
                accessKeySecretInput = "",
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
        // 第二对凭证：留空即沿用已保存的值。两项要一起在（只有火山用得到）。
        val accessKeyId = snapshot.accessKeyIdInput.ifBlank { saved[provider.id]?.accessKeyId.orEmpty() }
        val accessKeySecret = snapshot.accessKeySecretInput.ifBlank {
            saved[provider.id]?.accessKeySecret.orEmpty()
        }
        if (provider.accessKeyLabel != null && accessKeyId.isNotBlank() != accessKeySecret.isNotBlank()) {
            _state.value = snapshot.copy(
                error = "${provider.accessKeyLabel} 和 Secret 要一起填，缺一个都用不了"
            )
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

            // 自定义接口在名称仍为默认占位（或用户清空名称框）时，与 /models 并行探测中转站名，
            // 成功连接后自动设为当前厂商名，几乎零额外耗时；已由用户手动命名的接口绝不覆盖。
            val isCustom = AIProviderCatalog.isCustom(provider.id)
            val inputName = snapshot.customNameInput.trim()
            val shouldAutoName = isCustom &&
                CustomNameDefaults.isDefaultName(inputName) &&
                (CustomNameDefaults.isDefaultName(provider.name) || inputName.isEmpty())
            val detectedDeferred = if (shouldAutoName) {
                async(Dispatchers.IO) {
                    runCatching {
                        RelayStationDetector.detect(baseUrl) { url, timeoutMs ->
                            WebRepository.instance.getTextShort(url, timeoutMs = timeoutMs)
                        }
                    }.getOrNull()
                }
            } else {
                null
            }

            // 没有 /models 的厂商（目前是星火）验证不了 Key，只能直接存下来：
            // 模型名走目录里的兜底列表，等真聊一次才知道 Key 对不对。
            if (!provider.hasModelList) {
                detectedDeferred?.cancel()
                saveCredential(
                    provider, apiKey, baseUrl, provider.fallbackModels,
                    accessKeyId = accessKeyId,
                    accessKeySecret = accessKeySecret,
                )
                return@launch
            }

            val result = WebRepository.instance.getModel<OpenAIModelResponse>(config)

            result.onSuccess { response ->
                val priced = if (provider.id == AIProviderCatalog.Gemini.id) {
                    // Gemini 的 /v1beta/openai/models 返回带 models/ 前缀的 id，且混着 Live / Omni /
                    // TTS / Decisions API 等非对话模型；与 AIGemini 共用同一套过滤规则，并放行 Gemma 4。
                    com.core.ai.model.GeminiModelResponse(
                        geminiModelList = response.modelList.map {
                            com.core.ai.model.GeminiModel(
                                id = it.id,
                                type = it.type,
                                ownedBy = it.ownedBy,
                            )
                        }
                    ).toChatModelIds().map { UISettingsModel(id = it) }
                } else {
                    // 聚合平台的价格分两路：OpenRouter 在模型上自带 pricing，
                    // 硅基流动没有，靠内置快照补。查不到的留 null，不编价。
                    response.modelList
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
                }

                val models = priced.map { it.id }.ifEmpty { provider.fallbackModels }

                if (models.isEmpty()) {
                    detectedDeferred?.cancel()
                    _state.value = _state.value.copy(
                        isVerifying = false,
                        isLoadingModels = false,
                        verifyResult = UIVerifyResult.Failure(
                            "连接通了，但没取到可用模型。请确认 Base URL 是否正确。"
                        )
                    )
                    return@launch
                }

                val detectedName = withTimeoutOrNull(3_500L) { detectedDeferred?.await()?.name }
                detectedDeferred?.cancel()
                saveCredential(
                    provider, apiKey, baseUrl, models, priced,
                    accessKeyId = accessKeyId,
                    accessKeySecret = accessKeySecret,
                    autoDetectedName = detectedName,
                )
            }.onFailure { error ->
                detectedDeferred?.cancel()
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
        priced: List<UISettingsModel> = emptyList(),
        accessKeyId: String = "",
        accessKeySecret: String = "",
        autoDetectedName: String? = null,
    ) {
        val isCustom = AIProviderCatalog.isCustom(provider.id)
        val hadSavedModels = isCustom && settings.custom.modelsOf(provider.id).isNotEmpty()
        val credential = AICredential(
            providerId = provider.id,
            apiKey = apiKey,
            // 只有自定义服务商的地址是用户填的，其余用目录里的固定地址，
            // 存下来的话用户以后改目录也改不动它。
            baseUrl = if (isCustom) baseUrl else "",
            accessKeyId = accessKeyId,
            accessKeySecret = accessKeySecret,
        )
        var appliedAutoName: String? = null
        val stored = withContext(Dispatchers.IO) {
            val ok = settings.save(credential)
            if (ok) {
                // 自定义接口把这次拉到的模型也存下来：断网时还有兜底列表可选，
                // 创建页不用等一次成功的 /models 才知道有哪些模型。
                if (isCustom) {
                    settings.custom.saveModels(provider.id, models)
                    if (!autoDetectedName.isNullOrBlank()) {
                        val occupied = AIProviderCatalog.all
                            .filter { it.id != provider.id }
                            .map { it.name }
                            .toSet()
                        val builtins = AIProviderCatalog.builtin.map { it.name }.toSet()
                        val uniqueName = RelayStationDetector.uniqueName(
                            desired = autoDetectedName,
                            occupiedNames = occupied,
                            builtinNames = builtins,
                        )
                        val oldName = settings.custom.rename(
                            id = provider.id,
                            newName = uniqueName,
                            recordAlias = hadSavedModels,
                        )
                        if (oldName != null && oldName != uniqueName) {
                            appliedAutoName = uniqueName
                            val previousNames = AIProviderCatalog.byId(provider.id)?.aliases.orEmpty() + oldName
                            val friends = friendRepository.load()
                            friends.filter { it.brand in previousNames }.forEach { friend ->
                                friendRepository.upsert(friend.copy(brand = uniqueName, company = uniqueName))
                            }
                        }
                    }
                }
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
        val latestProvider = AIProviderCatalog.byId(provider.id) ?: provider
        FriendProbeStore.invalidateBrands(
            (listOf(provider.name, latestProvider.name) + provider.aliases + latestProvider.aliases).distinct()
        )
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
            accessKeyIdInput = "",
            accessKeySecretInput = "",
            customNameInput = if (visible && appliedAutoName != null) appliedAutoName!! else current.customNameInput,
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
            verifyResult = UIVerifyResult.Success(models.size, detectedName = appliedAutoName),
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
        editableBaseUrl = AIProviderCatalog.isCustom(id),
        baseUrl = saved[id]?.baseUrl.orEmpty(),
        isGateway = isGateway,
        customId = id.takeIf { AIProviderCatalog.isCustom(it) },
        accessKeyLabel = accessKeyLabel,
        hasAccessKey = saved[id]?.accessKeyId?.isNotBlank() == true,
    )

    /** Key 只露头尾各 4 位，够用户认出是哪把，又不至于被旁人瞥去。 */
    private fun maskKey(key: String): String {
        if (key.isBlank()) return ""
        if (key.length <= 10) return "••••"
        return "${key.take(4)}••••${key.takeLast(4)}"
    }

    // ---- TTS 凭证配置 -------------------------------------------------

    fun selectTtsProvider(id: String) {
        val tts = _state.value.tts
        val saved = savedTts[id]
        _state.value = _state.value.copy(
            tts = tts.copy(
                selectedId = id,
                keyInput = "",
                secretInput = "",
                appIdInput = saved?.appId.orEmpty(),
                baseUrlInput = saved?.baseUrl.orEmpty(),
                hint = null,
                testResult = null
            )
        )
    }

    fun setTtsKeyInput(value: String) {
        _state.value = _state.value.copy(
            tts = _state.value.tts.copy(keyInput = value.trim(), hint = null, testResult = null)
        )
    }

    fun setTtsSecretInput(value: String) {
        _state.value = _state.value.copy(
            tts = _state.value.tts.copy(secretInput = value.trim(), hint = null, testResult = null)
        )
    }

    fun setTtsAppIdInput(value: String) {
        _state.value = _state.value.copy(
            tts = _state.value.tts.copy(appIdInput = value.trim(), hint = null, testResult = null)
        )
    }

    fun setTtsBaseUrlInput(value: String) {
        _state.value = _state.value.copy(
            tts = _state.value.tts.copy(baseUrlInput = value.trim(), hint = null, testResult = null)
        )
    }

    fun toggleTtsKeyVisible() {
        val tts = _state.value.tts
        _state.value = _state.value.copy(tts = tts.copy(keyVisible = !tts.keyVisible))
    }

    fun clearTtsKey() {
        val id = _state.value.tts.selectedId
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ttsSettings.clear(id) }
            savedTts = savedTts - id
            _state.value = _state.value.copy(
                tts = _state.value.tts.copy(
                    providers = TTSProviderCatalog.all.map { it.toTtsOption() },
                    keyInput = "",
                    secretInput = "",
                    appIdInput = "",
                    hint = "已清除该服务的凭证",
                    testResult = null
                )
            )
        }
    }

    /**
     * 测试并保存 TTS 凭证。
     *
     * 以前这里只写盘，Key 错要等开局狼人杀才知道——太晚了。现在先拿输入框里的
     * （或已保存的）凭证真的合成一句，播放成功才落盘；失败把原因摆在设置页上。
     */
    fun testAndSaveTtsCredential() {
        val tts = _state.value.tts
        val provider = TTSProviderCatalog.byId(tts.selectedId) ?: return
        val existing = savedTts[provider.id]
        val apiKey = tts.keyInput.ifBlank { existing?.apiKey.orEmpty() }
        if (apiKey.isBlank()) {
            _state.value = _state.value.copy(tts = tts.copy(hint = "请先填写 ${provider.keyLabel}"))
            return
        }
        // 第二凭证 / AppID：留空表示沿用已保存的值，没有旧值就拦一下。
        val secret = tts.secretInput.ifBlank { existing?.apiSecret.orEmpty() }
        if (provider.secretLabel != null && secret.isBlank()) {
            _state.value = _state.value.copy(tts = tts.copy(hint = "请先填写 ${provider.secretLabel}"))
            return
        }
        val appId = tts.appIdInput.ifBlank { existing?.appId.orEmpty() }
        // 火山的 AppID 可留空：留空即走新版控制台的 API Key 单头鉴权。
        // 这里少判一次，用户就会被卡在「不填不行」，而新版控制台压根没有 AppID。
        if (provider.appIdLabel != null && !provider.appIdOptional && appId.isBlank()) {
            _state.value = _state.value.copy(tts = tts.copy(hint = "请先填写 ${provider.appIdLabel}"))
            return
        }
        val baseUrl = tts.baseUrlInput.trim()
            .ifBlank { existing?.baseUrl.orEmpty() }
        if (provider.editableBaseUrl && baseUrl.isBlank()) {
            _state.value = _state.value.copy(tts = tts.copy(hint = "请填写 Base URL"))
            return
        }
        baseUrl.takeIf { it.isNotBlank() }?.let { validateBaseUrl(it) }?.let { problem ->
            _state.value = _state.value.copy(tts = tts.copy(hint = problem))
            return
        }

        val credential = TTSCredential(provider.id, apiKey, baseUrl, secret, appId)
        _state.value = _state.value.copy(
            tts = tts.copy(testing = true, hint = null, testResult = null)
        )
        viewModelScope.launch {
            val result = try {
                // 用服务商默认音色验证：设置页只回答「这套凭证在这台机器上能不能出声」，
                // 具体音色是角色的属性，在建角色页挑并逐个试听。
                previewPlayer.preview(provider, credential, null)
            } catch (t: Throwable) {
                Result.failure<Unit>(t)
            }
            val error = result.exceptionOrNull()

            if (error != null) {
                _state.value = _state.value.copy(
                    tts = _state.value.tts.copy(
                        testing = false,
                        testResult = UITTSTestResult.Failure(ttsReadableError(error, provider))
                    )
                )
                return@launch
            }

            val ok = withContext(Dispatchers.IO) { ttsSettings.save(credential) }
            if (ok) savedTts = savedTts + (provider.id to credential)
            val voiceLabel = VoiceCatalog.defaultOption(provider.id).label
            _state.value = _state.value.copy(
                tts = _state.value.tts.copy(
                    testing = false,
                    providers = TTSProviderCatalog.all.map { it.toTtsOption() },
                    keyInput = "",
                    secretInput = "",
                    hint = if (ok) null else "试听成功，但凭证保存失败（系统密钥库不可用），请重试",
                    testResult = if (ok) UITTSTestResult.Success(voiceLabel) else null
                )
            )
        }
    }

    /** 文案与建角色页试听共用一份，别一边说「Key 不对」一边摔堆栈给用户。 */
    private fun ttsReadableError(
        throwable: Throwable,
        provider: com.core.repository.settings.TTSProvider,
    ): String = com.core.tts.TTSReadableError.of(throwable, provider.name)

    private fun com.core.repository.settings.TTSProvider.toTtsOption() = UITTSProviderOption(
        id = id,
        name = name,
        description = description,
        maskedKey = maskKey(savedTts[id]?.apiKey.orEmpty()),
        hasKey = savedTts[id]?.apiKey?.isNotBlank() == true,
        editableBaseUrl = this === TTSProviderCatalog.Custom,
        baseUrl = savedTts[id]?.baseUrl.orEmpty(),
        keyLabel = keyLabel,
        secretLabel = secretLabel,
        appIdLabel = appIdLabel,
        appIdOptional = appIdOptional,
        hasSecret = savedTts[id]?.apiSecret?.isNotBlank() == true,
        hasAppId = savedTts[id]?.appId?.isNotBlank() == true,
    )

    private companion object {
        /** 刷新结果提示停留时长。 */
        const val HINT_DURATION_MS = 3_000L
    }
}
