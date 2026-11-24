package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole

/** 女巫：两瓶药都是「一次性、不可逆」的资源，提示词的重点是别把药留到不会到来的明天。 */
internal object WitchKit : RoleKit {

    override val roles = setOf(WerewolfRole.WITCH)

    // 写进公共板子说明：女巫的药怎么用，直接影响**所有人**怎么读死讯。
    // 原来这几条只在女巫自己的 notes 里，别人一概不知道——于是「昨夜死了人、女巫没救」
    // 会被当成「场上没有女巫」，或者把「同夜死了两个」解释成见鬼。
    override fun rules(role: WerewolfRole): List<String> = listOf(
        "女巫有一瓶解药和一瓶毒药，每种只能用一次；同一夜不能同时用两瓶药。",
        "解药能救活当夜被狼刀的人，救不了自己——只有本局开局允许时，第一夜才能自救" +
            "（女巫在夜里会被告知今晚到底能不能救自己，不用自己猜）。",
        "毒药对任何人都有效，守卫守着的人也照样被毒死；被毒死的猎人不能开枪。",
    )

    override fun notes(game: WerewolfGame, seat: Int): List<String> {
        val lines = mutableListOf<String>()
        lines += "解药：" + if (game.witch.antidoteAvailable) "还在" else "已经用完"
        lines += "毒药：" + if (game.witch.poisonAvailable) "还在" else "已经用完"
        usageHistory(game)?.let { lines += it }
        if (game.phase == Phase.NIGHT_WITCH) {
            val victim = game.tonightVictim
            val canSelfHeal = game.witch.antidoteAvailable &&
                game.day == 1 && game.config.witchCanSelfHealOnFirstNight
            when {
                // 解药一空：夜里零信息。刀口、平安夜、甚至自己被刀都不说——
                // 这是用掉解药的代价。白天的死讯播报是全场公开的渠道。
                !game.witch.antidoteAvailable ->
                    if (game.witch.poisonAvailable) {
                        lines += "解药已经用完：今晚谁被袭击、还是没有人被袭击，你都不会被告知。" +
                            "你只剩毒药的抉择。"
                    } else {
                        lines += "两瓶药都已用完，今晚没有可以做的事。"
                    }

                victim == null ->
                    lines += "今晚没有人被狼人袭击，解药这次没有用处，你可以只用毒药或什么都不做。"

                victim == seat && canSelfHeal ->
                    lines += "**今晚被狼人袭击的就是你自己（$seat 号）。** " +
                        "第一夜你还能自救：用解药就活下来，不用就当场出局。"

                victim == seat -> {
                    // 必死的局面（解药还在但救不了自己）。以前这里只写「被袭击的是 2 号」，
                    // 模型按普通夜晚的思路答「这晚先不用药，毒药留到更关键的时候」——
                    // 可它天一亮就死了，「更关键的时候」永远不会到来，毒药跟着进棺材。
                    // 所以要明写「你今晚会死」和「这是最后机会」，不能只给客观信息。
                    lines += "**今晚被狼人袭击的就是你自己（$seat 号），而你已经救不了自己" +
                        "——天一亮你就出局了。**"
                    if (game.witch.poisonAvailable) {
                        lines += "这是你最后一次行动：毒药现在不用，就跟着你一起作废，" +
                            "以后永远没有第二次机会。想清楚最该带走谁，" +
                            "别把毒药省给一个不会到来的明天。"
                    }
                }

                else -> {
                    lines += "今晚被狼人袭击的是 $victim 号。"
                    lines += "你可以救他，也可以不救。解药只有一次，" +
                        "救他等于把这一次押在「他是好人」上。"
                }
            }
        }
        // 「不能自救」必须写成确定句。
        // 原来这里是一句无条件的「不能救自己（第一夜除外）」，而 rules 里又写「看本局设定」——
        // 女巫自己并不知道本局设定是什么，白天于是要靠猜：实测她会推演「我是不是自救的」，
        // 再顺着推出「狼知道我是女巫」这种假结论，整段推理全废。
        lines += if (game.config.witchCanSelfHealOnFirstNight) {
            "解药只能用一次，能救活当夜被狼刀的人；第一夜你被刀还能自救，之后就不能了。"
        } else {
            "解药只能用一次，能救活当夜被狼刀的人；本局**任何时候都不能自救**，包括第一夜。"
        }
        lines += "毒药只能用一次，同一夜不能和解药一起用；毒药无视守护，守的人也照毒死。"
        return lines
    }

