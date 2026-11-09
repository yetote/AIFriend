package com.core.ai.entity

class AISpark : OfficialAI(
    brandName = "星火",
    company = "科大讯飞",
    providerId = "spark"
) {
    /**
     * 讯飞没有开放 /models 接口（实测 404），模型名走目录里的兜底列表。
     */
    override suspend fun loadModels() {
        useFallbackModels()
    }
}
