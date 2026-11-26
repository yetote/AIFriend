package com.feature.werewolf.engine

import com.core.ai.entity.IAI
import com.core.ai.model.ChatMessage
import com.core.ai.model.TokenUsage
import com.core.ai.structured.StructuredSpec
import com.core.ai.structured.decide
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfGame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

/**
 * 负责旁听公开发言、客观整理公开台账的「裁判 / GM」模型。
 *
 * 只借用所选模型的推理能力，不注入任何好友性格设定，也不参与场上发言或投票。
 */
data class GmAgent(
    val ai: IAI,
    val model: String,
    val label: String = model,
)

@Serializable
data class SpeechCheckFact(
    val target: Int,
    val verdict: String,
)

@Serializable
data class SpeechWitchFact(
    val action: String,
    val target: Int,
)

/**
 * 裁判 / GM 从**单条公开发言**中抽取出的结构化台账事实。
 *
 * 随 [WerewolfMessage.facts] 一起挂在消息上并落库：
 * - 回放与 TTS 朗读进度截断仍按消息下标同步过滤，保证零剧透；
 * - 任一字段出现越界或违背物理约束（如自己验自己）都会在 [PublicLedger] 再做一道确定性校验。
 */
@Serializable
data class SpeechLedgerFacts(
    val claimRole: String? = null,
    val retractedSeer: Boolean = false,
    val checks: List<SpeechCheckFact> = emptyList(),
    val badgeFlow: List<Int> = emptyList(),
    val witchReports: List<SpeechWitchFact> = emptyList(),
    val voteTarget: Int? = null,
    val conditionalVoteTargets: List<Int> = emptyList(),
)

/**
 * 裁判 / GM 对单条公开发言的增量结构化抽取器。
 *
 * 定位是「语义理解增强」，不是替换引擎：
 * - 实际投票结果（[MessageKind.BALLOT]）与正式退水（[GameEvent.SheriffWithdrawn]）
 *   仍以引擎硬事件为唯一权威来源；
 * - 超时、报错或未配置时一律安全返回 `null`，由 [PublicLedger] 无缝回退到本地正则。
 */
internal object GmJudge {

    private const val GM_TIMEOUT_MS = 8_000L
    private const val MAX_SPEECH_CHARS = 1_200
    private val ALLOWED_ROLES = setOf("预言家", "女巫", "猎人", "白痴", "守卫", "熊")

    @Serializable
    internal data class GmExtractDto(
        val claimRole: String? = null,
        val retractedSeer: Boolean = false,
        val checks: List<SpeechCheckFact> = emptyList(),
        val badgeFlow: List<Int> = emptyList(),
        val witchReports: List<SpeechWitchFact> = emptyList(),
        val voteTarget: Int? = null,
        val conditionalVoteTargets: List<Int> = emptyList(),
    )

    internal data class Outcome(
        val facts: SpeechLedgerFacts? = null,
        val usage: TokenUsage = TokenUsage.EMPTY,
    )

    suspend fun judgeSpeech(
        gm: GmAgent,
        game: WerewolfGame,
        speakerSeat: Int,
        phase: Phase,
        speechText: String,
    ): Outcome {
        if (speechText.isBlank()) return Outcome()
        val maxSeat = game.players.maxOfOrNull { it.seat } ?: return Outcome()
        val validSeats = game.players.map { it.seat }.toSet()
        val prompt = buildPrompt(
            speakerSeat = speakerSeat,
            day = game.day,
            phaseName = phase.displayName,
            maxSeat = maxSeat,
            includeVote = phase == Phase.DAY_DISCUSSION,
        )
        val userMsg = listOf(
            ChatMessage.user(
                "${speakerSeat} 号发言原文：「${speechText.take(MAX_SPEECH_CHARS)}」"
            )
        )
        val spec = StructuredSpec(
            instruction = prompt,
            serializer = GmExtractDto.serializer(),
            validate = { true },
        )
        return try {
            val outcome = withTimeout(GM_TIMEOUT_MS) {
                gm.ai.decide(
                    messages = userMsg,
                    model = gm.model,
                    spec = spec,
                    fallback = { GmExtractDto() },
                    systemPrompt = "你是狼人杀对局的客观裁判（GM），只负责把公开发言里的可核对事实严格转成 JSON。",
                    enableThinking = false,
                    maxAttempts = 1,
                )
            }
            if (outcome.usedFallback) {
                Outcome(facts = null, usage = outcome.usage)
            } else {
                Outcome(
                    facts = sanitize(outcome.value, speakerSeat, phase, validSeats),
                    usage = outcome.usage,
                )
            }
        } catch (cancelled: CancellationException) {
            if (!currentCoroutineContext().isActive) throw cancelled
            Outcome()
        } catch (_: Throwable) {
            Outcome()
        }
    }

