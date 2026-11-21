package com.feature.werewolf.route

// 实时局面 → 牌桌的映射：座位状态、高亮、气泡分类、进度文案、身份揭露。

import com.feature.werewolf.engine.HumanTurn
import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfRole
import com.ui.werewolf.data.UIWerewolfFate
import com.ui.werewolf.data.UIWerewolfLine
import com.ui.werewolf.data.UIWerewolfLineKind
import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfSeatLive
import com.ui.werewolf.data.UIWerewolfStreaming

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
            (me?.role?.isWerewolf == true && phase == Phase.NIGHT_WOLF_CHAT)
    }
    val actorSeat = progress?.seat
        ?.takeIf { onStage }
        ?.takeIf { seat -> actorVisible(seat, progress.phase) }

    // 并发阶段（投票、上警报名）正在请求的是**一批**座位，不是一个。
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
            wolf = player.role.isWerewolf,
            active = player.seat in litSeats,
            votes = voteCounts[player.seat],
            // 警长是公开身份，牌桌上必须一直挂着——它是「谁的一票值 1.5」的唯一提示
            sheriff = game.sheriffSeat == player.seat,
            isWolfMate = player.seat in wolfMateSeats,
        )
    }.orEmpty()

    // 先在**原始** transcript 上截断，再按视角过滤私有消息。
    // 顺序不能反：transcriptFor 会剔掉私有消息导致下标重排，拿过滤后的下标
    // 去截原始进度会错位，把别人的话放出来或把自己的话藏住。
    val visible = emergedVisible(table?.transcript.orEmpty(), revealed, humanSeat)

    val lines = visible.mapIndexed { index, message ->
        val speaker = message.seat?.let { seat -> game?.players?.firstOrNull { it.seat == seat } }
        UIWerewolfLine(
            index = index,
            kind = lineKindOf(message.kind),
            seat = message.seat,
            speaker = speaker?.name,
            avatarPath = speaker?.id?.let { avatars[it] },
            text = message.text,
            innerLabel = innerLabelOf(message),
            secret = !message.isPublic,
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
    // 4. **思考过程给不给看**——参与局默认不给（别人推理=作弊），观战/已结束一律给；
    //    参与局里自己打开「思考」开关也就有了，界面上会写清楚那是作弊。
    //    开关只放行「想的内容」，不放行「谁在动」：夜里的思考框照样受第 1 条管，
    //    否则一开开关就等于把每个角色的身份摊在桌上。
    val streaming = live
        ?.takeIf { running && !finished }
        ?.takeIf { item -> actorVisible(item.seat, item.phase) && item.seat != humanSeat }
        ?.takeIf { !gated || (!speechBusy && revealed >= (table?.transcript?.lastIndex ?: -1)) }
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
        phaseLabel = moment.phase?.displayName ?: "准备中",
        night = moment.phase?.let { it.isNightPhase() } ?: false,
        running = running,
        finished = finished,
        winnerLabel = game?.winner?.let { "${it.displayName}胜" },
        // 舞台还停在过去时，「轮到谁、走到第几个」属于未来的阶段，一律不显示。
        // 「谁在忙」并进这一行（「发言 3/8 · 5 号」），天数不重复：顶栏标题已经在报。
        // 夜里只给不带座位的那版：夜里谁在动就等于把身份摊给好人看。
        stageLabel = progress?.takeIf { onStage }?.let { p ->
            if (p.phase.isNightPhase()) p.stageText else p.stageLabel
        },
        seats = seats,
        lines = lines,
        streaming = streaming,
        selfSeat = humanSeat,
        selfRoleLabel = me?.role?.displayName,
        selfFactionLabel = me?.role?.faction?.displayName,
        spectating = humanSeat == null,
        turn = if (game != null) buildTurn(game, humanSeat, me, turn) else null,
        error = error,
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
    MessageKind.SPEECH -> UIWerewolfLineKind.SPEECH
    MessageKind.WOLF_CHAT -> UIWerewolfLineKind.PRIVATE
    MessageKind.VOTE, MessageKind.NIGHT_ACT -> UIWerewolfLineKind.INNER
}

/**
 * 内心活动的小标签：投票、守人、查验、用药。
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
 * 亮出来就等于把身份说穿。狼人下刀本来就是并发的，没有「当前是谁」，单独排除。
 */
internal val Phase.hidesActor: Boolean
    get() = isNightPhase() && this != Phase.NIGHT_WOLF

internal fun Phase.isNightPhase(): Boolean =
    this == Phase.NIGHT_GUARD || this == Phase.NIGHT_WOLF_CHAT ||
        this == Phase.NIGHT_WOLF || this == Phase.NIGHT_SEER || this == Phase.NIGHT_WITCH

internal fun DeathCause.toFate(): UIWerewolfFate = when (this) {
    DeathCause.WOLF_KILL -> UIWerewolfFate.WOLF_KILL
    DeathCause.WITCH_POISON -> UIWerewolfFate.WITCH_POISON
    DeathCause.EXILE -> UIWerewolfFate.EXILE
    DeathCause.HUNTER_SHOOT -> UIWerewolfFate.HUNTER_SHOOT
    DeathCause.SELF_EXPLODE -> UIWerewolfFate.SELF_EXPLODE
}
