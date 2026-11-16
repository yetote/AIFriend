package com.core.ai.structured

import kotlinx.serialization.json.Json

/**
 * 从模型回复里把 JSON 抠出来的工具。
 *
 * 即使开了 `response_format = json_object`，也不能假设回复就是干净的 JSON：
 * 有些模型会包一层 markdown 代码块，有些会在前面写一句「好的，我的决定是：」，
 * 还有的厂商根本不支持这个字段（星火、Gemini），完全靠提示词约束。
 *
 * 所以这里不假设格式，直接做括号配对扫描：把文本里所有**顶层**的 `{...}` 找出来，
 * 逐个尝试解析，第一个能解析且通过校验的就是答案。
 */
object StructuredJson {

    /**
     * 解析用的 Json 实例。
     *
     * 比项目里网络层那份更宽松：模型偶尔会把数字写成字符串（`"target": "3"`），
     * `coerceInputValues` 能救一部分；多余字段直接忽略，免得模型多吐一个 `reason`
     * 就整条作废。
     */
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 扫出文本里所有顶层的完整 JSON 对象。
     *
     * 用括号栈配对，并且**跳过字符串内部**的括号——否则
     * `{"reason": "我投 {3} 号"}` 这种就会被切碎。
     * 转义引号（`\"`）同样要跳过，不然字符串会被提前截断。
     */
    fun balancedObjects(text: String): List<String> {
        val result = mutableListOf<String>()
        val stack = ArrayDeque<Int>()
        var inString = false
        var escaped = false

        text.forEachIndexed { index, ch ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                return@forEachIndexed
            }
            when (ch) {
                '"' -> inString = true
                '{' -> stack.addLast(index)
                '}' -> if (stack.isNotEmpty()) {
                    val start = stack.removeLast()
                    // 栈空了才说明这一层是顶层的对象，嵌套的内层不单独返回
                    if (stack.isEmpty()) result += text.substring(start, index + 1)
                }
            }
        }
        return result
    }
}
