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
