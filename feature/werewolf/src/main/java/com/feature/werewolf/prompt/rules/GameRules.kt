package com.feature.werewolf.prompt.rules

import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WinRule

/**
 * 全局硬规则（唯一事实源）：胜负、绑票、守救同归、警长竞选与退水、投票、发言顺序、遗言、暗牌、自爆。
 *
 * 只写客观机制，不夹带任何打法倾向；引导式与自由式共用同一份规则。
 * 警长流程统一收口在这里，不再在板子说明里重复第二遍。
 */
internal fun rulesBrief(config: WerewolfConfig): String {
    val win = when (config.winRule) {
        WinRule.SLAY_SIDE -> buildString {
            append("【胜负规则】屠边局：狼人杀光全部神职，或杀光全部平民即获胜；好人放逐全部狼人则好人获胜。\n")
            val gods = WerewolfRole.entries.filter { it.isGod && config.has(it) }
            if (gods.isEmpty()) {
                append("本局没有神职：杀光全部平民即算狼胜。\n")
            } else {
                append("本局算神的是：").append(gods.joinToString("、") { it.displayName })
                append("——全部出局才算屠神（白痴翻牌不算出局，翻牌后再死才算）；狼人（含狼王）永远不算神，其余好人为平民。\n")
            }
            if (gods.isNotEmpty() && config.has(WerewolfRole.VILLAGER)) {
                append("屠边需选一条边打穿，神民分着刀最容易两边都差一刀。\n")
            }
        }

        WinRule.SLAY_ALL ->
            "【胜负规则】屠城局：狼人票权不少于好人票权时狼人获胜（警长 1.5 票）；好人放逐全部狼人则好人获胜。\n"
    }

    return buildString {
        append(win)
        append("【绑票】狼人票权不少于好人票权时，狼人直接获胜、游戏当场结束（按票权算，不按人头）。\n")
        append(mutualProtectionBrief(config))
        if (config.hasSheriff) {
            append("【警长与退水】第 1 天（及竞选被单爆顺延的次日）**先竞选、后公布昨夜死讯**，死讯公布前昨夜倒牌者照常参与上警、竞选与警长投票（若当选则在公布死讯后立即处理警徽）。")
            append("上警只是举手报名、报名时不说话（不要拿「上警前没说话」当质疑点）。")
            append("首轮竞选发言后有一次**退水**：退水者不再被投，首轮也无警长票（上过警者首轮均不投）；")
            append("全员退水、全员上警或警下全员弃票则警徽流失，仅剩一人不退则直接当选。若警长票平票则平票者 PK 发言并重投（**PK 发言后不再退水**，**PK 轮台上不投、台外所有在场玩家含退水者均可投**），两轮平票则警徽流失。")
            append("警长一票算 1.5 票；警长出局时**必须处理警徽**（移交给一名存活玩家或撕掉，被枪带走也照常处理）。\n")
            append("【白天投票】白天放逐**不能投自己**，可弃票。平票直接加投一轮（不开 PK 发言），由台外有票权者在平票候选中重投；再平或无台外投票者则当天无人出局。\n")
            append("【发言顺序】**每轮白天讨论与警长竞选每人仅限发言一次**，按顺序依次说完即进入投票/退水，**不可插话、无二次补充或反驳机会**（仅警长票平票时平票者多一轮 PK 发言）；关键信息与表态须在本次发言一次性说清。")
            append("警长每天选起点方向（左 = 座位号增大，右 = 座位号减小）：**警左/警右**警长最后归票，")
            append("**死左/死右**从昨夜最小号死者旁起（平安夜仅可选警左/警右）。警长不在场时起点与方向由系统随机指定。\n")
            append("【静默决策】上警报名、退水、警长投票与白天放逐投票（含平票加投）均**只操作不发言**，且为**同时暗投、落票前互不可见**；切勿把想对全场说的话留到退水/投票时解释，也不要拿「某人报名/退水/投票时没说话」或「看别人投票后再跟票」作为怀疑依据。\n")
        } else {
            append("【白天投票】白天放逐**不能投自己**，可弃票。本局无警长、无加投：平票即当天无人出局。\n")
            append("【发言顺序】**每轮白天讨论每人仅限发言一次**，按顺序依次说完直接进入投票，**不可插话、无二次补充或反驳机会**；关键信息与表态须在本次发言一次性说清。")
            append("无警长主持，每天起点和方向由系统随机指定。\n")
            append("【静默决策】白天放逐投票**只投票不发言**，且为**同时暗投、落票前互不可见**；切勿把想对全场说的话留到投票时解释，也不要拿「某人投票时没说话」或「看别人投票后再跟票」作为怀疑依据。\n")
        }
        append("【遗言】第 1 夜死者、被投票放逐者、以及非被枪带走的警长有遗言；第 2 夜起夜死者无遗言；")
        append("**被猎人开枪带走的人无遗言**（若是警长仍需处理警徽）。")
        append("被投票放逐的猎人/狼王**先遗言、再开枪**；天亮被狼刀死的猎人**先开枪、再遗言**（仅首夜或警长）。\n")
        append(darkCardBrief(config))
        append("【自爆】狼人白天发言（含竞选/PK 发言）开头写「【自爆】」即当场永久出局并立刻入夜（当天无放逐）。")
        if (config.hasSheriff) {
            append("竞选/PK 期间自爆则竞选中止、警徽顺延到下一个白天；连续两次竞选被打断（双爆）警徽作废。")
        }
        append("自爆狼**当晚仍参与狼队商议与定刀**，定刀结束后彻底离场。\n")
        append("【夜刀】狼人多数决，平票加商一次；可刀任何人（含自刀、刀队友），也可空刀。")
    }
}

