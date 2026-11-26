package com.core.repository.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/**
 * 识别到的中转站信息。
 */
data class DetectedRelayStation(
    /** 已清洗、可直接用作自定义接口展示名的名称。 */
    val name: String,
    val source: Source,
) {
    enum class Source {
        /** 已知商业聚合站或官方兼容域名直判。 */
        KNOWN_DOMAIN,
        /** One API / New API / One Hub 等 `/api/status` 返回的站点名。 */
        STATUS_API,
        /** 本地推理服务（Ollama / LM Studio）特征探测。 */
        SERVICE_PROBE,
        /** 站点首页 `<title>` 兜底提取。 */
        HTML_TITLE,
        /** 无法探测站点名时从域名提炼的可读名称。 */
        HOST_FALLBACK,
    }
}

/**
 * 接入自定义 OpenAI 兼容接口时，按「域名直判 → `/api/status` → 本地服务特征 → 首页标题 → 域名提炼」
 * 逐级识别中转站名称。
 *
 * 安全原则：探测请求**不携带**用户的 API Key，且只访问用户填入的同一个 origin；
 * 任何探测失败都静默跳过，绝不阻塞正常的连接验证与保存。
 */
object RelayStationDetector {

    private const val MAX_NAME_LENGTH = 24
    private const val PROBE_TIMEOUT_MS = 2_500L

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 尽力识别中转站名称；完全无法识别时返回 null。
     *
     * @param fetchText 短超时 GET 原始文本的回调，便于单测注入桩实现。
     */
    suspend fun detect(
        baseUrl: String,
        fetchText: suspend (url: String, timeoutMs: Long) -> Result<String>,
    ): DetectedRelayStation? {
        val parsed = parseEndpoint(baseUrl) ?: return null

        // 1. 已知商业聚合站 / 官方兼容域名：零网络开销、最准确
        detectByKnownDomain(parsed.host)?.let { name ->
            return DetectedRelayStation(name, DetectedRelayStation.Source.KNOWN_DOMAIN)
        }

        // 2. One API / New API / One Hub / Done Hub / VoAPI 等中转站通用状态接口
        for (statusUrl in statusCandidateUrls(parsed)) {
            val body = fetchText(statusUrl, PROBE_TIMEOUT_MS).getOrNull() ?: continue
            parseStatusJsonName(body)?.let { name ->
                return DetectedRelayStation(name, DetectedRelayStation.Source.STATUS_API)
            }
        }

        // 3. 本地部署推理服务（Ollama / LM Studio）特征探测
        if (parsed.isLocal) {
            if (parsed.port == 11434) {
                val versionBody = fetchText("${parsed.origin}/api/version", PROBE_TIMEOUT_MS).getOrNull()
                if (versionBody != null && looksLikeOllamaVersion(versionBody)) {
                    return DetectedRelayStation("Ollama", DetectedRelayStation.Source.SERVICE_PROBE)
                }
            }
            if (parsed.port == 1234) {
                return DetectedRelayStation("LM Studio", DetectedRelayStation.Source.SERVICE_PROBE)
            }
        }

        // 4. 站点首页文本：同时识别 Ollama 默认页 ("Ollama is running") 与中转站网页 <title>
        val rootBody = fetchText("${parsed.origin}/", PROBE_TIMEOUT_MS).getOrNull()
        if (rootBody != null) {
            if (rootBody.trim().equals("Ollama is running", ignoreCase = true)) {
                return DetectedRelayStation("Ollama", DetectedRelayStation.Source.SERVICE_PROBE)
            }
            parseHtmlTitleName(rootBody)?.let { name ->
                return DetectedRelayStation(name, DetectedRelayStation.Source.HTML_TITLE)
            }
        }

        // 本地非默认端口的 Ollama 补查一次 /api/version
        if (parsed.isLocal && parsed.port != 11434) {
            val versionBody = fetchText("${parsed.origin}/api/version", PROBE_TIMEOUT_MS).getOrNull()
            if (versionBody != null && looksLikeOllamaVersion(versionBody)) {
                return DetectedRelayStation("Ollama", DetectedRelayStation.Source.SERVICE_PROBE)
            }
        }

        // 5. 域名兜底：剥离无意义的 api./openai. 前缀，至少比「自定义 API」好认
        extractHostLabel(parsed.host)?.let { name ->
            return DetectedRelayStation(name, DetectedRelayStation.Source.HOST_FALLBACK)
        }

        return null
    }

