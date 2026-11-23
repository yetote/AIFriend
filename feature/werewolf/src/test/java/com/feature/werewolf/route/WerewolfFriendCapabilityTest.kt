package com.feature.werewolf.route

import com.core.ai.model.Friend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 好友 → 开局页展示模型的映射。
 *
 * 这次多带了一个字段：这家模型的对话接口有没有内置联网搜索。
 * 它必须在选人那一步就显示出来——搜索式下用户最怕的是选完人、等自检跑完，
 * 才知道自家模型根本不会联网。
 */
class WerewolfFriendCapabilityTest {

    private fun friend(brand: String = "Deepseek") = Friend(
        id = "f1",
        name = "阿狸",
        role = "话痨",
        prompt = "",
        brand = brand,
        company = brand,
        model = "deepseek-chat",
    )

    @Test
    fun `支持联网的厂商在界面上要标得出来`() {
        val ui = friend().toUiFriend(supportsWebSearch = true)

        assertTrue("支持联网要如实带过去", ui.supportsWebSearch)
    }

    @Test
    fun `不支持联网的厂商默认就是 false`() {
        // 默认值必须是 false 而不是 true：漏传能力时，宁可多标一句「不会联网」，
        // 也不能让搜索式悄悄退化成「以为会查、其实没查」
        val ui = friend().toUiFriend()

        assertFalse(ui.supportsWebSearch)
    }
}
