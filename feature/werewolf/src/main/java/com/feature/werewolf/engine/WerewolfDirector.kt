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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
     * 只在**发言**这条路径上触发（[askSpeech]）。投票、查验、用药这些决策返回的是一段
     * JSON，逐字吐出来的中间态既没法解析也没法显示；而发言本来就是给人听的话，
     * 让它一个字一个字地出现，牌桌才像有人真的在说话，而不是等十几秒后整段蹦出来。
     *
     * 回调的是**半句话**：调用方只能拿去显示，不能落档、不能进上下文、不能被朗读。
     */
    private val onLive: suspend (WerewolfLive?) -> Unit = {},
    /**
     * 两次 [onLive] 之间的最小间隔。
     *
     * 模型一秒能吐几十个片段，每片都刷一次界面纯属浪费——牌桌一次重排要重建整张记录流。
     * 80ms 是「看得出在打字」和「不要每帧都重组」之间的折中。测试里传 0 才能断言每次增量。
     */
    private val streamIntervalMs: Long = STREAM_PUSH_MS,
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
        speakers.forEachIndexed { index, seat ->
            if (current.game.phase != Phase.DAY_DISCUSSION) return current
            onProgress(
                WerewolfProgress(
                    day = current.game.day,
                    phase = Phase.DAY_DISCUSSION,
                    step = index + 1,
                    total = speakers.size,
                    seat = seat,
                )
            )
            val text = askSpeech(current, seat)
            current = current.speakAndMaybeExplode(seat, Phase.DAY_DISCUSSION, text)
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
     */
    suspend fun sheriffSignup(table: WerewolfTable): WerewolfTable {
        val game = table.game
        if (game.phase != Phase.DAY_SHERIFF_SIGNUP) return table

        onProgress(WerewolfProgress(game.day, Phase.DAY_SHERIFF_SIGNUP, 0, game.aliveSeats.size))
        val outcomes = coroutineScope {
            game.aliveSeats.map { seat -> async { seat to askSheriffSignup(table, seat) } }.awaitAll()
        }
        onProgress(WerewolfProgress(game.day, Phase.DAY_SHERIFF_SIGNUP, game.aliveSeats.size, game.aliveSeats.size))

        var next = table
        outcomes.forEach { (seat, outcome) ->
            val running = outcome.value.target == seat
            next = next.append(
                WerewolfMessage(
                    day = game.day,
                    phase = Phase.DAY_SHERIFF_SIGNUP,
                    seat = seat,
                    kind = MessageKind.SPEECH,
                    text = (if (running) "我上警。" else "我不上警。") + outcome.value.reason,
                )
            )
        }
        val runners = outcomes.filter { (seat, outcome) -> outcome.value.target == seat }
            .map { it.first }
            .toSet()
        val after = game.signUpSheriff(runners)
        return next.copy(game = after).syncBroadcasts(game).also { onTranscript(it) }
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

        speakers.forEachIndexed { index, seat ->
            if (current.game.phase != Phase.DAY_SHERIFF_SPEECH) return current
            onProgress(
                WerewolfProgress(
                    day = current.game.day,
                    phase = Phase.DAY_SHERIFF_SPEECH,
                    step = index + 1,
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
            current = current.speakAndMaybeExplode(seat, Phase.DAY_SHERIFF_SPEECH, text)
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
            val outcomes = coroutineScope {
                voters.map { seat -> async { seat to askSheriffVote(current, seat, candidates) } }.awaitAll()
            }
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
        pending.forEachIndexed { index, seat ->
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
                current = current.append(
                    WerewolfMessage(
                        day = current.game.day,
                        phase = Phase.DAY_LAST_WORDS,
                        seat = seat,
                        kind = MessageKind.SPEECH,
                        text = head + outcome.value.reason,
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
                        text = text,
                    )
                )
            }
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
                // 每个座位投完就发一个完成信号，主协程逐个收、逐个报进度。
                // 不在 async 里直接调 onProgress：那样并发写 StateFlow 虽然安全，
                // 但到达顺序由调度决定，step 会 3、1、2 乱跳。收回这里报才严格单调。
                // 信号里带 voters 的下标而非座位号：归位顺序要和改动前的
                // `map{async}.awaitAll()` 逐字节一致，不依赖座位号恰好升序。
                val finished = Channel<Result<Pair<Int, Pair<Int, StructuredOutcome<VoteChoice>>>>>(
                    voters.size
                )
                voters.forEachIndexed { index, seat ->
                    launch {
                        // 异常必须随信号一起收回主协程再抛：若在这里直接抛，coroutineScope
                        // 只会给上层一个 CancellationException，Controller 会误当成「用户退出」
                        // 而不显示错误。也不能失败就不发信号——那样 receive 会永远挂起。
                        finished.send(
                            runCatching { index to (seat to askVote(current, seat, candidates)) }
                        )
                    }
                }
                val collected = arrayOfNulls<Pair<Int, StructuredOutcome<VoteChoice>>>(voters.size)
                repeat(voters.size) { step ->
                    // getOrThrow 把网络/Key 错误原样抛回 Controller 的 catch(t: Throwable)
                    val (index, outcome) = finished.receive().getOrThrow()
                    collected[index] = outcome
                    onProgress(
                        WerewolfProgress(
                            day = game.day,
                            phase = Phase.DAY_VOTE,
                            step = step + 1,
                            total = voters.size,
                            note = if (rounds > 0) "平票，重新投票" else null,
                        )
                    )
                }
                // 按原始下标归位：票型顺序不能随模型返回快慢变化
                collected.map { it!! }
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
    ): String {
        val agent = agentOf(seat)
        val game = table.game
        val messages = listOf(ChatMessage.user(context(table, seat, task)))
        val system = WerewolfPrompt.systemPrompt(game, seat, agent.persona)
        val content = StringBuilder()
        val reasoning = StringBuilder()
        var lastPush = 0L
        var pushed = false

        try {
            agent.ai.chatStream(
                messages = messages,
                model = agent.model,
                systemPrompt = system,
                enableThinking = agent.thinkingEnabled,
            ).collect { delta ->
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
                        )
                    )
                }
            }
        } finally {
            withContext(NonCancellable) { onLive(null) }
        }

        // 流式一个片段都没收到（厂商没实现 SSE，或者返回了空流）时补一次普通请求。
        // 不补的话这句话会变成空白，后面所有人都会看到「3 号：」后面什么都没有
        val raw = if (content.isEmpty()) {
            agent.ai.chat(
                messages = messages,
                model = agent.model,
                systemPrompt = system,
                enableThinking = agent.thinkingEnabled,
            )
        } else {
            content.toString()
        }
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
     * 只取当天的消息，因为跨天的信息已经写进 system 的公开播报了。
     * 顺序是**先把投票压成票型，再按字数裁剪**（见 [renderDay] / [recentlySaid]）——
     * 反过来先裁再压，预算就被一堆「我投 3 号。因为……」提前吃掉了，
     * 当天最早的那几句发言会在压缩之前就已经被丢掉。
     */
    private fun context(table: WerewolfTable, seat: Int, task: String): String = buildString {
        val lines = recentlySaid(renderDay(table.transcriptFor(seat).filter { it.day == table.game.day }))
        if (lines.isNotEmpty()) {
            append("【本日进程】\n")
            lines.forEach { append(it).append("\n") }
            append("\n")
        }
        append(task)
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
     * [MAX_CONTEXT_CHARS] 这条线是留给「模型今天话特别多」那种情况的。
     *
     * 最后 [MIN_CONTEXT_MESSAGES] 行无条件保留：宁可这一次略超预算，
     * 也不能把「刚说完的话」裁掉——模型会立刻开始自相矛盾。
     */
    private fun recentlySaid(lines: List<String>): List<String> {
        val kept = ArrayDeque<String>()
        var chars = 0
        for (line in lines.asReversed()) {
            val size = line.length + 1
            if (kept.size >= MIN_CONTEXT_MESSAGES && chars + size > MAX_CONTEXT_CHARS) break
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
    private fun WerewolfTable.speakAndMaybeExplode(seat: Int, phase: Phase, raw: String): WerewolfTable {
        val marker = EXPLODE_MARKERS.firstOrNull { raw.trimStart().startsWith(it) }
        val exploding = marker != null && game.canExplode(seat)
        val text = if (exploding) raw.trimStart().removePrefix(marker!!).trim() else raw
        var next = append(
            WerewolfMessage(day = game.day, phase = phase, seat = seat, kind = MessageKind.SPEECH, text = text)
        )
        if (exploding) {
            val before = game
            next = next.copy(game = before.wolfExplode(seat)).syncBroadcasts(before)
        }
        return next
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
        /**
         * 当天进程的字数预算。
         *
         * 12 人局一句发言上限 [MAX_SPEECH_LENGTH] 字，一天最多 12 条发言加 12 张票再加几条播报。
         * 投票压成票型之后，典型一天只剩一千字上下，这条线留给「模型今天话特别多」的情况。
         */
        const val MAX_CONTEXT_CHARS = 3200

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

        /** 一句发言的长度上限。模型偶尔会写一整段小作文。 */
        const val MAX_SPEECH_LENGTH = 200

        /**
         * 自爆标记。模型写成全角、半角方括号都认，中文括号也认——
         * 这几个写法都是它自己会写出来的，多认一种就少一次「明明爆了却没算」。
         */
        val EXPLODE_MARKERS = listOf("【自爆】", "[自爆]", "（自爆）", "(自爆)")

        /** 整局的调度步数上限，防止某处状态不推进导致死循环。 */
        const val MAX_STEPS = 500

        /** 流式发言的推送间隔，见 [WerewolfDirector.streamIntervalMs]。 */
        const val STREAM_PUSH_MS = 80L
    }
}
