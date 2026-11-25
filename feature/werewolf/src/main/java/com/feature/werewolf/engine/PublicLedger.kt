package com.feature.werewolf.engine

import com.feature.werewolf.game.Ballot
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase

/**
 * 公开台账（高清无污染版）：把「谁说过什么可核对的硬信息」从公开发言与公开唱票里摘出来，跨天累积。
 *
 * 覆盖六类可核对事实：
 * 1. 身份声称（含「我是真预言家」及已退水/撤回标记）；
 * 2. 预言家起跳者报出的第一人称验人（严格隔离转述与跨子句拼错）；
 * 3. 预言家起跳者声明的警徽流目标（保留最新一版）；
 * 4. 女巫起跳者报出的第一人称银水（救人）与毒口；
 * 5. 警长投票公开票型；
 * 6. 白天放逐票型对账（兼容「投 X 号 / 今天出 X / 这轮推 X」）。
 */
object PublicLedger {

    internal data class Claim(
        val day: Int,
        val seat: Int,
        val role: String,
        val withdrawn: Boolean = false,
        val retracted: Boolean = false,
    )

    internal data class Check(val day: Int, val seat: Int, val target: Int, val verdict: String)

    internal data class BadgeFlow(val day: Int, val seat: Int, val targets: List<Int>)

    internal data class WitchReport(val day: Int, val seat: Int, val action: String, val target: Int)

    internal data class VoteLine(val day: Int, val seat: Int, val said: Int?, val actual: Int?)

    internal data class SheriffVoteRound(val day: Int, val ballots: List<Ballot>)

    internal data class Ledger(
        val claims: List<Claim> = emptyList(),
        val checks: List<Check> = emptyList(),
        val badgeFlows: List<BadgeFlow> = emptyList(),
        val witchReports: List<WitchReport> = emptyList(),
        val sheriffVotes: List<SheriffVoteRound> = emptyList(),
        val votes: List<VoteLine> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = claims.isEmpty() && checks.isEmpty() && badgeFlows.isEmpty() &&
                witchReports.isEmpty() && sheriffVotes.isEmpty() && votes.isEmpty()
    }

    fun of(table: WerewolfTable): String = render(collect(table))

    // ---------------------------------------------------------------------
    // 摘取
    // ---------------------------------------------------------------------

    internal fun collect(table: WerewolfTable): Ledger {
        val claims = LinkedHashMap<Pair<Int, String>, Claim>()
        val checks = LinkedHashMap<Triple<Int, Int, String>, Check>()
        val badgeFlows = LinkedHashMap<Int, BadgeFlow>()
        val witchReports = LinkedHashMap<Triple<Int, String, Int>, WitchReport>()
        val sheriffVotes = mutableListOf<SheriffVoteRound>()
        val votes = mutableListOf<VoteLine>()
        val promised = HashMap<Pair<Int, Int>, Int>()

        val withdrawnSeats = table.game.history
            .filterIsInstance<GameEvent.SheriffWithdrawn>()
            .flatMap { it.seats }
            .toMutableSet()
        val seerClaimants = HashSet<Int>()
        val witchClaimants = HashSet<Int>()
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
                        if (role == SEER_ROLE) seerClaimants += seat
                        if (role == WITCH_ROLE) witchClaimants += seat
                    }

                    // 只有声称过预言家且未撤回者，才摘录第一人称验人与警徽流
                    if (seat in seerClaimants && seat !in retractedSeerSeats) {
                        checkClaims(text, seat).forEach { (target, verdict) ->
                            checks.getOrPut(Triple(seat, target, verdict)) {
                                Check(message.day, seat, target, verdict)
                            }
                        }
                        badgeFlowTargets(text, seat)?.let { targets ->
                            badgeFlows[seat] = BadgeFlow(message.day, seat, targets)
                        }
                    }

