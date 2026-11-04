package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.QianwenModelResponse
import com.core.ai.model.toChatModelIds
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.AIConfigs
import com.core.repository.WebRepository

class AIQianwen : OpenAICompatibleAI(
    brandName = "通义千问",
    company = "阿里巴巴",
    config = AIConfigs.qianwenConfig()
) {
    override suspend fun requestModel() {
        val response = WebRepository.instance
            .getModel<QianwenModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                return
            }
        modelSet.clear()
        modelSet.addAll(response.qianwenModelList.toChatModelIds())
    }

    override fun ChatCompletionRequest.applyThinking(
        enableThinking: Boolean
    ): ChatCompletionRequest =
        if (enableThinking) this else copy(enableThinking = false)
}
