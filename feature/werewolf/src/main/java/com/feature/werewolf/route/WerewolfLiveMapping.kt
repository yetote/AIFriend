package com.feature.werewolf.route

// 实时局面 → 牌桌的映射：座位状态、高亮、气泡分类、进度文案、身份揭露。

import com.feature.werewolf.engine.HumanTurn
import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.TurnKind
import com.feature.werewolf.engine.TurnTimeout
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfProgress
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfPlayer
import com.feature.werewolf.game.WerewolfRole
import com.ui.werewolf.data.UIWerewolfBallot
import com.ui.werewolf.data.UIWerewolfBallotCard
import com.ui.werewolf.data.UIWerewolfBallotTally
import com.ui.werewolf.data.UIWerewolfCheck
import com.ui.werewolf.data.UIWerewolfFate
import com.ui.werewolf.data.UIWerewolfLine
import com.ui.werewolf.data.UIWerewolfLineKind
import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfRetry
import com.ui.werewolf.data.UIWerewolfSeatLive
import com.ui.werewolf.data.UIWerewolfStreaming
import com.ui.werewolf.data.UITokenUsage
import com.ui.werewolf.data.UIWerewolfVerdict

/**
 * 狼人杀的入口。
 *
 * 只做三件事：把领域数据翻成展示模型、把界面操作翻回 ViewModel、在「列表 / 配置 / 对局」
 * 之间切换。规则、请求调度、存档都在更下层，这一层不出现任何判定逻辑。
 */
