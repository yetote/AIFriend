package com.feature.werewolf.game

/** 死亡原因。规则里唯一要看原因的地方是猎人：只有被刀、被票出可以开枪，被毒不能。 */
enum class DeathCause {
    /** 夜里被狼人刀死。 */
    WOLF_KILL,

    /** 被女巫毒死。 */
    WITCH_POISON,

    /** 白天被投票放逐。 */
    EXILE,

    /** 被猎人开枪带走。 */
    HUNTER_SHOOT,

    /**
     * 狼人白天自爆。
     *
     * 单独成一个原因而不是复用 [EXILE]：自爆是狼人主动结束白天，
     * 复盘时要能一眼看出「这天不是投出来的，是被自爆打断的」。
     */
    SELF_EXPLODE,
}

/**
 * 一位玩家在局内的完整状态。
 *
 * [id] 对应好友的持久化 id，是这局游戏与「角色人设」之间唯一的连接点；
 * 规则层只认 [seat]，因为号码才是玩家之间互相称呼、投票指代的东西。
 */
data class WerewolfPlayer(
    /** 座位号，从 1 开始。 */
    val seat: Int,
    /** 对应 Friend.id，用于取人设、头像、模型配置。 */
    val id: String,
    /** 显示名。AI 之间互相称呼用的就是这个名字。 */
    val name: String,
    val role: WerewolfRole,
    val alive: Boolean = true,
    val deathCause: DeathCause? = null,
    val deathDay: Int? = null,
) {
    val faction: Faction get() = role.faction
}

/**
 * 开局名单的一项：只描述「谁坐上这个位子」，不含身份。
 *
 * 身份由 [WerewolfGame.deal] 洗牌后分配，名单本身不携带任何隐藏信息，
 * 所以它可以安全地给 UI 展示。
 */
data class RosterEntry(
    val id: String,
    val name: String,
)

/**
 * 人类座位的固定 id。
 *
 * 它落在存档里，是「这一局我坐在桌上」的唯一凭据——列表页要靠它分辨
 * 一局是「我参与的」还是「纯观战的」，续档时也要靠它找回自己的座位。
 * 放在领域层而不是 ViewModel 里，是因为读存档的一方（存档层）也需要它。
 */
const val SELF_PLAYER_ID = "__self__"
