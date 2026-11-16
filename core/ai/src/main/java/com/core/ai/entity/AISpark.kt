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

    /**
     * 星火的 OpenAI 兼容层不接受 `response_format`，硬发会被拒。
     * 结构化输出只能靠提示词约束 + 解析兜底，所以这里直接声明不支持。
     */
    override val supportsJsonMode: Boolean
        get() = false
}
