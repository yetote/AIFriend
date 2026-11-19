package com.feature.werewolf.prompt

import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole

/**
 * 残局里好人的「穿神衣服」——主动谎报一个神职身份，让狼把刀浪费在你身上、或者不敢动你。
 *
 * 只喂好人，不喂狼：狼要是读到「平民也会跳神」，就不会被这套伪装骗到。
 * 这不是信息不对称的漏洞——狼本来就会悍跳、本来就该怀疑所有跳身份的人，
 * 不给它们这份说明书，反而更接近真人局里「狼被平民的一件神衣服晃了一刀」的效果。
 *
 * 只在残局给：人多的时候跳神只会把自己架上焦点位，好人票不够、狼也一点不忌惮；
 * 只剩几张牌时每一刀都决定胜负，一条假身份就能让狼的屠边账算错。
 *
 * 必须写清「这是允许的伪装」：公共打法里有「神职不要主动报身份」，
 * 平民 kit 里又写着「你没有任何夜间信息」，不点明例外，模型会自我矛盾、
 * 或者跳完之后真以为自己有验人记录。
 */
internal object GoodBluff {

    /**
     * 存活总数 ≤ 6，或好人只剩 ≤ 3 张时才给。
     * 两个条件取或：12 人局掉到 6 人是残局，8 人局死到只剩 3 个好人也一样该拼。
     */
    private fun isEndgame(game: WerewolfGame): Boolean {
        val alive = game.alivePlayers.size
        val goodAlive = game.alivePlayers.count { !it.role.isWerewolf }
        return alive <= 6 || goodAlive <= 3
    }

    fun of(game: WerewolfGame, seat: Int): List<String> {
        val player = game.playerAt(seat)
        if (player.role.isWerewolf || !isEndgame(game)) return emptyList()

        return buildList {
            add("【残局挡刀：可以穿神的衣服】现在人已经不多了，每一刀都可能决定胜负。" +
                "这时候「神职不要主动报身份」有一条例外：你可以主动穿一件神的衣服来挡刀。")
            if (player.role == WerewolfRole.VILLAGER) {
                add("你是平民，却可以说自己是预言家、猎人、守卫或女巫中的某一个——" +
                    "这是策略性伪装，不违反身份保密：实际身份和你的投票判断仍然按平民来，" +
                    "只是让狼以为刀你能打穿神边，或者忌惮你而不敢动你。")
                add("怎么穿得像：给一个能自洽的理由（为什么验这个人、为什么守这个位），" +
                    "别编别人一问就穿帮的细节。优先挑一个**已经出局的神**来跳；" +
                    "别和还活着、又报得出完整验人记录的真预言家对跳——那不是挡刀，是送人头。")
            } else {
                add("你本身是神职，残局可以主动把身份跳明：要么带队把票砸实，" +
                    "要么让狼来刀你、替后置位挡一刀。到这一步，藏着已经不如站出来值钱。")
            }
            add("翻车点：跳了就别缩。从穿身份那一轮起，发言和投票都要像个神——" +
                "给判断、给理由、敢投票；只跳一次就改口说「我其实是平民」，" +
                "好人会当你心虚，狼也白捡一张票。")
        }
    }
}
