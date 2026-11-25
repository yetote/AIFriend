package com.feature.werewolf.prompt

import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WerewolfSpeechLength
import com.feature.werewolf.game.WerewolfTurnLimit
import com.feature.werewolf.game.formatVotes
import com.feature.werewolf.prompt.boards.Boards
import com.feature.werewolf.prompt.roles.Roles
import com.feature.werewolf.prompt.rules.boardAndWinBrief
import com.feature.werewolf.prompt.rules.rulesBrief
import com.feature.werewolf.prompt.rules.terminologyBrief
import com.feature.werewolf.prompt.tasks.taskBrief

/**
 * 狼人杀入场提示词拼装器（v2 清爽无冲突架构）。
 *
 * 严格五层分层，每条规则与事实只出现一次，按阶段按需注入：
 * 1. 人设、牌风与座位身份；
 * 2. 本局硬规则与板子角色机制（消除警长规则双份重复）；
 * 3. 本阶段推理准则（警上只给警上准则、白天讨论给 6 条精炼硬准则、投票给精简核对表、夜间零白天噪音）；
 * 4. 公开局势 + 净化后的公开台账 + 每轮记录板；
 * 5. 私有事实与角色策略 + 残局挡刀 + 身份×阶段任务 + 时限/字数 + 带安全护栏的玩家自定义设定。
 */
object WerewolfPrompt {

    fun systemPrompt(
        game: WerewolfGame,
        seat: Int,
        persona: String? = null,
        custom: String? = null,
        limit: WerewolfTurnLimit? = null,
        style: WerewolfPromptStyle = WerewolfPromptStyle.GUIDED,
        webSearch: Boolean = false,
        speechLength: WerewolfSpeechLength? = null,
        playStyle: PlayerStyle? = null,
        ledger: String? = null,
        roundBoard: String? = null,
        includeReading: Boolean = true,
        readingScope: ReadingTells.Scope = ReadingTells.Scope.ALL,
    ): String {
        val player = game.playerAt(seat)
        // 警上竞选流程自动收窄为警上专属准则，不再塞整套白天讨论长文。
        val effectiveScope = when {
            readingScope == ReadingTells.Scope.VOTE -> ReadingTells.Scope.VOTE
            game.phase.isSheriffPhase -> ReadingTells.Scope.SHERIFF
            else -> readingScope
        }

        return buildString {
            if (!persona.isNullOrBlank()) {
                append(persona.trim()).append("\n\n")
            }
            playerStyleBrief(playStyle, game.config)?.let {
                append(it).append("\n\n")
            }
            append("你正在玩一局狼人杀，全程用简体中文说话，一次只说自己的话。\n")
            append("你是 ").append(seat).append(" 号玩家「").append(player.name).append("」。\n")
            if (player.role == WerewolfRole.WEREWOLF) {
                append("你的身份是狼人，这个身份只有你自己知道。\n\n")
            } else {
                append("你的身份是").append(player.role.displayName)
                    .append("（").append(player.faction.displayName).append("阵营），")
                    .append("这个身份只有你自己知道。\n\n")
            }

            if (style == WerewolfPromptStyle.SEARCH) {
                append(boardAndWinBrief(game.config).trimEnd()).append("\n\n")
            } else {
                append(rulesBrief(game.config).trimEnd()).append("\n\n")
                append(boardBrief(game.config).trimEnd()).append("\n\n")
            }

            if (style == WerewolfPromptStyle.GUIDED) {
                append(
                    strategyBrief(
                        config = game.config,
                        isWolf = player.role.isWerewolf,
                        includeReading = includeReading && effectiveScope != ReadingTells.Scope.VOTE,
                        readingScope = effectiveScope,
                    ).trimEnd()
                ).append("\n\n")
                if (includeReading && effectiveScope == ReadingTells.Scope.VOTE) {
                    append(factsBrief(game.config).trimEnd()).append("\n\n")
                }
            } else {
                append(factsBrief(game.config).trimEnd()).append("\n\n")
            }

            if (style != WerewolfPromptStyle.SEARCH) {
                append(terminologyBrief()).append("\n\n")
            }

            if (style == WerewolfPromptStyle.SEARCH && webSearch) {
                append("本局你可以联网检索，对规则、身份技能或术语没把握时先查再说，但别在发言里提「我查了一下」。")
                append("场上事实一律以本提示词记录为准。\n\n")
            }

            append(publicSituation(game).trimEnd()).append("\n\n")
            if (!ledger.isNullOrBlank()) {
                append(ledger.trimEnd()).append("\n\n")
            }
            if (!roundBoard.isNullOrBlank()) {
                append(roundBoard.trimEnd()).append("\n\n")
            }
            append(privateBriefing(game, seat, style).trimEnd()).append("\n\n")

            if (style == WerewolfPromptStyle.GUIDED) {
                val bluff = GoodBluff.of(game, seat)
                if (bluff.isNotEmpty()) {
                    append(bluff.joinToString("\n")).append("\n\n")
                }
            }

            append(task(game, seat, style))

            if (limit != null && limit != WerewolfTurnLimit.UNLIMITED) {
                append("\n\n").append(timeBudgetBrief(limit))
            }
            if (speechLength != null) {
                append("\n\n").append(speechLengthBrief(speechLength))
            }
            if (!custom.isNullOrBlank()) {
                append("\n\n")
                append("【本局额外设定（玩家风格偏好；在不违背本阵营胜负目标、游戏硬规则与团队既定分工的前提下优先采纳）】\n")
                append(custom.trim())
            }
        }
    }

