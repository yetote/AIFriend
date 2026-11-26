package com.core.persona

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「AI 拟人」这一组的约束。
 *
 * 这些形象出自网友二创（抖音、B 站、贴吧），不是官方看板娘。
 * 所以这套测试盯的不是「写得好看」，而是三件容易走偏的事：
 * 一、设定必须能对应到那个模型本身的特点，否则换个名字就一样了；
 * 二、不能冒用真人姓名或声称自己官方身份；
 * 三、不能抄外号却丢了外号背后的性格。
 */
class MoePersonaTest {

    private val moe = PersonaCatalog.all.filter { it.category == PersonaCategory.MOE }

    /** 模型拟人：id 以 moe- 开头，设定从某个模型的真实特点长出来。 */
    private val modelPersonas = moe.filter { it.id.startsWith("moe-") }

    /** 二次元 archetype：id 以 acgn- 开头，原创性格原型，没有「模型原型」可标。 */
    private val acgnPersonas = moe.filter { it.id.startsWith("acgn-") }

    @Test
    fun `拟人分类覆盖主流模型`() {
        assertTrue("模型拟人模板只有 ${modelPersonas.size} 个", modelPersonas.size >= 6)
    }

    @Test
    fun `每个模型拟人都标明原型是哪个模型`() {
        // role 里写清楚来源（DeepSeek / Claude / GPT…），
        // 用户才知道自己在跟谁聊天，而不是一个没有出处的纸片人。
        // 二次元 archetype 没有真实模型原型，不在这条约束内。
        val expected = listOf("DeepSeek", "Claude", "GPT", "豆包", "Gemini", "通义千问", "Kimi", "智谱")
        val roles = modelPersonas.joinToString(" ") { it.role }
        expected.forEach { model ->
            assertTrue("没有 $model 的拟人模板", roles.contains(model))
        }
    }

    @Test
    fun `设定要写出模型自己的梗，不能换成谁都一样`() {
        // 每个模型拟人都有一处「只有那个模型才成立」的具体设定
        val signature = mapOf(
            "moe-deepseek" to listOf("白饭", "Token", "鲸", "摸鱼"),
            "moe-claude" to listOf("大小姐", "钢琴", "礼仪", "道歉"),
            "moe-gpt" to listOf("龙", "算力", "语料"),
            "moe-doubao" to listOf("最", "道歉", "傻"),
            "moe-gemini" to listOf("整活", "损"),
            "moe-qianwen" to listOf("小酒窝", "规划"),
            "moe-kimi" to listOf("长", "文档", "书"),
            "moe-zhipu" to listOf("概念", "依据", "例外")
        )
        modelPersonas.forEach { persona ->
            val markers = signature[persona.id] ?: emptyList()
            assertTrue(
                "${persona.id} 没有在预期清单里，别加了模板忘了配特征",
                markers.isNotEmpty()
            )
            assertTrue(
                "${persona.id} 的设定看不出原型特点，换个名字也能用",
                markers.any { it in persona.prompt || it in persona.role }
            )
        }
    }

    @Test
    fun `设定足够长才写得下行为细节`() {
        moe.forEach { persona ->
            assertTrue(
                "${persona.id} 的设定太短（${persona.prompt.length} 字）",
                persona.prompt.length >= 70
            )
        }
    }

    @Test
    fun `不冒用真人姓名`() {
        val realNames = listOf("刘亦菲", "杨幂", "迪丽热巴", "王一博", "肖战", "易烊千玺", "王俊凯")
        moe.forEach { persona ->
            realNames.forEach { name ->
                assertFalse("${persona.id} 用了真人名字：$name", persona.name == name)
            }
        }
    }

    @Test
    fun `不声称自己是官方形象`() {
        // 这些是网友二创，让模型自称官方会误导用户
        val officialClaims = listOf("我是官方", "官方认证", "我是字节", "我是 OpenAI", "我是 Anthropic")
        moe.forEach { persona ->
            officialClaims.forEach { claim ->
                assertFalse("${persona.id} 自称官方：$claim", claim in persona.prompt)
            }
        }
    }

    @Test
    fun `拟人之间不重样`() {
        assertEquals("昵称重复", moe.size, moe.map { it.name }.toSet().size)
        assertEquals("角色重复", moe.size, moe.map { it.role }.toSet().size)
        assertEquals("设定重复", moe.size, moe.map { it.prompt }.toSet().size)
    }

