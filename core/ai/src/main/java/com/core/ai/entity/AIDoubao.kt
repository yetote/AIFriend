package com.core.ai.entity

import com.core.ai.model.AIRequestBody
import com.core.ai.model.DoubaoModelResponse
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.AIConfigs
import com.core.repository.WebRepository

class AIDoubao : IAI {
    override var brandName = "Doubao"
    override var company = "字节跳动"
    val config = AIConfigs.doubaoConfig()
    override suspend fun chat(message: String, model: String): String {

        val content = WebRepository.instance.chat<AIRequestBody>(
            config,
            AIRequestBody(model, message),
        )
        return "Gemini AI response to: $content"
    }

    override suspend fun requestModel() {
        WebRepository.instance.getModel<DoubaoModelResponse>(config)
            .fold(
                onSuccess = { response ->
                    modelSet.clear()
                    modelSet.addAll(response.doubaoModelList.map { it.id ?: "" })
                },
                onFailure = { error ->
                    handleCoreError(error)
                }
            )
    }

    override var modelSet: MutableSet<String> = mutableSetOf()
}