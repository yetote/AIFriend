package com.feature.werewolf.engine

import com.feature.werewolf.game.Phase

/**
 * 公开台账：把「谁说过什么可核对的话」从发言原文里摘出来，跨天累积。
 *
 * ## 为什么要有这个东西
 *
 * 发言原文只喂今天 + 昨天（见 `WerewolfDirector.context`），公开播报只有事件、
 * 没有内容。所以到第 3 天，第 1 天谁跳的预言家、报了什么验人、谁说了投 A 却投了 B，
 * 在上下文里一个字都不剩——模型只剩「存活名单 + 几条出局播报」，只能凭印象编，
 * 表现出来就是「没有逻辑、瞎玩」。
 *
 * 台账补的就是这一段记忆。它摘的是**事实**，不是判断：
 * 谁声称什么身份、谁报了什么验人、谁说了投谁而实际投了谁。谁真谁假仍然交给模型。
 *
 * ## 信息隔离
 *
 * 只读 `audience == null` 的公开发言，以及公开的唱票卡。夜聊（[MessageKind.WOLF_CHAT]）、
 * 内心活动（[MessageKind.NIGHT_ACT] / [MessageKind.VOTE]）一律不进来——那些本来就是
 * 各自知道的，摘进台账等于泄底。这也意味着这里的每一条，场上任何人都能自己写出来。
 */
object PublicLedger {

    /** 一条「我是什么身份」。同一座位同一身份只留第一次说的那次。 */
    internal data class Claim(val day: Int, val seat: Int, val role: String)

    /** 一条报出来的验人。同一座位对同一目标只留第一次（后面的改口多半是复述）。 */
    internal data class Check(val day: Int, val seat: Int, val target: Int, val verdict: String)

    /** 一轮投票里某个座位「说投谁 / 实际投谁」。 */
    internal data class VoteLine(val day: Int, val seat: Int, val said: Int?, val actual: Int?)

    internal data class Ledger(
        val claims: List<Claim> = emptyList(),
        val checks: List<Check> = emptyList(),
        val votes: List<VoteLine> = emptyList(),
    ) {
        val isEmpty: Boolean get() = claims.isEmpty() && checks.isEmpty() && votes.isEmpty()
    }

    /** 拼成一段可直接注入 system 的文字。没有可摘的东西时返回空串（不占位）。 */
    fun of(table: WerewolfTable): String = render(collect(table))

    // ---------------------------------------------------------------------
    // 摘取
    // ---------------------------------------------------------------------

    internal fun collect(table: WerewolfTable): Ledger {
        val claims = LinkedHashMap<Pair<Int, String>, Claim>()
        val checks = LinkedHashMap<Pair<Int, Int>, Check>()
        val votes = mutableListOf<VoteLine>()
        // 每个座位当天最后说出口的票向。投票那一刻读它，就是「话」；唱票卡是「票」。
        val promised = HashMap<Pair<Int, Int>, Int>()

        table.transcript.forEach { message ->
            when (message.kind) {
                MessageKind.SPEECH -> {
                    if (!message.isPublic) return@forEach
                    val seat = message.seat ?: return@forEach
                    val text = message.text

                    roleClaims(text).forEach { role ->
                        claims.getOrPut(seat to role) { Claim(message.day, seat, role) }
                    }
                    checkClaims(text).forEach { (target, verdict) ->
                        checks.getOrPut(seat to target) { Check(message.day, seat, target, verdict) }
                    }
                    lastPromise(text)?.let { promised[message.day to seat] = it }
                }

                MessageKind.BALLOT -> {
                    // 只对账放逐票：警长投票时大家说的是「我选 X 号当警长」，
                    // 和「我这一票投 X 号」不是同一个句式，混在一起会一直误报。
                    if (message.phase != Phase.DAY_VOTE) return@forEach
                    val card = message.ballot ?: return@forEach
                    card.ballots.forEach { ballot ->
                        votes += VoteLine(
                            day = message.day,
                            seat = ballot.voterSeat,
                            said = promised[message.day to ballot.voterSeat],
                            actual = ballot.targetSeat,
                        )
                    }
                }

                else -> Unit
            }
        }
        return Ledger(claims.values.toList(), checks.values.toList(), votes)
    }

    private val ROLE_WORDS = "(预言家|女巫|猎人|白痴|守卫)"

    /**
     * 「我是预言家」「我跳预言家」「我认女巫」。
     *
     * 不认「我是好人」「我是民」：那两句谁都说得出来，摘进台账只是噪音。
     * 也不认模型自己编的神职名——台账只服务于「谁和谁在对跳」这一个判断。
     */
    private val ROLE_CLAIM = Regex("""(?:我是|我跳|我认|我就是|我这边是)\s*(?:那个\s*)?$ROLE_WORDS""")

    /** 「我预言家，昨晚验了……」——省掉「是/跳」的口语说法。 */
    private val ROLE_CLAIM_BARE = Regex("""我$ROLE_WORDS[，,。：:]""")

    private fun roleClaims(text: String): List<String> {
        val found = mutableListOf<String>()
        ROLE_CLAIM.findAll(text).forEach { if (!isHypothetical(text, it.range.first)) found += it.groupValues[1] }
        ROLE_CLAIM_BARE.findAll(text).forEach { if (!isHypothetical(text, it.range.first)) found += it.groupValues[1] }
        return found.distinct()
    }

