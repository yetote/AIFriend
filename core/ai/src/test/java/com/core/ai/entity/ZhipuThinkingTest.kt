package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 智谱的思考开关分支。
 *
 * GLM-5 系列「始终思考」，发 `thinking: disabled` 会 400，只能降 `reasoning_effort`；
 * GLM-4.x 才支持完全关闭。两者的判断依赖模型 id 前缀，而 /models 的大小写不稳定，
 * 所以前缀匹配必须忽略大小写，否则免费档能显示、GLM-5 却一调用就 400。
 */
class ZhipuThinkingTest {

    private val zhipu = AIZhipu()

    /**
     * applyThinking 是 protected 的成员函数（带接收者的扩展），AIZhipu 又是 final，
     * 子类化不可行，只能反射。JVM 签名是 (ChatCompletionRequest, boolean)。
     */
    private fun withoutThinking(model: String): ChatCompletionRequest {
        val method = AIZhipu::class.java.getDeclaredMethod(
            "applyThinking",
            ChatCompletionRequest::class.java,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(
            zhipu,
            ChatCompletionRequest(model = model, messages = emptyList()),
            false
        ) as ChatCompletionRequest
    }

    @Test
    fun `小写 glm-5 降 reasoning_effort 而不是发 disabled`() {
        val request = withoutThinking("glm-5.3")
        assertEquals("low", request.reasoningEffort)
        assertNull(request.thinking)
    }

    @Test
    fun `大写 GLM-5 走同一分支`() {
        val request = withoutThinking("GLM-5.3")
        assertEquals("low", request.reasoningEffort)
        assertNull(
            "大写 GLM-5 若被当成 GLM-4.x，会发出 disabled 导致 400",
            request.thinking
        )
    }

    @Test
    fun `免费档 GLM-4_7-Flash 可以关闭思考`() {
        val request = withoutThinking("GLM-4.7-Flash")
        assertNull(request.reasoningEffort)
        assertEquals("disabled", request.thinking?.type)
    }
}
