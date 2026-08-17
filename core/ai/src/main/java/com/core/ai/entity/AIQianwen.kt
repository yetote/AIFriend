package com.core.ai.entity

import com.core.ai.model.AIRequestBody
import com.core.ai.model.DeepseekChatResponse
import com.core.ai.model.QianwenModelResponse
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.AIConfigs
import com.core.repository.WebRepository

class AIQianwen : IAI {
    override var brandName = "通义千问"
    override var company = "阿里"
    val config = AIConfigs.qianwenConfig()
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
        WebRepository.instance.getModel<QianwenModelResponse>(config)
            .fold(
                onSuccess = { response ->
                    modelSet.clear()
                    modelSet.addAll(response.qianwenModelList.map { it.id ?: "" })
                },
                onFailure = { error ->
                    handleCoreError(error)
                }
            )
    }

    override var modelSet: MutableSet<String> = mutableSetOf()
}