internal fun WerewolfUiState.toLive(
    humanSeat: Int?,
    turn: HumanTurn?,
    avatars: Map<String, String?>,
    autoSpeak: Boolean = true,
    speechReady: Boolean = true,
    revealedIndex: Int = Int.MAX_VALUE,
    /** 喇叭此刻在念谁的发言，用来把牌桌上那个人亮起来。null = 没在念。 */
    speakingSeat: Int? = null,
    /** 喇叭手里还有没有没念完的行（含正在念的这一句）。 */
    speechBusy: Boolean = false,
    /**
     * 要不要把 AI 的思考过程给界面。
     *
     * 参与局看到别人的思考等于读心——那会把「猜谁在想什么」变成主要玩法。
     * 所以默认不给，只有观战（或者对局结束）才给；参与局里用户可以自己在牌桌顶栏打开，
     * 界面会明写着「这是作弊」。开关的值记在设置里，开了就一直是开的。
     */
    showThinking: Boolean = false,
    /**
     * 每个座位的思考气泡可见性**覆盖值**（seat → visible）。缺席 = 跟随全局开关。
     * 长按气泡写 false，对局设置面板里可以单独开/关；优先于 [showThinking]。
     */
    bubbleVisible: Map<Int, Boolean> = emptyMap(),
    /** 这一局的思考时限（秒），0 表示不设限。气泡上要显示「12s / 60s」。 */
    decisionSeconds: Int = 0,
    speechSeconds: Int = 0,
    /** 刚刚超时的那一步，显示几秒就过。 */
    timeoutNotice: String? = null,
    timeoutAt: Long = 0L,
    /** 某一步请求失败、正挂着等人重发。null = 没有待处理的失败。 */
    retry: UIWerewolfRetry? = null,
): UIWerewolfLive {
    val table = table
    val game = table?.game
    val reveal = humanSeat == null || finished
    val voteCounts = game?.ballots
        ?.mapNotNull { it.targetSeat }
        ?.groupingBy { it }
        ?.eachCount()
        .orEmpty()

    val me = game?.players?.firstOrNull { it.seat == humanSeat }

    // 显示闸门：朗读正在推进时，界面只跟着队列走——文字、背景、第几天都一样。
    // 关了朗读、没语音包、对局没在跑、已结束——这些情况一律全文显示，绝不能
    // 让闸门把话永久藏住（否则用户关掉语音反而看不到任何发言）。
    val gated = autoSpeak && speechReady && running && !finished
    val revealed = if (gated) revealedIndex.coerceAtLeast(-1) else Int.MAX_VALUE
    // 舞台时间（背景 / 第几天 / 白天黑夜）：朗读没追上就停在已播到最后一条上，
    // 否则会「声音还在夜里，背景已经天亮」。见 StageMoment。
    val moment = stageMoment(
        transcript = table?.transcript.orEmpty(),
        revealed = revealed,
        gated = gated,
        fallbackDay = game?.day ?: 1,
        fallbackPhase = game?.phase,
        // 喇叭还在响就说明舞台还没走完最后一条。少了这一半判断，念最后一条的时候
        // `revealed` 已经追平，界面会当场切到引擎的下一阶段（遗言没念完，天已经黑了）
        speechBusy = speechBusy,
    )
    // 舞台追上引擎了吗。没追上说明屏幕上是「回放」，引擎那边的「轮到谁、走到第几个」
    // 都属于未来：先亮起 4 号在夜里思考，再听见白天的话，是比背景抢跑更明显的剧透。
    val onStage = moment.phase == game?.phase && moment.day == (game?.day ?: moment.day)

    // 夜里「现在轮到谁」本身就是隐私：狼人商议挨个开口，守卫、预言家、女巫也都只有一个座位
    // 在动。把这个人亮在牌桌上，等于把身份直接摊给好人看。观战和本人轮到时照常亮。
    //
    // 这条判断同时给三处用：座位高亮、牌桌底部的「正在思考」、以及流式发言。
    // 三处必须同源——分开写迟早会有一处漏判，而漏判的后果是把狼人身份亮在桌上。
    val actorVisible: (Int, Phase) -> Boolean = { seat, phase ->
        reveal || !phase.hidesActor || seat == humanSeat ||
            // 狼人密谈对狼队是公开的：他们本来就要一起商量。
            // 但只对**狼**开放——这一档本来就不该有非狼的座位冒出来，
            // 多这一道判断是防以后有人往这个阶段塞别的东西。
            (phase == Phase.NIGHT_WOLF_CHAT && me?.role?.isWerewolf == true &&
                game?.players?.firstOrNull { it.seat == seat }?.role?.isWerewolf == true)
    }
    val actorSeat = progress?.seat
        ?.takeIf { onStage }
        ?.takeIf { seat -> actorVisible(seat, progress.phase) }

    // 并发阶段（投票、上警报名、退水）正在请求的是**一批**座位，不是一个。
    // 白天亮出来是有用的——「他们几个正在投票」一眼就懂；
    // 夜里绝不能亮：夜里谁在动就等于把身份摊给好人看（见上面 actorVisible 的注释）。
    val busySeats = progress?.seats
        ?.takeIf { onStage && !progress.phase.isNightPhase() }
        ?.filter { seat -> actorVisible(seat, progress.phase) }
        ?.toSet()
        .orEmpty()

    val litSeats = litSeats(
        actorSeat = actorSeat,
        busySeats = busySeats,
        speakingSeat = speakingSeat,
        running = running,
    )

    // 你是狼时，队友名单本来就是你的已知信息（线下睁眼就认识）。
    // 观战 / 已结束时 roleLabel 已经把身份写在脸上，不需要再标一遍。
    val wolfMateSeats = if (!reveal && me?.role?.isWerewolf == true && game != null) {
        game.players.asSequence()
            .filter { it.role.isWerewolf && it.seat != humanSeat }
            .map { it.seat }
            .toSet()
    } else {
        emptySet()
    }

    // 预言家的查验结论长在**被验的那个座位**上：牌桌里「3 号是狼」这件事直接
    // 长在 3 号头像下面，白天报验人、对账全靠这一眼，不用回头翻记录流。
    //
    // 只给参与局里的**预言家本人**：那是他的私有情报，好人和狼人拿到就等于
    // 把局势直接交出去。上帝视角（观战）和已结束的局都不给——那里所有人的身份
    // 本来就摊开着，徽标那一格留给身份名就够，「好人 / 狼人」是多余的信息。
    //
    // 同一个座位被验过多次时取最后一次（associate 保留后写进去的）。
    val verdictBySeat = if (me?.role == WerewolfRole.SEER && !reveal) {
        game?.seerChecks.orEmpty().associate { it.targetSeat to it.isWerewolf }
    } else {
        emptyMap()
    }

    val seats = game?.players?.map { player ->
        // 翻过牌的白痴身份是公开的：牌桌上必须给所有人看见，
        // 否则会留下一个「没有身份标记却还活着」的座位，读起来自相矛盾
        val known = reveal || (player.role == WerewolfRole.IDIOT && game.idiotRevealed)
        UIWerewolfSeatLive(
            seat = player.seat,
            name = player.name,
            avatarPath = avatars[player.id],
            // 人座是 "human"，界面据此不显示；AI 座显示存档里记下的原局模型
            model = player.model.takeIf { it != "human" }.orEmpty(),
            isSelf = player.seat == humanSeat,
            alive = player.alive,
            // 参与中的局不暴露刀/毒：那是 AI 玩家拿不到的私有信息。
            // 只给一个中性的「夜」，让「他死了」这件事照常可见。
            // 观战和已结束才显示真实死因——那时复盘本来就该看清。
            fate = player.deathCause?.toFate()?.let { fate ->
                if (!reveal && (fate == UIWerewolfFate.WOLF_KILL || fate == UIWerewolfFate.WITCH_POISON)) {
                    UIWerewolfFate.NIGHT
                } else {
                    fate
                }
            },
            roleLabel = if (known) player.role.displayName else null,
            // 查验结论挂在这个被验的座位上。只有参与局里的预言家拿得到（口径见上），
            // 别的视角这一项恒为 null——私有情报不在客户端留路径。
            seerVerdict = verdictBySeat[player.seat]?.let { isWolf ->
                if (isWolf) UIWerewolfVerdict.WOLF else UIWerewolfVerdict.GOOD
            },
            // 阵营同样要等身份公开才有资格报。参与中的局里把真实的 isWerewolf
            // 原样交给界面，等于把「谁是狼」发到了客户端——今天界面上还没用它画什么，
            // 但只要有人加一个狼人徽标，身份就当场翻了。
            // 「公开」的口径跟 roleLabel 保持一致：观战/结束，或者翻过牌的白痴。
            wolf = known && player.role.isWerewolf,
            active = player.seat in litSeats,
            votes = voteCounts[player.seat],
            // 警长是公开身份，牌桌上必须一直挂着——它是「谁的一票值 1.5」的唯一提示
            sheriff = game.sheriffSeat == player.seat,
            // 竞选进行中、尚未选出警长时，标出此刻仍在警上的候选人（退水后自动消失）
            sheriffCandidate = game.sheriffSeat == null && player.seat in game.sheriffCandidates,
            isWolfMate = player.seat in wolfMateSeats,
        )
    }.orEmpty()

    // 先在**原始** transcript 上截断，再按视角过滤私有消息。
    // 顺序不能反：transcriptFor 会剔掉私有消息导致下标重排，拿过滤后的下标
    // 去截原始进度会错位，把别人的话放出来或把自己的话藏住。
    val visible = emergedVisible(table?.transcript.orEmpty(), revealed, humanSeat)

    val lines = visible.mapIndexed { index, message ->
        val speaker = message.seat?.let { seat -> game?.players?.firstOrNull { it.seat == seat } }
        val sheriffRoster = if (game != null) buildSheriffRoster(message, game, humanSeat) else null
        UIWerewolfLine(
            index = index,
            kind = if (sheriffRoster != null) UIWerewolfLineKind.SHERIFF_ROSTER else lineKindOf(message),
            seat = message.seat,
            speaker = speaker?.name,
            avatarPath = speaker?.id?.let { avatars[it] },
            text = message.text,
            innerLabel = innerLabelOf(message),
            secret = !message.isPublic,
            ballot = message.ballot?.let { card ->
                UIWerewolfBallotCard(
                    ballots = card.ballots.map { UIWerewolfBallot(it.voterSeat, it.targetSeat) },
                    tally = card.tally.map { (seat, votes) -> UIWerewolfBallotTally(seat, votes) }
                        .sortedWith(compareByDescending<UIWerewolfBallotTally> { it.votes }.thenBy { it.seat }),
                    abstained = card.abstained,
                    title = if (message.phase == Phase.DAY_SHERIFF_VOTE) "警长投票" else "放逐投票",
                )
            },
            sheriffRoster = sheriffRoster,
        )
    }

    // 此刻正在逐字说的一段话。四道闸门缺一不可：
    //
    // 1. 对他可见吗——和座位高亮同一套判断（actorVisible）。夜里守卫在守人，
    //    把他亮出来、还把他想的话打出来，等于直接把身份念给全场听；
    // 2. 朗读积压了吗——文字跟着语音走的时候，抢先打字就是把下一句提前剧透；
    // 3. **喇叭闲了吗**——只比 revealedIndex 是不够的：念 A 的时候进度已经推到 A，
    //    看起来追平了，但 A 还在响。这时把 B 的打字打出来，用户看到的就是
    //    「下一个人的对话框冒出来、然后又消失」（等 B 真念到才重新出现）。
    //    所以要等队列真的空下来（speechBusy=false）才允许显示；
    //    **例外：已经开口的那段**（text 非空）。朗读永远追不上生成速度，闸门
    //    会在一条发言的生成过程中反复开关；每关一次，屏幕上那半句话就被撤掉，
    //    等整段落进记录才重新出现——用户看到的就是「说到一半突然没了」。
    //    抢跑一点（下一句的字先出来）远好过让正在讲话的人凭空消失。
    // 4. **思考过程给不给看**——参与局默认不给（别人推理=作弊），观战/已结束一律给；
    //    参与局里自己打开「思考」开关也就有了，界面上会写清楚那是作弊。
    //    开关只放行「想的内容」，不放行「谁在动」：夜里的思考框照样受第 1 条管，
    //    否则一开开关就等于把每个角色的身份摊在桌上。
    val streaming = live
        ?.takeIf { running && !finished }
        ?.takeIf { item -> actorVisible(item.seat, item.phase) && item.seat != humanSeat }
        ?.takeIf { item ->
            !gated || item.text.isNotEmpty() ||
                (!speechBusy && revealed >= (table?.transcript?.lastIndex ?: -1))
        }
        ?.let { item ->
            val speaker = game?.players?.firstOrNull { it.seat == item.seat }
            if (speaker == null) {
                null
            } else {
                UIWerewolfStreaming(
                    seat = item.seat,
                    speaker = speaker.name,
                    avatarPath = avatars[speaker.id],
                    text = item.text,
                    isDecision = item.isDecision,
                    usage = UITokenUsage(
                        input = item.usage.input,
                        output = item.usage.output,
                        estimated = item.usage.estimated,
                    ),
                    // 思考内容是别人的心里话：观战/结束、或者用户开了开关才给。
                    // 单个座位的覆盖值优先：长按藏掉的座位，全局开着也不给。
                    reasoning = if (bubbleVisible[item.seat] ?: (reveal || showThinking)) {
                        item.reasoning
                    } else {
                        ""
                    },
                )
            }
        }

    return UIWerewolfLive(
        day = moment.day,
        // 夜里的阶段名要按视角收：参与局里「守卫守护」「女巫用药」是在报这个角色在场，
        // 「狼人商议 1/3」还顺带把**狼的数量**报给了全场——平民看一眼就知道这局有几匹狼。
        // 详见 nightStageLabel。
        phaseLabel = moment.phase?.let { phase ->
            if (reveal || !phase.isNightPhase()) phase.displayName else nightStageLabel(phase, me)
        } ?: "准备中",
        night = moment.phase?.let { it.isNightPhase() } ?: false,
        running = running,
        finished = finished,
        winnerLabel = game?.winner?.let { "${it.displayName}胜" },
        // 舞台还停在过去时，「轮到谁、走到第几个」属于未来的阶段，一律不显示。
        // 「谁在忙」并进这一行（「发言 3/8 · 5 号」），天数不重复：顶栏标题已经在报。
        //
        // 夜里分三档：
        // - 观战：上帝视角，阶段和进度照给，但仍然不带座位；
        // - 本人正处在这一阶段：给阶段名和进度（他本来就知道自己是什么角色，
        //   而且需要知道自己正卡在「下刀」「用药」这样的环节里）；
        // - 别人：一句中性的「夜间进行中」，阶段名和人数一个字都不给。
        stageLabel = progress?.takeIf { onStage }?.let { p ->
            when {
                !p.phase.isNightPhase() -> p.stageLabel
                reveal -> p.stageText
                else -> nightStageLabel(p.phase, me, p)
            }
        },
        seats = seats,
        lines = lines,
        streaming = streaming,
        selfSeat = humanSeat,
        selfRoleLabel = me?.role?.displayName,
        selfFactionLabel = me?.role?.faction?.displayName,
        // 验人本只给**预言家本人**。它是私有信息：别人的牌桌上不能有它，
        // 否则界面就等于把「谁被验出是狼」发到了客户端。
        // 观战看不到这一份——上帝视角要看验人记录，去复盘页那一节。
        selfChecks = if (me?.role == WerewolfRole.SEER) {
            game?.seerChecks.orEmpty().map { UIWerewolfCheck(it.targetSeat, it.isWerewolf) }
        } else {
            emptyList()
        },
        spectating = humanSeat == null,
        turn = if (game != null) buildTurn(game, humanSeat, me, turn) else null,
        // 自爆按钮比 turn 宽一档：别人说话时就能点。状态机是唯一判据
        // （白天发言环节 + 活着 + 是狼），阶段性关掉的局、已结束的局都不会亮
        canExplode = game != null && humanSeat != null && running && !finished &&
            game.canExplode(humanSeat),
        error = error,
        // 局已经结束/回列表时，把迟到的重试提示丢掉——那一步的协程早被取消了
        retry = retry?.takeIf { running && !finished },
        autoSpeak = autoSpeak,
        speechReady = speechReady,
        // 界面拿它点亮顶栏那个「思考」开关。观战 / 已结束恒为开：那两个视角本来就能看
        // 整段思考（复盘里也摊着），不给才是怪事
        showThinking = reveal || showThinking,
        // 闸门没生效时一律当作「不忙」：关朗读/已结束/没语音包的时候，
        // 界面必须照常显示正在生成的内容，否则看起来就是卡住了。
        speechBusy = speechBusy && gated,
        thinkingDecisionSeconds = decisionSeconds,
        thinkingSpeechSeconds = speechSeconds,
        timeoutNotice = timeoutNotice,
        timeoutAt = timeoutAt,
    )
}

