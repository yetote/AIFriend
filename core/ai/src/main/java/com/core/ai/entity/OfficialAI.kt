package com.core.ai.entity

import com.core.ai.model.GatewayModel
import com.core.ai.model.vendorLabelOf
import com.core.repository.ApiConfig
import com.core.repository.settings.AIProviderCatalog
import com.core.repository.storage.AICredentials

/**
 * 官方厂商（DeepSeek、豆包、智谱、Kimi、通义千问、星火、Gemini）的公共父类。
 *
 * 这些厂商的共同点：一家公司一个模型家族，baseUrl 固定，Key 由用户自己去
 * 各家开放平台申请。APK 里不带任何一把 Key —— 带了的后果是被反编译出来
 * 白刷别人的额度，而且那把 Key 一换版本就得跟着重新发版。
 *
 * [providerId] 指向 [AIProviderCatalog] 里的一条记录，用它在每次请求前
 * 现取 baseUrl 和 Key，所以用户在设置页改完 Key 不用重启 App。
 */
abstract class OfficialAI(
    brandName: String,
    company: String,
    private val providerId: String
) : OpenAICompatibleAI(
    brandName = brandName,
    company = company,
    // 每次请求都会重新读凭证，这里给个占位的空配置
    config = ApiConfig(baseUrl = "", apiKey = "")
) {

    /**
     * 动态配置：请求发生的那一刻才去读用户填的 Key。
     *
     * 没配 Key 时 apiKey 为空，请求会 401 —— 但 [requestModel] 已经先拦住了，
     * 真正会走到这里的只有聊天，而聊天时上层已经把「未配置」翻译成可读提示。
     */
    override val config: ApiConfig
        get() {
            val provider = AIProviderCatalog.byId(providerId)
            val stored = AICredentials.get(providerId)
            val baseUrl = when {
                stored != null && provider != null -> stored.resolvedBaseUrl(provider)
                stored != null -> stored.baseUrl.ifBlank { "" }
                provider != null -> provider.baseUrl
                else -> ""
            }
            val apiKey = AICredentials.apiKeyOf(providerId)
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

    /**
     * 拉模型列表。
     *
     * 没配 Key 就直接给兜底列表：发出去也只会 401，
     * 白白在聊天气泡和日志里留一条错误，用户还以为是网络问题。
     */
    final override suspend fun requestModel() {
        if (AICredentials.apiKeyOf(providerId).isBlank()) {
            useFallbackModels()
            return
        }
        loadModels()
    }

    /** 子类实现真正的拉取与解析。失败时应调用 [useFallbackModels]。 */
    protected abstract suspend fun loadModels()

    /**
     * 官方厂商里「代理了别家模型」的那一类，把自己和第三方拆成组。
     *
     * 百炼、方舟本身就是平台，一个 Key 底下既有自家模型也有 DeepSeek、Kimi 这些
     * 别家的。以前只留自家前缀，用户看到的选择列表比控制台少一大截。
     *
     * 注意 [isGateway] 仍然返回 false：它决定的是「要不要当聚合平台展示」
     * （厂商分组、价格脚注），而价格只有带定价页的平台才有 ——
     * 这里只是借用分组，不能因此给一个查不到价格的平台挂上价格说明。
     */
    protected fun buildVendorGroups(models: List<String>): List<GatewayModel> =
        models.map { id ->
            GatewayModel(id = id, vendor = vendorLabelOf(id, brandName))
        }

    /** 用目录里写好的兜底模型顶上，保证创建页至少能选到一个。 */
    protected fun useFallbackModels() {
        val fallback = AIProviderCatalog.byId(providerId)?.fallbackModels.orEmpty()
        modelSet.clear()
        modelSet.addAll(fallback)
    }
}
