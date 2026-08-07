package com.core.ai.factory

import com.core.ai.entity.AIDeepseek
import com.core.ai.entity.AIGemini
import com.core.ai.entity.IAI
import kotlin.to

class AIFactory : IFactory {
    fun <T : IAI> createAI(aiClass: Class<T>): T {
        return aiClass.getDeclaredConstructor().newInstance()
    }

    companion object {
        val aiMap = mutableMapOf(
            "gemini" to AIGemini::class.java,
            "deepseek" to AIDeepseek::class.java,
        )

        fun getAIClassByName(name: String): Class<out IAI>? {
            return aiMap[name]
        }
    }
}