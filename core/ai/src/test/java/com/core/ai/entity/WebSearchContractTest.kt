package com.core.ai.entity

import com.core.ai.model.ChatCompletionRequest
import com.core.ai.model.ChatMessage
import com.core.ai.model.ServerTool
import com.core.ai.model.ServerToolConfig
import com.core.ai.model.WebSearchOptions
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 联网搜索的**字段名**每家都不一样，这是最容易写错也最难发现的一处：
 * 传错字段有的厂商 400，有的（豆包）静默忽略，界面上完全看不出来「其实没搜」。
 *
 * 这里用各家真实的请求体校验一次，字段一改就红。
 *
 * 序列化用 `explicitNulls = false`，和线上 [com.core.network.KtorClient] 的配置一致——
 * 否则「没开的字段不出现在报文里」这条会被 JSON 里的 null 掩盖掉。
 */
class WebSearchContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun bodyOf(ai: OpenAICompatibleAI, webSearch: Boolean): JsonObject =
        Json.parseToJsonElement(
            json.encodeToString(
                ai.buildRequest(
                    messages = listOf(ChatMessage.user("杭州今天天气")),
                    model = "test-model",
                    systemPrompt = null,
                    enableThinking = true,
                    jsonMode = false,
                    webSearch = webSearch,
                    stream = false,
                )
            )
        ).jsonObject

    @Test
    fun `豆包用空对象的 web_search_options`() {
        val body = bodyOf(AIDoubao(), webSearch = true)
        val options = body["web_search_options"]!!.jsonObject
        assertTrue("传空对象即开启", options.isEmpty())
        // 方舟文档：tools 与 web_search_options 同时出现时联网不生效，所以绝不能带 tools
        assertNull("不能同时带 tools，否则搜索被静默关掉", body["tools"])
    }

    @Test
    fun `千问用 enable_search`() {
        val body = bodyOf(AIQianwen(), webSearch = true)
        assertTrue(body["enable_search"]!!.jsonPrimitive.content.toBoolean())
        assertNull("千问不支持 web_search_options", body["web_search_options"])
    }

    @Test
    fun `混元用 enable_web_search`() {
        val body = bodyOf(AIHunyuan(), webSearch = true)
        assertTrue(body["enable_web_search"]!!.jsonPrimitive.content.toBoolean())
        // 混元收到千问那个字段会 400，两个都发等于自杀
        assertNull(body["enable_search"])
    }

    @Test
    fun `智谱用 tools 里的 web_search 并要引用`() {
        val body = bodyOf(AIZhipu(), webSearch = true)
        val tool = body["tools"]!!.toString()
        assertTrue("工具类型", tool.contains("\"web_search\""))
        assertTrue("开 enable", tool.contains("\"enable\":true"))
        assertTrue("不开 search_result 就不带引用，排查时看不出搜没搜", tool.contains("\"search_result\":true"))
        assertNull(body["web_search_options"])
    }

    @Test
    fun `不开搜索时报文里一个搜索字段都没有`() {
        listOf(AIDoubao(), AIQianwen(), AIHunyuan(), AIZhipu()).forEach { ai ->
            val body = bodyOf(ai, webSearch = false)
            assertNull("${ai.brandName} 不该带 web_search_options", body["web_search_options"])
            assertNull("${ai.brandName} 不该带 enable_search", body["enable_search"])
            assertNull("${ai.brandName} 不该带 enable_web_search", body["enable_web_search"])
            assertNull("${ai.brandName} 不该带 tools", body["tools"])
        }
    }

    @Test
    fun `没有内置搜索的厂商不会因为搜索式就发出未知字段`() {
        // DeepSeek / Kimi 的对话接口没有内置搜索（Kimi 的 $web_search 已下线），
        // 它们由父类的 supportsWebSearch=false 挡住：开关开了也不加任何字段。
        listOf(AIDeepseek() to AIDeepseek().supportsWebSearch, AIKimi() to AIKimi().supportsWebSearch)
            .forEach { (ai, supported) ->
                assertFalse("${ai.brandName} 不该声称支持", supported)
                val request: ChatCompletionRequest = ai.buildRequest(
                    messages = listOf(ChatMessage.user("hi")),
                    model = "test-model",
                    systemPrompt = null,
                    enableThinking = true,
                    jsonMode = false,
                    webSearch = true,
                    stream = false,
                )
                assertNull(ai.brandName, request.webSearchOptions)
                assertNull(ai.brandName, request.enableSearch)
                assertNull(ai.brandName, request.enableWebSearch)
                assertNull(ai.brandName, request.tools)
            }
    }

    @Test
    fun `支持的厂商都声明 supportsWebSearch`() {
        listOf<IAI>(AIDoubao(), AIQianwen(), AIHunyuan(), AIZhipu()).forEach {
            assertTrue("${it.brandName} 应声明支持联网", it.supportsWebSearch)
        }
    }

    @Test
    fun `模型字段形状与运行期一致`() {
        // 单独锁一次数据类本身：显式构造时也要能序列化成厂商认的形状
        val options = Json.parseToJsonElement(json.encodeToString(WebSearchOptions())).jsonObject
        assertTrue("豆包要的是 {}", options.isEmpty())

        val tool = Json.parseToJsonElement(
            json.encodeToString(
                listOf(ServerTool("web_search", ServerToolConfig(enable = true, searchResult = true)))
            )
        ).toString()
        assertEquals(
            """[{"type":"web_search","web_search":{"enable":true,"search_result":true}}]""",
            tool,
        )
    }
}