    /**
     * 前面是不是假设句。
     *
     * 「我是预言家」是声称，「**如果**我是预言家」是打比方——记进台账会让全场以为
     * 他在对跳，一次误记就足以把好人推成焦点。只往前看三个字：中文的假设连词都很短，
     * 再往前的词已经不修饰这一句了。
     */
    private fun isHypothetical(text: String, index: Int): Boolean {
        val before = text.substring(maxOf(0, index - 3), index)
        return HYPOTHETICAL.any { before.contains(it) }
    }

    private val HYPOTHETICAL = listOf("如果", "假如", "假设", "要是", "万一", "若是", "倘若")

    /**
     * 「验了 7 号是好人」「查 3 号金水」「我验的 5 号是狼」。
     *
     * 中间允许几个字（「，结果是」），但不许跨句号：跨句之后的主语已经不是验人了，
     * 「我验了 3 号。他是狼」这种两句话的写法，后半句本来就不该算进同一条验人。
     */
    private val CHECK = Regex(
        """[验查]\s*(?:了|过|的)?\s*(\d{1,2})\s*号?[^。！？\n]{0,10}?(好人|狼人|金水|查杀)"""
    )

    /** 「3 号是金水」「5 号查杀」——先报结果再补验人顺序的说法。 */
    private val CHECK_BARE = Regex("""(\d{1,2})\s*号(?:是|为)?\s*(金水|查杀)""")

    private fun checkClaims(text: String): List<Pair<Int, String>> {
        val found = mutableListOf<Pair<Int, String>>()
        CHECK.findAll(text).forEach { match ->
            match.groupValues[1].toIntOrNull()?.let { found += it to normalizeVerdict(match.groupValues[2]) }
        }
        CHECK_BARE.findAll(text).forEach { match ->
            match.groupValues[1].toIntOrNull()?.let { found += it to normalizeVerdict(match.groupValues[2]) }
        }
        return found.distinct()
    }

    private fun normalizeVerdict(word: String): String = when (word) {
        "金水", "好人" -> "好人"
        "查杀", "狼人" -> "狼人"
        else -> word
    }

    /** 「我这一票投 5 号」「我投 5 号」「投给 5 号」。 */
    private val PROMISE = Regex("""投(?:给|到)?\s*(\d{1,2})\s*号""")

    /**
     * 一段发言里最后提到的票向。
     *
     * 取最后一个而不是第一个：发言骨架要求把票向放在最后一句，前面出现的
     * 「我本来想投 3 号」不是承诺。前面带否定的（「别投 3 号」「不能投 3 号」）直接跳过——
     * 这类句子恰恰是在劝别人别投，当成自己的票向会做出反向对账。
     */
    private fun lastPromise(text: String): Int? {
        var last: Int? = null
        PROMISE.findAll(text).forEach { match ->
            val before = text.substring(maxOf(0, match.range.first - 2), match.range.first)
            if (before.any { it in "别不没勿" }) return@forEach
            match.groupValues[1].toIntOrNull()?.let { last = it }
        }
        return last
    }

    // ---------------------------------------------------------------------
    // 渲染
    // ---------------------------------------------------------------------

    internal fun render(ledger: Ledger): String {
        if (ledger.isEmpty) return ""
        return buildString {
            append("【公开台账】\n")
            append("下面这些是从大家说过的话里摘出来的，跨天累积——原文说久了会从上下文里淡出，这里不会。\n")
            if (ledger.claims.isNotEmpty()) {
                append("· 声称身份：")
                append(
                    ledger.claims.joinToString("；") { "${it.seat} 号自称${it.role}（第 ${it.day} 天）" }
                )
                append("\n")
                // 「同一身份几个版本」是狼人杀最核心的对立关系，数一遍比让模型自己数可靠。
                ledger.claims.groupBy { it.role }.filter { it.value.size > 1 }.forEach { (role, list) ->
                    append("  —— ${role}有 ${list.size} 个人声称：")
                    append(list.joinToString("、") { "${it.seat} 号" })
                    append("，这几个人里至少有一个在说谎。\n")
                }
            }
            if (ledger.checks.isNotEmpty()) {
                append("· 报出的验人：")
                append(
                    ledger.checks.joinToString("；") {
                        "${it.seat} 号称第 ${it.day} 天验 ${it.target} 号是${it.verdict}"
                    }
                )
                append("\n")
            }
            if (ledger.votes.isNotEmpty()) {
                ledger.votes.groupBy { it.day }.toSortedMap().forEach { (day, lines) ->
                    append("· 第 $day 天票型对账：\n")
                    lines.forEach { line ->
                        append("  · ").append(line.seat).append(" 号")
                        append(if (line.said != null) "说投 ${line.said} 号" else "投票前没说票向")
                        append("，实际")
                        append(if (line.actual != null) "投了 ${line.actual} 号" else "弃票")
                        if (line.said != null && line.said != line.actual) {
                            append("  ← 话和票对不上")
                        }
                        append("\n")
                    }
                }
            }
        }
    }
}
