package com.core.ai.entity

import com.core.repository.AIConfigs

class AISpark : OpenAICompatibleAI(
    brandName = "星火",
    company = "科大讯飞",
    config = AIConfigs.sparkConfig()
) {
    /**
     * 讯飞没有开放 /models 接口（实测 404），目前确认可用的对话模型是 x1，先固定下来。
     */
    override suspend fun requestModel() {
        modelSet.clear()
        modelSet.addAll(SPARK_MODELS)
    }

    private companion object {
        val SPARK_MODELS = listOf("x1")
    }
}
