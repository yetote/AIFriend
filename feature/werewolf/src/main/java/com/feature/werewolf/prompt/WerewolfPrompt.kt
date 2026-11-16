package com.feature.werewolf.prompt

import com.feature.werewolf.game.Faction
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WinRule

/**
 * 每个玩家的「入场提示词」。
 *
 * 这个类是整套编排里最容易被低估的一处。狼人杀的上下文没法像聊天室那样靠历史消息撑着，
 * 原因有两条：
 *
 * 1. **身份不能靠记忆。** 历史一旦按 token 预算裁剪，AI 就会忘了自己是狼。这不是概率问题，
 *    进到第五天几乎必然发生。所以身份、队友、查验记录这些信息**每一轮都重新拼一遍**，
 *    不依赖任何历史还能不能留在上下文里。
 * 2. **私有信息不能靠过滤。** 谁的队友是谁、验过谁是什么结果，这些只能出现在当事人手里。
 *    把私有信息写进 system 而公共播报写进 user，边界就落在数据结构上，而不是靠调用方记得过滤。
 *
 * 拼装顺序也是设计过的：**人设 → 身份 → 规则 → 公开局势与历史 → 私有信息 → 本轮任务**。
 * 身份放前面保证一眼看到；私有信息放在长历史之后，是因为历史可能有几十行，放在中间会被冲淡；
 * 本轮任务放最后，紧贴模型要输出的东西。
 */
object WerewolfPrompt {

    /**
     * 拼出一份完整的 system prompt。
     *
     * @param persona 好友人设（`Friend.prompt`）。只放「性格与说话风格」，不要把 `Friend.systemPrompt`
     *   整段塞进来——那里有「你是用户的 AI 好友」之类的措辞，会让模型以为自己还在陪用户聊天。
     */
    fun systemPrompt(
        game: WerewolfGame,
        seat: Int,
        persona: String? = null,
    ): String {
        val player = game.playerAt(seat)
        return buildString {
            if (!persona.isNullOrBlank()) {
                append(persona.trim())
                append("\n\n")
            }
            append("你正在玩一局狼人杀，全程用简体中文说话，一次只说自己的话。\n")
            append("你是 ").append(seat).append(" 号玩家「").append(player.name).append("」。\n")
            // 狼人自己就是「狼人阵营」，再写一遍会变成「狼人（狼人阵营）」，读着别扭
            if (player.role == WerewolfRole.WEREWOLF) {
                append("你的身份是狼人，这个身份只有你自己知道。\n\n")
            } else {
                append("你的身份是").append(player.role.displayName)
                    .append("（").append(player.faction.displayName).append("阵营），")
                    .append("这个身份只有你自己知道。\n\n")
            }
            append(ruleBrief(game.config.winRule)).append("\n\n")
            append(boardBrief(game.config).trimEnd()).append("\n\n")
            append(publicSituation(game).trimEnd()).append("\n\n")
            append(privateBriefing(game, seat)).append("\n\n")
            append(task(game, seat))
        }
    }

    // ---------------------------------------------------------------------
    // 公开局势
    // ---------------------------------------------------------------------

    /** 所有人看到都一样的部分：天数、阶段、存活与出局、已经播报过的事。 */
    fun publicSituation(game: WerewolfGame): String = buildString {
        append("【当前局势】第 ").append(game.day).append(" 天 · ").append(game.phase.displayName).append("\n")
        append("存活：").append(seats(game.aliveSeats))
            .append("（共 ").append(game.aliveSeats.size).append(" 人）\n")
        val dead = game.players.filter { !it.alive }.map { it.seat }
        if (dead.isNotEmpty()) {
            append("已出局：").append(seats(dead)).append("\n")
        }
        append("\n【已经发生的事】\n")
        if (game.history.isEmpty()) {
            append("还没有任何播报，这是第一夜。")
        } else {
            game.history.forEach { append("· ").append(describe(it)).append("\n") }
        }
    }

