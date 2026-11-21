package com.ui.common

// 轻量 Markdown 渲染：把 AI 发言里常见的 **加粗**、# 标题、- 列表等
// 变成真正的排版，而不是让 ** 这些符号原样躺在气泡里。
//
// 为什么不引第三方库：项目离线构建，加依赖要走镜像；而且狼人杀气泡里
// 需要的只是「加粗 / 斜体 / 标题 / 列表 / 引用 / 行内代码 / 分隔线」这几样，
// 表格和代码块反而会把气泡撑变形。自己解析还能顺便处理流式未闭合的语法。
//
// 分两层：下面的解析是纯函数（可单测），上面的 Composable 只负责把结果画出来。

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** 行内片段的样式。嵌套不考虑——AI 发言里几乎不会出现「加粗里套斜体」。 */
internal enum class MdInlineStyle { NORMAL, BOLD, ITALIC, CODE, STRIKE }

internal data class MdSpan(val text: String, val style: MdInlineStyle = MdInlineStyle.NORMAL)

/** 块级行。分隔线/空行没有内容，剩下的行都带一组行内片段。 */
internal enum class MdLineKind { TEXT, HEADING, BULLET, ORDERED, QUOTE, DIVIDER, BLANK }

internal data class MdLine(
    val kind: MdLineKind,
    val level: Int = 0,
    val marker: String = "",
    val spans: List<MdSpan> = emptyList(),
)

// 行内标记不再用多个正则分别 findAll 再去重——星号会被加粗/斜体互相抢占
// （"**1号**...*7号*" 里斜体会从第 2 颗星起配，把 *7号* 吞掉）。
// 改成从左到右单次扫描：每个位置只试一次「当前标记 + 最近闭合」，命中就整段消费。

/** 行首块级标记。列表/引用后面必须有空格，避免误伤 "1号""-3" 这种正文。 */
private val HEADING_RE = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET_RE = Regex("^\\s*[-*+]\\s+(.*)$")
private val ORDERED_RE = Regex("^\\s*(\\d{1,2})\\.\\s+(.*)$")
private val QUOTE_RE = Regex("^\\s*>\\s?(.*)$")
private val DIVIDER_RE = Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$")

/**
 * 把一段可能含 Markdown 的文本切成块级行。纯函数，供单测直接调用。
 *
 * 逐行处理而非整段正则：一行是标题还是列表只取决于它自己的行首，
 * 整段匹配会让 `**多行\n加粗**` 之类的边界行为很难预测。
 */
internal fun parseMarkdown(text: String): List<MdLine> {
    val result = ArrayList<MdLine>()
    text.replace("\r\n", "\n").split('\n').forEach { raw ->
        when {
            raw.isBlank() -> result += MdLine(MdLineKind.BLANK)

            DIVIDER_RE.matches(raw) -> result += MdLine(MdLineKind.DIVIDER)

            else -> {
                val heading = HEADING_RE.matchEntire(raw)
                val bullet = BULLET_RE.matchEntire(raw)
                val ordered = ORDERED_RE.matchEntire(raw)
                val quote = QUOTE_RE.matchEntire(raw)
                when {
                    heading != null -> result += MdLine(
                        kind = MdLineKind.HEADING,
                        level = heading.groupValues[1].length,
                        spans = parseInline(heading.groupValues[2]),
                    )
                    bullet != null -> result += MdLine(
                        kind = MdLineKind.BULLET,
                        marker = "•",
                        spans = parseInline(bullet.groupValues[1]),
                    )
                    ordered != null -> result += MdLine(
                        kind = MdLineKind.ORDERED,
                        marker = ordered.groupValues[1] + ".",
                        spans = parseInline(ordered.groupValues[2]),
                    )
                    quote != null -> result += MdLine(
                        kind = MdLineKind.QUOTE,
                        spans = parseInline(quote.groupValues[1]),
                    )
                    else -> result += MdLine(MdLineKind.TEXT, spans = parseInline(raw))
                }
            }
        }
    }
    return result
}

/**
 * 解析一行里的加粗/斜体/代码/删除线。
 *
 * 从左到右单次扫描：每个位置按「长标记优先」试 4 种开标记，找到最近的闭合
 * 就整段消费；找不到就当普通字符往前走一个。这样既不会让 ** 被 * 拆开，
 * 也天然处理流式半截语法（没有闭合就原样输出，一个字不吞）。
 * 重复标记符按「成对消费」计数：**a** 里的中间星不会被当斜体起点。
 */
internal fun parseInline(input: String): List<MdSpan> {
    if (input.isEmpty()) return emptyList()
    val out = ArrayList<MdSpan>()
    var plain = StringBuilder()
    var i = 0

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            out += MdSpan(plain.toString())
            plain = StringBuilder()
        }
    }

    while (i < input.length) {
        val matched = inlineMarkAt(input, i)
        if (matched != null) {
            flushPlain()
            out += MdSpan(matched.content, matched.style)
            i = matched.endExclusive
        } else {
            plain.append(input[i])
            i++
        }
    }
    flushPlain()
    return out
}