/**
 * 一条消息在界面上的排版性质。
 *
 * 「说出口」和「心里想」必须分成两种样子：
 *
 * - [MessageKind.SPEECH] → 发言气泡（有头像、正常字号）
 * - [MessageKind.WOLF_CHAT] → 密谈卡（也是说出口的，只是听众只有狼，所以不能并进内心活动，
 *   否则会让人以为队友当时没在商量）
 * - [MessageKind.VOTE] / [MessageKind.NIGHT_ACT] → 内心活动（压暗的小字），
 *   它们**没有人听见**，也永远不朗读
 * - [MessageKind.DIVINE] → 居中播报
 */
internal fun lineKindOf(kind: MessageKind): UIWerewolfLineKind = when (kind) {
    MessageKind.DIVINE -> UIWerewolfLineKind.DIVINE
    MessageKind.BALLOT -> UIWerewolfLineKind.BALLOT
    MessageKind.SPEECH -> UIWerewolfLineKind.SPEECH
    MessageKind.WOLF_CHAT -> UIWerewolfLineKind.PRIVATE
    MessageKind.VOTE -> UIWerewolfLineKind.VOTE
    MessageKind.NIGHT_ACT -> UIWerewolfLineKind.INNER
}

/**
 * 按整条消息定排版。
 *
 * 只有一个地方要比 [lineKindOf] 更细：**预言家的查验结论**。
 *
 * 它在引擎里也是 `NIGHT_ACT`（夜里发生、没人听见），但它跟「投票念头」
 * 分量完全不同：那是预言家唯一的价值所在，他白天要拿它去带票、去对账。
 * 做成压暗小字，玩家多半当成一句闲话划过去——查验完什么都不知道，
 * 这个身份就废了。所以单独给一档。
 */
