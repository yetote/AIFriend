package com.feature.werewolf.prompt

import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfSpeechLength
import com.feature.werewolf.game.WerewolfTurnLimit
import com.feature.werewolf.game.formatVotes
import com.feature.werewolf.prompt.boards.Boards
import com.feature.werewolf.prompt.roles.Roles
import com.feature.werewolf.prompt.rules.rulesBrief
import com.feature.werewolf.prompt.tasks.taskBrief

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
 *
 * ## 内容放在哪
 *
 * 这个文件只做**拼装**，具体的话分在四个地方，加板子或加身份时不用改这里：
 * - `rules/GameRules.kt`：所有人共用的规则（胜负、绑票、投票、遗言、自爆）；
 * - `roles/`：**按身份**，一个身份一个 `XxxKit`——机制、白天打法、私有简报、收尾叮嘱；
 * - `boards/`：**按板子**，一张板子一个 `XxxBoard`——几狼几神几民、打哪边更省刀、容错几次；
 * - `tasks/TaskBriefs.kt`：**按阶段**——这一步要做什么。
 *
 * 为什么按身份、不把所有东西按板子铺开：板子只是身份的集合，同一份守卫规则
 * 要出现在三张板子里，按板子写就是抄三遍、改一漏二。板子上真正独有的只剩
 * 「人数结构」这一层，那才是 `boards/` 该管的事。
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
        /**
         * 用户自定义的补充设定（全局 + 当前板子拼在一起）。
         *
         * 放在**最后**：后写的优先，用户想微调策略时，这段能压过内置的默认打法。
         * 但它进不了公共播报、也不进 transcript——只在这一个座位的 system prompt 里，
         * 信息隔离不受影响。
         */
        custom: String? = null,
        /**
         * 本局每一步的时限（来自 [WerewolfTurnLimit]）。
         *
         * 告诉模型自己有多少时间，是为了让它**自己把结论前置**：不说的话，它按
         * 聊天习惯先铺垫两三百字，真正有用的验人/查杀压在末尾，一到点被掐断就前功尽弃。
         * 这里只写档位对应的秒数，不写谁已经被关了深度思考——那是运行时状态，
         * 每轮都会变，写进去等于让模型反复收到自相矛盾的要求。
         *
         * 默认 null＝不注入，让调用方（如复盘、测试）在不需要时限的场合保持原样。
         */
        limit: WerewolfTurnLimit? = null,
        /**
         * 提示词风格。默认 [WerewolfPromptStyle.GUIDED]＝现状，所有老测试与存档行为不变。
         *
         * [WerewolfPromptStyle.FREE] 只砍**打法**：读人框架、身份作业、狼队战术、屠边账、
         * 残局挡刀都不给；身份、规则、板子、局势、私有信息（含保密底线）、本轮任务照旧。
         * 保密底线在其中尤其重要——自由不等于允许在白天报验人或说出队友。
         */
        style: WerewolfPromptStyle = WerewolfPromptStyle.GUIDED,
        /**
         * 这一局发言的目标长度（见 [WerewolfSpeechLength]）。默认 null＝不注入，
         * 让不需要的场合（测试、复盘）保持原样。
         *
         * 和时限一样是环境约束：不说的话模型按聊天习惯写小作文，
         * 被引擎裁掉的后半段里可能藏着关键判断。
         */
        speechLength: WerewolfSpeechLength? = null,
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
            append(rulesBrief(game.config)).append("\n\n")
            append(boardBrief(game.config).trimEnd()).append("\n\n")
            // 引导式才给内置打法。自由式的核心就是「不给方法，只给事实和底线」——
            // 读人框架、板型结论、狼队菜单都在 strategyBrief 里，整段不出现。
            if (style == WerewolfPromptStyle.GUIDED) {
                append(strategyBrief(game.config, player.role.isWerewolf).trimEnd()).append("\n\n")
            }
            append(publicSituation(game).trimEnd()).append("\n\n")
            append(privateBriefing(game, seat, style)).append("\n\n")
            // 残局挡刀是打法，自由式不给——能不能靠穿神衣服晃狼，让它自己判断。
            if (style == WerewolfPromptStyle.GUIDED) {
                val bluff = GoodBluff.of(game, seat)
                if (bluff.isNotEmpty()) {
                    append(bluff.joinToString("\n")).append("\n\n")
                }
            }
            append(task(game, seat))
            // 时限是环境约束不是打法，两种风格都给：不给模型会铺垫太长被掐断，
            // 那就变成「测超时兜底」而不是「测它自己的判断」了。
            if (limit != null && limit != WerewolfTurnLimit.UNLIMITED) {
                append("\n\n")
                append(timeBudgetBrief(limit))
            }
            // 发言长度同样两种风格都给：它不是打法，是这一局「说多少」的约束。
            if (speechLength != null) {
                append("\n\n")
                append(speechLengthBrief(speechLength))
            }
            // 用户自定义放最后：它是「玩家对这一局玩法的要求」，
            // 位置太靠前会被后面固定的几段稀释掉，模型照着内置打法走就白改了。
            if (!custom.isNullOrBlank()) {
                append("\n\n")
                append("【本局额外设定（玩家写的，优先级高于上面所有默认打法）】\n")
                append(custom.trim())
            }
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
        append("存活：").append(seatsText(alive))
            .append("（共 ").append(alive.size).append(" 人）\n")
        if (announcedDead.isNotEmpty()) {
            append("已出局：").append(seatsText(announcedDead)).append("\n")
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
                append("上警：").append(seatsText(game.sheriffRunners)).append("\n")
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
            if (event.isPeaceful) "夜里平安" else "夜里 ${seatsText(event.deadSeats)}出局"

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

        is GameEvent.SheriffSignup -> "上警的是 ${seatsText(event.runners)}"

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
            "第${event.day}天 天亮：${seatsText(event.deadSeats)}出局"
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
            "第${event.day}天：上警的是 ${seatsText(event.runners)}"

        is GameEvent.SheriffElected -> if (event.votes <= 0) {
            "第${event.day}天：只有 ${event.seat} 号上警，自动当选警长"
        } else {
            "第${event.day}天：${event.seat} 号当选警长（${event.votes} 票）"
        }

        is GameEvent.SheriffVacant ->
            "第${event.day}天：本局没有警长（${event.reason}）"

        is GameEvent.SheriffPk ->
            "第${event.day}天：警长投票平票（${seatsText(event.seats)}），进入 PK 发言"

        is GameEvent.SheriffBadgePassed -> if (event.toSeat == null) {
            "第${event.day}天：警长 ${event.fromSeat} 号出局，警徽没有交出，已作废"
        } else {
            "第${event.day}天：警长 ${event.fromSeat} 号把警徽交给 ${event.toSeat} 号"
        }

        is GameEvent.GameOver ->
            "游戏结束：${event.winner.displayName}阵营获胜"
    }

    // ---------------------------------------------------------------------
    // 板子
    // ---------------------------------------------------------------------

    /**
     * 板子规则的出场顺序。
     *
     * 按「谁会改变夜里的结果」排：守卫最影响判断，狼王和白痴其次。顺序写死在这里，
     * 而不是直接遍历 [WerewolfRole.entries]——枚举的声明顺序是代码组织，不该决定文案顺序。
     */
    private val RULE_ORDER = listOf(
        WerewolfRole.GUARD,
        WerewolfRole.IDIOT,
        WerewolfRole.WEREWOLF_KING,
        WerewolfRole.BEAR,
    )

    /**
     * 本局板子。
     *
     * 板子是**公开信息**：线下开局前所有人都知道今天打的是什么配置。不告诉模型，
     * 它就会拿 8 人局的经验去推 12 人局——比如笃定场上只有三神，或者不知道有守卫。
     *
     * 内容来自两处：身份规则（`roles`，谁在场就说谁）和板子规则（`boards`，警长这类
     * 不归任何身份的东西）。这里只负责按顺序拼起来。
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
        RULE_ORDER.filter { config.has(it) }.forEach { role ->
            Roles.of(role).rules(role).forEach { append(it).append("\n") }
        }
        Boards.sheriffRules(config).forEach { append(it).append("\n") }
    }

    // ---------------------------------------------------------------------
    // 基本打法
    // ---------------------------------------------------------------------

    /**
     * 判断方法，而不是规则。
     *
     * [rulesBrief] 说的是「能做什么、怎么算赢」，这一层说的是「怎么判断谁像狼」。
     * 缺了这一层，模型的发言完全由人设驱动，实测出现过三种灾难：
     * 真预言家在警上、PK、白天把同一段话说了三遍；守卫第一天就当众解释
     * 「我昨夜守了自己，所以是平安夜」；好人看到预言家对跳，只看谁语气更硬。
     *
     * 写死在这里而不是塞进每个角色的私有简报：**判断别人的方法是公共知识**，
     * 平民、狼人、神职都用得上，而且必须一致——否则同一局里两个人用两套标准。
     *
     * 每条都尽量写成「可执行的动作」，不写「要理性分析」这种正确但没用的话。
     *
     * @param isWolf 板型打法里有一段只对狼有用（打哪边更省刀、要不要分头站边），
     *   好人读到只会稀释自己的判断，所以按阵营分开。
     */
    private fun strategyBrief(config: WerewolfConfig, isWolf: Boolean): String = buildString {
        append("【基本打法】以下和你的性格、说话风格无关，冲突时以这里为准：\n")
        append("· 预言家每天开口第一件事，是报出前一晚验了谁、结果是好人还是狼人，然后才谈判断。")
        append("跳过不报、只喊结论不报验人，在好人眼里就是悍跳。\n")
        append("· 谁跳了预言家却报不出连续的验人记录，或者验的人专挑已经被全场怀疑的，就该被盯上——")
        append("这是好人盘对跳的第一依据，比语气和文采可靠得多。\n")
        // 第 1 天只有一个验人记录，说人家「警徽流接不上」会把真预言家冤死。
        // 实测一局 12 人：真预言家第 1 夜验的正好是当夜被刀的人，只报了一个人就被说成
        // 「死无对证」，好人全票投他，狼人顺势拿到警徽——真预言家第一天就走了。
        append("· **第 1 天只报了一个验人是正常的**，别因为他只报了一个就判他悍跳；")
        append("验到死人只是运气不好（他自己也不知道对方会死），不是悍跳的证据。")
        append("只有**验了活人还乱报、或者报不出自己验过的人**，那才是悍跳。\n")
        append("· 狼给谁发金水都不亏，「被发金水」证明不了干净；反过来，给金水的人也没必要急着投。\n")
        // 从发言读人的框架：上面几条是「验人/金水该不该信」的单点判断，
        // 这里补成一套能贯穿整局的方法（心路、警徽流、保人、划水与冲锋的反面）。
        // 放在单点判断之后、板型打法之前，先教「怎么读」，再给「这张板子读什么」。
        ReadingTells.lines(config).forEach { append("· ").append(it).append("\n") }
        append("· 神职不要主动报身份。")
        // 只有守卫有这一句：别人守不守人跟好人无关，守卫自己却最容易一开口就交代守人路线。
        Roles.of(WerewolfRole.GUARD).publicStrategy(WerewolfRole.GUARD)
            ?.takeIf { config.has(WerewolfRole.GUARD) }
            ?.let { append(it) }
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

        // 板型打法：由人数与阵营结构决定，模型自己数不出来，也不该靠猜。
        val board = Boards.text(config)
        board.common.forEach { append("· ").append(it).append("\n") }
        if (isWolf) board.wolves.forEach { append("· ").append(it).append("\n") }
    }

    // ---------------------------------------------------------------------
    // 私有信息
    // ---------------------------------------------------------------------
    /**
     * 只有本人知道的那些事。
     *
     * 每种身份各自不同，共同点是**绝不进公共播报**。狼队友必须知道彼此，否则没法配合；
     * 预言家必须看到历史查验，否则每晚都在重复验同一个人。
     *
     * 内容全在 `roles/` 的各份 `XxxKit` 里：`notes` 是局面相关的私有信息，`playbook` 是身份作业。
     * 这个函数只负责「按什么顺序拼」和「哪些是任何身份都可能碰上的事」。
     */
    fun privateBriefing(
        game: WerewolfGame,
        seat: Int,
        style: WerewolfPromptStyle = WerewolfPromptStyle.GUIDED,
    ): String {
        val player = game.playerAt(seat)
        val kit = Roles.of(player.role)

        val lines = mutableListOf<String>()
        // notes 是私有**事实**（队友是谁、验过谁、药还剩什么），不是打法——
        // 自由式照样给：拿掉它模型就不知道自己是狼、不知道验人结果，游戏没法打。
        lines += kit.notes(game, seat, style)
        // 身份专属打法。只给本人看，所以可以写得很具体——
        // 公共打法（strategyBrief）解决「怎么判断」，这里解决「我这个身份该说什么」。
        // 自由式不给：这是「内置答案」最集中的一层。
        if (style == WerewolfPromptStyle.GUIDED) {
            lines += kit.playbook(player.role, game.config)
        }

        // 警长、竞选、遗言这三件事和身份无关，任何座位都可能碰上，所以放在身份之外。
        // 尤其是「你是警长」——警长本身是公开身份，但**只有本人**需要知道自己的票权是 1.5。
        if (game.sheriffSeat == seat && game.phase != Phase.FINISHED) {
            lines += "你是警长：你的一票算 1.5 票，白天从你的下一位开始发言、你最后说（归票位）。"
            lines += "你出局时要决定把警徽交给谁——交给错的人比撕掉更糟。" +
                "警徽移交和遗言是两件事：被猎人一枪带走时你说不了话，但警徽照样要处理。"
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
            kit.conceal(player.role)?.let { append(it) }
        }
    }

    // ---------------------------------------------------------------------
    // 本轮任务
    // ---------------------------------------------------------------------

    /** 当前阶段要做什么。内容在 `tasks/TaskBriefs.kt`，这里只留入口。 */
    fun task(game: WerewolfGame, seat: Int): String = taskBrief(game, seat)

    /**
     * 时间预算的一段话。UNLIMITED 由调用方跳过，这里只处理有限的档位。
     *
     * 关键是**结论前置**：把验人、查杀、站边、目标座位号写在最前，理由随后。
     * 这样就算真被掐断，丢的也只是后半段补充，核心判断已经落地。
     * 不说这一句时，模型习惯先铺垫一大段再给结论，超时就什么都没剩。
     */
    internal fun timeBudgetBrief(limit: WerewolfTurnLimit): String = buildString {
        append("【时间预算】这一局你开口前想的时间大约 ")
            .append(limit.decisionSeconds).append(" 秒")
        if (limit.speechSeconds > 0) {
            append("，说的时间大约 ").append(limit.speechSeconds)
                .append(" 秒（从你说出第一个字起算）")
        } else {
            append("，说出口之后不限时间")
        }
        append("。")
        append("超时不会让游戏停下，但这一步会按兜底处理，并关掉你这一局的深度思考。")
        append("所以**先把结论说出来**：验人结果、查杀、你站边谁、要投谁、目标座位号放最前面，")
        append("理由和铺垫放在后面——哪怕说到一半被打断，关键判断也已经说出口了。")
        append("别把所有可能性在心里过一遍再开口：想太久，说出来的往往不是更好的答案。")
    }

    /**
     * 发言长度的一段话。null 由调用方跳过，这里只处理确定的档位。
     *
     * 不只说上限：还告诉模型「为什么」——说清判断就收。只给一个数字，
     * 模型会把它当成硬指标，为凑字数注水或者被掐时丢结论。
     */
    internal fun speechLengthBrief(length: WerewolfSpeechLength): String = buildString {
        if (length.chars > 0) {
            append("【发言长度】这一局你每次公开发言控制在约 ").append(length.chars)
                .append(" 字以内（当前档：").append(length.label).append("）。")
            append("说清你的判断和理由就收，不用凑字数，也别写成小作文——")
            append("超出的部分会被直接截断，压在后面的结论会一起丢掉。")
        } else {
            // 不限档：没有截断兜底，只能靠模型自觉——把「结论前置」抬高到第一位，
            // 长发言里最重要的东西丢了比短发言更伤。
            append("【发言长度】这一局你的发言不限字数（当前档：").append(length.label).append("）。")
            append("但也别灌水：先把结论说出来，再展开论证；")
            append("别人还要跟着你的话做判断，读不完的长篇等于没有发言。")
        }
    }
}
