package com.core.ai.entity

import com.core.ai.model.AIRequestBody
import com.core.ai.model.KimiModelResponse
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.AIConfigs
import com.core.repository.WebRepository

class AIKimi : IAI {
    override var brandName = "KIMI"
    override var company = "月之暗面"
    val config = AIConfigs.kimiConfig()
    override suspend fun chat(message: String, model: String): String {

        val content = WebRepository.instance.chat<AIRequestBody>(
            config,
            AIRequestBody(model, message),
        )
        return "Gemini AI response to: $content"
    }

    override suspend fun requestModel() {
        WebRepository.instance.getModel<KimiModelResponse>(config)
            .fold(
                onSuccess = { response ->
                    modelSet.clear()
                    modelSet.addAll(response.kimiModelList.map { it.id ?: "" })
                },
                onFailure = { error ->
                    handleCoreError(error)
                }
            )
    }

    override var modelSet: MutableSet<String> = mutableSetOf()
}