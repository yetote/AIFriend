package com.core.repository.balance

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.util.Locale

/**
 * 各家余量响应的解析器。
 *
 * 全部写成纯函数（输入是响应文本，输出是 [BalanceResult]），不碰网络也不碰凭证，
 * 这样每家的字段规则都能用单测钉住——这些接口没有官方文档，
 * 字段名和语义全靠实测，改错了在测试里就能看出来，而不是等用户打开页面才发现。
 */
internal object BalanceParsers {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(
        kind: BalanceParserKind,
        providerId: String,
        providerName: String,
        body: String,
        /** 火山要判断「这个模型开通了没」，其余家不用。 */
        targetModel: String? = null,
    ): BalanceResult {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
            ?: return BalanceResult.Failed("返回的不是合法 JSON")
        return when (kind) {
            BalanceParserKind.DEEPSEEK -> deepseek(providerId, providerName, root)
            BalanceParserKind.MOONSHOT -> moonshot(providerId, providerName, root)
            BalanceParserKind.MINIMAX -> minimax(providerId, providerName, root)
            BalanceParserKind.ZHIPU -> zhipu(providerId, providerName, root)
            BalanceParserKind.SILICONFLOW -> siliconFlow(providerId, providerName, root)
            BalanceParserKind.OPENROUTER -> openRouter(providerId, providerName, root)
            BalanceParserKind.VOLCENGINE -> volcengine(providerId, providerName, root, targetModel)
        }
    }

