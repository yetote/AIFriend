package com.feature.agent.batch

import com.core.ai.model.ModelCost
import com.core.ai.model.ModelCosts
import com.core.ai.model.VoiceGroup
import com.core.persona.Persona
import com.core.persona.PersonaCategory
import com.feature.agent.model.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BatchRosterTest {

    @Test
    fun `模型只从传入的池子里挑`() {
        // 只要有一个模型不在调用方给的池子里，就说明这里偷偷扩大了范围——
        // 那个模型很可能属于没配 Key 的厂商，建出来的角色一开口就报错
        val providers = listOf(
            provider("DeepSeek", listOf("deepseek-chat", "deepseek-reasoner")),
            provider("Kimi", listOf("moonshot-v1-8k"))
        )
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿毒"), persona("p2", "小咪")),
            count = 4,
            providers = providers,
            modelsOf = { brand -> providers.first { it.brand == brand }.fallbackModels },
            voicesOf = { emptyList() },
            ttsIds = emptySet(),
            random = Random(7)
        )

        assertEquals(4, plan.size)
        val poolByBrand = providers.associate { it.brand to it.fallbackModels.toSet() }
        plan.forEach { slot ->
            val pool = poolByBrand[slot.brand]
            assertTrue("分配到了没配过 Key 的厂商：${slot.brand}", pool != null)
            assertTrue("模型 ${slot.model} 不在 ${slot.brand} 的池子里", pool!!.contains(slot.model))
        }
    }

    @Test
    fun `每家厂商都会被轮到`() {
        // 纯随机会出现「八个人全落在一家」，用户特意配了好几家就等于白配
        val providers = listOf(
            provider("A", listOf("a1")),
            provider("B", listOf("b1")),
            provider("C", listOf("c1"))
        )
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿毒")),
            count = 3,
            providers = providers,
            modelsOf = { brand -> providers.first { it.brand == brand }.fallbackModels },
            voicesOf = { emptyList() },
            ttsIds = emptySet(),
            random = Random(1)
        )

        assertEquals(setOf("A", "B", "C"), plan.map { it.brand }.toSet())
    }

    @Test
    fun `名字不重复，撞名时加序号`() {
        // 模板只有几十个，建十个必然撞名；列表里两个「小咪」分不清谁是谁
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "小咪"), persona("p2", "阿毒")),
            count = 5,
            providers = listOf(provider("A", listOf("a1"))),
            modelsOf = { listOf("a1") },
            voicesOf = { emptyList() },
            ttsIds = emptySet(),
            random = Random(3)
        )

        val names = plan.map { it.name }
        assertEquals("名字必须两两不同：$names", names.size, names.toSet().size)
        assertTrue(names.all { it.startsWith("小咪") || it.startsWith("阿毒") })
    }

    @Test
    fun `不和已有好友重名`() {
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿毒")),
            count = 2,
            providers = listOf(provider("A", listOf("a1"))),
            modelsOf = { listOf("a1") },
            voicesOf = { emptyList() },
            ttsIds = emptySet(),
            existingNames = setOf("阿毒"),
            random = Random(5)
        )

        assertTrue("已有好友不能被顶掉名字", plan.none { it.name == "阿毒" })
        assertEquals(setOf("阿毒2", "阿毒3"), plan.map { it.name }.toSet())
    }

    @Test
    fun `同一批里音色不重复`() {
        val voices = (1..5).map { "v$it" }
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿毒")),
            count = 4,
            providers = listOf(provider("A", listOf("a1"))),
            modelsOf = { listOf("a1") },
            voicesOf = { voices },
            ttsIds = setOf("minimax"),
            random = Random(11)
        )

        assertEquals(4, plan.mapNotNull { it.voiceId }.toSet().size)
        assertTrue(plan.all { it.ttsConfigId == "minimax" })
    }

    @Test
    fun `音色池不够时允许重复，但不编造 ID`() {
        // 编一个不存在的音色会让合成直接失败，比几个人同声更糟
        val voices = listOf("v1", "v2")
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿毒")),
            count = 5,
            providers = listOf(provider("A", listOf("a1"))),
            modelsOf = { listOf("a1") },
            voicesOf = { voices },
            ttsIds = setOf("minimax"),
            random = Random(13)
        )

        assertTrue(plan.all { it.voiceId in voices })
    }

    @Test
    fun `没有配语音时不分配音色`() {
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿毒")),
            count = 2,
            providers = listOf(provider("A", listOf("a1"))),
            modelsOf = { listOf("a1") },
            voicesOf = { emptyList() },
            ttsIds = emptySet(),
            random = Random(17)
        )

        assertNull(plan.first().ttsConfigId)
        assertNull(plan.first().voiceId)
    }

    @Test
    fun `某家语音没有音色时不会被轮到`() {
        // 有一家配了凭证但音色目录空（自定义网关），分给它就等于没分配
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿毒")),
            count = 2,
            providers = listOf(provider("A", listOf("a1"))),
            modelsOf = { listOf("a1") },
            voicesOf = { id -> if (id == "minimax") listOf("v1", "v2") else emptyList() },
            ttsIds = setOf("minimax", "custom"),
            random = Random(19)
        )

        assertTrue(plan.all { it.ttsConfigId == "minimax" })
    }

    @Test
    fun `没模板没厂商或数量为零都返回空`() {
        val provider = provider("A", listOf("a1"))
        assertTrue(
            BatchRoster.plan(
                personas = emptyList(), count = 3, providers = listOf(provider),
                modelsOf = { listOf("a1") }, voicesOf = { emptyList() }, ttsIds = emptySet()
            ).isEmpty()
        )
        assertTrue(
            BatchRoster.plan(
                personas = listOf(persona("p1", "阿毒")), count = 3, providers = emptyList(),
                modelsOf = { listOf("a1") }, voicesOf = { emptyList() }, ttsIds = emptySet()
            ).isEmpty()
        )
        assertTrue(
            BatchRoster.plan(
                personas = listOf(persona("p1", "阿毒")), count = 0, providers = listOf(provider),
                modelsOf = { listOf("a1") }, voicesOf = { emptyList() }, ttsIds = emptySet()
            ).isEmpty()
        )
    }

    @Test
    fun `模型池全空的厂商会被跳过`() {
        // 建一个 model 为空串的角色，聊天时必然报错，而它在列表里看着很正常
        val empty = Provider(brand = "Empty", company = "Empty", defaultModel = "", fallbackModels = emptyList())
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿毒")),
            count = 3,
            providers = listOf(provider("A", listOf("a1")), empty),
            modelsOf = { brand -> if (brand == "A") listOf("a1") else emptyList() },
            voicesOf = { emptyList() },
            ttsIds = emptySet(),
            random = Random(29)
        )

        assertEquals(3, plan.size)
        assertTrue("不该分给没有模型的厂商", plan.all { it.brand == "A" })
    }

    @Test
    fun `模型池为空时退回厂商的默认模型`() {
        // 模型列表还没拉回来，也不能建一个 model 为空串的角色
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿毒")),
            count = 1,
            providers = listOf(provider("A", listOf("a1"))),
            modelsOf = { emptyList() },
            voicesOf = { emptyList() },
            ttsIds = emptySet(),
            random = Random(23)
        )

        assertEquals("a1", plan.single().model)
    }

    @Test
    fun `批量只从自家模型里挑 避开平台代理来但没开通的模型`() {
        // 方舟、百炼的 /models 会把代理来的别家模型一起列出来（方舟上有 Qwen），
        // 那些模型要在控制台单独开通，没开通调用直接 404。批量是随机挑的，
        // 挑中一个就是「建完当天说不了话」，而角色在列表里看着完全正常
        val doubao = Provider(
            brand = "豆包",
            company = "字节跳动",
            defaultModel = "doubao-seed-evolving",
            fallbackModels = listOf("doubao-seed-evolving"),
            ownModelPrefixes = listOf("doubao")
        )
        val pool = listOf(
            "doubao-seed-evolving",
            "doubao-seed-2-1-pro-260915",
            "qwen3-0-6b-20250429",
            "qwen2-5-72b-20240919"
        )
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿豆"), persona("p2", "小豆")),
            count = 6,
            providers = listOf(doubao),
            modelsOf = { pool },
            voicesOf = { emptyList() },
            ttsIds = emptySet(),
            random = Random(11)
        )

        assertEquals(6, plan.size)
        assertTrue("挑到了代理来但未必开通的模型：${plan.map { it.model }}", plan.all { it.model.startsWith("doubao-") })
    }

    @Test
    fun `池子里没有自家模型时退回全池`() {
        // 前缀只是「优先」，不是白名单：一家都匹配不上还硬筛，会建出模型为空串的角色
        val provider = Provider(
            brand = "星火",
            company = "科大讯飞",
            defaultModel = "x1",
            fallbackModels = listOf("x1"),
            ownModelPrefixes = listOf("x1")
        )
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿火")),
            count = 2,
            providers = listOf(provider),
            modelsOf = { listOf("4.0Ultra", "generalv3.5") },
            voicesOf = { emptyList() },
            ttsIds = emptySet(),
            random = Random(13)
        )

        assertEquals(2, plan.size)
        assertTrue(plan.all { it.model in setOf("4.0Ultra", "generalv3.5") })
    }

    @Test
    fun `聚合平台没有自家模型 全池可选`() {
        val gateway = Provider(
            brand = "硅基流动",
            company = "硅基流动",
            defaultModel = "deepseek-ai/DeepSeek-V3",
            fallbackModels = listOf("deepseek-ai/DeepSeek-V3"),
            ownModelPrefixes = emptyList(),
            isGateway = true
        )
        val pool = listOf("deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B-Instruct", "THUDM/glm-4-9b-chat")
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿网")),
            count = 3,
            providers = listOf(gateway),
            modelsOf = { pool },
            voicesOf = { emptyList() },
            ttsIds = emptySet(),
            random = Random(17)
        )

        assertTrue(plan.all { it.model in pool })
    }

    @Test
    fun `关掉付费后只挑轻量档的模型`() {
        val providers = listOf(
            provider("豆包", listOf("doubao-seed-2.1-pro", "doubao-seed-2.1-lite"))
        )
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿省"), persona("p2", "小抠")),
            count = 4,
            providers = providers,
            modelsOf = { brand -> providers.first { it.brand == brand }.fallbackModels },
            voicesOf = { emptyList() },
            ttsIds = emptySet(),
            allowPaid = false,
            costOf = { _, model -> ModelCosts.of("doubao", model) },
            random = Random(11)
        )

        assertEquals(4, plan.size)
        assertTrue("挑到了付费模型：${plan.map { it.model }}", plan.all { it.model.endsWith("-lite") })
    }

    @Test
    fun `关掉付费后查不到档位的厂商整个跳过`() {
        // 混元只有 hy3 / hy4，名字里没有任何档位词，查不到价 —— 不能当免费用
        val providers = listOf(
            provider("混元", listOf("hy4", "hy3")),
            provider("豆包", listOf("doubao-seed-2.1-lite"))
        )
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿省"), persona("p2", "小抠")),
            count = 4,
            providers = providers,
            modelsOf = { brand -> providers.first { it.brand == brand }.fallbackModels },
            voicesOf = { emptyList() },
            ttsIds = emptySet(),
            allowPaid = false,
            costOf = { _, model -> ModelCosts.fromName(model) },
            random = Random(5)
        )

        assertEquals(4, plan.size)
        // 只剩豆包一家，四个人都该落在豆包上；混元一个都不能出现
        assertTrue(plan.all { it.brand == "豆包" })
    }

    @Test
    fun `一家都挑不出免费模型时 一个都不建`() {
        val providers = listOf(provider("混元", listOf("hy4")))
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿省")),
            count = 3,
            providers = providers,
            modelsOf = { brand -> providers.first { it.brand == brand }.fallbackModels },
            voicesOf = { emptyList() },
            ttsIds = emptySet(),
            allowPaid = false,
            costOf = { _, model -> ModelCosts.fromName(model) },
            random = Random(9)
        )

        assertTrue("宁可一个不建，也不能悄悄退回付费模型", plan.isEmpty())
    }

    @Test
    fun `默认允许付费 模型池不受成本影响`() {
        val providers = listOf(provider("豆包", listOf("doubao-seed-2.1-pro", "doubao-seed-2.1-lite")))
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "阿用")),
            count = 6,
            providers = providers,
            modelsOf = { brand -> providers.first { it.brand == brand }.fallbackModels },
            voicesOf = { emptyList() },
            ttsIds = emptySet(),
            // 刻意给一个「全都超贵」的判定：开关没关就不该筛掉任何一个
            costOf = { _, _ -> ModelCost.PREMIUM },
            random = Random(13)
        )

        assertEquals(6, plan.size)
        assertTrue(plan.any { it.model.endsWith("-pro") })
    }

    private fun persona(id: String, name: String) = Persona(
        id = id,
        name = name,
        role = "角色",
        prompt = "设定",
        category = PersonaCategory.CHAT
    )

    @Test
    fun `音色按角色性别分配`() {
        // 「大小姐」配一把男声：用户一进语音就听出来，而列表上完全看不出问题。
        // 音色目录本来就按性别分组，所以分配时要按分组挑。
        val voices = listOf("male-1", "male-2", "female-1", "female-2")
        val groups = mapOf(
            "male-1" to VoiceGroup.MALE,
            "male-2" to VoiceGroup.MALE,
            "female-1" to VoiceGroup.FEMALE,
            "female-2" to VoiceGroup.FEMALE,
        )
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "大小姐"), persona("p2", "审哥")),
            count = 2,
            providers = listOf(provider("DeepSeek", listOf("deepseek-chat"))),
            modelsOf = { listOf("deepseek-chat") },
            voicesOf = { voices },
            voiceGroupOf = { _, id -> groups[id] },
            ttsIds = setOf("minimax"),
            random = Random(7),
        )

        val lady = plan.first { it.name.startsWith("大小姐") }
        val bro = plan.first { it.name.startsWith("审哥") }
        assertTrue("女角色要配女声，实际 ${lady.voiceId}", lady.voiceId!!.startsWith("female"))
        assertTrue("男角色要配男声，实际 ${bro.voiceId}", bro.voiceId!!.startsWith("male"))
    }

    @Test
    fun `没有同性别音色时退回全池，而不是不配音`() {
        // 只有男声可选时，给一把男声也好过没有声音
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "大小姐")),
            count = 1,
            providers = listOf(provider("DeepSeek", listOf("deepseek-chat"))),
            modelsOf = { listOf("deepseek-chat") },
            voicesOf = { listOf("male-1") },
            voiceGroupOf = { _, _ -> VoiceGroup.MALE },
            ttsIds = setOf("minimax"),
            random = Random(7),
        )

        assertEquals("male-1", plan.single().voiceId)
    }

    @Test
    fun `判断不出性别时不强行挑组`() {
        // 工具型角色（记账管家、面试官）本来就没有性别，
        // 硬猜只会配错，保持原来的随机分配
        val plan = BatchRoster.plan(
            personas = listOf(persona("p1", "账本")),
            count = 1,
            providers = listOf(provider("DeepSeek", listOf("deepseek-chat"))),
            modelsOf = { listOf("deepseek-chat") },
            voicesOf = { listOf("male-1", "female-1") },
            voiceGroupOf = { _, id -> if (id == "male-1") VoiceGroup.MALE else VoiceGroup.FEMALE },
            ttsIds = setOf("minimax"),
            random = Random(7),
        )

        assertTrue(plan.single().voiceId in listOf("male-1", "female-1"))
    }

    private fun provider(brand: String, models: List<String>) = Provider(
        brand = brand,
        company = brand,
        defaultModel = models.first(),
        fallbackModels = models
    )
}
