package com.feature.werewolf.game

/**
 * 天亮之后的下一段：第一天死者要留遗言，遗言说完才进白天讨论。
 *
 * 大多数规则用例想验的是「天亮了没有、能不能投票」，不关心遗言这一段，
 * 所以统一从这里过一手。遗言本身（谁该说、说了之后去哪）在
 * `WerewolfLastWordsTest` 里单独验，不靠这个辅助函数兜着。
 */
internal fun WerewolfGame.atDiscussion(): WerewolfGame =
    if (phase == Phase.DAY_LAST_WORDS) finishLastWords() else this

/**
 * 女巫行动 + 天亮结算（一步走完）。
 *
 * 生产代码里女巫只写用药决定、不结算，死亡统一到 DAWN_SETTLE 节点算（三路并发的前提）。
 * 规则层用例想验的是「用药后天亮的结果」，所以这里把两步并成一步，
 * 相当于替这些用例走了责任链的 DAWN_SETTLE 节点。
 */
internal fun WerewolfGame.witchActSettled(
    heal: Boolean,
    poisonTarget: Int? = null,
): WerewolfGame = witchAct(heal, poisonTarget).settlePendingNight()

/** 停在 DAWN_SETTLE 就走完结算；不在则原样返回。规则用例据此越过天亮节点。 */
internal fun WerewolfGame.atDawn(): WerewolfGame =
    if (phase == Phase.DAWN_SETTLE) settlePendingNight() else this
