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
        lines += "【屠边账（只对你们有用）】这是屠边局：把神职全杀光、或把平民全杀光，你们就赢，" +
            "不必追着预言家一个人砍。现在场上大约还有 $godsAlive 张神牌、$villagersAlive 个平民" +
            "（这是按板子构成和已经播报的出局数出来的，你不知道谁具体是哪个身份）。"

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
