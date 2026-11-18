package com.core.repository.settings

/**
 * 一个语音合成服务商。
 *
 * 和 [AIProvider] 一样，凭证由用户在设置页自填、不随 APK 分发。
 * 各家鉴权差异很大：MiniMax 一把 Bearer Key，讯飞要 AppID+APIKey，
 * 火山要 AppID+Access Token，腾讯云要 SecretId+SecretKey（TC3 签名），
 * 阿里云只要一把 DashScope Key。所以这里用三个「凭证槽」描述要填什么，
 * 引擎按 [protocol] 选对应实现。
 *
 * id 是持久化键，改名会让已存凭证失联，不要动。
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
    /** 第一凭证的输入框标签（Key / Token / SecretId 等）。 */
    val keyLabel: String = "API Key",
    /** 第二凭证的输入框标签；null 表示这家不用。 */
    val secretLabel: String? = null,
    /** AppID 类字段的输入框标签；null 表示这家不用。 */
    val appIdLabel: String? = null,
    /** baseUrl 是否由用户填写（自定义网关）。 */
    val editableBaseUrl: Boolean = false,
    /** 该协议的默认音色，角色没选音色时用它。 */
    val defaultVoice: String = "",
)

/** 已接通的语音协议。加一家 = 加一个枚举值 + [com.core.tts.TTSProtocols] 里一个分支。 */
enum class TTSProtocol {
    /** MiniMax `POST {baseUrl}/t2a_v2`，非流式 JSON，data.audio 是 hex 编码 mp3。 */
    MINIMAX,
    /** 科大讯飞 `POST {baseUrl}/v2/tts`，MD5 签名 + 表单，响应是二进制音频。 */
    IFLYTEK,
    /** 火山引擎 `POST {baseUrl}/api/v1/tts`，Bearer;token + JSON，data 是 base64。 */
    VOLCENGINE,
    /** 阿里云百炼 `POST {baseUrl}/api/v1/services/audio/tts/SpeechSynthesizer`，返回音频 URL。 */
    ALIBABA,
    /** 腾讯云 `POST https://tts.tencentcloudapi.com/`，TC3-HMAC-SHA256 签名，Response.Audio 是 base64。 */
    TENCENT,
}

object TTSProviderCatalog {
    val MiniMax = TTSProvider(
        id = "minimax",
        name = "MiniMax",
        description = "speech 系列，自然度高，中文音色丰富",
        baseUrl = "https://api.minimaxi.com/v1",
        consoleUrl = "https://platform.minimaxi.com/subscribe/tts",
        protocol = TTSProtocol.MINIMAX,
        defaultVoice = "male-qn-qingse",
    )

    val Iflytek = TTSProvider(
        id = "iflytek",
        name = "科大讯飞",
        description = "老牌中文语音，发音清晰稳定",
        baseUrl = "https://tts.xfyun.cn",
        consoleUrl = "https://console.xfyun.cn/services/tts",
        protocol = TTSProtocol.IFLYTEK,
        keyLabel = "API Key",
        appIdLabel = "App ID",
        defaultVoice = "xiaoyan",
    )

    val Volcengine = TTSProvider(
        id = "volcengine",
        name = "火山引擎（豆包）",
        description = "豆包语音，音色自然，需 AppID + Access Token",
        baseUrl = "https://openspeech.bytedance.com",
        consoleUrl = "https://console.volcengine.com/speech/app",
        protocol = TTSProtocol.VOLCENGINE,
        keyLabel = "Access Token",
        appIdLabel = "App ID",
        defaultVoice = "zh_female_qingxin",
    )

    val Alibaba = TTSProvider(
        id = "alibaba",
        name = "阿里云百炼",
        description = "CosyVoice 系列，声音克隆能力强",
        baseUrl = "https://dashscope.aliyuncs.com",
        consoleUrl = "https://bailian.console.aliyun.com/",
        protocol = TTSProtocol.ALIBABA,
        keyLabel = "DashScope API Key",
        defaultVoice = "longanyang",
    )

    /** 腾讯混元本身没有独立公开 TTS 接口，语音走腾讯云 TTS（同一套 SecretId/SecretKey）。 */
    val Tencent = TTSProvider(
        id = "tencent",
        name = "腾讯云（混元语音）",
        description = "腾讯云 TextToVoice，混元生态同用此凭证",
        baseUrl = "https://tts.tencentcloudapi.com",
        consoleUrl = "https://console.cloud.tencent.com/tts",
        protocol = TTSProtocol.TENCENT,
        keyLabel = "SecretId",
        secretLabel = "SecretKey",
        defaultVoice = "101001",
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

    val all: List<TTSProvider> = listOf(MiniMax, Iflytek, Volcengine, Alibaba, Tencent, Custom)

    fun byId(id: String?): TTSProvider? = all.firstOrNull { it.id == id }

    /** 角色没指定配置时的回落。 */
    fun default(): TTSProvider = MiniMax
}
