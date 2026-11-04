package com.feature.agent.data
import com.core.ai.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBudgetTest {

    @Test
    fun `short history is untouched`() {
        val messages = List(3) { ChatMessage.user("消息$it") }
        assertEquals(messages, messages.trimToBudget(maxTokens = 6_000, minMessages = 6))
    }

    @Test
    fun `old messages are dropped when over budget`() {
        val messages = (1..60).map { ChatMessage.user("第 $it 条消息") }
        val trimmed = messages.trimToBudget(maxTokens = 30, minMessages = 6)

        assertTrue(trimmed.size < messages.size)
        assertTrue(trimmed.size >= 6)
        assertEquals(messages.takeLast(trimmed.size), trimmed)
    }

    @Test
    fun `newest message is always kept even when over budget`() {
        val messages = listOf(
            ChatMessage.user("旧"),
            ChatMessage.assistant("x".repeat(10_000)),
            ChatMessage.user("最新")
        )

        val trimmed = messages.trimToBudget(maxTokens = 10, minMessages = 1)

        assertEquals("最新", trimmed.last().content)
    }

    @Test
    fun `cjk text costs more than latin text`() {
        assertTrue(estimateTokens("你好世界") > estimateTokens("abcd"))
    }
}
