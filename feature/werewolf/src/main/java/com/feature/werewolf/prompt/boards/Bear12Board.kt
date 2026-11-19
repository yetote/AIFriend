package com.feature.werewolf.prompt.boards

import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig

/**
 * 12 人熊局：4 狼 + 预女猎白熊 + 3 民，开警长。
 *
 * 神有 5 个、民只有 3 个——这是所有板子里屠边最不平衡的一张，
 * 也是唯一一张「狼人不该按直觉打神」的板子。
 */
internal object Bear12Board : BoardProfile {

    override val key = WerewolfBoards.BEAR_12

    override fun text(config: WerewolfConfig) = BoardText(
        common = listOf(
            "12 人局平民只有 3 个、神有 5 个：狼人打民边只要 3 刀，比打神快两刀——" +
                "这是这张板子最容易被忽略的一条。"
        ),
        wolves = listOf(
            "打民边只要 3 刀，别被熊牵着走；但熊每天都替好人缩圈，越早刀掉越好，" +
                "刀不掉就在发言里把咆哮解释成「狼在别处」，别让好人顺着它缩圈。"
        ),
    )
}
