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
     * Gemini 的 OpenAI 兼容层只实现了消息、流式、温度这几个字段，
     * `response_format` 不在支持列表里。声明不支持，调用方会自动降级。
     */
    override val supportsJsonMode: Boolean
        get() = false

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
