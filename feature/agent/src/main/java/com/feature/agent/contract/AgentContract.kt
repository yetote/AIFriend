package com.feature.agent.contract

import com.core.ai.model.GatewayModel
import com.core.ai.model.ModelPrice
import com.core.repository.settings.AIProviderCatalog
import com.feature.agent.model.Friend
import com.feature.agent.model.Provider

/**
 * 官方厂商的默认形态，直接从服务商目录派生。
 *
 * 以前这份列表是手写的第二份模型名，和目录里那份迟早对不上：
 * 目录是凭证加载的依据，这里只是创建页的初值，两处各写一遍必然漂移。
 * 现在只保留一处真源 [AIProviderCatalog.official]。
 *
 * 注意这只是「初值」：真正可选的厂商由 AgentViewModel 按「用户是否配了 Key」筛。
 */
val DEFAULT_PROVIDERS: List<Provider> = AIProviderCatalog.official.map { provider ->
    Provider(
        brand = provider.name,
        company = provider.company.ifBlank { provider.name },
        defaultModel = provider.fallbackModels.firstOrNull().orEmpty(),
        fallbackModels = provider.fallbackModels,
        thinkingSwitchable = provider.thinkingSwitchable,
        isGateway = false
    )
}

data class AgentUiState(
    val friends: List<Friend> = emptyList(),
    val userAvatarPath: String? = null,
    /**
     * 可选服务商。
     *
     * 初值是空的，不是 [DEFAULT_PROVIDERS]：Key 由用户自己填，
     * 加载完成前谁都没配，先摆出 7 个官方厂商会闪一下又消失。
     */
    val providers: List<Provider> = emptyList(),
    val selectedBrand: String = "",
    val models: List<String> = emptyList(),
    val selectedModel: String = "",
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

    /**
     * 智能体信息页，从聊天页点头像进入。
     *
     * 不直接弹相册：先给一层「他是什么角色、用的什么模型」的信息页，
     * 换头像在那页里再单独点一次。
     */
    data class Info(val friendId: String) : AgentScreen
}
