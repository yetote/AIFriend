package com.feature.agent.batch

import com.core.ai.model.ModelCost
import com.core.ai.model.ModelCosts
import com.core.ai.model.VoiceGroup
import com.core.persona.CharacterGender
import com.core.persona.GenderGuess
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
        /**
         * 某个音色的分组（女声 / 男声 / 童声…）。返回 null 表示这个音色没有分组信息。
         *
         * 有了它才能按性别配音色：音色目录里女声男声是分开的，
         * 不看分组就随机挑，「大小姐」会配一把男声——用户在语音里一听就出戏，
         * 而列表上完全看不出来哪里不对。
         */
        voiceGroupOf: (ttsId: String, voiceId: String) -> VoiceGroup? = { _, _ -> null },
        /** 允许分配音色的 TTS 服务（已配凭证的）。 */
        ttsIds: Set<String>,
        /** 已有好友的名字：新角色不能和它们重名。 */
        existingNames: Set<String> = emptySet(),
        /**
         * 允不允许给角色配付费模型。
         *
         * 关掉（false）时只从「免费 / 轻量档」里挑，查不到档位的模型一律不入选：
         * 一个模型查不到价，最可能的原因就是它本来就要钱。
         */
        allowPaid: Boolean = true,
        /** 某厂商某模型的档位；价格买不到时由调用方按名字判断后传进来。 */
        costOf: (brand: String, model: String) -> ModelCost = { _, _ -> ModelCost.UNKNOWN },
        random: Random = Random.Default,
    ): List<BatchSlot> {
        // 每个厂商先算出「这次允许挑的模型池」。
        // 池子空的厂商直接跳过：建出来 model 是空串，聊天时必然报错，
        // 而这种角色在列表里看着是正常的，用户根本查不出原因。
        // 关掉付费模型后池子被筛空，也是同一个道理 —— 那个厂商这次一个都不建。
        val pools = providers.associate { provider ->
            provider.brand to allowedModels(provider, modelsOf, costOf, allowPaid)
        }
        val usable = providers.filter { pools[it.brand].orEmpty().isNotEmpty() }
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

            val modelPool = pools[provider.brand].orEmpty()
            val model = pickFree(modelPool, usedModels, { "${provider.brand}|$it" }, random)
                ?: provider.defaultModel

            val ttsId = ttsOrder.takeIf { it.isNotEmpty() }?.let { it[index % it.size] }
            val voicePool = ttsId?.let { voicesOf(it) }.orEmpty()
            // 按性别挑音色：先在同性别分组里挑，挑不到再退回全池。
            // 退回而不是报错——没有女声可选时，给一把男声也好过没有声音。
            val preferred = when (GenderGuess.of(persona.name, persona.descriptionText)) {
                CharacterGender.FEMALE -> VoiceGroup.FEMALE
                CharacterGender.MALE -> VoiceGroup.MALE
                CharacterGender.UNKNOWN -> null
            }
            val matched = preferred?.let { group ->
                ttsId?.let { id -> voicePool.filter { voiceGroupOf(id, it) == group } }
            }.orEmpty()
            val pool = matched.ifEmpty { voicePool }
            val voice = pickFree(pool, usedVoices, { "$ttsId|$it" }, random)

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

    /**
     * 这一家这次允许挑哪些模型。
     *
     * 两层筛选，顺序不能换：
     * 1. 先只留自家模型（[preferOwnBrand]）—— 聚合平台列出来的别家模型常常没开通，
     *    挑中就是 404；
     * 2. 再按成本筛（[allowPaid] 关掉时只留免费 / 轻量档）。
     *
     * 筛完为空就返回空：调用方据此把这家整个跳过，而不是退回到「全池」——
     * 用户关掉付费模型，就是想避开贵的，悄悄退回去等于骗他。
     */
    private fun allowedModels(
        provider: Provider,
        modelsOf: (String) -> List<String>,
        costOf: (String, String) -> ModelCost,
        allowPaid: Boolean,
    ): List<String> {
        val loaded = preferOwnBrand(provider, modelsOf(provider.brand))
            .filter { it.isNotBlank() }
        val pool = loaded.ifEmpty { provider.fallbackModels.filter { it.isNotBlank() } }
        if (allowPaid) return pool
        return pool.filter { model -> ModelCosts.isBudget(costOf(provider.brand, model)) }
    }

    /**
     * 只留自家模型；一家都匹配不上时退回全池。
     *
     * 方舟、百炼、TokenHub 的 `GET /models` 会把代理来的别家模型一起列出来
     * （方舟上就有 Qwen、DeepSeek），这些模型要在控制台单独「开通」，
     * 没开通调用直接 404。随机挑中一个，角色建完全天说不了话，而列表里看着正常。
     * 自家模型通常随账号自动开通，随机池只用它们，才符合「建完就能聊」。
     *
     * 聚合平台没有自家模型（前缀为空），不做筛选。
     */
    private fun preferOwnBrand(provider: Provider, pool: List<String>): List<String> {
        val prefixes = provider.ownModelPrefixes
        if (prefixes.isEmpty()) return pool
        val own = pool.filter { id -> prefixes.any { id.startsWith(it, ignoreCase = true) } }
        return own.ifEmpty { pool }
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
