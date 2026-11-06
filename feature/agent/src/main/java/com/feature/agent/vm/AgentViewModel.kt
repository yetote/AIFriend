package com.feature.agent.vm

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.core.ai.entity.IAI
import com.core.ai.factory.AIFactory
import com.core.ai.model.ChatMessage
import com.core.ai.model.GatewayModel
import com.core.ai.model.ModelPrice
import com.core.ai.model.ModelPriceRefresher
import com.core.ai.model.ModelPrices
import com.core.common.BaseViewModel
import com.core.common.GlobalUiEvent
import com.core.common.GlobalUiEventManager
import com.core.repository.storage.AICredentials
import com.core.repository.storage.AIProviderSettings
import com.core.repository.storage.AvatarStore
import com.core.repository.storage.FriendRepository
import com.core.repository.storage.LegacyMigration
import com.core.repository.storage.ProfileRepository
import com.core.repository.storage.StoredMessage
import com.core.repository.settings.AIProviderCatalog
import com.feature.agent.contract.AgentUiState
import com.feature.agent.contract.DEFAULT_PROVIDERS
import com.feature.agent.data.toFriend
import com.feature.agent.data.toStored
import com.feature.agent.data.trimToBudget
import com.feature.agent.model.Friend
import com.feature.agent.model.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val modelCache = mutableMapOf<String, List<String>>()

    /** 带价格的模型明细缓存，切回已加载过的平台时直接复用，价格才不会丢。 */
    private val modelDetailCache = mutableMapOf<String, List<GatewayModel>>()
    private val settings = AIProviderSettings(application)

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
        }
    }

    /**
     * 已配置的网关 id 列表。
     *
     * 用它判断「用户是否刚在设置页配好了一个新的网关」：
     * ViewModel 是 Activity 级的，从设置页返回不会重建，
     * 只有比对出变化才重新拉模型，免得每次返回都白跑一次网络请求。
     */
    private var configuredGateways: List<String> = emptyList()

    /**
     * 把「已配置 KEY / 已填地址的聚合网关」并入可选服务商。
     *
     * 没配置的网关不列出来 —— 用户选中之后拉不到模型、也聊不了，
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
        configuredGateways = usable.map { it.id }

        val gateways = usable.map { provider ->
            Provider(
                brand = provider.name,
                company = provider.name,
                defaultModel = provider.fallbackModels.firstOrNull().orEmpty(),
                fallbackModels = provider.fallbackModels,
                thinkingSwitchable = false,
                isGateway = true
            )
        }

        val providers = DEFAULT_PROVIDERS + gateways.filter { gateway ->
            DEFAULT_PROVIDERS.none { it.brand == gateway.brand }
        }

        _uiState.update { state ->
            val brand = state.selectedBrand
                .takeIf { name -> providers.any { it.brand == name } }
                ?: DEFAULT_PROVIDERS.first().brand
            val switched = brand != state.selectedBrand
            val provider = providers.first { it.brand == brand }
            state.copy(
                providers = providers,
                selectedBrand = brand,
                selectedModel = if (!switched) {
                    state.selectedModel
                } else {
                    provider.defaultModel
                },
                // 换厂商时价格必须跟着换，否则会显示上一个平台的价
                isGateway = provider.isGateway,
                gatewayModels = if (switched) emptyList() else state.gatewayModels,
                priceIsSnapshot = false,
                priceSnapshotDate = null
            )
        }
        loadModelsFor(_uiState.value.selectedBrand)
    }

    fun refresh() {
        viewModelScope.launch {
            loadFriends()
            // 回到创建页时顺手同步一次凭证：用户可能刚在设置页配好了新网关。
            runCatching { settings.load() }
            val now = AIProviderCatalog.all
                .filter { it.requiresKey.not() || AICredentials.apiKeyOf(it.id).isNotBlank() }
                .map { it.id }
            if (now != configuredGateways) refreshProviders()
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

    fun createFriend(
        name: String,
        role: String,
        prompt: String,
        avatarBytes: ByteArray? = null,
        thinkingEnabled: Boolean = false
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
            thinkingEnabled = thinkingEnabled
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

    fun saveFriendEdits(
        friendId: String,
        name: String,
        role: String,
        prompt: String,
        avatarBytes: ByteArray?,
        thinkingEnabled: Boolean
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
                            thinkingEnabled = thinkingEnabled
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

    fun send() = viewModelScope.launch {
        val state = _uiState.value
        val friend = state.activeFriend ?: return@launch
        val text = state.input.trim()
        if (text.isEmpty() || state.isSending) return@launch

        val history = friend.messages + ChatMessage.user(text)
        appendMessage(friend.id, ChatMessage.user(text))
        _uiState.update { it.copy(input = "", isSending = true, error = null) }
        requestReply(friend.id, friend, history)
    }

    fun retry() = viewModelScope.launch {
        val state = _uiState.value
        val friend = state.activeFriend ?: return@launch
        if (state.isSending || friend.messages.isEmpty()) return@launch

        _uiState.update { it.copy(isSending = true, error = null) }
        requestReply(friend.id, friend, friend.messages)
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

    private suspend fun requestReply(
        friendId: String,
        friend: Friend,
        history: List<ChatMessage>
    ) {
        try {
            val ai = aiFor(friend.brand)
                ?: throw IllegalStateException("暂不支持的厂商：${friend.brand}")
            val reply = ai.chat(
                messages = history.trimToBudget(),
                model = friend.model,
                systemPrompt = friend.systemPrompt,
                enableThinking = friend.thinkingEnabled
            )
            if (_uiState.value.friends.any { it.id == friendId }) {
                appendMessage(friendId, ChatMessage.assistant(reply))
            }
        } catch (t: Throwable) {
            _uiState.update { it.copy(error = t.readableMessage()) }
        } finally {
            _uiState.update { it.copy(isSending = false) }
        }
    }

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
                    message = StoredMessage(role = message.role, content = message.content)
                )
            }
        }
    }

    private fun Throwable.readableMessage(): String {
        var message = ""
        handleError { message = it }
        return message.ifBlank { "发生未知错误，请重试" }
    }

    private companion object {
        const val USER_AVATAR_ID = "user"
    }
}
