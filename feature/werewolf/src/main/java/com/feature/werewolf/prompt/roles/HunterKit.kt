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
        "死之前可以留下判断，但别替好人做决定：你只剩一票的价值，浪费在嘴上就没人替你说话了。",
    )

    override fun conceal(role: WerewolfRole): String =
        "你的猎人身份不要主动说出来，除非你已经开枪——开枪之后就藏不住了。"
}
