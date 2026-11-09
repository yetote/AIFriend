package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.KimiModelResponse
import com.core.ai.model.ThinkingConfig
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.WebRepository

class AIKimi : OfficialAI(
    brandName = "Kimi",
    company = "月之暗面",
    providerId = "kimi"
) {
    override suspend fun loadModels() {
        val response = WebRepository.instance
            .getModel<KimiModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                useFallbackModels()
                return
            }
        val models = response.kimiModelList
            .mapNotNull { it.id }
            .filter { it.isNotBlank() }
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
