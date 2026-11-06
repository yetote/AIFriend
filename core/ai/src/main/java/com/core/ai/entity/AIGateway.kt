package com.core.ai.entity

import com.core.ai.model.GatewayModel
import com.core.ai.model.ModelPrice
import com.core.ai.model.ModelPrices
import com.core.ai.model.OpenAIModelResponse
import com.core.ai.model.toSelectableChatModelsWithPrices
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.ApiConfig
import com.core.repository.WebRepository
import com.core.repository.settings.AIProvider
import com.core.repository.storage.AICredentials

/**
 * 聚合网关服务商（硅基流动、OpenRouter、自定义…）。
 *
 * 这些平台都遵循 OpenAI Chat Completions 协议，差别只在 baseUrl、Key
 * 和思考开关的字段名，所以统一由一个类承载，用 [provider] 区分。
 *
 * Key 来自用户在设置页填写的凭证，不在 APK 里硬编码。
 * [ApiConfig] 在每次请求前现取，所以用户改完 Key 不用重启 App。
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
        get() {
            val baseUrl = AICredentials.baseUrlOf(provider).ifBlank { provider.baseUrl }
            val apiKey = AICredentials.apiKeyOf(provider.id)
            return ApiConfig(
                baseUrl = baseUrl,
                chatUrl = "$baseUrl/chat/completions",
                apiKey = apiKey,
                headers = if (apiKey.isBlank()) {
                    emptyMap()
                } else {
                    mapOf("Authorization" to "Bearer $apiKey")
                }
            )
        }

    override val isGateway: Boolean
        get() = true

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

    /** 兜底模型同样尝试补价格，补不到就留空。 */
    private fun fallbackModel(id: String): GatewayModel = GatewayModel(
        id = id,
        vendor = id.substringBefore('/', missingDelimiterValue = ""),
        price = ModelPrices.lookup(provider.id, id)
    )

    /*
     * 不覆盖 applyThinking：网关后面挂着各种模型，`enable_thinking` 只有
     * Qwen3、GLM 这类思考模型认，普通模型收到这个参数会直接 400。
     * 与其按模型名猜它支不支持，不如一律不带，让各家走自己的默认值；
     * 相应地，网关在创建页里标了 thinkingSwitchable = false，不给用户这个开关。
     */
}
