package com.core.ai.model

/**
 * 从硅基流动定价页面里解析模型价格。
 *
 * 为什么解析页面而不是调接口：`GET /v1/models` 只返回 id 和 owned_by，
 * 不带任何价格；`/api` 下的路径全是 307 重定向，没有可用的 JSON 接口。
 * 价格只存在于 https://www.siliconflow.cn/pricing 这张服务端渲染的页面里。
 *
 * 页面是 Next.js App Router，数据以 flight 载荷的形式内嵌在
 * `self.__next_f.push([1,"..."])` 里，所以不解析 HTML 标签，只认字段名。
 *
 * ## 真正的价格字段是 `realTimePriceCnyUnit`
 *
 * 载荷里有两套看起来都像价格的字段，**只有一套是对的**：
 *
 * - 模型对象上的 `inputPrice` / `price`：这是 `specification == "pricing"` 的
 *   那份摘要，**分档模型上取的档位不确定**。比如 `Qwen/Qwen3.5-27B` 页面上写的是
 *   `输入 [0, 128k) ¥0.6 / ¥4.8`、`输入 [128k, +∞) ¥1.8 / ¥14.4`，
 *   而 `inputPrice` / `price` 给的是 1.8 / 14.4 —— 悄悄变成了长上下文那档的价。
 *   `DeepSeek-V4-Flash` 更离谱，给的是 1 / 2，两档都不是。
 * - 计费明细对象上的 `realTimePriceCnyUnit`（单位「K tokens」）：逐档、逐分量，
 *   和页面上渲染出来的表格一一对应。
 *
 * 所以这里用 `realTimePriceCnyUnit` × 1000 得到每百万 token 的价格，
 * 再按下面「取哪一档」的规则收敛成一个数。
 *
 * ## 模型在页面上的两种存在形式
 *
 * - **模型对象**：带 `modelName` / `subType`，能从 `subType` 直接认出对话模型。
 * - **只有计费明细**：只有 `playgroundName` 和价格，没有类型字段。
 *   LoRA 部署、免费体验模型属于这种，而 `/v1/models` 照样会返回它们的 id。
 *   早先只遍历模型对象，这几个在列表里就成了「查不到价格」。
 *   现在补一遍：没有模型对象的，只要输入、输出两个分量齐全就收下 ——
 *   实测「两个分量都有 K tokens 单价」与平台的 chat 分类完全一致（误收 0、漏 0）。
 *
 * ## 取哪一档
 *
 * 与内置快照 [SiliconFlowPriceSnapshot] 保持同一口径，两条规则：
 * - **阶梯价**（`输入 [0, 128k)` 这类按上下文长度分段）：取**最低档**当基础价，
 *   代表常规上下文的价格，不拿长上下文的高价吓人。更贵的档不丢，
 *   放进 [ModelPrice.bands]，UI 以小字注在价格下面。
 * - **时段价**（`费用发生时段: 2点～8点` 这类）：取**标准时段**（不打折、较贵那档），
 *   避免让用户以为随时都这么便宜。
 *
 * 这套规则已用 29 条内置快照逐条校验，结果完全一致。
 */
internal object SiliconFlowPricingParser {

    /** 定价页面地址。 */
    const val PRICING_URL = "https://www.siliconflow.cn/pricing"

    /** 一次解析的结果。 */
    data class ParseResult(
        /** 模型 id → 价格，人民币 / 百万 token。 */
        val prices: Map<String, ModelPrice>,
        /**
         * 平台明确标成非对话模型的 id（向量、重排、语音、图像、视频）。
         *
         * 这份名单是「确定不是对话模型」，用来精确剔除 ——
         * 比按名字猜准，也不会误伤名字里带 `audio`、`image` 的对话模型。
         * 名单里没有的模型**不能**据此认定是对话模型：定价页只列在售模型。
         */
        val nonChatModels: Set<String> = emptySet()
    ) {
        val isEmpty: Boolean get() = prices.isEmpty()
    }

