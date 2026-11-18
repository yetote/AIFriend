package com.core.ai.model

import com.core.repository.settings.TTSProviderCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `同一家的音色 id 不重复`() {
        // 重复的 id 会在选择器里出现两个一模一样的项，看着像界面出错
        listOf("minimax", "custom", "iflytek", "volcengine", "alibaba", "tencent").forEach { id ->
            val ids = VoiceCatalog.forProvider(id).mapNotNull { it.id }
            assertEquals("$id 有重复音色 id", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun `每家都有拿得出手的音色数量`() {
        // 太少的列表会让人以为功能没做完；这是「丰富度」的下限，不是上限
        listOf("minimax", "iflytek", "volcengine", "alibaba", "tencent").forEach { id ->
            val count = VoiceCatalog.forProvider(id).count { it.id != null }
            assertTrue("$id 只有 $count 个音色", count >= 8)
        }
    }

    @Test
    fun `自定义音色 ID 走 MiniMax 那一套`() {
        // 自定义服务用的是 MiniMax 的 t2a_v2 协议，音色库当然也是它的
        assertTrue(VoiceCatalog.forProvider("custom") == VoiceCatalog.forProvider("minimax"))
    }

    @Test
    fun `内置判定只认列表里的，不判断能不能用`() {
        assertTrue("null 等于跟随默认", VoiceCatalog.isBuiltIn("minimax", null))
        assertTrue(VoiceCatalog.isBuiltIn("minimax", "female-yujie"))
        assertFalse("复刻音色不在列表里，但完全合法", VoiceCatalog.isBuiltIn("minimax", "my-cloned-voice"))
        assertFalse(
            "别家的 id 不能算这家的内置项",
            VoiceCatalog.isBuiltIn("minimax", "xiaoyan"),
        )
    }

    @Test
    fun `分配音色时跳过已被占用的`() {
        val provider = "minimax"
        val all = VoiceCatalog.forProvider(provider).mapNotNull { it.id }
        val first = VoiceCatalog.spareVoice(provider, emptySet())
        assertEquals("没占用时给列表第一个", all.first(), first)

        val next = VoiceCatalog.spareVoice(provider, setOf(all.first()))
        assertEquals(all[1], next)
        assertFalse("分出去的不能是已占用的", next == all.first())
    }

    @Test
    fun `音色用完了就返回 null，不编造 id`() {
        // 编一个不存在的音色会让合成直接失败，比几个人同声更糟
        val all = VoiceCatalog.forProvider("minimax").mapNotNull { it.id }.toSet()
        assertNull(VoiceCatalog.spareVoice("minimax", all))
    }

    @Test
    fun `分组覆盖全部音色，且不含默认项`() {
        listOf("minimax", "iflytek", "volcengine", "alibaba", "tencent").forEach { provider ->
            val grouped = VoiceCatalog.grouped(provider).flatMap { it.second }
            val all = VoiceCatalog.forProvider(provider).filter { it.id != null }
            assertEquals("$provider 分组后数量对不上", all.size, grouped.size)
            assertTrue("$provider 分组里不该出现默认音色", grouped.none { it.id == null })
            // 顺序固定按 VoiceGroup 声明顺序，界面才稳定，不会每次打开换一遍
            val order = VoiceCatalog.grouped(provider).map { it.first }
            assertEquals(order.sortedBy { it.ordinal }, order)
        }
    }

    @Test
    fun `空分组不出现`() {
        // 空组只会让面板上多一个点进去什么都没有的分类。逐家逐组检查，
        // 别只盯讯飞——讯飞现在是有方言音色的（见下一条）。
        listOf("minimax", "iflytek", "volcengine", "alibaba", "tencent").forEach { provider ->
            VoiceCatalog.grouped(provider).forEach { (group, voices) ->
                assertTrue("$provider 的「${group.label}」组是空的", voices.isNotEmpty())
            }
        }
    }

    @Test
    fun `讯飞 xiaolin 是台湾腔女声，不许再标成男声`() {
        // 官方发音人列表：xiaolin = 晓琳，青年女声，台湾普通话。
        // 早先写成「小林（男）」放进男声组，批量建角色时给剑道部主将配了它，
        // 听感就是台湾腔女声，被当成「TTS 音频乱码」查了两轮。
        val voice = VoiceCatalog.forProvider("iflytek").first { it.id == "xiaolin" }

        assertEquals(VoiceGroup.DIALECT, voice.group)
        assertTrue(voice.label, voice.label.contains("台湾"))
        assertFalse(voice.label, voice.label.contains("男"))
    }

    @Test
    fun `搜索能同时按名字和 id 命中`() {
        // 名字
        assertTrue(
            VoiceCatalog.search("tencent", "粤语").any { it.id == "101019" }
        )
        // id 片段，且忽略大小写（用户常从文档里抄 ID）
        assertTrue(
            VoiceCatalog.search("alibaba", "LONGXIAOCHUN").any { it.id == "longxiaochun_v2" }
        )
        // 空关键字等于不过滤，面板默认要看到全部
        assertEquals(
            VoiceCatalog.search("tencent", " ").size,
            VoiceCatalog.forProvider("tencent").size
        )
        assertTrue(VoiceCatalog.search("tencent", "不可能存在的音色").isEmpty())
    }

    @Test
    fun `按服务商回显名字，不跨家串台`() {
        // 腾讯的 101001 在 MiniMax 列表里没有，必须原样显示 id 而不是借用别家的名字
        assertEquals("智瑜（情感女声）", VoiceCatalog.labelOf("tencent", "101001"))
        assertEquals("101001", VoiceCatalog.labelOf("minimax", "101001"))
        assertEquals("默认音色", VoiceCatalog.labelOf("tencent", null))
    }

    @Test
    fun `方言音色归到方言组而不是性别组`() {
        // 找粤语的人不会先去翻「女声」组，方言必须优先
        val cantonese = VoiceCatalog.forProvider("tencent").first { it.id == "101019" }
        assertEquals(VoiceGroup.DIALECT, cantonese.group)
    }

    @Test
    fun `每家的默认音色都在自己的目录里`() {
        // 默认音色不在列表里时，用户看到的永远是「默认音色」四个字，
        // 想确认「现在到底用的哪个声音」都做不到
        TTSProviderCatalog.all.filter { it.defaultVoice.isNotBlank() }.forEach { provider ->
            val ids = VoiceCatalog.forProvider(provider.id).mapNotNull { it.id }
            assertTrue(
                "${provider.id} 的默认音色 ${provider.defaultVoice} 不在自己的音色列表里",
                ids.contains(provider.defaultVoice),
            )
        }
    }

    @Test
    fun `火山的 2点0 音色齐备且走新协议`() {
        val volcano = VoiceCatalog.forProvider("volcengine").mapNotNull { it.id }
        // v1 接口用不了 2.0，这些 id 能出现在列表里，前提是协议已经迁到 v3
        assertTrue(volcano.any { it.endsWith("_uranus_bigtts") })
        assertTrue("2.0 音色太少，等于没升级", volcano.count { it.endsWith("_uranus_bigtts") } >= 20)
        // 1.0 音色要留着：老角色存的可能就是这些 id
        assertTrue(volcano.any { it.endsWith("_mars_bigtts") })
    }
}
