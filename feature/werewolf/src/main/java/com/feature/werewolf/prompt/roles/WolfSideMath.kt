package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WinRule

/**
 * 狼队的屠边账——**只对狼**喂，按当前存活的神/民数动态算打哪边更快。
 *
 * 专栏那局（8 号猎人）的答案就藏在这道算术里：狼刀死 5、8 之后，神职只剩 6、7 里的少数，
 * 所以「打穿神边」近在眼前，猎人那一枪该打深水狼而不是女巫/守卫——那是好人的反向账。
 * 反过来站在狼的视角：每晚下刀前若不算这条，就会闷头追着预言家砍，明明民边只差一刀却看不见。
 *
 * 只喂**公开可推**的信息：板子构成是开局公布的、谁出局是播报过的。
 * 绝不读某个存活玩家的具体身份——狼不知道谁是哪个神，账本只能算「还活着几个神/民」，
 * 不能说「3 号是守卫所以去刀 4 号」。这条界限和 [WolfKillBrief] 完全一致。
 */
internal object WolfSideMath {

    /**
     * 屠边进度：还活着几张神、几个平民。
     *
     * **这是事实，不是打法，两种提示词风格都给。** 板子构成开局就公布、谁出局有播报，
     * 任何一个坐在牌桌上的人都数得出来——不给的后果是狼完全按「谁嘴最像预言家」下刀，
     * 从来没算过自己离屠边还差几张，一局刀到天亮才发现神边只死了一个。
     *
     * 只报数字，不报「该打哪边」——那是 [of] 的事，留给引导式。
     */
    fun counts(game: WerewolfGame): String? {
        if (game.config.winRule != WinRule.SLAY_SIDE) return null
        val godsAlive = game.alivePlayers.count { it.role.isGod }
        val villagersAlive = game.alivePlayers.count { it.role == WerewolfRole.VILLAGER }
        if (godsAlive == 0 && villagersAlive == 0) return null
        return "【屠边进度】按板子构成和已经播报的出局数，场上大约还有 $godsAlive 张神牌、" +
            "$villagersAlive 个平民（你不知道谁具体是哪张，只能靠发言去猜）。" +
            "屠边是杀光其中一边，不是把人头砍够数。"
    }

    /** 屠城局没有「边」可屠，返回空。屠边局才给狼算账。 */
    fun of(game: WerewolfGame): List<String> {
        if (game.config.winRule != WinRule.SLAY_SIDE) return emptyList()

        // 存活好人按「神 / 平民」分。神职 = isGod（预女猎守白痴熊），剩下的是平民。
        // 白痴翻牌后仍在场，但已失去投票权；屠边数的是「还活着的身份」，他仍算一张神牌，
        // 所以这里直接用 isGod，不额外剔除——规则层判胜负也是这么数的（见 checkWin）。
        val godsAlive = game.alivePlayers.count { it.role.isGod }
        val villagersAlive = game.alivePlayers.count {
            it.role == WerewolfRole.VILLAGER
        }
        if (godsAlive == 0 && villagersAlive == 0) return emptyList()

        val lines = mutableListOf<String>()
        // 计数由 [counts] 单独给出（两种风格都给），这里只负责「哪边近、怎么打」。
        lines += "【屠边账（只对你们有用）】哪边更近就先打穿哪边，不必追着预言家一个人砍。"

        when {
            villagersAlive == 0 -> lines += "平民已经清完了——赢的就是这一刻，剩下只要别让好人把狼全投出去。"
            godsAlive == 0 -> lines += "神职已经清完——屠边已经达成，别再浪费刀。"
            villagersAlive < godsAlive -> lines += "**平民这边更近**：再打 $villagersAlive 刀就能打穿。" +
                "平民没有守卫护着，砍外置位话少的人往往比硬刀预言家省一刀。" +
                "但别以为跳了身份的就一定是神——残局有人会**穿神衣服挡刀**，" +
                "尤其别把刚跳明、又给不出具体依据的那张当成真神放弃。"
            godsAlive < villagersAlive -> lines += "**神职这边更近**：再打 $godsAlive 刀就能打穿。" +
                "但神职会抱团跳身份挡枪、还可能有守卫护着，硬砍要挑刚跳出来、又露了底的那张。"
            else -> lines += "两边一样近（各 $godsAlive 张）：先砍**已经跳明身份又没人保**的那张，" +
                "把好人逼到要么救、要么认，再顺着票型找下一刀。"
        }

        // 好人会反过来用「谁在保谁」找狼（见公共打法里的读人框架）。
        // 狼得知道自己保人会被记着，才不会一夜间连保同一张牌。
        lines += "提醒：白天谁保了谁、谁一直替某张牌说话，好人会拿来盘你们的关系。" +
            "要保就换着保、给得出理由，别整局只护同一个人。"
        return lines
    }
}
