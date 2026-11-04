package com.feature.friend.model

import com.core.ai.model.ChatMessage

/**
 * 一个 AI 好友：展示信息 + 角色设定 + 绑定模型 + 本地会话历史。
 * 目前只保存在内存里，进程被杀后会丢失（后续接 DataStore/Room）。
 */
data class Friend(
    val id: String,
    val name: String,
    val role: String,
    val prompt: String,
    val brand: String,
    val model: String,
    val messages: List<ChatMessage> = emptyList()
) {
    /**
     * 把「名字 + 角色 + 详细设定」拼成 system prompt，让模型保持人格。
     */
    val systemPrompt: String
        get() = buildString {
            append("你是「").append(name).append("」，")
            append(role.ifBlank { "一位乐于助人的 AI 好友" })
            append("。请始终以这个身份、用简体中文与用户对话，保持人格与语气一致。")
            if (prompt.isNotBlank()) {
                append("\n\n你的详细设定：\n")
                append(prompt)
            }
        }
}