internal fun lineKindOf(message: com.feature.werewolf.engine.WerewolfMessage): UIWerewolfLineKind =
    if (message.kind == MessageKind.NIGHT_ACT && message.phase == Phase.NIGHT_SEER) {
        UIWerewolfLineKind.SEER_RESULT
    } else {
        lineKindOf(message.kind)
    }

/**
 * 内心活动的小标签：投票、守人、查验、用药、开枪。
 *
 * 只写「内心」的话，一夜里的几条记录看起来像同一件事说了好几遍，
 * 玩家要靠点开上下文才知道刚才那是在守人还是在查验。
 */
internal fun innerLabelOf(message: com.feature.werewolf.engine.WerewolfMessage): String? =
    when (message.kind) {
        MessageKind.VOTE -> "投票"
        MessageKind.NIGHT_ACT -> when (message.phase) {
            Phase.NIGHT_GUARD -> "守人"
            Phase.NIGHT_SEER -> "查验"
            Phase.NIGHT_WITCH -> "用药"
            // 开枪是白天的事，但「为什么打这一枪」跟夜里那些念头同类：
            // 枪响公开、理由不公开
            Phase.HUNTER_SHOOT -> "开枪"
            else -> "念头"
        }
        else -> null
    }

/**
 * 把一局翻译成牌桌能直接画的东西。
 *
 * 三条规则：
 *
 * 1. **可见性在这一层过滤完**，界面不再判断。观战看全量（上帝视角，狼人夜聊也看得见），
 *    参与看 `transcriptFor(我的座位)`。
 * 2. **身份只在公开时给出**：观战，或者已经分出胜负。参与中的局里，别人的身份连 UI 都拿不到，
 *    想从界面泄露也没有路径。
 * 3. **「现在该我做什么」只看 `phase` 加本人身份**，不解析提示词。提示词是文案，
 *    随时会改，拿它当协议早晚对不上。
 */
