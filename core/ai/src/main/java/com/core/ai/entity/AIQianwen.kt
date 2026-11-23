package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.GatewayModel
import com.core.ai.model.QianwenModelResponse
import com.core.ai.model.toChatModelIds
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.WebRepository

class AIQianwen : OfficialAI(
    brandName = "通义千问",
    company = "阿里巴巴",
    providerId = "qianwen"
) {
    /** 百炼上代理了别家模型，按牌子分组展示；自家 Qwen 排在最前。 */
    override val gatewayModels: List<GatewayModel>
        get() = buildVendorGroups(modelSet.toList())

    override suspend fun loadModels() {
        val response = WebRepository.instance
            .getModel<QianwenModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                useFallbackModels()
                return
            }
        val models = response.qianwenModelList.toChatModelIds()
        modelSet.clear()
        if (models.isEmpty()) {
            useFallbackModels()
        } else {
            modelSet.addAll(models)
        }
    }

    override fun ChatCompletionRequest.applyThinking(
        enableThinking: Boolean
    ): ChatCompletionRequest =
        if (enableThinking) this else copy(enableThinking = false)

    /** 千问只认 `enable_search`；`web_search_options` 在它这里是「不支持，请改用 enable_search」。 */
    override val supportsWebSearch: Boolean get() = true

    override fun ChatCompletionRequest.applyWebSearch(
        webSearch: Boolean
    ): ChatCompletionRequest =
        if (webSearch) copy(enableSearch = true) else this
}
