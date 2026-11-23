package com.core.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** token 估算与累计的口径。这些数字只用于展示，但要保证同一段文本前后一致。 */
class TokenUsageTest {

    @Test
    fun `中文按字算英文按四字符算`() {
        assertEquals(4, TokenCounter.estimate("你好世界"))
        assertEquals(2, TokenCounter.estimate("abcdabcd"))
        assertEquals(3, TokenCounter.estimate("你好abcd"))
        assertEquals(0, TokenCounter.estimate(""))
        assertEquals(0, TokenCounter.estimate(null))
    }

    @Test
    fun `输入估算含 system 与消息边框但不含思维链`() {
        val messages = listOf(
            ChatMessage.assistant("回答", reasoning = "这一段不该发回给模型"),
            ChatMessage.user("你好"),
        )
        val withReasoning = TokenCounter.estimateInput(messages, "你是助手")
        val withoutReasoning = TokenCounter.estimateInput(
            listOf(ChatMessage.assistant("回答"), ChatMessage.user("你好")),
            "你是助手",
        )
        assertEquals(withoutReasoning, withReasoning)
        assertTrue(withReasoning > 0)
    }

    @Test
    fun `合并不确定的两段会把总量标成估算`() {
        val real = TokenUsage(input = 10, output = 20, estimated = false)
        val guess = TokenUsage(input = 5, output = 7, estimated = true)
        val merged = real + guess
        assertEquals(15, merged.input)
        assertEquals(27, merged.output)
        assertTrue(merged.estimated)

        val bothReal = real + TokenUsage(input = 1, output = 2)
        assertFalse(bothReal.estimated)
    }

    @Test
    fun `展示文案区分估算与真实值`() {
        assertEquals("46 tokens", TokenUsage.format(TokenUsage(input = 12, output = 34)))
        assertEquals("约 46 tokens", TokenUsage.format(TokenUsage(input = 12, output = 34, estimated = true)))
    }

    @Test
    fun `服务端字段两种写法都能认`() {
        val openai = TokenUsageResponse(promptTokens = 10, completionTokens = 20).toUsage()
        val anthropic = TokenUsageResponse(inputTokens = 10, outputTokens = 20).toUsage()
        assertEquals(TokenUsage(10, 20, false), openai)
        assertEquals(TokenUsage(10, 20, false), anthropic)
        assertEquals(null, TokenUsageResponse(totalTokens = 30).toUsage())
    }
}
