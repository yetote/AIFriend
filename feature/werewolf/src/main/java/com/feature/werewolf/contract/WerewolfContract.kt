package com.feature.werewolf.contract

/**
 * 狼人杀内部的页面切换。
 *
 * 和 `HomeScreen` 一样用 sealed interface 表达，而不是引入导航库：
 * 页面只有三个，而且对局期间不希望被系统返回键直接弹掉，自己持有状态反而好控制。
 *
 * [History] 是入口页而不是 [Setup]：一局可能打到一半，进来先问「接着打还是重新开」，
 * 比直接铺开配置页（或者更糟：直接把人扔回昨晚那张牌桌上）要合理。
 */
sealed interface WerewolfScreen {

    /** 历史对局：继续未打完的一局、翻已结束的复盘，或者新开一局。 */
    data object History : WerewolfScreen

    /** 开局配置：选玩法、板子、参与者。 */
    data object Setup : WerewolfScreen

    /** 对局进行中（牌桌未落地前先用进度页顶上）。 */
    data object Table : WerewolfScreen

    /**
     * 提示词微调：玩家为这一局写的额外设定。
     *
     * 从开局配置页进——那个页面上正挑着板子，「按这张板子微调」才说得清。
     */
    data object PromptSettings : WerewolfScreen
}
