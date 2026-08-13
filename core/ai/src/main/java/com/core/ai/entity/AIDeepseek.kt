package com.core.ai.entity

import com.core.ai.model.AIRequestBody
import com.core.ai.model.DeepseekChatResponse
import com.core.ai.model.DeepseekModelResponse
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.AIConfigs
import com.core.repository.WebRepository

class AIDeepseek : IAI {
    override var brandName="Deepseek"
    override var company="深度求索"
    val config = AIConfigs.deepseekConfig()
    override suspend fun chat(message: String, model: String): String {
        val content =
            WebRepository.instance.chat<DeepseekChatResponse>(config, AIRequestBody(model, message)).fold(
                onSuccess = { response ->
                    response.output.find {
                        it.type == "message"
                    }?.deepseekContent?.first()?.text ?: "No message found in response"
                },
                onFailure = { error ->
                    handleCoreError(error)
                }
            )
        return "Deepseek AI response to: $content"
    }

    override suspend fun requestModel() {
        WebRepository.instance.getModel<DeepseekModelResponse>(config)
            .fold(
                onSuccess = { response ->
                    modelSet.clear()
                    modelSet.addAll(response.data.map { it.id })
                },
                onFailure = { error ->
                    handleCoreError(error)
                }
            )
    }

    override var modelSet: MutableSet<String> = mutableSetOf()
}