package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.OpenAIModelResponse
import com.core.ai.model.ThinkingConfig
import com.core.ai.model.isLikelyChatModel
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.WebRepository

/**
 * 小米 MiMo 开放平台。
 *
 * 选它的理由不是规格 —— V2.6-Flash 在榜单上不算顶尖 —— 而是价格：
 * 输入 ¥1 / 输出 ¥2 每百万，缓存命中输入只要 ¥0.02。
 * 狼人杀那种「九个 AI 每轮各决策一次」的局，一次七十多次调用，
 * 用旗舰模型单局成本能差出二十多倍，这一档是唯一不心疼的。
 */
class AIMiMo : OfficialAI(
    brandName = "小米",
    company = "小米",
    providerId = "mimo"
) {
    override suspend fun loadModels() {
        val response = WebRepository.instance
            .getModel<OpenAIModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                useFallbackModels()
                return
            }
        val models = response.modelList
            .map { it.id.trim() }
            .filter { it.isNotBlank() }
            .filter { isLikelyChatModel(it) }
            .filterNot { id -> NON_CHAT_ENTRIES.any { id.contains(it, ignoreCase = true) } }
            .distinct()
            // flash 是日常档，排在 pro 前面：大部分场景用不上旗舰，先摆便宜的那个
            .sortedWith(
                compareBy(
                    { id -> if (id.contains("flash")) 0 else 1 },
                    { it.lowercase() }
                )
            )

        modelSet.clear()
        if (models.isEmpty()) {
            useFallbackModels()
        } else {
            modelSet.addAll(models)
        }
    }

    /** MiMo 官方的示例报文里就带 `thinking: {"type": "disabled"}`，可以放心关。 */
    override fun ChatCompletionRequest.applyThinking(
        enableThinking: Boolean
    ): ChatCompletionRequest =
        if (enableThinking) this else copy(thinking = ThinkingConfig("disabled"))

    private companion object {
        val NON_CHAT_ENTRIES = listOf("embedding", "tts", "asr", "speech", "video", "image")
    }
}