    /**
     * 解析页面 HTML。
     *
     * 返回空 [ParseResult] 表示「没解析出东西」——页面改版、被风控、
     * 或者请求到的是错误页。调用方应当把这种情况当作失败处理，
     * 继续用已有的价格（快照或上次抓到的），而不是把价格清空。
     */
    fun parse(html: String): ParseResult {
        if (html.isEmpty()) return ParseResult(emptyMap())

        val payload = flightPayload(html)
        if (payload.isEmpty()) return ParseResult(emptyMap())

        val subTypes = modelSubTypes(payload)
        val tiers = priceTiers(payload)

        val prices = LinkedHashMap<String, ModelPrice>()
        val nonChat = HashSet<String>()

        for ((id, subType) in subTypes) {
            // 平台自己标了类型，照它办：非 chat 的进「确定不能聊天」名单，
            // 这个判断比按模型名猜可靠得多。
            if (subType != CHAT) {
                nonChat.add(id)
                continue
            }

            // 两个价格必须都有：只拿到输入价就推出价格是瞎猜，宁可这个模型不显示价。
            val price = tiers[id]?.let { pickTier(it) } ?: continue
            prices[id] = price
        }

        // 页面上的模型有两种存在形式：带类型的「模型对象」，和只有「计费明细」的条目。
        // 后者没有 `modelName` / `subType`，所以上面那轮完全看不到它们 ——
        // 而它们恰恰是在售的模型（LoRA 部署、免费体验模型都属于这种），
        // `/v1/models` 会返回它们，于是列表里这几个就成了「查不到价」。
        // 这里补一遍：没有模型对象的，只要计费明细齐全就收下。
        for ((id, tierList) in tiers) {
            if (subTypes.containsKey(id)) continue
            val price = pickTier(tierList) ?: continue
            prices[id] = price
        }

        // 同一批数据里既出现 chat 又被标成别的类型，说明命名有歧义，
        // 这种情况以 chat 为准（它至少能聊天），别把用户能用的模型藏起来。
        nonChat.removeAll(prices.keys)

        return ParseResult(prices, nonChat)
    }

    /**
     * 一档价格：档位说明 + 输入 / 输出单价（人民币 / 百万 token）。
     *
     * 两者都可能缺失（有的模型只标了输入价，或者缓存价那一栏是 `-`）。
     */
    private class Tier(
        /** 档位说明，例如 `输入 [0, 128k)` 或 `费用发生时段: 2点～8点`；无档位时为空串。 */
        val coord: String,
        var input: Double? = null,
        var output: Double? = null
    )

    /**
     * 按规则从多档里挑出基础价，并把更贵的阶梯档一并带上。
     *
     * 基础价 = 最低阶梯档（没有阶梯价时是标准时段档），
     * 更贵的阶梯档进 [ModelPrice.bands]，UI 会把它们当小字注在价格下面。
     *
     * @return 输入价和输出价都有才返回，否则 null（宁可无价也不显示半截）。
     */
    private fun pickTier(tiers: List<Tier>): ModelPrice? {
        // 阶梯价优先：按上下文长度分段时取最低档当基础价
        val ranged = tiers.filter { rangeStart(it.coord) != null }
        val ordered = if (ranged.isNotEmpty()) {
            ranged.sortedBy { rangeStart(it.coord) ?: 0L }
        } else {
            // 时段价：取标准时段，也就是价格较高的那档
            listOfNotNull(
                tiers.filter { it.input != null }
                    .maxByOrNull { (it.input ?: 0.0) + (it.output ?: 0.0) }
            )
        }
        val chosen = ordered.firstOrNull() ?: return null

        val input = chosen.input ?: return null
        val output = chosen.output ?: return null
        if (input < 0.0 || output < 0.0) return null

        // 基础档之后的每一档都带上；缺输入或输出价的档不展示，
        // 只显示半截价格比不显示更容易误导。
        val bands = ordered.drop(1).mapNotNull { tier ->
            val bandInput = tier.input ?: return@mapNotNull null
            val bandOutput = tier.output ?: return@mapNotNull null
            if (bandInput < 0.0 || bandOutput < 0.0) return@mapNotNull null
            val from = rangeStart(tier.coord) ?: return@mapNotNull null
            if (from <= 0L) return@mapNotNull null
            PriceBand(
                fromTokens = from,
                inputPerMillion = bandInput,
                outputPerMillion = bandOutput
            )
        }

        return ModelPrice(input, output, bands)
    }

    /**
     * 从档位说明里读出阶梯的起点，用来排序取最低档。
     *
     * `输入 [0, 128k)` → 0；`输入 [128k, +∞)` → 128000。
     * 不是阶梯价（时段价、空串）返回 null。
     */
    private fun rangeStart(coord: String): Long? {
        val m = RANGE_START.find(coord) ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        val scaled = if (m.groupValues[2].isNotEmpty()) value * 1000 else value
        return scaled.toLong()
    }