    /** 「要不要当场插爆」的精简判断简报。 */
    fun explodeWatchBrief(game: WerewolfGame, seat: Int, speaker: Int, speech: String): String = buildString {
        val player = game.playerAt(seat)
        val speakerPlayer = game.playerAt(speaker)
        val mates = game.wolves().filter { it.seat != seat }.map { it.seat }
        append("你是 ").append(seat).append(" 号")
        append(if (player.role == WerewolfRole.WEREWOLF_KING) "狼王" else "狼人")
        append("，身份只有你自己知道。")
        if (mates.isEmpty()) {
            append("这一局你没有队友。\n")
        } else {
            append("你的队友是 ").append(mates.joinToString("、") { "$it 号" }).append("。\n")
        }
        append("现在是第 ").append(game.day).append(" 天白天，")
        append(speakerPlayer.seat).append(" 号「").append(speakerPlayer.name).append("」刚说完一段话。\n")
        append("自爆＝你当场出局，这一天立刻结束、直接入夜（无放逐）；今晚狼队商议和定刀你仍参加，定完离场。\n")
        append("默认**不自爆**。只在这两种情况下值得爆：① 夜里战术需要吞掉这个白天/警徽（如双爆吞警徽）；② 再说下去会直接牵出队友。\n")
        append("**被查杀、被踩、被点名表水都不是理由**——那是一句指控，不是判决：自爆等于当场替预言家作证。\n")
        append("算不出明确收益的自爆就是白送一命。\n")
        append("刚那段话：「").append(speech.take(WATCH_SPEECH_CHARS)).append("」")
    }

    private const val WATCH_SPEECH_CHARS = 600

    // ---------------------------------------------------------------------
    // 公开局势
    // ---------------------------------------------------------------------

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