    /**
     * 从失败响应里翻出厂商给的原因。
     *
     * 各家放错误的位置不一样（error.message / message / error_msg / error.code），
     * 翻到就把原文给用户——「余额不足」比「返回里没有余额信息」有用得多。
     *
     * 只在**解析已经失败**时才调用：正常响应里也可能带 message 字段，
     * 无条件提取会把成功的结果误判成失败。
     */
    fun extractErrorMessage(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        root.dig("error")?.let { e ->
            val message = if (e is JsonObject) e.dig("message")?.asText() else e.asText()
            if (!message.isNullOrBlank()) return message
            val code = if (e is JsonObject) e.dig("code")?.asText() else null
            if (!code.isNullOrBlank()) return "错误码 $code"
        }
        root.dig("error_msg")?.asText()?.takeIf { it.isNotBlank() }?.let { return it }
        root.dig("message")?.asText()?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    // ---- DeepSeek ----------------------------------------------------

    /**
     * `balance_infos[]` 里每项是一种货币的余额。
     *
     * 官方文档同时给 CNY 和 USD 两种，这里优先取人民币——
     * 国内用户看美元余额没什么用；没有人民币项再退回第一项。
     */
    private fun deepseek(id: String, name: String, root: JsonElement): BalanceResult {
        val infos = root.arrayAt("balance_infos")
        if (infos.isEmpty()) return BalanceResult.Failed("返回里没有余额信息")
        val info = infos.firstOrNull { it.dig("currency")?.asText() == "CNY" } ?: infos.first()
        val currency = info.dig("currency")?.asText() ?: "CNY"
        val total = info.dig("total_balance").asNumber()
        val granted = info.dig("granted_balance").asNumber()
        val topped = info.dig("topped_up_balance").asNumber()
        if (total == null && granted == null && topped == null) {
            return BalanceResult.Failed("余额字段为空")
        }
        val available = root.dig("is_available").asBoolean() ?: true
        val details = buildList {
            granted?.let { add("赠金 ${money(it, currency)}") }
            topped?.let { add("充值 ${money(it, currency)}") }
        }
        return BalanceResult.Ok(
            BalanceSnapshot(
                providerId = id,
                providerName = name,
                headline = money(total, currency) ?: "—",
                details = details,
                tone = if (!available) BalanceTone.Empty else toneOfAmount(total),
            )
        )
    }

    // ---- Kimi / Moonshot ----------------------------------------------

    /**
     * 官方文档写的是 `data.available_balance`，实测别的实现读的是 `data.total_balance`。
     * 两个都试：只认一个的话，换个套餐类型就变成「查不到余额」。
     */
    private fun moonshot(id: String, name: String, root: JsonElement): BalanceResult {
        val code = root.dig("code").asNumber()
        if (code != null && code != 0.0) {
            return BalanceResult.Failed(root.dig("message")?.asText() ?: "接口返回错误")
        }
        val data = root.dig("data")
        val total = data.dig("available_balance").asNumber()
            ?: data.dig("total_balance").asNumber()
        if (total == null) return BalanceResult.Failed("返回里没有余额信息")
        val voucher = data.dig("voucher_balance").asNumber()
        val cash = data.dig("cash_balance").asNumber()
        val details = buildList {
            voucher?.let { add("代金券 ${money(it, "CNY")}") }
            cash?.let { add("现金 ${money(it, "CNY")}") }
        }
        return BalanceResult.Ok(
            BalanceSnapshot(
                providerId = id,
                providerName = name,
                headline = money(total, "CNY") ?: "—",
                details = details,
                tone = toneOfAmount(total),
            )
        )
    }

    // ---- MiniMax -----------------------------------------------------

    /**
     * Token Plan 的额度剩余。
     *
     * 两个实测坑：
     * 1. `*_usage_count` 这个名字骗人，它返回的是**剩余**次数（已用 = 总量 − 它）；
     * 2. `*_remaining_percent` 已经是剩余百分比，别再减 100。
     *
     * 另外 `base_resp.status_code != 0` 是业务失败，而且**照样 HTTP 200**——
     * 只看状态码会把「套餐没买」显示成「剩余 0%」。
     */
    private fun minimax(id: String, name: String, root: JsonElement): BalanceResult {
        val status = root.dig("base_resp").dig("status_code").asNumber()
        if (status != null && status != 0.0) {
            return BalanceResult.Failed(
                root.dig("base_resp").dig("status_msg")?.asText() ?: "接口返回错误"
            )
        }
        val rows = root.arrayAt("model_remains")
        if (rows.isEmpty()) {
            // 按量计费的账号没有套餐额度，这个接口会空着返回
            return BalanceResult.Failed("这个账号没有套餐额度（按量计费不显示剩余）")
        }
        // 优先挑 MiniMax-M* 那几行：响应里可能混着别的模型行
        val row = rows.firstOrNull {
            it.dig("model_name")?.asText()?.startsWith("MiniMax-M") == true
        } ?: rows.first()

        val details = mutableListOf<String>()

        val intervalPercent = row.dig("current_interval_remaining_percent").asNumber()
        if (intervalPercent != null) {
            details += "5 小时窗口剩余 ${percent(intervalPercent)}"
        } else {
            val total = row.dig("current_interval_total_count").asNumber()
            val remain = row.dig("current_interval_usage_count").asNumber()
            if (total != null && remain != null) {
                details += "5 小时窗口剩余 ${percent(remain / total * 100)}"
            }
        }
        row.dig("end_time").asNumber()?.let { details += "5 小时窗口 ${resetTime(it)} 重置" }

        // status == 3 表示这个套餐压根没有周额度，不是「用完了」
        val weeklyStatus = row.dig("current_weekly_status").asNumber()
        if (weeklyStatus != 3.0) {
            val weeklyPercent = row.dig("current_weekly_remaining_percent").asNumber()
            if (weeklyPercent != null) {
                details += "本周剩余 ${percent(weeklyPercent)}"
            } else {
                val total = row.dig("current_weekly_total_count").asNumber()
                val remain = row.dig("current_weekly_usage_count").asNumber()
                if (total != null && remain != null && total > 0) {
                    details += "本周剩余 ${percent(remain / total * 100)}"
                }
            }
            row.dig("weekly_end_time").asNumber()?.let { details += "本周 ${resetTime(it)} 重置" }
        }

        if (details.isEmpty()) return BalanceResult.Failed("返回里没有额度信息")

        val headline = intervalPercent?.let { "5 小时剩余 ${percent(it)}" }
            ?: details.first().substringAfter("剩余 ")
        val tone = when {
            intervalPercent == null -> BalanceTone.Normal
            intervalPercent <= 0 -> BalanceTone.Empty
            intervalPercent <= 10 -> BalanceTone.Low
            else -> BalanceTone.Normal
        }
        return BalanceResult.Ok(
            BalanceSnapshot(
                providerId = id,
                providerName = name,
                headline = headline,
                details = details,
                tone = tone,
            )
        )
    }

    // ---- 智谱 GLM ----------------------------------------------------

    /**
     * 智谱的额度接口。
     *
     * `data.limits[]` 里一个窗口一项，靠 [type] 和 [unit] 区分：
     * - `TOKENS_LIMIT` + `unit=3` → 5 小时 prompt 池；`unit=6` → 本周；
     * - `TIME_LIMIT` + `unit=5` → 月度（MCP / 工具类额度池）；
     * - `CREDIT_LIMIT` → 积分 / 资源包套餐。
     *
     * 三个容易踩的字段语义：
     * 1. `number` 是**周期长度**（5 小时、1 周、1 个月），不是额度总量。
     *    拿它去除 `remaining` 会算出 10000% 这种数；
     * 2. `percentage` 是**已用**比例，不是剩余。剩余要自己 `100 -` 一下；
     * 3. `nextResetTime` 是毫秒时间戳（数字），不是字符串。
     */
    private fun zhipu(id: String, name: String, root: JsonElement): BalanceResult {
        val limits = root.arrayAt("data.limits")
        if (limits.isEmpty()) return BalanceResult.Failed("返回里没有额度信息")

        val details = mutableListOf<String>()
        root.dig("data").dig("level")?.asText()?.takeIf { it.isNotBlank() }
            ?.let { details += "套餐 $it" }

        var headline: String? = null
        var tone = BalanceTone.Normal

        for (limit in limits) {
            val label = zhipuWindowLabel(limit)
            val remaining = limit.dig("remaining").asNumber()
            val usedPercent = limit.dig("percentage").asNumber()
            // percentage 是已用比例，剩下的才是自己的
            val remainPercent = usedPercent?.let { 100 - it }

            val line = when {
                remainPercent != null && remaining != null ->
                    "$label 剩余 ${percent(remainPercent)}（${longNumber(remaining)}）"
                remainPercent != null -> "$label 剩余 ${percent(remainPercent)}"
                remaining != null -> "$label 剩余 ${longNumber(remaining)}"
                else -> null
            } ?: continue

            details += line
            limit.dig("nextResetTime").asNumber()?.let { details += "$label ${resetTime(it)} 重置" }

            // 5 小时窗口是主指标：它一满，这家基本就停工了
            if (headline == null && label.startsWith("5 小时")) {
                headline = "$label 剩余 ${percent(remainPercent ?: 0.0)}"
                tone = if (remainPercent != null) toneOfPercent(remainPercent) else BalanceTone.Normal
            }
        }

        if (details.isEmpty()) return BalanceResult.Failed("返回里没有额度信息")
        return BalanceResult.Ok(
            BalanceSnapshot(
                providerId = id,
                providerName = name,
                headline = headline ?: "剩余 ${details.last().substringAfter("剩余 ", "")}",
                details = details,
                tone = tone,
            )
        )
    }

    private fun zhipuWindowLabel(limit: JsonElement): String {
        val type = limit.dig("type")?.asText().orEmpty()
        val unit = limit.dig("unit").asNumber()
        return when {
            type == "TIME_LIMIT" -> "月度"
            type == "CREDIT_LIMIT" -> "额度"
            unit == 3.0 -> "5 小时"
            unit == 6.0 -> "本周"
            else -> "窗口"
        }
    }

    // ---- 硅基流动 ----------------------------------------------------

    private fun siliconFlow(id: String, name: String, root: JsonElement): BalanceResult {
        val data = root.dig("data")
        val balance = data.dig("balance").asNumber()
        if (balance == null) return BalanceResult.Failed("返回里没有余额信息")
        val charged = data.dig("chargeBalance").asNumber()
        val used = data.dig("totalUsage").asNumber()
        val details = buildList {
            charged?.let { add("充值 ${money(it, "CNY")}") }
            used?.let { add("已用 ${money(it, "CNY")}") }
        }
        return BalanceResult.Ok(
            BalanceSnapshot(
                providerId = id,
                providerName = name,
                headline = money(balance, "CNY") ?: "—",
                details = details,
                tone = toneOfAmount(balance),
            )
        )
    }

    // ---- OpenRouter --------------------------------------------------

    private fun openRouter(id: String, name: String, root: JsonElement): BalanceResult {
        val data = root.dig("data")
        val total = data.dig("total_credits").asNumber()
        val used = data.dig("total_usage").asNumber()
        if (total == null) return BalanceResult.Failed("返回里没有额度信息")
        val remain = total - (used ?: 0.0)
        val details = buildList {
            add("总额 ${money(total, "USD")}")
            used?.let { add("已用 ${money(it, "USD")}") }
        }
        return BalanceResult.Ok(
            BalanceSnapshot(
                providerId = id,
                providerName = name,
                headline = money(remain, "USD") ?: "—",
                details = details,
                tone = toneOfAmount(remain),
            )
        )
    }

    // ---- 火山方舟 ----------------------------------------------------

    /**
     * 方舟的「模型开通列表」。
     *
     * 这里查的不是钱，是**开通状态**：方舟的模型要在控制台单独开通，
     * 没开通就直接 404。而 `/models` 会把没开通的模型也列出来——
     * 好友建好了、看着也正常，一开口就说不了话，就是踩了这一脚。
     *
     * 字段名按多个候选取（官方文档没写死），保证换个版本也能读出来。
     */
    private fun volcengine(
        id: String,
        name: String,
        root: JsonElement,
        targetModel: String?,
    ): BalanceResult {
        val items = root.arrayAt("Items")
        if (items.isEmpty()) return BalanceResult.Failed("返回里没有模型列表")

        fun modelName(item: JsonElement): String =
            item.dig("FoundationModelName")?.asText()
                ?: item.dig("Name")?.asText()
                ?: item.dig("DisplayName")?.asText()
                ?: ""

        fun state(item: JsonElement): String =
            item.dig("State")?.asText()
                ?: item.dig("ActivationState")?.asText()
                ?: item.dig("Status")?.asText()
                ?: ""

        val opened = items.count { state(it).equals("Available", ignoreCase = true) }
        val details = mutableListOf<String>()
        details += "账号已开通 $opened / ${items.size} 个模型"

        if (targetModel.isNullOrBlank()) {
            return BalanceResult.Ok(
                BalanceSnapshot(
                    providerId = id,
                    providerName = name,
                    headline = "已开通 $opened 个模型",
                    details = details,
                )
            )
        }

        // 好友用的模型对不上基础模型名时（推理接入点的 id 和基础模型名不是一个东西），
        // 只能在开通列表里找包含关系，找不到就说「没在这一批里」，不能直接判没开通
        val hit = items.firstOrNull { modelName(it).equals(targetModel, ignoreCase = true) }
            ?: items.firstOrNull { modelName(it).contains(targetModel, ignoreCase = true) }
        if (hit == null) {
            return BalanceResult.Ok(
                BalanceSnapshot(
                    providerId = id,
                    providerName = name,
                    headline = "未在开通列表里",
                    details = details + "（$targetModel 不在这一批基础模型中，可能是推理接入点）",
                    tone = BalanceTone.Low,
                )
            )
        }
        val isAvailable = state(hit).equals("Available", ignoreCase = true)
        return BalanceResult.Ok(
            BalanceSnapshot(
                providerId = id,
                providerName = name,
                headline = if (isAvailable) "模型已开通" else "模型未开通",
                details = details + "$targetModel：${if (isAvailable) "已开通" else "未开通"}",
                tone = if (isAvailable) BalanceTone.Normal else BalanceTone.Empty,
            )
        )
    }
}

// ---- 通用小工具 ------------------------------------------------------

/** 按点路径取值；路径中间遇到数组时取第一项。 */
internal fun JsonElement?.dig(path: String): JsonElement? {
    var current: JsonElement = this ?: return null
    for (segment in path.split('.')) {
        if (segment.isEmpty()) continue
        current = when (current) {
            is JsonObject -> current[segment] ?: return null
            is JsonArray -> current.firstOrNull() ?: return null
            else -> return null
        }
    }
    return current
}

/** 取数组；路径末端是数组时返回全部元素。 */
internal fun JsonElement?.arrayAt(path: String): List<JsonElement> {
    var current: JsonElement = this ?: return emptyList()
    for (segment in path.split('.')) {
        if (segment.isEmpty()) continue
        current = when (current) {
            is JsonObject -> current[segment] ?: return emptyList()
            is JsonArray -> current.firstOrNull() ?: return emptyList()
            else -> return emptyList()
        }
    }
    return if (current is JsonArray) current.toList() else emptyList()
}

/** 厂商有时把数字写成字符串（"110.00"），两种都要认。 */
internal fun JsonElement?.asNumber(): Double? = when (this) {
    null -> null
    is JsonPrimitive -> this.content.toDoubleOrNull() ?: this.doubleOrNull
    else -> null
}

internal fun JsonElement?.asText(): String? = when (this) {
    null -> null
    is JsonPrimitive -> this.content
    else -> null
}

internal fun JsonElement?.asBoolean(): Boolean? = when (this) {
    null -> null
    is JsonPrimitive -> this.booleanOrNull ?: this.content.toBooleanStrictOrNull()
    else -> null
}

/** 金额带货币符号。 */
internal fun money(amount: Double?, currency: String): String? {
    if (amount == null) return null
    val symbol = when (currency.uppercase()) {
        "USD" -> "$"
        "CNY" -> "¥"
        else -> ""
    }
    return symbol + String.format(Locale.US, "%.2f", amount)
}

/** 百分比文本。 */
internal fun percent(value: Double): String =
    String.format(Locale.US, "%.0f%%", value.coerceIn(0.0, 100.0))

/** 大数字用千分位，额度动辄上百万，看不清位数容易误判。 */
internal fun longNumber(value: Double): String =
    String.format(Locale.US, "%,.0f", value)

/** 时间戳（毫秒）转「今天 14:30」这样的短文本。 */
internal fun resetTime(epochMillis: Double): String {
    val time = java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return time.format(java.util.Date(epochMillis.toLong()))
}

/** 金额型的紧张程度：低于 10 元算快见底。 */
internal fun toneOfAmount(amount: Double?): BalanceTone = when {
    amount == null -> BalanceTone.Normal
    amount <= 0 -> BalanceTone.Empty
    amount < 10 -> BalanceTone.Low
    else -> BalanceTone.Normal
}

/** 百分比型的紧张程度：低于 10% 算快见底。 */
internal fun toneOfPercent(percent: Double): BalanceTone = when {
    percent <= 0 -> BalanceTone.Empty
    percent <= 10 -> BalanceTone.Low
    else -> BalanceTone.Normal
}
