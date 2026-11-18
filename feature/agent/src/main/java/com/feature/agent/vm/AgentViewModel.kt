package com.feature.agent.vm

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.core.ai.entity.IAI
import com.core.ai.factory.AIFactory
import com.core.ai.model.ChatMessage
import com.core.ai.model.Friend
import com.core.ai.model.GatewayModel
import com.core.ai.model.ModelCost
import com.core.ai.model.ModelCosts
import com.core.ai.model.ModelPrice
import com.core.ai.model.ModelPriceRefresher
import com.core.ai.model.ModelPrices
import com.core.ai.model.VoiceCatalog
import com.core.ai.model.toFriend
import com.core.ai.model.toStored
import com.core.ai.model.trimToBudget
import com.core.common.BaseViewModel
import com.core.common.GlobalUiEvent
import com.core.common.GlobalUiEventManager
import com.core.network.withModelHint
import com.core.persona.Persona
import com.core.repository.storage.AICredentials
import com.core.repository.storage.AIProviderSettings
import com.core.repository.storage.AvatarStore
import com.core.repository.storage.FriendRepository
import com.core.repository.storage.LegacyMigration
import com.core.repository.storage.ProfileRepository
import com.core.repository.storage.StoredMessage
import com.core.repository.settings.AIProviderCatalog
import com.core.repository.settings.TTSProviderCatalog
import com.core.repository.storage.TTSCredentials
import com.core.repository.storage.TTSProviderSettings
import com.core.tts.TTSPreviewPlayer
import com.core.tts.TTSReadableError
import com.feature.agent.batch.BatchRoster
import com.feature.agent.contract.AgentUiState
import com.feature.agent.contract.DEFAULT_PROVIDERS
import com.feature.agent.model.Provider
import com.ui.agent.data.UIReadyTts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID

class AgentViewModel(application: Application) : BaseViewModel(application) {

    private val friendRepository = FriendRepository(application)
    private val profileRepository = ProfileRepository(application)
    private val avatarStore = AvatarStore(application)

    private val ais = mutableMapOf<String, IAI>()

    /** 正在生成的那条回复。停止、重试都要能掐掉它。 */
    private var replyJob: Job? = null

    private val modelCache = mutableMapOf<String, List<String>>()

    /** 带价格的模型明细缓存，切回已加载过的平台时直接复用，价格才不会丢。 */
    private val modelDetailCache = mutableMapOf<String, List<GatewayModel>>()
    private val settings = AIProviderSettings(application)

    /** 建角色页试听用：读 TTS 凭证 + 真实合成并播放。 */
    private val ttsSettings = TTSProviderSettings(application)
    private val previewPlayer = TTSPreviewPlayer(application)