    internal fun sanitize(
        dto: GmExtractDto,
        speakerSeat: Int,
        phase: Phase,
        validSeats: Set<Int>,
    ): SpeechLedgerFacts {
        val role = dto.claimRole?.trim()?.let { raw ->
            ALLOWED_ROLES.firstOrNull { raw == it || raw.contains(it) }
        }
        val checks = dto.checks
            .mapNotNull { check ->
                if (check.target !in validSeats || check.target == speakerSeat) return@mapNotNull null
                val verdict = normalizeVerdict(check.verdict) ?: return@mapNotNull null
                SpeechCheckFact(target = check.target, verdict = verdict)
            }
            .distinct()
        val badgeFlow = dto.badgeFlow
            .filter { it in validSeats && it != speakerSeat }
            .distinct()
            .take(3)
        val witchReports = dto.witchReports
            .mapNotNull { report ->
                if (report.target !in validSeats) return@mapNotNull null
                val action = normalizeWitchAction(report.action) ?: return@mapNotNull null
                if (action == "毒" && report.target == speakerSeat) return@mapNotNull null
                SpeechWitchFact(action = action, target = report.target)
            }
            .distinct()
        val voteTarget = if (phase == Phase.DAY_DISCUSSION) {
            dto.voteTarget?.takeIf { it in validSeats && it != speakerSeat }
        } else {
            null
        }
        val conditionalVotes = if (phase == Phase.DAY_DISCUSSION) {
            dto.conditionalVoteTargets
                .filter { it in validSeats && it != speakerSeat && it != voteTarget }
                .distinct()
        } else {
            emptyList()
        }
        return SpeechLedgerFacts(
            claimRole = role,
            retractedSeer = dto.retractedSeer,
            checks = checks,
            badgeFlow = badgeFlow,
            witchReports = witchReports,
            voteTarget = voteTarget,
            conditionalVoteTargets = conditionalVotes,
        )
    }

    private fun normalizeVerdict(raw: String): String? = when (raw.trim()) {
        "好人", "金水" -> "好人"
        "狼人", "狼", "查杀" -> "狼人"
        else -> null
    }

    private fun normalizeWitchAction(raw: String): String? = when (raw.trim()) {
        "救", "捞", "银水" -> "救"
        "毒", "泼" -> "毒"
        else -> null
    }

    private fun buildPrompt(
        speakerSeat: Int,
        day: Int,
        phaseName: String,
        maxSeat: Int,
        includeVote: Boolean,
    ): String = buildString {
        append("说话人：${speakerSeat} 号；当前阶段：第 ${day} 天 · ${phaseName}；有效座位号：1~${maxSeat}。\n")
        append("只输出一个 JSON 对象，字段如下：\n")
        append("{\"claimRole\": 字符串或null, \"retractedSeer\": 布尔, \"checks\": [{\"target\": 座位号, \"verdict\": \"好人\"|\"狼人\"}], ")
        append("\"badgeFlow\": [座位号], \"witchReports\": [{\"action\": \"救\"|\"毒\", \"target\": 座位号}], ")
        append("\"voteTarget\": 座位号或null, \"conditionalVoteTargets\": [座位号]}\n")
        append("严格规则（违背即判错）：\n")
        append("1. 只记【${speakerSeat}号本人】用第一人称正式宣告的事实；转述他人、引用原话、质疑他人、假设句（如果/假如/要是）、未来计划（打算/今晚/明天再看）一律不要填！\n")
        append("2. claimRole：只有本人正式认领身份时填（\"预言家\"/\"女巫\"/\"猎人\"/\"白痴\"/\"守卫\"/\"熊\"），否则 null。\n")
        append("3. retractedSeer：只有本人承认不是预言家/警上只是诈身份/收回预言家才填 true。\n")
        append("4. checks：只记本人起跳预言家报出的已验完结果（金水记\"好人\"，查杀记\"狼人\"）；计划去验或转述他人验人不填。\n")
        append("5. badgeFlow：本人最新声明的警徽流座位顺序（最多3个），转述他人不填。\n")
        append("6. witchReports：只记本人以女巫身份报出的已用过的药；否定句（没救/没毒）或计划/条件毒绝不填。\n")
        if (includeVote) {
            append("7. voteTarget 与 conditionalVoteTargets（极重要）：\n")
            append("   · 明确无条件说主投谁（如「我这票投 X 号」「归票 X 号」），填 voteTarget=X；\n")
            append("   · 若含条件兜底（如「若 A 改口我改投 B」「主投 A，如果出现情况转投 B」），主投放 voteTarget，条件备选放 conditionalVoteTargets；\n")
            append("   · 若整句只有「如果…就投 B」而无无条件主投，voteTarget 必须填 null，把 B 放进 conditionalVoteTargets。")
        } else {
            append("7. 本阶段不是白天讨论投票前表态，voteTarget 填 null，conditionalVoteTargets 填 []。")
        }
    }
}
