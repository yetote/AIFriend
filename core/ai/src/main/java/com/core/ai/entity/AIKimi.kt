package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.KimiModelResponse
import com.core.ai.model.ThinkingConfig
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.AIConfigs
import com.core.repository.WebRepository

class AIKimi : OpenAICompatibleAI(
    brandName = "Kimi",
    company = "月之暗面",
    config = AIConfigs.kimiConfig()
) {
    override suspend fun requestModel() {
        val response = WebRepository.instance
            .getModel<KimiModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                return
            }
        modelSet.clear()
        modelSet.addAll(
            response.kimiModelList
                .mapNotNull { it.id }
                .filter { it.isNotBlank() }
        )
    }

    override fun ChatCompletionRequest.applyThinking(
        enableThinking: Boolean
    ): ChatCompletionRequest =
        if (enableThinking) this else copy(thinking = ThinkingConfig("disabled"))
}