                    // 只有声称过女巫者，才摘录第一人称银水（救人）与毒口
                    if (seat in witchClaimants) {
                        witchReportClaims(text, seat).forEach { (action, target) ->
                            witchReports.getOrPut(Triple(seat, action, target)) {
                                WitchReport(message.day, seat, action, target)
                            }
                        }
                    }

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
        return Ledger(
            claims = finalizedClaims,
            checks = checks.values.toList(),
            badgeFlows = badgeFlows.values.toList(),
            witchReports = witchReports.values.toList(),
            sheriffVotes = sheriffVotes,
            votes = votes,
        )
    }

    private const val SEER_ROLE = "预言家"
    private const val WITCH_ROLE = "女巫"
    private val ROLE_WORDS = "(预言家|女巫|猎人|白痴|守卫|熊)"

    private val ROLE_CLAIM = Regex(
        """(?:我是|我跳|我认|我就是|我这边是|我才是)\s*(?:那个|一名|一张|真|正牌)?\s*$ROLE_WORDS"""
    )

    private val ROLE_CLAIM_BARE = Regex("""我(?:真)?$ROLE_WORDS[，,。：:]""")

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

    private val SENTENCE_SPLIT = Regex("""[。！？；\n]+""")

    private val CHECK = Regex(
        """(?:查验|验|查|摸)\s*(?:了|过|的|到|出)?\s*(\d{1,2})\s*号?\s*(?:[，,：:]\s*)?(?:结果)?(?:是|为|牌是|是个)?\s*(好人|狼人|金水|查杀|狼(?![队坑面刀人]))"""
    )

    private val CHECK_KILL_PREFIX = Regex("""(?<!被)查杀\s*(?:了|的)?\s*(\d{1,2})\s*号""")

    private val CHECK_BARE = Regex("""(?<!\d)(\d{1,2})\s*号\s*(?:是|为)?\s*(金水|查杀)""")

    private val THIRD_PERSON_BEFORE_CHECK = Regex(
        """(?:他|她|对方|别人|人家|\d{1,2}\s*号|改口说|声称|凭什么|信他|补验|准备|打算|计划|警徽流|留.{0,4}验|先验|后验|要验|想验|如果|假如|假设|要是|没验|未验)"""
    )

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
                val before = sentence.substring(0, match.range.first)
                if (!FIRST_PERSON_CHECK_CUE.containsMatchIn(before)) return@forEach
                found += target to normalizeVerdict(match.groupValues[2])
            }
        }
        return found.distinct()
    }

    private val CLAUSE_BOUNDARY = Regex("""[，,：:]""")

    /** 截出紧贴 [start] 之前的当前子句（最后一个逗号/冒号之后）。 */
    private fun localClauseBefore(sentence: String, start: Int): String {
        val prefix = sentence.substring(0, start)
        val last = CLAUSE_BOUNDARY.findAll(prefix).lastOrNull()?.range?.last ?: -1
        return prefix.substring(last + 1)
    }

    private val SENTENCE_HYPOTHETICAL = Regex("""(?:如果|假如|假设|要是|万一|若是|倘若)""")
    private val THIRD_PERSON_ACTOR = Regex(
        """(?:他|她|对方|别人|人家|你\s*\d{1,2}\s*号|\d{1,2}\s*号\s*(?:自己|警上|跳|自称|说|称|报|验|查(?!杀)|摸|改口|发|给|留|救|毒|泼|捞))"""
    )
    private val CLAUSE_FIRST_PERSON = Regex("""(?:^|[，,：:])\s*我""")

    /** 句子前半段若存在整句假设，或存在未在后续子句切回「我」的第三人称动作主语，判定为转述/假设。 */
    private fun hasUnresetThirdPersonBefore(sentence: String, start: Int): Boolean {
        val prefix = sentence.substring(0, start)
        if (SENTENCE_HYPOTHETICAL.containsMatchIn(prefix)) return true
        val lastActor = THIRD_PERSON_ACTOR.findAll(prefix).lastOrNull() ?: return false
        val afterActor = prefix.substring(lastActor.range.last + 1)
        return !CLAUSE_FIRST_PERSON.containsMatchIn(afterActor)
    }

    private val FIRST_PERSON_CHECK_CUE = Regex("""(?:我|昨晚|昨夜|首夜|第\s*[1-9一二三四五六七八九]\s*夜|昨天|今早)""")

    private fun isCheckQuotedOrThirdPerson(sentence: String, start: Int, end: Int): Boolean {
        if (isInsideQuotes(sentence, start)) return true
        val clauseBefore = localClauseBefore(sentence, start)
        if (THIRD_PERSON_BEFORE_CHECK.containsMatchIn(clauseBefore)) return true
        if (hasUnresetThirdPersonBefore(sentence, start)) return true
        val after = sentence.substring(minOf(end, sentence.length))
        if (QUESTION_AFTER_CHECK.containsMatchIn(after)) return true
        return false
    }

    private fun normalizeVerdict(word: String): String = when (word) {
        "金水", "好人" -> "好人"
        "查杀", "狼人", "狼" -> "狼人"
        else -> word
    }

    // ---------------------------------------------------------------------
    // 预言家警徽流摘取（仅本人声明，忽略转述）
    // ---------------------------------------------------------------------

    private val THIRD_PERSON_BEFORE_FLOW = Regex("""(?:他|她|对方|别人|\d{1,2}\s*号(?:的)?|说|称|问)""")
    private val FLOW_DECLARE_VERB = Regex("""(?:留|改|先|顺|验|打|是|：|:)""")
    private val SEAT_NUMBER_IN_FLOW = Regex("""(?<!\d)(\d{1,2})(?!\s*[天夜票轮次人个])""")

    internal fun badgeFlowTargets(text: String, seat: Int): List<Int>? {
        var latest: List<Int>? = null
        SENTENCE_SPLIT.split(text).forEach { rawSentence ->
            val sentence = rawSentence.trim()
            val idx = sentence.indexOf("警徽流")
            if (idx < 0 || isInsideQuotes(sentence, idx)) return@forEach
            val clauseBefore = localClauseBefore(sentence, idx)
            if (THIRD_PERSON_BEFORE_FLOW.containsMatchIn(clauseBefore)) return@forEach
            if (hasUnresetThirdPersonBefore(sentence, idx)) return@forEach
            // 取「警徽流」之后到句尾（或到第一个转折/解释大段之前，最多 28 字）
            val after = sentence.substring(idx + 3).take(28)
            if (!FLOW_DECLARE_VERB.containsMatchIn(after)) return@forEach
            val targets = SEAT_NUMBER_IN_FLOW.findAll(after)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it in 1..20 && it != seat }
                .distinct()
                .take(3)
                .toList()
            if (targets.isNotEmpty()) latest = targets
        }
        return latest
    }

    // ---------------------------------------------------------------------
    // 女巫银水 / 毒口摘取（仅声称女巫者本人第一人称报出）
    // ---------------------------------------------------------------------

    private val WITCH_HEAL = Regex("""(?:救(?:了|下|的|过)?|捞(?:了|起|的)?|银水(?:是|为))\s*(\d{1,2})\s*号""")
    private val WITCH_POISON = Regex("""(?:毒(?:了|死|的|走)?|泼(?:了|的)?)\s*(\d{1,2})\s*号""")
    private val THIRD_PERSON_OR_PLAN_BEFORE_WITCH = Regex(
        """(?:他|她|对方|别人|\d{1,2}\s*号|如果|假如|假设|要是|准备|打算|计划|今晚|明晚|想|要|可以|别|没|不|会|可能|说|称)"""
    )

    internal fun witchReportClaims(text: String, seat: Int): List<Pair<String, Int>> {
        val found = mutableListOf<Pair<String, Int>>()
        SENTENCE_SPLIT.split(text).forEach { rawSentence ->
            val sentence = rawSentence.trim()
            if (sentence.isEmpty()) return@forEach

            WITCH_HEAL.findAll(sentence).forEach { match ->
                if (isInsideQuotes(sentence, match.range.first)) return@forEach
                val clauseBefore = localClauseBefore(sentence, match.range.first)
                if (THIRD_PERSON_OR_PLAN_BEFORE_WITCH.containsMatchIn(clauseBefore)) return@forEach
                if (hasUnresetThirdPersonBefore(sentence, match.range.first)) return@forEach
                val target = match.groupValues[1].toIntOrNull() ?: return@forEach
                if (target in 1..20) found += "救" to target
            }

            WITCH_POISON.findAll(sentence).forEach { match ->
                if (isInsideQuotes(sentence, match.range.first)) return@forEach
                val clauseBefore = localClauseBefore(sentence, match.range.first)
                if (THIRD_PERSON_OR_PLAN_BEFORE_WITCH.containsMatchIn(clauseBefore)) return@forEach
                if (hasUnresetThirdPersonBefore(sentence, match.range.first)) return@forEach
                val target = match.groupValues[1].toIntOrNull() ?: return@forEach
                if (target in 1..20 && target != seat) found += "毒" to target
            }
        }
        return found.distinct()
    }

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
            if (ledger.badgeFlows.isNotEmpty()) {
                append("· 预言家最新警徽流：")
                append(
                    ledger.badgeFlows.joinToString("；") { flow ->
                        "${flow.seat} 号（第 ${flow.day} 天）留 " +
                            flow.targets.joinToString("→") { "${it}号" }
                    }
                )
                append("\n")
            }
            if (ledger.witchReports.isNotEmpty()) {
                append("· 女巫起跳者报出的用药：")
                append(
                    ledger.witchReports.joinToString("；") {
                        "${it.seat} 号称第 ${it.day} 天${it.action}了 ${it.target} 号"
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