/**
 * 显示闸门：只放出前 [revealed] 条（含），再按视角过滤私有消息。
 *
 * 顺序是关键——必须先在**原始** transcript 上截断，再过滤：反过来做的话，
 * 私有消息被剔掉后下标重排，拿旧下标去截会把别人的话放出来、把自己的话藏住。
 * [revealed] 为 Int.MAX_VALUE 表示不设闸门（朗读关/无语音包/已结束），全文显示。
 */
internal fun emergedVisible(
    transcript: List<com.feature.werewolf.engine.WerewolfMessage>,
    revealed: Int,
    humanSeat: Int?,
): List<com.feature.werewolf.engine.WerewolfMessage> {
    // 不能写 take(revealed + 1)：无闸门时 revealed 是 Int.MAX_VALUE，
    // 加一溢出成负数，take(-2147483648) 直接抛异常。用 size 兜住上界。
    val count = if (revealed >= transcript.lastIndex) transcript.size else revealed + 1
    val emerged = transcript.take(count)
    return if (humanSeat == null) emerged else emerged.filter { it.visibleTo(humanSeat) }
}

/**
 * 牌桌上该亮起来的座位。
 *
 * 两条独立的线：引擎此刻在问谁（[actorSeat]）、喇叭此刻在念谁（[speakingSeat]）。
 * 引擎比朗读跑得快，这两个经常不是同一个人——一个在说，下一个已经在想。
 * 两件都是「正在发生」，所以都亮。
 *
 * 对局没在跑时一个都不亮：复盘看的是整段历史，没有「此刻」可言，
 * 留一个还在呼吸的头像会让人以为这局还在打。
 *
 * [speakingSeat] 的安全性不在这一层：它只由朗读队列里 `speakable=true` 的行产生，
 * 而筛选已经保证了参与局里只有 AI 的公开发言会出声（见 SpeechFilter）。
 */
