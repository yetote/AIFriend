package com.core.repository.settings

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayStationDetectorTest {

    @Test
    fun `已知商业聚合站和官方域名直接识别且不发网络请求`() = runBlocking {
        var calls = 0
        val fetcher: suspend (String, Long) -> Result<String> = { _, _ ->
            calls++
            Result.failure(IllegalStateException("should not call network"))
        }

        val cases = listOf(
            "https://api.siliconflow.cn/v1" to "硅基流动",
            "https://openrouter.ai/api/v1" to "OpenRouter",
            "https://aihubmix.com/v1" to "AiHubMix",
            "https://api.deepseek.com/v1" to "DeepSeek",
            "https://api.dmxapi.cn/v1" to "DMXAPI",
            "https://api.302.ai/v1" to "302.AI",
            "https://api.bltcy.ai/v1" to "柏拉图",
            "https://api.groq.com/openai/v1" to "Groq",
        )
        for ((url, expected) in cases) {
            val detected = RelayStationDetector.detect(url, fetcher)
            assertNotNull("应能识别 $url", detected)
            assertEquals(expected, detected!!.name)
            assertEquals(DetectedRelayStation.Source.KNOWN_DOMAIN, detected.source)
        }
        assertEquals("已知域名不应发起额外网络请求", 0, calls)
    }

    @Test
    fun `One API 与 New API 通过 api status 识别站点名称`() = runBlocking {
        val statusJson = """
            {
              "success": true,
              "message": "",
              "data": {
                "system_name": "星云 AI 中转站",
                "version": "v0.6.10"
              }
            }
        """.trimIndent()

        val requested = mutableListOf<String>()
        val detected = RelayStationDetector.detect("https://api.xingyun-relay.com/v1") { url, _ ->
            requested += url
            if (url == "https://api.xingyun-relay.com/api/status") {
                Result.success(statusJson)
            } else {
                Result.failure(RuntimeException("404"))
            }
        }

        assertEquals(listOf("https://api.xingyun-relay.com/api/status"), requested)
        assertEquals("星云 AI 中转站", detected?.name)
        assertEquals(DetectedRelayStation.Source.STATUS_API, detected?.source)
    }

    @Test
    fun `带子路径的中转站优先探测子路径 api status 再回退根路径`() = runBlocking {
        val requested = mutableListOf<String>()
        val detected = RelayStationDetector.detect("https://example.org/gateway/v1") { url, _ ->
            requested += url
            if (url == "https://example.org/api/status") {
                Result.success("""{"success":true,"data":{"system_name":"小橘子 API"}}""")
            } else {
                Result.failure(RuntimeException("404"))
            }
        }

        assertEquals(
            listOf("https://example.org/gateway/api/status", "https://example.org/api/status"),
            requested,
        )
        assertEquals("小橘子 API", detected?.name)
        assertEquals(DetectedRelayStation.Source.STATUS_API, detected?.source)
    }

    @Test
    fun `status 返回 SPA HTML 或 success=false 时安全跳过并回退首页标题`() = runBlocking {
        val detected = RelayStationDetector.detect("https://relay.example.net/v1") { url, _ ->
            when (url) {
                "https://relay.example.net/api/status" -> Result.success("""{"success":false,"message":"not found"}""")
                "https://relay.example.net/" -> Result.success(
                    "<html><head><title>极光中转 &amp; 聚合 - 稳定高速大模型 API</title></head></html>"
                )
                else -> Result.failure(RuntimeException("404"))
            }
        }

        assertEquals("极光中转 & 聚合", detected?.name)
        assertEquals(DetectedRelayStation.Source.HTML_TITLE, detected?.source)
    }

    @Test
    fun `过滤无意义的错误与 Cloudflare 拦截页标题并回退到干净域名`() = runBlocking {
        val detected = RelayStationDetector.detect("https://api.my-station.top/v1") { url, _ ->
            when (url) {
                "https://api.my-station.top/api/status" -> Result.failure(RuntimeException("404"))
                "https://api.my-station.top/" -> Result.success("<html><title>Just a moment...</title></html>")
                else -> Result.failure(RuntimeException("404"))
            }
        }

        assertEquals("my-station.top", detected?.name)
        assertEquals(DetectedRelayStation.Source.HOST_FALLBACK, detected?.source)
    }

    @Test
    fun `本地 Ollama 与 LM Studio 特征识别`() = runBlocking {
        val ollama = RelayStationDetector.detect("http://127.0.0.1:11434/v1") { url, _ ->
            if (url == "http://127.0.0.1:11434/api/version") {
                Result.success("""{"version":"0.6.2"}""")
            } else {
                Result.failure(RuntimeException("404"))
            }
        }
        assertEquals("Ollama", ollama?.name)
        assertEquals(DetectedRelayStation.Source.SERVICE_PROBE, ollama?.source)

        val ollamaByRoot = RelayStationDetector.detect("http://192.168.1.20:8080/v1") { url, _ ->
            if (url == "http://192.168.1.20:8080/") {
                Result.success("Ollama is running")
            } else {
                Result.failure(RuntimeException("404"))
            }
        }
        assertEquals("Ollama", ollamaByRoot?.name)
        assertEquals(DetectedRelayStation.Source.SERVICE_PROBE, ollamaByRoot?.source)

        val lmStudio = RelayStationDetector.detect("http://localhost:1234/v1") { _, _ ->
            Result.failure(RuntimeException("404"))
        }
        assertEquals("LM Studio", lmStudio?.name)
        assertEquals(DetectedRelayStation.Source.SERVICE_PROBE, lmStudio?.source)
    }

    @Test
    fun `纯本地 IP 无特征时不强行把 IP 当成名称`() = runBlocking {
        val detected = RelayStationDetector.detect("http://192.168.1.99:9000/v1") { _, _ ->
            Result.failure(RuntimeException("404"))
        }
        assertNull(detected)
    }

    @Test
    fun `重名去重避开内置厂商与已有自定义接口`() {
        val builtins = setOf("硅基流动", "OpenRouter", "Deepseek")
        val occupied = setOf("硅基流动", "OpenRouter", "Deepseek", "星云中转")

        assertEquals(
            "硅基流动 (自定义)",
            RelayStationDetector.uniqueName("硅基流动", occupied, builtins),
        )
        assertEquals(
            "星云中转 2",
            RelayStationDetector.uniqueName("星云中转", occupied, builtins),
        )
        assertEquals(
            "星云中转 3",
            RelayStationDetector.uniqueName("星云中转", occupied + "星云中转 2", builtins),
        )
    }

    @Test
    fun `默认占位名判断准确区分默认名与用户手动命名`() {
        assertTrue(CustomNameDefaults.isDefaultName(""))
        assertTrue(CustomNameDefaults.isDefaultName("自定义"))
        assertTrue(CustomNameDefaults.isDefaultName("自定义 API"))
        assertTrue(CustomNameDefaults.isDefaultName("自定义 API 2"))
        assertFalse(CustomNameDefaults.isDefaultName("主力中转站"))
        assertFalse(CustomNameDefaults.isDefaultName("OpenRouter"))
    }
}
