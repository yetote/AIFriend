package com.core.repository.settings

import com.core.network.URL_DEEPSEEK
import com.core.network.URL_DOUBAO
import com.core.network.URL_GEMINI
import com.core.network.URL_KIMI
import com.core.network.URL_QIANWEN
import com.core.network.URL_SPARK
import com.core.network.URL_ZHIPU
/**
 * 一个模型服务商，可以是官方厂商，也可以是聚合平台。
 *
 * 两类都遵循 OpenAI 兼容协议，差别在覆盖面：
 * 聚合平台（硅基流动、OpenRouter）本身不训练模型，只把多家厂商的接口
 * 统一成一套协议，一把 Key 就能调几十个模型；
 * 官方厂商（DeepSeek、豆包、智谱…）只提供自家的模型家族。
 *
 * 无论哪一类，Key 都来自用户自己在设置页填写，不随 APK 分发。
 *
 * [id] 会作为持久化的键，改名会让用户已保存的 Key 失联，不要动。
 */
data class AIProvider(
    val id: String,
    /** 展示名。官方厂商这里必须和好友表里存的品牌名一致。 */
    val name: String,
    /** 一句话说明，用于设置页副标题。 */
    val description: String,
    /** OpenAI 兼容的 baseUrl，`GET {baseUrl}/models` 和 `POST {chatUrl}` 都基于它。 */
    val baseUrl: String,
    /** 申请 Key 的入口，设置页给个「去申请」跳转。 */
    val consoleUrl: String,
    /** Key 是否必填。本地部署（如 Ollama）可以留空。 */
    val requiresKey: Boolean = true,
    /**
     * 拉不到模型列表时的兜底选项。
     * 留空表示「必须联网拉到列表才能选」，避免用户选到一个不存在的模型名。
     */
    val fallbackModels: List<String> = emptyList(),
    /**
     * 是不是聚合平台。
     *
     * 创建页按这个字段把两类服务商分两组摆：混在一排 chip 里，
     * 用户会以为硅基流动也是个模型厂商。
     */
    val isGateway: Boolean = false,
    /**
     * 这个服务商有没有可用的 `GET /models`。
     *
     * 设置页的「测试并保存」靠它验证 Key，没有的话就只能保存下来，
     * 让模型名走兜底列表 —— 不能因此拒绝用户保存。
     */
    val hasModelList: Boolean = true,
    /** 归属公司，官方厂商才有；聚合平台留空，展示时回落到 [name]。 */
    val company: String = "",
    /**
     * 创建页要不要给「深度思考」开关。
     *
     * 星火和 Gemini 的接口不接受关思考的字段，给了开关只会报错，所以关掉。
     */
    val thinkingSwitchable: Boolean = true
) {
    /** 拼出 Chat Completions 的完整地址。 */
    val chatUrl: String get() = "$baseUrl/chat/completions"
}

/**
 * 校验用户手填的 Base URL。
 *
 * 只放行 https：Bearer Key 是随每次请求明文发出的，
 * 走 http 等于把 Key 交给同网段的任何人。
 * 本地部署（localhost / 10.x / 192.168.x）例外，
 * 那些地址根本出不了局域网，不会经过公网。
 */
fun validateBaseUrl(input: String): String? {
    val url = input.trim()
    if (url.isEmpty()) return "请填写 Base URL"
    if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
        return "地址要以 https:// 开头"
    }
    if (url.startsWith("http://", true) && !isLocalAddress(url)) {
        return "公网地址必须用 https，否则 API Key 会明文传输"
    }
    if (url.endsWith("/")) return "地址末尾不要带 /"
    return null
}

/** 本地 / 局域网的 http 地址是安全的：流量不出内网。 */
private fun isLocalAddress(url: String): Boolean {
    val host = url.removePrefix("http://").removePrefix("HTTPS://").removePrefix("https://")
        .substringBefore('/').substringBefore(':').lowercase()
    return host == "localhost" ||
        host == "127.0.0.1" ||
        host == "10.0.2.2" ||            // Android 模拟器访问宿主机
        host.startsWith("10.") ||
        host.startsWith("192.168.") ||
        Regex("""^172\.(1[6-9]|2\d|3[01])\.""").containsMatchIn(host)
}

/**
 * 内置服务商目录。
 *
 * 分两类：[official] 是模型厂商自己的开放平台，[gateways] 是一把 Key 调多家的聚合平台。
 * 两类放在同一个目录里，是因为它们对「凭证」这件事的需求完全一样 ——
 * 都要用户自己申请 Key、都存在本机 —— 只是展示时分组而已。
 *
 * 官方厂商的 [AIProvider.name] 必须和好友表里存的品牌名逐字一致：
 * 品牌名是数据库里的字符串，改了用户之前创建的好友就找不到厂商了。
 */
object AIProviderCatalog {

    // ---------- 官方厂商 ----------

    val Deepseek = AIProvider(
        id = "deepseek",
        name = "Deepseek",
        description = "深度求索官方接口，代码和推理见长",
        baseUrl = URL_DEEPSEEK,
        consoleUrl = "https://platform.deepseek.com/api_keys",
        fallbackModels = listOf("deepseek-flash", "deepseek-v4-pro"),
        company = "深度求索"
    )

