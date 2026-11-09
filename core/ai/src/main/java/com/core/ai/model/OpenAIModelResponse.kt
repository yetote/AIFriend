package com.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容的 `GET /models` 响应。
 *
 * 硅基流动、DeepSeek、智谱、OpenRouter 都返回同一个形状：
 * `{"object":"list","data":[{"id":"...","owned_by":"..."}]}`。
 * 只声明用得到的字段，其余交给 `ignoreUnknownKeys` 丢掉。
 *
 * [OpenAIModel.pricing] 只有 OpenRouter 这类平台会给，其余平台是 null，
 * 价格改由 [ModelPrices] 从内置快照补齐。
 */
@Serializable
data class OpenAIModelResponse(
    @SerialName("data")
    val modelList: List<OpenAIModel> = emptyList(),
    @SerialName("object")
    val type: String? = null
)

@Serializable
data class OpenAIModel(
    @SerialName("id")
    val id: String = "",
    @SerialName("object")
    val type: String? = null,
    @SerialName("owned_by")
    val ownedBy: String? = null,
    @SerialName("pricing")
    val pricing: OpenAIModelPricing? = null
) {
    /** 从 `owned_by` 取厂商名，用于列表里做分组标签。 */
    val vendor: String
        get() = ownedBy.orEmpty()
            .substringAfterLast('/')
            .ifBlank { id.substringBefore('/') }

    /**
     * 平台自带的价格，折算成每百万 token。
     *
     * 只有 OpenRouter 会给这个字段；拿不到就返回 null，交给快照兜底。
     */
    val livePrice: ModelPrice?
        get() = pricing?.let { ModelPrices.fromOpenRouter(it.prompt, it.completion) }
}

/**
 * OpenRouter 在每个模型上附带的价格，单位是「每 token 的美元」字符串。
 *
 * 写成 String 是因为接口确实返回字符串（`"prompt":"0.0000002"`），
 * 有些平台还会给 `"-1"` 表示免费，所以保留原始字符串再自行折算。
 */
@Serializable
data class OpenAIModelPricing(
    @SerialName("prompt")
    val prompt: String? = null,
    @SerialName("completion")
    val completion: String? = null,
    @SerialName("input_cache_read")
    val inputCacheRead: String? = null
)

/**
 * 剔除不能用来聊天的模型。
 *
 * 聚合平台的模型列表里混着向量、重排、语音、图像、视频这些非对话模型，
 * 直接展示会让用户选中一个「不支持对话」的模型，然后在聊天页报错。
 */
private val NON_CHAT_MARKERS = listOf(
    "embedding", "embed", "rerank", "tts", "asr", "whisper", "speech",
    "audio", "voice", "image", "vision-only", "stable-diffusion", "flux",
    "kolors", "video", "wan-t2v", "moderation", "guard", "ocr", "bge",
    "baai", "gte-", "m3e", "cosyvoice", "sensevoice", "sam-", "vit-"
)

/** 明显是对话模型的标记，用来在拿不准时保留而不是误删。 */
private val CHAT_MARKERS = listOf(
    "chat", "instruct", "deepseek", "qwen", "glm", "kimi", "moonshot",
    "gpt", "claude", "gemini", "llama", "mistral", "yi-", "minimax",
    "internlm", "baichuan", "doubao", "hunyuan", "step-", "spark"
)

/**
 * 把模型列表收敛成「能聊天的」并按名字排序。
 *
 * 判断统一走 [isLikelyChatModel]：平台标注的非对话模型优先剔除，
 * 剩下的才按名字兜底 —— 两条入口用同一套规则，免得一个地方留、
 * 另一个地方删，用户看到的两份列表不一致。
 */
fun List<OpenAIModel>.toSelectableChatModels(): List<String> = asSequence()
    .map { it.id.trim() }
    .filter { it.isNotBlank() }
    .filter { isLikelyChatModel(it) }
    .distinct()
    .sortedBy { it.lowercase() }
    .toList()

