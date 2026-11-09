package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.DeepseekModelResponse
import com.core.ai.model.ThinkingConfig
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.WebRepository

class AIDeepseek : OfficialAI(
    brandName = "Deepseek",
    company = "深度求索",
    providerId = "deepseek"
) {
    override suspend fun loadModels() {
        val response = WebRepository.instance
            .getModel<DeepseekModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                useFallbackModels()
                return
            }
        val models = response.deepseekModelList.mapNotNull { it.id }.filter { it.isNotBlank() }
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
