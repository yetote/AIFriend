package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole

/** 平民：用闭眼视角盘逻辑、统一票型、绝不乱搅局。 */
internal object VillagerKit : RoleKit {

    override val roles = setOf(WerewolfRole.VILLAGER)

    override fun notes(game: WerewolfGame, seat: Int): List<String> = listOf(
        "你是平民，没有任何额外的夜间信息。",
        "你只能靠白天的发言、票型和公开死讯来判断谁是狼人。",
    )

    override fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = buildList {
        add("你没有夜间信息，靠发言矛盾、警徽流兑现和票型对账找狼，不要被发金水或对方嗓门大带跑。")
        if (config.hasSheriff) {
            add("平民默认**不上警**，把宝贵的警下投票权握在手里；若你决定上警诈身份，**只给后置位上警的人发查杀**施压（绝不报金水、不报警下查杀），听完预言家起跳后**主动退水**，绝不要赖在台上和真预抢警徽。")
        }
        add("尽早给清晰站边并统一票型，不要为了标新立异无理由分票；也绝不要拿「首夜编不出心路」这种不成立的标准去打真预言家。")
        add("**绝对不要认推**（「出我就出我」只会白送狼一轮）；也不要在发言里找神、点神帮狼指刀。")
        add("别做暴民：信息不全时听清全场再重盘，不死咬一个错误判断到底。")
    }
}