/**
 * 把模型列表收敛成带价格的条目，按「便宜的在前」排序。
 *
 * 排序是刻意的：用户来到这个列表，最常问的就是「哪个便宜」。
 * 按价格升序排，把免费的和小模型顶到最上面，等于直接回答问题。
 * 没有价格的排在最后 —— 不确定的东西不该占据最显眼的位置。
 */
fun List<OpenAIModel>.toSelectableChatModelsWithPrices(): List<GatewayModel> = asSequence()
    .map { model ->
        GatewayModel(
            id = model.id.trim(),
            vendor = model.vendor,
            price = model.livePrice
        )
    }
    .filter { it.id.isNotBlank() }
    .filter { isLikelyChatModel(it.id) }
    .distinctBy { it.id }
    .sortedWith(
        compareBy(
            // 有价格的排前面
            { it.price == null },
            // 便宜的排前面
            { it.price?.costIndex ?: Double.MAX_VALUE },
            // 同价按名字，保证顺序稳定
            { it.id.lowercase() }
        )
    )
    .toList()

/**
 * 判断单个模型名是否像对话模型，供 UI 侧兜底过滤用。
 *
 * 平台分类优先于猜测，两个方向都认：
 * - 平台标了非 chat 的（向量、重排、语音……）直接判否，比按名字猜准；
 * - 平台标了 chat 的直接判是，免得名字里恰好带 `audio`、`image`
 *   之类字眼的对话模型被关键词误杀。
 *
 * 两份名单都不含它（还没抓到过价，或者这是新上架、定价页还没收录的模型）时，
 * 才退回按名字关键词判断 —— 拿不准就保留，别把用户想用的模型藏起来。
 */
fun isLikelyChatModel(id: String): Boolean {
    if (LivePriceCache.knownNonChatModels()?.contains(id) == true) return false
    if (LivePriceCache.knownChatModels()?.contains(id) == true) return true

    val lower = id.lowercase()
    if (NON_CHAT_MARKERS.any { lower.contains(it) }) return false
    if (CHAT_MARKERS.any { lower.contains(it) }) return true
    return true
}

/**
 * 聚合平台上的一个可选模型：id、厂商、价格。
 *
 * [vendor] 用来在列表里分组（同一个厂商的模型排在一起），
 * [price] 为 null 表示没查到价格。
 */
data class GatewayModel(
    val id: String,
    val vendor: String,
    val price: ModelPrice? = null
) {

    /** id 按 `/` 切开的路径段，`Pro/deepseek-ai/DeepSeek-V3.2` → [Pro, deepseek-ai, DeepSeek-V3.2]。 */
    private val segments: List<String>
        get() = id.split('/').filter { it.isNotBlank() }

    /**
     * 厂商的展示名，用作列表的分组标题。
     *
     * 优先用 `owned_by`，但它是平台给的、可能不可用：缺失时上游会退化成
     * id 的首段，于是 `LoRA/...` 被当成一家叫「LoRA」的厂商、
     * `Kev-4B` 这种没有前缀的模型干脆自己当组名 —— 列表里就多出几个假分组。
     * 所以这里要复核：`owned_by` 等于整个 id、或者只是 `LoRA` 这类部署前缀时，
     * 改从 id 路径里取真正的厂商段；连厂商段都没有的，归到「其他」。
     */
    val vendorLabel: String
        get() {
            val owned = vendor.trim()
            // owned_by 只有和 id 不等、且不是部署前缀时才是可信的厂商名
            val usableOwned = owned.isNotEmpty() &&
                !owned.equals(id, ignoreCase = true) &&
                owned.lowercase() !in DEPLOY_PREFIXES
            if (usableOwned) return owned.substringAfterLast('/')

            val path = segments.let { if (it.size >= 2 && it[0].lowercase() in DEPLOY_PREFIXES) it.drop(1) else it }
            // 只剩一段说明这个 id 根本没有厂商前缀（`Kev-4B`、`SemIf`）
            return if (path.size >= 2) path[0] else OTHER_VENDOR
        }

    /**
     * 行内展示的模型名：去掉与组标题重复的厂商前缀。
     *
     * `LoRA/Qwen/Qwen2.5-7B-Instruct` 去掉 `LoRA/` 和厂商段后是
     * `Qwen2.5-7B-Instruct`；`Pro/` 要留着 —— 它是另一档服务，
     * 去掉就会和同名的普通版撞成两条一模一样、价格却不同的行。
     */
    val displayName: String
        get() {
            val path = segments
            if (path.size <= 1) return id
            val index = path.indexOfFirst { it.equals(vendorLabel, ignoreCase = true) }
            if (index < 0) return id
            // 厂商段之前只保留 `Pro` 这种产品线标记，部署前缀丢掉
            val head = path.take(index).filter { it.lowercase() in PRODUCT_LINES }
            val tail = path.drop(index + 1)
            if (tail.isEmpty()) return id
            return (head + tail).joinToString("/")
        }

    private companion object {
        /** 出现在厂商前面、只表示部署方式的前缀，不作为厂商名。 */
        val DEPLOY_PREFIXES = setOf("lora", "online", "free")

        /**
         * 出现在厂商前面、但代表另一档服务的标记，展示名里要保留。
         * `LoRA` 是同一模型的微调部署、`Pro` 是另一档服务，单价都不同，
         * 去掉就会和同名模型撞成两条一模一样、价格却不同的行。
         */
        val PRODUCT_LINES = setOf("pro", "lora")

        const val OTHER_VENDOR = "其他"
    }
}

