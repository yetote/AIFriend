package com.feature.werewolf.contract

/**
 * 狼人杀内部的页面切换。
 *
 * 和 `HomeScreen` 一样用 sealed interface 表达，而不是引入导航库：
 * 目前只有「配置」和「对局」两个页面，且对局期间不希望被系统返回键直接弹掉，
 * 自己持有状态反而好控制。
 */
sealed interface WerewolfScreen {
    /** 开局配置：选玩法、板子、参与者。 */
    data object Setup : WerewolfScreen

    /** 对局进行中（牌桌未落地前先用进度页顶上）。 */
    data object Table : WerewolfScreen
}
