package com.core.ai.model

/**
 * 一个 AI 好友：展示信息 + 角色设定 + 绑定的厂商/模型 + 本地会话历史。
 *
 * 放在 core:ai 而不是 feature:agent：它是「一次 AI 会话的配置」，
 * 聊天室、狼人杀这些不依赖 feature:agent 的模块也要读它（选成员、拼 system prompt）。
 * 依赖方向上 core:ai 已经依赖 core:repository，反过来把 Friend 放进
 * core:repository 会让后者必须引用 [ChatMessage]，形成环。
 */
data class Friend(
    val id: String,
    val name: String,
    val role: String,
    val prompt: String,
    val brand: String,
    val company: String,
    val model: String,
    val thinkingEnabled: Boolean = false,
    val avatarPath: String? = null,
    /** 朗读音色（MiniMax voice_id）。null = 跟随引擎默认音色，见 [VoiceCatalog]。 */
    val voiceId: String? = null,
    /** 朗读用哪套 TTS 配置（TTSProvider.id）。null = 跟随默认配置。 */
    val ttsConfigId: String? = null,
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
