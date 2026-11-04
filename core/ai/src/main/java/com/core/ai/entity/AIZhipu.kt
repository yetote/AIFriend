package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.ThinkingConfig
import com.core.ai.model.ZhipuModelResponse
import com.core.ai.model.toChatModelIds
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.AIConfigs
import com.core.repository.WebRepository

class AIZhipu : OpenAICompatibleAI(
    brandName = "智谱清言",
    company = "北京智谱华章",
    config = AIConfigs.zhipuConfig()
) {
    override suspend fun requestModel() {
        val response = WebRepository.instance
            .getModel<ZhipuModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                return
            }
        modelSet.clear()
        modelSet.addAll(response.zhipuModelList.toChatModelIds())
    }

    /**
     * GLM-5 系列「始终思考」，传 disabled 会直接 400，只能把 reasoning_effort 降到 low；
     * GLM-4.x 支持完全关闭。
     */
    override fun ChatCompletionRequest.applyThinking(
        enableThinking: Boolean
    ): ChatCompletionRequest {
        if (enableThinking) return this
        return if (model.startsWith("glm-5")) {
            copy(reasoningEffort = "low")
        } else {
            copy(thinking = ThinkingConfig("disabled"))
        }
    }
}
