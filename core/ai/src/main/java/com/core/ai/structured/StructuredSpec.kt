package com.core.ai.structured

import kotlinx.serialization.KSerializer

/**
 * 一次「结构化决策」的规格。
 *
 * 狼人杀里的投票、狼刀、女巫用药都是**硬决策**：解析错一次，游戏结果就错，玩家当场能看出来。
 * 靠正则从自然语言里抠座位号（「我投3号，虽然5号更可疑」会被最后一句话带跑）不够用，
 * 所以要求模型直接输出 JSON，再用 [serializer] 反序列化。
 *
 * [instruction] 会把字段含义写给人看的那套说清楚，例如：
 * ```
 * 输出 {"target": 座位号}，target 必须是你要投的存活玩家座位号。
 * ```
 * 这段文字会被贴到 system prompt 末尾，所以写得越具体，模型跑偏越少。
 *
 * [validate] 是最后一道闸：JSON 能解析不等于决策合法（可能投了个已经出局的人）。
 * 校验不通过会和解析失败走同一条重试路径。
 */
data class StructuredSpec<T>(
    val instruction: String,
    val serializer: KSerializer<T>,
    val validate: (T) -> Boolean = { true },
)

/**
 * 决策结果。
 *
 * [usedFallback] 为 true 表示模型几次都没给出可用输出，[value] 是调用方给的兜底值。
 * 这个标志只用于日志和统计——**不要告诉玩家**。玩家看到「AI 思考失败，随机投了一票」，
 * 这局游戏的沉浸感就没了；而兜底值本身是从合法选项里选的，游戏可以照常进行。
 */
data class StructuredOutcome<T>(
    val value: T,
    val attempts: Int,
    val usedFallback: Boolean,
    val lastError: String? = null,
)