internal fun litSeats(
    actorSeat: Int?,
    busySeats: Set<Int> = emptySet(),
    speakingSeat: Int?,
    running: Boolean,
): Set<Int> =
    if (!running) emptySet() else setOfNotNull(actorSeat, speakingSeat) + busySeats

/** 夜晚几个阶段算夜。猎人开枪归到白天播报里，所以不算。 */
/**
 * 这个阶段「谁在行动」是不是隐私。
 *
 * 白天发言、投票、猎人开枪都是公开的；夜里那些阶段一次只有一个座位能动，
 * 亮出来就等于把身份说穿。
 *
 * **狼人下刀也算在内**。它是并发阶段、没有唯一的「当前是谁」，但正在下刀的那几个人
 * 就是全部狼人——**并发不等于不隐私**。
 *
 * 之前把 [Phase.NIGHT_WOLF] 单独特豁免，是把「没有当前」当成了「不隐私」：
 * 高亮那一路本来就用 [Phase.isNightPhase] 单独挡过（并发阶段一律不亮），
 * 豁免在这里只漏掉了另一路——流式气泡。谁在下刀，气泡一冒出来，狼就全亮了。
 */
internal val Phase.hidesActor: Boolean
    get() = isNightPhase()

internal fun Phase.isNightPhase(): Boolean =
    this == Phase.NIGHT_GUARD || this == Phase.NIGHT_WOLF_CHAT ||
        this == Phase.NIGHT_WOLF || this == Phase.NIGHT_SEER || this == Phase.NIGHT_WITCH ||
        // 与 WerewolfProgress.isNight 保持一致：汇聚 / 结算阶段也是夜间，
        // 参与局走 nightStageLabel，不给阶段名和座位（DAWN_SETTLE 显示「夜间进行中」而非
        // 「天亮结算」，免得在死讯播报前先暗示「要死人了」）。
        this == Phase.NIGHT_PENDING || this == Phase.DAWN_SETTLE

