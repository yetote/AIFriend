package com.feature.werewolf.prompt.roles

import com.feature.werewolf.engine.isNight
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfRole

/**
 * 女巫：掌握解药（银水）与毒药（追轮次）的强神。
 *
 * 重点解决真局里的两类翻车：
 * 1. 把首夜银水伪装成「我是真预言家」去警上抢警徽还不退水，直接撞废真预言家；
 * 2. 双药用完后仍死守「不能报身份」，导致被当假预抗推、甚至被队友猎人一枪带走。
 */
internal object WitchKit : RoleKit {

    override val roles = setOf(WerewolfRole.WITCH)

    override fun rules(role: WerewolfRole): List<String> = listOf(
        "女巫有一瓶解药和一瓶毒药，每种只能用一次；同一夜不能同时用两瓶药。",
        "解药能救活当夜被狼刀的人，救不了自己——只有本局开局允许时，第一夜才能自救" +
            "（女巫在夜里会被告知今晚到底能不能救自己，不用自己猜）。",
        "毒药对任何人都有效，守卫守着的人也照样被毒死；被毒死的猎人不能开枪。",
    )

    override fun notes(game: WerewolfGame, seat: Int): List<String> =
        notes(game, seat, WerewolfPromptStyle.GUIDED)

    override fun notes(game: WerewolfGame, seat: Int, style: WerewolfPromptStyle): List<String> {
        val guided = style == WerewolfPromptStyle.GUIDED
        val lines = mutableListOf<String>()
        lines += "解药：" + if (game.witch.antidoteAvailable) "还在" else "已经用完"
        lines += "毒药：" + if (game.witch.poisonAvailable) "还在" else "已经用完"
        usageHistory(game)?.let { lines += it }

        if (game.phase == Phase.NIGHT_WITCH) {
            val victim = game.tonightVictim
            val canSelfHeal = game.witch.antidoteAvailable &&
                game.day == 1 && game.config.witchCanSelfHealOnFirstNight
            when {
                !game.witch.antidoteAvailable ->
                    if (game.witch.poisonAvailable) {
                        lines += "解药已经用完：今晚谁被袭击、还是没有人被袭击，你都不会被告知。你只剩毒药的抉择。"
                    } else {
                        lines += "两瓶药都已用完，今晚没有可以做的事。"
                    }

                victim == null ->
                    lines += "今晚没有人被狼人袭击，解药这次没有用处，你可以只用毒药或什么都不做。"

                victim == seat && canSelfHeal ->
                    lines += "**今晚被狼人袭击的就是你自己（$seat 号）。** 第一夜你还能自救：用解药就活下来，不用就当场出局。"

                victim == seat -> {
                    lines += "**今晚被狼人袭击的就是你自己（$seat 号），而你已经救不了自己——天一亮你就出局了。**"
                    if (game.witch.poisonAvailable) {
                        lines += "这是你最后一次行动：毒药现在不用就跟着你一起作废，想清楚最该带走谁。"
                    }
                }

                else -> {
                    lines += "今晚被狼人袭击的是 $victim 号。你可以救他，也可以不救。"
                }
            }
        } else if (guided && !game.phase.isNight) {
            // 白天按当前药品余量给清晰的起跳状态，避免「双药已空仍死藏」
            when {
                !game.witch.antidoteAvailable && !game.witch.poisonAvailable ->
                    lines += "【当前状态：双药已空】你两瓶药都已用完，不再有需要替狼保密的底牌。" +
                        "若你被怀疑、真预言家被冲、或双死夜大家搞混了谁吃刀谁吃毒，**请直接拍明女巫身份**，" +
                        "把每夜救了谁、毒了谁完整交代清楚，帮好人排坑、避免猎人误枪带走你。"

                !game.witch.antidoteAvailable && game.witch.poisonAvailable ->
                    lines += "【当前状态：解药已用、毒药还在】你手里握着银水和毒药威慑：" +
                        "局势混乱或你被踩时，可以阳光起跳女巫报银水带队归票；但绝不要冒充真预言家。"
            }
        }

        lines += if (game.config.witchCanSelfHealOnFirstNight) {
            "解药只能用一次，能救活当夜被狼刀的人；第一夜你被刀还能自救，之后就不能了。"
        } else {
            "解药只能用一次，能救活当夜被狼刀的人；本局**任何时候都不能自救**，包括第一夜。"
        }
        lines += "毒药只能用一次，同一夜不能和解药一起用；毒药无视守护，守的人也照毒死。"
        return lines
    }

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
            "你救过的人大概率是好人（但狼会自刀骗解药，救到只说明那一夜他是狼刀目标）；你毒死的人是你自己下手的。"
    }

    override fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = listOf(
        "**严禁冒充真预言家抢警徽**：绝不要把自己的银水包装成「我是真预言家、昨晚验的 X 号是好人」，更不要不退水硬抢警徽——那会直接和真预言家撞车，把好人阵营搅崩。",
        "**始终牢记你的用药记录**：谁是你救的银水、谁是你亲手毒的，白天推理必须和它一致——绝不能忘了自己毒过谁，反过来因为别人猜中毒口就把银水打成狼。",
        "首夜通常适合开解药救人保轮次；解药还在且局势平稳时先藏好女巫身份，不轻易暴露以免吃首刀。",
        "用掉解药后可以阳光起跳女巫报银水带队（帮你和银水双双做实好人面，并压缩狼坑）；手里捏着毒药本身就是威慑，关键轮次可直接起跳归票。",
        "两瓶药都用完、或你被查杀眼看要被抗推时，立刻拍明女巫身份报出完整银水与毒口——狼知道刀口反驳不了你，守卫也能侧面坐实，绝不憋到出局或挨猎人误枪。",
        "毒药极其珍贵，只毒高度确定的狼（例如和你对跳女巫却报不出正确银水的人、或坐实的悍跳狼），宁可晚一夜也别盲毒好人。",
        "若首夜被刀且本局不能自救，上警时直接跳女巫报清刀口与信息，别把信息带进坟墓。",
    )
}
