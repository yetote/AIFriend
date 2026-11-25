package com.feature.werewolf.engine

import com.feature.werewolf.game.Ballot
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase

/**
 * 公开台账：把「谁说过什么可核对的话」从公开发言里摘出来，跨天累积。
 *
 * ## 设计底线
 *
 * 台账在模型眼里等同于「客观事实」，宁可少摘，绝不能误摘：
 * 1. **只摘本人第一人称声称**：引用、转述或质疑别人说过的身份与验人，绝不能记成发言人自己说的。
 * 2. **只有声称过预言家且未撤回者才记验人**：外置位讨论「6 号验 3 号查杀」「4 号金水」不是他自己验的。
 * 3. **退水与撤回要留痕**：警上诈身份后退水或改口声明不是预言家的，要在台账里显式标出。
 * 4. **信息隔离**：只读 `audience == null` 的公开发言、公开事件与公开唱票卡，夜聊与内心活动一律不进。
 */
object PublicLedger {

    /** 一条「我是什么身份」。同一座位同一身份只留第一次说的那次。 */
    internal data class Claim(
        val day: Int,
        val seat: Int,
        val role: String,
        val withdrawn: Boolean = false,
        val retracted: Boolean = false,
    )

    /** 一条报出来的验人。同一座位对同一目标同一结果只留第一次；若改口不同结果会如实保留两条。 */
    internal data class Check(val day: Int, val seat: Int, val target: Int, val verdict: String)

    /** 一轮投票里某个座位「说投谁 / 实际投谁」。 */
    internal data class VoteLine(val day: Int, val seat: Int, val said: Int?, val actual: Int?)

    /** 一轮警长投票的公开票型：警长票不做言行对账，只保留「谁把警徽投给了谁」。 */
    internal data class SheriffVoteRound(val day: Int, val ballots: List<Ballot>)

    internal data class Ledger(
        val claims: List<Claim> = emptyList(),
        val checks: List<Check> = emptyList(),
        val sheriffVotes: List<SheriffVoteRound> = emptyList(),
        val votes: List<VoteLine> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = claims.isEmpty() && checks.isEmpty() && sheriffVotes.isEmpty() && votes.isEmpty()
    }

    /** 拼成一段可直接注入 system 的文字。没有可摘的东西时返回空串（不占位）。 */
    fun of(table: WerewolfTable): String = render(collect(table))

    // ---------------------------------------------------------------------
    // 摘取
    // ---------------------------------------------------------------------

