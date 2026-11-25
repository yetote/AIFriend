package com.feature.werewolf.engine

/**
 * 流式输出退化检测。
 *
 * 小模型偶尔会卡在某个 token 上出不来：要么同一个字刷一整屏
 * （`||||||||||||...`），要么几个字循环（`环环环环...`）。这种输出
 * 既占着版面又烧 token，得在流式收集阶段就掐掉，只留退化前说出来的
 * 正常内容。
 *
 * 判据刻意偏保守——中文口语里叠词、喊口号很常见：
 * - 单字符连续 [SINGLE_RUN] 个才判退化；「哈、呵、啊」这类情绪字放宽到 [LAUGH_RUN]，
 *   免得把「哈哈哈哈哈哈」当成故障。
 * - 短串要循环满 [CYCLE_MIN_TIMES] 轮、且重复段至少 [CYCLE_MIN_CHARS] 字才判退化；
 *   「加油加油加油」这种三两轮的排比不会被误伤。
 *
 * 本类只做纯文本判定，不碰引擎状态，方便单测。
 */
internal object SpeechDegeneration {
    /** 普通字符连续这么多个就算退化。 */
    private const val SINGLE_RUN = 14
    /** 情绪字连续这么多个才算退化——「哈哈哈哈」是正常的。 */
    private const val LAUGH_RUN = 24
    /** 短串循环的周期长度范围。 */
    private const val CYCLE_MIN = 2
    private const val CYCLE_MAX = 8
    /** 短串循环满这么多轮、且总长够，才算退化。 */
    private const val CYCLE_MIN_TIMES = 10
    private const val CYCLE_MIN_CHARS = 40

    /** 这些字反复打是情绪表达，阈值单独放宽。 */
    private val LAUGH_CHARS = setOf(
        '哈', '呵', '嘿', '嘻', '啊', '哦', '噢', '嗯', '呀', '啦',
        '喽', '呜', '哇', '哒', '嘛', '呢', '吧', '咦', '唉', '哎',
    )

    /**
     * 该从第几个字符截断（保留 `[0, index)`）。没有退化返回 null。
     * 同时命中两种模式时取更早的那个，宁可多砍一点也别把刷屏留下来。
     */
    fun cutIndex(text: String): Int? = cutIndex(text as CharSequence)

    /** StringBuilder 版本，流式场景用，避免每来一个 delta 就复制整段字符串。 */
    fun cutIndex(text: StringBuilder): Int? = cutIndex(text as CharSequence)

    private fun cutIndex(text: CharSequence): Int? {
        if (text.length < SINGLE_RUN) return null
        val single = singleRunCut(text)
        val cycle = cycleCut(text)
        return when {
            single == null -> cycle
            cycle == null -> single
            else -> minOf(single, cycle)
        }
    }

    /** 截断版文本；没有退化则原样返回。 */
    fun cut(text: String): String {
        val index = cutIndex(text) ?: return text
        return text.substring(0, index).trimEnd()
    }

    /**
     * 同一字符一路连到阈值：返回这段重复的起点。
     * 换行和空格视为透明——模型刷屏时常在字符之间夹 `\n`，
     * 不跳空的话每行只有一两个字符，反而漏检。
     */
    private fun singleRunCut(text: CharSequence): Int? {
        var runStart = -1
        var runChar: Char? = null
        var runLength = 0
        for (i in text.indices) {
            val ch = text[i]
            if (ch == '\n' || ch == '\r' || ch == ' ' || ch == '\t') continue
            if (ch == runChar) {
                runLength++
            } else {
                // 换了一个字符，先结算上一段
                if (runChar != null && runLength >= thresholdOf(runChar)) return runStart
                runStart = i
                runChar = ch
                runLength = 1
            }
        }
        if (runChar != null && runLength >= thresholdOf(runChar)) return runStart
        return null
    }

    private fun thresholdOf(ch: Char) = if (ch in LAUGH_CHARS) LAUGH_RUN else SINGLE_RUN

