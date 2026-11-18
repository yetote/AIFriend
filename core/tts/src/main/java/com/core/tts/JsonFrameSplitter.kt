package com.core.tts

import java.io.ByteArrayOutputStream

/**
 * 从字节流里切出一个个完整的 JSON 对象。
 *
 * 火山 V3 的 `/api/v3/tts/unidirectional` 用 HTTP Chunked 返回，音频帧是一串 JSON，
 * 但**官方没有承诺帧与帧之间用什么分隔**，而且一个 JSON 完全可能被 HTTP 分块切开。
 * 所以不能像 SSE 那样按行读：一按行读，长音频帧里被切断的那一半就解析失败，
 * 表现出来就是「朗读偶尔丢字」——最难查的那种。
 *
 * 这里按 JSON 结构本身切：数 `{` `}` 的层级，字符串内的括号和转义引号不算。
 * 帧外的空白和换行直接丢掉，所以 NDJSON 和「连着的 JSON」两种形态都能吃。
 *
 * 吃字节而不是 String：UTF-8 的一个汉字可能横跨两个 HTTP 分块，
 * 先解码再拼接会把字切坏；而 JSON 的结构字符全是 ASCII，按字节扫描是安全的，
 * 切出完整帧后再整体解码。
 */
internal class JsonFrameSplitter {

    private val pending = ByteArrayOutputStream()
    private var depth = 0
    private var inString = false
    private var escaped = false

    /** 喂一段字节，返回这次能凑齐的所有完整 JSON 文本（可能为空）。 */
    fun feed(chunk: ByteArray): List<String> {
        val frames = mutableListOf<String>()
        for (byte in chunk) {
            val c = byte.toInt() and 0xFF
            // 帧外的空白（换行分隔）丢掉，否则会混进 pending
            if (depth == 0 && !inString && c != BRACE_OPEN) continue
            pending.write(c)

            if (inString) {
                when {
                    escaped -> escaped = false
                    c == BACKSLASH -> escaped = true
                    c == QUOTE -> inString = false
                }
            } else {
                when (c) {
                    QUOTE -> inString = true
                    BRACE_OPEN -> depth++
                    BRACE_CLOSE -> {
                        depth--
                        if (depth == 0) {
                            frames += pending.toString("UTF-8")
                            pending.reset()
                        }
                    }
                }
            }
        }
        return frames
    }

    private companion object {
        const val QUOTE = 0x22        // "
        const val BACKSLASH = 0x5C    // \
        const val BRACE_OPEN = 0x7B   // {
        const val BRACE_CLOSE = 0x7D  // }
    }
}
