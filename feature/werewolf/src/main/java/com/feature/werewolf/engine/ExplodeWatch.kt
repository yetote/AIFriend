package com.feature.werewolf.engine

import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfGame

/**
 * 哪一段发言值得拿去问狼「要不要当场插爆」。
 *
 * 每句都问所有狼代价太大（12 人局一天四十多次请求，而绝大多数答案是「不爆」），
 * 所以先用规则筛一遍：这句话**点名了这只狼或它的队友**，并且**在推着全场今天就把人投出去**，
 * 才值得问一次。
 *
 * 漏判的代价只是「这一句没机会插爆」，比句句都问便宜得多，也不会把局打乱。
 * 误判（多问一次）的代价是一次请求，模型答 false 就是了。
 *
 * 判据从「表态词」收紧成「放逐动作词」，是因为「被查杀就自爆」：
 * 「1 号是狼」「1 号被我查杀」只是**指控**，被点的人还有一整轮可以反咬、
 * 指出对方查验不接警徽流；真正值得掐断的是「今天票 1 号」「必须出 1 号」这种
 * 已经在收票的话。把指控也当成放逐，等于让狼连辩都不辩就自己把命交出去。
 */
object ExplodeWatch {

    /**
     * 放逐动作词：这句话在推着全场**今天就把人投出去**。
     *
     * 只认推票这一步。「查杀」「狼」「踩」不算——它们是指控，不是判决：
     * 被查杀恰恰是最该辩的时候，自爆等于当场替预言家作证。
     * 这些词不区分语境（「我不会投 3 号」也会命中），可以接受——
     * 多问一次只是多一次请求，漏掉一次却会让插爆时灵时不灵。
     */
    private val BANISH_WORDS = listOf(
        "投", "票", "出", "归票", "推", "扛推",
    )

    /**
     * 这段发言要不要问 [wolfSeat]。
     *
     * @param speech 刚说完的那段话（已去掉旁白/标记的正文）
     */
    fun shouldAsk(game: WerewolfGame, wolfSeat: Int, speech: String): Boolean {
        if (speech.isBlank()) return false
        val mates = game.aliveWolves().filter { it.seat != wolfSeat }.map { it.seat }
        val targets = mates + wolfSeat
        val pointed = targets.any { seat ->
            mentions(speech, seat, game.playerAt(seat).name)
        }
        return pointed && BANISH_WORDS.any { speech.contains(it) }
    }

    /**
     * 为插爆简报压缩当前回合的公开上下文（起跳阵容 + 本阶段先前发言的极简立场）。
     *
     * 控制在 ~260 字以内，既让无思考的小模型分辨清谁是队友、谁是对跳、当前在推谁，
     * 又不会把一次轻量询问撑成完整长提示词。
     */
    fun briefContext(table: WerewolfTable, wolfSeat: Int, maxChars: Int = 260): String {
        val game = table.game
        fun tagSeat(seat: Int): String = if (game.isWerewolf(seat)) "${seat}号(队友)" else "${seat}号"

        val parts = mutableListOf<String>()
        val claims = PublicLedger.collect(table).claims
        if (claims.isNotEmpty()) {
            val claimText = claims.joinToString("、") { claim ->
                val retired = if (claim.withdrawn || claim.retracted) "[已退]" else ""
                "${tagSeat(claim.seat)}跳${claim.role}$retired"
            }
            parts += "已起跳：$claimText"
        }

        val phaseSpeeches = table.transcript.filter {
            it.day == game.day && it.phase == game.phase && it.kind == MessageKind.SPEECH && it.isPublic
        }
        // watchExplode 在 speaker 刚说完上屏后调用；末尾若就是当前这句 speech，则略过避免与正文重复
        val prior = if (phaseSpeeches.size > 1) phaseSpeeches.dropLast(1) else emptyList()
        if (prior.isNotEmpty()) {
            val speechSummary = prior.takeLast(5).joinToString("；") { msg ->
                val seatLabel = msg.seat?.let(::tagSeat) ?: "未知"
                "$seatLabel:${condenseSpeech(msg.text)}"
            }
            parts += "本轮先前发言：$speechSummary"
        }

        if (parts.isEmpty()) return ""
        val joined = parts.joinToString("\n")
        return if (joined.length <= maxChars) joined else joined.take(maxChars - 1) + "…"
    }

    private fun condenseSpeech(text: String): String {
        val clean = text.replace('\n', ' ').replace(Regex("""\s+"""), " ").trim()
        if (clean.length <= 36) return clean
        return clean.take(16) + "…" + clean.takeLast(18)
    }

    /**
     * 「3 号」「3号」以及名字都算点名。
     *
     * 只认带「号」的写法，是因为裸数字会撞上「今天有 3 个人」「第 2 天」这类；
     * 前面加负向前瞻 `(?<!\d)`，防止「11 号」「12 号」误中「1 号」「2 号」。
     * 名字没有这个问题，直接匹配。
     */
    private fun mentions(speech: String, seat: Int, name: String): Boolean =
        Regex("""(?<!\d)$seat\s*号""").containsMatchIn(speech) ||
            (name.isNotBlank() && speech.contains(name))
}
