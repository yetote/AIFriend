package com.ui.home

import com.ui.agent.data.UIPriceTone

/** 设置页里一个服务商的展示数据。
 *
 * ui 模块不直接依赖 core:repository 的实体，这里做一层薄 DTO，
 * 顺便把「有没有 Key」「能不能用」这类状态算好，页面只负责画。
 */
data class UIProviderOption(
    val id: String,
    val name: String,
    val description: String,
    /** 已保存的 Key 打码后的样子；空表示还没填。 */
    val maskedKey: String,
    val hasKey: Boolean,
    /** 自定义服务商需要用户自己填 Base URL。 */
    val editableBaseUrl: Boolean = false,
    val baseUrl: String = "",
    /** 聚合平台；页面据此把官方厂商和聚合平台分成两组。 */
    val isGateway: Boolean = false
)

/** 设置页模型列表里的一项：id + 可选价格。 */
data class UISettingsModel(
    val id: String,
    /** 已格式化的价格文本；null 表示这个平台不提供价格。 */
    val price: String? = null,
    /** 价格档位，用来着色。 */
    val tone: UIPriceTone? = null,
    /**
     * 超出常规上下文后更贵的档位说明，一行一档。
     *
     * 空表示不分档，列表不占额外高度。
     */
    val bands: List<String> = emptyList()
)

/** 设置页的整体状态。 */
data class UIModelSettingsState(
    val providers: List<UIProviderOption> = emptyList(),
    val selectedProviderId: String = "",
    val keyInput: String = "",
    val baseUrlInput: String = "",
    val models: List<String> = emptyList(),
    /** 带价格的模型列表，聚合平台才有内容。 */
    val pricedModels: List<UISettingsModel> = emptyList(),
    val selectedModel: String = "",
    val isLoadingModels: Boolean = false,
    val isVerifying: Boolean = false,
    /** 价格来自内置快照时提示数据日期。 */
    val priceIsSnapshot: Boolean = false,
    val priceSnapshotDate: String? = null,
    /** 正在抓价格表。 */
    val isRefreshingPrice: Boolean = false,
    /** 手动刷新的结果提示，成功后短暂显示「已更新到 X 个模型」。 */
    val priceRefreshHint: String? = null,
    /** 该服务商是否支持刷新价格（目前只有硅基流动有定价页可抓）。 */
    val canRefreshPrice: Boolean = false,
    /** 测试连接的结果，null 表示还没测。 */
    val verifyResult: UIVerifyResult? = null,
    val error: String? = null,
    /** 语音合成配置（独立于模型 Key）。 */
    val tts: UITTSState = UITTSState()
) {
    val selectedProvider: UIProviderOption?
        get() = providers.firstOrNull { it.id == selectedProviderId }

    /** 能不能点「测试并保存」。 */
    val canSave: Boolean
        get() {
            val provider = selectedProvider ?: return false
            if (keyInput.isBlank() && provider.hasKey.not()) {
                // 自定义服务商允许不填 Key（本地部署）
                if (!provider.editableBaseUrl) return false
            }
            if (provider.editableBaseUrl && baseUrlInput.isBlank() && provider.baseUrl.isBlank()) {
                return false
            }
            return !isVerifying && !isLoadingModels
        }
}

/** 测试连接的结果，区分成功和失败两种展示。 */
sealed interface UIVerifyResult {
    data class Success(val modelCount: Int) : UIVerifyResult
    data class Failure(val message: String) : UIVerifyResult
}

/** 设置页里一个 TTS 服务商的展示数据。和 [UIProviderOption] 同构，只是不含模型。 */
data class UITTSProviderOption(
    val id: String,
    val name: String,
    val description: String,
    val maskedKey: String,
    val hasKey: Boolean,
    val editableBaseUrl: Boolean = false,
    val baseUrl: String = "",
    /** 第一凭证的输入框标签（API Key / SecretId / Access Token…）。 */
    val keyLabel: String = "API Key",
    /** 第二凭证标签；null 表示这家不用（目前只有腾讯云要 SecretKey）。 */
    val secretLabel: String? = null,
    /** AppID 标签；null 表示这家不用（讯飞、火山要）。 */
    val appIdLabel: String? = null,
    /** 已保存的第二凭证 / AppID，留空表示沿用旧值。 */
    val hasSecret: Boolean = false,
    val hasAppId: Boolean = false,
)

/** 试听音色选项，映射到 core:ai 的 VoiceCatalog，UI 不直接依赖那层。 */
data class UIVoiceOption(val id: String?, val label: String)

/** TTS 试听结果：成功会真的响一声，失败要给出能照着改的原因。 */
sealed interface UITTSTestResult {
    data class Success(val voiceLabel: String) : UITTSTestResult
    data class Failure(val message: String) : UITTSTestResult
}

/** TTS 配置区块的状态，独立于模型 Key 的一套。 */
data class UITTSState(
    val providers: List<UITTSProviderOption> = emptyList(),
    val selectedId: String = "",
    val keyInput: String = "",
    val secretInput: String = "",
    val appIdInput: String = "",
    val baseUrlInput: String = "",
    val keyVisible: Boolean = false,
    /** 试听用的音色，null = 服务商默认。 */
    val voiceId: String? = null,
    val voices: List<UIVoiceOption> = emptyList(),
    /** 正在真实合成并播放，禁用按钮防止连点。 */
    val testing: Boolean = false,
    /** 试听结果；失败时带出可直接照着改的原因。 */
    val testResult: UITTSTestResult? = null,
    /** 保存结果提示（成功/失败各一句），null 不显示。 */
    val hint: String? = null,
) {
    val selected: UITTSProviderOption?
        get() = providers.firstOrNull { it.id == selectedId }

    val selectedVoiceLabel: String
        get() = voices.firstOrNull { it.id == voiceId }?.label ?: "默认音色"

    val canSave: Boolean
        get() {
            if (testing) return false
            val p = selected ?: return false
            if (keyInput.isBlank() && !p.hasKey) return false
            // 腾讯云要 SecretKey、讯飞火山要 AppID；输入框留空时允许沿用已保存的值。
            if (p.secretLabel != null && secretInput.isBlank() && !p.hasSecret) return false
            if (p.appIdLabel != null && appIdInput.isBlank() && !p.hasAppId) return false
            if (p.editableBaseUrl && baseUrlInput.isBlank() && p.baseUrl.isBlank()) return false
            return true
        }
}
