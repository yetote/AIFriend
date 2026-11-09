package com.core.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 百炼 / 方舟这类「官方厂商里代理了别家模型」的平台，按牌子分组。
 *
 * 分组的难点在 id 形态不统一：自家的 `qwen3.8-flash` 没有厂商段，
 * 代理的既有平的 `deepseek-v4-pro-0813`，也有带路径的 `kimi/kimi-k3`。
 * 直接用 GatewayModel 那套「按 / 切段」的规则，平的 id 会因为
 * 没有 `/` 而被整段当成厂商名，分组栏里就出现拿模型名当分类的假组。
 */
class VendorLabelTest {

    private val qianwen = "通义千问"
    private val doubao = "豆包"

    @Test
    fun `own models stay in the self group`() {
        assertEquals(qianwen, vendorLabelOf("qwen3.8-flash", qianwen))
        assertEquals(qianwen, vendorLabelOf("qwen3-vl-plus", qianwen))
        assertEquals(doubao, vendorLabelOf("doubao-seed-evolving", doubao))
        assertEquals(doubao, vendorLabelOf("doubao-seed-2-1-pro-260915", doubao))
    }

    @Test
    fun `flat third party ids map to a known vendor`() {
        // 百炼的第三方 id 大多是平的，没有 `/`
        assertEquals("DeepSeek", vendorLabelOf("deepseek-v4-pro-0813", qianwen))
        assertEquals("DeepSeek", vendorLabelOf("deepseek-v4.1-flash", qianwen))
        assertEquals("GLM", vendorLabelOf("glm-5.3", qianwen))
        assertEquals("MiniMax", vendorLabelOf("MiniMax-M3.1-Flash", qianwen))
    }

    @Test
    fun `path style third party ids map by segment`() {
        assertEquals("Kimi", vendorLabelOf("kimi/kimi-k3", qianwen))
        assertEquals("GLM", vendorLabelOf("ZHIPU/GLM-5.3", qianwen))
    }

    @Test
    fun `unknown models fall back to the self group instead of a fake vendor`() {
        // 认不出的新模型不该冒出一个「某某-3.5-xxx」假分组
        assertEquals(qianwen, vendorLabelOf("happyoyster-1.0-adventure", qianwen))
        assertEquals(doubao, vendorLabelOf("some-new-model-v9", doubao))
    }

    @Test
    fun `blank and malformed ids do not crash`() {
        assertEquals(qianwen, vendorLabelOf("", qianwen))
        assertEquals(qianwen, vendorLabelOf("/", qianwen))
    }

    @Test
    fun `generic words are not swallowed as vendor names`() {
        // matchesVendor 用 startsWith，太泛的 marker 会误吞同前缀的模型名。
        // `step` 若不写成 `step-`，`stepladder-xxx` 会被当成阶跃星辰
        assertEquals(qianwen, vendorLabelOf("stepladder-1.0", qianwen))
        assertEquals("阶跃星辰", vendorLabelOf("step-2-flash", qianwen))
        assertEquals("阶跃星辰", vendorLabelOf("stepfun-v2", qianwen))
        // `yinglong` 不该被 `yi` 吃掉，`yi-` 保留横杠才安全
        assertEquals(qianwen, vendorLabelOf("yinglong-7b", qianwen))
        assertEquals("零一万物", vendorLabelOf("yi-lightning", qianwen))
    }

    @Test
    fun `vendor label follows the model brand not the platform`() {
        // 同一个模型挂在不同平台下，分组标题按牌子走 ——
        // 百炼里的 qwen 应该叫「通义千问」，不该因为平台换了就改叫别的
        assertEquals("通义千问", vendorLabelOf("qwen3.8-flash", qianwen))
        assertEquals("通义千问", vendorLabelOf("qwen3.8-flash", doubao))
        // 反过来也一样：豆包的模型挂在百炼下，仍归豆包
        assertEquals("豆包", vendorLabelOf("doubao-seed-evolving", qianwen))
    }
}
