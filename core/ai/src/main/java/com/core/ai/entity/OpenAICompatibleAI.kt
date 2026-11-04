package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.ChatCompletionResponse
import com.core.ai.model.ChatMessage
import com.core.repository.ApiConfig
import com.core.repository.WebRepository

/**
 * OpenAI Chat Completions 协议的公共实现：
 * 子类只需要提供 [ApiConfig]（baseUrl / chatUrl / headers）、模型列表解析，
 * 以及按需覆盖 [applyThinking] 来关闭深度思考。
 */
abstract class OpenAICompatibleAI(
    final override var brandName: String,
    final override var company: String,
    protected val config: ApiConfig
) : IAI {

    override var modelSet: MutableSet<String> = mutableSetOf()

    override suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String?,
        enableThinking: Boolean
    ): String {
        val apiMessages = buildList {
            if (!systemPrompt.isNullOrBlank()) {
                add(ChatMessage.system(systemPrompt))
            }
            addAll(messages)
        }
        val request = ChatCompletionRequest(model = model, messages = apiMessages)
            .applyThinking(enableThinking)

        val response = WebRepository.instance
            .chat<ChatCompletionResponse>(config = config, body = request)
            .getOrElse { throw it }

        val content = response.choices.firstOrNull()?.message?.content
        return content?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("模型没有返回内容，请重试")
    }

    /**
     * 默认不处理思考开关；支持关闭思考的厂商覆盖此方法。
     */
    protected open fun ChatCompletionRequest.applyThinking(
        enableThinking: Boolean
    ): ChatCompletionRequest = this

    @Deprecated("改用 chat(messages, model, systemPrompt, enableThinking)")
    override suspend fun chat(message: String, model: String): String =
        chat(
            messages = listOf(ChatMessage.user(message)),
            model = model,
            systemPrompt = null,
            enableThinking = true
        )
}