    /**
     * 用药履历：你哪一夜救了谁、毒了谁。
     *
     * 原来这里只报「解药：已经用完」，等于告诉女巫药没了，却不告诉她药用在谁身上。
     * 代价在真局里很清楚：第一夜平安夜，她不知道那瓶药救的是 6 号，于是整段推理都在猜
     * 「我是不是自救的」，甚至推出「狼知道我是女巫」；第二天又卡在
     * 「私有信息只写了用完，没写目标」，只能从公开死讯倒推。
     *
     * 这是她自己做过的事，没有任何理由不给。它只说「我救/毒了谁」，
     * 不碰「那个人是什么身份」——狼可以自刀骗药，所以下面要明写这一点。
     *
     * 守救同归也必须写明白：救了、人还是死了，不解释的话她要么以为自己判断错，
     * 要么怀疑引擎算错了。
     */
    private fun usageHistory(game: WerewolfGame): String? {
        val used = game.nights.filter { it.healed || it.poisonTarget != null }
        if (used.isEmpty()) return null
        val text = used.joinToString("；") { night ->
            val acts = buildList {
                if (night.healed) {
                    val target = night.wolfTarget
                    add(
                        when {
                            target == null -> "用了解药，但记录里没有刀口"
                            night.mutualProtection ->
                                "用解药救了 $target 号，但守卫那一夜守的也是他，守救同归，他照样出局"
                            else -> "用解药救了 $target 号"
                        }
                    )
                }
                night.poisonTarget?.let { add("用毒药毒了 $it 号") }
            }
            "第 ${night.day} 夜" + acts.joinToString("、")
        }
        return "【你的用药记录】$text。" +
            "你救过的人不一定是好人——狼会自刀骗解药，救到只说明那一夜他是狼刀的目标。"
    }

    override fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = listOf(
        "你是女巫，白天要把自己藏起来。",
        "不要说自己救过谁、毒过谁，哪怕那是真的——被认出来的女巫下一夜就会被刀。",
        "不要用「我昨晚听说」「我猜他昨晚」这种暗示自己有夜间信息的话。",
        "首夜通常是开药的好时机：狼领先一个轮次太亏，万一刀中神职更是大损失；" +
            "救下的人叫银水，大概率是好人（但狼会自刀骗药，别无条件信）。",
        "毒药只有一次，宁可留到确定是狼的那天；救人也只救你确定该救的人，" +
            "别因为「他好像挺重要」就浪费解药。",
        // 权威女巫攻略：用过解药后阳光起跳报银水，能一次帮好人确定两张好人牌、压缩狼的生存空间。
        // 之前只有「被查杀才跳」的逃生门，缺了这条常规带队打法。
        "用掉解药之后可以阳光起跳报银水：你救的那个人大概率是好人，" +
            "等于帮全场排掉一张、也压缩了狼队空间；藏到死并不总是最优。",
        "有人跳女巫和你对跳、又报不出对得上的银水，多半是想找女巫的悍跳狼：" +
            "白天先忍住别跳，晚上直接用毒药送走他。",
        "捏着毒药本身就是威慑：局势乱、好人分不清时，可以起跳带队归票疑似狼，" +
            "用「再乱投我就毒你」压住冲锋的狼。",
        "你若首夜被刀又不能自救，上警时把这件事告诉好人——" +
            "女巫首刀是好人阵营的重大损失，让他们仔细听发言、别被悍跳带偏。",
        // 藏身份不是送头的理由：真局里女巫被查杀票出，到死没跳身份，
        // 全场只能听悍跳狼的查杀表演。下面这条就是那条路上的逃生门。
        "藏身份有一个例外：你被查杀、眼看要被抗推时，硬辩救不了你——" +
            "如果你昨晚救的人正好对得上「刀口没死」（平安夜），跳女巫报银水就是最硬的自证：" +
            "狼知道自己刀了谁，反驳不了你；守卫也能从「不是自己守的」侧面坐实。" +
            "解药已经用完的女巫暴露身份的代价小得多，别抱着一堆措辞疑点白给。",
    )
}
