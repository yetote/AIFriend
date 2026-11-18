package com.feature.agent.contract

import com.core.ai.model.ChatMessage
import com.core.ai.model.Friend
import com.core.ai.model.GatewayModel
import com.core.ai.model.ModelPrice
import com.core.repository.settings.AIProviderCatalog
import com.feature.agent.model.Provider
import com.ui.agent.data.UIReadyTts

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
     * 加载完成前谁都没配，先摆出一排官方厂商会闪一下又消失。
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
    /**
     * 正在流式接收的那条回复。null 表示此刻没有在收。
     *
     * 它是一个**还没落库的临时消息**：正文和思考过程都在里面，每收到一段就更新一次。
     * 收完才写进 [friends] 里的消息列表并落库——如果边流边落库，
     * 一条回复会写几十次数据库，而且进程被杀时会留下半句话在历史里。
     */
    val streaming: ChatMessage? = null,
    /**
     * 建角色页正在试听音色。
     *
     * 和 [previewingVoiceId] 一起用：id 为 null 表示听的是「跟随默认音色」那条，
     * 光靠 id 分不出「没在听」和「在听默认音色」。
     */
    val previewingVoice: Boolean = false,
    val previewingVoiceId: String? = null,
    /** 试听失败的原因（Key 没配、音色没开通、余额不足…），换音色时清空。 */
    val previewError: String? = null,
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
    val isRefreshingPrice: Boolean = false,

    /**
     * 已配好凭证的语音服务，批量创建面板据此列选项。
     *
     * 只放「能用的」：没配 Key 的服务一旦参与随机，建出来的角色朗读全报错，
     * 而用户建的时候看不出来——设置页配没配，创建页没必要让人再记一遍。
     */
    val readyTts: List<UIReadyTts> = emptyList()
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
