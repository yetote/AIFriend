package com.core.ai.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPriceTest {

    @Test
    fun `免费模型价格为零`() {
        assertTrue(ModelPrice.FREE.isFree)
        assertEquals("免费", ModelPrice.FREE.format(ModelPrice.CNY))
    }

    @Test
    fun `格式化保留小数并去掉多余的零`() {
        val price = ModelPrice(2.0, 8.0)
        assertEquals("¥2 入 / ¥8 出 /M", price.format(ModelPrice.CNY))
    }

    @Test
    fun `格式化保留两位小数`() {
        val price = ModelPrice(0.15, 0.6)
        assertEquals("$0.15 入 / $0.6 出 /M", price.format(ModelPrice.USD))
    }

    @Test
    fun `成本指数偏向输出价`() {
        val cheapIn = ModelPrice(0.0, 10.0)
        val cheapOut = ModelPrice(10.0, 0.0)
        // 输出更贵，所以成本指数更高
        assertTrue(cheapIn.costIndex > cheapOut.costIndex)
    }

    @Test
    fun `美元口径按阈值分档`() {
        assertEquals(PriceTier.CHEAP, ModelPrice(0.1, 0.2).tier(ModelPrice.USD))
        assertEquals(PriceTier.NORMAL, ModelPrice(1.0, 3.0).tier(ModelPrice.USD))
        assertEquals(PriceTier.EXPENSIVE, ModelPrice(10.0, 30.0).tier(ModelPrice.USD))
    }

    @Test
    fun `人民币不会被美元阈值误判成贵`() {
        // ¥1 入 / ¥3 出 折合约 $0.55，属于便宜档，不该按人民币数字判成「贵」
        val price = ModelPrice(1.0, 3.0)
        assertEquals(PriceTier.CHEAP, price.tier(ModelPrice.CNY))
    }
}

class OpenRouterPricingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `解析 OpenRouter 的每 token 美元价格`() {
        val raw = """
            {
              "data": [
                {
                  "id": "openai/gpt-4o-mini",
                  "owned_by": "openai",
                  "pricing": { "prompt": "0.00000015", "completion": "0.0000006" }
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<OpenAIModelResponse>(raw)
        val price = response.modelList.single().livePrice

        assertNotNull(price)
        assertEquals(0.15, price!!.inputPerMillion, 1e-9)
        assertEquals(0.6, price.outputPerMillion, 1e-9)
    }

    @Test
    fun `没有 pricing 字段时价格为 null`() {
        val raw = """{"data":[{"id":"deepseek-ai/DeepSeek-V3","owned_by":"deepseek"}]}"""
        val response = json.decodeFromString<OpenAIModelResponse>(raw)
        assertNull(response.modelList.single().livePrice)
    }

    @Test
    fun `非对话模型被过滤掉`() {
        val ids = listOf(
            "Qwen/Qwen3.5-27B",
            "BAAI/bge-m3",
            "FunAudioLLM/CosyVoice2-0.5B",
            "Kwai-Kolors/Kolors",
            "Qwen/Qwen3-Embedding-8B"
        ).map { OpenAIModel(id = it) }

        val kept = ids.toSelectableChatModels()
        assertEquals(listOf("Qwen/Qwen3.5-27B"), kept)
    }

    @Test
    fun `带价格的模型按便宜到贵排序，无价格的排最后`() {
        val models = listOf(
            OpenAIModel(id = "b/expensive", pricing = OpenAIModelPricing("0.00001", "0.00003")),
            OpenAIModel(id = "a/unknown"),
            OpenAIModel(id = "c/cheap", pricing = OpenAIModelPricing("0.0000001", "0.0000002"))
        )

        val sorted = models.toSelectableChatModelsWithPrices()
        assertEquals(listOf("c/cheap", "b/expensive", "a/unknown"), sorted.map { it.id })
    }

    @Test
    fun `厂商名从模型 id 前缀推断`() {
        val model = OpenAIModel(id = "deepseek-ai/DeepSeek-V3", ownedBy = "deepseek-ai")
        assertEquals("deepseek-ai", model.vendor)
    }
}

class SiliconFlowPriceSnapshotTest {

    /*
     * 这里断言的是「没有实时价格时」的兜底行为，而 LivePriceCache 是
     * 进程级单例 —— 别的测试类塞了实时价格就会让这些断言失败。
     * 每个用例前清空，测的才是快照本身。
     */
    @org.junit.Before
    fun resetLiveCache() {
        LivePriceCache.clear()
    }

    @Test
    fun `快照里的价格都能查到`() {
        val price = ModelPrices.lookup("siliconflow", "deepseek-ai/DeepSeek-V3.2")
        assertNotNull(price)
        assertEquals(4.0, price!!.inputPerMillion, 1e-9)
        assertEquals(6.0, price.outputPerMillion, 1e-9)
    }

    @Test
    fun `未知模型不编造价格`() {
        assertNull(ModelPrices.lookup("siliconflow", "unknown/model-x"))
        assertNull(ModelPrices.lookup("openrouter", "deepseek-ai/DeepSeek-V3.2"))
    }

    @Test
    fun `快照标人民币且带抓取日期`() {
        assertEquals(ModelPrice.CNY, ModelPrices.currencyOf("siliconflow"))
        assertTrue(ModelPrices.isSnapshot("siliconflow"))
        assertEquals("2026-02-14", ModelPrices.snapshotDate("siliconflow"))
    }

    @Test
    fun `实时价格平台不标快照`() {
        assertEquals(ModelPrice.USD, ModelPrices.currencyOf("openrouter"))
        assertTrue(!ModelPrices.isSnapshot("openrouter"))
        assertNull(ModelPrices.snapshotDate("openrouter"))
    }

    @Test
    fun `免费模型在快照里标记为免费`() {
        val price = ModelPrices.lookup("siliconflow", "tencent/Hunyuan-MT-7B")
        assertNotNull(price)
        assertTrue(price!!.isFree)
    }

    @Test
    fun `只有计费明细的模型也在快照里`() {
        // 这些模型在定价页上没有模型对象，早先被漏掉，离线首屏就显示成「查不到价格」
        val kev = ModelPrices.lookup("siliconflow", "Kev-4B")
        assertNotNull(kev)
        assertTrue(kev!!.isFree)

        val lora = ModelPrices.lookup("siliconflow", "LoRA/Qwen/Qwen2.5-14B-Instruct")
        assertNotNull(lora)
        assertEquals(1.05, lora!!.inputPerMillion, 1e-9)
        assertEquals(1.05, lora.outputPerMillion, 1e-9)
    }
}
