package com.core.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSE 逐行解析的测试。
 *
 * 流式最容易出问题的地方不是网络，而是报文细节：冒号后有没有空格、
 * 结束标记长什么样、厂商塞进来的心跳注释怎么处理。这些用真请求测成本很高，
 * 用字符串测几乎免费，所以这里把各家真实见过的写法都过一遍。
 */
class ChatStreamParserTest {

    @Test
    fun `标准 data 行解析出正文`() {
        val event = ChatStreamParser.parse(
            """data: {"choices":[{"delta":{"content":"你好"}}]}"""
        )
        assertEquals(ChatStreamEvent.Delta(content = "你好", reasoning = ""), event)
    }

    @Test
    fun `冒号后没有空格也能解析`() {
        val event = ChatStreamParser.parse(
            """data:{"choices":[{"delta":{"content":"嗯"}}]}"""
        )
        assertEquals(ChatStreamEvent.Delta(content = "嗯", reasoning = ""), event)
    }

    @Test
    fun `reasoning_content 和正文分开返回`() {
        val reasoning = ChatStreamParser.parse(
            """data: {"choices":[{"delta":{"reasoning_content":"先想一下"}}]}"""
        )
        assertEquals(ChatStreamEvent.Delta(content = "", reasoning = "先想一下"), reasoning)

        val content = ChatStreamParser.parse(
            """data: {"choices":[{"delta":{"content":"答案是 42"}}]}"""
        )
        assertEquals(ChatStreamEvent.Delta(content = "答案是 42", reasoning = ""), content)
    }

    @Test
    fun `聚合平台的 reasoning 字段同样认`() {
        val event = ChatStreamParser.parse(
            """data: {"choices":[{"delta":{"reasoning":"再看看"}}]}"""
        )
        assertEquals(ChatStreamEvent.Delta(content = "", reasoning = "再看看"), event)
    }

    @Test
    fun `结束标记不当作内容`() {
        assertEquals(ChatStreamEvent.Done, ChatStreamParser.parse("data: [DONE]"))
        assertEquals(ChatStreamEvent.Done, ChatStreamParser.parse("data:[DONE]"))
        assertEquals(ChatStreamEvent.Done, ChatStreamParser.parse("data: DONE"))
    }

    @Test
    fun `注释 事件名和空行都跳过`() {
        // 注释行（部分网关用它保活）不是错误，只是没有正文
        assertEquals(ChatStreamEvent.Skip, ChatStreamParser.parse(": keep-alive"))
        assertEquals(ChatStreamEvent.Skip, ChatStreamParser.parse("event: message"))
        assertEquals(ChatStreamEvent.Skip, ChatStreamParser.parse("id: 12"))
        assertEquals(ChatStreamEvent.Skip, ChatStreamParser.parse(""))
        assertEquals(ChatStreamEvent.Skip, ChatStreamParser.parse("data:"))
    }

    @Test
    fun `坏掉的 JSON 跳过而不是崩溃`() {
        // 某一行被截断时，宁可丢这一帧也不能把整轮对话打断
        assertEquals(ChatStreamEvent.Skip, ChatStreamParser.parse("""data: {"choices":["""))
        assertEquals(ChatStreamEvent.Skip, ChatStreamParser.parse("data: 不是 JSON"))
    }

    @Test
    fun `只带 role 或 finish_reason 的帧跳过`() {
        assertEquals(
            ChatStreamEvent.Skip,
            ChatStreamParser.parse("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""),
        )
        assertEquals(
            ChatStreamEvent.Skip,
            ChatStreamParser.parse("""data: {"choices":[{"delta":{},"finish_reason":"stop"}]}"""),
        )
    }

    @Test
    fun `未知字段不影响解析`() {
        val event = ChatStreamParser.parse(
            """data: {"id":"x","choices":[{"index":0,"delta":{"content":"好"},"logprobs":null}],"usage":{"total_tokens":3}}"""
        )
        assertTrue(event is ChatStreamEvent.Delta)
        assertEquals("好", (event as ChatStreamEvent.Delta).content)
    }

    @Test
    fun `空增量帧跳过`() {
        assertEquals(
            ChatStreamEvent.Skip,
            ChatStreamParser.parse("""data: {"choices":[{"delta":{"content":""}}]}"""),
        )
    }
}
