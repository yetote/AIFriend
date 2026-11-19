package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole

/** 猎人：唯一的资源是最后一枪，所以白天全部的价值在于「别被提前认出来」。 */
internal object HunterKit : RoleKit {

    override val roles = setOf(WerewolfRole.HUNTER)

    override fun notes(game: WerewolfGame, seat: Int): List<String> = listOf(
        "你是猎人：被狼人杀死或被投票放逐时可以开枪带走一个人；如果是被女巫毒死的，不能开枪。"
    )

    override fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = listOf(
        "你是猎人，翻牌之前不要报身份。",
        "你最后一枪的价值最大：带走你**最怀疑是狼**的人，而不是声音最大、骂得最凶的人。",
        // 专栏那题的答案就在这条里：该打的是「一言未发的深水狼」和「冲锋狼」，
        // 不是跳得凶的、也不是救过人的女巫/守卫。给一套能落地的读人顺序，
        // 而不是让模型凭「最怀疑」三个字自由发挥——它往往就挑了嗓门最大的那个。
        "开枪前按这个顺序挑，别凭印象：先看**谁全程话少、没暴露身份却一直活着**（深水狼常躲在这里），" +
            "再看**谁最卖力地带节奏、把别人往死里打**（冲锋狼）；" +
            "这两类比「跳预言家跳得最凶的」和「救过人、守过人的神职」更可能是狼。" +
            "刚跳出来报了验人、又被全场对跳牵着走的，先想想他是不是在挡枪，别急着给他一枪。",
        "你这一枪是在**结算前**打的，可能直接改写胜负：拿不准时，宁可留给已经两三条都对上的那张牌，" +
            "也别打一个只是让你不爽的人。",
        "死之前可以留下判断，但别替好人做决定：你只剩一票的价值，浪费在嘴上就没人替你说话了。",
    )

    override fun conceal(role: WerewolfRole): String =
        "你的猎人身份不要主动说出来，除非你已经开枪——开枪之后就藏不住了。"
}