    internal fun collect(table: WerewolfTable): Ledger {
        val claims = LinkedHashMap<Pair<Int, String>, Claim>()
        val checks = LinkedHashMap<Triple<Int, Int, String>, Check>()
        val sheriffVotes = mutableListOf<SheriffVoteRound>()
        val votes = mutableListOf<VoteLine>()
        val promised = HashMap<Pair<Int, Int>, Int>()

        // 已退水座位（来自公开事件 + 退水阶段公开发言）
        val withdrawnSeats = table.game.history
            .filterIsInstance<GameEvent.SheriffWithdrawn>()
            .flatMap { it.seats }
            .toMutableSet()
        // 曾经声称过预言家的座位 & 明确撤回/声明诈身份的座位
        val seerClaimants = HashSet<Int>()
        val retractedSeerSeats = HashSet<Int>()

        table.transcript.forEach { message ->
            when (message.kind) {
                MessageKind.SPEECH -> {
                    if (!message.isPublic) return@forEach
                    val seat = message.seat ?: return@forEach
                    val text = message.text

                    if (message.phase == Phase.DAY_SHERIFF_WITHDRAW && text.contains("我退水")) {
                        withdrawnSeats += seat
                    }
                    if (isSeerRetraction(text)) {
                        retractedSeerSeats += seat
                    }

                    roleClaims(text).forEach { role ->
                        claims.getOrPut(seat to role) { Claim(message.day, seat, role) }
                        if (role == SEER_ROLE) {
                            seerClaimants += seat
                            // 若之前退过水但之后又明确重跳，不取消已退水历史，只允许当下记录验人
                        }
                    }

                    // 只有声称过预言家、且尚未声明撤回/诈身份的座位，才摘录其第一人称验人
                    if (seat in seerClaimants && seat !in retractedSeerSeats) {
                        checkClaims(text, seat).forEach { (target, verdict) ->
                            checks.getOrPut(Triple(seat, target, verdict)) {
                                Check(message.day, seat, target, verdict)
                            }
                        }
                    }

                    // 白天讨论阶段的票向承诺才用于放逐对账（警上竞选发言的「今天推 X」不算放逐阶段承诺，
                    // 但若当天白天讨论未改口也可兜底；这里仅在讨论阶段覆盖或记录）
                    if (message.phase == Phase.DAY_DISCUSSION) {
                        lastPromise(text, seat)?.let { promised[message.day to seat] = it }
                    }
                }

                MessageKind.BALLOT -> {
                    val card = message.ballot ?: return@forEach
                    when (message.phase) {
                        Phase.DAY_SHERIFF_VOTE -> {
                            if (card.ballots.isNotEmpty()) {
                                sheriffVotes += SheriffVoteRound(message.day, card.ballots)
                            }
                        }

                        Phase.DAY_VOTE -> {
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

                else -> Unit
            }
        }

        val finalizedClaims = claims.values.map { claim ->
            if (claim.role == SEER_ROLE) {
                claim.copy(
                    withdrawn = claim.seat in withdrawnSeats,
                    retracted = claim.seat in retractedSeerSeats,
                )
            } else {
                claim
            }
        }
        return Ledger(finalizedClaims, checks.values.toList(), sheriffVotes, votes)
    }

    private const val SEER_ROLE = "预言家"
    private val ROLE_WORDS = "(预言家|女巫|猎人|白痴|守卫|熊)"

    /**
     * 「我是预言家」「我是真预言家」「我跳预言家」「我认女巫」。
     *
     * 兼容「真 / 正牌 / 那个 / 一张」等修饰词，防止漏掉「我是真预言家」。
     */
    private val ROLE_CLAIM = Regex(
        """(?:我是|我跳|我认|我就是|我这边是|我才是)\s*(?:那个|一名|一张|真|正牌)?\s*$ROLE_WORDS"""
    )

    /** 「我预言家，昨晚验了……」「我真预言家：……」 */
    private val ROLE_CLAIM_BARE = Regex("""我(?:真)?$ROLE_WORDS[，,。：:]""")

    /** 明确撤回预言家身份或说明警上只是诈身份。 */
    private val SEER_RETRACT = Regex(
        """(?:我(?:其实|本来|压根)?不是\s*(?:真)?预言家|我收回预言家|我警上跳预(?:言家)?(?:只|就)是诈|我(?:刚才|警上)(?:只是|就是)诈身份)"""
    )

    private fun isSeerRetraction(text: String): Boolean = SEER_RETRACT.containsMatchIn(text)

    private fun roleClaims(text: String): List<String> {
        val found = mutableListOf<String>()
        ROLE_CLAIM.findAll(text).forEach { match ->
            if (!isHypotheticalOrQuoted(text, match.range.first)) {
                found += match.groupValues[1]
            }
        }
        ROLE_CLAIM_BARE.findAll(text).forEach { match ->
            if (!isHypotheticalOrQuoted(text, match.range.first)) {
                found += match.groupValues[1]
            }
        }
        return found.distinct()
    }

    /**
     * 前面是不是假设句、否定句、或正在引用别人的原话。
     *
     * 「如果我是预言家」「他说“我是预言家”」「我不是预言家」都不能记成本人声称。
     */
    private fun isHypotheticalOrQuoted(text: String, index: Int): Boolean {
        if (isInsideQuotes(text, index)) return true
        val before = text.substring(maxOf(0, index - 6), index)
        return HYPOTHETICAL_OR_NEGATION.any { before.contains(it) }
    }

    private fun isInsideQuotes(text: String, index: Int): Boolean {
        var openCorner = 0
        var openDoubleCorner = 0
        var openCurly = 0
        var straightQuotes = 0
        for (i in 0 until index) {
            when (text[i]) {
                '「' -> openCorner++
                '」' -> if (openCorner > 0) openCorner--
                '『' -> openDoubleCorner++
                '』' -> if (openDoubleCorner > 0) openDoubleCorner--
                '“' -> openCurly++
                '”' -> if (openCurly > 0) openCurly--
                '"' -> straightQuotes++
            }
        }
        return openCorner > 0 || openDoubleCorner > 0 || openCurly > 0 || straightQuotes % 2 == 1
    }

    private val HYPOTHETICAL_OR_NEGATION = listOf(
        "如果", "假如", "假设", "要是", "万一", "若是", "倘若",
        "不是", "并非", "假装", "装成", "冒充",
    )

    /** 句子切分：不跨句号、问号、感叹号、分号或换行提取验人，杜绝跨子句硬拼。 */
    private val SENTENCE_SPLIT = Regex("""[。！？；\n]+""")

    /**
     * 显式验人句：「第 1 夜验了 7 号，是好人」「首夜验的 4 号是好人」「验到 3 号是狼」「昨晚验的 2 号金水」。
     *
     * 中间只允许短停顿与系词（如「，结果是」「是」），绝不跨分号或大段正文匹配。
     */
    private val CHECK = Regex(
        """(?:查验|验|查|摸)\s*(?:了|过|的|到|出)?\s*(\d{1,2})\s*号?\s*(?:[，,：:]\s*)?(?:结果)?(?:是|为|牌是|是个)?\s*(好人|狼人|金水|查杀|狼(?![队坑面刀人]))"""
    )

    /** 「查杀 3 号」「昨晚查杀 3 号」。 */
    private val CHECK_KILL_PREFIX = Regex("""(?<!被)查杀\s*(?:了|的)?\s*(\d{1,2})\s*号""")

    /** 「3 号金水」「5 号是查杀」——仅在无第三人称主语的本人报验人句里采纳。 */
    private val CHECK_BARE = Regex("""(?<!\d)(\d{1,2})\s*号\s*(?:是|为)?\s*(金水|查杀)""")

    /** 第三人称或转述/未来计划特征：一旦出现在同一句的验人动词之前，说明不是本人已完成的查验。 */
    private val THIRD_PERSON_BEFORE_CHECK = Regex(
        """(?:他|她|对方|别人|人家|\d{1,2}\s*号|改口说|声称|凭什么|信他|补验|准备|打算|计划|警徽流|留.{0,4}验|先验|后验|要验|想验|如果|假如|假设|要是|没验|未验)"""
    )

    /** 验人结果紧跟疑问/选择词说明不是定论（如「验 8 号是好人还是狼」）。 */
    private val QUESTION_AFTER_CHECK = Regex("""^\s*(?:还是|吗|呢|\?|？)""")

    internal fun checkClaims(text: String, seat: Int): List<Pair<Int, String>> {
        val found = mutableListOf<Pair<Int, String>>()
        SENTENCE_SPLIT.split(text).forEach { rawSentence ->
            val sentence = rawSentence.trim()
            if (sentence.isEmpty()) return@forEach

            CHECK.findAll(sentence).forEach { match ->
                val target = match.groupValues[1].toIntOrNull() ?: return@forEach
                if (target == seat) return@forEach
                if (isCheckQuotedOrThirdPerson(sentence, match.range.first, match.range.last + 1)) return@forEach
                found += target to normalizeVerdict(match.groupValues[2])
            }

            CHECK_KILL_PREFIX.findAll(sentence).forEach { match ->
                val target = match.groupValues[1].toIntOrNull() ?: return@forEach
                if (target == seat) return@forEach
                if (isCheckQuotedOrThirdPerson(sentence, match.range.first, match.range.last + 1)) return@forEach
                found += target to "狼人"
            }

            CHECK_BARE.findAll(sentence).forEach { match ->
                val target = match.groupValues[1].toIntOrNull() ?: return@forEach
                if (target == seat) return@forEach
                if (isCheckQuotedOrThirdPerson(sentence, match.range.first, match.range.last + 1)) return@forEach
                // 裸写「X 号金水/查杀」要求本句有明确第一人称/夜间报验语境，避免顺口讨论「4 号金水」被误记
                val before = sentence.substring(0, match.range.first)
                if (!FIRST_PERSON_CHECK_CUE.containsMatchIn(before)) return@forEach
                found += target to normalizeVerdict(match.groupValues[2])
            }
        }
        return found.distinct()
    }

    private val FIRST_PERSON_CHECK_CUE = Regex("""(?:我|昨晚|昨夜|首夜|第\s*[1-9一二三四五六七八九]\s*夜|昨天|今早)""")

    private fun isCheckQuotedOrThirdPerson(sentence: String, start: Int, end: Int): Boolean {
        if (isInsideQuotes(sentence, start)) return true
        val before = sentence.substring(0, start)
        if (THIRD_PERSON_BEFORE_CHECK.containsMatchIn(before)) return true
        val after = sentence.substring(minOf(end, sentence.length))
        if (QUESTION_AFTER_CHECK.containsMatchIn(after)) return true
        return false
    }

    private fun normalizeVerdict(word: String): String = when (word) {
        "金水", "好人" -> "好人"
        "查杀", "狼人", "狼" -> "狼人"
        else -> word
    }

    /**
     * 白天发言里的票向承诺：
     * - 「我这一票投 5 号」「投给 5 号」
     * - 「今天出 2 号」「这轮推 6 号」「我归票 4 号」
     */
    private val PROMISE = Regex(
        """(?:投(?:给|到)?|(?:今天|这轮|本轮|这一票|我(?:今天|这一票)?|归票(?:给)?)\s*(?:优先|先|就|还是|直接)?\s*(?:出|推|投|砸(?:向|在)?))\s*(\d{1,2})\s*号?"""
    )

    private val PROMISE_REJECT_BEFORE = Regex("""(?:别|不|没|勿|明天|后天|下轮|如果|假如|要是|他|她|说|叫|让|\d\s*号)""")

    private fun lastPromise(text: String, seat: Int): Int? {
        var last: Int? = null
        PROMISE.findAll(text).forEach { match ->
            val start = match.range.first
            if (isInsideQuotes(text, start)) return@forEach
            val before = text.substring(maxOf(0, start - 4), start)
            if (PROMISE_REJECT_BEFORE.containsMatchIn(before)) return@forEach
            val target = match.groupValues[1].toIntOrNull() ?: return@forEach
            if (target == seat) return@forEach
            last = target
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
            append("下面这些是从大家公开发言与公开唱票里摘出来的可核对记录，跨天累积：\n")
            if (ledger.claims.isNotEmpty()) {
                append("· 声称身份：")
                append(
                    ledger.claims.joinToString("；") { claim ->
                        val status = when {
                            claim.retracted -> "，已退水/自称诈身份"
                            claim.withdrawn -> "，已退水"
                            else -> ""
                        }
                        "${claim.seat} 号自称${claim.role}（第 ${claim.day} 天$status）"
                    }
                )
                append("\n")
                ledger.claims.groupBy { it.role }.filter { it.value.size > 1 }.forEach { (role, list) ->
                    append("  —— ${role}有 ${list.size} 个人声称：")
                    append(list.joinToString("、") { "${it.seat} 号" })
                    val retired = list.filter { it.withdrawn || it.retracted }
                    if (retired.isNotEmpty()) {
                        append("（其中 ")
                        append(retired.joinToString("、") { "${it.seat} 号" })
                        append("已退水/撤回）")
                    }
                    append("，这几个人里至少有一个在说谎。\n")
                }
            }
            if (ledger.checks.isNotEmpty()) {
                append("· 预言家起跳者报出的验人：")
                append(
                    ledger.checks.joinToString("；") {
                        "${it.seat} 号称第 ${it.day} 天验 ${it.target} 号是${it.verdict}"
                    }
                )
                append("\n")
            }
            if (ledger.sheriffVotes.isNotEmpty()) {
                val countByDay = ledger.sheriffVotes.groupingBy { it.day }.eachCount()
                val seenByDay = HashMap<Int, Int>()
                ledger.sheriffVotes.forEach { round ->
                    val roundIndex = (seenByDay[round.day] ?: 0) + 1
                    seenByDay[round.day] = roundIndex
                    val label = if ((countByDay[round.day] ?: 0) > 1) {
                        "第 ${round.day} 天警长票型（第 $roundIndex 轮）"
                    } else {
                        "第 ${round.day} 天警长票型"
                    }
                    append("· $label：")
                    append(
                        round.ballots.joinToString("、") { b ->
                            val target = b.targetSeat?.let { "$it" } ?: "弃票"
                            "${b.voterSeat}→$target"
                        }
                    )
                    append("\n")
                }
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
