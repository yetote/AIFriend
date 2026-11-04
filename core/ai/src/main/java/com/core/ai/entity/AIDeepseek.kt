package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.DeepseekModelResponse
import com.core.ai.model.ThinkingConfig
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.AIConfigs
import com.core.repository.WebRepository

class AIDeepseek : OpenAICompatibleAI(
    brandName = "Deepseek",
    company = "深度求索",
    config = AIConfigs.deepseekConfig()
) {
    override suspend fun requestModel() {
        val response = WebRepository.instance
            .getModel<DeepseekModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                return
            }
        modelSet.clear()
        modelSet.addAll(response.deepseekModelList.map { it.id })
    }

    override fun ChatCompletionRequest.applyThinking(
        enableThinking: Boolean
    ): ChatCompletionRequest =
        if (enableThinking) this else copy(thinking = ThinkingConfig("disabled"))
}
