package com.feature.agent.batch

import com.core.persona.Persona
import com.feature.agent.model.Provider
import kotlin.random.Random

/**
 * 批量创建时给一个角色配好的一整套：人设、名字、模型、音色。
 */
data class BatchSlot(
    val persona: Persona,
    /** 去重后的名字：模板撞名时自动加序号，列表里才不会有两个「小咪」。 */
    val name: String,
    val brand: String,
    val company: String,
    val model: String,
    /** 分配到的 TTS 服务；null = 这次不分配音色。 */
    val ttsConfigId: String?,
    /** 分配到的音色；null = 跟随该服务的默认音色。 */
    val voiceId: String?,
)

/**
 * 批量创建时的随机搭配。
 *
 * 三个约束，缺一个就会建出「看着有、其实用不了」的角色：
 *
 * 1. **只从传进来的池子里挑**。调用方传的已经是「配了 Key 的厂商」和
 *    「配了凭证的语音服务」，这里不会扩大范围。建一个没配 Key 的角色，
 *    用户点进去第一句话就是 401，而且看不出是创建时选错了。
 * 2. **名字不重复**。模板只有几十个，建十个必然撞名。
 * 3. **模型和音色尽量不重复**。八个角色一个声音就没意义了；挑不到干净的
 *    才允许重复，但绝不拼一个不存在的 ID——那会直接合成失败，比同声更糟。
 *
 * 纯函数、随机源可注入，所以能稳定测试。
 */
object BatchRoster {

    fun plan(
        personas: List<Persona>,
        count: Int,
        /** 已配置的模型厂商；空表示一个都没配，返回空列表。 */
        providers: List<Provider>,
        /** 某厂商的模型池：已加载的优先，调用方自己回落目录兜底。 */
        modelsOf: (String) -> List<String>,
        /** 某 TTS 服务的音色池，**不含**「跟随默认」那一项。 */
        voicesOf: (String) -> List<String>,
        /** 允许分配音色的 TTS 服务（已配凭证的）。 */
        ttsIds: Set<String>,
        /** 已有好友的名字：新角色不能和它们重名。 */
        existingNames: Set<String> = emptySet(),
        random: Random = Random.Default,
    ): List<BatchSlot> {
        // 模型池完全空的厂商直接跳过：建出来 model 是空串，聊天时必然报错，
        // 而这种角色在列表里看着是正常的，用户根本查不出原因
        val usable = providers.filter { provider ->
            provider.defaultModel.isNotBlank() ||
                modelsOf(provider.brand).any { it.isNotBlank() } ||
                provider.fallbackModels.any { it.isNotBlank() }
        }
        if (count <= 0 || personas.isEmpty() || usable.isEmpty()) return emptyList()

        val personaOrder = personas.shuffled(random)
        // 厂商和语音服务轮着来，不纯随机：纯随机会出现「八个人全落在一家」，
        // 用户特意配了好几家就等于白配了。
        val providerOrder = usable.shuffled(random)
        val ttsOrder = ttsIds.filter { voicesOf(it).isNotEmpty() }.sorted()

        val usedNames = existingNames.toMutableSet()
        val usedModels = mutableSetOf<String>()
        val usedVoices = mutableSetOf<String>()

        return (0 until count).map { index ->
            val persona = personaOrder[index % personaOrder.size]
            val provider = providerOrder[index % providerOrder.size]

            val modelPool = modelsOf(provider.brand)
                .filter { it.isNotBlank() }
                .ifEmpty { provider.fallbackModels.filter { it.isNotBlank() } }
            val model = pickFree(modelPool, usedModels, { "${provider.brand}|$it" }, random)
                ?: provider.defaultModel

            val ttsId = ttsOrder.takeIf { it.isNotEmpty() }?.let { it[index % it.size] }
            val voicePool = ttsId?.let { voicesOf(it) }.orEmpty()
            val voice = pickFree(voicePool, usedVoices, { "$ttsId|$it" }, random)

            BatchSlot(
                persona = persona,
                name = uniqueName(persona.name.ifBlank { DEFAULT_NAME }, usedNames),
                brand = provider.brand,
                company = provider.company,
                model = model,
                ttsConfigId = ttsId,
                voiceId = voice,
            )
        }
    }

    /** 先试没被占用的，池子都占满了再退回全池随机；池子本身为空就返回 null。 */
    private fun pickFree(
        pool: List<String>,
        used: MutableSet<String>,
        keyOf: (String) -> String,
        random: Random,
    ): String? {
        if (pool.isEmpty()) return null
        val free = pool.filter { keyOf(it) !in used }
        val picked = (free.ifEmpty { pool }).randomOrNull(random) ?: return null
        used += keyOf(picked)
        return picked
    }

    /** 撞名就加序号：小咪、小咪2、小咪3…… 而不是随机后缀，用户要能一眼认出来。 */
    private fun uniqueName(base: String, used: MutableSet<String>): String {
        var candidate = base
        var suffix = 2
        while (!used.add(candidate)) {
            candidate = "$base$suffix"
            suffix++
        }
        return candidate
    }

    /** 模板没填名字时的兜底，不能让角色列表里出现一个空格子。 */
    private const val DEFAULT_NAME = "AI 好友"
}
