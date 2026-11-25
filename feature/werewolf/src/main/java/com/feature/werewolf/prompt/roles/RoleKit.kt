package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfRole

/**
 * 一个身份在提示词里的全部职责，按四个清晰边界分层：
 * - [rules]：公开硬规则（所有人可见，只写客观机制）；
 * - [publicStrategy]：公共推理层里与本身份相关的补充提醒；
 * - [playbook]：引导式专属打法（仅本人可见）；
 * - [notes]：本人动态私有事实（查验、药品、守护记录、狼队友与头狼分工）。
 *
 * [conceal] 默认返回 null（显式声明才给保密收尾），
 * 避免无意间给预言家或双药已空的女巫套上「以上信息不能在白天说出来」的禁言锁。
 */
internal interface RoleKit {

    val roles: Set<WerewolfRole>

    fun rules(role: WerewolfRole): List<String> = emptyList()

    fun publicStrategy(role: WerewolfRole): String? = null

    fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = emptyList()

    fun notes(game: WerewolfGame, seat: Int): List<String> = emptyList()

    fun notes(game: WerewolfGame, seat: Int, style: WerewolfPromptStyle): List<String> =
        notes(game, seat)

    fun conceal(role: WerewolfRole): String? = null
}

/** 身份 → kit。 */
internal object Roles {

    private val all: List<RoleKit> = listOf(
        WolfKit, SeerKit, WitchKit, GuardKit, HunterKit, IdiotKit, BearKit, VillagerKit,
    )

    fun of(role: WerewolfRole): RoleKit =
        all.firstOrNull { role in it.roles } ?: VillagerKit
}