    private val priceRefresher = ModelPriceRefresher(application)

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { LegacyMigration.migrateIfNeeded(application) }
            // 先把用户自己填的 Key 装进内存快照，否则 aiFor 建出来的实例
            // 拿到的还是空 Key，网关那边会直接 401。
            runCatching { settings.load() }
            // 价格表同理：先把磁盘上抓到的价装进内存，再拉模型列表，
            // 否则第一屏会显示内置快照里那些过期的价。
            runCatching { priceRefresher.restore() }
            runCatching { priceRefresher.refreshIfNeeded() }
            loadFriends()
            refreshProviders()
            // 批量创建面板要列「已经配好的语音服务」，冷启动时快照还是空的
            runCatching { ttsSettings.load() }
            syncReadyTts()
        }
    }

    /**
     * 已配置的服务商 id 列表（官方厂商 + 聚合平台）。
     *
     * 用它判断「用户是否刚在设置页配好了一个新的服务商」：
     * ViewModel 是 Activity 级的，从设置页返回不会重建，
     * 只有比对出变化才重新拉模型，免得每次返回都白跑一次网络请求。
     */
    private var configuredProviders: List<String> = emptyList()

    /**
     * 把「已配置 Key 的服务商」并入可选列表。
     *
     * Key 全部由用户自己填，APK 里不带任何一把，所以没配的服务商
     * 必须藏起来 —— 列出来用户选中之后既拉不到模型也聊不了，
     * 只会让人以为是 App 坏了。想用哪个就先去设置页配好。
     */
    private fun refreshProviders() {
        val usable = AIProviderCatalog.all.filter { provider ->
            if (provider.requiresKey) {
                AICredentials.apiKeyOf(provider.id).isNotBlank()
            } else {
                // 自定义服务商不要求 Key，但必须填了地址才有意义
                AICredentials.baseUrlOf(provider).isNotBlank()
            }
        }
        configuredProviders = usable.map { it.id }

        val available = DEFAULT_PROVIDERS.filter { provider ->
            usable.any { it.name == provider.brand }
        } + usable.filter { it.isGateway }.map { provider ->
            Provider(
                brand = provider.name,
                company = provider.name,
                defaultModel = provider.fallbackModels.firstOrNull().orEmpty(),
                fallbackModels = provider.fallbackModels,
                // 网关一把 Key 覆盖多家，没有「自家模型」可言，批量时全池可选
                ownModelPrefixes = provider.ownModelPrefixes,
                // 聚合平台的模型未必接受 enable_thinking，一律不发
                thinkingSwitchable = false,
                isGateway = true
            )
        }

        _uiState.update { state ->
            val brand = state.selectedBrand
                .takeIf { name -> available.any { it.brand == name } }
                ?: available.firstOrNull()?.brand
                ?: state.selectedBrand
            val switched = brand != state.selectedBrand
            val provider = available.firstOrNull { it.brand == brand }
            state.copy(
                providers = available,
                selectedBrand = brand,
                selectedModel = if (!switched) {
                    state.selectedModel
                } else {
                    provider?.defaultModel.orEmpty()
                },
                models = if (switched) emptyList() else state.models,
                // 换厂商时价格必须跟着换，否则会显示上一个平台的价
                isGateway = provider?.isGateway == true,
                gatewayModels = if (switched) emptyList() else state.gatewayModels,
                priceIsSnapshot = false,
                priceSnapshotDate = null
            )
        }
        // 一家都没配时不发请求，创建页会显示引导去设置页
        if (available.isNotEmpty()) loadModelsFor(_uiState.value.selectedBrand)
    }

    /**
     * 把「已经配好凭证的语音服务」同步到界面状态。
     *
     * 批量创建只能在这些里面随机：没配 Key 的服务一旦被分到，角色朗读全报错，
     * 而错误发生在建好之后，用户根本不会想到是创建时选错了。
     */
    private fun syncReadyTts() {
        val ready = TTSProviderCatalog.all
            .filter { provider ->
                TTSCredentials.apiKeyOf(provider.id).isNotBlank() &&
                    TTSCredentials.baseUrlOf(provider).isNotBlank()
            }
            .map { UIReadyTts(id = it.id, label = it.name) }
        _uiState.update { it.copy(readyTts = ready) }
    }

    fun refresh() {
        viewModelScope.launch {
            loadFriends()
            // 回到创建页时顺手同步一次凭证：用户可能刚在设置页配好了新服务商。
            runCatching { settings.load() }
            // 语音凭证也同步一次：批量创建面板要列出「已经配好的 TTS」，
            // 而冷启动时内存快照还是空的（只有设置页 load 过）
            runCatching { ttsSettings.load() }
            syncReadyTts()
            val now = AIProviderCatalog.all
                .filter { it.requiresKey.not() || AICredentials.apiKeyOf(it.id).isNotBlank() }
                .map { it.id }
            if (now != configuredProviders) refreshProviders()
        }
    }

    /**
     * 手动刷新聚合平台的模型价格。
     *
     * 创建页里就能刷，不用为了看一眼最新价专门跑一趟设置页。
     * 抓的是公开定价页，和用户填的 Key 无关，所以没配 Key 也能刷；
     * 失败不动已有价格，只把结果通过 Toast 说一声。
     */
    fun refreshPrice() {
        val state = _uiState.value
        if (!state.isGateway || state.isRefreshingPrice) return
        val catalog = AIProviderCatalog.all.firstOrNull { it.name == state.selectedBrand } ?: return
        if (!ModelPrices.canRefresh(catalog.id)) return

        _uiState.update { it.copy(isRefreshingPrice = true) }

        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { priceRefresher.refresh() }.getOrDefault(false)
            }
            if (!ok) {
                // 失败保留上次的价格，用户至少还能参考
                _uiState.update { it.copy(isRefreshingPrice = false) }
                GlobalUiEventManager.sendEventSync(GlobalUiEvent.ShowToast("刷新失败，仍在显示上次的价格"))
                return@launch
            }

            // 刷成功后把当前的模型列表按新价格重算，不用重新选平台
            val repriced = modelDetailCache[state.selectedBrand].orEmpty().map { model ->
                model.copy(price = ModelPrices.lookup(catalog.id, model.id) ?: model.price)
            }
            if (repriced.isNotEmpty()) modelDetailCache[state.selectedBrand] = repriced
            _uiState.update { current ->
                if (current.selectedBrand != state.selectedBrand) {
                    current
                } else {
                    current.copy(
                        isRefreshingPrice = false,
                        gatewayModels = repriced,
                        priceIsSnapshot = false,
                        priceSnapshotDate = ModelPrices.snapshotDate(catalog.id),
                        error = null
                    )
                }
            }
            GlobalUiEventManager.sendEventSync(GlobalUiEvent.ShowToast("已更新为最新价格"))
        }
    }

    private suspend fun loadFriends() {
        val storedFriends = runCatching { friendRepository.load() }.getOrDefault(emptyList())
        val avatar = runCatching { profileRepository.loadAvatarPath() }.getOrNull()
        _uiState.update { state ->
            state.copy(
                friends = storedFriends.map { it.toFriend() },
                userAvatarPath = avatar ?: state.userAvatarPath,
                loaded = true
            )
        }
    }

    fun selectBrand(brand: String) {
        val state = _uiState.value
        if (brand == state.selectedBrand) return
        val provider = state.providers.firstOrNull { it.brand == brand } ?: return

        val cached = modelCache[brand]
        val cachedDetail = modelDetailCache[brand]
        val catalogId = AIProviderCatalog.all.firstOrNull { it.name == brand }?.id
        _uiState.update {
            it.copy(
                selectedBrand = brand,
                models = cached.orEmpty(),
                selectedModel = provider.defaultModel,
                isLoadingModels = cached.isNullOrEmpty(),
                error = null,
                // 切厂商时用该平台自己的缓存，没有就清空，避免串台显示
                gatewayModels = cachedDetail.orEmpty(),
                isGateway = provider.isGateway,
                priceCurrency = catalogId?.let { id -> ModelPrices.currencyOf(id) } ?: ModelPrice.USD,
                priceIsSnapshot = catalogId != null &&
                    cachedDetail.orEmpty().any { model -> model.price != null } &&
                    ModelPrices.isSnapshot(catalogId),
                priceSnapshotDate = catalogId?.let { ModelPrices.snapshotDate(it) }
            )
        }
        if (cached.isNullOrEmpty()) {
            loadModelsFor(brand)
        }
    }

    fun selectModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun openChat(friendId: String) {
        _uiState.update { it.copy(activeFriendId = friendId, input = "", error = null) }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /**
     * 建角色页试听音色。
     *
     * 为什么不在设置页做：音色是角色属性，而且**只能靠真合成一次来判断能不能用**。
     * 腾讯云的精品音色、火山的部分渠道音色要在控制台单独开通，Key 完全正常也会失败；
     * 用户在这里当场听到报错，才知道该换一个音色，而不是回头怀疑自己 Key 填错了。
     *
     * 凭证读盘而不是用内存快照：快照是给朗读用的，可能还没加载；
     * 试听是低频操作，多读一次盘没有性能问题。
     */
    /** 换服务商 / 换音色时清掉上一次的报错，否则用户会以为新服务也是坏的。 */
    fun clearVoicePreviewError() {
        _uiState.update { it.copy(previewError = null) }
    }

    fun previewVoice(ttsConfigId: String?, voiceId: String?) {
        // 播放器一次只放一条，连点会把上一段截断并叠着响
        if (_uiState.value.previewingVoice) return
        val provider = TTSProviderCatalog.byId(ttsConfigId) ?: TTSProviderCatalog.default()
        _uiState.update {
            it.copy(previewingVoice = true, previewingVoiceId = voiceId, previewError = null)
        }
        viewModelScope.launch {
            val credential = withContext(Dispatchers.IO) {
                runCatching { ttsSettings.load() }.getOrDefault(emptyList())
                    .firstOrNull { it.providerId == provider.id }
            }
            if (credential == null || credential.apiKey.isBlank()) {
                _uiState.update {
                    it.copy(
                        previewingVoice = false,
                        previewError = "还没配「${provider.name}」的语音凭证。" +
                            "先在上面选一家配过的服务，或去 设置 → 语音朗读 里填好并测试通过。"
                    )
                }
                return@launch
            }
            val outcome = runCatching { previewPlayer.preview(provider, credential, voiceId) }
            val failure = outcome.getOrNull()?.exceptionOrNull() ?: outcome.exceptionOrNull()
            _uiState.update {
                it.copy(
                    previewingVoice = false,
                    previewError = failure?.let { error -> TTSReadableError.of(error, provider.name) }
                )
            }
        }
    }

    fun createFriend(
        name: String,
        role: String,
        prompt: String,
        avatarBytes: ByteArray? = null,
        thinkingEnabled: Boolean = false,
        voiceId: String? = null,
        ttsConfigId: String? = null,
    ): Friend? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return null

        val state = _uiState.value
        val provider = state.selectedProvider
        val friend = Friend(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            role = role.trim(),
            prompt = prompt.trim(),
            brand = state.selectedBrand,
            company = provider?.company.orEmpty(),
            model = state.selectedModel.ifBlank { provider?.defaultModel.orEmpty() },
            thinkingEnabled = thinkingEnabled,
            voiceId = voiceId,
            ttsConfigId = ttsConfigId
        )
        _uiState.update {
            it.copy(
                friends = it.friends + friend,
                activeFriendId = friend.id,
                error = null,
                input = ""
            )
        }
        viewModelScope.launch {
            runCatching { friendRepository.upsert(friend.toStored()) }
        }
        if (avatarBytes != null) {
            viewModelScope.launch {
                val path = avatarStore.save(avatarBytes, friend.id) ?: return@launch
                updateFriendAvatar(friend.id, path)
            }
        }
        return friend
    }

    /**
     * 批量创建：一次建 [count] 个，人设、模型、音色各自随机搭配。
     *
     * 和 [createFriend] 分开，是因为方向相反：单个创建是「用户已经选好了」，
     * 这里用户只说建几个，剩下从**已配置**的模型与语音里随机分。
     * 分配规则在 `BatchRoster`（纯函数、有单测），这里只负责落库与界面。
     *
     * @return 实际创建数量；0 表示没有可用的人设或模型，什么都没建。
     */
    fun createFriendsBatch(
        personas: List<Persona>,
        count: Int,
        brands: Set<String>,
        ttsIds: Set<String>,
        allowPaid: Boolean = true,
    ): Int {
        val state = _uiState.value
        // 再按当前已配置筛一遍：面板是打开时取的快照，期间用户可能删了某家的 Key
        val providers = state.providers.filter { it.brand in brands }.ifEmpty { state.providers }
        val slots = BatchRoster.plan(
            personas = personas,
            count = count,
            providers = providers,
            modelsOf = { brand -> modelCache[brand].orEmpty() },
            voicesOf = { id -> VoiceCatalog.forProvider(id).mapNotNull { it.id } },
            ttsIds = ttsIds,
            existingNames = state.friends.map { it.name }.toSet(),
            allowPaid = allowPaid,
            costOf = ::modelCost,
        )
        if (slots.isEmpty()) {
            // 一个都没建出来必须说清楚原因。最常见的不是没凭证，而是
            // 「关掉了付费模型 + 这几家一个轻量档都没有」——
            // 不说的话，用户只会看到面板又弹回来（点了没反应）。
            if (!allowPaid) {
                GlobalUiEventManager.sendEventSync(
                    GlobalUiEvent.ShowToast(
                        "这几家没有查到免费或轻量档的模型：价格接口拿不到的模型不会被选。" +
                            "把「允许付费模型」打开，或者去控制台看看哪档便宜"
                    )
                )
            }
            return 0
        }

        val created = slots.map { slot ->
            Friend(
                id = UUID.randomUUID().toString(),
                name = slot.name,
                role = slot.persona.role,
                prompt = slot.persona.prompt,
                brand = slot.brand,
                company = slot.company,
                model = slot.model,
                // 批量不走深度思考：一批建出来的角色默认该是能立刻对话的状态，
                // 开了思考每条回复都要等一截，用户会以为是卡住了
                thinkingEnabled = false,
                voiceId = slot.voiceId,
                ttsConfigId = slot.ttsConfigId
            )
        }
        // 一次性更新：逐个 append 会让列表重建 N 次，角色一多就明显抖
        _uiState.update { it.copy(friends = it.friends + created, error = null) }
        viewModelScope.launch {
            // 落库失败不回滚界面：用户已经看到了，删掉更困惑；下次刷新会自然消失
            created.forEach { friend -> runCatching { friendRepository.upsert(friend.toStored()) } }
        }
        GlobalUiEventManager.sendEventSync(
            GlobalUiEvent.ShowToast("已创建 ${created.size} 个智能体")
        )
        return created.size
    }

    /**
     * 某个模型贵不贵。
     *
     * 价格优先级：模型列表里带回来的报价（OpenRouter 这类，最准）→
     * 内置快照 / 定价页抓取（硅基流动）→ 只能看模型名里的档位词。
     * 官方厂商（方舟、百炼、TokenHub、MiMo）没有公开的价格接口，
     * 走到最后一步是常态，所以「查不到」要如实返回 UNKNOWN，不能猜成免费。
     */
    private fun modelCost(brand: String, model: String): ModelCost {
        val providerId = AIProviderCatalog.all.firstOrNull { it.name == brand }?.id.orEmpty()
        val price = modelDetailCache[brand]?.firstOrNull { it.id == model }?.price
        if (price != null) return ModelCosts.fromPrice(price, ModelPrices.currencyOf(providerId))
        return ModelCosts.of(providerId, model)
    }

    fun saveFriendEdits(
        friendId: String,
        name: String,
        role: String,
        prompt: String,
        avatarBytes: ByteArray?,
        thinkingEnabled: Boolean,
        voiceId: String?,
        ttsConfigId: String?,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return

        _uiState.update { state ->
            state.copy(
                friends = state.friends.map { friend ->
                    if (friend.id == friendId) {
                        friend.copy(
                            name = trimmedName,
                            role = role.trim(),
                            prompt = prompt.trim(),
                            thinkingEnabled = thinkingEnabled,
                            voiceId = voiceId,
                            ttsConfigId = ttsConfigId
                        )
                    } else {
                        friend
                    }
                },
                error = null
            )
        }

        val updated = _uiState.value.friends.firstOrNull { it.id == friendId } ?: return
        viewModelScope.launch {
            runCatching { friendRepository.upsert(updated.toStored()) }
        }
        if (avatarBytes != null) {
            viewModelScope.launch {
                val path = avatarStore.save(avatarBytes, friendId) ?: return@launch
                updateFriendAvatar(friendId, path)
            }
        }
    }

    fun setFriendAvatar(friendId: String, bytes: ByteArray) {
        viewModelScope.launch {
            val path = avatarStore.save(bytes, friendId) ?: return@launch
            updateFriendAvatar(friendId, path)
        }
    }

    fun setUserAvatar(bytes: ByteArray) {
        viewModelScope.launch {
            val path = avatarStore.save(bytes, USER_AVATAR_ID) ?: return@launch
            runCatching { profileRepository.saveAvatarPath(path) }
            _uiState.update { it.copy(userAvatarPath = path) }
        }
    }

    private fun updateFriendAvatar(friendId: String, path: String) {
        _uiState.update { state ->
            state.copy(
                friends = state.friends.map { friend ->
                    if (friend.id == friendId) friend.copy(avatarPath = path) else friend
                }
            )
        }
        viewModelScope.launch {
            runCatching { friendRepository.updateAvatar(friendId, path) }
        }
    }

    fun send() {
        val state = _uiState.value
        val friend = state.activeFriend ?: return
        val text = state.input.trim()
        if (text.isEmpty() || state.isSending) return

        val history = friend.messages + ChatMessage.user(text)
        appendMessage(friend.id, ChatMessage.user(text))
        _uiState.update { it.copy(input = "", isSending = true, error = null, streaming = null) }
        startReply(friend.id, friend, history)
    }

    fun retry() {
        val state = _uiState.value
        val friend = state.activeFriend ?: return
        if (state.isSending || friend.messages.isEmpty()) return

        _uiState.update { it.copy(isSending = true, error = null, streaming = null) }
        startReply(friend.id, friend, friend.messages)
    }

    /**
     * 停止生成。
     *
     * 已经吐出来的半句话会保留下来，而不是整段丢弃：用户看着字一个个出现，
     * 突然全部消失会让人以为出错了。留下的内容和他看到的最后一眼一致，
     * 不满意可以直接接着让他重说。
     */
    fun stopReply() {
        replyJob?.cancel()
    }

    private fun loadModelsFor(brand: String) = viewModelScope.launch {
        val provider = _uiState.value.providers.firstOrNull { it.brand == brand } ?: return@launch
        val ai = aiFor(brand) ?: run {
            _uiState.update { it.copy(isLoadingModels = false) }
            return@launch
        }

        _uiState.update { it.copy(isLoadingModels = true) }
        ai.requestModel()

        val available = ai.modelSet.toList().ifEmpty { provider.fallbackModels }
        modelCache[brand] = available

        // 聚合平台的模型带价格，官方厂商没有（ai.gatewayModels 为空）
        val detailed = ai.gatewayModels
        modelDetailCache[brand] = detailed
        val catalogId = AIProviderCatalog.all.firstOrNull { it.name == brand }?.id

        _uiState.update { state ->
            if (state.selectedBrand != brand) {
                state
            } else {
                state.copy(
                    isLoadingModels = false,
                    models = available,
                    // 列表可能为空（网关挂了又没兜底模型），不能直接 first()
                    selectedModel = state.selectedModel
                        .takeIf { it in available }
                        ?: provider.defaultModel.takeIf { it in available }
                        ?: available.firstOrNull().orEmpty(),
                    gatewayModels = detailed,
                    isGateway = ai.isGateway,
                    priceCurrency = ai.priceCurrency,
                    priceIsSnapshot = catalogId != null &&
                        detailed.any { it.price != null } &&
                        ModelPrices.isSnapshot(catalogId),
                    priceSnapshotDate = catalogId?.let { ModelPrices.snapshotDate(it) }
                )
            }
        }
    }

    /** 起一轮回复。重试和首次发送走同一条路，避免两边对「取消旧请求」的处理不一致。 */
    private fun startReply(friendId: String, friend: Friend, history: List<ChatMessage>) {
        replyJob?.cancel()
        replyJob = viewModelScope.launch { requestReply(friendId, friend, history) }
    }

    private suspend fun requestReply(
        friendId: String,
        friend: Friend,
        history: List<ChatMessage>
    ) {
        // 累加器放在 try 外面：被取消时 catch 里还要用它们把半句话存下来
        val content = StringBuilder()
        val reasoning = StringBuilder()
        var lastPush = 0L
        try {
            // 以前 Key 是编译进 App 的，这里不会缺；现在全由用户自己填，
            // 老好友或刚换设备时很可能一把 Key 都没有。直接发请求会拿到
            // 一段 401，用户看不出是「该去填 Key」还是「Key 填错了」。
            val provider = AIProviderCatalog.byName(friend.brand)
            if (provider != null && provider.requiresKey &&
                AICredentials.apiKeyOf(provider.id).isBlank()
            ) {
                throw MissingKeyException(provider.name)
            }
            val ai = aiFor(friend.brand)
                ?: throw IllegalStateException("暂不支持的厂商：${friend.brand}")

            ai.chatStream(
                messages = history.trimToBudget(),
                model = friend.model,
                systemPrompt = friend.systemPrompt,
                enableThinking = friend.thinkingEnabled
            ).collect { delta ->
                content.append(delta.content)
                reasoning.append(delta.reasoning)
                // 每帧都推会把主线程的重组排满，长回复反而更卡。
                // 60ms 一次肉眼已经是「一个字一个字」的效果了。
                val now = System.currentTimeMillis()
                if (now - lastPush >= STREAM_PUSH_INTERVAL_MS) {
                    lastPush = now
                    pushStreaming(friendId, content, reasoning)
                }
            }

            val full = content.toString()
            if (full.isBlank()) throw IllegalStateException("模型没有返回内容，请重试")
            commitReply(friendId, full, reasoning.toString())
        } catch (cancelled: CancellationException) {
            commitReply(friendId, content.toString(), reasoning.toString())
            throw cancelled
        } catch (t: MissingKeyException) {
            _uiState.update {
                it.copy(error = "还没有填 ${t.providerName} 的 API Key，去设置页填一把就能继续聊。")
            }
        } catch (t: Throwable) {
            _uiState.update { it.copy(error = t.readableMessage(friend.brand, friend.model)) }
        } finally {
            _uiState.update { it.copy(isSending = false, streaming = null) }
            replyJob = null
        }
    }

    /**
     * 把正在生成的内容推给界面。
     *
     * 只推给当前正在看的那个好友：用户切到别人那儿以后，这里的重组没有意义，
     * 但请求本身要继续跑完——切回来还能看到完整结果。
     */
    private fun pushStreaming(friendId: String, content: StringBuilder, reasoning: StringBuilder) {
        if (_uiState.value.activeFriendId != friendId) return
        _uiState.update {
            it.copy(streaming = ChatMessage.assistant(content.toString(), reasoning.ifNotBlank()))
        }
    }

    /**
     * 一轮结束（说完了、被停止、或者出错了）时把已有的内容落成正式消息。
     *
     * 空内容不落：那会留下一个点了没反应的气泡，重试也没法解释它是哪来的。
     */
    private fun commitReply(friendId: String, content: String, reasoning: String) {
        _uiState.update { it.copy(streaming = null) }
        if (content.isBlank()) return
        if (_uiState.value.friends.none { it.id == friendId }) return
        appendMessage(friendId, ChatMessage.assistant(content, reasoning.takeIf { it.isNotBlank() }))
    }

    /** 全是空白就当作没有思考过程，别在界面上留一块空的「思考」标题。 */
    private fun StringBuilder.ifNotBlank(): String? = toString().takeIf { it.isNotBlank() }

    private fun aiFor(brand: String): IAI? {
        ais[brand]?.let { return it }
        val created = AIFactory().createAI(brand) ?: return null
        ais[brand] = created
        return created
    }

    private fun appendMessage(friendId: String, message: ChatMessage) {
        _uiState.update { state ->
            state.copy(
                friends = state.friends.map { friend ->
                    if (friend.id == friendId) {
                        friend.copy(messages = friend.messages + message)
                    } else {
                        friend
                    }
                }
            )
        }
        viewModelScope.launch {
            runCatching {
                friendRepository.appendMessage(
                    friendId = friendId,
                    message = StoredMessage(
                        role = message.role,
                        content = message.content,
                        // 思考过程跟消息一起落库，退出页面再回来还在
                        reasoning = message.reasoning
                    )
                )
            }
        }
    }

    /**
     * 报错文案。
     *
     * 传 brand/model 只为一件事：404 时能点名到模型。用户手上可能是批量建出来的
     * 十几个角色，「接口或模型不存在」不说是哪一个，等于没说。
     */
    private fun Throwable.readableMessage(brand: String? = null, model: String? = null): String {
        var message = ""
        handleError { message = it }
        val base = message.ifBlank { "发生未知错误，请重试" }
        return withModelHint(base, brand, model)
    }

    private companion object {
        const val USER_AVATAR_ID = "user"

        /**
         * 流式推送给界面的最小间隔。
         *
         * token 级推送看起来最「实时」，但一次长回复会有几千次重组，主线程跟不上，
         * 反而表现为卡顿和掉帧。60ms 一次在观感上和逐字毫无区别。
         */
        const val STREAM_PUSH_INTERVAL_MS = 60L
    }
}


/**
 * 用户还没给这个服务商填 Key。
 *
 * 单独一个类型，是因为它的提示和网络错误完全不同 ——
 * 要告诉用户「去哪里做什么」，而不是把 401 原文丢给他。
 */
private class MissingKeyException(val providerName: String) : Exception()