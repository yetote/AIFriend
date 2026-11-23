package com.feature.werewolf.prompt.boards

import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig

/**
 * 一张板子的「特殊性」：由**人数与阵营结构**决定、且确实影响决策的东西。
 *
 * 和 `prompt/roles` 的分工是刻意的：
 * - 身份规则（守卫怎么守、狼王那一枪怎么算）挂在**身份**上——同一张板子换一个人就不该变；
 * - 板型打法（几狼几神几民、打哪边更省刀、容错几次）挂在**板子**上——同样的身份换一张板子就该变。
 *
 * 分错层的后果很具体：把「平民只有 3 个」写进守卫规则，那么同样的守卫放到 12 人局里
 * 就会开始胡说；把「守卫不能连守」写进板子，那么 10 人和 12 人两张守卫局各要抄一遍。
 *
 * 这里只写「这张板子跟默认打法不一样的地方」。表里没有的板子（自定义局、新板子还没配）
 * 走 [Boards.text] 的空实现，不硬编。
 */
internal interface BoardProfile {

    /** 对应 [WerewolfBoards] 里的 key。 */
    val key: String

    fun text(config: WerewolfConfig): BoardText
}

/**
 * 一张板子的两段话。
 *
 * 拆成两段是因为读者不同：[common] 是双方都要用的算术（几刀打穿哪边、容错几次），
 * [wolves] 是只对狼有用的打法（打哪边更省、要不要分头站边）。
 * 把狼的打法写给好人会稀释他们自己的判断，所以按阵营分开。
 */
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
        Fool12Board,
        Bear12Board,
    )

    /**
     * 按**规则**认板子，不按 key。
     *
     * 提示词只拿得到 [WerewolfConfig]，拿不到玩家选的是哪张卡片（配置里没有 key，
     * 存进存档也不该有——那是界面概念）。比对三样东西：身份构成、有没有警长、胜负规则。
     *
     * 三样都对上才算同一张板子：板型打法里写着「打民边只要 3 刀」「警徽落在谁手上」，
     * 这些结论只在**屠边 + 这个人数结构**下成立。换胜负规则的自定义局宁可什么都不说，
     * 也不能把别张板子的结论套上去。
     */
    fun of(config: WerewolfConfig): BoardProfile? = all.firstOrNull {
        val board = WerewolfBoards.configOf(it.key)
        board.composition == config.composition &&
            board.hasSheriff == config.hasSheriff &&
            board.winRule == config.winRule
    }

    fun text(config: WerewolfConfig): BoardText =
        of(config)?.text(config) ?: BoardText()

    /**
     * 警长流程。
     *
     * 它不属于任何一个身份（谁当上都一样），也不属于某一张板子（10 人和 12 人都开），
     * 只由 [WerewolfConfig.hasSheriff] 决定，所以单独放在这里。
     */
    fun sheriffRules(config: WerewolfConfig): List<String> = if (!config.hasSheriff) {
        emptyList()
    } else {
        listOf(
            "本局有警长：第 1 天**先竞选、后公布昨夜死讯**——想当的人上警、各说一段，" +
                "再由**没上警的人**投票选出；竞选和投票结束时才公布昨夜谁出局。" +
                // 「单爆吞白天，双爆吞警徽」：竞选被打断不等于警徽没了，
                // 第二天开场还是先上警，不写清楚会被当成流程出错。
                "竞选被自爆打断就**顺延到下一个白天**重新竞选，连续两次被打断（双爆）才作废。" +
                "上警只是**举手报名**，报名时不用说话，每个人的第一次发言就是竞选发言——" +
                "**不要拿「他上警前没说话」当质疑点**。" +
                "警长的一票算 1.5 票，并且决定当天从谁开始发言：警左 / 警右（他自己最后说），" +
                "或死左 / 死右（从昨夜死者起）。" +
                // 警徽没了的日子也要说：竞选被自爆炸掉、警长死了没交接，
                // 这两件事都会让「今天谁先说」变成随机
                "**没有警长时**（警徽还在顺延、双爆作废、警长出局后没人接手），" +
                "发言起点和方向由系统随机指定，不再从死者下位起。" +
                "警长出局时**必须处理警徽**：移交给一个存活玩家，或者撕掉——" +
                "不管怎么死的都躲不过这一步（被枪带走也一样，只是那时没有遗言可留）。"
        )
    }
}
