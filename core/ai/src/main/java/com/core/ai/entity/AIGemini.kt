package com.core.ai.entity

import com.core.ai.model.AIRequestBody
import com.core.repository.AIConfigs
import com.core.repository.WebRepository

class AIGemini : IAI {
    override var brandName="Gemini"
    override var company="Google"
    val config = AIConfigs.geminiConfig()
    override suspend fun chat(message: String,model: String): String {

        val content = WebRepository.instance.chat<AIRequestBody>(
            config,
            AIRequestBody(model, message),
        )
        return "Gemini AI response to: $content"
    }

    override suspend fun requestModel() {
        TODO("Not yet implemented")
    }

    override var modelSet: MutableSet<String> = mutableSetOf()

}