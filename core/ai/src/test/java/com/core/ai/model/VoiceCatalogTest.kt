package com.core.ai.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 音色按服务商分组：各家 id 不通用，界面不能把别家的音色混进来。 */
class VoiceCatalogTest {

    @Test
    fun `每家都有自己的默认音色选项`() {
        listOf("minimax", "iflytek", "volcengine", "alibaba", "tencent").forEach { id ->
            val voices = VoiceCatalog.forProvider(id)
            assertTrue("$id 应有音色列表", voices.isNotEmpty())
            assertNotNull("$id 应有默认音色项", voices.first { it.id == null })
        }
    }

    @Test
    fun `未知服务回落到 MiniMax 音色`() {
        assertTrue(VoiceCatalog.forProvider("not-exist") == VoiceCatalog.all)
    }

    @Test
    fun `各家的音色 id 不串台`() {
        val minimax = VoiceCatalog.forProvider("minimax").map { it.id }
        val iflytek = VoiceCatalog.forProvider("iflytek").map { it.id }
        val tencent = VoiceCatalog.forProvider("tencent").map { it.id }

        assertTrue("MiniMax 用 male-qn-*", minimax.contains("male-qn-qingse"))
        assertTrue("讯飞用 xiaoyan", iflytek.contains("xiaoyan"))
        assertFalse("讯飞列表不该出现 MiniMax 音色", iflytek.contains("male-qn-qingse"))
        assertTrue("腾讯用数字音色号", tencent.contains("101001"))
    }

    @Test
    fun `服务数量与音色分组对得上`() {
        // TTSServiceCatalog 列出的每家（除默认）都要能在 VoiceCatalog 找到分组。
        TTSServiceCatalog.all.mapNotNull { it.id }.forEach { id ->
            assertTrue("$id 缺音色分组", VoiceCatalog.forProvider(id).isNotEmpty())
        }
    }
}
