package com.feature.werewolf.engine

import com.core.ai.model.ChatMessage
import com.core.ai.structured.StructuredOutcome
import com.core.ai.structured.StructuredSpec
import com.core.ai.structured.decide
import com.feature.werewolf.decision.SeatChoice
import com.feature.werewolf.decision.VoteChoice
import com.feature.werewolf.decision.WerewolfDecisions
import com.feature.werewolf.decision.WitchChoice
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.prompt.WerewolfPrompt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

/**
 * 把 [WerewolfGame] 的规则状态和真实模型接起来。
 *
 * 这里只做调度，不做判定——谁死谁赢全部交给状态机。三条调度原则：
 *
 * 1. **发言串行，决策并发。** 白天讨论、以及夜里的狼人商议，必须一个人说完另一个人接
 *    （后一位要能接上前一位的话）；而白天投票必须是**同时收集、互相看不到对方的选择**。
 *    串行收集投票会让后面的 AI 看见前面的票型，等于公开投暗票。
 *    狼人下刀虽然也是并发，但在此之前已经有一轮公开商议，所以并发的只是最后那一下。
 * 2. **身份每轮现拼。** 每调一次模型都重新生成 system prompt，不缓存、不依赖历史长度。
 *    这是「上下文被裁掉之后 AI 忘了自己是狼」的唯一解法。
 * 3. **可见性跟着消息走。** 夜间消息自带 [WerewolfMessage.audience]，
 *    拼上下文时按座位过滤，不靠调用方记得「这里是狼人视角」。
 *
 * 调度本身不抛业务异常：模型输出跑偏由 `decide()` 重试并兜底，规则非法由校验挡住。
 * 只有传输层错误（网络、Key）会往上抛，那类问题重试没意义，也不该被伪装成「AI 乱投票」。
 */
