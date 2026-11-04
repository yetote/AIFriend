package com.core.ai.model

import kotlinx.serialization.Serializable

/**
 * 一条对话消息，同时也是发送给 OpenAI 兼容接口的 messages 元素。
 * [role] 取值见 [SYSTEM]、[USER]、[ASSISTANT]。
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String
) {
    companion object {
        const val SYSTEM = "system"
        const val USER = "user"
        const val ASSISTANT = "assistant"

        fun system(content: String) = ChatMessage(SYSTEM, content)
        fun user(content: String) = ChatMessage(USER, content)
        fun assistant(content: String) = ChatMessage(ASSISTANT, content)
    }
}
