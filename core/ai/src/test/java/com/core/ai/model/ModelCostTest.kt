package com.core.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCostTest {

    // ---- 有报价时按报价判 ------------------------------------------------

    @Test
    fun `平台标价为零就是免费`() {
        assertEquals(ModelCost.FREE, ModelCosts.fromPrice(ModelPrice(0.0, 0.0), ModelPrice.CNY))
    }

    @Test
    fun `便宜档折成美元后判为轻量`() {
        // ¥2 入 / ¥8 出 ≈ $0.86 的加权成本，低于便宜档阈值
        assertEquals(ModelCost.LIGHT, ModelCosts.fromPrice(ModelPrice(2.0, 8.0), ModelPrice.CNY))
    }

    @Test
    fun `中间档和旗舰档分开`() {
        assertEquals(ModelCost.STANDARD, ModelCosts.fromPrice(ModelPrice(10.0, 30.0), ModelPrice.CNY))
        assertEquals(ModelCost.PREMIUM, ModelCosts.fromPrice(ModelPrice(30.0, 120.0), ModelPrice.CNY))
    }

    @Test
    fun `有报价就不再猜名字`() {
        // 名字里带 pro，但平台真报价是 0：报价优先，别让名字把它踢出免费池
        assertEquals(
            ModelCost.FREE,
            ModelCosts.of("openrouter", "x-ai/grok-pro", ModelPrice.FREE)
        )
    }

    // ---- 没报价时按名字判 ------------------------------------------------

    @Test
    fun `轻量档的常见后缀`() {
        assertEquals(ModelCost.LIGHT, ModelCosts.fromName("doubao-seed-2.1-lite"))
        assertEquals(ModelCost.LIGHT, ModelCosts.fromName("qwen3.8-flash"))
        assertEquals(ModelCost.LIGHT, ModelCosts.fromName("mimo-v2.6-flash"))
        assertEquals(ModelCost.LIGHT, ModelCosts.fromName("glm-4.7-flash"))
    }

    @Test
    fun `旗舰后缀判成贵`() {
        assertEquals(ModelCost.PREMIUM, ModelCosts.fromName("doubao-seed-2.1-pro"))
        assertEquals(ModelCost.PREMIUM, ModelCosts.fromName("qwen3.8-max"))
    }

    @Test
    fun `厂商名里的 mini 不算档位`() {
        // MiniMax-M3 的 mini 是厂商名。按子串匹配会把它当成轻量档，
        // 于是「不允许付费」时挑中一个按量付费的旗舰模型
        assertEquals(ModelCost.UNKNOWN, ModelCosts.fromName("MiniMax-M3"))
        assertEquals(ModelCost.UNKNOWN, ModelCosts.fromName("MiniMax-M3.1"))
    }

    @Test
    fun `认不出的模型就是未知 不是免费`() {
        assertEquals(ModelCost.UNKNOWN, ModelCosts.fromName("hy4"))
        assertEquals(ModelCost.UNKNOWN, ModelCosts.fromName("doubao-seed-character-260628"))
        assertEquals(ModelCost.UNKNOWN, ModelCosts.fromName("qwen3-0-6b-20250429"))
        assertEquals(ModelCost.UNKNOWN, ModelCosts.fromName(""))
    }

    @Test
    fun `OpenRouter 的 free 后缀认成免费`() {
        assertEquals(ModelCost.FREE, ModelCosts.fromName("deepseek/deepseek-chat:free"))
    }

    @Test
    fun `没有报价也没有档位词 不会进免费池`() {
        assertFalse(ModelCosts.isBudget(ModelCost.UNKNOWN))
        assertFalse(ModelCosts.isBudget(ModelCost.STANDARD))
        assertFalse(ModelCosts.isBudget(ModelCost.PREMIUM))
        assertTrue(ModelCosts.isBudget(ModelCost.FREE))
        assertTrue(ModelCosts.isBudget(ModelCost.LIGHT))
    }

    // ---- 接上快照 --------------------------------------------------------

    @Test
    fun `硅基流动的免费模型能查出来`() {
        assertEquals(ModelCost.FREE, ModelCosts.of("siliconflow", "tencent/Hunyuan-MT-7B"))
    }

    @Test
    fun `查不到价的模型落到名字判断`() {
        assertEquals(ModelCost.UNKNOWN, ModelCosts.of("siliconflow", "unknown/model-x"))
        assertEquals(ModelCost.LIGHT, ModelCosts.of("doubao", "doubao-seed-2.1-lite"))
    }
}