class WerewolfDirector(
    private val agents: Map<Int, SeatAgent>,
    private val random: Random = Random.Default,
    /**
     * 每推进一步回调一次，用来给界面显示「现在在做什么、到第几个」。
     *
     * 和 `play()` 的 `onUpdate` 分工不同：`onUpdate` 是**阶段结束**时给一次快照，
     * 这里给的是**阶段内部**的步进——白天 8 个人发言要发 8 次，没有它这中间就是几十秒的静止。
     */
    private val onProgress: suspend (WerewolfProgress) -> Unit = {},
    /**
     * 阶段**内部**产出一条消息就回调一次，用来让界面边打边显示。
     *
     * 和 `play()` 的 `onUpdate` 差在粒度：`onUpdate` 一个阶段才给一次快照，
     * 白天讨论八个人就是一分多钟之后才一次性蹦出八条——中间那段时间界面是静止的，
     * 看着像卡死。这个回调只更新界面，不落档（落档仍然是每阶段一次）。
     */
    private val onTranscript: suspend (WerewolfTable) -> Unit = {},
) {

    // ---------------------------------------------------------------------
    // 夜晚
    // ---------------------------------------------------------------------

    /**
     * 守卫守护。**先于狼人**，所以这里拿不到刀口，守卫只能盲守。
     *
     * 守了谁只有守卫自己知道——说出来的话，狼人下一夜就知道该换谁了。
     */
    suspend fun nightGuard(table: WerewolfTable): WerewolfTable {
        val game = table.game
        if (game.phase != Phase.NIGHT_GUARD) return table
        val guard = game.alivePlayers.firstOrNull { it.role == WerewolfRole.GUARD } ?: return table

        onProgress(WerewolfProgress(game.day, Phase.NIGHT_GUARD, 1, 1, seat = guard.seat))
        val outcome = askGuard(table, guard.seat)
        val target = outcome.value.target
        val next = game.guardProtect(target)
        return table
            .append(
                WerewolfMessage(
                    day = game.day,
                    phase = Phase.NIGHT_GUARD,
                    seat = guard.seat,
                    kind = MessageKind.NIGHT_ACT,
                    text = if (target == null) "这晚我不守人。" + outcome.value.reason
                    else "我守 $target 号。" + outcome.value.reason,
                    audience = setOf(guard.seat),
                )
            )
            .let { it.copy(game = next).syncBroadcasts(game) }
    }

    /**
     * 狼人商议。按座位顺序**串行**说，说完一句就推给界面。
     *
     * 两个决定：
     *
     * 1. **串行**。商议的意义就是「听到队友说什么再回应」。并发收集的话每只狼都是
     *    对着一片空白表态，各说各的等于没商量——这正是第一夜最容易空刀的地方。
     * 2. **单独成阶段**，不和下刀合并。合成一个请求的话，模型会一边说一边把目标定了，
     *    嘴上说「刀 3 号」、JSON 里填 5 号，而它自己也不觉得矛盾。
     */
    suspend fun nightWolfChat(table: WerewolfTable): WerewolfTable {
        val game = table.game
        if (game.phase != Phase.NIGHT_WOLF_CHAT) return table
        val spoken = talkRound(table, note = null)
        return spoken.copy(game = spoken.game.beginWolfVote())
    }

    /**
     * 狼人下刀。存活狼人**并发**给出目标，再交给状态机多数决。
     *
     * 并发而不是串行：商议已经公开说过一轮了，到定刀这一步再让后一只狼看见前一只的
     * 具体选择，它就只是在抄答案，狼队等于只有一只狼在思考。
     *
     * 平票时**回到商议**再谈一轮，而不是直接空刀：谈不拢说明话没说透，
     * 那就把分歧摆出来再说一次——这一步才是第一夜空刀的正解。
     */
    suspend fun nightWolf(table: WerewolfTable): WerewolfTable {
        var current = table
        var rounds = 0
        while (current.game.phase == Phase.NIGHT_WOLF && rounds < WerewolfGame.MAX_WOLF_VOTE_ROUNDS) {
            val game = current.game
            val wolves = game.aliveWolves()
            if (wolves.isEmpty()) break

            onProgress(
                WerewolfProgress(
                    day = game.day,
                    phase = Phase.NIGHT_WOLF,
                    step = 0,
                    total = wolves.size,
                    note = if (rounds > 0) "平票，重新定刀" else null,
                )
            )

            val outcomes = coroutineScope {
                wolves.map { wolf -> async { wolf.seat to askWolf(current, wolf.seat) } }.awaitAll()
            }

            // 并发阶段没有确定的「当前是谁」，所以到齐之后才报一次完成
            onProgress(WerewolfProgress(game.day, Phase.NIGHT_WOLF, wolves.size, wolves.size))

            var next = current
            outcomes.forEach { (seat, outcome) ->
                next = next.append(
                    WerewolfMessage(
                        day = game.day,
                        phase = Phase.NIGHT_WOLF,
                        seat = seat,
                        kind = MessageKind.WOLF_CHAT,
                        text = "我定 ${outcome.value.target} 号。" + outcome.value.reason,
                        audience = wolves.map { it.seat }.toSet(),
                    )
                )
            }

            val votes = outcomes.associate { (seat, outcome) -> seat to outcome.value.target }
            val afterVote = game.wolfVote(votes)
            current = next.copy(game = afterVote).syncBroadcasts(game)
            // 定刀结果也推一次：平票时界面要能立刻看到「谁指了谁」，
            // 否则下一轮商议的发言会显得凭空多出来一段
            onTranscript(current)

            if (current.game.phase == Phase.NIGHT_WOLF) {
                // 平票。把阶段真的退回商议，提示词里那句「现在定刀」才不会和
                // 「再商量一次」打架
                val reopened = current.copy(game = current.game.reopenWolfChat())
                val talked = talkRound(reopened, note = "平票，再商一次")
                current = talked.copy(game = talked.game.beginWolfVote())
            }
            rounds++
        }
        return current
    }

    /**
     * 一轮狼人商议：按座位顺序各说一句，说完立刻推给界面。
     *
     * 只追加消息、不碰 [WerewolfGame]——阶段推进由调用方决定。
     * 这样「第一次商议」和「平票后再商一次」能共用同一段逻辑。
     */
    private suspend fun talkRound(table: WerewolfTable, note: String?): WerewolfTable {
        val game = table.game
        val wolves = game.aliveWolves().sortedBy { it.seat }
        if (wolves.isEmpty()) return table

        val audience = wolves.map { it.seat }.toSet()
        var current = table
        wolves.forEachIndexed { index, wolf ->
            onProgress(
                WerewolfProgress(
                    day = game.day,
                    phase = Phase.NIGHT_WOLF_CHAT,
                    step = index + 1,
                    total = wolves.size,
                    seat = wolf.seat,
                    note = note,
                )
            )
            val text = askSpeech(
                current,
                wolf.seat,
                task = if (note == null) {
                    "现在只有你们狼人醒着。说一句你想刀谁、为什么。"
                } else {
                    "上一轮你们指了不同的人。说清楚这轮想刀谁，别再分票。"
                },
            )
            current = current.append(
                WerewolfMessage(
                    day = game.day,
                    phase = Phase.NIGHT_WOLF_CHAT,
                    seat = wolf.seat,
                    kind = MessageKind.WOLF_CHAT,
                    text = text,
                    audience = audience,
                )
            )
            // 说一句推一次：界面上一个一个冒出来，看得出来现在轮到谁在想
            onTranscript(current)
        }
        return current
    }

    /** 预言家查验。结果只进私有记录，播报里不会出现。 */
    suspend fun nightSeer(table: WerewolfTable): WerewolfTable {
        val game = table.game
        if (game.phase != Phase.NIGHT_SEER) return table
        val seer = game.alivePlayers.firstOrNull { it.role == WerewolfRole.SEER } ?: return table

        onProgress(WerewolfProgress(game.day, Phase.NIGHT_SEER, 1, 1, seat = seer.seat))
        val outcome = askSeer(table, seer.seat)
        val next = game.seerCheck(outcome.value.target)
        return table
            .append(
                WerewolfMessage(
                    day = game.day,
                    phase = Phase.NIGHT_SEER,
                    seat = seer.seat,
                    kind = MessageKind.NIGHT_ACT,
                    text = "查验 ${outcome.value.target} 号。" + outcome.value.reason,
                    audience = setOf(seer.seat),
                )
            )
            .let { it.copy(game = next).syncBroadcasts(game) }
    }

    /**
     * 女巫用药。行动结束后天已经亮了，[WerewolfTable.game] 回来时是白天阶段。
     *
     * 女巫说了什么、救没救、毒了谁，全都是秘密，所以这条消息只给女巫本人。
     */
    suspend fun nightWitch(table: WerewolfTable): WerewolfTable {
        val game = table.game
        if (game.phase != Phase.NIGHT_WITCH) return table
        val witch = game.alivePlayers.firstOrNull { it.role == WerewolfRole.WITCH } ?: return table

        onProgress(WerewolfProgress(game.day, Phase.NIGHT_WITCH, 1, 1, seat = witch.seat))
        val outcome = askWitch(table, witch.seat)
        val choice = outcome.value
        val next = game.witchAct(heal = choice.heal, poisonTarget = choice.poison)
        return table
            .append(
                WerewolfMessage(
                    day = game.day,
                    phase = Phase.NIGHT_WITCH,
                    seat = witch.seat,
                    kind = MessageKind.NIGHT_ACT,
                    text = describeWitchChoice(choice.heal, choice.poison) + choice.reason,
                    audience = setOf(witch.seat),
                )
            )
            .let { it.copy(game = next).syncBroadcasts(game) }
    }

    // ---------------------------------------------------------------------
    // 白天
    // ---------------------------------------------------------------------

    /**
     * 白天讨论，按座位顺序**串行**发言。
     *
     * 必须是串行：后一位要能接上前一位的话，否则一圈下来像各说各的。
     * 每说完一条就追加到 [WerewolfTable.transcript]，下一位拼上下文时自然就能看到。
     */
    suspend fun discussion(table: WerewolfTable): WerewolfTable {
        val game = table.game
        if (game.phase != Phase.DAY_DISCUSSION) return table

        var current = table
        val speakers = game.aliveSeats.sorted()
        speakers.forEachIndexed { index, seat ->
            onProgress(
                WerewolfProgress(
                    day = game.day,
                    phase = Phase.DAY_DISCUSSION,
                    step = index + 1,
                    total = speakers.size,
                    seat = seat,
                )
            )
            val text = askSpeech(current, seat)
            current = current.append(
                WerewolfMessage(
                    day = game.day,
                    phase = Phase.DAY_DISCUSSION,
                    seat = seat,
                    kind = MessageKind.SPEECH,
                    text = text,
                )
            )
            // 说完一个就推一次。下一位要看到的上下文里已经有这句（current 已带），
            // 界面拿到的也是同一份，不存在「显示的和模型看到的不是一回事」
            onTranscript(current)
        }
        return current
    }

    /**
     * 白天投票。所有有投票权的玩家**并发**投票，互相看不到。
     *
     * 用的是 [WerewolfGame.votingSeats] 而不是存活座位：翻过牌的白痴还在场上说话，
     * 但票已经和他无关了。让他投一票再被引擎拒掉，只会污染上下文。
     *
     * 平票就是当天结束：本板子没有警长，没人能主持 PK，[WerewolfConfig.maxVoteRounds]
     * 默认 1，这个 while 只走一圈。循环本身留着，是为了将来有警长的板子能直接复用。
     */
    suspend fun vote(table: WerewolfTable): WerewolfTable {
        var current = table
        var rounds = 0
        while (current.game.phase == Phase.DAY_VOTE && rounds < current.game.config.maxVoteRounds) {
            val game = current.game
            val voters = game.votingSeats
            val candidates = game.voteCandidates ?: voters

            onProgress(
                WerewolfProgress(
                    day = game.day,
                    phase = Phase.DAY_VOTE,
                    step = 0,
                    total = voters.size,
                    note = if (rounds > 0) "平票，重新投票" else null,
                )
            )

            val outcomes = coroutineScope {
                voters.map { seat ->
                    async { seat to askVote(current, seat, candidates) }
                }.awaitAll()
            }

            onProgress(
                WerewolfProgress(game.day, Phase.DAY_VOTE, voters.size, voters.size)
            )

            var next = current
            outcomes.forEach { (seat, outcome) ->
                val target = outcome.value.target
                next = next.append(
                    WerewolfMessage(
                        day = game.day,
                        phase = Phase.DAY_VOTE,
                        seat = seat,
                        kind = MessageKind.VOTE,
                        text = voteText(target, outcome.value.reason),
                    )
                )
            }

            val votes = outcomes.associate { (seat, outcome) -> seat to outcome.value.target }
            val afterVote = game.castVotes(votes)
            current = next.copy(game = afterVote).syncBroadcasts(game)
            rounds++
        }
        return current
    }

    /** 猎人开枪。此时猎人已经出局，开枪宣言所有人都能看到。 */
    suspend fun hunterShoot(table: WerewolfTable): WerewolfTable {
        val game = table.game
        if (game.phase != Phase.HUNTER_SHOOT) return table
        val hunterSeat = game.pendingHunterSeat ?: return table

        onProgress(WerewolfProgress(game.day, Phase.HUNTER_SHOOT, 1, 1, seat = hunterSeat))
        val outcome = askHunter(table, hunterSeat)
        val target = outcome.value.target
        val next = game.hunterShoot(target)
        return table
            .append(
                WerewolfMessage(
                    day = game.day,
                    phase = Phase.HUNTER_SHOOT,
                    seat = hunterSeat,
                    kind = MessageKind.SPEECH,
                    text = if (target == null) "我不开枪。" + outcome.value.reason
                    else "我开枪带走 $target 号。" + outcome.value.reason,
                )
            )
            .let { it.copy(game = next).syncBroadcasts(game) }
    }

    // ---------------------------------------------------------------------
    // 整局
    // ---------------------------------------------------------------------

    /**
     * 从头跑到分出胜负。
     *
     * [onUpdate] 每完成一个阶段回调一次，UI 拿它做流式展示。注意它拿到的是快照，
     * 不要在里面改状态。
     */
    suspend fun play(
        table: WerewolfTable,
        onUpdate: suspend (WerewolfTable) -> Unit = {},
    ): WerewolfTable {
        var current = table
        var steps = 0
        while (!current.game.isFinished) {
            check(steps++ < MAX_STEPS) { "编排没有收敛，停在 ${current.game.phase}" }
            current = when (current.game.phase) {
                Phase.NIGHT_GUARD -> nightGuard(current)
                Phase.NIGHT_WOLF_CHAT -> nightWolfChat(current)
                Phase.NIGHT_WOLF -> nightWolf(current)
                Phase.NIGHT_SEER -> nightSeer(current)
                Phase.NIGHT_WITCH -> nightWitch(current)
                Phase.DAY_DISCUSSION -> {
                    val discussed = discussion(current)
                    discussed.copy(game = discussed.game.beginVote())
                }
                Phase.DAY_VOTE -> vote(current)
                Phase.HUNTER_SHOOT -> hunterShoot(current)
                // 只有 winner 非空才会是 FINISHED，循环条件已经挡住
                Phase.FINISHED -> current
            }
            onUpdate(current)
        }
        return current
    }

    // ---------------------------------------------------------------------
    // 单个请求
    // ---------------------------------------------------------------------

    private suspend fun askWolf(table: WerewolfTable, seat: Int): StructuredOutcome<SeatChoice> {
        val game = table.game
        return ask(
            table = table,
            seat = seat,
            spec = WerewolfDecisions.wolfKill(game, seat),
            fallback = { WerewolfDecisions.fallbackWolfKill(game, seat, random) },
            task = "请给出你今晚想刀的目标。",
        )
    }

    private suspend fun askSeer(table: WerewolfTable, seat: Int): StructuredOutcome<SeatChoice> {
        val game = table.game
        return ask(
            table = table,
            seat = seat,
            spec = WerewolfDecisions.seerCheck(game, seat),
            fallback = { WerewolfDecisions.fallbackSeerCheck(game, seat, random) },
            task = "请给出你今晚要查验的人。",
        )
    }

    private suspend fun askGuard(table: WerewolfTable, seat: Int): StructuredOutcome<VoteChoice> {
        val game = table.game
        return ask(
            table = table,
            seat = seat,
            spec = WerewolfDecisions.guardProtect(game, seat),
            fallback = { WerewolfDecisions.fallbackGuard(game, random) },
            task = "请给出你今晚要守护的人，不想守就填 null。",
        )
    }

    private suspend fun askWitch(table: WerewolfTable, seat: Int): StructuredOutcome<WitchChoice> {
        val game = table.game
        return ask(
            table = table,
            seat = seat,
            spec = WerewolfDecisions.witch(game, seat),
            // 不用药永远合法，所以兜底就选它。宁可这晚白过，也不能让游戏卡死。
            fallback = { WitchChoice() },
            task = "请给出你今晚的用药决定。",
        )
    }

    private suspend fun askVote(
        table: WerewolfTable,
        seat: Int,
        candidates: List<Int>,
    ): StructuredOutcome<VoteChoice> {
        val game = table.game
        return ask(
            table = table,
            seat = seat,
            spec = WerewolfDecisions.vote(game, candidates),
            fallback = { WerewolfDecisions.fallbackVote(game, seat, candidates, random) },
            task = "请给出你的投票。",
        )
    }

    private suspend fun askHunter(table: WerewolfTable, seat: Int): StructuredOutcome<VoteChoice> {
        val game = table.game
        return ask(
            table = table,
            seat = seat,
            spec = WerewolfDecisions.hunterShoot(game),
            // 不开枪永远合法
            fallback = { VoteChoice(target = null) },
            task = "请决定是否开枪，以及带走谁。",
        )
    }

    /** 自由发言走普通 chat，不需要 JSON。狼人商议也走这条路。 */
    private suspend fun askSpeech(
        table: WerewolfTable,
        seat: Int,
        task: String = "现在轮到你发言。",
    ): String {
        val agent = agentOf(seat)
        val game = table.game
        val raw = agent.ai.chat(
            messages = listOf(ChatMessage.user(context(table, seat, task))),
            model = agent.model,
            systemPrompt = WerewolfPrompt.systemPrompt(game, seat, agent.persona),
            enableThinking = agent.thinkingEnabled,
        )
        return sanitize(raw, seat, game.playerAt(seat).name)
    }

    private suspend fun <T> ask(
        table: WerewolfTable,
        seat: Int,
        spec: StructuredSpec<T>,
        fallback: () -> T,
        task: String,
    ): StructuredOutcome<T> {
        val agent = agentOf(seat)
        return agent.ai.decide(
            messages = listOf(ChatMessage.user(context(table, seat, task))),
            model = agent.model,
            spec = spec,
            fallback = fallback,
            systemPrompt = WerewolfPrompt.systemPrompt(table.game, seat, agent.persona),
            enableThinking = agent.thinkingEnabled,
        )
    }

    // ---------------------------------------------------------------------
    // 辅助
    // ---------------------------------------------------------------------

    private fun agentOf(seat: Int): SeatAgent =
        agents[seat] ?: throw IllegalStateException("$seat 号没有配置模型，无法行动")

    /**
     * 拼本次请求的 user 消息：当天进程 + 交付给模型的任务。
     *
     * 只取当天的消息，因为跨天的信息已经写进 system 的公开播报了；
     * 再取最近若干条，避免夜聊越攒越长把当天的发言挤出去。
     */
    private fun context(table: WerewolfTable, seat: Int, task: String): String = buildString {
        val recent = table.transcriptFor(seat)
            .filter { it.day == table.game.day }
            .takeLast(MAX_CONTEXT_MESSAGES)
        if (recent.isNotEmpty()) {
            append("【本日进程】\n")
            recent.forEach { message ->
                append(if (message.seat == null) "上帝" else "${message.seat} 号")
                append("：")
                append(message.text)
                append("\n")
            }
            append("\n")
        }
        append(task)
    }

    /**
     * 把阶段推进产生的新播报补成消息。
     *
     * 用「history 的长度差」判断新增，而不是让每个阶段自己记得播报什么——
     * 播报文案只有 [WerewolfPrompt.describe] 一处，规则改了不用同步改第二个地方。
     *
     * 消息的 phase 取 **previous.phase**，也就是「产生这条播报的那个阶段」，不是推进后的阶段。
     * 这里差一步会出真问题：投票结束时状态机直接翻到下一夜，如果标成新阶段，
     * 一条「无人被放逐」的公开播报就会被记成「守卫阶段」的事情，谁都看得见。
     */
    private fun WerewolfTable.syncBroadcasts(previous: WerewolfGame): WerewolfTable {
        val fresh = game.history.drop(previous.history.size)
        if (fresh.isEmpty()) return this
        var next = this
        fresh.forEach { event ->
            next = next.append(
                WerewolfMessage(
                    day = event.day,
                    phase = previous.phase,
                    seat = null,
                    kind = MessageKind.DIVINE,
                    text = WerewolfPrompt.describe(event),
                )
            )
        }
        return next
    }

    /**
     * 剥掉模型爱加的前缀。
     *
     * 「3号：」「小明：」这类前缀在 UI 里会重复显示（座位和头像已经表明是谁），
     * 不剥掉读起来很出戏。
     */
    private fun sanitize(raw: String, seat: Int, name: String): String {
        var text = raw.trim().removeSurrounding("\"")
        listOf("$seat 号：", "${seat}号：", "「$name」：", "$name：").forEach { prefix ->
            if (text.startsWith(prefix)) text = text.removePrefix(prefix).trim()
        }
        return text.take(MAX_SPEECH_LENGTH)
    }

    private fun describeWitchChoice(heal: Boolean, poison: Int?): String = when {
        heal -> "我用解药。"
        poison != null -> "我用毒药毒 $poison 号。"
        else -> "这晚我先不用药。"
    }

    private fun voteText(target: Int?, reason: String): String {
        val head = if (target == null) "我弃票。" else "我投 $target 号。"
        return head + reason
    }

    private companion object {
        /** 单次请求带几条当天消息。够接上前文，又不会把上下文撑太长。 */
        const val MAX_CONTEXT_MESSAGES = 24

        /** 一句发言的长度上限。模型偶尔会写一整段小作文。 */
        const val MAX_SPEECH_LENGTH = 200

        /** 整局的调度步数上限，防止某处状态不推进导致死循环。 */
        const val MAX_STEPS = 500
    }
}
