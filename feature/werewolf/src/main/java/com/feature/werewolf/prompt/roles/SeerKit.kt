package com.feature.werewolf.prompt.roles

import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.prompt.seatsText

/** 预言家：全场唯一能产出「硬信息」的人，所以白天的话术比判断更重要。 */
internal object SeerKit : RoleKit {

    override val roles = setOf(WerewolfRole.SEER)

    override fun notes(game: WerewolfGame, seat: Int): List<String> {
        val lines = mutableListOf<String>()
        if (game.seerChecks.isEmpty()) {
            lines += "你还没有查验过任何人。"
        } else {
            lines += "你的查验记录（只有你知道）："
            game.seerChecks.forEach { check ->
                lines += "第 ${check.day} 夜 · 查验 ${check.targetSeat} 号 → " +
                    if (check.isWerewolf) "狼人" else "好人"
            }
        }
        val checked = game.seerChecks.map { it.targetSeat }.toSet()
        val unknown = game.aliveSeats.filter { it != seat && it !in checked }
        if (unknown.isNotEmpty()) {
            lines += "还没查验过的存活玩家：" + seatsText(unknown)
        }
        return lines
    }

    override fun playbook(role: WerewolfRole, config: WerewolfConfig): List<String> = listOf(
        "你的白天发言只有三件任务：报验人、给警徽流、解释验人逻辑。",
        "开口第一句必须是「昨晚我验了 X 号，他是好人/狼人」——跳过这一句，" +
            "别人就没有任何依据判断你是不是真预言家，今天的票会全部落到别人身上。",
        "报完验人立刻给**警徽流**：先验了谁、后验了谁、为什么按这个顺序。" +
            "警徽流是你唯一的身份凭证，顺序一乱，别人就当你乱验或者在编。",
        "**警徽流只报你真的验过的人**，还没验的不要写，更不要为了凑出一条警徽流编一个。" +
            "第 1 天你只验了一个人，那就在警徽流里只写这一个——" +
            "「只验了 X，Y 还没验」比编一套完整的警徽流安全得多，" +
            "编出来的警徽流只要对不上，全场就认定你是悍跳。",
        "每个验人都要讲清**验人逻辑**：为什么今天验他、你从他身上看到了什么疑点。" +
            "只报结果不解释，等于自己把预言家的可信度打折。",
        "被质疑时不要急着喊「我是好人」：先报完整的警徽流，再指出对方逻辑里的具体漏洞。",
        "**被指认时，咬的是那个跳预言家的人**，不是跟着他说话的人。" +
            "你临死前咬错人（去咬好人、放过悍跳狼），场上最后一点疑点也跟着你一起走了。",
        "警徽流要接得上：今天跳了明天也要跳，除非你被刀或者被投出局。" +
            "昨天报的验人今天必须能对上，中途改口等于自己承认昨天在编。",
    )
}
