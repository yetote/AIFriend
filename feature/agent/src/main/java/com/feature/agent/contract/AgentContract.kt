package com.feature.agent.contract

import com.feature.agent.model.Friend
import com.feature.agent.model.Provider

/**
 * 当前已接入对话链路的厂商。未迁移到多轮协议的厂商不列出来，避免用户创建出不能聊的好友。
 */
val DEFAULT_PROVIDERS = listOf(
    Provider(
        brand = "Deepseek",
        company = "深度求索",
        defaultModel = "deepseek-flash",
        fallbackModels = listOf("deepseek-flash", "deepseek-v4-pro")
    ),
    Provider(
        brand = "豆包",
        company = "字节跳动",
        defaultModel = "doubao-seed-evolving",
        fallbackModels = listOf("doubao-seed-evolving", "doubao-seed-2-1-pro-260915")
    ),
    Provider(
        brand = "智谱清言",
        company = "北京智谱华章",
        defaultModel = "glm-5.3-flash",
        fallbackModels = listOf("glm-5.3-flash", "glm-5.3", "glm-5.2")
    ),
    Provider(
        brand = "Kimi",
        company = "月之暗面",
        defaultModel = "kimi-k3.1-pro",
        fallbackModels = listOf("kimi-k3.1-pro", "kimi-k3", "kimi-k2.6")
    ),
    Provider(
        brand = "通义千问",
        company = "阿里巴巴",
        defaultModel = "qwen3.8-flash",
        fallbackModels = listOf("qwen3.8-flash", "qwen3.8-max", "qwen-plus")
    ),
    Provider(
        brand = "星火",
        company = "科大讯飞",
        defaultModel = "x1",
        fallbackModels = listOf("x1"),
        thinkingSwitchable = false
    ),
    Provider(
        brand = "Gemini",
        company = "Google",
        defaultModel = "gemini-3.8-flash",
        fallbackModels = listOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-flash-latest"),
        thinkingSwitchable = false
    )
)

data class AgentUiState(
    val friends: List<Friend> = emptyList(),
    val userAvatarPath: String? = null,
    val providers: List<Provider> = DEFAULT_PROVIDERS,
    val selectedBrand: String = DEFAULT_PROVIDERS.first().brand,
    val models: List<String> = emptyList(),
    val selectedModel: String = DEFAULT_PROVIDERS.first().defaultModel,
    val isLoadingModels: Boolean = false,
    val thinkingEnabled: Boolean = false,
    val activeFriendId: String? = null,
    val input: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false
) {
    val selectedProvider: Provider?
        get() = providers.firstOrNull { it.brand == selectedBrand }

    val activeFriend: Friend?
        get() = activeFriendId?.let { id -> friends.firstOrNull { it.id == id } }
}

sealed interface AgentScreen {
    data object Create : AgentScreen
    data class Chat(val friendId: String) : AgentScreen
    data class Edit(val friendId: String) : AgentScreen
}