/**
 * 官方厂商里「代理了别家模型」的那一类（百炼、方舟）用的分组名推断。
 *
 * 这两家的模型 id 混着两种写法：
 * - 自家的 `qwen3.8-flash`、`doubao-seed-evolving`，没有厂商段；
 * - 代理的 `deepseek-v4-pro-0813`、`kimi/kimi-k3`、`ZHIPU/GLM-5.3`，
 *   有的带 `/`，有的只是名字里带着别家的牌子。
 *
 * 不能直接复用 [GatewayModel.vendorLabel]：它靠 `/` 切段，
 * 而百炼的第三方 id 多数是平的（`deepseek-v4-pro-0813`），
 * 按那条规则会整段被当成厂商名，分组栏里就会出现
 * 「deepseek-v4-pro-0813」这种以模型名当分类的假组。
 * 所以这里按「认识的牌子」匹配，认不出的归到 [selfName]。
 */
fun vendorLabelOf(modelId: String, selfName: String): String {
    val lower = modelId.lowercase()

    // 带路径的（`kimi/kimi-k3`、`ZHIPU/GLM-5.3`）先按段匹配，再看整串
    KNOWN_VENDOR_MARKERS.forEach { (marker, label) ->
        if (lower.startsWith(marker) || lower.contains("/$marker")) return label
    }
    // 认不出的（自家新系列、或没听过的第三方）留在自家组，
    // 避免分组栏多出一堆只有一条模型的分组
    return selfName
}

/**
 * 认得出的模型牌子 → 展示用厂商名。
 *
 * 只收「一眼能认」的：这些是百炼 / 方舟上真正会出现的第三方对话模型。
 * 宁可把少见模型归到自家组，也不要让分组栏塞满一行行猜出来的名字。
 *
 * 匹配用 startsWith，所以 marker 本身要有足够区分度：
 * `step` 这种通用词会误吞 `stepladder-xxx`，必须写成 `step-` / `stepfun`。
 * 同理 `yi-` 带横杠，避免吃掉 `yinglong` 之类。
 */
private val KNOWN_VENDOR_MARKERS: List<Pair<String, String>> = listOf(
    "qwen" to "通义千问",
    "doubao" to "豆包",
    "deepseek" to "DeepSeek",
    "kimi" to "Kimi",
    "moonshot" to "Kimi",
    "glm" to "GLM",
    "zhipu" to "GLM",
    "minimax" to "MiniMax",
    "mimo-" to "小米",
    "ernie" to "文心一言",
    "hunyuan" to "混元",
    "baichuan" to "百川",
    "internlm" to "书生",
    "step-" to "阶跃星辰",
    "stepfun" to "阶跃星辰",
    "yi-" to "零一万物",
    "llama" to "Llama",
    "mistral" to "Mistral"
)
