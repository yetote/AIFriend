package com.ui.werewolf.data

/**
 * 展示层的 token 用量。
 *
 * 不直接用 core:ai 的 TokenUsage：ui:werewolf 按约定不依赖 core:ai，
 * 而且界面只需要「显示成什么」，不需要参与计算。
 */
data class UITokenUsage(
    val input: Int = 0,
    val output: Int = 0,
    val estimated: Boolean = false,
) {
    val total: Int get() = input + output

    operator fun plus(other: UITokenUsage): UITokenUsage = UITokenUsage(
        input = input + other.input,
        output = output + other.output,
        estimated = estimated || other.estimated,
    )

    /**
     * 气泡、逐条、每天小计用的一小段：`入5.2k 出0.8k`。
     *
     * 分开列而不是给总数：入是这一轮重发的上下文（提示词＋局势），出是它实际写了多少。
     * 合并成一个数，看到 6k 只会以为「思考真长」，其实大半是提示词。
     */
    val shortLabel: String get() = "${if (estimated) "约 " else ""}入${compact(input)} 出${compact(output)}"

    /** 汇总卡里的完整标签：`入 120k / 出 24k`。 */
    val fullLabel: String get() = "${if (estimated) "约 " else ""}入 ${compact(input)} / 出 ${compact(output)}"

    private fun compact(value: Int): String = when {
        value < 1_000 -> value.toString()
        value < 100_000 -> "${(value / 100) / 10.0}".removeSuffix(".0") + "k"
        value < 1_000_000 -> "${value / 1_000}k"
        else -> "${(value / 100_000) / 10.0}".removeSuffix(".0") + "M"
    }
}

/** 复盘汇总里的一行：谁、多少 token。`seat <= 0` 表示非座位角色（例如裁判 / GM）。 */
data class UITokenSeatTotal(
    val seat: Int,
    val name: String,
    val usage: UITokenUsage,
) {
    val displayLabel: String
        get() = if (seat > 0) "$seat 号 $name".trim() else name.ifBlank { "裁判 / GM" }
}
