package com.core.repository.settings

/**
 * 一个语音合成服务商。
 *
 * 和 [AIProvider] 一样，Key 由用户在设置页自填、不随 APK 分发。
 * 但 TTS 各家的接口协议不统一（MiniMax 同步返回 hex 音频，别家多是异步任务），
 * 所以这里除了 baseUrl 还带一个 [protocol]，引擎据此选对应实现。
 * id 是持久化键，改名会让已存 Key 失联，不要动。
 */
data class TTSProvider(
    val id: String,
    /** 展示名。 */
    val name: String,
    /** 设置页副标题的一句话说明。 */
    val description: String,
    /** 接口根地址，引擎在后面拼各自的路径。 */
    val baseUrl: String,
    /** 申请 Key 的入口。 */
    val consoleUrl: String,
    /** 接口协议，决定引擎走哪套请求/响应解析。 */
    val protocol: TTSProtocol,
    /** baseUrl 是否由用户填写（自定义网关）。 */
    val editableBaseUrl: Boolean = false,
    /** 该协议的默认音色，角色没选音色时用它。 */
    val defaultVoice: String = "male-qn-qingse",
)

/** 已接通的语音协议。加一家 = 加一个枚举值 + 引擎里一个分支。 */
enum class TTSProtocol {
    /** MiniMax `POST {baseUrl}/t2a_v2`，非流式一次返回 JSON，data.audio 是 hex 编码 mp3。 */
    MINIMAX,
}

object TTSProviderCatalog {
    val MiniMax = TTSProvider(
        id = "minimax",
        name = "MiniMax",
        description = "speech 系列，自然度高，支持丰富中文音色",
        baseUrl = "https://api.minimaxi.com/v1",
        consoleUrl = "https://platform.minimaxi.com/subscribe/tts",
        protocol = TTSProtocol.MINIMAX,
        defaultVoice = "male-qn-qingse",
    )

    /** 兼容 MiniMax 协议的第三方网关：用户自填 baseUrl 和 Key。 */
    val Custom = TTSProvider(
        id = "custom",
        name = "自定义（MiniMax 兼容）",
        description = "填自建或网关地址，接口协议同 MiniMax t2a_v2",
        baseUrl = "",
        consoleUrl = "",
        protocol = TTSProtocol.MINIMAX,
        editableBaseUrl = true,
        defaultVoice = "male-qn-qingse",
    )

    val all: List<TTSProvider> = listOf(MiniMax, Custom)

    fun byId(id: String?): TTSProvider? = all.firstOrNull { it.id == id }

    /** 角色没指定配置时的回落。 */
    fun default(): TTSProvider = MiniMax
}
