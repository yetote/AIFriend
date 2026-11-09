package com.core.ai.entity

import com.core.ai.model.GeminiModelResponse
import com.core.ai.model.toChatModelIds
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.WebRepository

class AIGemini : OfficialAI(
    brandName = "Gemini",
    company = "Google",
    providerId = "gemini"
) {
    /**
     * OpenAI 兼容层确实有 /models，但返回的 id 带 `models/` 前缀
     * （`models/gemini-2.5-flash`），聊天接口不认这个前缀，要剥掉。
     */
    override suspend fun loadModels() {
        val response = WebRepository.instance
            .getModel<GeminiModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                useFallbackModels()
                return
            }
        val models = response.geminiModelList
            .toChatModelIds()
            .map { it.removePrefix("models/") }
            .filter { it.isNotBlank() }
        modelSet.clear()
        if (models.isEmpty()) {
            useFallbackModels()
        } else {
            modelSet.addAll(models)
        }
    }
}
