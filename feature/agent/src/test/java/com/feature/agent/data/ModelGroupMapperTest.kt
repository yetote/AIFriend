package com.feature.agent.data

import com.core.ai.model.GatewayModel
import com.core.ai.model.ModelPrice
import com.ui.agent.data.ALL_VENDOR_LABEL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 聚合平台的模型分组：跨厂商「全部」+ 厂商分组。
 *
 * 重点验证三件事：「全部」排在第一位且成员齐全、按价格从低到高、
 * 厂商分组不被汇总逻辑破坏。
 */
class ModelGroupMapperTest {

    private fun model(
        id: String,
        vendor: String,
        price: ModelPrice? = null
    ) = GatewayModel(id = id, vendor = vendor, price = price)

    private fun price(input: Double, output: Double) = ModelPrice(input, output)

    @Test
    fun `全部排在第一个厂商分组里面`() {
        val groups = listOf(
            model("deepseek-ai/DeepSeek-V3", "deepseek-ai", price(2.0, 8.0)),
            model("Qwen/Qwen2.5-72B", "Qwen", price(4.0, 12.0))
        ).toUIModelGroups("¥")

        assertEquals(ALL_VENDOR_LABEL, groups.first().vendor)
        assertTrue(groups.first().isAll)
        // 两家厂商 + 一个「全部」
        assertEquals(3, groups.size)
        assertEquals(setOf("全部", "deepseek-ai", "Qwen"), groups.map { it.vendor }.toSet())
    }

    @Test
    fun `全部收齐所有厂商的模型`() {
        val groups = listOf(
            model("deepseek-ai/DeepSeek-V3", "deepseek-ai", price(2.0, 8.0)),
            model("deepseek-ai/DeepSeek-V3.2", "deepseek-ai", price(4.0, 6.0)),
            model("Qwen/Qwen2.5-72B", "Qwen", price(4.0, 12.0))
        ).toUIModelGroups("¥")

        val all = groups.first { it.isAll }
        assertEquals(3, all.models.size)
        assertEquals(
            setOf("deepseek-ai/DeepSeek-V3", "deepseek-ai/DeepSeek-V3.2", "Qwen/Qwen2.5-72B"),
            all.models.map { it.id }.toSet()
        )
    }

    @Test
    fun `全部里按价格从低到高，没价格的排最后`() {
        val groups = listOf(
            model("Qwen/Qwen2.5-72B", "Qwen", price(4.0, 12.0)),
            model("unknown/model", "unknown", null),
            model("deepseek-ai/DeepSeek-V3", "deepseek-ai", price(2.0, 8.0))
        ).toUIModelGroups("¥")

        val all = groups.first { it.isAll }
        assertEquals(
            listOf("deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B", "unknown/model"),
            all.models.map { it.id }
        )
        // 没价格的一律没有排序权重，UI 靠它把这类沉到最底
        assertTrue(all.models.last().sortWeight == null)
    }

    @Test
    fun `全部里的每个模型都带得出来源厂商`() {
        val groups = listOf(
            model("deepseek-ai/DeepSeek-V3", "deepseek-ai", price(2.0, 8.0)),
            model("Qwen/Qwen2.5-72B", "Qwen", price(4.0, 12.0))
        ).toUIModelGroups("¥")

        val all = groups.first { it.isAll }
        assertEquals(
            mapOf(
                "deepseek-ai/DeepSeek-V3" to "deepseek-ai",
                "Qwen/Qwen2.5-72B" to "Qwen"
            ),
            all.models.associate { it.id to it.vendorName }
        )
    }

    @Test
    fun `只有一家厂商时不额外加全部`() {
        val groups = listOf(
            model("deepseek-ai/DeepSeek-V3", "deepseek-ai", price(2.0, 8.0))
        ).toUIModelGroups("¥")

        assertEquals(1, groups.size)
        assertEquals("deepseek-ai", groups.first().vendor)
    }

    @Test
    fun `厂商分组内部仍然按便宜到贵`() {
        val groups = listOf(
            model("deepseek-ai/DeepSeek-V3.2", "deepseek-ai", price(4.0, 6.0)),
            model("deepseek-ai/DeepSeek-V3", "deepseek-ai", price(2.0, 8.0))
        ).toUIModelGroups("¥")

        val vendor = groups.first { it.vendor == "deepseek-ai" }
        // 成本指数按 0.3 输入 + 0.7 输出：V3.2 = 5.4，V3 = 6.2，输出便宜的排前面
        assertEquals(
            listOf("deepseek-ai/DeepSeek-V3.2", "deepseek-ai/DeepSeek-V3"),
            vendor.models.map { it.id }
        )
    }

    @Test
    fun `排序权重取综合成本指数，不是输入价`() {
        val groups = listOf(
            model("a/low-output", "a", price(5.0, 5.0)),
            model("a/high-output", "a", price(1.0, 40.0)),
            model("b/other", "b", price(3.0, 3.0))
        ).toUIModelGroups("¥")

        val all = groups.first { it.isAll }
        val order = all.models.map { it.id }
        // 只看输入价会得出「1 比 5 便宜」；按综合成本（输出权重 0.7）则反过来
        assertTrue(order.indexOf("a/low-output") < order.indexOf("a/high-output"))
        assertEquals("b/other", order.first())
    }
}
