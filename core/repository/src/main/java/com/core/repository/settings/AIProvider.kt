package com.core.repository.settings

import com.core.network.URL_DEEPSEEK
import com.core.network.URL_DOUBAO
import com.core.network.URL_GEMINI
import com.core.network.URL_HUNYUAN
import com.core.network.URL_KIMI
import com.core.network.URL_MINIMAX
import com.core.network.URL_MIMO
import com.core.network.URL_QIANWEN
import com.core.network.URL_SPARK
import com.core.network.URL_ZHIPU
import com.core.repository.balance.BalanceParserKind
import com.core.repository.balance.BalanceMethod
import com.core.repository.balance.ProviderBalanceSpec
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
     * 自家模型的 ID 前缀，用来把「平台代理来的别家模型」挑出去。
     *
     * 方舟、百炼、TokenHub 的 `GET /models` 会把代理来的别家模型一起列出来
     * （方舟上就有 Qwen、DeepSeek），但那些模型要在控制台单独「开通」，
     * 没开通调用直接 404。批量建角色是从列表里随机挑模型的，挑中一个没开通的，
     * 就是「建完当天说不了话」，而角色在列表里看着完全正常。
     * 自家模型通常随账号自动开通，所以随机池只取这些前缀；一家都匹配不上才退回全池。
     *
     * 聚合平台留空：它们本来就没有「自家模型」，全部可选。
     */
    val ownModelPrefixes: List<String> = emptyList(),
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
    val thinkingSwitchable: Boolean = true,
    /**
     * 历史展示名。只有用户自建的接口在改名后会有值。
     *
     * 老好友、老狼人杀存档里存的是当时的品牌名字符串。改名后靠它继续找到
     * 同一个服务商，否则那些好友会变成「暂不支持的厂商」。
     */
    val aliases: List<String> = emptyList(),
    /**
     * 这个服务商的余量查询方式；null 表示这家不提供。
     *
     * 八成厂商直接开了 HTTP 接口，用用户已经填在设置页的那把 Key 就能查余额，
     * 不用真的做网页登录（OAuth 要企业资质、要审核，个人开发者走不通）。
     */
    val balance: ProviderBalanceSpec? = null,
    /**
     * 第二对凭证的标签；null 表示这家不需要。
     *
     * 目前只有火山方舟要：它的管控面接口（查模型开通状态、查推理限额）
     * 不认聊天用的 API Key，只认 Access Key ID + Secret Access Key。
     */
    val accessKeyLabel: String? = null,
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
        company = "深度求索",
        ownModelPrefixes = listOf("deepseek"),
        balance = ProviderBalanceSpec(
            url = "https://api.deepseek.com/user/balance",
            parser = BalanceParserKind.DEEPSEEK,
        ),
    )

    val Doubao = AIProvider(
        id = "doubao",
        name = "豆包",
        description = "字节跳动火山方舟，豆包系列模型",
        baseUrl = URL_DOUBAO,
        consoleUrl = "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey",
        fallbackModels = listOf("doubao-seed-evolving", "doubao-seed-2-1-pro-260915"),
        company = "字节跳动",
        ownModelPrefixes = listOf("doubao"),
        // 方舟的额度接口要 Access Key 签名，聊天用的 API Key 查不了。
        // 这里查的是「模型开通状态」——方舟的模型要在控制台单独开通，
        // 没开通调用直接 404，而 /models 会把没开通的也列出来。
        balance = ProviderBalanceSpec(
            url = "https://ark.cn-beijing.volcengineapi.com/",
            method = BalanceMethod.SIGNED_POST,
            parser = BalanceParserKind.VOLCENGINE,
            action = "ListModelActivations",
        ),
        accessKeyLabel = "Access Key ID",
    )

    val Zhipu = AIProvider(
        id = "zhipu",
        name = "智谱清言",
        description = "智谱 GLM 系列，GLM-4.7-Flash 免费不限量",
        baseUrl = URL_ZHIPU,
        consoleUrl = "https://bigmodel.cn/usercenter/apikeys",
        // glm-4.7-flash 是官方定价页上标「输入免费/输出免费」的那一档（200K 上下文），
        // 排最后是因为它能干的事最少；但在拉不到列表时，它是唯一不用充钱就能跑的选择
        fallbackModels = listOf("glm-5.3-flash", "glm-5.3", "glm-5.2", "glm-4.7-flash"),
        company = "北京智谱华章",
        ownModelPrefixes = listOf("glm"),
        // 智谱的额度接口认两种写法：官方文档说 Bearer，实测直接传原始 Key 也行，
        // 加了 Bearer 反而会鉴权失败。两种都试一遍，别赌。
        balance = ProviderBalanceSpec(
            url = "https://open.bigmodel.cn/api/monitor/usage/quota/limit",
            parser = BalanceParserKind.ZHIPU,
            authHeaderValues = listOf("{key}", "Bearer {key}"),
        ),
    )

    val Kimi = AIProvider(
        id = "kimi",
        name = "Kimi",
        description = "月之暗面官方接口，长文本见长",
        baseUrl = URL_KIMI,
        consoleUrl = "https://platform.moonshot.cn/console/api-keys",
        fallbackModels = listOf("kimi-k3.1-pro", "kimi-k3", "kimi-k2.6"),
        company = "月之暗面",
        ownModelPrefixes = listOf("kimi", "moonshot"),
        balance = ProviderBalanceSpec(
            url = "https://api.moonshot.cn/v1/users/me/balance",
            parser = BalanceParserKind.MOONSHOT,
        ),
    )

    val Qianwen = AIProvider(
        id = "qianwen",
        name = "通义千问",
        description = "阿里云百炼，Qwen 系列模型",
        baseUrl = URL_QIANWEN,
        consoleUrl = "https://bailian.console.aliyun.com/",
        fallbackModels = listOf("qwen3.8-flash", "qwen3.8-max", "qwen-plus"),
        company = "阿里巴巴",
        ownModelPrefixes = listOf("qwen", "qwq"),
        // 故意不配 balance：百炼没有公开的「剩余余额」接口，只有用量/账单查询。
        // 配一个半真半假的地址过去，查出来是 404，比明说「没这个接口」更让人困惑。
    )

    val Hunyuan = AIProvider(
        id = "hunyuan",
        name = "混元",
        description = "腾讯混元 Hy4，1M 上下文，带视觉和电脑操作",
        baseUrl = URL_HUNYUAN,
        consoleUrl = "https://console.cloud.tencent.com/tokenhub/apikey",
        // Hy4 是 2026-11 的旗舰；Hy3 便宜、输出上限还更高（128K），留着当日常档
        fallbackModels = listOf("hy4", "hy3"),
        company = "腾讯",
        ownModelPrefixes = listOf("hy"),
    )

    val MiniMax = AIProvider(
        id = "minimax",
        name = "MiniMax",
        description = "MiniMax 官方接口，多轮对话与角色扮演有专门优化",
        baseUrl = URL_MINIMAX,
        consoleUrl = "https://platform.minimaxi.com/user-center/basic-information/interface-key",
        // M2-her 是专攻角色扮演和多轮对话的那一支，对好友场景比通用旗舰更对口
        fallbackModels = listOf("MiniMax-M3.1-Flash", "MiniMax-M3", "M2-her"),
        company = "MiniMax",
        // M3.1-Flash 恒定思考，发 thinking:disabled 会被 400 拒掉，所以不给开关
        thinkingSwitchable = false,
        ownModelPrefixes = listOf("minimax", "m2"),
        // Token Plan 的额度接口。这家没公开文档，路径是从控制台产物里摸出来的，
        // 随时可能变，所以解析层对字段名做了多候选。
        balance = ProviderBalanceSpec(
            url = "https://api.minimaxi.com/v1/token_plan/remains",
            parser = BalanceParserKind.MINIMAX,
        ),
    )

    val MiMo = AIProvider(
        id = "mimo",
        name = "小米",
        description = "小米 MiMo，全模态、低成本，多角色并发首选",
        baseUrl = URL_MIMO,
        consoleUrl = "https://platform.xiaomimimo.com/",
        fallbackModels = listOf("mimo-v2.6-flash", "mimo-v2.6-pro"),
        company = "小米",
        ownModelPrefixes = listOf("mimo"),
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
        thinkingSwitchable = false,
        ownModelPrefixes = listOf("x1", "spark", "general"),
    )

    val Gemini = AIProvider(
        id = "gemini",
        name = "Gemini",
        description = "Google Gemini，走 OpenAI 兼容层",
        baseUrl = URL_GEMINI,
        consoleUrl = "https://aistudio.google.com/app/apikey",
        fallbackModels = listOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-flash-latest"),
        company = "Google",
        thinkingSwitchable = false,
        ownModelPrefixes = listOf("gemini"),
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
        isGateway = true,
        balance = ProviderBalanceSpec(
            url = "https://api.siliconflow.cn/v1/user/info",
            parser = BalanceParserKind.SILICONFLOW,
        ),
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
        isGateway = true,
        balance = ProviderBalanceSpec(
            url = "https://openrouter.ai/api/v1/credits",
            parser = BalanceParserKind.OPENROUTER,
        ),
    )

    /**
     * 旧版「单个自定义」的兼容 id。
     *
     * 现在自定义接口由 [CustomProviderSettings] 在运行时注册，可以有任意多个。
     * 保留这个常量只是为了让老代码/老配置认得 id="custom" 那一条；
     * 它本身已经不在目录里，不要再用它判断「是不是自定义」。
     */
    const val LEGACY_CUSTOM_ID = "custom"

    fun isCustom(id: String?): Boolean =
        id == LEGACY_CUSTOM_ID || id?.startsWith("${LEGACY_CUSTOM_ID}_") == true

    /** 官方厂商，创建页和设置页里排在聚合平台前面。 */
    val official: List<AIProvider> = listOf(
        Deepseek, Doubao, Zhipu, Kimi, Qianwen, Hunyuan, MiniMax, MiMo, Spark, Gemini
    )

    /** 一把 Key 调多家的平台。自定义接口由运行时注册，不在这里。 */
    private val builtinGateways: List<AIProvider> = listOf(SiliconFlow, OpenRouter)

    /**
     * 内置服务商：官方厂商 + 两个内置聚合平台。
     *
     * 凭证加载只读这一份 —— 自定义接口的 Key 存在自己的配置里，
     * 跟着 [all] 重复读会把两条数据混在一起。
     */
    val builtin: List<AIProvider> = official + builtinGateways

    @Volatile
    private var customGateways: List<AIProvider> = emptyList()

    /**
     * 注入用户自建的 OpenAI 兼容接口。
     *
     * 由 [CustomProviderSettings] 在读盘后调用；调用方传进来的 [AIProvider]
     * 已经带着该接口自己的 name / baseUrl / 上次拉到的模型列表。
     */
    fun installCustomGateways(providers: List<AIProvider>) {
        customGateways = providers
    }

    /** 聚合平台：内置两个 + 用户自建的任意多个。 */
    val gateways: List<AIProvider> get() = builtinGateways + customGateways

    /**
     * 全部服务商。
     *
     * 这个列表同时被「设置页展示」「凭证加载」「按名字找厂商」使用，
     * 自定义接口必须进来，否则创建页看不到、好友也创建不了。
     */
    val all: List<AIProvider> get() = official + gateways

    fun byId(id: String?): AIProvider? = all.firstOrNull { it.id == id }

    /** 按品牌名找服务商，也认改名前的旧名字。 */
    fun byName(name: String?): AIProvider? = all.firstOrNull {
        it.name == name || (name != null && name in it.aliases)
    }

    /** 默认选中硅基流动：一把 Key 覆盖最多模型，配置成本最低。 */
    fun default(): AIProvider = SiliconFlow
}
