package com.feature.werewolf.prompt.boards

import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig

/**
 * 一张板子的结构性提示（几狼几神几民、容错与核心节奏）。
 *
 * 警长流程规则已统一收口在 `GameRules.rulesBrief` 中，这里不再重复一份，避免双份文案互相干扰。
 */
internal interface BoardProfile {

    val key: String

    fun text(config: WerewolfConfig): BoardText
}

internal data class BoardText(
    val common: List<String> = emptyList(),
    val wolves: List<String> = emptyList(),
)

internal object Boards {

    private val all: List<BoardProfile> = listOf(
        Quick6Board,
        Standard8Board,
        Standard9Board,
        Guard10Board,
        King10Board,
        Guard12Board,
        King12Board,
        Fool12Board,
        Bear12Board,
    )

    fun of(config: WerewolfConfig): BoardProfile? = all.firstOrNull {
        val board = WerewolfBoards.configOf(it.key)
        board.composition == config.composition &&
            board.hasSheriff == config.hasSheriff &&
            board.winRule == config.winRule
    }

    fun text(config: WerewolfConfig): BoardText =
        of(config)?.text(config) ?: BoardText()
}