    /**
     * 收集每个模型的每一档价格。
     *
     * 计费明细是一个个扁平对象，`playgroundName` 是模型名、`coordinateDesc` 是档位、
     * `componentCode` 是计费分量、`realTimePriceCnyUnit` 是**每 K token 的人民币价**。
     * 先按 (模型, 档位) 归拢，翻成每百万 token 再参与挑选。
     */
    private fun priceTiers(payload: String): Map<String, List<Tier>> {
        val out = HashMap<String, MutableList<Tier>>()

        for (segment in flatObjects(payload)) {
            val component = field(segment, "componentCode") ?: continue
            // 只关心对话计费的两个分量，图片 / 视频 / 字符单价单位不同，不能混进来
            val isInput = component == "input-tokens"
            val isOutput = component == "output-tokens"
            if (!isInput && !isOutput) continue

            val unit = field(segment, "unitZhCnName") ?: continue
            // 只认「K tokens」：别的单位乘 1000 会得出荒唐的数
            if (unit != TOKEN_UNIT) continue

            val model = field(segment, "playgroundName") ?: continue
            val perK = field(segment, "realTimePriceCnyUnit")?.toDoubleOrNull() ?: continue
            if (perK < 0.0) continue

            val coord = field(segment, "coordinateDesc").orEmpty()
            val perMillion = perK * 1000.0

            val list = out.getOrPut(model) { ArrayList(2) }
            val tier = list.firstOrNull { it.coord == coord }
                ?: Tier(coord).also { list.add(it) }
            if (isInput) tier.input = perMillion else tier.output = perMillion
        }
        return out
    }

    /** 收集所有模型的 `modelName` → `subType`，用来区分对话模型和别的模型。 */
    private fun modelSubTypes(payload: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (segment in modelObjects(payload)) {
            val id = field(segment, "modelName") ?: continue
            val subType = field(segment, "subType") ?: continue
            // 同名模型可能在多个分区里出现，且类型标得不一样。
            // 只要有一处标成 chat 就当对话模型 —— 它至少能聊天，
            // 别因为另一处标成 embedding 把用户能用的模型藏起来。
            val existing = out[id]
            if (existing == CHAT) continue
            out[id] = if (existing == null) subType else if (subType == CHAT) CHAT else existing
        }
        return out
    }

    /**
     * 抽出 Next.js 的 flight 载荷。
     *
     * App Router 把服务端组件数据分片塞进
     * `self.__next_f.push([1,"<转义后的字符串>"])`，
     * 这里按 JSON 字符串规则逐个还原再拼起来。
     *
     * **不用正则**：`("(?:[^"\\]|\\.)*")` 这种交替量词在 Java 正则里是递归实现，
     * 递归深度随匹配长度线性增长。真实页面有 738 KB、单个片段几十 KB，
     * 直接 `StackOverflowError` 把 App 打崩（单元测试用手写小样本时发现不了，
     * 因为样本太短）。手写扫描没有这个问题，也快得多。
     */
    private fun flightPayload(html: String): String {
        val out = StringBuilder(html.length / 2)
        var from = 0
        while (true) {
            val start = html.indexOf(FLIGHT_PUSH_MARKER, from)
            if (start < 0) break
            val quote = start + FLIGHT_PUSH_MARKER.length
            if (quote >= html.length || html[quote] != '"') {
                from = quote
                continue
            }
            val end = scanJsonString(html, quote)
            if (end < 0) break
            out.append(unescapeJsonString(html.substring(quote, end + 1)))
            from = end + 1
        }
        return out.toString()
    }

