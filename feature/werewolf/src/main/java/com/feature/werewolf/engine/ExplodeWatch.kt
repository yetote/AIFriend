package com.feature.werewolf.engine

import com.feature.werewolf.game.WerewolfGame

/**
 * 哪一段发言值得拿去问狼「要不要当场插爆」。
 *
 * 每句都问所有狼代价太大（12 人局一天四十多次请求，而绝大多数答案是「不爆」），
 * 所以先用规则筛一遍：这句话**点名了这只狼或它的队友**，并且带着威胁动作，
 * 才值得问一次。
 *
 * 漏判的代价只是「这一句没机会插爆」，比句句都问便宜得多，也不会把局打乱。
 * 误判（多问一次）的代价是一次请求，模型答 false 就是了——所以这里宁可**松**一点：
 * 只要点了名、又出现表态词，就问。
 */
object ExplodeWatch {

    /**
     * 表态类动词。
     *
     * 「狼」也算：单独点名不足以说明什么，「3 号是狼」才是在推人。
     * 这些词不区分语境（「我不会投 3 号」也会命中），可以接受——
     * 多问一次只是多一次请求，漏掉一次却会让插爆时灵时不灵。
     */
    private val THREAT_WORDS = listOf(
        "投", "票", "出", "查杀", "踩", "归票", "推", "扛推", "狼",
    )

    /**
     * 这段发言要不要问 [wolfSeat]。
     *
     * @param speech 刚说完的那段话（已去掉旁白/标记的正文）
     */
    fun shouldAsk(game: WerewolfGame, wolfSeat: Int, speech: String): Boolean {
        if (speech.isBlank()) return false
        val mates = game.wolves().filter { it.seat != wolfSeat }.map { it.seat }
        val targets = mates + wolfSeat
        val pointed = targets.any { seat ->
            mentions(speech, seat, game.playerAt(seat).name)
        }
        return pointed && THREAT_WORDS.any { speech.contains(it) }
    }

    /**
     * 「3 号」「3号」以及名字都算点名。
     *
     * 只认带「号」的写法，是因为裸数字会撞上「今天有 3 个人」「第 2 天」这类；
     * 名字没有这个问题，直接匹配。
     */
    private fun mentions(speech: String, seat: Int, name: String): Boolean =
        speech.contains("$seat 号") ||
            speech.contains("${seat}号") ||
            (name.isNotBlank() && speech.contains(name))
}