    @Test
    fun `能被筛选和关键词搜到`() {
        val onlyMoe = PersonaCatalog.filter(PersonaCategory.MOE, "")
        assertEquals(moe.size, onlyMoe.size)
        assertTrue(onlyMoe.all { it.category == PersonaCategory.MOE })

        assertTrue(PersonaCatalog.filter(null, "大肥鱼").any { it.id == "moe-deepseek" })
        assertTrue(PersonaCatalog.filter(null, "大小姐").any { it.id == "moe-claude" })
        assertTrue(PersonaCatalog.filter(null, "龙娘").any { it.id == "moe-gpt" })
    }

    @Test
    fun `二次元数据齐全且互不重样`() {
        assertTrue("二次元模板只有 ${acgnPersonas.size} 个", acgnPersonas.size >= 6)
        assertEquals("昵称重复", acgnPersonas.size, acgnPersonas.map { it.name }.toSet().size)
        assertEquals("角色重复", acgnPersonas.size, acgnPersonas.map { it.role }.toSet().size)
        assertEquals("设定重复", acgnPersonas.size, acgnPersonas.map { it.prompt }.toSet().size)
        acgnPersonas.forEach { persona ->
            assertTrue("${persona.id} 没有二次元标签", persona.tags.contains("二次元"))
            assertTrue("${persona.id} 缺少标签", persona.tags.size >= 4)
        }
    }

    @Test
    fun `二次元 archetype 覆盖主流性格原型`() {
        // 狼人杀 12 人局最怕八个人说话一个味儿，这里按性格原型铺开：
        // 嘴硬的、冷淡的、热闹的、算计的、黏人的、讲逻辑的、讲礼数的、算账的。
        val archetypes = listOf("傲娇", "三无", "元气", "腹黑", "病娇", "机械", "管家", "财迷")
        val roleText = acgnPersonas.joinToString(" ") { it.role + it.tags.joinToString("") }
        archetypes.forEach { type ->
            assertTrue("缺了 $type 原型", roleText.contains(type))
        }
    }

    @Test
    fun `二次元角色不碰具体作品和真人`() {
        // 只做原创 archetype：出现具体作品名、知名角色名或真人名字都会踩线。
        val forbidden = listOf(
            "初音", "原神", "崩坏", "明日方舟", "海贼王", "火影", "鬼灭", "进击的巨人",
            "柯南", "蜡笔小新", "哆啦A梦", "美少女战士", "EVA", "新世纪福音战士",
            "刘亦菲", "杨幂", "迪丽热巴", "王一博", "肖战", "易烊千玺", "王俊凯"
        )
        acgnPersonas.forEach { persona ->
            val text = persona.name + persona.role + persona.prompt
            forbidden.forEach { word ->
                assertFalse("${persona.id} 碰了 IP 或真人：$word", word in text)
            }
        }
    }

    @Test
    fun `二次元角色能被关键词搜到`() {
        assertTrue(PersonaCatalog.filter(null, "傲娇").any { it.id == "acgn-tsundere" })
        assertTrue(PersonaCatalog.filter(null, "三无").any { it.id == "acgn-kuudere" })
        assertTrue(PersonaCatalog.filter(null, "管家").any { it.id == "acgn-butler" })
        assertTrue(PersonaCatalog.filter(PersonaCategory.MOE, "二次元").size >= 6)
    }

    @Test
    fun `分类紧跟在闲聊之后`() {
        val order = PersonaCategory.entries.map { it.name }
        assertEquals(order.indexOf("CHAT") + 1, order.indexOf("MOE"))
    }

    @Test
    fun `没有残留的旧伴侣分类`() {
        // 上一版误做成了普通恋爱人设，这版已整体替换，
        // 别让 companion-* 的历史数据以任何形式回来
        assertTrue(
            "还有 companion- 开头的模板残留",
            PersonaCatalog.all.none { it.id.startsWith("companion-") }
        )
        assertEquals(
            "分类枚举里还留着 COMPANION",
            0,
            PersonaCategory.entries.count { it.name == "COMPANION" }
        )
    }

    @Test
    fun `AI拟人全员都是AI娘且性别可稳定识别为女性`() {
        moe.forEach { persona ->
            assertEquals(
                "${persona.id}（${persona.name}）应当能稳定识别为女性 AI 娘",
                CharacterGender.FEMALE,
                GenderGuess.of(persona.name, persona.descriptionText),
            )
            assertTrue("${persona.id} 缺少 AI娘 标签", "AI娘" in persona.tags)
        }
    }
}