    /**
     * 把一条公开事件写成一句话。
     *
     * 死亡只写座位号，不写身份——这是线下规则，也是信息隔离的一部分：
     * 一旦把「3 号是猎人」写进播报，狼人就不用猜了。身份只有在 [GameEvent.GameOver] 之后才公开。
     */
    fun describe(event: GameEvent): String = when (event) {
        is GameEvent.Dawn -> if (event.isPeaceful) {
            "第${event.day}天 天亮：平安夜，没有人出局"
        } else {
            "第${event.day}天 天亮：${seats(event.deadSeats)}出局"
        }

        is GameEvent.Exiled ->
            "第${event.day}天 投票：${event.seat} 号被放逐（${event.voteCount} 票）"

        is GameEvent.NoExile ->
            "第${event.day}天 投票：无人被放逐"

        is GameEvent.IdiotRevealed ->
            "第${event.day}天 投票：${event.seat} 号以 ${event.voteCount} 票被投出，" +
                "翻开是白痴，没有出局，此后失去投票权"

        is GameEvent.HunterShot -> if (event.targetSeat == null) {
            "第${event.day}天：猎人 ${event.hunterSeat} 号出局，没有开枪"
        } else {
            "第${event.day}天：猎人 ${event.hunterSeat} 号开枪带走了 ${event.targetSeat} 号"
        }

        is GameEvent.GameOver ->
            "游戏结束：${event.winner.displayName}阵营获胜"
    }

    /**
     * 本局板子。
     *
     * 板子是**公开信息**：线下开局前所有人都知道今天打的是什么配置。不告诉模型，
     * 它就会拿 8 人局的经验去推 12 人局——比如笃定场上只有三神，或者不知道有守卫。
     *
     * 新身份的规则也写在这里，而不是指望模型从「守卫」这个名字猜出他能做什么。
     */
    fun boardBrief(config: WerewolfConfig): String = buildString {
        append("【本局板子】").append(config.playerCount).append(" 人局：")
        append(
            WerewolfRole.entries
                .mapNotNull { role ->
                    config.composition[role]?.takeIf { it > 0 }?.let { "$it ${role.displayName}" }
                }
                .joinToString("、")
        ).append("\n")
        if (config.has(WerewolfRole.GUARD)) {
            append("守卫每夜先于狼人守一个人，被守的人当夜不会被刀死；不能连续两夜守同一个人，可以守自己。")
            append("守卫和解药同时作用在一个人身上时，那个人活下来。\n")
        }
        if (config.has(WerewolfRole.IDIOT)) {
            append("白痴被投票放逐时翻开身份，不会死，但从此不能再投票、也不能再被投；夜里被刀或被毒照常会死。\n")
        }
    }

