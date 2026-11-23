package com.core.ai.probe

import com.core.ai.entity.IAI
import com.core.ai.model.ChatMessage
import com.core.network.getReadableMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * 一次连通性检查的结果。
 *
 * 只回答一个问题：**这个模型现在能不能真的回一句话**。
 * 不判断它答得好不好、也不验证业务提示词——那是另一回事，而且做检测的目的是
 * 在开局前把「Key 过期」「模型名写错」「网络不通」这三类问题拦下来，
 * 它们的共同点是让整局一个 token 都跑不动。
 */
data class ApiProbeResult(
    val ok: Boolean,
    /** 从发出到收到响应的耗时。失败时是失败发生前等了多久。 */
    val elapsedMs: Long,
    /** 失败原因，已经翻成人话。成功时为 null。 */
    val message: String? = null,
    /**
     * 这次失败是不是「这家没有联网搜索」。
     *
     * 单独标出来是因为它和「连不上」要开完全不同的药：连不上要去查 Key，
     * 没有联网搜索只能换厂商或换打法。混成一句话，界面上的建议就会指错方向。
     */
    val searchUnsupported: Boolean = false,
)

/**
 * 探活用的那句话。
 *
 * 刻意要求「只回复 OK」：这次请求是要花钱的，能省则省；同时短回复也更快，
 * 超时阈值才压得住。模型不听话回了一长段也无所谓，能返回就说明链路是通的。
 */
private const val PROBE_PROMPT = "这是一次连通性检查，请只回复 OK，不要解释。"

/**
 * 默认超时。
 *
 * 比正常对话宽松（20 秒），因为要覆盖冷启动、深度思考、跨区线路这些情况；
 * 但必须有上限，否则一个卡死的厂商会把整个开局流程钉在配置页上——
 * 用户看到的是「点了开局没反应」，比明确报错更糟。
 */
const val DEFAULT_PROBE_TIMEOUT_MS = 20_000L

/**
 * 要求联网检索时的超时。
 *
 * 检索不是一次普通对话：厂商要先去搜、再把网页内容塞回上下文，多花的这十几秒
 * 不属于「模型慢」。沿用 20 秒会把所有支持联网的厂商都判成超时——
 * 那等于把搜索式整档钉死在开局页上。
 */
const val SEARCH_PROBE_TIMEOUT_MS = 45_000L

/**
 * 真发一次极短的对话请求，判断这个模型是否可用。
 *
 * 为什么不用拉模型列表来代替：
 * - 官方厂商在没配 Key 时会直接给一份兜底列表（见 `OfficialAI.requestModel`），
 *   那条路径不碰网络，永远「成功」，也就永远发现不了 Key 是错的；
 * - 聚合网关的列表和聊天可能用的是不同 Key 或不同权限。
 *
 * 所以只有真的发一次 chat 才算数。
 *
 * 取消要原样抛出去：用户退出页面时这些请求应该立刻停，不能被当成「检测失败」
 * 写进状态里——否则回来看到一片红点，其实是自己关掉的。
 */
suspend fun IAI.probe(
    model: String,
    timeoutMs: Long = DEFAULT_PROBE_TIMEOUT_MS,
    /**
     * 这一测要不要代表「搜索式」这一个前提。
     *
     * 同一个座位在两种前提下结论可能不同：厂商没有内置搜索时，普通探活是通的，
     * 搜索式探活必须是红的——它连得上，但连上了也不会去查。开局前要拦的正是这件事。
     */
    webSearch: Boolean = false,
): ApiProbeResult {
    val startedAt = System.currentTimeMillis()
    fun elapsed() = System.currentTimeMillis() - startedAt

    // 没有 Key 就不发：这一发必然 401，拿回来的「Key 无效」会把人引到错的方向，
    // 他要么以为自己填错了，要么以为 Key 过期了，实际上只是还没填
    if (!hasCredentials()) {
        return ApiProbeResult(
            ok = false,
            elapsedMs = 0,
            message = "还没有配置这个厂商的 API Key",
        )
    }

    // 搜索式 + 这家没有内置搜索：一个字节都不发。
    // 发出去要么被静默忽略（最糟：提示词写着可以查，模型把记忆当查证结果），
    // 要么 400 回来一条看不懂的报错。直接说清楚是哪件事不行，用户才知道该换人还是换风格
    if (webSearch && !supportsWebSearch) {
        return ApiProbeResult(
            ok = false,
            elapsedMs = 0,
            message = "「$brandName」的对话接口不支持联网搜索",
            searchUnsupported = true,
        )
    }

    val budget = if (webSearch) maxOf(timeoutMs, SEARCH_PROBE_TIMEOUT_MS) else timeoutMs

    return try {
        withTimeout(budget) {
            chat(
                messages = listOf(ChatMessage.user(PROBE_PROMPT)),
                model = model,
                systemPrompt = null,
                // 关掉深度思考：开着的话一个推理模型可能先想十几秒，
                // 白白吃掉超时额度，而这只是一次「你在不在」的确认
                enableThinking = false,
                // 搜索式这一测的意义就在于「带着搜索也能回话」，不带就白测
                webSearch = webSearch,
            )
        }
        // 空内容也算通：模型回空串是它自己的问题，但链路、Key、模型名都是对的，
        // 而这三样才是开局前要确认的
        ApiProbeResult(ok = true, elapsedMs = elapsed())
    } catch (timeout: TimeoutCancellationException) {
        // 必须先于 CancellationException 捕获：它是它的子类
        ApiProbeResult(
            ok = false,
            elapsedMs = elapsed(),
            message = "${budget / 1000} 秒没有响应",
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (t: Throwable) {
        ApiProbeResult(ok = false, elapsedMs = elapsed(), message = getReadableMessage(t))
    }
}
