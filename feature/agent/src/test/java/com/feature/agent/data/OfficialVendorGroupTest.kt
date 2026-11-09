package com.feature.agent.data

import com.core.ai.model.GatewayModel
import com.core.ai.model.vendorLabelOf
import com.ui.agent.data.ALL_VENDOR_LABEL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方厂商里「代理了别家模型」的那两家（百炼、方舟）走分组展示。
 *
 * 这一层原本只为带价格的聚合平台服务，现在要给一批「没有价格」的模型用，
 * 所以要确认：分组能建起来、不冒出价格文案、自家模型排第一。
 */
class OfficialVendorGroupTest {

    private val qianwen = "通义千问"

    /** 模拟 AIDoubao / AIQianwen 里的 buildVendorGroups。 */
    private fun groupsOf(models: List<String>, selfName: String) =
        models.map { GatewayModel(id = it, vendor = vendorLabelOf(it, selfName)) }
            .toUIModelGroups("¥")

    @Test
    fun `百炼的第三方模型会独立成组`() {
        val models = listOf(
            "qwen3.8-flash",
            "qwen3.8-max",
            "deepseek-v4-pro-0813",
            "kimi/kimi-k3",
            "glm-5.3"
        )
        val groups = groupsOf(models, qianwen)

        // 自家 + DeepSeek + Kimi + GLM，再加一个「全部」
        assertEquals(5, groups.size)
        assertEquals(
            setOf(ALL_VENDOR_LABEL, "通义千问", "DeepSeek", "Kimi", "GLM"),
            groups.map { it.vendor }.toSet()
        )

        val own = groups.first { it.vendor == "通义千问" }
        assertEquals(listOf("qwen3.8-flash", "qwen3.8-max"), own.models.map { it.id })
    }

    @Test
    fun `没有价格的官方分组不会凭空造出价格文案`() {
        val groups = groupsOf(listOf("qwen3.8-flash", "deepseek-v4-pro"), qianwen)

        groups.forEach { group ->
            assertNull("官方厂商查不到价格，组标题不该有起价", group.from)
            group.models.forEach { model ->
                assertNull("模型行不该显示价格", model.price)
            }
        }
    }

    @Test
    fun `全部那一栏收齐所有模型`() {
        val models = listOf("qwen3.8-flash", "deepseek-v4-pro", "kimi/kimi-k3")
        val groups = groupsOf(models, qianwen)

        val all = groups.first { it.isAll }
        assertEquals(models.size, all.models.size)
        assertEquals(models.toSet(), all.models.map { it.id }.toSet())
    }

    @Test
    fun `只有自家模型时不产生多余的厂商组`() {
        // 兜底列表（没配 Key 时）全是自家模型，不该出现空分组或多出来的标签
        val groups = groupsOf(listOf("qwen3.8-flash", "qwen3.8-max"), qianwen)

        // 只有一家厂商，不插「全部」
        assertEquals(1, groups.size)
        assertEquals("通义千问", groups.first().vendor)
        assertEquals(2, groups.first().models.size)
    }

    @Test
    fun `第三方模型名在行内仍显示完整 id`() {
        // 分组标题已经写了牌子，但 id 不重复厂商段（deepseek 没有斜杠路径），
        // 行内必须还能看到完整名字，否则用户不知道选的是哪个版本
        val groups = groupsOf(listOf("deepseek-v4-pro-0813"), qianwen)
        val model = groups.first { it.vendor == "DeepSeek" }.models.first()

        assertEquals("deepseek-v4-pro-0813", model.id)
        assertTrue("行内标签不能为空", model.label.isNotBlank())
    }
}
