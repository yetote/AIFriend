package com.feature.werewolf.engine

import com.core.ai.isTimeoutLike
import com.core.ai.model.ChatMessage
import com.core.ai.structured.StructuredOutcome
import com.core.ai.structured.StructuredSpec
import com.core.ai.structured.decide
import com.feature.werewolf.decision.SeatChoice
import com.feature.werewolf.decision.VoteChoice
import com.feature.werewolf.decision.WerewolfDecisions
import com.feature.werewolf.decision.WitchChoice
import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WerewolfSpeechLength
import com.feature.werewolf.prompt.WerewolfPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
    /**
     * 有位 AI 正在逐字开口时回调，说完了给 null 收尾。
     *
     * 两条路径会触发：
     *
     * - **发言**（[askSpeech]）：正文逐字长出来，牌桌才像有人真的在说话；
     * - **决策**（[ask]，投票/查验/用药这些）：只推**思维链**，正文是 JSON，中间态既不能解析也不能显示。
     *   只在开了深度思考的座位上推——没有思维链可推时多开一条流，只是多一处会出问题的地方。
     *
     * 并发阶段同时有好几个座位在想，这里靠 [thinkingOwner] 保证屏幕上只有一个思考框。
     *
     * 回调的是**半截内容**：调用方只能拿去显示，不能落档、不能进上下文、不能被朗读。
     */
    private val onLive: suspend (WerewolfLive?) -> Unit = {},
    /**
     * 玩家为这一局写的额外设定（全局 + 当前板子拼在一起）。
     *
     * 走构造参数而不是从哪读设置：导演层不该知道 DataStore 存在，
     * 谁在开局时读到设置、拼成一段话传进来，是外面的事。
     */
    private val customPrompt: String? = null,
    /**
     * 提示词风格。默认引导式，保持现有行为；自由式只给事实和底线，不给内置打法。
     *
     * 和 customPrompt 一样走构造参数：导演层不该知道设置存在，谁在开局时读到传进来是外面的事。
     */
    private val promptStyle: WerewolfPromptStyle = WerewolfPromptStyle.GUIDED,
    /**
     * 这一局发言的目标长度（见 [WerewolfSpeechLength]）。
     *
     * 管两处：模型说收不住时按这个数在句末裁；提示词里也告诉模型约多少字，
     * 让它自己收着写。和 promptStyle 同样走构造参数：导演层不该知道设置存在。
     */
    private val speechLength: WerewolfSpeechLength = WerewolfSpeechLength.STANDARD,
    /**
     * 两次 [onLive] 之间的最小间隔。
     *
     * 模型一秒能吐几十个片段，每片都刷一次界面纯属浪费——牌桌一次重排要重建整张记录流。
     * 80ms 是「看得出在打字」和「不要每帧都重组」之间的折中。测试里传 0 才能断言每次增量。
     */
    private val streamIntervalMs: Long = STREAM_PUSH_MS,
    /**
     * 这一局的节奏：时限档位、被关掉深度思考的座位、每座位的时间账本。
     *
     * 传一个**可变**对象而不是值：牌局中途也能改（用户打开对局设置随时调档），
     * 而导演协程是开局就起、跑到结束的，只有共享同一个实例才改得动。
     */
    private val pacing: WerewolfPacing = WerewolfPacing(),
    /**
     * 某一步超时了。
     *
     * 单独一个回调而不是只写进 [WerewolfPacing] 的账本：账本是汇总，
     * 界面还需要「刚刚这一刻谁超时了」才能提示一句——否则用户只会看到
     * 某个人突然投了个莫名其妙的票。
     */
    private val onTimedOut: suspend (TurnTimeout) -> Unit = {},
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
                    // 思维链跟着这条记录存：对局里靠开关决定给不给看，复盘里点开就能看
                    reasoning = outcome.reasoning,
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
        // 每晚随机指定一名头狼：没有指挥，四只狼就是四份互不相干的上下文，
        // 夜里各说各的、白天各跳各的。随机而不是固定给某个座位（第一个发言的、
        // 或者模型最强的），是为了让每局都有变化，也避免永远有人背这个角色。
        //
        // 续档时**沿用存档里的头狼**：同一条夜里换人当头狼，而 transcript 里已经
        // 躺着旧头狼的发言，场上会凭空多出一个「假头狼」。存档里的头狼已经出局
        // （理论上不会，但存档可能被改过）才重选。
        val aliveWolves = game.aliveWolves()
        val head = game.nightHead?.takeIf { seat -> aliveWolves.any { it.seat == seat } }
            ?: aliveWolves.randomOrNull(random)?.seat
        val prepared = if (head == null) table else table.copy(game = game.copy(nightHead = head))
        val spoken = talkRound(prepared, note = null)
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

            val outcomes = chunkedAsync(
                wolves.map { it.seat },
                onTick = { done, total, pending ->
                    onProgress(
                        WerewolfProgress(
                            day = game.day,
                            phase = Phase.NIGHT_WOLF,
                            step = done,
                            total = total,
                            seats = pending,
                            note = if (rounds > 0) "平票，重新定刀" else null,
                        )
                    )
                },
            ) { seat -> seat to askWolf(current, seat) }

            // 并发阶段没有确定的「当前是谁」，所以到齐之后才报一次完成
            onProgress(WerewolfProgress(game.day, Phase.NIGHT_WOLF, wolves.size, wolves.size))

            // 定刀结果落账。**一致时只落一条**：四只狼吃的是同一份上下文、同一个模型，
            // 结论相同的时候连理由都几乎一样，逐条落账再读出来就是同一句话重复四遍
            // （实测「我定 2 号。存在感低、信息量小」连说四遍，观感像复读机）。
            // 只有真分票时才逐条保留——那时「谁指了谁」才是复盘需要的，
            // 而完整票型本来就记在 game.wolfVotes 里。
            val targets = outcomes.map { it.second.value.target }.distinct()
            var next = current
            if (targets.size == 1) {
                val (firstSeat, first) = outcomes.first()
                next = next.append(
                    WerewolfMessage(
                        day = game.day,
                        phase = Phase.NIGHT_WOLF,
                        seat = firstSeat,
                        kind = MessageKind.WOLF_CHAT,
                        text = killLine("我们", first.value.target) + first.value.reason,
                        audience = wolves.map { it.seat }.toSet(),
                    )
                )
            } else {
                outcomes.forEach { (seat, outcome) ->
                    next = next.append(
                        WerewolfMessage(
                            day = game.day,
                            phase = Phase.NIGHT_WOLF,
                            seat = seat,
                            kind = MessageKind.WOLF_CHAT,
                            text = killLine("我", outcome.value.target) + outcome.value.reason,
                            audience = wolves.map { it.seat }.toSet(),
                        )
                    )
                }
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

        // 发言顺序：**头狼先**，其余按座位。头狼是这一夜的指挥，
        // 他先说战术，后面的人才有「执行还是反对」的对象——顺序反了，
        // 先发言的人只能说自己的计划，后发言的人听到一堆互不相干的方案。
        val head = game.nightHead
        val ordered = if (head == null) {
            wolves
        } else {
            wolves.sortedBy { if (it.seat == head) 0 else 1 }
        }

        val audience = wolves.map { it.seat }.toSet()
        var current = table
        // 续档跳过本阶段已发言的狼。轮次变了 key 就变（见 currentPhaseKey），
        // 平票重谈会重新说一轮，不会误用上一轮的名单。
        ordered.filterNot { current.game.hasSpoken(it.seat) }.forEachIndexed { index, wolf ->
            onProgress(
                WerewolfProgress(
                    day = game.day,
                    phase = Phase.NIGHT_WOLF_CHAT,
                    step = ordered.indexOf(wolf) + 1,
                    total = wolves.size,
                    seat = wolf.seat,
                    note = note,
                )
            )
            val isHead = wolf.seat == head
            val text = askSpeech(
                current,
                wolf.seat,
                task = when {
                    isHead -> "你是今晚的头狼（狼队指挥），你先说。安排今晚的战术：" +
                        "刀谁、为什么；白天谁跳预言家、谁负责冲锋、谁负责划水。" +
                        "说成一条能执行的方案，别写成各说各的。"
                    note == null -> "头狼已经安排好了战术（见上文）。" +
                        "你要么照着执行并补一句理由，要么明确反对并给出你的刀口——" +
                        "别说「同意」「都行」这种空话。"
                    else -> "上一轮你们指了不同的人。说清楚这轮想刀谁，别再分票。"
                },
            )
            current = current.append(
                WerewolfMessage(
                    day = game.day,
                    phase = Phase.NIGHT_WOLF_CHAT,
                    seat = wolf.seat,
                    kind = MessageKind.WOLF_CHAT,
                    text = text.text,
                    reasoning = text.reasoning,
                    audience = audience,
                )
            )
            // 头狼的战术原文单独存一份。白天的上下文只喂公开消息，
            // 夜聊在白天是看不见的——不存这一份，昨晚的安排等于没说过，
            // 白天每只狼还是各跳各的。
            if (isHead) {
                current = current.copy(game = current.game.copy(wolfPlan = text.text))
            }
            current = current.copy(game = current.game.markSpoken(wolf.seat))
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
                    reasoning = outcome.reasoning,
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
                    reasoning = outcome.reasoning,
                    audience = setOf(witch.seat),
                )
            )
            .let { it.copy(game = next).syncBroadcasts(game) }
    }

    // ---------------------------------------------------------------------
    // 白天
    // ---------------------------------------------------------------------

    /**
     * 白天讨论，按 [WerewolfGame.discussionOrder] **串行**发言。
     *
     * 必须是串行：后一位要能接上前一位的话，否则一圈下来像各说各的。
     * 每说完一条就追加到 [WerewolfTable.transcript]，下一位拼上下文时自然就能看到。
     *
     * 顺序不是「活人按座位升序」：有警长从警长下位起（警长归票位最后说），
     * 没警长从昨夜死者下位起。规则写在引擎里，这里只照着念。
     *
     * 每说完一句都要重新看一眼阶段：狼人可能在这一句里自爆，那样这一天就结束了，
     * 后面的人不该再被叫起来说话。
     */
    suspend fun discussion(table: WerewolfTable): WerewolfTable {
        var current = table
        if (current.game.phase != Phase.DAY_DISCUSSION) return current

        val speakers = current.game.discussionOrder
        // 续档：本阶段已经说过的不再问一遍。见 WerewolfGame.spokenPhaseKey。
        val pending = speakers.filterNot { current.game.hasSpoken(it) }
        pending.forEachIndexed { index, seat ->
            if (current.game.phase != Phase.DAY_DISCUSSION) return current
            onProgress(
                WerewolfProgress(
                    day = current.game.day,
                    phase = Phase.DAY_DISCUSSION,
                    step = speakers.indexOf(seat) + 1,
                    total = speakers.size,
                    seat = seat,
                )
            )
            val text = askSpeech(current, seat)
            current = current.speakAndMaybeExplode(
                seat,
                Phase.DAY_DISCUSSION,
                text.text,
                reasoning = text.reasoning,
            ).let { it.copy(game = it.game.markSpoken(seat)) }
            // 说完一个就推一次。下一位要看到的上下文里已经有这句（current 已带），
            // 界面拿到的也是同一份，不存在「显示的和模型看到的不是一回事」
            onTranscript(current)
        }
        return current
    }

    // ---------------------------------------------------------------------
    // 警长竞选
    // ---------------------------------------------------------------------

    /**
     * 上警报名。全员**并发**表态，互相看不到谁报了名。
     *
     * 并发是为了不让后面的座位跟风：串行的话，第 5 个人听到前 4 个都上警了，
     * 就只会做「我也上」或「那我算了」的从众判断，而这恰恰是唯一不该从众的一步。
     * 报名结果随后一次性公开——线下本来也是举手，谁都能看见。
     *
     * 这一步**不产生任何发言**。举手就是举手，信息留到竞选发言：[signUpSheriff] 只往
     * history 里记一条名单，播报说「上警的是 3、5、7 号」就完了。此前这里给每个座位
     * append 了一条「我上警。」+ 模型给的理由，12 个人各说一句，第一阶段比竞选环节还长，
     * 真正该曝信息的警上发言反倒被淹没了。
     */
    suspend fun sheriffSignup(table: WerewolfTable): WerewolfTable {
        val game = table.game
        if (game.phase != Phase.DAY_SHERIFF_SIGNUP) return table

        val outcomes = chunkedAsync(
            game.aliveSeats,
            onTick = { done, total, pending ->
                onProgress(
                    WerewolfProgress(
                        day = game.day,
                        phase = Phase.DAY_SHERIFF_SIGNUP,
                        step = done,
                        total = total,
                        seats = pending,
                    )
                )
            },
        ) { seat -> seat to askSheriffSignup(table, seat) }
        onProgress(WerewolfProgress(game.day, Phase.DAY_SHERIFF_SIGNUP, game.aliveSeats.size, game.aliveSeats.size))

        val runners = outcomes.filter { (seat, outcome) -> outcome.value.target == seat }
            .map { it.first }
            .toSet()
        val after = game.signUpSheriff(runners)
        return table.copy(game = after).syncBroadcasts(game).also { onTranscript(it) }
    }

    /**
     * 竞选发言。候选人按座位顺序各说一段，平票 PK 时也是这里，只是人少了。
     *
     * 候选人在这一句里也可以自爆（把竞选搅乱是狼队的正当打法），
     * 所以每说完一个都要检查阶段。
     */
    suspend fun sheriffSpeech(table: WerewolfTable): WerewolfTable {
        var current = table
        if (current.game.phase != Phase.DAY_SHERIFF_SPEECH) return current
        val speakers = current.game.sheriffCandidates.sorted()
        val pk = current.game.sheriffRound > 1
        // 续档跳过本阶段已发过言的座位（PK 会经过投票阶段再回来，key 变了会重新说）
        val pending = speakers.filterNot { current.game.hasSpoken(it) }

        pending.forEachIndexed { index, seat ->
            if (current.game.phase != Phase.DAY_SHERIFF_SPEECH) return current
            onProgress(
                WerewolfProgress(
                    day = current.game.day,
                    phase = Phase.DAY_SHERIFF_SPEECH,
                    step = speakers.indexOf(seat) + 1,
                    total = speakers.size,
                    seat = seat,
                    note = if (pk) "平票 PK" else null,
                )
            )
            val text = askSpeech(
                current,
                seat,
                task = if (pk) {
                    "你和另一位候选人平票了，现在是 PK 发言。" +
                        "说清楚为什么警下应该把票投给你，而不是另一位。"
                } else {
                    "现在轮到你竞选发言。" +
                        "用 2-3 句说清你的判断和当选后的思路，让警下愿意把票投给你。"
                },
            )
            current = current.speakAndMaybeExplode(
                seat,
                Phase.DAY_SHERIFF_SPEECH,
                text.text,
                reasoning = text.reasoning,
            ).let { it.copy(game = it.game.markSpoken(seat)) }
            onTranscript(current)
        }

        // 中途自爆的话阶段已经变了，这里不能再推进投票
        return if (current.game.phase == Phase.DAY_SHERIFF_SPEECH) {
            current.copy(game = current.game.beginSheriffVote())
        } else {
            current
        }
    }

    /**
     * 警下投票选警长。**只有没上警的人投**，并发收集，互相看不到。
     *
     * 平票时 [WerewolfGame.castSheriffVotes] 把阶段退回竞选发言做 PK，
     * 这个循环因此只走一圈；留着 while 是为了 PK 之后再平票时能自然收尾。
     */
    suspend fun sheriffVote(table: WerewolfTable): WerewolfTable {
        var current = table
        var rounds = 0
        while (current.game.phase == Phase.DAY_SHERIFF_VOTE && rounds < WerewolfGame.SHERIFF_MAX_ROUNDS) {
            val game = current.game
            val voters = game.sheriffVoters
            val candidates = game.sheriffCandidates

            if (voters.isEmpty()) {
                // 全员上警：没人有资格投票。引擎会直接判「没有警长」
                val after = game.castSheriffVotes(emptyMap())
                current = current.copy(game = after).syncBroadcasts(game)
                break
            }

            onProgress(
                WerewolfProgress(
                    day = game.day,
                    phase = Phase.DAY_SHERIFF_VOTE,
                    step = 0,
                    total = voters.size,
                    note = if (game.sheriffRound > 1) "PK 后重投" else null,
                )
            )
            val outcomes = chunkedAsync(
                voters,
                onTick = { done, total, pending ->
                    onProgress(
                        WerewolfProgress(
                            day = game.day,
                            phase = Phase.DAY_SHERIFF_VOTE,
                            step = done,
                            total = total,
                            seats = pending,
                            note = if (rounds > 0) "PK 后重投" else null,
                        )
                    )
                },
            ) { seat -> seat to askSheriffVote(current, seat, candidates) }
            onProgress(WerewolfProgress(game.day, Phase.DAY_SHERIFF_VOTE, voters.size, voters.size))

            var next = current
            outcomes.forEach { (seat, outcome) ->
                val target = outcome.value.target
                next = next.append(
                    WerewolfMessage(
                        day = game.day,
                        phase = Phase.DAY_SHERIFF_VOTE,
                        seat = seat,
                        kind = MessageKind.VOTE,
                        text = (if (target == null) "我弃票。" else "我选 $target 号当警长。") + outcome.value.reason,
                        // 警长投票是私下的：理由只有投票人自己看得到，不进别人的上下文，
                        // 别人在牌桌上也看不到。这和 INNER_REASON_HINT 里告诉模型的
                        // 「没人听见」终于对上了（放逐投票是另一回事，它明说了会公开）。
                        // 观战是上帝视角，emergedVisible 对 humanSeat==null 不过滤，照样看得见。
                        audience = setOf(seat),
                        reasoning = outcome.reasoning,
                    )
                )
            }
            val votes = outcomes.associate { (seat, outcome) -> seat to outcome.value.target }
            val after = game.castSheriffVotes(votes)
            current = next.copy(game = after).syncBroadcasts(game)
            onTranscript(current)
            rounds++
        }
        return current
    }

    // ---------------------------------------------------------------------
    // 遗言
    // ---------------------------------------------------------------------

    /**
     * 遗言。按 [WerewolfGame.pendingLastWords] 的顺序一个个说。
     *
     * 死者说话不影响任何规则状态，但有两件事必须走这条路：
     * 1. 遗言是**公开信息**，后面投票的人要听得到；
     * 2. 警长的遗言里要交警徽——那是唯一能拿到他本人意愿的时机。
     */
    suspend fun lastWords(table: WerewolfTable): WerewolfTable {
        var current = table
        if (current.game.phase != Phase.DAY_LAST_WORDS) return current

        val pending = current.game.pendingLastWords
        pending.filterNot { current.game.hasSpoken(it) }.forEachIndexed { index, seat ->
            if (current.game.phase != Phase.DAY_LAST_WORDS) return current
            onProgress(
                WerewolfProgress(
                    day = current.game.day,
                    phase = Phase.DAY_LAST_WORDS,
                    step = index + 1,
                    total = pending.size,
                    seat = seat,
                )
            )
            if (current.game.canTransferBadge(seat)) {
                val outcome = askBadge(current, seat)
                val target = outcome.value.target
                val head = if (target == null) "我把警徽撕了。" else "我把警徽交给 $target 号。"
                // 被猎人一枪带走的警长没有遗言：他只有「处理警徽」这一个动作，
                // 多出来的 reason 不能当成他的话念出去，否则牌桌上会冒出一具尸体的长篇大论。
                val silent = current.game.playerAt(seat).deathCause == DeathCause.HUNTER_SHOOT
                current = current.append(
                    WerewolfMessage(
                        day = current.game.day,
                        phase = Phase.DAY_LAST_WORDS,
                        seat = seat,
                        kind = MessageKind.SPEECH,
                        text = if (silent) head else head + outcome.value.reason,
                    )
                )
                val before = current.game
                current = current.copy(game = before.transferBadge(seat, target)).syncBroadcasts(before)
            } else {
                val text = askSpeech(
                    current,
                    seat,
                    task = "你已经出局了，现在说遗言。用 1-3 句交代你的判断，提醒活着的人别投错。",
                )
                current = current.append(
                    WerewolfMessage(
                        day = current.game.day,
                        phase = Phase.DAY_LAST_WORDS,
                        seat = seat,
                        kind = MessageKind.SPEECH,
                        text = text.text,
                        reasoning = text.reasoning,
                    )
                )
            }
            current = current.copy(game = current.game.markSpoken(seat))
            onTranscript(current)
        }
        return current.copy(game = current.game.finishLastWords())
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

            // 分批并发，每回一票就报一次进度，并带上「这一批还没回话的人」。
            // 十二个人同时问模型，服务端要排队、本机带宽也要分，
            // 深度思考时更会互相拖到超时——分批之后总时长反而更短。
            val outcomes = chunkedAsync(
                voters,
                onTick = { done, total, pending ->
                    onProgress(
                        WerewolfProgress(
                            day = game.day,
                            phase = Phase.DAY_VOTE,
                            step = done,
                            total = total,
                            seats = pending,
                            note = if (rounds > 0) "平票，重新投票" else null,
                        )
                    )
                },
            ) { seat -> seat to askVote(current, seat, candidates) }

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
                        reasoning = outcome.reasoning,
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

    /** 开枪。此时猎人或狼王已经出局，开枪宣言所有人都能看到。 */
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
                Phase.DAY_SHERIFF_SIGNUP -> sheriffSignup(current)
                Phase.DAY_SHERIFF_SPEECH -> sheriffSpeech(current)
                Phase.DAY_SHERIFF_VOTE -> sheriffVote(current)
                Phase.DAY_DISCUSSION -> {
                    val discussed = discussion(current)
                    // 讨论里可能有人自爆，那样这一天已经结束了，不能再开投票
                    if (discussed.game.phase == Phase.DAY_DISCUSSION) {
                        discussed.copy(game = discussed.game.beginVote())
                    } else {
                        discussed
                    }
                }
                Phase.DAY_VOTE -> vote(current)
                Phase.DAY_LAST_WORDS -> lastWords(current)
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

    private suspend fun askWolf(table: WerewolfTable, seat: Int): StructuredOutcome<VoteChoice> {
        val game = table.game
        return ask(
            table = table,
            seat = seat,
            spec = WerewolfDecisions.wolfKill(game, seat),
            fallback = { WerewolfDecisions.fallbackWolfKill(game, seat, random) },
            task = "请给出你今晚想刀的目标；也可以刀队友或自己，或者填 null 空刀。",
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
        // 候选里去掉自己。实测真预言家被全场踩的时候会顺着气氛把票投给自己——
        // 记录里明明白白写着「9 号投 9 号」，一票自投等于白送好人一个轮次。
        // 线下没有这种打法，干脆从候选里拿掉，模型想投也投不了。
        // 万一过滤后为空（理论上不可能，场上至少还有另一方），退回原列表保住流程。
        val withoutSelf = candidates.filter { it != seat }
        val valid = withoutSelf.ifEmpty { candidates }
        return ask(
            table = table,
            seat = seat,
            spec = WerewolfDecisions.vote(game, valid),
            fallback = { WerewolfDecisions.fallbackVote(game, seat, valid, random) },
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

    private suspend fun askSheriffSignup(table: WerewolfTable, seat: Int): StructuredOutcome<VoteChoice> {
        val game = table.game
        return ask(
            table = table,
            seat = seat,
            spec = WerewolfDecisions.sheriffSignup(game, seat),
            // 不上警永远合法，也比「兜底上一个警」安全：警上的人没有警长投票权
            fallback = { VoteChoice(target = null, reason = "我先看看局势。") },
            task = "请决定要不要上警。",
        )
    }

    private suspend fun askSheriffVote(
        table: WerewolfTable,
        seat: Int,
        candidates: List<Int>,
    ): StructuredOutcome<VoteChoice> {
        val game = table.game
        return ask(
            table = table,
            seat = seat,
            spec = WerewolfDecisions.sheriffVote(game, candidates),
            fallback = { WerewolfDecisions.fallbackVote(game, seat, candidates, random) },
            task = "请投出你的警长票。",
        )
    }

    private suspend fun askBadge(table: WerewolfTable, seat: Int): StructuredOutcome<VoteChoice> {
        val game = table.game
        return ask(
            table = table,
            seat = seat,
            spec = WerewolfDecisions.badgeTransfer(game, seat),
            // 兜底撕掉警徽，而不是随便找个人接手：交错的代价比没有警长大得多
            fallback = { VoteChoice(target = null, reason = "我来不及交代了。") },
            task = "请在遗言里交代警徽交给谁。",
        )
    }

    /**
     * 自由发言走**流式** chat，不需要 JSON。狼人商议也走这条路。
     *
     * 为什么只有这里流式：[decide] 拿回来的是一段 JSON，它的价值全在解析完的那个对象上，
     * 中间态既不能显示也不能用；而发言就是最终产物本身，边生成边显示对用户才是「有人在说话」。
     *
     * [onLive] 推的是**累计**后的当前文本，不是增量：界面只做整体替换，
     * 不需要自己维护拼接状态——一旦中途重连或重试，增量拼接会出现重复的半句。
     *
     * 无论正常说完、报错还是被取消，`finally` 都会把「正在说话」收回。
     * 用 [NonCancellable] 是因为取消时调用挂起函数会立刻再抛一次，
     * 那样牌桌上会永久留着半句话和闪烁的光标。
     */
    private suspend fun askSpeech(
        table: WerewolfTable,
        seat: Int,
        task: String = "现在轮到你发言。",
    ): SpeechResult {
        val agent = agentOf(seat)
        val game = table.game
        val messages = listOf(ChatMessage.user(context(table, seat, task)))
        val system = WerewolfPrompt.systemPrompt(
            game, seat, agent.persona, customPrompt,
            limit = pacing.currentLimit, style = promptStyle, speechLength = speechLength,
        )
        val thinking = pacing.thinkingAllowed(seat, agent.thinkingEnabled)
        val content = StringBuilder()
        val reasoning = StringBuilder()
        var lastPush = 0L
        var pushed = false
        // 底层超时降级（关思考重试）是正常返回的，外层看不到异常。
        // 靠 delta 带出来的标志记一笔，好在下面对这个座位关思考。
        var degradedByTimeout = false

        suspend fun consume() {
            agent.ai.chatStream(
                messages = messages,
                model = agent.model,
                systemPrompt = system,
                enableThinking = thinking,
            ).collect { delta ->
                if (delta.timeoutDegraded) degradedByTimeout = true
                if (delta.content.isEmpty() && delta.reasoning.isEmpty()) return@collect
                content.append(delta.content)
                reasoning.append(delta.reasoning)
                val now = System.currentTimeMillis()
                if (!pushed || now - lastPush >= streamIntervalMs) {
                    lastPush = now
                    pushed = true
                    onLive(
                        WerewolfLive(
                            seat = seat,
                            phase = game.phase,
                            text = content.toString(),
                            reasoning = reasoning.toString(),
                            isDecision = false,
                        )
                    )
                }
            }
        }

        // 时限分两段，口径和界面一致：**气泡里是思考、发言列表里是发言**。
        // - 思考段：正文一个字都还没吐出来，受思考时限（决策档）管——
        //   想太久就掐，多等一分钟换来的往往不是更好的话，是更犹豫的话。
        // - 发言段：正文开始往外流。AI「说」就是吐 token，和思考同一条链路，
        //   掐它只会把话说一半，所以档位默认不限（0）；流挂住有传输层兜底。
        //   字段保留是为了万一要给喋喋不休的模型自定义上限。
        // 超时不抛异常——说话说到一半被打断，把已经说出去的那半句留下就行，牌桌能继续。
        val thinkingMs = pacing.limitMs(TurnKind.DECISION)
        val speechMs = pacing.limitMs(TurnKind.SPEECH)
        val bounded = (thinkingMs > 0 || speechMs > 0) && agent.ai !is HumanSeatAI
        val started = System.currentTimeMillis()
        var timedOut = false
        // 真正掐下去的那一段的上限：思考段超时和发言段超时，提示里报的秒数不一样
        var timedOutLimitMs = 0L
        // 正文第一次吐字的时刻。> 0 表示已经进入发言段。
        var speechStartedAt = 0L

        try {
            when {
                !bounded -> consume()
                // withTimeout 表达不了「跑着跑着换一个 deadline」（吐字的时刻因模型而异），
                // 用看门狗手动掐：正文吐出的那一刻切换时钟；某一段为 0 就不限那一段。
                else -> coroutineScope {
                    val worker = launch { consume() }
                    while (worker.isActive) {
                        if (speechStartedAt == 0L && content.isNotEmpty()) {
                            speechStartedAt = System.currentTimeMillis()
                        }
                        val segmentMs = if (speechStartedAt > 0L) speechMs else thinkingMs
                        if (segmentMs <= 0L) {
                            // 本段不限。两段都不限不会进到这里（外层已拦），
                            // 所以另一段必然有限：轮询等收工或等段切换，切换后换 deadline。
                            if (withTimeoutOrNull(SEGMENT_POLL_MS) { worker.join() } != null) break
                            continue
                        }
                        val wait = (if (speechStartedAt > 0L) speechStartedAt else started) +
                            segmentMs - System.currentTimeMillis()
                        if (wait <= 0) {
                            timedOut = true
                            timedOutLimitMs = segmentMs
                            // 我们掐的：取消 worker 是正常收尾，不会把异常抛给外层
                            worker.cancel()
                            break
                        }
                        // 等 worker 收工，最多等到本段的 deadline：谁先到算谁的。
                        // 到点没等到（返回 null）就回循环再看一眼——可能刚好吐字，时钟要换段。
                        if (withTimeoutOrNull(wait) { worker.join() } != null) break
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            // 和 [ask] 同一条规矩：外层还活着说明是这次时限到了；
            // 外层已经取消（退出、暂停、换局）就照常抛
            if (!currentCoroutineContext().isActive) throw cancelled
            if (!bounded) throw cancelled
            timedOut = true
            timedOutLimitMs = if (speechStartedAt > 0L) speechMs else thinkingMs
        } catch (error: Throwable) {
            // **超时**且已经说出半句的，保留这半句继续这一轮。
            // 抛出去等于整局停在「模型响应超时」上，续档还会重放同一阶段、撞同一个超时，
            // 用户看到的是「这局再也打不下去了」。半句话不好听，但牌桌能往前走。
            //
            // 只对超时这么办：连接被重置、鉴权失败这类是「链路真的坏了」，
            // 必须让编排层把错误显示出来，别伪装成一局正常但奇怪的发言。
            if (content.isEmpty() || !error.isTimeoutLike()) throw error
            // 流式吐了一半才超时：底层抛上来的超时同样要关思考，
            // 只是这半句还得留着（下面的 raw 分支用 content），不能当整体超时丢掉。
            degradedByTimeout = true
        } finally {
            withContext(NonCancellable) { onLive(null) }
        }

        val elapsed = System.currentTimeMillis() - started
        if (timedOut || degradedByTimeout) {
            pacing.disableThinking(seat)
            pacing.record(seat, elapsed, TurnKind.SPEECH, timedOut = true)
            onTimedOut(TurnTimeout(seat, TurnKind.SPEECH, timedOutLimitMs, elapsed))
        } else {
            pacing.record(seat, elapsed, TurnKind.SPEECH, timedOut = false)
        }

        // 流式一个片段都没收到（厂商没实现 SSE，或者返回了空流）时补一次普通请求。
        // 不补的话这句话会变成空白，后面所有人都会看到「3 号：」后面什么都没有。
        //
        // 但**超时的不补**：再打一次只会把等待翻倍，而且多半还会超时。
        // 这时给一句能接得上的话，比留个空白或者让整局报错都好。
        val raw = when {
            content.isNotEmpty() -> content.toString()
            timedOut -> SILENT_SPEECH
            else -> agent.ai.chat(
                messages = messages,
                model = agent.model,
                systemPrompt = system,
                enableThinking = thinking,
            )
        }
        return SpeechResult(
            text = sanitize(raw, seat, game.playerAt(seat).name),
            // 思考过程只留给复盘：对局里不显示，但存进 transcript，
            // 局结束后「他当时是怎么想的」是复盘最有意思的部分之一
            reasoning = reasoning.toString().trim(),
        )
    }

    /** 一句流式发言的成品。思考过程跟正文一起回来，各走各的去处。 */
    private data class SpeechResult(val text: String, val reasoning: String)

    /**
     * 一次结构化决策（投票、查验、用药、狼刀……）。
     *
     * **开了深度思考的座位会边想边推给界面**：这类请求拿回来的是一段 JSON，其中间态
     * 既不能显示也不能用，但模型在写 JSON 之前的那段推理，恰恰是「他为什么这么打」。
     * 关着深度思考时仍然走一次性请求——没有思维链可显示，多开一条流只是多一处风险。
     *
     * 和 [askSpeech] 一样，不管成功、失败还是被取消，`finally` 都会把「正在思考」收回；
     * 取消时用 [NonCancellable]，否则牌桌上会永久留着一个飘在半空的思考框。
     */
    private suspend fun <T> ask(
        table: WerewolfTable,
        seat: Int,
        spec: StructuredSpec<T>,
        fallback: () -> T,
        task: String,
    ): StructuredOutcome<T> {
        val agent = agentOf(seat)
        val phase = table.game.phase
        // 深度思考是「角色属性 + 本局运行状态」相与：超时被关掉的座位，
        // 这一局剩下的步骤都按关着走，直到玩家在设置面板里手动打开
        val thinking = pacing.thinkingAllowed(seat, agent.thinkingEnabled)
        var lastPush = 0L
        // 抢到思考框才推：并发阶段好几个人同时在响，见 [thinkingOwner]
        var holdsBubble = false
        // 节流和发言同源：模型一秒能吐几十段，每段都刷一次界面只会让气泡抖
        val pushReasoning: (suspend (String) -> Unit)? = if (thinking) {
            { reasoning ->
                if (!holdsBubble) holdsBubble = claimThinking(seat)
                if (holdsBubble) {
                    val now = System.currentTimeMillis()
                    if (lastPush == 0L || now - lastPush >= streamIntervalMs) {
                        lastPush = now
                        onLive(WerewolfLive(seat = seat, phase = phase, reasoning = reasoning, isDecision = true))
                    }
                }
            }
        } else {
            null
        }

        // 时限是**这一局**的节奏控制，和传输层那 8 分钟不是一回事：
        // 它管的是「想太久等于白想」，超时之后按兜底动作往下走，牌局不中断。
        // 人类座位不受限——他要想多久是他自己的事。
        val limitMs = pacing.limitMs(TurnKind.DECISION)
        val bounded = limitMs > 0 && agents[seat]?.ai !is HumanSeatAI
        val started = System.currentTimeMillis()
        val outcome = try {
            val call: suspend () -> StructuredOutcome<T> = {
                agent.ai.decide(
                    messages = listOf(ChatMessage.user(context(table, seat, task))),
                    model = agent.model,
                    spec = spec,
                    fallback = fallback,
                    systemPrompt = WerewolfPrompt.systemPrompt(
                        table.game, seat, agent.persona, customPrompt,
                        limit = pacing.currentLimit, style = promptStyle, speechLength = speechLength,
                    ),
                    enableThinking = thinking,
                    onReasoning = pushReasoning,
                )
            }
            // 用 withTimeout 而不用 withTimeoutOrNull：`decide` 内部把超时当成
            // 「这个座位这次没答上来」并返回兜底值，那样我们就分不清
            // 「它自己想放弃了」和「我们把它掐了」——而后者必须关掉这个座位的深度思考。
            if (bounded) withTimeout(limitMs) { call() } else call()
        } catch (cancelled: CancellationException) {
            // 这里会收到两种取消，必须分清：
            // - 内层 withTimeout 到点了 → 外层 context 还是活的 → 这是我们自己的超时；
            // - 用户退出 / 暂停 / 换局 → 外层已经 inactive，照常往上抛，别伪装成超时。
            // 只捕获 TimeoutCancellationException 不够：`decide` 内部重试时会重新抛，
            // 抛出来的可能是普通 CancellationException。
            if (!currentCoroutineContext().isActive || !bounded) throw cancelled
            null
        } finally {
            if (holdsBubble) {
                // 先收回再让位：反过来的话，下一位刚推上来的一段会被这里清掉
                withContext(NonCancellable) { onLive(null) }
                releaseThinking(seat)
            }
        }

        val elapsed = System.currentTimeMillis() - started
        // 两种超时都要关思考：
        // - outcome == null：外层时限掐断了请求；
        // - outcome.timedOut：decide 内部超时后降级重试/兜底，正常返回了结果。
        // 后者以前被当成「没事」，下一轮又把思考打开，模型一次次撞同一个超时。
        // 超时的头号原因就是思考把额度吃光了，关掉之后通常几十秒就能回话。
        if (outcome == null || outcome.timedOut) {
            pacing.disableThinking(seat)
            pacing.record(seat, elapsed, TurnKind.DECISION, timedOut = true)
            onTimedOut(TurnTimeout(seat, TurnKind.DECISION, limitMs, elapsed))
        } else {
            pacing.record(seat, elapsed, TurnKind.DECISION, timedOut = false)
        }
        // 内部降级成功时 outcome 已经有合法结果（或已经兜底），直接用它；
        // 只有外层掐断才需要我们在这里补一个兜底。
        return outcome ?: StructuredOutcome(
            value = fallback(),
            attempts = 0,
            usedFallback = true,
            lastError = "超出思考时限",
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
     * 只取当天的消息，因为跨天的信息已经写进 system 的公开播报了。
     * 顺序是**先把投票压成票型，再按字数裁剪**（见 [renderDay] / [recentlySaid]）——
     * 反过来先裁再压，预算就被一堆「我投 3 号。因为……」提前吃掉了，
     * 当天最早的那几句发言会在压缩之前就已经被丢掉。
     */
    private fun context(table: WerewolfTable, seat: Int, task: String): String = buildString {
        // 白天只喂公开消息。夜聊原文是「只有狼醒着时说的话」，而模型分不清
        // 「我记得」和「我能说」——把原文摆在它面前，它就会在白天复述，实测出现过
        // 「昨夜四狼一致刀 4 号」这种当众自曝。狼白天真正需要的东西（队友名单、
        // 昨晚刀了谁）在 system 的私有简报里，不依赖这段原文。
        val daytime = !table.game.phase.isNight
        val game = table.game
        // 保留昨天。只看当天的话，到第 4 天模型完全不知道前几天谁说过什么，
        // 只能从上帝播报里反推，实测出现过「6 号昨天投票给 3 号很可疑」这种幻觉——
        // 那天有人自爆，根本没有投票环节。预算由 recentlySaid 的字数上限兜住。
        val from = (game.day - 1).coerceAtLeast(1)
        val recent = table.transcriptFor(seat)
            .filter { it.day >= from }
            .filter { !daytime || it.isPublic }
        // 身份与队友放在**最前面**再重申一遍。
        // system 里已经有一份私有简报，但它排在规则、板子、打法、公共局势之后，
        // 十几段中文压下来，模型对这种「一句话事实」的注意力会被稀释——实测表现为
        // 白天盘身份时把队友当成可疑对象，甚至投票落到队友身上，等于白送一张狼。
        // 所以每次请求的正文开头都钉一条，短、硬、只在本人的请求里出现。
        appendMemoryAnchor(game, seat)
        val lines = recentlySaid(renderRecent(recent, game.day))
        if (lines.isNotEmpty()) {
            append("【本日进程】\n")
            lines.forEach { append(it).append("\n") }
            append("\n")
        }
        appendSaidToday(recent, seat, game.day)
        append(task)
    }

    /**
     * 把「本人今天已经说过的话」单独列一段。
     *
     * 不列这一段，模型照样看得到自己的发言——但它混在十几个人的行里，
     * 权重和「别人说了什么」一样，模型不会把它当成「我刚说过」。
     * 实测后果是真预言家在警上发言、PK、白天讨论连说三段几乎一字不差的话，
     * 全场据此判定它是背词的悍跳狼，把它全票投了出去。
     */
    private fun StringBuilder.appendSaidToday(
        recent: List<WerewolfMessage>,
        seat: Int,
        today: Int,
    ) {
        val mine = recent.filter {
            it.day == today && it.seat == seat && it.kind.isSpokenOutLoud
        }
        if (mine.isEmpty()) return
        append("【你今天已经说过的话】\n")
        mine.forEach { message ->
            append("· ").append(message.phase.displayName).append("：")
            append(message.text.replace('\n', ' ').take(SAID_ECHO_CHARS))
            if (message.text.length > SAID_ECHO_CHARS) append("…")
            append("\n")
        }
        append("—— 上面这些不要再说一遍。这一轮必须给出新的内容：新的怀疑对象、")
        append("对别人刚说的那句话的回应，或者对自己上一票的解释。\n\n")
    }

    /**
     * 每次请求开头重申「我是谁、我的队友是谁」。
     *
     * 只给狼人。这不是给模型补规则，而是补**记性**：队友名单是一条硬事实，
     * 一旦在长上下文里被冲掉，狼队就在白天互踩、把票砸在自己人身上，
     * 而这在外人看来完全是好人的正常推理，连复盘都不好发现。
     *
     * 措辞上必须两头都说到：白天绝不能说出去，但心里绝不能不认。只写前者，
     * 模型会把「不提队友」执行成「不知道队友」。
     */
    private fun StringBuilder.appendMemoryAnchor(game: WerewolfGame, seat: Int) {
        val player = game.playerAt(seat)
        if (!player.role.isWerewolf) return
        val mates = game.wolves().filter { it.seat != seat }
        append("【别忘了你是谁】你是 ").append(seat).append(" 号")
        append(if (player.role == WerewolfRole.WEREWOLF_KING) "狼王" else "狼人").append("。")
        if (mates.isEmpty()) {
            append("这一局只有你一只狼，场上再没有你的同伙。\n\n")
            return
        }
        append("你的狼队友是 ")
        append(
            mates.joinToString("、") { mate ->
                "${mate.seat} 号「${mate.name}」" + if (mate.alive) "" else "（已出局）"
            }
        )
        append("。\n")
        append("白天**一个字都不能提他们**，但你自己心里必须清楚：他们不是候选，")
        append("永远不要把他们写进怀疑名单，更不要把票投给他们——那等于白送狼队一张牌。\n\n")
    }

    /**
     * 跨天渲染。昨天和今天分开标，只有一天时不留标题——白天的预算全给发言原文。
     */
    private fun renderRecent(messages: List<WerewolfMessage>, today: Int): List<String> {
        // 判据是「有没有更早的天」，不是「分了几个组」：第 2 天早上一条发言都还没有时，
        // 列表里只剩昨天的内容，按组数判会以为只有一天，把日期标题省掉
        if (messages.none { it.day < today }) return renderDay(messages)
        val out = mutableListOf<String>()
        messages.groupBy { it.day }.toSortedMap().forEach { (day, list) ->
            out += if (day >= today) "— 第 $day 天 —" else "— 第 $day 天（昨天及更早） —"
            out += renderDay(list)
        }
        return out
    }

    /**
     * 把当天的消息渲染成行，**投票压成一行票型**。
     *
     * 一条投票原文是「我投 3 号。因为他昨天说漏了。」，二三十字，12 个人就是三四百字；
     * 压成 `1→3（因为他昨天说漏了）、2→3（跟票）` 之后只剩几十字。
     * 省下来的预算全给发言——真正需要原文的只有发言，票型本身才是推理依据。
     *
     * 同一阶段连续的投票合成一行（警长投票与放逐票分开），因为它们是并发发生的，
     * 逐条列出来反而暗示了先后。
     */
    private fun renderDay(messages: List<WerewolfMessage>): List<String> {
        val lines = mutableListOf<String>()
        var index = 0
        while (index < messages.size) {
            val message = messages[index]
            if (message.kind != MessageKind.VOTE) {
                lines += "${message.seat?.let { "$it 号" } ?: "上帝"}：${message.text}"
                index++
                continue
            }
            val phase = message.phase
            val ballots = mutableListOf<String>()
            while (index < messages.size &&
                messages[index].kind == MessageKind.VOTE &&
                messages[index].phase == phase
            ) {
                ballots += ballotOf(messages[index])
                index++
            }
            val head = if (phase == Phase.DAY_SHERIFF_VOTE) "警长投票" else "投票"
            lines += "$head：" + ballots.joinToString("、")
        }
        return lines
    }

    /**
     * 一条投票压成「3→5（理由）」。
     *
     * 投票文本由 [voteText] 与警长投票那两处生成，格式是固定的。解析不出来就退回原文前
     * 十几字——宁可这一票读起来别扭，也不能因为改了文案就让整条投票从上下文里消失。
     */
    private fun ballotOf(message: WerewolfMessage): String {
        val seat = message.seat ?: return message.text
        val match = BALLOT.find(message.text)
            ?: return "$seat→" + message.text.take(VOTE_REASON_CHARS)
        val target = match.groupValues[1].ifEmpty { match.groupValues[2] }
        val reason = match.groupValues[3].trim().take(VOTE_REASON_CHARS)
        val head = "$seat→" + target.ifEmpty { "弃票" }
        return if (reason.isEmpty()) head else "$head（$reason）"
    }

    /**
     * 当天渲染好的行里，最后能塞进一次请求的那一段。
     *
     * 按字数裁而不是按条数：一句 200 字的发言和一行票型在上下文里差了十倍，
     * 按条数算对谁都不公平。压完票之后一天的正文通常在一千字上下，
     * [WerewolfSpeechLength.contextBudget] 这条线随发言档位缩放，留给「模型今天话特别多」的情况。
     *
     * 最后 [MIN_CONTEXT_MESSAGES] 行无条件保留：宁可这一次略超预算，
     * 也不能把「刚说完的话」裁掉——模型会立刻开始自相矛盾。
     */
    private fun recentlySaid(lines: List<String>): List<String> {
        val kept = ArrayDeque<String>()
        var chars = 0
        val budget = speechLength.contextBudget
        for (line in lines.asReversed()) {
            val size = line.length + 1
            if (kept.size >= MIN_CONTEXT_MESSAGES && chars + size > budget) break
            kept.addFirst(line)
            chars += size
        }
        return kept
    }

    /**
     * 记一条发言，并识别「自爆」。
     *
     * 自爆不走结构化决策，而是让狼在**发言开头写一个标记**。理由：自爆是一句话，
     * 不是一次选择——如果为了它再加一次 JSON 请求，每只狼每天都要多调一次模型，
     * 而绝大多数时候答案是「不爆」。用标记的话，自爆的成本是零，误判的成本也只是
     * 一句被剥掉前缀的发言。
     *
     * 标记只在「真的是能自爆的狼」时才生效：好人写了这四个字，不该让他凭空出局，
     * 也不该把这句话吃掉——那是他自己说的，就让它留在记录里。
     */
    private fun WerewolfTable.speakAndMaybeExplode(
        seat: Int,
        phase: Phase,
        raw: String,
        reasoning: String = "",
    ): WerewolfTable {
        val trimmed = raw.trimStart()
        val marker = EXPLODE_MARKERS.firstOrNull { trimmed.startsWith(it) }
        var text = if (marker != null) trimmed.removePrefix(marker).trim() else raw
        // 标记之外还要满足下面之一，才算「值得自爆」：
        //
        // 1. **点名一个活着的队友**（救队友）。原来只看标记，实测有狼在只被自己吓到的时候，
        //    输出一个光秃秃的「【自爆】」——正文为空、没有投票、直接送掉一条命。
        // 2. **竞选发言里自爆**（吞警徽）。这是正经花板子：一爆下去选举当场中止，
        //    本局没有警长，好人白丢一个能归票的位置。它不需要救谁，所以不能套第 1 条的判据。
        val exploding = marker != null && game.canExplode(seat) &&
            (phase == Phase.DAY_SHERIFF_SPEECH || game.explodesToSaveMate(seat, text))
        if (marker != null && !exploding && text.isBlank()) {
            // 标记被拒、正文又是空的：留一句占位，否则牌桌上会出现一条空白发言
            text = EMPTY_SPEECH_FALLBACK
        }
        var next = append(
            WerewolfMessage(
                day = game.day,
                phase = phase,
                seat = seat,
                kind = MessageKind.SPEECH,
                text = text,
                reasoning = reasoning,
            )
        )
        if (exploding) {
            val before = game
            next = next.copy(game = before.wolfExplode(seat)).syncBroadcasts(before)
        }
        return next
    }

    /**
     * 定刀结果那句话。空刀与自刀（刀自己人）都要说出来：
     * 空刀写成「定刀 0 号」会像个 bug，自刀不提就等于让队友以为刀的是好人。
     */
    private fun killLine(who: String, target: Int?): String =
        if (target == null) "$who 今晚空刀。" else "$who 定刀 $target 号。"

    /** 正文里点到至少一个**存活队友**的座位号，才算自爆有代价意义。 */
    private fun WerewolfGame.explodesToSaveMate(seat: Int, body: String): Boolean {
        val mates = wolves().filter { it.alive && it.seat != seat }.map { it.seat }.toSet()
        if (mates.isEmpty()) return false
        return SEAT_NUMBER.findAll(body)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .any { it in mates }
    }

    /** 座位号写法。只认「4 号」这种明确说法：裸数字会误吃「第 2 天」「两个」。 */
    private val SEAT_NUMBER = Regex("""(\d{1,2})\s*号""")

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
        return truncateAtSentence(text, speechLength.chars)
    }

    /**
     * 按**句子边界**截断。
     *
     * 原来直接 take(200)，切出来的常常是半句话——「我认我」「这刀必」「为什么偏偏要」，
     * 牌桌上读着像卡带，朗读也会把这半句念出来。现在退到最后一个句末标点，
     * 实在切不动才硬切并加省略号。
     *
     * 「退到最后一个句末标点」也有下限：只剩三五个字的话，宁可硬切加省略号，
     * 那样至少还看得出「后面还有话」。
     */
    internal fun truncateAtSentence(text: String, limit: Int): String {
        if (text.length <= limit) return text
        val cut = text.take(limit)
        val boundary = cut.indexOfLast { it in "。！？…" }
        return if (boundary > limit / 3) {
            cut.take(boundary + 1)
        } else {
            cut.trimEnd() + "…"
        }
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

    /**
     * 此刻占着思考气泡的座位。
     *
     * 并发阶段（投票、狼刀、上警报名）有好几个座位同时在思考。都往界面上推的话，
     * 屏幕上那**唯一**一个思考框会在四张脸之间一秒跳几十次，谁也读不完一句。
     * 所以先开口的那个占着，他结束了下一个才接手——同一时刻屏幕上永远只有一段完整的思路。
     * 没抢到的照样把思维链写进自己的消息里，复盘时一行都不少。
     */
    private var thinkingOwner: Int? = null
    private val thinkingLock = Any()

    private fun claimThinking(seat: Int): Boolean = synchronized(thinkingLock) {
        if (thinkingOwner == null || thinkingOwner == seat) {
            thinkingOwner = seat
            true
        } else {
            false
        }
    }

    private fun releaseThinking(seat: Int) {
        synchronized(thinkingLock) {
            if (thinkingOwner == seat) thinkingOwner = null
        }
    }

    /**
     * 分批并发。
     *
     * 全量并发在**深度思考**模型上会互相拖死：十二个人同时思考，服务端要排队，
     * 本机带宽也要分，每个请求都慢到超时——而且十二个全超时比一个一个来还慢。
     * 分批之后总时长反而更短，单个请求也能拿到更多带宽。
     *
     * 批次大小取 4：够并行又不至于把服务打爆。
     */
    private suspend fun <T, R> chunkedAsync(
        items: List<T>,
        chunkSize: Int = 4,
        /**
         * 进度回调：(已完成数, 总数, 这一批还没回话的人)。
         *
         * 「还没回话的人」就是界面上要显示的「谁在忙」——并发阶段同时有好几个请求
         * 在飞，只报一个座位会指错，只报总数又看不出在等谁。
         */
        onTick: suspend (done: Int, total: Int, pending: List<T>) -> Unit = { _, _, _ -> },
        block: suspend (T) -> R,
    ): List<R> {
        val out = ArrayList<R>(items.size)
        var done = 0
        items.chunked(chunkSize).forEach { chunk ->
            val pending = chunk.toMutableList()
            val lock = Any()
            onTick(done, items.size, pending.toList())
            out += coroutineScope {
                chunk.map { item ->
                    async {
                        val result = block(item)
                        synchronized(lock) {
                            done++
                            pending.remove(item)
                        }
                        onTick(done, items.size, pending.toList())
                        result
                    }
                }.awaitAll()
            }
        }
        return out
    }

    private companion object {

        /**
         * 看门狗里「本段不限时」时的轮询间隔。只在「一段不限、另一段有限」时走到，
         * 档位默认（思考有限、发言不限）下每圈睡这么久等收工或吐字，开销可忽略。
         */
        const val SEGMENT_POLL_MS = 500L

        /** 复述自己旧发言时，每条摘要留多少字。够认出「这是同一段」，又不占预算。 */
        const val SAID_ECHO_CHARS = 80

        /** 发言被清洗/拒绝后仍然为空时的占位。宁可平庸，也不要一条空气泡。 */
        const val EMPTY_SPEECH_FALLBACK = "我先听完这一轮，暂时不改判断。"

        /** 无论超没超预算都要留下的最近几行，保证「刚说完的话」一定在。 */
        const val MIN_CONTEXT_MESSAGES = 12

        /**
         * 票型里每个人理由保留的字数。
         *
         * 再长就是重复白天的发言了，而票型真正要回答的是「谁和谁投在一起」。
         */
        const val VOTE_REASON_CHARS = 15

        /**
         * 投票消息的固定格式，由 [voteText] 与警长投票两处生成。
         *
         * 三个分支对应三种文本：`我投 3 号。`、`我选 3 号当警长。`、`我弃票。`，
         * 句号之后全部是理由。
         */
        val BALLOT = Regex("""^我(?:投 (\d+) 号|选 (\d+) 号当警长|弃票)。(.*)$""", RegexOption.DOT_MATCHES_ALL)

        /** 一句发言的长度上限（标准档）。各档位见 [WerewolfSpeechLength]，这里只留注释不占常量。 */

        /**
         * 自爆标记。模型写成全角、半角方括号都认，中文括号也认——
         * 这几个写法都是它自己会写出来的，多认一种就少一次「明明爆了却没算」。
         */
        val EXPLODE_MARKERS = listOf("【自爆】", "[自爆]", "（自爆）", "(自爆)")

        /** 整局的调度步数上限，防止某处状态不推进导致死循环。 */
        const val MAX_STEPS = 500

        /** 流式发言的推送间隔，见 [WerewolfDirector.streamIntervalMs]。 */
        const val STREAM_PUSH_MS = 80L

        /**
         * 发言超出时限、又一句话都没接上时的替身发言。
         *
         * 不让它变成空白：牌桌上「3 号：」后面什么都没有，后面的人还会对着空白接话，
         * 看起来像这局坏了。这句话是**认输**而不是撒谎，不会给狼人送信息，
         * 也不会让好人误判谁的身份。
         */
        const val SILENT_SPEECH = "……我这轮先听大家的。"
    }
}
