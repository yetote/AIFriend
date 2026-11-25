package com.feature.room.data

import com.core.ai.model.estimateTokens
import com.feature.room.model.RoomMessage
import com.feature.room.model.RoomSender

/**
 * 聊天室长对话「共享纪要（滚动摘要）」纯逻辑。
 *
 * 职责边界：
 * - 只挑**公开的**用户与 AI 发言进共享纪要（定向 `@` 私有轮次与系统通知不入纪要，防泄密）；
 * - 始终保留最近 [KEEP_RECENT_MESSAGES] 条公开消息作为原文活跃窗口，只把更早的溢出处压成纪要；
 * - 只有当待摘要片段达到条数或 token 阈值时才触发（手动刷新可放宽到至少 2 条），避免每聊一句就多发一次请求。
 */
object RoomSummary {

    /** 活跃窗口保留的最近公开消息条数，不参与摘要，保持近轮对话细节完整。 */
    const val KEEP_RECENT_MESSAGES = 8

    /** 自动触发摘要的最少新增旧消息条数。 */
    const val MIN_BATCH_MESSAGES = 6

    /** 自动触发摘要的最少新增估算 token（条数未满但单条很长时也触发）。 */
    const val MIN_BATCH_TOKENS = 800

    /** 共享纪要正文上限，避免纪要本身反向撑爆 system prompt。 */
    const val MAX_SUMMARY_CHARS = 900

    data class Batch(
        val messages: List<RoomMessage>,
        val throughSeq: Long,
    )

    /**
     * 挑选下一批需要压进共享纪要的公开历史消息。
     *
     * @param force 为 true 时（用户手动点「立即更新」）只要有至少 2 条未摘要的较早公开消息就返回。
     */
    fun pickBatchToSummarize(
        messages: List<RoomMessage>,
        summaryUntilSeq: Long,
        force: Boolean = false,
    ): Batch? {
        val sorted = messages.sortedWith(compareBy({ it.createdAt }, { it.seq }))
        // 识别哪些消息属于定向 @ 的私有轮次：私聊轮次不能写进全员共享纪要
        val publicMessages = ArrayList<RoomMessage>(sorted.size)
        var privateRound = false
        for (msg in sorted) {
            when (msg.sender) {
                RoomSender.User -> {
                    privateRound = msg.targetIds != null
                    if (!privateRound) publicMessages += msg
                }

                is RoomSender.Ai -> {
                    if (!privateRound) publicMessages += msg
                }

                RoomSender.System -> Unit
            }
        }

        // 保留最近 KEEP_RECENT_MESSAGES 条原文，只对更早的未摘要消息做压缩
        val cutoffIndex = publicMessages.size - KEEP_RECENT_MESSAGES
        if (cutoffIndex <= 0) return null

        val candidates = publicMessages.subList(0, cutoffIndex)
            .filter { it.seq > summaryUntilSeq }
        if (candidates.isEmpty()) return null

        if (force) {
            if (candidates.size < 2) return null
        } else {
            val tokens = candidates.sumOf { estimateTokens(it.content) }
            if (candidates.size < MIN_BATCH_MESSAGES && tokens < MIN_BATCH_TOKENS) {
                return null
            }
        }
        return Batch(
            messages = candidates,
            throughSeq = candidates.maxOf { it.seq },
        )
    }

    /** 构造给模型生成/更新共享纪要的提示词正文。 */
    fun buildSummaryPrompt(
        roomName: String,
        roomPrompt: String,
        existingSummary: String,
        batch: Batch,
        friendNames: Map<String, String>,
    ): String = buildString {
        append("请为聊天室「").append(roomName).append("」更新一份简明、客观的共享纪要。\n")
        if (roomPrompt.isNotBlank()) {
            append("房间背景：").append(roomPrompt.trim().take(200)).append("\n")
        }
        append("\n")
        if (existingSummary.isNotBlank()) {
            append("【已有纪要】\n").append(existingSummary.trim()).append("\n\n")
            append("【新增的较早对话（请合并到已有纪要中）】\n")
        } else {
            append("【较早对话】\n")
        }
        batch.messages.forEach { msg ->
            val who = when (val sender = msg.sender) {
                RoomSender.User -> "用户"
                RoomSender.System -> "系统"
                is RoomSender.Ai -> friendNames[sender.friendId] ?: "成员"
            }
            val body = msg.content.replace('\n', ' ').trim().take(240)
            append("- ").append(who).append("：").append(body).append("\n")
        }
        append("\n要求：\n")
        append("1. 提取核心议题、重要前提/事实、已达成共识的结论，以及尚存的关键分歧。\n")
        append("2. 合并去重，不要流水账，不要编造对话里没有的内容。\n")
        append("3. 用 3–6 条简洁要点输出，总字数严格控制在 300 字以内，只输出纪要正文。")
    }

    /** 清洗并截断模型返回的纪要文本。 */
    fun sanitize(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("```markdown").removePrefix("```").removeSuffix("```")
            .trim()
        if (cleaned.isEmpty()) return ""
        return if (cleaned.length <= MAX_SUMMARY_CHARS) {
            cleaned
        } else {
            cleaned.take(MAX_SUMMARY_CHARS - 1).trimEnd() + "…"
        }
    }
}