/**
 * 这个阶段里，**本人**是不是正在行动的那一方。
 *
 * 夜里一个阶段至多一个对应角色在动（守卫 / 预言家 / 女巫），狼人则是整队。
 * 用身份判断就够：阶段会走到，就说明那个角色还在场上。
 */
internal fun nightActorIsSelf(phase: Phase, me: WerewolfPlayer?): Boolean = when (phase) {
    Phase.NIGHT_GUARD -> me?.role == WerewolfRole.GUARD
    Phase.NIGHT_WOLF_CHAT, Phase.NIGHT_WOLF -> me?.role?.isWerewolf == true
    Phase.NIGHT_SEER -> me?.role == WerewolfRole.SEER
    Phase.NIGHT_WITCH -> me?.role == WerewolfRole.WITCH
    else -> false
}

/**
 * 参与局的夜里，顶栏那一行（副标题）该写什么。
 *
 * 夜里的「谁在做什么」是秘密，而且泄得比想象中狠：
 * - 「守卫守护」「女巫用药」＝把这个角色在场报给全场；
 * - 「狼人商议 1/3」＝把**狼的数量**直接报给全场——平民扫一眼就知道这局有几匹狼，
 *   等于开局就把最关键的一条信息白送出去。
 *
 * 所以参与局的夜里只给两种文案：
 * - 本人正处在这一阶段 → 阶段名（[progress] 在手就连进度一起给）。
 *   他本来就知道自己是什么角色，这句不泄露新信息；而且他需要知道自己正卡在
 *   「下刀」「用药」这样的环节里，不然不知道该做什么。
 * - 别人 → 一句中性的「夜间进行中」，阶段名和人数一个字都不给。
 *
 * 观战和已结束是上帝视角，阶段名照给（调用处直接走原路径，不进这里）。
 */
internal fun nightStageLabel(
    phase: Phase,
    me: WerewolfPlayer?,
    /** 引擎此刻的进度。给了就连「第几个 / 共几个」一起报。 */
    progress: WerewolfProgress? = null,
): String = when {
    nightActorIsSelf(phase, me) -> progress?.stageText ?: phase.displayName
    else -> NIGHT_FILLER
}

/**
 * 参与局的夜里，对「不是这一阶段行动者」只能给这一句。
 */
internal const val NIGHT_FILLER = "夜间进行中"

/**
 * 超时提示那句话。
 *
 * **夜里的超时不能点名**。夜里的「决定」只有守卫 / 预言家 / 女巫 / 狼人做，
 * 所以「5 号的决定超出 60 秒」等于当着全场的面报身份：参与者只要盯着这一条，
 * 就知道刚才那个座位是夜里在动的人。
 *
 * 所以：
 * - 观战（[humanSeat] 为 null）→ 上帝视角，照旧点名；
 * - 参与局的夜里、超时的不是本人 → 只说一句中性话，不给座位、不给类型；
 * - 其余（白天、超时的是本人）→ 照旧点名。
 */
