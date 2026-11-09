package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.DoubaoModelResponse
import com.core.ai.model.GatewayModel
import com.core.ai.model.ThinkingConfig
import com.core.ai.model.toChatModelIds
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.WebRepository

class AIDoubao : OfficialAI(
    brandName = "豆包",
    company = "字节跳动",
    providerId = "doubao"
) {
    /** 方舟上代理了别家模型，按牌子分组展示；自家豆包排在最前。 */
    override val gatewayModels: List<GatewayModel>
        get() = buildVendorGroups(modelSet.toList())

    override suspend fun loadModels() {
        val response = WebRepository.instance
            .getModel<DoubaoModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                useFallbackModels()
                return
            }
        val models = response.doubaoModelList.toChatModelIds()
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
        if (enableThinking) this else copy(thinking = ThinkingConfig("disabled"))
}
