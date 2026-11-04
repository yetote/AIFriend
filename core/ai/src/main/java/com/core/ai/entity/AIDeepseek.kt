package com.core.ai.entity

import com.core.ai.model.ChatMessage
import com.core.ai.model.DeepSeekChatRequest
import com.core.ai.model.DeepSeekCompletionResponse
import com.core.ai.model.DeepseekModelResponse
import com.core.common.GlobalErrorHandler.handleCoreError
import com.core.repository.AIConfigs
import com.core.repository.WebRepository

class AIDeepseek : IAI {
    override var brandName = "Deepseek"
    override var company = "深度求索"
    val config = AIConfigs.deepseekConfig()

    override var modelSet: MutableSet<String> = mutableSetOf()

    /**
     * 走 DeepSeek 的 Chat Completions 协议：
     * POST /chat/completions，body = { model, messages, stream }，回复取 choices[0].message.content。
     */
    override suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String?
    ): String {
        val apiMessages = buildList {
            if (!systemPrompt.isNullOrBlank()) {
                add(ChatMessage.system(systemPrompt))
            }
            addAll(messages)
        }
        val response = WebRepository.instance
            .chat<DeepSeekCompletionResponse>(
                config = config,
                body = DeepSeekChatRequest(model = model, messages = apiMessages)
            )
            .getOrElse { throw it }

        val content = response.choices.firstOrNull()?.message?.content
        return content?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("模型没有返回内容，请重试")
    }

    override suspend fun requestModel() {
        val response = WebRepository.instance.getModel<DeepseekModelResponse>(config)
            .getOrElse {
                handleCoreError(it)
                return
            }
        modelSet.clear()
        modelSet.addAll(response.deepseekModelList.map { it.id })
    }

    @Deprecated("改用 chat(messages, model, systemPrompt)")
    override suspend fun chat(message: String, model: String): String =
        chat(messages = listOf(ChatMessage.user(message)), model = model, systemPrompt = null)
}