/** 守救同归硬规则：守卫与女巫同场时两档风格共用。 */
internal fun mutualProtectionBrief(config: WerewolfConfig): String =
    if (config.has(WerewolfRole.GUARD) && config.has(WerewolfRole.WITCH)) {
        "【守救同归】守卫守的人若同时被女巫解药救了，**仍然会死**（两种保护抵消）；守卫与女巫彼此看不到对方行动。\n"
    } else {
        ""
    }

/** 暗牌局硬规则：出局不翻牌，猎人开枪也不等于验出对方是狼。 */
internal fun darkCardBrief(config: WerewolfConfig): String = buildString {
    append("【底牌】本局是**暗牌局**：任何人出局都不公开底牌（夜死、放逐、被枪带走均不翻牌）。")
    if (config.has(WerewolfRole.IDIOT)) {
        append("唯一例外是白痴：被票出时翻牌亮明身份且不出局，此后失去投票权。\n")
    } else {
        append("本局没有白痴，不存在任何翻牌环节。\n")
    }
    append("**猎人开枪带走某人只代表猎人的怀疑，不等于「翻牌坐实对方是狼」**；任何人声称「某人翻出好人/狼」都不是事实。\n")
}

/** 精简术语约定，避免模型生造黑话。 */
internal fun terminologyBrief(): String = buildString {
    append("【术语约定】使用标准狼人杀术语，不生造身份或黑话：")
    append("金水＝预言家验出的好人；查杀＝预言家验出的狼；银水＝女巫解药救下的人；")
    append("悍跳＝狼冒充预言家；对跳＝多人自称同一身份；警徽流＝预言家计划接下来查验的顺序（不要叫「验人链」）；")
    append("站边＝支持某预言家；倒钩＝狼站真预踩队友；冲锋＝狼明推好人；归票＝末位统一票向；抗推＝推错/硬推嫌疑人；绑票＝狼票权压过好人直接胜。")
}

/** 「搜索式」最小事实集：仅版型与胜负。 */
internal fun boardAndWinBrief(config: WerewolfConfig): String = buildString {
    val parts = WerewolfRole.entries.mapNotNull { role ->
        val count = config.composition[role] ?: 0
        if (count > 0) "${role.displayName} ×$count" else null
    }
    append("【版型】本局 ").append(config.playerCount).append(" 人：")
    append(parts.joinToString("、")).append("。")
    if (config.hasSheriff) append("本局有警长竞选，**警长一票算 1.5 票**。")
    append("\n【胜负】")
    when (config.winRule) {
        WinRule.SLAY_SIDE ->
            append("屠边：狼人杀光**全部神职**或**全部平民**即获胜，好人放逐全部狼人即获胜。")

        WinRule.SLAY_ALL ->
            append("屠城：狼人票权不少于好人票权时获胜，好人放逐全部狼人即获胜。")
    }
    append("判断输赢一律按**票权**算，不按人头。\n")
}