    /**
     * 为自动识别出的名称去重，避免与内置厂商或已有自定义接口撞名。
     *
     * - 与内置厂商同名（如用户自建了一个硅基流动/DeepSeek 反代）：优先加「(自定义)」后缀，
     *   防止按品牌名查找时误命中内置官方实现；
     * - 与其他自定义接口同名：依次追加 ` 2`、` 3`…
     */
    fun uniqueName(
        desired: String,
        occupiedNames: Set<String>,
        builtinNames: Set<String> = emptySet(),
    ): String {
        val base = sanitizeName(desired) ?: return CustomNameDefaults.DEFAULT_NAME
        if (base !in occupiedNames && base !in builtinNames) return base

        if (base in builtinNames) {
            val tagged = "$base (自定义)"
            if (tagged !in occupiedNames && tagged !in builtinNames) return tagged
        }

        var index = 2
        while (true) {
            val candidate = "$base $index"
            if (candidate !in occupiedNames && candidate !in builtinNames) return candidate
            index++
        }
    }

    internal fun detectByKnownDomain(host: String): String? {
        val normalized = host.trim().lowercase().trimEnd('.')
        if (normalized.isEmpty()) return null
        return KNOWN_DOMAIN_NAMES.firstOrNull { (suffix, _) ->
            normalized == suffix || normalized.endsWith(".$suffix")
        }?.second
    }