    // ---------------------------------------------------------------------
    // 私有信息
    // ---------------------------------------------------------------------
    /**
     * 只有本人知道的那些事。
     *
     * 每种身份各自不同，共同点是**绝不进公共播报**。狼队友必须知道彼此，否则没法配合；
     * 预言家必须看到历史查验，否则每晚都在重复验同一个人。
     */
    fun privateBriefing(game: WerewolfGame, seat: Int): String {
        val player = game.playerAt(seat)
        val lines = mutableListOf<String>()

        when (player.role) {
            WerewolfRole.WEREWOLF -> {
                val mates = game.wolves().filter { it.seat != seat }
                lines += if (mates.isEmpty()) {
                    "你是唯一的狼人。"
                } else {
                    "你的狼队友：" + mates.joinToString("、") { mate ->
                        "${mate.seat} 号「${mate.name}」" + if (mate.alive) "" else "（已出局）"
                    }
                }
                lines += "白天不要暴露自己是狼人，也不要直接说出队友的名字。"
                if (game.wolfVoteRound > 1 && game.wolfVotes.isNotEmpty()) {
                    lines += "上一轮你们没能统一意见：" +
                        game.wolfVotes.entries.sortedBy { it.key }
                            .joinToString("、") { "${it.key} 号想刀 ${it.value} 号" } +
                        "。这一轮必须定下来，再平票今晚就空刀。"
                }
            }

            WerewolfRole.SEER -> {
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
                    lines += "还没查验过的存活玩家：" + seats(unknown)
                }
            }

            WerewolfRole.WITCH -> {
                lines += "解药：" + if (game.witch.antidoteAvailable) "还在" else "已经用完"
                lines += "毒药：" + if (game.witch.poisonAvailable) "还在" else "已经用完"
                if (game.phase == Phase.NIGHT_WITCH) {
                    lines += if (game.tonightVictim == null) {
                        "今晚没有人被狼人袭击。"
                    } else {
                        "今晚被狼人袭击的是 ${game.tonightVictim} 号。"
                    }
                }
                lines += "解药用一次就没了，可以救活被狼刀的人，但不能救自己（第一夜除外）。"
                lines += "毒药只能用一次，同一夜不能和解药一起用。"
            }

            WerewolfRole.HUNTER -> {
                lines += "你是猎人：被狼人杀死或被投票放逐时可以开枪带走一个人；如果是被女巫毒死的，不能开枪。"
            }

            WerewolfRole.GUARD -> {
                lines += if (game.lastGuardedSeat == null) {
                    "你昨夜没有守人，今晚任何人都可以守。"
                } else {
                    "你昨夜守的是 ${game.lastGuardedSeat} 号，今晚不能再守他。"
                }
                if (game.phase == Phase.NIGHT_GUARD) {
                    lines += "现在轮到你守护。你不知道今晚狼人会刀谁，只能凭白天的发言去猜。"
                }
                lines += "你守的人当夜不会被刀死，但你自己也可能被刀——守自己同样是合法选择。"
                lines += "你救下人的时候不会被告知，平安夜也可能只是狼人空刀，不要在白天当成证据说出来。"
            }

            WerewolfRole.IDIOT -> {
                if (game.idiotRevealed) {
                    lines += "你已经翻过牌了：所有人都知道你是白痴，你还能说话，但**不能再投票**，也不会再被投。"
                    lines += "你现在唯一有用的地方，是继续用发言帮好人指认狼人。"
                } else {
                    lines += "你是白痴：如果被投票投出，你会翻开身份但不死；代价是从此失去投票权。"
                    lines += "所以被投不是灾难，但也不能因此乱说话——翻牌之后你就只剩一张嘴了。"
                }
            }

            WerewolfRole.VILLAGER -> {
                // 平民确实没有私有信息。明确写出来，比留一段空白好——
                // 空白会让模型自己脑补「我是不是漏了什么」，进而编出不存在的信息。
                lines += "你是平民，没有任何额外的夜间信息。"
                lines += "你只能靠白天的发言、投票和死讯来判断谁是狼人。"
            }
        }

