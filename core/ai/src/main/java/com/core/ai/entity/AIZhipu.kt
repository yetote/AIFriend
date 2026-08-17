package com.core.ai.entity

import com.core.ai.model.AIRequestBody
import com.core.ai.model.ZhipuModelResponse
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.AIConfigs
import com.core.repository.WebRepository

class AIZhipu : IAI {
    override var brandName = "智谱清言"
    override var company = "北京智谱华章科技有限公司"
    val config = AIConfigs.zhipuConfig()
    override suspend fun chat(message: String, model: String): String {

        val content = WebRepository.instance.chat<AIRequestBody>(
            config,
            AIRequestBody(model, message),
        )
        return "Gemini AI response to: $content"
    }

    override suspend fun requestModel() {
        WebRepository.instance.getModel<ZhipuModelResponse>(config)
            .fold(
                onSuccess = { response ->
                    modelSet.clear()
                    modelSet.addAll(response.zhipuModelList.map { it.id ?: "" })
                },
                onFailure = { error ->
                    handleCoreError(error)
                }
            )
    }

    override var modelSet: MutableSet<String> = mutableSetOf()
}