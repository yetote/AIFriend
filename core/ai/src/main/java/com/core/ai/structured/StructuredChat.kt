package com.core.ai.structured

import com.core.ai.entity.IAI
import com.core.ai.isTimeoutLike
import com.core.ai.model.ChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

/** 默认尝试次数：1 次原始请求 + 1 次带纠正提示的重试。 */
const val DEFAULT_STRUCTURED_ATTEMPTS = 2

/** 重试时把上一次的回复原样带回去，让模型看到自己错在哪。太长的截断，避免上下文被撑爆。 */
private const val MAX_ECHO_LENGTH = 600

/** 超时后重试前等多久。给服务端一点喘息，别紧接着再打一次。 */
private const val RETRY_BACKOFF_MS = 3_000L

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
 * @param onReasoning 思维链回调。传了它就改走**流式**请求：思考过程边生成边回调，
 *   决策照样在收完之后才解析。不传则走原来的**一次性**请求——没有思维链可显示时，
 *   多开一条流只是多一处可能出问题的地方（流式对 `stream + json_object` 的支持，
 *   各家兼容层并不整齐）。
 */
suspend fun <T> IAI.decide(
    messages: List<ChatMessage>,
    model: String,
    spec: StructuredSpec<T>,
    fallback: () -> T,
    systemPrompt: String? = null,
    enableThinking: Boolean = false,
    maxAttempts: Int = DEFAULT_STRUCTURED_ATTEMPTS,
    onReasoning: (suspend (String) -> Unit)? = null,
): StructuredOutcome<T> {
    require(maxAttempts >= 1) { "至少要有一次尝试" }

    val system = buildOutputRequirement(systemPrompt, spec.instruction)
    var conversation = messages
    var lastError: String? = null
    // 重试时可以换一种更省时间的打法，见下面的超时分支
    var thinking = enableThinking
    // 只有最后一次尝试的思维链有意义：前面那几次的答案已经被判为不可用
    var reasoning = ""
    // 只要有一次等待超时，就算这次决策「降级过」——即使重试后拿到了正常答案。
    // 这个标志往外传，让调用方（狼人杀）关掉该座位本局的深度思考：
    // 超时多半就是思考吃满了额度，下一轮再开只会再撞一次。
    var timedOut = false

    for (attempt in 1..maxAttempts) {
        val reply = try {
            askOnce(
                messages = conversation,
                model = model,
                system = system,
                thinking = thinking,
                jsonMode = supportsJsonMode,
                onReasoning = onReasoning,
            )
        } catch (error: Throwable) {
            // 超时值得再试一次：模型排队、网抖、客户端长连接被回收都可能，
            // 而 Key 错误、参数错误重试多少次都一样。
            if (error.isTimeoutLike()) {
                timedOut = true
                if (attempt < maxAttempts) {
                    // **重试时关掉深度思考。** 超时的头号原因是思考把等待额度吃光了，
                    // 用一模一样的参数再打一次，只会把等待时间翻倍，然后照样失败——
                    // 用户等十几分钟拿到一句「网络异常」，比直接降级更糟。
                    // 关掉之后这次请求通常几十秒就回来，决策质量差一点，但这一轮能打完。
                    thinking = false
                    delay(RETRY_BACKOFF_MS)
                    continue
                }
                // 超时是「这个座位这一次没答上来」，不是「整局废了」。
                // 兜底值在规则上合法（随机投一个候选、随机刀一个好人），
                // 这一局能继续打完；把异常抛出去会让整个阶段卡死，
                // 用户点「继续」还会再撞一次同样的超时。
                return StructuredOutcome(
                    value = fallback(),
                    attempts = attempt,
                    usedFallback = true,
                    lastError = "响应超时，已用兜底选择",
                    timedOut = true,
                )
            }
            throw error
        }

        reasoning = reply.reasoning
        val raw = reply.content
        val parsed = parseReply(raw, spec)
        parsed.fold(
            onSuccess = {
                return StructuredOutcome(
                    it, attempt, usedFallback = false, reasoning = reasoning, timedOut = timedOut,
                )
            },
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
        reasoning = reasoning,
        timedOut = timedOut,
    )
}

/** 一次请求的完整答复：正文 + 思维链。 */
private data class Reply(val content: String, val reasoning: String)

/**
 * 走一次请求。
 *
 * [onReasoning] 为空时用 `chat()`：一次拿全，没有任何中间态。
 * 给了它就用 `chatStream()`：思维链边到边回调，正文照样攒齐了再交给解析器，
 * 因为决策的价值全在解析完的那个对象上，半截 JSON 既不能用也不能显示。
 */
private suspend fun IAI.askOnce(
    messages: List<ChatMessage>,
    model: String,
    system: String,
    thinking: Boolean,
    jsonMode: Boolean,
    onReasoning: (suspend (String) -> Unit)?,
): Reply {
    if (onReasoning == null) {
        return Reply(
            content = chat(
                messages = messages,
                model = model,
                systemPrompt = system,
                enableThinking = thinking,
                jsonMode = jsonMode,
            ),
            reasoning = "",
        )
    }

    val content = StringBuilder()
    val chain = StringBuilder()
    chatStream(
        messages = messages,
        model = model,
        systemPrompt = system,
        enableThinking = thinking,
        jsonMode = jsonMode,
    ).collect { delta ->
        content.append(delta.content)
        if (delta.reasoning.isNotEmpty()) {
            chain.append(delta.reasoning)
            // 回调的是**累计**后的全量，不是增量：显示方只做整体替换，
            // 断线重连或重试时也不会拼出重复的半句
            onReasoning(chain.toString())
        }
    }
    return Reply(content = content.toString(), reasoning = chain.toString())
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
