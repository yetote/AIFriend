package com.core.ai.entity

import com.core.ai.model.GatewayModel
import com.core.ai.model.ModelPrices
import com.core.ai.model.OpenAIModelResponse
import com.core.ai.model.toSelectableChatModelsWithPrices
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.ApiConfig
import com.core.repository.WebRepository
import com.core.repository.settings.AIProvider
import com.core.repository.settings.CustomGroupCodec
import com.core.repository.storage.AICredentialGroup
import com.core.repository.storage.AICredentials
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 聚合网关服务商（硅基流动、OpenRouter、自定义…）。
 *
 * 这些平台都遵循 OpenAI Chat Completions 协议，差别只在 baseUrl、Key
 * 和思考开关的字段名，所以统一由一个类承载，用 [provider] 区分。
 *
 * 支持同一自定义中转站下挂多个分组令牌（[AICredentials.groupsOf]）：
 * - 单分组时保持原始模型名；
 * - 多分组时模型 ID 携带 `@groupId::` 前缀，并按分组名归类展示，
 *   发起对话请求前自动路由到对应分组的 Key 并剥离前缀发送原始模型 ID。
 */
class AIGateway(
    private val provider: AIProvider
) : OpenAICompatibleAI(
    brandName = provider.name,
    company = provider.name,
    // 每次请求都会重新读凭证，这里给个占位的空配置
    config = ApiConfig(baseUrl = provider.baseUrl, apiKey = "")
) {

    /**
     * 覆盖成动态配置：Key 和 baseUrl 都从运行时凭证取。
     * 用户没填 Key 时 apiKey 为空，请求会 401，
     * 上层会把「未配置」翻译成可读的提示。
     */
    override val config: ApiConfig
        get() = buildApiConfig(
            baseUrl = AICredentials.baseUrlOf(provider).ifBlank { provider.baseUrl },
            apiKey = AICredentials.apiKeyOf(provider.id),
        )

    override fun configFor(model: String): ApiConfig = buildApiConfig(
        baseUrl = AICredentials.baseUrlOf(provider).ifBlank { provider.baseUrl },
        apiKey = AICredentials.apiKeyFor(provider.id, model),
    )

    override fun resolveApiModel(model: String): String = CustomGroupCodec.rawModelId(model)

    private fun buildApiConfig(baseUrl: String, apiKey: String): ApiConfig = ApiConfig(
        baseUrl = baseUrl,
        chatUrl = "$baseUrl/chat/completions",
        apiKey = apiKey,
        headers = if (apiKey.isBlank()) {
            emptyMap()
        } else {
            mapOf("Authorization" to "Bearer $apiKey")
        }
    )

    override val isGateway: Boolean
        get() = true

    /** 不需要 Key 的平台（本地 Ollama 之类）永远算配好了。 */
    override fun hasCredentials(): Boolean =
        !provider.requiresKey ||
            AICredentials.apiKeyOf(provider.id).isNotBlank() ||
            AICredentials.groupsOf(provider.id).any { it.apiKey.isNotBlank() }

    override val priceCurrency: String
        get() = ModelPrices.currencyOf(provider.id)

    /**
     * 带价格的模型列表。
     *
     * 价格来源分两路：OpenRouter 之类平台在模型上自带 `pricing`，
     * 硅基流动没有，靠 [ModelPrices.lookup] 从内置快照补。
     * 两边都查不到就留 null，UI 不显示价格。
     */
    override val gatewayModels: List<GatewayModel>
        get() = parsedModels

    private var parsedModels: List<GatewayModel> = emptyList()

    override suspend fun requestModel() {
        val groups = AICredentials.groupsOf(provider.id)
        if (groups.size > 1) {
            requestMultiGroupModels(groups)
            return
        }

        // 没有 Key 就不用发请求了，直接给兜底列表
        if (provider.requiresKey && AICredentials.apiKeyOf(provider.id).isBlank()) {
            modelSet.clear()
            modelSet.addAll(provider.fallbackModels)
            parsedModels = provider.fallbackModels.map { fallbackModel(it) }
            return
        }

        val response = WebRepository.instance
            .getModel<OpenAIModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                modelSet.clear()
                modelSet.addAll(provider.fallbackModels)
                parsedModels = provider.fallbackModels.map { fallbackModel(it) }
                return
            }

        val models = response.modelList
            .toSelectableChatModelsWithPrices()
            .map { model ->
                // 平台自带价格优先，没有再查快照
                if (model.price != null) {
                    model
                } else {
                    model.copy(price = ModelPrices.lookup(provider.id, model.id))
                }
            }

        modelSet.clear()
        if (models.isEmpty()) {
            modelSet.addAll(provider.fallbackModels)
            parsedModels = provider.fallbackModels.map { fallbackModel(it) }
        } else {
            modelSet.addAll(models.map { it.id })
            parsedModels = models
        }
    }

    /**
     * 多分组自定义中转站：并发用每个分组各自的 Key 拉取 `/models`，
     * 以分组名作为 UI 分组标签，并在模型 ID 中编码 `groupId` 以精确区分不同分组的同名模型。
     */
    private suspend fun requestMultiGroupModels(groups: List<AICredentialGroup>) {
        val baseUrl = AICredentials.baseUrlOf(provider).ifBlank { provider.baseUrl }
        val combined = coroutineScope {
            groups.map { group ->
                async {
                    val fetched = if (group.apiKey.isBlank() && baseUrl.isBlank()) {
                        null
                    } else {
                        WebRepository.instance
                            .getModel<OpenAIModelResponse>(buildApiConfig(baseUrl, group.apiKey))
                            .getOrNull()
                            ?.modelList
                            ?.toSelectableChatModelsWithPrices()
                    }
                    if (!fetched.isNullOrEmpty()) {
                        fetched.map { model ->
                            val rawId = model.id
                            GatewayModel(
                                id = CustomGroupCodec.encode(group.id, rawId),
                                vendor = group.name,
                                price = model.price ?: ModelPrices.lookup(provider.id, rawId),
                            )
                        }
                    } else {
                        group.models.distinct().map { rawId ->
                            GatewayModel(
                                id = CustomGroupCodec.encode(group.id, rawId),
                                vendor = group.name,
                                price = ModelPrices.lookup(provider.id, rawId),
                            )
                        }
                    }
                }
            }.awaitAll().flatten()
        }

        modelSet.clear()
        if (combined.isEmpty()) {
            modelSet.addAll(provider.fallbackModels)
            parsedModels = provider.fallbackModels.map { fallbackModel(it) }
        } else {
            modelSet.addAll(combined.map { it.id })
            parsedModels = combined
        }
    }

    /** 兜底模型同样尝试补价格，补不到就留空。 */
    private fun fallbackModel(id: String): GatewayModel {
        val decoded = CustomGroupCodec.decode(id)
        val groupName = decoded.groupId?.let { gid ->
            AICredentials.groupsOf(provider.id).firstOrNull { it.id == gid }?.name
        }
        return GatewayModel(
            id = id,
            vendor = groupName ?: decoded.rawModelId.substringBefore('/', missingDelimiterValue = ""),
            price = ModelPrices.lookup(provider.id, decoded.rawModelId)
        )
    }

    /*
     * 不覆盖 applyThinking：网关后面挂着各种模型，`enable_thinking` 只有
     * Qwen3、GLM 这类思考模型认，普通模型收到这个参数会直接 400。
     * 与其按模型名猜它支不支持，不如一律不带，让各家走自己的默认值；
     * 相应地，网关在创建页里标了 thinkingSwitchable = false，不给用户这个开关。
     */
}
