package com.core.ai.factory

import com.core.ai.entity.AIDeepseek
import com.core.ai.entity.AIDoubao
import com.core.ai.entity.AIGateway
import com.core.ai.entity.AIGemini
import com.core.ai.entity.AIKimi
import com.core.ai.entity.AIQianwen
import com.core.ai.entity.AISpark
import com.core.ai.entity.AIZhipu
import com.core.ai.entity.IAI
import com.core.repository.settings.AIProviderCatalog

class AIFactory : IFactory {
    fun <T : IAI> createAI(aiClass: Class<T>): T {
        return aiClass.getDeclaredConstructor().newInstance()
    }

    /**
     * 按品牌名创建实例。
     *
     * 两条路径：内置品牌走 [aiMap] 的无参构造；
     * 聚合网关要带上服务商信息，走 [gatewayMap]。
     */
    fun createAI(brandName: String): IAI? {
        gatewayMap[brandName]?.let { return AIGateway(it) }
        return aiMap[brandName]?.let { createAI(it) }
    }

    companion object {
        val aiMap = mutableMapOf(
            "Gemini" to AIGemini::class.java,
            "Deepseek" to AIDeepseek::class.java,
            "豆包" to AIDoubao::class.java,
            "智谱清言" to AIZhipu::class.java,
            "Kimi" to AIKimi::class.java,
            "通义千问" to AIQianwen::class.java,
            "星火" to AISpark::class.java,
        )

        /**
         * 聚合网关品牌名 → 服务商。
         *
         * 用展示名做键，是因为好友表里存的就是品牌名字符串，
         * 这样用户换厂商不用改数据库结构。
         */
        val gatewayMap: Map<String, com.core.repository.settings.AIProvider> =
            AIProviderCatalog.all.associateBy { it.name }

        fun getAIClassByName(name: String): Class<out IAI>? = aiMap[name]

        /** 所有可选的品牌名，创建页按这个顺序展示。 */
        fun brandNames(): List<String> =
            aiMap.keys.toList() + gatewayMap.keys.toList()
    }
}
