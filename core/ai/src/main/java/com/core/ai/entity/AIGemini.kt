package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
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
     * Google AI Studio 的 OpenAI 兼容层支持通过 `reasoning_effort` 控制思考深度：
     * - Gemini 2.5 Flash / Flash-Lite 可传 `"none"` 彻底关闭思考；
     * - Gemini 2.5 Pro、Gemini 3.x / 4.x、Gemma 4 及 `-latest` 别名不支持 `"none"`，
     *   关思考时统一降档到 `"low"`，避免 400（与智谱 GLM-5 策略一致）；
     * - 更早的 Gemini 2.0 系列无思考能力，不传该字段。
     */
    override fun ChatCompletionRequest.applyThinking(
        enableThinking: Boolean
    ): ChatCompletionRequest {
        if (enableThinking) return this
        val id = model.lowercase()
        return when {
            id.startsWith("gemini-2.0") || id.startsWith("gemini-2-") -> this
            id.startsWith("gemini-2.5") && !id.contains("pro") ->
                copy(reasoningEffort = "none")
            else -> copy(reasoningEffort = "low")
        }
    }

    /**
     * OpenAI 兼容层确实有 /models，但返回的 id 带 `models/` 前缀
     * （`models/gemini-3.5-flash-lite`、`models/gemma-4-31b-it`），聊天接口不认这个前缀，
     * 由 [toChatModelIds] 统一剥离并过滤掉非对话模型。
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
            .filter { it.isNotBlank() }
        modelSet.clear()
        if (models.isEmpty()) {
            useFallbackModels()
        } else {
            modelSet.addAll(models)
        }
    }
}
