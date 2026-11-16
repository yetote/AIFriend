package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.ChatCompletionResponse
import com.core.ai.model.ChatMessage
import com.core.ai.model.ResponseFormat
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
    /**
     * open：聚合网关的 Key 是运行时才有的，需要在请求前现取，
     * 由子类覆盖成动态读取，不能用构造时算出的快照。
     */
    protected open val config: ApiConfig
) : IAI {

    override var modelSet: MutableSet<String> = mutableSetOf()

    override suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String?,
        enableThinking: Boolean,
        jsonMode: Boolean
    ): String {
        val apiMessages = buildList {
            if (!systemPrompt.isNullOrBlank()) {
                add(ChatMessage.system(systemPrompt))
            }
            addAll(messages)
        }
        val request = ChatCompletionRequest(model = model, messages = apiMessages)
            .applyThinking(enableThinking)
            // 厂商不支持就当作没开，宁可让模型自由发挥，也不要一个 400 把整轮对话打断
            .applyJsonMode(jsonMode && supportsJsonMode)

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

    /**
     * 加上 `response_format`。子类如果要用别的字段名（个别厂商自己搞了一套），覆盖这里。
     */
    protected open fun ChatCompletionRequest.applyJsonMode(
        jsonMode: Boolean
    ): ChatCompletionRequest =
        if (jsonMode) copy(responseFormat = ResponseFormat(ResponseFormat.JSON_OBJECT)) else this

    @Deprecated("改用 chat(messages, model, systemPrompt, enableThinking)")
    override suspend fun chat(message: String, model: String): String =
        chat(
            messages = listOf(ChatMessage.user(message)),
            model = model,
            systemPrompt = null,
            enableThinking = true
        )
}
