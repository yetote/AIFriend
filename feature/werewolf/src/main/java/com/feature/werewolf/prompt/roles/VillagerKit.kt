package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole

/** 平民：没有任何夜间信息。明确写出来，比留一段空白好——空白会让模型编出不存在的信息。 */
internal object VillagerKit : RoleKit {

    override val roles = setOf(WerewolfRole.VILLAGER)

    override fun notes(game: WerewolfGame, seat: Int): List<String> = listOf(
        "你是平民，没有任何额外的夜间信息。",
        "你只能靠白天的发言、投票和死讯来判断谁是狼人。",
    )

    override fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = listOf(
        "你没有夜间信息，只能靠白天的发言和投票分辨。",
        "不要因为有人给你发金水就信他——发金水是狼最省力的伪装。",
        "找矛盾比听语气有用：谁前后不一致、谁的行为和身份不匹配，票就往那儿投。",
        "尽早站边、统一票型：站错还能改回来，但一直不站边，等于给狼一个「摇摆不定」的扛推位。" +
            "划水是最容易被抗推的理由——用闭眼视角把你的心路讲饱满，让人看到你在找狼。",
        // 权威平民守则：不能认推（主动求投自己=白送轮次，身份也不会变高）。
        "**绝对不要认推**（「出我就出我」）：你白送狼一个轮次，身份还不会因此变高。" +
            "你的任务是活下去、用发言和投票帮好人，谁出局都不能是你。",
        "别在发言里**找神、点神**（「谁是女巫」「谁像守卫」）——那等于帮狼点刀，身份会做差。",
        "别做暴民：逻辑没盘清就乱踩人、乱穿神衣服，在好人眼里狼面很大，会被直接抗推。",
        "能判断就一定要投出去；但完全分不清时宁可弃票，也别无理由乱投——" +
            "乱投的人第二天讲不清站边依据，反而会被当冲票的狼。",
    )

    /** 平民本来就没秘密，跟它说「这些不能说出来」只会让它以为自己藏了什么。 */
    override fun conceal(role: WerewolfRole): String? = null
}
