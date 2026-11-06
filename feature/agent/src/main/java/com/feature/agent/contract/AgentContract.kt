package com.feature.agent.contract

import com.core.ai.model.GatewayModel
import com.core.ai.model.ModelPrice
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
    val loaded: Boolean = false,
    /**
     * 当前选中的聚合平台模型的明细（带价格）。
     *
     * 官方厂商不是聚合平台，这里始终为空，UI 就不显示价格区块 ——
     * 官方厂商的计费在各自的开放平台后台，APK 里没有可靠价目。
     */
    val gatewayModels: List<GatewayModel> = emptyList(),

    /** 当前选中的是不是聚合平台。 */
    val isGateway: Boolean = false,

    /** 价格标注货币，聚合平台各有各的标价币种。 */
    val priceCurrency: String = ModelPrice.USD,

    /** 价格是否来自内置快照（而非平台的实时接口）。 */
    val priceIsSnapshot: Boolean = false,

    /** 快照抓取日期，实时价格时为 null。 */
    val priceSnapshotDate: String? = null,

    /** 正在手动刷新价格，刷新期间按钮要禁用，免得连点发出多次请求。 */
    val isRefreshingPrice: Boolean = false
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
