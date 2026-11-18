package com.core.ai.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * 一条对话消息，同时也是发送给 OpenAI 兼容接口的 messages 元素。
 * [role] 取值见 [SYSTEM]、[USER]、[ASSISTANT]。
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    /**
     * 这一条 AI 回复的思维链。
     *
     * 标 `@Transient`：它是**展示用的附带信息**，不是对话内容。不排除的话，
     * 每轮请求都会把历史里的思考过程重新发回给厂商——有的厂商会因此 400，
     * 就算不报错也白白多花 token。
     */
    @Transient
    val reasoning: String? = null
) {
    companion object {
        const val SYSTEM = "system"
        const val USER = "user"
        const val ASSISTANT = "assistant"

        fun system(content: String) = ChatMessage(SYSTEM, content)
        fun user(content: String) = ChatMessage(USER, content)
        fun assistant(content: String, reasoning: String? = null) =
            ChatMessage(ASSISTANT, content, reasoning)
    }
}