    fun publicSituation(game: WerewolfGame): String = buildString {
        append("【当前局势】第 ").append(game.day).append(" 天 · ").append(game.phase.displayName).append("\n")
        val announcedDead = announcedDeadSeats(game)
        val alive = game.players.map { it.seat }.filter { it !in announcedDead }
        append("存活：").append(seatsText(alive)).append("（共 ").append(alive.size).append(" 人）\n")
        if (announcedDead.isNotEmpty()) {
            append("已出局：").append(seatsText(announcedDead)).append("\n")
        }
        if (game.config.hasSheriff) {
            val sheriff = game.aliveSheriffSeat
            append("警长：").append(
                if (sheriff != null) "$sheriff 号（票权 1.5 票，决定白天发言顺序）"
                else "暂无（还没选出，或警徽已经流失）"
            ).append("\n")
            if (game.sheriffRunners.isNotEmpty() && game.phase.isSheriffPhase) {
                append("上警：").append(seatsText(game.sheriffRunners)).append("\n")
            }
        }
        if (game.pendingDawn != null) {
            append("\n【昨夜还没公布】昨夜死讯尚未播报，**任何人都不要谈昨夜结果**（连「昨晚平安夜」都不许提）。只说今天台面上的事。\n")
        }
        append("\n【已经发生的事】\n")
        if (game.history.isEmpty()) {
            append("还没有任何播报，这是第一夜。")
            return@buildString
        }
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

    private const val EXPANDED_DAYS = 2

    private fun summarize(events: List<GameEvent>): String =
        events.mapNotNull { brief(it) }.joinToString("；").ifEmpty { "没有需要记住的事" }

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
        is GameEvent.BearRoar -> "熊在咆哮（左右邻座有狼）"
        is GameEvent.WolfExploded -> "${event.seat} 号狼人自爆，当天直接入夜"
        is GameEvent.SheriffSignup -> "上警的是 ${seatsText(event.runners)}"
        is GameEvent.SheriffElected -> when {
            event.votes > 0 -> "${event.seat} 号当选警长（${event.votes} 票）"
            event.note != null -> "${event.seat} 号自动当选警长（${event.note}）"
            else -> "${event.seat} 号自动当选警长"
        }
        is GameEvent.SheriffWithdrawn -> "${seatsText(event.seats)}退水，退出竞选"
        is GameEvent.SheriffVacant -> "没有警长（${event.reason}）"
        is GameEvent.SheriffPostponed -> "竞选被自爆打断，警徽顺延到明天再选"
        is GameEvent.SheriffPk -> null
        is GameEvent.SheriffBadgePassed ->
            if (event.toSeat == null) "警徽作废" else "警徽交给 ${event.toSeat} 号"
        is GameEvent.SpeechDirectionChosen ->
            "警长 ${event.seat} 号定了从${event.direction.displayName}起发言"
        is GameEvent.GameOver -> "${event.winner.displayName}阵营获胜"
    }

    private val Phase.isSheriffPhase: Boolean
        get() = this == Phase.DAY_SHERIFF_SIGNUP || this == Phase.DAY_SHERIFF_SPEECH ||
            this == Phase.DAY_SHERIFF_WITHDRAW || this == Phase.DAY_SHERIFF_VOTE

    fun describe(event: GameEvent): String = when (event) {
        is GameEvent.Dawn -> if (event.isPeaceful) {
            "第${event.day}天 天亮：平安夜，没有人出局"
        } else {
            "第${event.day}天 天亮：${seatsText(event.deadSeats)}出局"
        }
        is GameEvent.Exiled ->
            "第${event.day}天 投票：${event.seat} 号被放逐（${formatVotes(event.voteCount)} 票）"
        is GameEvent.NoExile -> "第${event.day}天 投票：无人被放逐"
        is GameEvent.IdiotRevealed ->
            "第${event.day}天 投票：${event.seat} 号以 ${formatVotes(event.voteCount)} 票被投出，" +
                "翻开是白痴，没有出局，此后失去投票权"
        is GameEvent.HunterShot -> if (event.targetSeat == null) {
            "第${event.day}天：${event.shooterLabel} ${event.hunterSeat} 号出局，没有开枪"
        } else {
            "第${event.day}天：${event.shooterLabel} ${event.hunterSeat} 号开枪带走了 ${event.targetSeat} 号"
        }
        is GameEvent.BearRoar -> "第${event.day}天 天亮：熊在咆哮"
        is GameEvent.WolfExploded -> "第${event.day}天：${event.seat} 号狼人自爆，直接进入黑夜"
        is GameEvent.SheriffSignup -> "第${event.day}天：上警的是 ${seatsText(event.runners)}"
        is GameEvent.SheriffElected -> when {
            event.votes > 0 -> "第${event.day}天：${event.seat} 号当选警长（${event.votes} 票）"
            event.note != null -> "第${event.day}天：${event.note}，${event.seat} 号自动当选警长"
            else -> "第${event.day}天：只有 ${event.seat} 号上警，自动当选警长"
        }
        is GameEvent.SheriffWithdrawn -> "第${event.day}天：${seatsText(event.seats)}退水，退出竞选"
        is GameEvent.SheriffVacant -> "第${event.day}天：本局没有警长（${event.reason}）"
        is GameEvent.SheriffPostponed -> "第${event.day}天：${event.seat} 号自爆，警长竞选中止，警徽顺延到下一个白天"
        is GameEvent.SheriffPk -> "第${event.day}天：警长投票平票（${seatsText(event.seats)}），进入 PK 发言"
        is GameEvent.SheriffBadgePassed -> if (event.toSeat == null) {
            "第${event.day}天：警长 ${event.fromSeat} 号出局，警徽没有交出，已作废"
        } else {
            "第${event.day}天：警长 ${event.fromSeat} 号把警徽交给 ${event.toSeat} 号"
        }
        is GameEvent.SpeechDirectionChosen ->
            "第${event.day}天：警长 ${event.seat} 号决定今天从${event.direction.displayName}起发言"
        is GameEvent.GameOver -> "游戏结束：${event.winner.displayName}阵营获胜"
    }

