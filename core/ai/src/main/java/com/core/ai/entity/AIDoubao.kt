package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.DoubaoModelResponse
import com.core.ai.model.ThinkingConfig
import com.core.ai.model.toChatModelIds
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.AIConfigs
import com.core.repository.WebRepository

class AIDoubao : OpenAICompatibleAI(
    brandName = "豆包",
    company = "字节跳动",
    config = AIConfigs.doubaoConfig()
) {
    override suspend fun requestModel() {
        val response = WebRepository.instance
            .getModel<DoubaoModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                return
            }
        modelSet.clear()
        modelSet.addAll(response.doubaoModelList.toChatModelIds())
    }

    override fun ChatCompletionRequest.applyThinking(
        enableThinking: Boolean
    ): ChatCompletionRequest =
        if (enableThinking) this else copy(thinking = ThinkingConfig("disabled"))
}
