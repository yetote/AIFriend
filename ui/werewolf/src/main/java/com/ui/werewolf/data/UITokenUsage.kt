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

    /** 气泡/列表里的一小段标签，例如「约 1.2k tok」。 */
    val shortLabel: String get() = "${if (estimated) "约" else ""}${compact(total)} tok"

    /** 汇总卡里的完整标签。 */
    val fullLabel: String get() = "${if (estimated) "约 " else ""}${compact(total)} tokens"

    private fun compact(value: Int): String = when {
        value >= 1_000 -> "${value / 100 / 10.0}k".removeSuffix(".0")
        else -> value.toString()
    }
}

/** 复盘汇总里的一行：谁、多少 token。 */
data class UITokenSeatTotal(
    val seat: Int,
    val name: String,
    val usage: UITokenUsage,
)