    /** 尾部出现固定短串循环：返回整段循环的起点。 */
    private fun cycleCut(text: CharSequence): Int? {
        var earliest: Int? = null
        for (period in CYCLE_MIN..CYCLE_MAX) {
            if (text.length < period * CYCLE_MIN_TIMES) continue
            // 拿末尾一组当样板，从后往前数它重复了多少轮。
            // 退化是发生在末尾的，从尾巴往前找比从左往右找更不容易被正常正文干扰。
            val unit = text.substring(text.length - period)
            if (unit.all { it.isWhitespace() || it in PUNCTUATION }) continue
            var times = 1
            var start = text.length - period
            while (start - period >= 0 && text.substring(start - period, start) == unit) {
                times++
                start -= period
            }
            if (times >= CYCLE_MIN_TIMES && text.length - start >= CYCLE_MIN_CHARS) {
                if (earliest == null || start < earliest) earliest = start
            }
        }
        return earliest
    }

    /**
     * 思考过程的退化/死循环检测。
     *
     * 深度思考模型卡住时，除了短串刷屏（[cutIndex]），更常见的是**整句/整段原地打转**
     * （同一句 20～200 字的分析反复写三四遍）。普通发言的 [cutIndex] 只扫 2～8 字短串，
     * 抓不到这种循环，这里补一层长周期检测，专供思维链使用。
     */
    fun cutReasoningIndex(text: String): Int? = cutReasoningIndex(text as CharSequence)

    fun cutReasoningIndex(text: StringBuilder): Int? = cutReasoningIndex(text as CharSequence)

    private fun cutReasoningIndex(text: CharSequence): Int? {
        val basic = cutIndex(text)
        val longLoop = longCycleCut(text)
        return when {
            basic == null -> longLoop
            longLoop == null -> basic
            else -> minOf(basic, longLoop)
        }
    }

    /**
     * 尾部出现长句/短段连续重复：周期 12～200 字。
     * 12～23 字要连续重复 4 轮，24～200 字连续重复 3 轮即判原地打转。
     * 返回第二次重复开始的位置（保留第一遍思考，砍掉后面的原地打转）。
     */
    private fun longCycleCut(text: CharSequence): Int? {
        val len = text.length
        if (len < LONG_CYCLE_MIN * LONG_CYCLE_SHORT_TIMES) return null
        val maxPeriod = minOf(LONG_CYCLE_MAX, len / LONG_CYCLE_TIMES)
        for (period in LONG_CYCLE_MIN..maxPeriod) {
            val needTimes = if (period < LONG_CYCLE_LONG_THRESHOLD) LONG_CYCLE_SHORT_TIMES else LONG_CYCLE_TIMES
            if (len < period * needTimes) continue
            // 先比最后一个字符，不匹配 O(1) 跳过，避免长思维链上无谓扫字符串
            val tailChar = text[len - 1]
            if (text[len - 1 - period] != tailChar || text[len - 1 - period * 2] != tailChar) continue
            val unitStart = len - period
            if (isAllTrivial(text, unitStart, len)) continue
            var times = 1
            var start = unitStart
            while (start - period >= 0 && regionEquals(text, start - period, unitStart, period)) {
                times++
                start -= period
            }
            if (times >= needTimes) {
                // 保留第一遍，从第二遍开头截断
                return start + period
            }
        }
        return null
    }

    private fun isAllTrivial(text: CharSequence, from: Int, to: Int): Boolean {
        for (i in from until to) {
            val ch = text[i]
            if (!ch.isWhitespace() && ch !in PUNCTUATION) return false
        }
        return true
    }

    private fun regionEquals(text: CharSequence, a: Int, b: Int, len: Int): Boolean {
        for (i in 0 until len) {
            if (text[a + i] != text[b + i]) return false
        }
        return true
    }

    /** 纯标点串不算退化（比如正文末尾的一串「……」）。 */
    private val PUNCTUATION = setOf(
        '。', '，', '、', '；', '：', '？', '！', '…', '·', '—', '-', '.', ',', ';', ':', '?', '!',
        '（', '）', '【', '】', '「', '」', '《', '》', '(', ')', '[', ']', '{', '}',
    )

    private const val LONG_CYCLE_MIN = 12
    private const val LONG_CYCLE_MAX = 200
    private const val LONG_CYCLE_LONG_THRESHOLD = 24
    private const val LONG_CYCLE_SHORT_TIMES = 4
    private const val LONG_CYCLE_TIMES = 3
}
