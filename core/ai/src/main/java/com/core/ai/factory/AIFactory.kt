package com.core.ai.factory

import com.core.ai.entity.AIDeepseek
import com.core.ai.entity.AIDoubao
import com.core.ai.entity.AIGemini
import com.core.ai.entity.AIKimi
import com.core.ai.entity.AIQianwen
import com.core.ai.entity.AIZhipu
import com.core.ai.entity.IAI

class AIFactory : IFactory {
    fun <T : IAI> createAI(aiClass: Class<T>): T {
        return aiClass.getDeclaredConstructor().newInstance()
    }

    /**
     * 按品牌名创建实例，品牌名以 [companion] 中的 aiMap 为唯一来源。
     */
    fun createAI(brandName: String): IAI? = aiMap[brandName]?.let { createAI(it) }

    companion object {
        val aiMap = mutableMapOf(
            "Gemini" to AIGemini::class.java,
            "Deepseek" to AIDeepseek::class.java,
            "豆包" to AIDoubao::class.java,
            "智谱清言" to AIZhipu::class.java,
            "Kimi" to AIKimi::class.java,
            "通义千问" to AIQianwen::class.java,
        )

        fun getAIClassByName(name: String): Class<out IAI>? {
            return aiMap[name]
        }
    }
}