    /**
     * 从 [open] 处的开引号开始，找到字符串字面量结束的闭引号下标。
     *
     * 跳过 `\"` 这类转义，避免把描述里的引号当结尾。
     *
     * @return 闭引号下标；没有闭合返回 -1。
     */
    private fun scanJsonString(text: String, open: Int): Int {
        var i = open + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i += 2      // 跳过转义的下一个字符
                '"' -> return i
                else -> i++
            }
        }
        return -1
    }

    /**
     * 从 flight 载荷里切出每个模型对象（以 `modelId` 打头的那些）。
     *
     * 不能按行切：描述文字里如果带换行符（JSON 里的 `\n` 解码后就是真换行），
     * 会把一个对象劈成两行，字段落在第二行上就会被漏掉。
     */
    private fun modelObjects(payload: String): Sequence<String> =
        objectsAnchoredAt(payload, "\"modelId\"")

    /**
     * 从 flight 载荷里切出每个计费明细对象（以 `playgroundName` 打头的那些）。
     *
     * 这些对象是扁平的（没有嵌套的对象 / 数组字段），所以也能按同样的
     * 括号配对切出来。
     */
    private fun flatObjects(payload: String): Sequence<String> =
        objectsAnchoredAt(payload, "\"playgroundName\"")

    /**
     * 以 [anchor] 定位对象起点，按括号配对切出完整对象。
     *
     * 扫描时跳过字符串内部，避免描述里的 `{` `}` 干扰层级。
     */
    private fun objectsAnchoredAt(payload: String, anchor: String): Sequence<String> = sequence {
        var from = 0
        while (true) {
            val start = payload.indexOf(anchor, from)
            if (start < 0) break
            val open = payload.lastIndexOf('{', start)
            if (open < 0) {
                from = start + 1
                continue
            }
            val end = matchBrace(payload, open)
            if (end < 0) {
                from = start + 1
                continue
            }
            yield(payload.substring(open, end + 1))
            from = end + 1
        }
    }

    /**
     * 从 [open] 处的 `{` 开始找配对的 `}`，跳过字符串内容。
     *
     * @return 配对括号的下标；不配对返回 -1。
     */
    private fun matchBrace(text: String, open: Int): Int {
        var depth = 0
        var inString = false
        var i = open
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                when (c) {
                    '\\' -> i++          // 跳过转义的下一个字符，\" \\ 都算
                    '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return -1
    }

    /** 取一个字符串 / 数字字段的原始值。 */
    private fun field(obj: String, name: String): String? {
        val match = Regex("\"$name\":(\"([^\"\\\\]|\\\\.)*\"|[^,}]+)").find(obj)
            ?: return null
        val raw = match.groupValues[1].trim()
        return if (raw.length >= 2 && raw.first() == '"' && raw.last() == '"') {
            unescapeJsonString(raw)
        } else {
            raw
        }
    }

    /**
     * 还原一个 JSON 字符串字面量（带引号）。
     *
     * 只处理这里可能出现的转义：`\"`、`\\`、`\/`、`\n`、`\uXXXX`。
     * 模型描述里常有中文和引号，不还原会让字段值错位。
     */
    private fun unescapeJsonString(raw: String): String {
        val body = raw.removeSurrounding("\"")
        if (body.isEmpty()) return body
        if (body.indexOf('\\') < 0) return body

        val sb = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c != '\\' || i == body.length - 1) {
                sb.append(c)
                i++
                continue
            }
            when (val next = body[i + 1]) {
                '"' -> { sb.append('"'); i += 2 }
                '\\' -> { sb.append('\\'); i += 2 }
                '/' -> { sb.append('/'); i += 2 }
                'n' -> { sb.append('\n'); i += 2 }
                'r' -> { sb.append('\r'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                'u' -> {
                    // \uXXXX：越界就原样保留，避免抛异常打断整页解析
                    if (i + 5 < body.length) {
                        val hex = body.substring(i + 2, i + 6)
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            sb.append(code.toChar())
                            i += 6
                        } else {
                            sb.append(c); i++
                        }
                    } else {
                        sb.append(c); i++
                    }
                }
                else -> { sb.append(next); i += 2 }
            }
        }
        return sb.toString()
    }

    /** 每 K token 计价那一档的单位名，只有它才能乘 1000 换算成每百万 token。 */
    private const val TOKEN_UNIT = "K tokens"

    /** 平台对「对话模型」的分类名。 */
    private const val CHAT = "chat"

    /** 阶梯价档位说明里区间的起点，例如 `输入 [0, 128k)` 中的 `0` 和 `k`。 */
    private val RANGE_START = Regex("""\[\s*([0-9.]+)\s*([kK]?)\s*,""")

    /**
     * flight 载荷分片的开头标记。
     *
     * 完整形式是 `self.__next_f.push([1,"`，后面紧跟一段 JSON 字符串。
     */
    private const val FLIGHT_PUSH_MARKER = """self.__next_f.push([1,"""
}
