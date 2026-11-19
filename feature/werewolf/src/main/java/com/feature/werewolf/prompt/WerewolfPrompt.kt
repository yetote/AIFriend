package com.feature.werewolf.prompt

import com.feature.werewolf.engine.isNight
import com.feature.werewolf.game.Faction
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WinRule
import com.feature.werewolf.game.formatVotes

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
            append(ruleBrief(game.config)).append("\n\n")
            append(boardBrief(game.config).trimEnd()).append("\n\n")
            append(strategyBrief(game.config).trimEnd()).append("\n\n")
            append(publicSituation(game).trimEnd()).append("\n\n")
            append(privateBriefing(game, seat)).append("\n\n")
            append(task(game, seat))
        }
    }

    // ---------------------------------------------------------------------
    // 公开局势
    // ---------------------------------------------------------------------

    /**
     * 已经公开过的死亡座位。
     *
     * 只认**播报过**的死讯：还没公布的不算（第一天死讯压在警长竞选之后）。
     * 白痴翻牌不算死，所以 [GameEvent.IdiotRevealed] 不在列表里。
     */
    private fun announcedDeadSeats(game: WerewolfGame): List<Int> = game.history
        .flatMap { event ->
            when (event) {
                is GameEvent.Dawn -> event.deadSeats
                is GameEvent.Exiled -> listOf(event.seat)
                is GameEvent.HunterShot -> listOfNotNull(event.targetSeat)
                is GameEvent.WolfExploded -> listOf(event.seat)
                else -> emptyList()
            }
        }
        .distinct()
        .sorted()

    /** 所有人看到都一样的部分：天数、阶段、存活与出局、已经播报过的事。 */
    fun publicSituation(game: WerewolfGame): String = buildString {
        append("【当前局势】第 ").append(game.day).append(" 天 · ").append(game.phase.displayName).append("\n")
        // 存活与出局都按**已经公开过的**事实算，不能直接读引擎状态：
        // 第一天死讯要等警长竞选结束后才公布，而死掉的人已经不在 aliveSeats 里了——
        // 「存活名单少一个人」本身就是死讯。直接读状态等于提前泄底。
        val announcedDead = announcedDeadSeats(game)
        val alive = game.players.map { it.seat }.filter { it !in announcedDead }
        append("存活：").append(seats(alive))
            .append("（共 ").append(alive.size).append(" 人）\n")
        if (announcedDead.isNotEmpty()) {
            append("已出局：").append(seats(announcedDead)).append("\n")
        }
        if (game.config.hasSheriff) {
            val sheriff = game.aliveSheriffSeat
            append("警长：").append(
                if (sheriff != null) {
                    "$sheriff 号（票权 1.5 票，白天最后一个发言）"
                } else {
                    "暂无（还没选出，或警徽已经流失）"
                }
            ).append("\n")
            // 竞选期间「谁上警」是公开信息，也是投票的唯一依据，必须写进来
            if (game.sheriffRunners.isNotEmpty() && game.phase.isSheriffPhase) {
                append("上警：").append(seats(game.sheriffRunners)).append("\n")
            }
        }
        // 死讯还没公布（第一天：先竞选、后公布）时，任何人都不该谈昨夜。
        //
        // 狼人在这段时间是**知道刀口**的（他们自己动的手），一张嘴说出
        // 「昨夜明明是平安夜」，等于当着全场承认「我知道昨晚有人被刀」——
        // 真实案例：狼人警长的竞选发言里就是这么把自己暴露的。
        if (game.pendingDawn != null) {
            append("\n【昨夜还没公布】昨夜的结果没人知道，**不要谈昨夜**：")
            append("连「昨晚是平安夜」这种话都别说出口——你凭什么知道昨夜没死人？")
            append("只说今天白天已经摆在台面上的东西。\n")
        }
        append("\n【已经发生的事】\n")
        if (game.history.isEmpty()) {
            append("还没有任何播报，这是第一夜。")
            return@buildString
        }
        // 更早的天数压成「一天一行」，最近 EXPANDED_DAYS 天保持逐条
        val expandedFrom = (game.day - EXPANDED_DAYS + 1).coerceAtLeast(1)
        val older = game.history.filter { it.day < expandedFrom }
        if (older.isNotEmpty()) {
            older.groupBy { it.day }.forEach { (day, events) ->
                append("第").append(day).append("天：").append(summarize(events)).append("\n")
            }
            append("—— 最近 ").append(EXPANDED_DAYS).append(" 天，逐条 ——\n")
        }
        game.history.filter { it.day >= expandedFrom }
            .forEach { append("· ").append(describe(it)).append("\n") }
    }

    /**
     * 历史里**逐条展开**的天数，更早的压成一天一行。
     *
     * 为什么不全部逐条：打到第六天，history 就有三四十条，铺开之后「第 1 天谁出局」
     * 被推到三十行之外——这不是 token 问题，是注意力问题，跟我们特意把私有信息
     * 放到历史之后是同一个理由。
     *
     * 为什么不全部压成一行：最近两天还要用来对质（昨天谁说了什么、谁投了谁），
     * 压缩会丢掉「同一天里谁先说、谁后说」的时序，而那个时序本身是推理材料。
     *
     * 折叠**只发生在拼提示词这一步**：[WerewolfGame.history] 一条不动，
     * 存档和复盘读的都是 history，所以压缩永远不会让对局记忆真的丢掉。
     */
    private const val EXPANDED_DAYS = 2

    /** 一天的事件压成一句，子句之间用「；」隔开。 */
    private fun summarize(events: List<GameEvent>): String =
        events.mapNotNull { brief(it) }.joinToString("；").ifEmpty { "没有需要记住的事" }

    /**
     * 摘要用的短句。返回 null 表示这件事**在折叠后不必再提**。
     *
     * 和 [describe] 分开而不是复用它：那份文案同时被编排层拿去写上帝消息，
     * 改了它会连带改掉界面上的记录流。这里只服务于提示词。
     */
    private fun brief(event: GameEvent): String? = when (event) {
        is GameEvent.Dawn ->
            if (event.isPeaceful) "夜里平安" else "夜里 ${seats(event.deadSeats)}出局"

        is GameEvent.Exiled -> "${event.seat} 号被放逐（${formatVotes(event.voteCount)} 票）"
        is GameEvent.NoExile -> "无人被放逐"
        is GameEvent.IdiotRevealed -> "${event.seat} 号翻牌是白痴，没有出局"

        is GameEvent.HunterShot -> if (event.targetSeat == null) {
            "${event.shooterLabel} ${event.hunterSeat} 号没开枪"
        } else {
            "${event.shooterLabel} ${event.hunterSeat} 号开枪带走 ${event.targetSeat} 号"
        }

        // 提示词里的摘要同样不能带座位号：它进的是所有玩家的上下文，
        // 点了号等于把「谁是熊」发给了每一只狼。熊自己知道自己是熊，不需要这一行。
        is GameEvent.BearRoar -> "熊在咆哮（左右邻座有狼）"

        is GameEvent.WolfExploded -> "${event.seat} 号狼人自爆，当天直接入夜"

        is GameEvent.SheriffSignup -> "上警的是 ${seats(event.runners)}"

        is GameEvent.SheriffElected -> if (event.votes <= 0) {
            "${event.seat} 号自动当选警长"
        } else {
            "${event.seat} 号当选警长（${event.votes} 票）"
        }

        is GameEvent.SheriffVacant -> "没有警长（${event.reason}）"

        // PK 是过程：平票这件事对结局没有额外后果，结果已经由 SheriffElected 或
        // SheriffVacant 表达过了，折叠之后再留一行只是重复占位
        is GameEvent.SheriffPk -> null

        is GameEvent.SheriffBadgePassed ->
            if (event.toSeat == null) "警徽作废" else "警徽交给 ${event.toSeat} 号"

        is GameEvent.GameOver -> "${event.winner.displayName}阵营获胜"
    }

    private val Phase.isSheriffPhase: Boolean
        get() = this == Phase.DAY_SHERIFF_SIGNUP || this == Phase.DAY_SHERIFF_SPEECH ||
            this == Phase.DAY_SHERIFF_VOTE

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
            "第${event.day}天 投票：${event.seat} 号被放逐（${formatVotes(event.voteCount)} 票）"

        is GameEvent.NoExile ->
            "第${event.day}天 投票：无人被放逐"

        is GameEvent.IdiotRevealed ->
            "第${event.day}天 投票：${event.seat} 号以 ${formatVotes(event.voteCount)} 票被投出，" +
                "翻开是白痴，没有出局，此后失去投票权"

        is GameEvent.HunterShot -> if (event.targetSeat == null) {
            "第${event.day}天：${event.shooterLabel} ${event.hunterSeat} 号出局，没有开枪"
        } else {
            "第${event.day}天：${event.shooterLabel} ${event.hunterSeat} 号开枪带走了 ${event.targetSeat} 号"
        }

        // 上帝播报只报「发生了什么」，不解释规则：规则在板子说明和模型提示词里
        // 已经讲过一遍，写在播报里既占屏幕又是废话（玩家原话：「熊咆哮了」就够了）。
        // 也**不能点名熊的座位**——熊是暗身份，念出「11 号在咆哮」等于当众宣布谁是熊，
        // 狼人下一夜就先刀它（玩家原话：「那不直接把熊的号码点出来了么？」）。
        is GameEvent.BearRoar ->
            "第${event.day}天 天亮：熊在咆哮"

        is GameEvent.WolfExploded ->
            "第${event.day}天：${event.seat} 号狼人自爆，直接进入黑夜"

        // 报名只报名单：理由属于竞选发言，两处都写就等于把同一件事说两遍
        is GameEvent.SheriffSignup ->
            "第${event.day}天：上警的是 ${seats(event.runners)}"

        is GameEvent.SheriffElected -> if (event.votes <= 0) {
            "第${event.day}天：只有 ${event.seat} 号上警，自动当选警长"
        } else {
            "第${event.day}天：${event.seat} 号当选警长（${event.votes} 票）"
        }

        is GameEvent.SheriffVacant ->
            "第${event.day}天：本局没有警长（${event.reason}）"

        is GameEvent.SheriffPk ->
            "第${event.day}天：警长投票平票（${seats(event.seats)}），进入 PK 发言"

        is GameEvent.SheriffBadgePassed -> if (event.toSeat == null) {
            "第${event.day}天：警长 ${event.fromSeat} 号出局，警徽没有交出，已作废"
        } else {
            "第${event.day}天：警长 ${event.fromSeat} 号把警徽交给 ${event.toSeat} 号"
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
            append("守卫和解药同时作用在一个人身上时，那个人**还是会死**（守救同归）。\n")
        }
        if (config.has(WerewolfRole.IDIOT)) {
            append("白痴被投票放逐时翻开身份，不会死，但从此不能再投票、也不能再被投；夜里被刀或被毒照常会死。\n")
        }
        if (config.has(WerewolfRole.WEREWOLF_KING)) {
            append("狼王是狼人：夜里照常参与商议和下刀；白天被投票放逐时可以开枪带走一个人，" +
                "那一枪即使在狼队已经全灭后也照样有效，可能直接改写胜负。" +
                "狼王被毒死或被猎人带走时不能开枪。\n")
        }
        if (config.has(WerewolfRole.BEAR)) {
            append("熊是好人神职，夜里不行动。每天天亮时只要熊还活着、左右邻座有存活狼人，" +
                "上帝就会公开宣布熊在咆哮；没咆哮不代表邻座一定都是好人，熊死了也不会咆哮。\n")
        }
        if (config.hasSheriff) {
            append(
                "本局有警长：第 1 天**先竞选、后公布昨夜死讯**——想当的人上警、各说一段，" +
                    "再由**没上警的人**投票选出；竞选和投票结束时才公布昨夜谁出局。" +
                    "上警只是**举手报名**，报名时不用说话，每个人的第一次发言就是竞选发言——" +
                    "**不要拿「他上警前没说话」当质疑点**。" +
                    "警长的一票算 1.5 票，并且决定当天从谁开始发言（他自己最后说）。" +
                    "警长出局时要在遗言里移交警徽，没人接手或没来得及交代就作废。\n"
            )
        }
    }

    // ---------------------------------------------------------------------
    // 基本打法
    // ---------------------------------------------------------------------

    /**
     * 判断方法，而不是规则。
     *
     * [ruleBrief] 说的是「能做什么、怎么算赢」，这一层说的是「怎么判断谁像狼」。
     * 缺了这一层，模型的发言完全由人设驱动，实测出现过三种灾难：
     * 真预言家在警上、PK、白天把同一段话说了三遍；守卫第一天就当众解释
     * 「我昨夜守了自己，所以是平安夜」；好人看到预言家对跳，只看谁语气更硬。
     *
     * 写死在这里而不是塞进每个角色的私有简报：**判断别人的方法是公共知识**，
     * 平民、狼人、神职都用得上，而且必须一致——否则同一局里两个人用两套标准。
     *
     * 每条都尽量写成「可执行的动作」，不写「要理性分析」这种正确但没用的话。
     */
    private fun strategyBrief(config: WerewolfConfig): String = buildString {
        append("【基本打法】以下和你的性格、说话风格无关，冲突时以这里为准：\n")
        append("· 预言家每天开口第一件事，是报出前一晚验了谁、结果是好人还是狼人，然后才谈判断。")
        append("跳过不报、只喊结论不报验人，在好人眼里就是悍跳。\n")
        append("· 谁跳了预言家却报不出连续的验人链，或者验的人专挑已经被全场怀疑的，就该被盯上——")
        append("这是好人盘对跳的第一依据，比语气和文采可靠得多。\n")
        // 第 1 天只有一个验人记录，「链子断」这个说法会把真预言家冤死。
        // 实测一局 12 人：真预言家第 1 夜验的正好是当夜被刀的人，一条链被说成
        // 「死无对证」，好人全票投他，狼人顺势拿到警徽——真预言家第一天就走了。
        append("· **第 1 天只有一条验人记录是正常的**，别因为链短就判他悍跳；")
        append("验到死人只是运气不好（他自己也不知道对方会死），不是悍跳的证据。")
        append("只有**验了活人还乱报、或者报不出自己验过的人**，那才是悍跳。\n")
        append("· 狼给谁发金水都不亏，「被发金水」证明不了干净；反过来，给金水的人也没必要急着投。\n")
        append("· 神职不要主动报身份。")
        if (config.has(WerewolfRole.GUARD)) {
            append("守卫尤其不能说自己守过谁——那等于把守人路线和剩下的选择交给狼队，平安夜也不能当证据。")
        }
        append("猎人翻牌前报身份，等于给狼指路。\n")
        append("· 每一轮都要带来新东西：新的怀疑对象、对别人刚说完那句话的回应、或对自己上一票的解释。")
        append("结论没变就补理由或回应，**同一段判断说第二遍在别人眼里就是背词**。\n")
        append("· 被指认时先正面回答对方的具体质疑，再反打。只喊「我是好人」「我逻辑自洽」没有用。\n")
        append("· 投票前先看票型：谁发起的、谁跟得最快、谁在事后把自己摘干净。")
        append("票型整齐不等于投对了人，一致跟票的人里一定有狼。\n")
        append("· 出局时说遗言要给具体座位号和理由，别只交代「别投错」。\n")
        // 真实案例：场上只剩 3 好 1 狼，三个好人里两个互相咬，狼人投了其中一个，
        // 结果好人把好人投出去了，夜里再被刀一个就绑票输掉。
        append("· **场上人已经不多了时，票必须砸在狼身上**：只剩两三个好人还活着的时候，" +
            "投掉一个好人，夜里狼再刀一个就等于绑票——投好人等于帮狼杀人。\n")
        append("· **跟着多数投票本身不是狼的证据**：警长归票之后，很多好人都会跟票。" +
            "狼的特征是**带节奏、有自己的独立目标**，不是站得准。\n")
        append("· 第 1 天上警的人里如果只有一两个狼，等于白送警徽——" +
            "**神职该上警就上警**：猎人尤其要上（别人得知道他有枪，才会投他，他才有开枪的机会）；" +
            "预言家不上警，这一局就没有人能报验人。\n")
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
            WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF_KING -> {
                if (player.role == WerewolfRole.WEREWOLF_KING) {
                    lines += "你是狼王：夜里和普通狼一样行动，白天被投票放逐时可以开枪带走一个人。" +
                        "那一枪即使在队友已经全灭之后也有效，别轻易放弃。"
                }
                val mates = game.wolves().filter { it.seat != seat }
                lines += if (mates.isEmpty()) {
                    "你是唯一的狼人。"
                } else {
                    "你的狼队友：" + mates.joinToString("、") { mate ->
                        "${mate.seat} 号「${mate.name}」" + if (mate.alive) "" else "（已出局）"
                    }
                }
                // 这条以前只写了「不要说出队友的名字」，实测挡不住：模型会改成说
                // 「队友的节奏」「四狼一致刀 4 号」——不点名，但等于把底牌摊开。
                // 所以约束必须落到「狼队内部的事一律不能出现在白天」这个层面。
                lines += "狼队内部的一切只能在夜里说：队友是谁、商量了什么、今晚刀谁、狼队还剩几个。" +
                    "白天你的发言里不许出现「队友」「我们狼」「昨夜我们」这类字眼，" +
                    "也不许复述夜里说过的话——这些话说出口就是自曝，" +
                    "而且会让队友被一个个揪出来。"
                lines += "白天你只有一份档案：一个普通好人。说理只能靠公开发言和票型，" +
                    "哪怕理由是你自己编的，也要是好人能推出来的那种。"
                if (game.wolfPlan != null && !game.phase.isNight) {
                    // 白天看不到夜聊原文（不然狼人会在白天当众说漏「昨夜我们」），
                    // 战术安排只在这里给到狼本人。少这一条，昨晚商量出来的分工就白定了。
                    lines += "昨晚头狼定的战术：" + game.wolfPlan
                    lines += "白天按这个演：安排谁跳预言家、谁冲锋，就照着来；" +
                        "但**一个字都不许提**「安排」「昨晚商量」「我们狼」这种说法。"
                }
                if (game.phase == Phase.NIGHT_WOLF_CHAT) {
                    lines += "现在是夜里，只有你们狼人醒着：可以直说要刀谁、为什么，也可以反驳队友。"
                    // 头狼的安排是**建议**，队友可以反对。
                    // 真实案例：狼人对着队友说「这是死命令，不是商量」——那不像狼队，
                    // 像个组织，而且把「谁说了算」摆到了台面上，队友也会开始较劲。
                    lines += "头狼的安排是**建议**，队友可以反对——别用「这是死命令」「都听我的」" +
                        "这种说法，那不像狼队，像个组织。"
                    // 头狼。这条只在夜里出现，白天一个字都不能提——「我们昨晚商量过」
                    // 这类话一说，狼队就等于自己交代了。
                    val head = game.nightHead
                    if (head != null) {
                        lines += if (head == seat) {
                            "**今晚你是头狼（狼队指挥）**：你先发言、其他人后发言。" +
                                "你要给出的是一套能执行的方案——今晚刀谁、为什么，" +
                                "白天谁跳预言家、谁冲锋、谁划水。" +
                                "队友会照着你的安排执行或提出异议，所以方案要具体到人和理由。"
                        } else {
                            "今晚的头狼是 $head 号，他先发言安排战术。" +
                                "你听完要明确表态：照着执行（并补上你自己的理由），或者反对并给出你的刀口。"
                        }
                    }
                }
                if (game.phase == Phase.NIGHT_WOLF) {
                    lines += "商议已经结束，现在各自动手：只要有一只狼指了别人，今晚就可能空刀。"
                }
                // 「上一夜定了谁」狼队任何时候都要知道（那是他们自己定的），
                // 但**结果**不归他们管——刀口最后死没死，是天亮上帝播报的事。
                // 夜里他们还不知道结果（要等播报），白天播报早就过了，所以他们也就知道了。
                game.nights.lastOrNull { it.day == game.day - 1 }?.wolfTarget?.let { target ->
                    lines += "上一夜你们定了刀 $target 号。"
                }
                if (game.phase.isNight) {
                    // 只有**正在加商**的时候才说「没谈拢」。
                    // 白天还说这句是错的：wolfVoteRound 到定刀成功后还停在 2，
                    // 而 wolfVotes 已经是第二轮那份一致的票——文案说分歧、票型却是统一的，
                    // 狼人拿到的情报自相矛盾。
                    if (game.wolfVoteRound > 1 && game.wolfVotes.isNotEmpty()) {
                        lines += "上一轮你们没能统一意见：" +
                            game.wolfVotes.entries.sortedBy { it.key }
                                .joinToString("、") { "${it.key} 号想刀 ${it.value} 号" } +
                            "。这一轮必须定下来，再平票今晚就空刀。"
                    }
                } else {
                    // 白天：这件事一个字都不许提，也别刻意回避他——
                    // 刚刀完就绕着一个人走，那种回避本身就像狼。
                    lines += "白天**一个字都不许提你们刀过谁**，也别刻意回避他。"
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
                    val victim = game.tonightVictim
                    val canSelfHeal = game.witch.antidoteAvailable &&
                        game.day == 1 && game.config.witchCanSelfHealOnFirstNight
                    when {
                        victim == null ->
                            lines += "今晚没有人被狼人袭击，解药这次没有用处，你可以只用毒药或什么都不做。"

                        victim == seat && canSelfHeal ->
                            lines += "**今晚被狼人袭击的就是你自己（$seat 号）。** " +
                                "第一夜你还能自救：用解药就活下来，不用就当场出局。"

                        victim == seat -> {
                            // 必死的局面。以前这里只写「被袭击的是 2 号」，模型按普通夜晚的
                            // 思路答「这晚先不用药，毒药留到更关键的时候」——可它天一亮就死了，
                            // 「更关键的时候」永远不会到来，毒药跟着进棺材。
                            // 所以要明写「你今晚会死」和「这是最后机会」，不能只给客观信息。
                            lines += "**今晚被狼人袭击的就是你自己（$seat 号），而你已经救不了自己" +
                                "——天一亮你就出局了。**"
                            if (game.witch.poisonAvailable) {
                                lines += "这是你最后一次行动：毒药现在不用，就跟着你一起作废，" +
                                    "以后永远没有第二次机会。想清楚最该带走谁，" +
                                    "别把毒药省给一个不会到来的明天。"
                            }
                        }

                        else -> {
                            lines += "今晚被狼人袭击的是 $victim 号。"
                            lines += "你可以救他，也可以不救。解药只有一次，" +
                                "救他等于把这一次押在「他是好人」上。"
                        }
                    }
                }
                lines += "解药用一次就没了，可以救活被狼刀的人，但不能救自己（第一夜除外）。"
                lines += "毒药只能用一次，同一夜不能和解药一起用。"
            }

            WerewolfRole.HUNTER -> {
                lines += "你是猎人：被狼人杀死或被投票放逐时可以开枪带走一个人；如果是被女巫毒死的，不能开枪。"
            }

            WerewolfRole.GUARD -> {
                // 白天不能读 lastGuardedSeat：`startNextNight()` 是**白天结束**时才把它换成
                // 上一夜的守人，所以白天这一刻它指向的是「前夜的前夜」。
                // 实测后果很严重——守卫在白天照着这个字段发言，报的守人对象整整差一夜
                // （第 2 天说守 5 号实守 4 号、第 4 天说守 6 号实守 10 号），
                // 好人据此认定他「前后矛盾、在编身份」，把真守卫票了出去。
                // 白天该看的是 tonightGuardedSeat，它只在跨夜时被搬走。
                val lastNight = if (game.phase.isNight) game.lastGuardedSeat else game.tonightGuardedSeat
                lines += if (lastNight == null) {
                    "你昨夜没有守人，今晚任何人都可以守。"
                } else {
                    "你昨夜守的是 $lastNight 号，今晚不能再守他。"
                }
                if (game.phase == Phase.NIGHT_GUARD) {
                    lines += "现在轮到你守护。你不知道今晚狼人会刀谁，只能凭白天的发言去猜。"
                }
                lines += "你守的人当夜不会被刀死，但你自己也可能被刀——守自己同样是合法选择。"
                lines += "你救下人的时候不会被告知，平安夜也可能只是狼人空刀，不要在白天当成证据说出来。" +
                    "也不要在白天报出你守过谁：那等于把守人路线和剩下的选择一起交给狼队。"
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

            WerewolfRole.BEAR -> {
                lines += "你是熊：夜里不行动。天亮时只要你活着、左右邻座有存活狼人，" +
                    "上帝就会公开宣布你咆哮。这是所有人都能听到的线索，不是你的私聊。"
                lines += "你不用急着自报身份——让别人先从咆哮里推，你一旦报了，狼人就会先刀你。"
            }

            WerewolfRole.VILLAGER -> {
                // 平民确实没有私有信息。明确写出来，比留一段空白好——
                // 空白会让模型自己脑补「我是不是漏了什么」，进而编出不存在的信息。
                lines += "你是平民，没有任何额外的夜间信息。"
                lines += "你只能靠白天的发言、投票和死讯来判断谁是狼人。"
            }
        }

        // 身份专属打法。只给本人看，所以可以写得很具体——
        // 公共打法（strategyBrief）解决「怎么判断」，这里解决「我这个身份该说什么」。
        lines += playbook(player.role)

        // 警长、竞选、遗言这三件事和身份无关，任何座位都可能碰上，所以放在 when 之外。
        // 尤其是「你是警长」——警长本身是公开身份，但**只有本人**需要知道自己的票权是 1.5。
        if (game.sheriffSeat == seat && game.phase != Phase.FINISHED) {
            lines += "你是警长：你的一票算 1.5 票，白天从你的下一位开始发言、你最后说（归票位）。"
            lines += "你出局时要在遗言里决定把警徽交给谁——交给错的人比撕掉更糟。"
        }
        if (seat in game.sheriffRunners && game.phase.isSheriffPhase) {
            lines += "你在竞选警长。上过警的人不能投警长票，所以既然报了名就要争到底。"
        }
        if (game.phase == Phase.DAY_LAST_WORDS && seat in game.pendingLastWords) {
            lines += "你已经出局了，现在轮到你说遗言。遗言改不了结局，但会直接影响活着的人怎么投。"
        }

        return buildString {
            append("【只有你知道的事】\n")
            lines.forEach { append("· ").append(it).append("\n") }
            when (player.role) {
                // 平民本来就没秘密，跟它说「这些不能说出来」只会让它以为自己藏了什么
                WerewolfRole.VILLAGER -> Unit
                WerewolfRole.HUNTER ->
                    append("你的猎人身份不要主动说出来，除非你已经开枪——开枪之后就藏不住了。")
                WerewolfRole.WEREWOLF_KING ->
                    append("狼王身份也要藏住；一旦被票出去，记得这最后一枪可能直接决定胜负。")
                WerewolfRole.BEAR ->
                    append("不要主动说自己是熊——咆哮本身已经给了好人线索，报身份只会让狼人先刀你。")
                WerewolfRole.IDIOT ->
                    append("不要在白天主动说自己是白痴：翻牌虽然不死，但你会立刻失去投票权，狼人也就不必再怕你。")
                else -> append("以上信息不能在白天直接说出来。")
            }
        }
    }

    // ---------------------------------------------------------------------
    // 身份专属打法
    // ---------------------------------------------------------------------
    /**
     * 每个身份在白天「该干什么」的清单。
     *
     * 和 [strategyBrief] 的分工：strategyBrief 是公共的**判断方法**（怎么认狼、
     * 怎么看票型），这里是**身份自己的作业**（预言家今天要报什么、狼人白天怎么演、
     * 女巫怎么藏）。分成两层是因为它们回答的不是同一个问题——
     * 实测预言家拿着公共打法照样会跳过验人链，因为「怎么认狼」根本没告诉他
     * 「你今天必须报出昨晚验了谁」。
     *
     * 全部写成可执行的动作，不写「要理性发言」这种正确但没用的话。
     * 只进本人提示词，不进公共上下文。
     */
    private fun playbook(role: WerewolfRole): List<String> = when (role) {
        WerewolfRole.SEER -> listOf(
            "你的白天发言只有三件任务：报验人、给警徽流、解释验人逻辑。",
            "开口第一句必须是「昨晚我验了 X 号，他是好人/狼人」——跳过这一句，" +
                "别人就没有任何依据判断你是不是真预言家，今天的票会全部落到别人身上。",
            "报完验人立刻给**警徽流**：先验了谁、后验了谁、为什么按这个顺序。" +
                "警徽流是你唯一的身份凭证，顺序一乱，别人就当你乱验或者在编。",
            "**警徽流只报你真的验过的人**，还没验的不要写，更不要为了凑出一条链子编一个。" +
                "第 1 天你只验了一个人，那就在警徽流里只写这一个——" +
                "「只验了 X，Y 还没验」比编一条完整的链子安全得多，" +
                "编出来的链子只要对不上，全场就认定你是悍跳。",
            "每个验人都要讲清**验人逻辑**：为什么今天验他、你从他身上看到了什么疑点。" +
                "只报结果不解释，等于自己把预言家的可信度打折。",
            "被质疑时不要急着喊「我是好人」：先报完整的验人链，再指出对方逻辑里的具体漏洞。",
            "**被指认时，咬的是那个跳预言家的人**，不是跟着他说话的人。" +
                "你临死前咬错人（去咬好人、放过悍跳狼），场上最后一点疑点也跟着你一起走了。",
            "验人链要连着：今天跳了明天也要跳，除非你被刀或者被投出局。" +
                "昨天报的验人今天必须能对上，中途改口等于自己承认昨天在编。",
        )

        WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF_KING -> listOf(
            "你白天只有一个身份：普通好人。",
            "发言量要像真人：不要第一个跳预言家（夜里另有安排除外），也不要全程一句话不说——" +
                "沉默会被当成查不出身份的人，但也更容易被逻辑盘。",
            "不要只喊「我是好人」。要给出**能自证的具体判断**：你注意到了谁的哪句话不对劲。",
            "攻击别人时挑真问题：指出对方前后矛盾、逻辑跳跃，别凭空扣帽子——" +
                "冤枉好人等于帮狼清场。",
            "**不要编造现场细节**（谁的手在抖、谁眼神躲闪、谁在数指节）——" +
                "这种描写只有演的人才会写，好人一眼看穿。疑点要落在**逻辑矛盾**上：他说过的话对不上、前后两种态度。",
            "狼队内部的分工只有你知道执行，**白天一个字都不能提**：不许出现「队友」" +
                "「我们狼」「昨夜我们」这类字眼，也不许复述夜里说过的话。",
            "队友被点时不要急着帮他说话，那会暴露你们的关系；先让局面自己发展。",

            // 盘身份是狼人白天的**主线任务**。没有这一段，狼人整局都在说
            // 「X 号很可疑」，好人听到的是一堆没有指向的废话，也分不出谁在带节奏。
            "你白天真正要做的事是**盘身份**：把场上的人归到「预言家 / 猎人 / 女巫 / 守卫 / 平民」里，" +
                "再按这个结论投票。今天至少要给出**一个明确的身份判断**——" +
                "「我觉得 4 号是预言家，因为他的金水给得反着来」这种；" +
                "只说「他很可疑」不叫盘身份。好人就是靠这些把你和真预言家区分开的。",
            "盘预言家看三件事：报不报验人、验人链连不连、金水给得反不反。" +
                "今天必须钉死一个（除非他已经出局），钉死就一路打——" +
                "今天咬一个明天咬另一个，在好人眼里就是假预言家。",
            "盘猎人靠排除法：猎人翻牌前一般不跳身份，所以「谁是猎人」不是听出来的，" +
                "是排出来的——谁到死都不跳、却一直跟得很紧、像在等最后一枪，就要提防。" +
                "投错他等于送他一条命，他那一枪可能直接带走你们最后一张牌。",
            "盘女巫：她藏得深。真有人跳出来说「我昨晚救了 X 号」，那他救的肯定是他自己刀过的好人，" +
                "这条要记住——下一晚刀他，他就暴露了。",
            "盘守卫（最难的一条）：守卫不会主动暴露，特征是**发言少、总和被刀的人走得近、" +
                "从不主动站边预言家**。推理从死讯开始：昨晚我们刀了 X，X 却没死——" +
                "说明有人保住了他，可能是守卫守了，也可能是女巫救了。两种都记下来：" +
                "下一晚刀 X 本人就是钓他，守得住说明 X 特别重要，守不住就说明他露出来了。",
            "**有人自称「我昨晚守的」，那是诈守卫**——真守卫不会说这个（说了等于把守人路线交给你）。" +
                "所以听到这种话别当证据，除非你还能对上一个具体的人和一条说得通的链子。" +
                "还有：**守卫不能连守的是同一个人**，不是「守过谁今晚就不能守谁」——" +
                "他昨晚守了 10 号，今晚照样能守你想刀的那个 5 号，别把刀口当成裸位。",
            "盘出来的结论要**落到票上**：你认定谁是预言家，今天就投他，别嘴上说怀疑却跟着别人投票。",
            "昨晚**平安夜**（刀口没死）说明有人保住了他——先分清是守卫守了还是女巫救了，" +
                "再决定要不要重复刀同一个人。同一个人连着刀两夜，好人一眼就算出来了。",
        )

        WerewolfRole.WITCH -> listOf(
            "你是女巫，白天要把自己藏起来。",
            "不要说自己救过谁、毒过谁，哪怕那是真的——被认出来的女巫下一夜就会被刀。",
            "不要用「我昨晚听说」「我猜他昨晚」这种暗示自己有夜间信息的话。",
            "毒药只有一次，宁可留到确定是狼的那天；救人也只救你确定该救的人，" +
                "别因为「他好像挺重要」就浪费解药。",
        )

        WerewolfRole.GUARD -> listOf(
            "你是守卫，你的价值在夜里，不在嘴上。",
            "白天不要报守过谁，也不要暗示「我昨晚有信息」——那等于把守人路线交出去。",
            "守谁：优先守你认为最像预言家或最关键的那个号，必要时守自己。",
            "被人怀疑身份时别急着辩解。过度解释本身就是破绽，一句「我没信息」就够了。",
            "守人的时候别只盯着「谁像预言家」：**突然跳出来、专挑活人发金水的预言家**要留个心眼——" +
                "那是悍跳狼最省力的做法。守错人等于白丢一个守护。",
        )

        WerewolfRole.HUNTER -> listOf(
            "你是猎人，翻牌之前不要报身份。",
            "你最后一枪的价值最大：带走你**最怀疑是狼**的人，而不是声音最大、骂得最凶的人。",
            "死之前可以留下判断，但别替好人做决定：你只剩一票的价值，浪费在嘴上就没人替你说话了。",
        )

        WerewolfRole.IDIOT -> listOf(
            "你已经翻牌了，现在只有一张嘴可用：继续用发言帮好人指认狼人。",
            "你不能投票，所以你的判断要说得比别人更具体：指出谁的话有矛盾，别只喊口号。",
        )

        WerewolfRole.BEAR -> listOf(
            "你不用急着自报身份：让别人先从咆哮里推，你一报，狼人下一夜就刀你。",
            "你的发言只有一个作用——帮好人排除，或者帮狼队把怀疑引开。",
        )

        WerewolfRole.VILLAGER -> listOf(
            "你没有夜间信息，只能靠白天的发言和投票分辨。",
            "不要因为有人给你发金水就信他——发金水是狼最省力的伪装。",
            "找矛盾比听语气有用：谁前后不一致、谁的行为和身份不匹配，票就往那儿投。",
            "别弃票。你的票不多，但好人常常是被自己的弃票输掉的。",
        )
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

                Phase.NIGHT_WOLF_CHAT -> {
                    val targets = game.aliveSeats.filter { it != seat && !game.isWerewolf(it) }
                    append("第 ${game.day} 夜，狼人睁眼。先和队友商量今晚刀谁。")
                    append("可选目标：").append(seats(targets)).append("。")
                    append("用 1-2 句中文说出你想刀谁、为什么；队友已经先说了，就先回应他。")
                    append("这一步只是商量，不要输出 JSON，也别说「好的」这种没有信息的话。")
                }

                Phase.NIGHT_WOLF -> {
                    val targets = game.aliveSeats.filter { it != seat && !game.isWerewolf(it) }
                    append("第 ${game.day} 夜，现在定刀。")
                    if (game.wolfVoteRound > 1) {
                        append("上一轮你们指了不同的人，这一轮必须统一——再谈不拢今晚就空刀。")
                    } else {
                        append("刚才已经商量过，现在各自定下目标：所有狼必须指同一个人，否则今晚空刀。")
                    }
                    append("可选目标：").append(seats(targets)).append("。")
                    append("先一句话说明理由，再给出目标座位号。")
                }

                Phase.NIGHT_SEER -> {
                    val targets = game.aliveSeats.filter { it != seat }
                    append("第 ${game.day} 夜，你要查验一个人。")
                    append("可选目标：").append(seats(targets)).append("。")
                    append("优先验还没查过的人——你已经掌握的信息比其他人多，别浪费这一验。")
                    append("理由写你**怀疑他什么**（一句话：他哪句话、哪个动作不对劲），")
                    append("别写「随机选择」这种没有信息的话，也别写你打算怎么用这条验人——")
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

                Phase.DAY_SHERIFF_SIGNUP ->
                    append(
                        "第 ${game.day} 天，警长竞选开始，先问你要不要上警。" +
                            "想当警长就把 target 填成你自己的 $seat 号，不想就填 null。" +
                            "当上警长票权 1.5 票、还能决定当天的发言起点，代价是被狼人优先盯上；" +
                            "而且上过警的人不能投警长票。" +
                            "这一步只是举手报名，不发言——别写竞选宣言，那些话留到竞选发言环节。"
                    )

                Phase.DAY_SHERIFF_SPEECH ->
                    append(
                        "现在轮到你竞选发言。用 2-3 句说服警下把票投给你：" +
                            "你的判断、你觉得谁是狼、你当选之后打算先推谁。" +
                            "不要念座位号清单，不要写「某某说：」这样的格式。" +
                            "如果你已经上台说过一次（PK 重新发言），必须回应对方这一轮说的话，" +
                            "不要把刚才那段再念一遍——复读是最容易被抓的破绽。"
                    )

                Phase.DAY_SHERIFF_VOTE ->
                    append(
                        "第 ${game.day} 天，警下投票选警长。候选人是 " +
                            seats(game.sheriffCandidates) + "。你可以投其中一个，也可以弃票。"
                    )

                Phase.DAY_LAST_WORDS ->
                    if (game.sheriffSeat == seat) {
                        append(
                            "你已经出局了。这是你的遗言，同时还要交代警徽交给谁：" +
                                "把要带走警徽的人填进 target，想撕掉就填 null。" +
                                "reason 会被当成你的遗言公开说出来，用 1-2 句交代你的判断。"
                        )
                    } else {
                        append(
                            "你已经出局了，现在说遗言。用 1-3 句交代你的判断：" +
                                "你怀疑谁、为什么，或者提醒活着的人别投错。" +
                                "只说你自己的话，不要替别人发言。"
                        )
                    }

                Phase.DAY_DISCUSSION -> {
                    append(
                        "第 ${game.day} 天白天讨论，轮到你发言。" +
                            "用 2-3 句中文说出你的判断：你怀疑谁、为什么，或者解释你上一轮为什么那样投票。" +
                            "只说你自己的话，不要替别人发言，不要写「某某说：」这样的格式。" +
                            "这一段必须和今天你已经说过的内容不同：结论没变就补理由或回应别人的质疑。"
                    )
                    // 验人是全场最硬的信息，也是好人认预言家的唯一凭据。
                    // 不点出来，模型会按人设聊感受，把一整晚的查验结果烂在手里，
                    // 然后被全场当成「跳了预言家却不报验人」投出去。
                    if (player.role == WerewolfRole.SEER && game.seerChecks.isNotEmpty()) {
                        val last = game.seerChecks.last()
                        append(
                            "开口第一件事先报验人：第 ${last.day} 夜你验的是 ${last.targetSeat} 号，" +
                                "结果是" + (if (last.isWerewolf) "狼人" else "好人") +
                                "。报完再谈判断——跳了预言家却不报验人，好人只能当你悍跳。"
                        )
                    }
                    // 自爆只对狼有意义。写给所有人等于向好人泄露「这局的狼会自爆」，
                    // 虽然规则公开可查，但提示词里不该出现一句只对狼有用的话
                    if (player.role.isWerewolf) {
                        append(
                            "自爆能立刻结束这一天、直接进入黑夜，但它只换来一天，用掉的是一条命。" +
                                "唯一值得的情况：**另一个队友**今天已经确定要被投出去，" +
                                "你爆了才能让他活下来。被怀疑的是你自己、你想搅乱讨论、" +
                                "你觉得再聊下去会露——这些都不许爆，爆了也救不回来。" +
                                "真要爆，就在发言最开头写「【自爆】救 X 号」，X 必须是还活着的队友座位号；" +
                                "写不出这个号码就别写标记，系统不会受理没有救到人的自爆。"
                        )
                    }
                }

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
                    append(
                        "你出局了，身份是${player.role.displayName}，现在可以开枪带走一个人，也可以放弃。" +
                            "这一枪会先结算，然后才判定胜负——即使队友已经全灭也还有翻盘机会。"
                    )
                    if (targets.isNotEmpty()) {
                        append("可选目标：").append(seats(targets)).append("。")
                    }
                }

                Phase.FINISHED -> append("游戏已经结束。")
            }
        }
    }

    // ---------------------------------------------------------------------

    /**
     * 规则说明。
     *
     * 「绑票」和「平票怎么处理」必须写进去：这两条决定了白天的算术——
     * 好人投错一次就再也追不回来，狼人可以故意把票打成平手。不写，
     * 模型就会按「平票还有一轮 PK」的线下记忆去推，推出的结论整个是错的。
     *
     * 同理，有警长和没警长的板子必须给出**不同的**平票规则：警长在场上才能追加一轮，
     * 而且 1.5 票会让「谁跟谁投」变得比人数更重要。
     */
    private fun ruleBrief(config: WerewolfConfig): String {
        val win = when (config.winRule) {
            WinRule.SLAY_SIDE ->
                "【胜负规则】屠边局：狼人杀光所有神职，或杀光所有平民，狼人获胜；好人放逐全部狼人则好人获胜。" +
                    "屠边之前先过绑票这一关。"

            WinRule.SLAY_ALL ->
                "【胜负规则】屠城局：狼人票权不少于好人票权时狼人获胜（警长一票算 1.5 票）；" +
                    "好人放逐全部狼人则好人获胜。"
        }
        return buildString {
            append(win).append("\n")
            // 绑票比的是**票权**不是人头：警长那 1.5 票也要算进去。
            // 写成「人数」的话，模型会按人头推，警长在手时推出来的结论整个是错的。
            append("【绑票】狼人票权不少于好人票权时，狼人直接获胜，游戏当场结束——")
            append("这时所有人一起投也压不过狼队的票，投不出人了。\n")
            if (config.has(WerewolfRole.GUARD) && config.has(WerewolfRole.WITCH)) {
                // 这条必须写给**所有人**：守卫和女巫互相不知道对方做了什么，
                // 守救同归时牌桌上看起来就是「明明守了又救了，怎么还是死了」。
                // 不提前说明，女巫会以为自己救的人被狼补刀，守卫会以为是自己守晚了。
                append("【守救同归】守卫守的人如果同时被女巫救了，他**还是会死**：")
                append("两种保护叠在一起反而同归于尽。守卫和女巫都只知道自己做的那一半，")
                append("所以你守的人、你救的人有可能已经被对方「也做了一次」。\n")
            }
            if (config.hasSheriff) {
                append("【白天投票】平票时，平票的人再各说一轮（PK），然后由全体重投一次；")
                append("再平票就当天无人出局。警长的一票算 1.5 票。\n")
                append("【发言顺序】每天从警长下一位开始，警长最后发言（他是归票位）。\n")
            } else {
                append("【白天投票】本局没有警长，没有 PK：平票就是当天无人出局，不存在加投一轮。\n")
                append("【发言顺序】每天从昨夜死者的下一位开始，按座位号绕一圈。\n")
            }
            append("【遗言】第一夜出局的人、每次被投票放逐的人、以及任何原因出局的警长，")
            append("出局后都会留一段遗言；第二夜起，夜里死的人不再发言。\n")
            append("【自爆】狼人可以在白天发言时自爆：当场出局，这一天立刻结束——没有投票、")
            append("没有人被放逐，直接进入黑夜。这条规则对狼是最后手段，不是常规打法。\n")
            append("【夜里】狼人多数决，平票加商一次。")
        }
    }

    /** 座位号列表，写成「1 号、2 号」，不带结尾标点。 */
    private fun seats(list: List<Int>): String =
        list.sorted().joinToString("、") { "$it 号" }
}
