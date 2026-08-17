package com.core.ai.factory

import com.core.ai.entity.AIDeepseek
import com.core.ai.entity.AIDoubao
import com.core.ai.entity.AIGemini
import com.core.ai.entity.AIZhipu
import com.core.ai.entity.IAI

class AIFactory : IFactory {
    fun <T : IAI> createAI(aiClass: Class<T>): T {
        return aiClass.getDeclaredConstructor().newInstance()
    }

    companion object {
        val aiMap = mutableMapOf(
            "Gemini" to AIGemini::class.java,
            "Deepseek" to AIDeepseek::class.java,
            "豆包" to AIDoubao::class.java,
            "智谱清言" to AIZhipu::class.java,
        )

        fun getAIClassByName(name: String): Class<out IAI>? {
            return aiMap[name]
        }
    }
}