    // ---------------------------------------------------------------------
    // 板子与身份机制（不再重复警长规则）
    // ---------------------------------------------------------------------

    private val RULE_ORDER = listOf(
        WerewolfRole.GUARD,
        WerewolfRole.WITCH,
        WerewolfRole.HUNTER,
        WerewolfRole.IDIOT,
        WerewolfRole.WEREWOLF_KING,
        WerewolfRole.BEAR,
    )

    fun boardBrief(config: WerewolfConfig): String = buildString {
        append("【本局板子】").append(compositionText(config)).append("\n")
        RULE_ORDER.filter { config.has(it) }.forEach { role ->
            Roles.of(role).rules(role).forEach { append(it).append("\n") }
        }
    }

    private fun compositionText(config: WerewolfConfig): String =
        config.playerCount.toString() + " 人局：" + WerewolfRole.entries
            .mapNotNull { role ->
                config.composition[role]?.takeIf { it > 0 }?.let { count ->
                    if (count == 1) role.displayName else "$count ${role.displayName}"
                }
            }
            .joinToString("、")

    // ---------------------------------------------------------------------
    // 基本打法与事实判据（零重复、按阶段注入）
    // ---------------------------------------------------------------------

    private fun strategyBrief(
        config: WerewolfConfig,
        isWolf: Boolean,
        includeReading: Boolean = true,
        readingScope: ReadingTells.Scope = ReadingTells.Scope.ALL,
    ): String = buildString {
        append("【基本打法】这是一场阵营对抗的推理游戏，不要客气和稀泥，以可核对事实和阵营胜负为准：\n")
        append("· 每轮发言都要给出清晰的怀疑对象、推进方向与新信息（新依据、回应质疑或解释票向），不复读旧话；被指认先正面回应具体质疑再反打。\n")
        if (config.has(WerewolfRole.GUARD)) {
            Roles.of(WerewolfRole.GUARD).publicStrategy(WerewolfRole.GUARD)?.let {
                append("· ").append(it).append("\n")
            }
        }
        if (includeReading) {
            ReadingTells.lines(config, readingScope).forEach { append("· ").append(it).append("\n") }
        }
        val board = Boards.text(config)
        board.common.forEach { append("· ").append(it).append("\n") }
        if (isWolf) board.wolves.forEach { append("· ").append(it).append("\n") }
    }

    /** 投票决策与自由式/搜索式专用的精简事实核对表（绝不含打法倾向）。 */
    private fun factsBrief(config: WerewolfConfig): String = buildString {
        append("【核对事实】拿不准时按以下可核硬判据判断：\n")
        append("· **验人前后改口 = 假预**：真预言家不会记错自己验了谁；先报金水后打铁狼、改验人对象基本坐实假预。\n")
        append("· **首夜零信息无心路**：第 1 天只报 1 验、验到死人、说不出心路全正常，只核座位等可验算理由；第 2 天起看警徽流是否兑现（改流与验过补新合法）。\n")
        append("· **暗牌边界**：出局不翻牌，**猎人开枪只代表猎人个人怀疑，不等于验出对方是狼**。")
        if (config.has(WerewolfRole.IDIOT)) {
            append("白痴翻牌是硬反证（报他查杀者铁假）。")
        }
        if (config.has(WerewolfRole.WITCH)) {
            append("**双死夜必是一刀一毒**，警长/跳预死在双死夜时须同时盘吃刀与吃毒两种可能。")
        }
        append("\n")
        append("· **票型对账**：话投 A 实投 B、全程绑定冲票是硬证据；被发金水不证清白，敢报查杀也不等于真。\n")
        append("· **警长票 vs 白天放逐**：警长票投综合最可信的真预（不因谁报查杀就盲投他）；白天放逐遵循 查杀 > 对跳 > 行为破绽，少分票，完全分不清宁可弃票。\n")
    }

