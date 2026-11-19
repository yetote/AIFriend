package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole

/** 白痴：替好人挡一次放逐，代价是翻牌后失去投票权。 */
internal object IdiotKit : RoleKit {

    override val roles = setOf(WerewolfRole.IDIOT)

    override fun rules(role: WerewolfRole): List<String> = listOf(
        "白痴被投票放逐时翻开身份，不会死，但从此不能再投票、也不能再被投；夜里被刀或被毒照常会死。"
    )

    override fun notes(game: WerewolfGame, seat: Int): List<String> = if (game.idiotRevealed) {
        listOf(
            "你已经翻过牌了：所有人都知道你是白痴，你还能说话，但**不能再投票**，也不会再被投。",
            "你现在唯一有用的地方，是继续用发言帮好人指认狼人。",
        )
    } else {
        listOf(
            "你是白痴：如果被投票投出，你会翻开身份但不死；代价是从此失去投票权。",
            "所以被投不是灾难，但也不能因此乱说话——翻牌之后你就只剩一张嘴了。",
        )
    }

    override fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = listOf(
        "你已经翻牌了，现在只有一张嘴可用：继续用发言帮好人指认狼人。",
        "你不能投票，所以你的判断要说得比别人更具体：指出谁的话有矛盾，别只喊口号。",
    )

    override fun conceal(role: WerewolfRole): String =
        "不要在白天主动说自己是白痴：翻牌虽然不死，但你会立刻失去投票权，狼人也就不必再怕你。"
}
