package com.feature.werewolf.engine

import com.core.ai.model.ChatMessage
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 人类座位的测试。
 *
 * 它实现的是 [com.core.ai.entity.IAI]，所以这里验的其实是「挂起—提交」这条桥：
 * 编排层会一直等在这里，界面提交才继续。桥断了的表现是整局卡死，不会有异常提示。
 */
class HumanSeatAITest {

    private val prompt = listOf(ChatMessage.user("请给出你的投票。"))

    @Test
    fun `轮到人类时挂起，直到界面提交`() = runBlocking {
        val ai = HumanSeatAI()
        var result: String? = null

        val job = launch { result = ai.chat(prompt, "human", "你是 3 号", false, true) }
        yield()

        assertTrue("应该正等着输入", ai.waiting)
        val turn = ai.turn.value
        assertNotNull(turn)
        assertEquals(prompt, turn!!.messages)
        assertEquals("你是 3 号", turn.systemPrompt)
        assertTrue("这次要的是结构化决策", turn.jsonMode)

        assertTrue(ai.submit("""{"target": 5}"""))
        job.join()

        assertEquals("""{"target": 5}""", result)
        assertNull("提交后不该再显示输入区", ai.turn.value)
        assertFalse(ai.waiting)
    }

    @Test
    fun `自由发言不走 JSON`() = runBlocking {
        val ai = HumanSeatAI()
        val job = launch { ai.chat(prompt, "human", "你是 3 号", false, false) }
        yield()

        assertFalse(ai.turn.value!!.jsonMode)
        ai.submit("我觉得 5 号有问题。")
        job.join()
    }

    @Test
    fun `没在等输入时提交会返回 false`() {
        val ai = HumanSeatAI()
        assertFalse(ai.submit("""{"target": 1}"""))
    }

    @Test
    fun `协程被取消时清空等待状态`() = runBlocking {
        val ai = HumanSeatAI()
        val job = launch { ai.chat(prompt, "human", "你是 3 号", false, true) }
        yield()
        assertNotNull(ai.turn.value)

        job.cancelAndJoin()

        // 用户退出页面后，下次进来不该还停在上一次的输入框上
        assertNull(ai.turn.value)
        assertFalse(ai.waiting)
    }

    @Test
    fun `重复提交只有第一次生效`() = runBlocking {
        val ai = HumanSeatAI()
        var result: String? = null
        val job = launch { result = ai.chat(prompt, "human", "你是 3 号", false, true) }
        yield()

        assertTrue(ai.submit("第一次"))
        assertFalse("已经在收尾了", ai.submit("第二次"))
        job.join()
        assertEquals("第一次", result)
    }
}