private data class InlineHit(val content: String, val style: MdInlineStyle, val endExclusive: Int)

/**
 * 若 input[pos] 是一个完整的行内标记（含闭合），返回内容和结束位置；否则 null。
 * 顺序即优先级：删除线 > 双星/双下划线加粗 > 反引号代码 > 单星/单下划线斜体。
 */
private fun inlineMarkAt(input: String, pos: Int): InlineHit? {
    // 删除线 ~~x~~
    if (input.startsWith("~~", pos)) {
        val close = input.indexOf("~~", pos + 2)
        if (close > pos + 2) {
            return InlineHit(input.substring(pos + 2, close), MdInlineStyle.STRIKE, close + 2)
        }
    }
    // 加粗 **x** / __x__，要求内容非空且两端不是同类标记符
    for (mark in listOf("**", "__")) {
        if (input.startsWith(mark, pos)) {
            val close = input.indexOf(mark, pos + 2)
            if (close > pos + 2) {
                val content = input.substring(pos + 2, close)
                if (!content.startsWith(mark) && !content.endsWith(mark)) {
                    return InlineHit(content, MdInlineStyle.BOLD, close + 2)
                }
            }
        }
    }
    // 行内代码 `x`
    if (input[pos] == '`') {
        val close = input.indexOf('`', pos + 1)
        if (close > pos + 1) {
            return InlineHit(input.substring(pos + 1, close), MdInlineStyle.CODE, close + 1)
        }
    }
    // 斜体 *x* / _x_。左标记后面不能紧跟同类标记（那是加粗的起点），
    // 右标记前面也不能紧跟同类标记，避免吃掉加粗的收尾星。
    for (mark in listOf("*", "_")) {
        if (input[pos] == mark[0]) {
            val nextIsSame = pos + 1 < input.length && input[pos + 1] == mark[0]
            if (nextIsSame) continue
            var close = pos + 1
            while (close < input.length) {
                close = input.indexOf(mark[0], close)
                if (close < 0) break
                if (close > pos + 1 && input[close - 1] != mark[0]) {
                    return InlineHit(input.substring(pos + 1, close), MdInlineStyle.ITALIC, close + 1)
                }
                close++
            }
        }
    }
    return null
}

/**
 * 渲染一段可能含 Markdown 的 AI 发言。
 *
 * @param baseSize 正文基准字号，由调用方给（牌桌发言、密谈、内心字号本来不一样）
 * @param baseLineHeight 正文基准行高
 * @param trailing 追加在末尾的普通文本，流式光标用（不会被当 Markdown 解析）
 */
@Composable
fun UIMarkdownText(
    text: String,
    color: Color,
    baseSize: TextUnit,
    baseLineHeight: TextUnit,
    modifier: Modifier = Modifier,
    trailing: String = "",
) {
    val lines = parseMarkdown(text)
    val annotated = buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            if (index > 0) append('\n')
            when (line.kind) {
                MdLineKind.BLANK -> Unit
                MdLineKind.DIVIDER -> withStyle(SpanStyle(color = color.copy(alpha = 0.35f))) {
                    append("────────")
                }
                MdLineKind.HEADING -> {
                    val scale = when (line.level) {
                        1 -> 1.22f
                        2 -> 1.12f
                        else -> 1.04f
                    }
                    withStyle(
                        SpanStyle(
                            fontSize = baseSize * scale,
                            fontWeight = FontWeight.Bold,
                        )
                    ) { appendSpans(line.spans) }
                }
                MdLineKind.BULLET, MdLineKind.ORDERED -> {
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(firstLine = 0.sp, restLine = 12.sp)
                        )
                    ) {
                        withStyle(SpanStyle(color = color.copy(alpha = 0.72f))) {
                            append(line.marker + " ")
                        }
                        appendSpans(line.spans)
                    }
                }
                MdLineKind.QUOTE -> {
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(firstLine = 6.sp, restLine = 6.sp)
                        )
                    ) {
                        withStyle(SpanStyle(color = color.copy(alpha = 0.5f))) { append("▏ ") }
                        appendSpans(line.spans)
                    }
                }
                MdLineKind.TEXT -> appendSpans(line.spans)
            }
        }
        if (trailing.isNotEmpty()) append(trailing)
    }
    Text(
        text = annotated,
        modifier = modifier,
        fontSize = baseSize,
        lineHeight = baseLineHeight,
        color = color,
    )
}

private fun AnnotatedString.Builder.appendSpans(spans: List<MdSpan>) {
    spans.forEach { span ->
        when (span.style) {
            MdInlineStyle.NORMAL -> append(span.text)
            MdInlineStyle.BOLD -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
            MdInlineStyle.ITALIC -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) }
            MdInlineStyle.STRIKE -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                append(span.text)
            }
            MdInlineStyle.CODE -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                append(span.text)
            }
        }
    }
}