    /**
     * 解析 One API / New API / One Hub / Done Hub / VoAPI 等 `/api/status` 返回的 JSON。
     *
     * 标准格式：`{"success":true,"data":{"system_name":"XX 中转站",...}}`，
     * 同时兼容顶层 `system_name` / `site_name` / `title` / `name`。
     */
    internal fun parseStatusJsonName(body: String): String? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) return null
        val root = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: return null

        // 显式返回 success=false 时忽略（往往是错误提示页）
        val success = root["success"]?.jsonPrimitive?.booleanOrNull
        if (success == false) return null

        val dataObj = (root["data"] as? JsonObject)
        val candidates = listOfNotNull(
            dataObj?.stringField("system_name"),
            dataObj?.stringField("systemName"),
            dataObj?.stringField("site_name"),
            dataObj?.stringField("siteName"),
            dataObj?.stringField("title"),
            dataObj?.stringField("name"),
            root.stringField("system_name"),
            root.stringField("systemName"),
            root.stringField("site_name"),
            root.stringField("siteName"),
        )
        return candidates.firstNotNullOfOrNull { sanitizeName(it) }
    }

    /** 从站点首页 HTML 的 `<title>` 中提炼可读站点名，过滤掉 404/WAF/默认脚手架标题。 */
    internal fun parseHtmlTitleName(html: String): String? {
        // 只扫前 16KB：`<title>` 总在 `<head>` 里，避免对超大页面跑正则
        val head = if (html.length > 16_384) html.substring(0, 16_384) else html
        val match = TITLE_REGEX.find(head) ?: return null
        val decoded = decodeHtmlEntities(match.groupValues[1]).trim()
        if (decoded.isEmpty()) return null

        val primary = splitBrandFromTitle(decoded)
        val cleaned = sanitizeName(primary) ?: return null
        if (isJunkTitle(cleaned)) return null
        return cleaned
    }

    /** 从自定义域名提炼可读短名（如 `api.xingyun.top` -> `xingyun.top`），IP 与 localhost 返回 null。 */
    internal fun extractHostLabel(host: String): String? {
        val lower = host.trim().lowercase().trimEnd('.')
        if (lower.isEmpty() || lower == "localhost" || isIpAddress(lower)) return null

        var labels = lower.split('.').filter { it.isNotEmpty() }
        if (labels.size < 2) return null

        while (labels.size > 2 && labels.first() in STRIP_SUBDOMAINS) {
            labels = labels.drop(1)
        }
        return sanitizeName(labels.joinToString("."))
    }

    internal fun sanitizeName(raw: String): String? {
        val cleaned = raw
            .replace(HTML_TAG_REGEX, " ")
            .replace(CONTROL_CHAR_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
            .trim('-', '|', '·', '—', '–', ':')
            .trim()
        if (cleaned.isEmpty()) return null
        return cleaned.take(MAX_NAME_LENGTH).trim()
    }

    private data class EndpointParts(
        val origin: String,
        val host: String,
        val port: Int,
        val pathPrefix: String,
        val isLocal: Boolean,
    )

    private fun parseEndpoint(baseUrl: String): EndpointParts? {
        val uri = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port
        val defaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
        val portSuffix = if (port > 0 && !defaultPort) ":$port" else ""
        val origin = "$scheme://$host$portSuffix"

        val rawPath = (uri.rawPath ?: "").trimEnd('/')
        val prefix = stripApiPathSuffix(rawPath)
        return EndpointParts(
            origin = origin,
            host = host,
            port = port,
            pathPrefix = prefix,
            isLocal = isLocalHost(host),
        )
    }

    private fun statusCandidateUrls(parts: EndpointParts): List<String> = buildList {
        if (parts.pathPrefix.isNotEmpty()) {
            add("${parts.origin}${parts.pathPrefix}/api/status")
        }
        add("${parts.origin}/api/status")
    }.distinct()

    private fun stripApiPathSuffix(path: String): String {
        val lower = path.lowercase()
        val suffixes = listOf("/v1beta/openai", "/openai/v1", "/api/v1", "/v1beta", "/v1")
        for (suffix in suffixes) {
            if (lower.endsWith(suffix)) {
                return path.dropLast(suffix.length).trimEnd('/')
            }
        }
        return path
    }

    private fun looksLikeOllamaVersion(body: String): Boolean {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) return false
        val obj = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: return false
        val version = obj.stringField("version") ?: return false
        return version.isNotBlank() && obj.size <= 3
    }

    private fun splitBrandFromTitle(title: String): String {
        val parts = TITLE_SPLIT_REGEX.split(title).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size <= 1) return title
        val first = parts.first()
        return if (first.length >= 2 && !isJunkTitle(first)) first else parts.drop(1).firstOrNull() ?: title
    }

    private fun isJunkTitle(text: String): Boolean {
        val lower = text.lowercase()
        if (lower in JUNK_EXACT_TITLES) return true
        return JUNK_SUBSTRING_TITLES.any { lower.contains(it) }
    }

    private fun decodeHtmlEntities(input: String): String = input
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")

    private fun JsonObject.stringField(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun isLocalHost(host: String): Boolean =
        host == "localhost" ||
            host == "127.0.0.1" ||
            host == "10.0.2.2" ||
            host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            PRIVATE_172_REGEX.containsMatchIn(host)

    private fun isIpAddress(host: String): Boolean =
        IPV4_REGEX.matches(host) || host.contains(':')

    private val TITLE_REGEX = Regex(
        """<title[^>]*>(.*?)</title>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val TITLE_SPLIT_REGEX = Regex("""\s*(?:\s[-—–|]\s|\s[::·]\s)\s*""")
    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
    private val CONTROL_CHAR_REGEX = Regex("""[\p{Cntrl}]+""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")
    private val IPV4_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val PRIVATE_172_REGEX = Regex("""^172\.(1[6-9]|2\d|3[01])\.""")

    private val STRIP_SUBDOMAINS = setOf(
        "api", "openai", "ai", "llm", "gateway", "proxy", "v1", "www", "open", "relay", "chat",
    )

    private val JUNK_EXACT_TITLES = setOf(
        "404", "403", "401", "500", "502", "503", "504",
        "error", "home", "index", "login", "sign in", "signin", "api", "untitled",
        "document", "loading", "console", "dashboard", "首页", "登录", "控制台",
    )

    private val JUNK_SUBSTRING_TITLES = listOf(
        "not found", "forbidden", "bad gateway", "service unavailable",
        "just a moment", "attention required", "access denied",
        "welcome to nginx", "index of /", "react app", "vite app", "vue app",
        "cloudflare", "404 ", "403 ", "502 ", "503 ",
    )

    /** 常见商业聚合站、知名中转站与官方兼容域名表（按后缀匹配）。 */
    private val KNOWN_DOMAIN_NAMES: List<Pair<String, String>> = listOf(
        "siliconflow.cn" to "硅基流动",
        "siliconflow.com" to "硅基流动",
        "openrouter.ai" to "OpenRouter",
        "aihubmix.com" to "AiHubMix",
        "openai.com" to "OpenAI",
        "anthropic.com" to "Anthropic",
        "deepseek.com" to "DeepSeek",
        "generativelanguage.googleapis.com" to "Gemini",
        "dashscope.aliyuncs.com" to "阿里云百炼",
        "dashscope-intl.aliyuncs.com" to "阿里云百炼",
        "volces.com" to "火山方舟",
        "bigmodel.cn" to "智谱开放平台",
        "moonshot.cn" to "Moonshot",
        "moonshot.ai" to "Moonshot",
        "minimaxi.com" to "MiniMax",
        "minimax.io" to "MiniMax",
        "xiaomimimo.com" to "小米 MiMo",
        "groq.com" to "Groq",
        "together.xyz" to "Together AI",
        "fireworks.ai" to "Fireworks",
        "x.ai" to "xAI",
        "mistral.ai" to "Mistral",
        "perplexity.ai" to "Perplexity",
        "cerebras.ai" to "Cerebras",
        "models.inference.ai.azure.com" to "GitHub Models",
        "models.github.ai" to "GitHub Models",
        "302.ai" to "302.AI",
        "302ai.cn" to "302.AI",
        "ohmygpt.com" to "OhMyGPT",
        "closeai-asia.com" to "CloseAI",
        "openai-proxy.org" to "CloseAI",
        "chatanywhere.tech" to "ChatAnywhere",
        "chatanywhere.com.cn" to "ChatAnywhere",
        "gptapi.us" to "GPTAPI",
        "dmxapi.cn" to "DMXAPI",
        "dmxapi.com" to "DMXAPI",
        "poloai.top" to "PoloAI",
        "vveai.com" to "VVEAI",
        "bltcy.ai" to "柏拉图",
        "bltcy.top" to "柏拉图",
        "aigcbest.top" to "AIGCBest",
        "apiyi.com" to "API易",
        "deerapi.com" to "DeerAPI",
        "xiaoai.plus" to "小爱中转",
        "gptsapi.net" to "WildCard",
        "vectorengine.ai" to "VectorEngine",
        "poixe.com" to "Poixe",
        "sophnet.com" to "SophNet",
        "modelscope.cn" to "魔搭 ModelScope",
        "ppinfra.com" to "派欧算力",
        "stepfun.com" to "阶跃星辰",
        "baichuan-ai.com" to "百川智能",
        "lingyiwanwu.com" to "零一万物",
        "hunyuan.cloud.tencent.com" to "腾讯云",
        "lkeap.cloud.tencent.com" to "腾讯云",
        "qianfan.baidubce.com" to "百度千帆",
        "integrate.api.nvidia.com" to "NVIDIA NIM",
        "novita.ai" to "Novita AI",
        "hyperbolic.xyz" to "Hyperbolic",
        "deepinfra.com" to "DeepInfra",
        "chutes.ai" to "Chutes",
        "nebius.ai" to "Nebius",
        "studio.nebius.com" to "Nebius",
        "sambanova.ai" to "SambaNova",
    )
}

/** 自定义接口默认占位名判断。 */
object CustomNameDefaults {
    const val DEFAULT_NAME = "自定义 API"
    private val NUMBERED_DEFAULT_REGEX = Regex("""^自定义 API \d+$""")

    /** 仍处于默认占位名（未由用户手动命名）时返回 true。 */
    fun isDefaultName(name: String?): Boolean {
        val trimmed = name?.trim().orEmpty()
        return trimmed.isEmpty() ||
            trimmed == DEFAULT_NAME ||
            trimmed == "自定义" ||
            NUMBERED_DEFAULT_REGEX.matches(trimmed)
    }
}
