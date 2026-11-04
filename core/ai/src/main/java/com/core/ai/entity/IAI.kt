package com.core.ai.entity

import com.core.ai.model.ChatMessage

interface IAI {
    suspend fun requestModel()

    var modelSet: MutableSet<String>

    var brandName: String
    var company: String

    /**
     * 多轮对话：传入完整历史（不含 system），由实现方按厂商协议补 system 并转换为请求体。
     * 未接入的厂商默认抛出异常，避免静默返回错误内容。
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        systemPrompt: String? = null
    ): String = throw UnsupportedOperationException("「$brandName」暂未接入对话，请先选择 DeepSeek")

    @Deprecated("改用 chat(messages, model, systemPrompt)")
    suspend fun chat(message: String, model: String): String
}