        return buildString {
            append("【只有你知道的事】\n")
            lines.forEach { append("· ").append(it).append("\n") }
            when (player.role) {
                // 平民本来就没秘密，跟它说「这些不能说出来」只会让它以为自己藏了什么
                WerewolfRole.VILLAGER -> Unit
                WerewolfRole.HUNTER ->
                    append("你的猎人身份不要主动说出来，除非你已经开枪——开枪之后就藏不住了。")
                WerewolfRole.IDIOT ->
                    append("不要在白天主动说自己是白痴：翻牌虽然不死，但你会立刻失去投票权，狼人也就不必再怕你。")
                else -> append("以上信息不能在白天直接说出来。")
            }
        }
    }

    // ---------------------------------------------------------------------
    // 本轮任务
    // ---------------------------------------------------------------------

    /**
     * 当前阶段要做什么。
     *
     * 写具体动作而不是「请行动」：`请从 1、2、5 号中选一个` 比 `请选择目标` 的跑偏率低一个量级，
     * 尤其是小模型。
     */
    fun task(game: WerewolfGame, seat: Int): String {
        val player = game.playerAt(seat)
        return buildString {
            append("【本轮任务】")
            when (game.phase) {
                Phase.NIGHT_GUARD -> {
                    // 守卫可以守自己，所以候选里刻意包含自己的座位号，
                    // 和 WerewolfDecisions.guardProtect 里的候选保持同一份
                    append("第 ${game.day} 夜，你要守护一个人。")
                    append("可选目标：").append(seats(game.guardTargets)).append("（含你自己）。")
                    if (game.lastGuardedSeat != null) {
                        append("你昨夜守的是 ${game.lastGuardedSeat} 号，今晚不能再守他。")
                    }
                    append("空守等于这一夜白过，除非你确信狼人会空刀。")
                    append("先用一句话说明你为什么守他，再给出座位号。")
                }

                Phase.NIGHT_WOLF -> {
                    val targets = game.aliveSeats.filter { it != seat && !game.isWerewolf(it) }
                    append("第 ${game.day} 夜，狼人商议今晚刀谁。")
                    append("可选目标：").append(seats(targets)).append("。")
                    append("先用一句话说出你的想法，再给出目标座位号。")
                }

                Phase.NIGHT_SEER -> {
                    val targets = game.aliveSeats.filter { it != seat }
                    append("第 ${game.day} 夜，你要查验一个人。")
                    append("可选目标：").append(seats(targets)).append("。")
                    append("优先验还没查过的人——你已经掌握的信息比其他人多，别浪费这一验。")
                }

                Phase.NIGHT_WITCH -> {
                    val victim = game.tonightVictim
                    if (victim == null) {
                        append("今晚没有人被狼人袭击，你不需要用解药。")
                    } else {
                        append("今晚 ${victim} 号被狼人袭击。")
                    }
                    append("决定是否用解药，或者用毒药毒死一个人（同一夜只能用一种药）。")
                    append("也可以什么都不做，把药留到更关键的时候。")
                }

                Phase.DAY_DISCUSSION ->
                    append(
                        "第 ${game.day} 天白天讨论，轮到你发言。" +
                            "用 2-3 句中文说出你的判断：你怀疑谁、为什么，或者解释你上一轮为什么那样投票。" +
                            "只说你自己的话，不要替别人发言，不要写「某某说：」这样的格式。"
                    )

                Phase.DAY_VOTE -> {
                    val candidates = game.voteCandidates ?: game.aliveSeats
                    append("第 ${game.day} 天投票。")
                    if (game.voteRound > 1) {
                        append("上一轮平票，这一轮只能投 ").append(seats(candidates)).append("。")
                    } else {
                        append("可选目标：").append(seats(candidates)).append("。")
                    }
                    append("投票要指座位号，不要指名字；也可以弃票。")
                }

                Phase.HUNTER_SHOOT -> {
                    val targets = game.aliveSeats
                    append("你出局了，身份是猎人，现在可以开枪带走一个人，也可以放弃。")
                    if (targets.isNotEmpty()) {
                        append("可选目标：").append(seats(targets)).append("。")
                    }
                }

                Phase.FINISHED -> append("游戏已经结束。")
            }
        }
    }

    // ---------------------------------------------------------------------

    private fun ruleBrief(winRule: WinRule): String = when (winRule) {
        WinRule.SLAY_SIDE ->
            "【胜负规则】屠边局：狼人杀光所有神职，或杀光所有平民，狼人获胜；好人放逐全部狼人则好人获胜。"

        WinRule.SLAY_ALL ->
            "【胜负规则】屠城局：存活狼人数不少于存活好人数时狼人获胜；好人放逐全部狼人则好人获胜。"
    } + "\n每轮白天进行一轮投票，平票会加投一次；狼人夜里多数决，平票加商一次。"

    /** 座位号列表，写成「1 号、2 号」，不带结尾标点。 */
    private fun seats(list: List<Int>): String =
        list.sorted().joinToString("、") { "$it 号" }
}
