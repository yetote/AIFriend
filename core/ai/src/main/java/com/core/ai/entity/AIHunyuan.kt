package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.GatewayModel
import com.core.ai.model.OpenAIModelResponse
import com.core.ai.model.ThinkingConfig
import com.core.ai.model.isLikelyChatModel
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.WebRepository

/**
 * 腾讯混元，走腾讯云 TokenHub 的 OpenAI 兼容接口。
 *
 * 之所以不用老端点（api.hunyuan.cloud.tencent.com）：Hy3 / Hy4 这些新模型只在
 * TokenHub 上有，老平台已经停止新增模型能力、也不再支持新购。
 * 代价是 TokenHub 同时代理了 DeepSeek、GLM、Kimi 等别家模型，
 * 所以这个类跟百炼、方舟一样要实现 [gatewayModels]，按牌子分组展示。
 */
class AIHunyuan : OfficialAI(
    brandName = "混元",
    company = "腾讯",
    providerId = "hunyuan"
) {
    /** TokenHub 上一个 Key 能调多家模型，按牌子分组；自家 hy 系列排在最前。 */
    override val gatewayModels: List<GatewayModel>
        get() = buildVendorGroups(modelSet.toList())

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
            // 翻译、生图生视频、语音这些走的是各自的专用接口，塞进对话列表只会选错
            .filterNot { id -> NON_CHAT_ENTRIES.any { id.contains(it, ignoreCase = true) } }
            .distinct()
            // 旗舰 hy4 顶到最前，其余自家的次之，代理别家的排后面
            .sortedWith(
                compareBy(
                    { id ->
                        if (id.equals(FLAGSHIP, ignoreCase = true)) 0
                        else if (id.startsWith("hy", ignoreCase = true)) 1
                        else 2
                    },
                    { it }
                )
            )

        modelSet.clear()
        if (models.isEmpty()) {
            useFallbackModels()
        } else {
            modelSet.addAll(models)
        }
    }

    /**
     * 关闭深度思考：混元和豆包一样用 `thinking: {"type": "disabled"}`。
     *
     * 但 TokenHub 上代理的 GLM、Kimi、MiniMax 官方注明「不支持关闭」，
     * 对它们发这个字段只会白白换来一个 400，干脆原样放过去。
     */
    override fun ChatCompletionRequest.applyThinking(
        enableThinking: Boolean
    ): ChatCompletionRequest {
        if (enableThinking) return this
        if (THINKING_LOCKED.any { model.contains(it, ignoreCase = true) }) return this
        return copy(thinking = ThinkingConfig("disabled"))
    }

    private companion object {
        const val FLAGSHIP = "hy4"

        /**
         * 混元家族里的非对话条目：翻译（hy-mt2）、生图、生视频、生 3D、语音识别。
         *
         * 特意不排 `role`：hy-role 是官方优化过剧情互动的角色扮演模型，
         * 对「AI 好友」这种场景反而是最对口的，不能当成杂项滤掉。
         */
        val NON_CHAT_ENTRIES = listOf(
            "-mt", "mt2", "image", "video", "3d", "world", "asr", "tts", "embedding"
        )

        /** 在 TokenHub 上无法关闭深度思考的第三方模型。 */
        val THINKING_LOCKED = listOf("glm", "kimi", "minimax")
    }
}