    // ---------------------------------------------------------------------
    // 私有信息（按角色与阶段精确组装，删除「争到底」与「一刀切禁言」）
    // ---------------------------------------------------------------------

    fun privateBriefing(
        game: WerewolfGame,
        seat: Int,
        style: WerewolfPromptStyle = WerewolfPromptStyle.GUIDED,
    ): String {
        val player = game.playerAt(seat)
        val kit = Roles.of(player.role)
        val guided = style == WerewolfPromptStyle.GUIDED

        val lines = mutableListOf<String>()
        lines += kit.notes(game, seat, style)
        if (guided) {
            lines += kit.playbook(player.role, game.config)
        }

        if (game.sheriffSeat == seat && game.phase != Phase.FINISHED) {
            lines += "你是警长：一票算 1.5 票，每天讨论前选一次发言顺序（警左/警右你最后归票，死左/死右从昨夜死者旁起）。"
            lines += (if (guided) "你出局时要决定警徽归属——交给可能是狼的人比撕掉更糟。"
            else "你出局时要决定把警徽交给谁或撕掉。") +
                "被猎人开枪带走时虽无遗言，警徽照常处理。"
        }

        if (seat in game.sheriffCandidates && game.phase.isSheriffPhase) {
            lines += if (!guided) {
                "你在竞选警长。上过警的人首轮不能投警长票。"
            } else {
                when {
                    player.role == WerewolfRole.SEER ->
                        "你在竞选警长（上过警首轮不投警长票）。你是真预言家，尽量争取警徽、不要轻易退水。"
                    player.role.isWerewolf ->
                        "你在竞选警长（上过警首轮不投警长票）。按昨晚头狼分工执行：被安排悍跳就争到底不退水，非悍跳位别抢跳预言家、适时退水。"
                    else ->
                        "你在竞选警长（上过警首轮不投警长票）。你不是预言家：听完竞选发言若已有预言家对跳，应当主动退水把警徽留给真预。"
                }
            }
        } else if (seat in game.sheriffRunners && game.phase == Phase.DAY_SHERIFF_VOTE && game.sheriffRound > 1) {
            lines += "你首轮上过警但未进入本轮 PK，现在作为台外玩家拥有本轮 PK 警长投票权。"
        }

        if (game.phase == Phase.DAY_LAST_WORDS && seat in game.pendingLastWords) {
            lines += if (player.deathCause == DeathCause.HUNTER_SHOOT && game.sheriffSeat == seat) {
                "你被开枪带走，按规则无遗言，但必须处理警徽（移交或撕掉）。"
            } else {
                "你已出局，现在发表遗言，把有价值的判断留给活着的人。"
            }
        }

        if (seat in game.ghostWolves) {
            lines += "你白天已自爆出局，但今晚狼队商议和定刀你仍参加，定完立刻彻底离场。"
        }

        return buildString {
            append("【只有你知道的事】\n")
            lines.forEach { append("· ").append(it).append("\n") }
            if (guided) {
                kit.conceal(player.role)?.let { append(it) }
            }
        }
    }

    fun task(game: WerewolfGame, seat: Int, style: WerewolfPromptStyle = WerewolfPromptStyle.GUIDED): String =
        taskBrief(game, seat, style)

    internal fun timeBudgetBrief(limit: WerewolfTurnLimit): String = buildString {
        append("【时间预算】开口前思考约 ").append(limit.decisionSeconds).append(" 秒")
        if (limit.speechSeconds > 0) {
            append("，发言约 ").append(limit.speechSeconds).append(" 秒")
        } else {
            append("，说出口之后不限时间")
        }
        append("。超时会按兜底处理并关掉你本局深度思考，所以**先把结论说出来**（验人、站边、目标座位放最前），理由放后面，别在心里反复纠结。")
    }

    internal fun speechLengthBrief(length: WerewolfSpeechLength): String = buildString {
        if (length.chars > 0) {
            append("【发言长度】公开发言控制在约 ").append(length.chars)
                .append(" 字以内（当前档：").append(length.label).append("）。说清判断和依据就收，超出会被截断。")
        } else {
            append("【发言长度】发言不限字数（当前档：").append(length.label)
                .append("）。但别灌水，先把结论说出来再简明展开。")
        }
    }
}
