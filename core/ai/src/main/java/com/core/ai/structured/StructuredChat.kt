package com.core.ai.structured

import com.core.ai.entity.IAI
import com.core.ai.model.ChatMessage

/** 默认尝试次数：1 次原始请求 + 1 次带纠正提示的重试。 */
const val DEFAULT_STRUCTURED_ATTEMPTS = 2

/** 重试时把上一次的回复原样带回去，让模型看到自己错在哪。太长的截断，避免上下文被撑爆。 */
private const val MAX_ECHO_LENGTH = 600

/** 解析失败。带上原因，重试时会把原因告诉模型，比单纯「再试一次」有效得多。 */
class StructuredParseException(message: String) : Exception(message)

/**
 * 让模型做一次**结构化决策**并返回可用的对象。
 *
 * 与 `chat()` 的区别不只是「要 JSON」，而是把失败处理收进了一条确定的路径：
 *
 * 1. 把 [StructuredSpec.instruction] 贴到 system 末尾，字段含义写清楚；
 * 2. 支持 `response_format` 的厂商顺带开启 JSON 模式，不支持的（星火、Gemini）自动降级；
 * 3. 回复不干净也没关系——括号扫描会把 JSON 从散文里抠出来（见 [StructuredJson]）；
 * 4. 解析失败或 [StructuredSpec.validate] 不通过，带上原因重试；
 * 5. 仍然失败就返回 [fallback]，并标记 [StructuredOutcome.usedFallback]。
 *
 * **异常不在这里吞掉。** 网络断了、Key 没配这类问题会原样抛给调用方。理由：
 * 那是传输层的问题，重试几十毫秒后大概率还是失败；而吞掉之后「网络断了」会变成
 * 「AI 开始乱投票」，排查成本高得多。格式化问题才值得重试——模型确实可能第二次就对了。
 *
 * @param fallback 兜底值。必须是**合法动作**（从存活玩家/可选目标里挑），
 *   这样即使模型完全失灵，游戏规则也不会被破坏。
 */
suspend fun <T> IAI.decide(
    messages: List<ChatMessage>,
    model: String,
    spec: StructuredSpec<T>,
    fallback: () -> T,
    systemPrompt: String? = null,
    enableThinking: Boolean = false,
    maxAttempts: Int = DEFAULT_STRUCTURED_ATTEMPTS,
): StructuredOutcome<T> {
    require(maxAttempts >= 1) { "至少要有一次尝试" }

    val system = buildOutputRequirement(systemPrompt, spec.instruction)
    var conversation = messages
    var lastError: String? = null

    for (attempt in 1..maxAttempts) {
        val raw = chat(
            messages = conversation,
            model = model,
            systemPrompt = system,
            enableThinking = enableThinking,
            jsonMode = supportsJsonMode,
        )

        val parsed = parseReply(raw, spec)
        parsed.fold(
            onSuccess = { return StructuredOutcome(it, attempt, usedFallback = false) },
            onFailure = { error ->
                lastError = error.message ?: "输出格式不符合要求"
                conversation = conversation + buildRetryMessages(raw, lastError!!)
            },
        )
    }

    return StructuredOutcome(
        value = fallback(),
        attempts = maxAttempts,
        usedFallback = true,
        lastError = lastError,
    )
}

/**
 * 把输出要求拼到 system prompt 末尾。
 *
 * 定死的三句话是有用的：「只输出这个 JSON 对象」压制前言，「不要代码块」省掉一次括号扫描的
 * 干扰，「不要解释」是因为解释会挤占 token 而且容易被当成正文展示给用户。
 */
internal fun buildOutputRequirement(systemPrompt: String?, instruction: String): String =
    buildString {
        if (!systemPrompt.isNullOrBlank()) {
            append(systemPrompt.trim())
            append("\n\n")
        }
        append("【输出要求】\n")
        append(instruction.trim())
        append("\n只输出这一个 JSON 对象，不要 markdown 代码块，不要解释，不要前后缀。")
    }

/** 重试时追加的消息：上一次的原始回复（截断）+ 失败原因。 */
internal fun buildRetryMessages(rawReply: String, reason: String): List<ChatMessage> = listOf(
    ChatMessage.assistant(rawReply.take(MAX_ECHO_LENGTH)),
    ChatMessage.user("上一次输出无法使用：$reason。请重新输出，且只输出符合要求的 JSON 对象，不要代码块、不要解释。"),
)

/**
 * 从回复里解析出决策。
 *
 * 逐个候选对象尝试：能反序列化**并且**通过校验才算成功。
 * 失败时带具体原因返回，这个原因会写进重试提示——「字段取值不在允许范围」
 * 和「没找到 JSON」对模型来说是完全不同的两种纠正。
 */
internal fun <T> parseReply(raw: String, spec: StructuredSpec<T>): Result<T> {
    val candidates = StructuredJson.balancedObjects(raw)
    if (candidates.isEmpty()) {
        return Result.failure(StructuredParseException("没有找到 JSON 对象"))
    }

    var lastProblem = "JSON 无法解析"
    candidates.forEach { candidate ->
        val decoded = runCatching { StructuredJson.json.decodeFromString(spec.serializer, candidate) }
        decoded.onSuccess { value ->
            if (spec.validate(value)) return Result.success(value)
            lastProblem = "JSON 字段取值不在允许范围内"
        }.onFailure { error ->
            lastProblem = "JSON 语法不合法：${error.message?.take(120) ?: "未知原因"}"
        }
    }
    return Result.failure(StructuredParseException(lastProblem))
}
