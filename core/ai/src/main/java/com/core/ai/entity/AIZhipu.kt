package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.ThinkingConfig
import com.core.ai.model.ZhipuModelResponse
import com.core.ai.model.toChatModelIds
import com.core.ai.model.ServerTool
import com.core.ai.model.ServerToolConfig
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.WebRepository

class AIZhipu : OfficialAI(
    brandName = "智谱清言",
    company = "北京智谱华章",
    providerId = "zhipu"
) {
    override suspend fun loadModels() {
        val response = WebRepository.instance
            .getModel<ZhipuModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                useFallbackModels()
                return
            }
        val models = response.zhipuModelList.toChatModelIds()
        modelSet.clear()
        if (models.isEmpty()) {
            useFallbackModels()
        } else {
            modelSet.addAll(models)
        }
    }

    /**
     * GLM-5 系列「始终思考」，传 disabled 会直接 400，只能把 reasoning_effort 降到 low；
     * GLM-4.x 支持完全关闭。
     */
    override fun ChatCompletionRequest.applyThinking(
        enableThinking: Boolean
    ): ChatCompletionRequest {
        if (enableThinking) return this
        // 大小写不敏感：/models 返回的 id 可能是 `GLM-5.3`。大小写敏感的话
        // 大写 id 会被当成 GLM-4.x，对「始终思考」的 GLM-5 发 disabled 直接 400。
        return if (model.startsWith("glm-5", ignoreCase = true)) {
            copy(reasoningEffort = "low")
        } else {
            copy(thinking = ThinkingConfig("disabled"))
        }
    }

    /**
     * 智谱用 `tools[].web_search`，不是开关字段。
     *
     * 必须带 `search_result: true`：官方说法是不开它回复里不带引用，
     * 搜到的东西模型用了却看不出出处，排查「到底搜没搜」时完全没有线索。
     */
    override val supportsWebSearch: Boolean get() = true

    override fun ChatCompletionRequest.applyWebSearch(
        webSearch: Boolean
    ): ChatCompletionRequest =
        if (webSearch) {
            copy(
                tools = listOf(
                    ServerTool(
                        type = "web_search",
                        webSearch = ServerToolConfig(enable = true, searchResult = true),
                    )
                )
            )
        } else {
            this
        }
}