    val Doubao = AIProvider(
        id = "doubao",
        name = "豆包",
        description = "字节跳动火山方舟，豆包系列模型",
        baseUrl = URL_DOUBAO,
        consoleUrl = "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey",
        fallbackModels = listOf("doubao-seed-evolving", "doubao-seed-2-1-pro-260915"),
        company = "字节跳动"
    )

    val Zhipu = AIProvider(
        id = "zhipu",
        name = "智谱清言",
        description = "北京智谱华章，GLM 系列模型",
        baseUrl = URL_ZHIPU,
        consoleUrl = "https://bigmodel.cn/usercenter/apikeys",
        fallbackModels = listOf("glm-5.3-flash", "glm-5.3", "glm-5.2"),
        company = "北京智谱华章"
    )

    val Kimi = AIProvider(
        id = "kimi",
        name = "Kimi",
        description = "月之暗面官方接口，长文本见长",
        baseUrl = URL_KIMI,
        consoleUrl = "https://platform.moonshot.cn/console/api-keys",
        fallbackModels = listOf("kimi-k3.1-pro", "kimi-k3", "kimi-k2.6"),
        company = "月之暗面"
    )

    val Qianwen = AIProvider(
        id = "qianwen",
        name = "通义千问",
        description = "阿里云百炼，Qwen 系列模型",
        baseUrl = URL_QIANWEN,
        consoleUrl = "https://bailian.console.aliyun.com/",
        fallbackModels = listOf("qwen3.8-flash", "qwen3.8-max", "qwen-plus"),
        company = "阿里巴巴"
    )

    val Spark = AIProvider(
        id = "spark",
        name = "星火",
        description = "科大讯飞星火，目前只接入了 x1",
        baseUrl = URL_SPARK,
        consoleUrl = "https://console.xfyun.cn/services/bmx1",
        fallbackModels = listOf("x1"),
        // 讯飞没有开放 /models（实测 404），模型名只能走兜底列表
        hasModelList = false,
        company = "科大讯飞",
        thinkingSwitchable = false
    )

    val Gemini = AIProvider(
        id = "gemini",
        name = "Gemini",
        description = "Google Gemini，走 OpenAI 兼容层",
        baseUrl = URL_GEMINI,
        consoleUrl = "https://aistudio.google.com/app/apikey",
        fallbackModels = listOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-flash-latest"),
        company = "Google",
        thinkingSwitchable = false
    )

    // ---------- 聚合平台 ----------

    val SiliconFlow = AIProvider(
        id = "siliconflow",
        name = "硅基流动",
        description = "一把 Key 打通 DeepSeek、Qwen、GLM、Kimi 等数十个模型",
        baseUrl = "https://api.siliconflow.cn/v1",
        consoleUrl = "https://cloud.siliconflow.cn/account/ak",
        fallbackModels = listOf(
            "deepseek-ai/DeepSeek-V3",
            "Qwen/Qwen2.5-72B-Instruct",
            "THUDM/glm-4-9b-chat"
        ),
        isGateway = true
    )

    val OpenRouter = AIProvider(
        id = "openrouter",
        name = "OpenRouter",
        description = "模型最全（GPT / Claude / Gemini 都在），按量付费",
        baseUrl = "https://openrouter.ai/api/v1",
        consoleUrl = "https://openrouter.ai/keys",
        fallbackModels = listOf(
            "openai/gpt-4o-mini",
            "google/gemini-flash-1.5"
        ),
        isGateway = true
    )

    val Custom = AIProvider(
        id = "custom",
        name = "自定义",
        description = "任意 OpenAI 兼容服务，自己填 Base URL（本地部署选这个）",
        baseUrl = "",
        consoleUrl = "",
        requiresKey = false,
        fallbackModels = emptyList(),
        isGateway = true
    )

    /** 官方厂商，创建页和设置页里排在聚合平台前面。 */
    val official: List<AIProvider> = listOf(
        Deepseek, Doubao, Zhipu, Kimi, Qianwen, Spark, Gemini
    )

    /** 一把 Key 调多家的平台，自定义放最后。 */
    val gateways: List<AIProvider> = listOf(SiliconFlow, OpenRouter, Custom)

    /**
     * 全部服务商。
     *
     * 注意：这个列表同时被「设置页展示」「凭证加载」「按名字找厂商」三处使用，
     * 任何一处想改顺序或筛选，都应该用下面的 [official] / [gateways]，
     * 而不是在这里动手脚 —— 凭证加载依赖它覆盖所有服务商，漏一个就丢一把 Key。
     */
    val all: List<AIProvider> = official + gateways

    fun byId(id: String?): AIProvider? = all.firstOrNull { it.id == id }

    /** 按品牌名（好友表里存的那个字符串）找服务商。 */
    fun byName(name: String?): AIProvider? = all.firstOrNull { it.name == name }

    /** 默认选中硅基流动：一把 Key 覆盖最多模型，配置成本最低。 */
    fun default(): AIProvider = SiliconFlow
}
