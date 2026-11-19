package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfRole

/**
 * 一个身份在提示词里要说的全部话。
 *
 * **按身份拆，而不是按板子拆。** 板子只是「身份的一个集合」：守卫规则只取决于
 * 「这张板子有没有守卫」，而 8 张板子里三张都带守卫——按板子写就要抄三遍，
 * 改一句得记得全改。拆到身份之后，新加一张板子只要在 `WerewolfBoards` 里声明构成，
 * 提示词自动就对了；板子特有的那点东西（人数结构、警长、票权）放在 `prompt/boards`。
 *
 * 四段的分工，回答的是四个不同的问题：
 * - [rules] 机制：这个身份在场时，**所有玩家**都该知道的规则（守卫怎么守、狼王那一枪怎么算）；
 * - [publicStrategy] 公共打法里跟这个身份有关的那一句（比如「守卫不能说自己守过谁」）；
 * - [playbook] 白天打法：**只有本人**看得到，「我今天该说什么」；
 * - [notes] 私有简报：**只有本人**看得到，而且依赖局面（查验记录、药品、守人、队友）。
 */
internal interface RoleKit {

    /** 这份 kit 负责的身份。狼和狼王共用一份，所以是集合。 */
    val roles: Set<WerewolfRole>

    /**
     * 板子规则。
     *
     * [role] 是**触发这一条的那个身份**：狼和狼王共用一份 kit，但只有狼王在场时才该出现
     * 「狼王被票出去能开枪」。
     */
    fun rules(role: WerewolfRole): List<String> = emptyList()

    /**
     * 公共打法里跟这个身份有关的那一句，接在「神职不要主动报身份」后面。
     *
     * 只有守卫有：别人守不守人跟好人无关，但**守卫自己**最容易一开口就交代守人路线。
     * 返回 null 表示这个身份没有要补的。
     */
    fun publicStrategy(role: WerewolfRole): String? = null
    fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = emptyList()

    fun notes(game: WerewolfGame, seat: Int): List<String> = emptyList()

    /**
     * 私有简报，按提示词风格取舍。
     *
     * 默认 STYLE 不参与——绝大多数身份的 `notes` 全是**事实**（验过谁、药剩几瓶），
     * 自由式也照给。只有狼的 `notes` 里混了刀法分析和屠边账，需要覆盖这个重载
     * 把打法剔掉、把队友名单这类事实留下。
     */
    fun notes(game: WerewolfGame, seat: Int, style: WerewolfPromptStyle): List<String> =
        notes(game, seat)

    /** 「这些不能说出来」那句收尾；null 表示这个身份没有要藏的（平民）。 */
    fun conceal(role: WerewolfRole): String? = "以上信息不能在白天直接说出来。"
}

/** 身份 → kit。认不出来的身份按平民处理，宁可少说，不能给错信息。 */
internal object Roles {

    private val all: List<RoleKit> = listOf(
        WolfKit, SeerKit, WitchKit, GuardKit, HunterKit, IdiotKit, BearKit, VillagerKit,
    )

    fun of(role: WerewolfRole): RoleKit =
        all.firstOrNull { role in it.roles } ?: VillagerKit
}
