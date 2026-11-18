package com.core.tts

/**
 * 把「给人看的文字」翻成「给机器念的文字」。
 *
 * 角色发言里有大量舞台说明——`（指尖轻点白角，淡紫眼微微一敛）`、`*叹气*`、
 * `**重点**`、表情符号。这些字对眼睛是加分，对耳朵是灾难：TTS 会把括号里的
 * 动作描写一字一句念出来，听感立刻变成「一个人一丝不苟地读稿子」。
 * 语音合成服务商自己的 markdown / 括号过滤各不一样，靠不住，所以在本地统一做。
 *
 * 只做「不改变语义」的清理：删的不是内容，是本来就不该被读出来的排版符号。
 * 清理完是空串就返回 null，由调用方跳过这一句——读一段标点比不出声更难受。
 */
internal object SpeechText {

    /** 括号里的舞台说明。全角/半角/方括号各一套，模型写哪种的都有。 */
    private val PARENTHETICAL = listOf(
        Regex("（[^（）]*）"),
        Regex("\\([^()]*\\)"),
        Regex("【[^【】]*】"),
        Regex("\\[[^\\[\\]]*\\]"),
    )

    /** markdown 链接与图片：留下文字，丢掉地址。 */
    private val IMAGE = Regex("!\\[[^\\]]*]\\s*\\([^)]*\\)")
    private val LINK = Regex("\\[([^\\]]*)]\\s*\\([^)]*\\)")

    /** 强调符号。只删符号不删内容。 */
    private val EMPHASIS = Regex("[*_`~]+")

    /** 行首的标题、引用、列表符号。 */
    private val LINE_MARKER = Regex("(?m)^\\s*(?:#{1,6}\\s*|>\\s*|[-+*]\\s+|\\d+[.、]\\s*)")

    /**
     * 表情与装饰符号。
     *
     * emoji 主区必须写成码点转义（`\x{1F300}`），**不能在字符类里直接写代理对范围**
     * `\uD800-\uDFFF`：JVM 的 Pattern 会把那段范围当成整片代理区处理，顺手把全角逗号
     * （U+FF0C）、全角感叹号（U+FF01）这些正常标点一起吃掉，于是「熊咆哮，坑在」
     * 变成「熊咆哮坑在」——第一版就栽在这里。
     */
    private val DECORATION = Regex(
        "[" +
            "\\x{1F000}-\\x{1FAFF}" + // emoji 主区
            "\\x{1F1E6}-\\x{1F1FF}" + // 国旗
            "\\x{2190}-\\x{21FF}" + // 箭头
            "\\x{2600}-\\x{27BF}" + // 杂项符号、装饰符
            "\\x{2B00}-\\x{2BFF}" +
            "\\uFE0F\\u200D" + // 变体选择符、零宽连接符
            "♥♡★☆♪♫" +
            "]+"
    )

    /** 重复标点收成一个：`！！！！` → `！`，`。。。` → `。`。 */
    private val REPEATED_PUNCT = Regex("([!?！？。，、；：~～])\\1+")

    /** 中文之间不应该有空格：模型偶尔用空格断句，读出来会莫名停顿。 */
    private val CJK_SPACE = Regex("(?<=[\\u4e00-\\u9fff，。！？；：、])\\s+(?=[\\u4e00-\\u9fff，。！？；：、])")

    /** 句末标点。缺了它，TTS 会用平调把一句话拖到底，听起来就是「念稿」。 */
    private const val SENTENCE_END = "。！？!?…—．.）)」』\"”"

    /**
     * @return 可以送去合成的文本；`null` 表示这句没有任何能被读出来的内容。
     */
    fun forSpeech(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null

        text = IMAGE.replace(text, "")
        text = LINK.replace(text, "$1")
        // 括号可能嵌套（（笑）（停顿）叠在一起），删两遍把里层也带走
        repeat(2) {
            PARENTHETICAL.forEach { regex -> text = regex.replace(text, "") }
        }
        text = LINE_MARKER.replace(text, "")
        text = EMPHASIS.replace(text, "")
        text = DECORATION.replace(text, "")
        text = REPEATED_PUNCT.replace(text, "$1")

        // 换行在耳朵里没有意义，统一成停顿；再清掉中文之间的空格
        text = text.replace(Regex("\\s*\\n+\\s*"), "，")
        text = text.replace(Regex("[ \\t]{2,}"), " ")
        text = CJK_SPACE.replace(text, "")

        text = text.trim().trimStart('，', '、', '；', '：', ',', ';', ':').trim()
        // 结尾挂着逗号时先摘掉，否则会补成「…因为。」
        text = text.trimEnd('，', '、', '；', '：', ',', ';', ':').trim()

        // 只剩标点等于没说话
        if (text.none { it.isLetterOrDigit() }) return null

        // 没标点收尾就补一个句号：这是让语调落下来的唯一手段
        if (text.last() !in SENTENCE_END) text += "。"

        return text
    }
}
