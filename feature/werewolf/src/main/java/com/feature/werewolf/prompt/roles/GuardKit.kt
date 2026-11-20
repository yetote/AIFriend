package com.feature.werewolf.prompt.roles

import com.feature.werewolf.engine.isNight
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfRole

/**
 * 守卫。
 *
 * 规则（[rules]）写在这里，狼那边的**反制**写在 [WolfKillBrief]：同一条「不能连守」，
 * 守卫要用来安排今晚守谁，狼要用来判断哪一刀不会落空。
 */
internal object GuardKit : RoleKit {

    override val roles = setOf(WerewolfRole.GUARD)

    // 守救同归必须对**所有人**说：牌桌上看起来就是「明明守了又救了，怎么还是死了」。
    // 两句拼成一行，是因为它们在板子说明里本来就是同一段连续的机制描述。
    override fun rules(role: WerewolfRole): List<String> = listOf(
        "守卫每夜先于狼人守一个人，被守的人当夜不会被刀死；不能连续两夜守同一个人，可以守自己。" +
            "守卫和解药同时作用在一个人身上时，那个人**还是会死**（守救同归）。",
    )

    override fun publicStrategy(role: WerewolfRole): String =
        "守卫尤其不能说自己守过谁——那等于把守人路线和剩下的选择交给狼队，平安夜也不能当证据。"

    override fun notes(game: WerewolfGame, seat: Int): List<String> =
        notes(game, seat, WerewolfPromptStyle.GUIDED)

    override fun notes(game: WerewolfGame, seat: Int, style: WerewolfPromptStyle): List<String> {
        val guided = style == WerewolfPromptStyle.GUIDED
        // 白天不能读 lastGuardedSeat：`startNextNight()` 是**白天结束**时才把它换成
        // 上一夜的守人，所以白天这一刻它指向的是「前夜的前夜」。
        // 实测后果很严重——守卫在白天照着这个字段发言，报的守人对象整整差一夜
        // （第 2 天说守 5 号实守 4 号、第 4 天说守 6 号实守 10 号），
        // 好人据此认定他「前后矛盾、在编身份」，把真守卫票了出去。
        // 白天该看的是 tonightGuardedSeat，它只在跨夜时被搬走。
        val lastNight = if (game.phase.isNight) game.lastGuardedSeat else game.tonightGuardedSeat
        val lines = mutableListOf<String>()
        lines += if (lastNight == null) {
            "你昨夜没有守人，今晚任何人都可以守。"
        } else {
            "你昨夜守的是 $lastNight 号，今晚不能再守他。"
        }
        if (game.phase == Phase.NIGHT_GUARD) {
            lines += "现在轮到你守护。你不知道今晚狼人会刀谁，只能凭白天的发言去猜。"
        }
        lines += "你守的人当夜不会被刀死，但你自己也可能被刀——守自己同样是合法选择。"
        lines += "你救下人的时候不会被告知，平安夜也可能只是狼人空刀。"
        // 「平安夜别当证据、守过谁别报出来」是藏身打法：报不报守人是守卫自己的
        // 战术自由，自由模式里由他决定；引导式才提醒泄露风险。
        if (guided) {
            lines += "平安夜不要在白天当成证据说出来，也不要报出你守过谁：" +
                "那等于把守人路线和剩下的选择一起交给狼队。"
        }
        return lines
    }

    override fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = listOf(
        "你是守卫，你的价值在夜里，不在嘴上。",
        "白天不要报守过谁，也不要暗示「我昨晚有信息」——那等于把守人路线交出去。",
        "守谁：优先守你认为最像预言家或最关键的那个号，必要时守自己。",
        "被人怀疑身份时别急着辩解。过度解释本身就是破绽，一句「我没信息」就够了。",
        "守人的时候别只盯着「谁像预言家」：**突然跳出来、专挑活人发金水的预言家**要留个心眼——" +
            "那是悍跳狼最省力的做法。守错人等于白丢一个守护。",
    )
}
