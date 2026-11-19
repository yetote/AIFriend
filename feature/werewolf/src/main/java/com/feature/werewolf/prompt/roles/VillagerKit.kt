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
        "别弃票。你的票不多，但好人常常是被自己的弃票输掉的。",
    )

    /** 平民本来就没秘密，跟它说「这些不能说出来」只会让它以为自己藏了什么。 */
    override fun conceal(role: WerewolfRole): String? = null
}
