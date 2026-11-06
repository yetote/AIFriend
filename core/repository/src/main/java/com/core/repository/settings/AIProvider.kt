package com.core.repository.settings

/**
 * 聚合网关式的模型服务商。
 *
 * 这些平台本身不训练模型，只是把多家模型厂商的接口统一成 OpenAI 兼容协议，
 * 用一把 Key 就能调多个模型。对用户来说，不必为了凑齐几个 AI 好友
 * 去挨个官网注册、实名、绑卡 —— 这是引入这一层的原因。
 *
 * 目录里只放「一个 Key 覆盖多家模型」的平台。
 * DeepSeek、智谱、豆包这些官方接口在创建页已经是独立品牌了，
 * 这里再列一遍会让用户在两个地方看到同名厂商，反而分不清该选哪个。
 *
 * [id] 会作为持久化的键，改名会让用户已保存的 Key 失联，不要动。
 */
data class AIProvider(
    val id: String,
    /** 展示名。 */
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
    val fallbackModels: List<String> = emptyList()
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
 * 内置的聚合服务商目录。
 *
 * 只收录「个人开发者能注册」「OpenAI 兼容」「国内可直连」都满足的平台。
 * WorkBuddy 开放平台没有对外的对话接口，也不对个人开放，所以不在其中。
 */
object AIProviderCatalog {

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
        )
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
        )
    )

    val Custom = AIProvider(
        id = "custom",
        name = "自定义",
        description = "任意 OpenAI 兼容服务，自己填 Base URL（本地部署选这个）",
        baseUrl = "",
        consoleUrl = "",
        requiresKey = false,
        fallbackModels = emptyList()
    )

    /** 设置页展示顺序：先放推荐项，自定义放最后。 */
    val all: List<AIProvider> = listOf(SiliconFlow, OpenRouter, Custom)

    fun byId(id: String?): AIProvider? = all.firstOrNull { it.id == id }

    /** 默认选中硅基流动：一把 Key 覆盖最多模型，配置成本最低。 */
    fun default(): AIProvider = SiliconFlow
}
