package com.core.ai.entity

import com.core.ai.model.OpenAIModelResponse
import com.core.ai.model.isLikelyChatModel
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.WebRepository

/**
 * MiniMax 官方接口（国内版）。
 *
 * 这个类**不覆盖 applyThinking**：MiniMax-M3.1-Flash 是恒定思考的，
 * 官方明确说发 `thinking: {"type": "disabled"}` 会直接返回 400
 * （"requires adaptive thinking"）。所以干脆一个字段都不发，
 * 让模型走自己的默认值 —— 相应地 [com.core.repository.settings.AIProvider]
 * 里把 thinkingSwitchable 关掉了，创建页不会给用户这个开关。
 */
class AIMiniMax : OfficialAI(
    brandName = "MiniMax",
    company = "MiniMax",
    providerId = "minimax"
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
            // 语音、音乐、海螺视频这些是另一套接口和计费，混进来只会让人选错
            .filterNot { id -> NON_CHAT_ENTRIES.any { id.contains(it, ignoreCase = true) } }
            .distinct()
            .sortedBy { it.lowercase() }

        modelSet.clear()
        if (models.isEmpty()) {
            useFallbackModels()
        } else {
            modelSet.addAll(models)
        }
    }

    private companion object {
        val NON_CHAT_ENTRIES = listOf(
            "speech", "hailuo", "music", "video", "audio", "tts", "embedding", "image"
        )
    }
}