internal fun timeoutNoticeText(
    timeout: TurnTimeout,
    /** 座位号 → 名字。找不到就只报座位号。 */
    nameOf: (Int) -> String?,
    /** 玩家自己的座位。null = 观战。 */
    humanSeat: Int?,
): String {
    val nameless = timeout.phase.isNightPhase() && humanSeat != null && timeout.seat != humanSeat
    if (nameless) {
        return "刚才有一步超出 ${timeout.limitSeconds} 秒时限，已按兜底继续"
    }
    val player = nameOf(timeout.seat)
    val who = if (player != null) "${timeout.seat} 号 $player" else "${timeout.seat} 号"
    val what = if (timeout.kind == TurnKind.SPEECH) "发言" else "决定"
    return if (timeout.thinkingDisabled) {
        "$who 的$what 超出 ${timeout.limitSeconds} 秒时限，已按兜底继续；连续超时，本局不再给它开深度思考"
    } else {
        "$who 的$what 超出 ${timeout.limitSeconds} 秒时限，已按兜底继续（再次连续超时将自动关闭深度思考）"
    }
}

internal fun DeathCause.toFate(): UIWerewolfFate = when (this) {
    DeathCause.WOLF_KILL -> UIWerewolfFate.WOLF_KILL
    DeathCause.WITCH_POISON -> UIWerewolfFate.WITCH_POISON
    DeathCause.EXILE -> UIWerewolfFate.EXILE
    DeathCause.HUNTER_SHOOT -> UIWerewolfFate.HUNTER_SHOOT
    DeathCause.SELF_EXPLODE -> UIWerewolfFate.SELF_EXPLODE
}

private val SHERIFF_SEAT_NUMBER = Regex("""\d+""")

/**
 * 把上警报名的系统播报（「上警的是 1、4、5 号」）还原成结构化的上警名单卡数据，
 * 供牌桌记录流与演出回放渲染「警上竞选 + 警下投票」座位气泡列表。
 *
 * 优先读 [com.feature.werewolf.game.GameEvent.SheriffSignup]；老存档或单测构造的播报
 * 若历史里没对应事件，则从播报文案兜底解析座位号。
 */
internal fun buildSheriffRoster(
    message: WerewolfMessage,
    game: com.feature.werewolf.game.WerewolfGame,
    humanSeat: Int?,
): com.ui.werewolf.data.UIWerewolfSheriffRoster? {
    if (message.kind != MessageKind.DIVINE) return null
    if (message.phase != Phase.DAY_SHERIFF_SIGNUP) return null
    if (!message.text.contains("上警的是")) return null

    val runnerSeats = game.history
        .filterIsInstance<com.feature.werewolf.game.GameEvent.SheriffSignup>()
        .firstOrNull { it.day == message.day }
        ?.runners
        ?: SHERIFF_SEAT_NUMBER.findAll(message.text.substringAfter("上警的是"))
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
    if (runnerSeats.isEmpty()) return null

    val runnerSet = runnerSeats.toSet()
    val playersBySeat = game.players.associateBy { it.seat }

    // 上警发生在当天死讯公布之前，所以只有「此前天数已出局」的座位不参与当天报名
    val deadBeforeDay = buildSet {
        game.history.forEach { event ->
            if (event.day >= message.day) return@forEach
            when (event) {
                is com.feature.werewolf.game.GameEvent.Dawn -> addAll(event.deadSeats)
                is com.feature.werewolf.game.GameEvent.Exiled -> add(event.seat)
                is com.feature.werewolf.game.GameEvent.WolfExploded -> add(event.seat)
                is com.feature.werewolf.game.GameEvent.HunterShot -> event.targetSeat?.let { add(it) }
                else -> Unit
            }
        }
    }

    fun toRosterSeat(seat: Int): com.ui.werewolf.data.UIWerewolfRosterSeat {
        val name = playersBySeat[seat]?.name ?: "${seat}号"
        return com.ui.werewolf.data.UIWerewolfRosterSeat(
            seat = seat,
            name = name,
            isSelf = seat == humanSeat,
        )
    }

    val runners = runnerSeats.distinct().sorted().map(::toRosterSeat)
    val offPodium = game.players
        .asSequence()
        .map { it.seat }
        .filter { it !in runnerSet && it !in deadBeforeDay }
        .sorted()
        .map(::toRosterSeat)
        .toList()

    return com.ui.werewolf.data.UIWerewolfSheriffRoster(
        runners = runners,
        offPodium = offPodium,
    )
}
