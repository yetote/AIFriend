package com.core.ai.entity

import com.core.ai.model.GeminiModelResponse
import com.core.ai.model.toChatModelIds
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.AIConfigs
import com.core.repository.WebRepository

class AIGemini : OpenAICompatibleAI(
    brandName = "Gemini",
    company = "Google",
    config = AIConfigs.geminiConfig()
) {
    override suspend fun requestModel() {
        val response = WebRepository.instance
            .getModel<GeminiModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                return
            }
        modelSet.clear()
        modelSet.addAll(response.geminiModelList.toChatModelIds())
    }
}
