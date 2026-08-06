package com.core.ai.factory

import com.core.ai.entity.AIGemini
import com.core.ai.entity.IAI
import kotlin.to

class AIFactory : IFactory {
    fun <T : IAI> createAI(aiClass: Class<T>): T {
        return aiClass.getDeclaredConstructor().newInstance()
    }

    companion object {
        val aiMap = mutableMapOf<String, Class<out IAI>>(
            "gemini" to AIGemini::class.java
        )

        fun getAIClassByName(name: String): Class<out IAI>? {
            return aiMap[name]
        }
    }
}