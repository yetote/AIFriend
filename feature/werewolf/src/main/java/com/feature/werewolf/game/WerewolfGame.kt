package com.feature.werewolf.game

import kotlin.random.Random

/**
 * 一局狼人杀的**权威状态**。
 *
 * 设计上的三条硬约束，改代码前请先读：
 *
 * 1. **规则只在这里判定。** 谁死、谁赢、票数怎么算，全部由本类算完。模型只负责「说什么」
 *    和「投谁」，绝不让模型参与结算——它算错一次，整局就废了。
 * 2. **不可变。** 每个转换函数都 `return copy(...)` 或返回新对象，调用方拿到的旧引用永远
 *    代表那一刻的局面。编排层因此可以随时快照存档、断点续玩。
 * 3. **公开与私有两分。** [history] 只装 [GameEvent]（上帝会宣布的事），预言家查验结果在
 *    [seerChecks]、女巫药品在 [witch]、狼人刀人的选择在 [tonightVictim]——这些是私有信息，
 *    拼上下文时必须按角色过滤。
 *
 * 随机性只出现在 [deal] 的洗牌里（外部传 [Random]），其余转换全部确定，
 * 所以同样的行动序列永远得到同样的结果，可以直接写断言。
 */
data class WerewolfGame(
    val config: WerewolfConfig,
    val players: List<WerewolfPlayer>,
    /** 当前天数。夜晚 N 先于白天 N，两者共用同一个值。 */
    val day: Int = 1,
    val phase: Phase = Phase.NIGHT_WOLF,
    val witch: WitchStock = WitchStock(),
    /** 本夜各狼投给谁。空刀或未行动时为空。 */
    val wolfVotes: Map<Int, Int> = emptyMap(),
    /** 狼人商议轮次，平票后加商一次。 */
    val wolfVoteRound: Int = 1,
    /** 本夜狼人最终决定刀谁。null 表示空刀。 */
    val tonightVictim: Int? = null,
    /** 本夜守卫守了谁。null 表示空守。 */
    val tonightGuardedSeat: Int? = null,
    /**
     * 上一夜守卫守的人。
     *
     * 守卫不能连续两晚守同一个人，这个限制只能靠「记住昨夜」实现。放在状态里而不是
     * 放在 [nights] 记录里，是因为它还活着的时候就要用来算候选，不能等复盘。
     */
    val lastGuardedSeat: Int? = null,
    /** 白痴是否已经翻过牌。翻牌之后他没有投票权，也不能再被投。 */
    val idiotRevealed: Boolean = false,
    /** 预言家历史查验，私有信息。 */
    val seerChecks: List<SeerCheck> = emptyList(),
    val voteRound: Int = 1,
    /** 平票加投时只允许投给平票的这几个人。null 表示全员可投。 */
    val voteCandidates: List<Int>? = null,
    val ballots: List<Ballot> = emptyList(),
    /** 待开枪的猎人座位。非 null 时 [phase] 必为 [Phase.HUNTER_SHOOT]。 */
    val pendingHunterSeat: Int? = null,
    /**
     * 猎人开枪结算完之后去哪。天亮时猎人被刀，后面接着白天讨论；
     * 白天被票出，后面直接进入下一夜。两者不能用同一个默认值。
     */
    val afterHunterPhase: Phase = Phase.NIGHT_WOLF,
    /** 公开事件流，可以安全地给任何玩家看。 */
    val history: List<GameEvent> = emptyList(),
    val winner: Faction? = null,
    /**
     * 每夜的结算记录。**规则一行都不读它**，纯粹为了复盘。
     *
     * 刀口、用药这些是私有的，结算完就会被下一夜覆盖（[tonightVictim] 会被清空），
     * 事后从公开播报里也推不出来，所以必须在结算那一刻抄下来。
     */
    val nights: List<NightRecord> = emptyList(),
    /**
     * 逐轮投票历史。**规则不读它**。
     *
     * [ballots] 只装当前这一轮：进下一夜会清空，加投时会累加，两种语义混在一起，
     * 事后没法还原「第一天投了几轮、每轮是谁投的谁」。复盘需要这个还原，所以另存一份。
     */
    val voteRounds: List<VoteRound> = emptyList(),
) {
    val alivePlayers: List<WerewolfPlayer> get() = players.filter { it.alive }
    val aliveSeats: List<Int> get() = alivePlayers.map { it.seat }
    val isFinished: Boolean get() = winner != null

    fun playerAt(seat: Int): WerewolfPlayer =
        players.firstOrNull { it.seat == seat } ?: throw IllegalArgumentException("没有 $seat 号玩家")

    fun isWerewolf(seat: Int): Boolean = playerAt(seat).role == WerewolfRole.WEREWOLF

    /** 全部狼人，含已出局的。判胜负时用 [aliveWolves]。 */
    fun wolves(): List<WerewolfPlayer> = players.filter { it.role == WerewolfRole.WEREWOLF }

    fun aliveWolves(): List<WerewolfPlayer> = alivePlayers.filter { it.role == WerewolfRole.WEREWOLF }

    /**
     * 女巫此刻能否使用解药。药还在、并且今晚确实有人被刀，两个条件都满足才行。
     *
     * 编排层先用这个判断再调 [witchAct]，就能避开「空刀夜强行用药」这条异常路径。
     */
    val witchCanHeal: Boolean get() = witch.antidoteAvailable && tonightVictim != null

    /** 女巫此刻能否使用毒药。药还在，且场上还有别人可以毒。 */
    val witchCanPoison: Boolean
        get() = witch.poisonAvailable && aliveSeats.size > 1

    /**
     * 守卫今晚能守的人：所有存活玩家，除了他昨夜守过的那一个。
     *
     * 允许守自己——线下规则如此，而且守卫守自己是「浪费一次守护」的常见判断，
     * 不该由引擎替他决定。
     */
    val guardTargets: List<Int> get() = aliveSeats.filter { it != lastGuardedSeat }

    /**
     * 有资格投票的座位。
     *
     * 翻过牌的白痴不在里面：他已经亮明身份、不能再影响票数。**被投的候选也一并排除**，
     * 否则会出现「投他→翻牌→再投他」的死循环，一个永远出不了局的座位会把白天拖住。
     */
    val votingSeats: List<Int>
        get() = alivePlayers
            .filter { !(it.role == WerewolfRole.IDIOT && idiotRevealed) }
            .map { it.seat }

    // ---------------------------------------------------------------------
    // 夜晚
    // ---------------------------------------------------------------------

    /**
     * 守卫守护。先于狼人，所以他看不到刀口，只能盲守。
     *
     * [targetSeat] 为 null 表示空守——空守是合法动作，也是「这一夜没什么可守」时的正解，
     * 不该强迫 AI 随便挑一个人。
     */
    fun guardProtect(targetSeat: Int?): WerewolfGame {
        require(phase == Phase.NIGHT_GUARD) { "当前不是守卫行动阶段：$phase" }
        check(hasAliveRole(WerewolfRole.GUARD)) { "没有存活的守卫" }
        if (targetSeat != null) {
            require(targetSeat in guardTargets) {
                if (targetSeat == lastGuardedSeat) {
                    "守卫不能连续两夜守同一个人（$targetSeat 号）"
                } else {
                    "只能守存活玩家，$targetSeat 号不在可守列表"
                }
            }
        }
        val next = copy(tonightGuardedSeat = targetSeat)
        // 狼人一定在场上（判胜负会先把狼全灭的局结束掉），所以直接进狼人商议
        return next.copy(phase = Phase.NIGHT_WOLF_CHAT)
    }

    /**
     * 商议结束，开始下刀。
     *
     * 之所以要显式一步而不是「商议里说完就自动进下刀」：商议阶段每只狼都可能只表态
     * 不改状态，把阶段推进交给编排层，存档读回来时才知道该从哪一步接着走。
     */
    fun beginWolfVote(): WerewolfGame {
        require(phase == Phase.NIGHT_WOLF_CHAT) { "当前不是狼人商议阶段：$phase" }
        return copy(phase = Phase.NIGHT_WOLF)
    }

    /**
     * 平票之后回到商议。
     *
     * 是真的把阶段退回去，而不是留在下刀阶段再问一次：提示词、进度文案、
     * 界面上的阶段条全都跟着 `phase` 走，只把话问出去而不退阶段，
     * 模型会看到「现在定刀」的指令和「再商量一次」的问题，两句话互相打架。
     */
    fun reopenWolfChat(): WerewolfGame {
        require(phase == Phase.NIGHT_WOLF) { "当前不是狼人下刀阶段：$phase" }
        return copy(phase = Phase.NIGHT_WOLF_CHAT)
    }

    /**
     * 狼人投票。
     *
     * 规则：存活狼人**全部**必须表态（键集合必须等于存活狼人座位），多数决。
     * 平票则加商一次；加商仍平票视为意见不合，本夜空刀——这比「随机挑一个」更可解释，
     * 也给了狼人一个真实的博弈失败后果。
     */
    fun wolfVote(votes: Map<Int, Int>): WerewolfGame {
        require(phase == Phase.NIGHT_WOLF) { "当前不是狼人行动阶段：$phase" }
        val wolfSeats = aliveWolves().map { it.seat }.toSet()
        require(votes.keys == wolfSeats) {
            "需要全部存活狼人投票，期望 $wolfSeats，实际 ${votes.keys}"
        }
        val aliveSet = aliveSeats.toSet()
        votes.forEach { (wolf, target) ->
            require(target in aliveSet) { "$wolf 号狼人指向了非存活玩家 $target 号" }
        }

        val tally = votes.values.groupingBy { it }.eachCount()
        val max = tally.values.max()
        val top = tally.filterValues { it == max }.keys.sorted()

        if (top.size == 1) return advanceAfterWolves(votes, top.first())

        if (wolfVoteRound < MAX_WOLF_VOTE_ROUNDS) {
            return copy(wolfVotes = votes, wolfVoteRound = wolfVoteRound + 1)
        }
        return advanceAfterWolves(votes, victim = null)
    }

    /** 预言家查验。结果只进私有记录，不产生公开事件。 */
    fun seerCheck(targetSeat: Int): WerewolfGame {
        require(phase == Phase.NIGHT_SEER) { "当前不是预言家行动阶段：$phase" }
        val seer = alivePlayers.firstOrNull { it.role == WerewolfRole.SEER }
            ?: throw IllegalStateException("没有存活的预言家")
        require(targetSeat in aliveSeats) { "只能查验存活玩家，$targetSeat 号不在存活列表" }
        require(targetSeat != seer.seat) { "预言家不能查验自己" }

        val check = SeerCheck(day = day, targetSeat = targetSeat, isWerewolf = isWerewolf(targetSeat))
        return copy(seerChecks = seerChecks + check).advanceAfterSeer()
    }

    /**
     * 女巫行动。返回时天已亮，死亡已结算。
     *
     * 规则：解药毒药各限一次，**同一夜不能同时使用**；解药不能救自己（第一夜按配置可例外）；
     * 不能毒自己。守卫守中的人即使被刀也不会死，解药对他其实是浪费——但引擎不拦，
     * 因为「女巫不知道守卫守了谁」本来就是信息不对称的一部分。
     */
    fun witchAct(heal: Boolean, poisonTarget: Int? = null): WerewolfGame {
        require(phase == Phase.NIGHT_WITCH) { "当前不是女巫行动阶段：$phase" }
        val witchPlayer = alivePlayers.firstOrNull { it.role == WerewolfRole.WITCH }
            ?: throw IllegalStateException("没有存活的女巫")
        require(!(heal && poisonTarget != null)) { "同一夜不能同时使用解药和毒药" }

        if (heal) {
            require(witch.antidoteAvailable) { "解药已经用过了" }
            val victim = tonightVictim ?: throw IllegalStateException("今晚无人被刀，无需使用解药")
            if (victim == witchPlayer.seat) {
                require(day == 1 && config.witchCanSelfHealOnFirstNight) {
                    "女巫只能在第一夜自救，现在是第 $day 夜"
                }
            }
        }
        if (poisonTarget != null) {
            require(witch.poisonAvailable) { "毒药已经用过了" }
            require(poisonTarget in aliveSeats) { "只能毒存活玩家，$poisonTarget 号不在存活列表" }
            require(poisonTarget != witchPlayer.seat) { "女巫不能毒自己" }
        }

        val deaths = buildList {
            wolfKillDeath(healed = heal)?.let { add(it) }
            // 毒药无视守护：守卫挡的是刀，不是药
            if (poisonTarget != null) add(poisonTarget to DeathCause.WITCH_POISON)
        }
        val stock = WitchStock(
            antidoteAvailable = witch.antidoteAvailable && !heal,
            poisonAvailable = witch.poisonAvailable && poisonTarget == null,
        )
        return settleNight(deaths, stock, healed = heal)
    }

    // ---------------------------------------------------------------------
    // 白天
    // ---------------------------------------------------------------------

    /** 讨论结束，进入投票。 */
    fun beginVote(): WerewolfGame {
        require(phase == Phase.DAY_DISCUSSION) { "当前不是白天讨论阶段：$phase" }
        return copy(phase = Phase.DAY_VOTE, voteRound = 1, voteCandidates = null, ballots = emptyList())
    }

    /**
     * 投票。有投票权的玩家**全部**必须表态，弃票传 null。
     *
     * 平票时若还有加投额度，则把候选人收窄到平票的那几个人并留在 [Phase.DAY_VOTE]，
     * 调用方看到 `voteRound` 增加就知道要再收一轮票；额度用尽仍平票则无人出局。
     */
    fun castVotes(votes: Map<Int, Int?>): WerewolfGame {
        require(phase == Phase.DAY_VOTE) { "当前不是投票阶段：$phase" }
        val living = votingSeats.toSet()
        require(votes.keys == living) { "所有有投票权的玩家都要投票，弃票传 null。期望 $living，实际 ${votes.keys}" }

        val candidates = (voteCandidates ?: votingSeats).toSet()
        votes.forEach { (voter, target) ->
            if (target != null) {
                require(target in candidates) { "$voter 号投给了不在候选范围的 $target 号" }
            }
        }

        val recorded = ballots + votes.map { Ballot(it.key, it.value) }
        val thisRound = votes.map { Ballot(it.key, it.value) }
        val tally = votes.values.filterNotNull().groupingBy { it }.eachCount()
        if (tally.isEmpty()) {
            // 全员弃票：直接无人出局，没有加投的意义
            return finishVote(
                recorded = recorded,
                exiled = null,
                voteCount = 0,
                round = thisRound.toRound(VoteOutcome.NO_EXILE),
            )
        }

        val max = tally.values.max()
        val top = tally.filterValues { it == max }.keys.sorted()
        if (top.size > 1) {
            return if (voteRound < config.maxVoteRounds) {
                copy(
                    ballots = recorded,
                    voteRound = voteRound + 1,
                    voteCandidates = top,
                    // 平票只是「这一天还没结束」，单独记一轮 TIE，复盘才能看出投了两次
                    voteRounds = voteRounds + thisRound.toRound(VoteOutcome.TIE),
                )
            } else {
                finishVote(
                    recorded = recorded,
                    exiled = null,
                    voteCount = 0,
                    round = thisRound.toRound(VoteOutcome.NO_EXILE),
                )
            }
        }
        return finishVote(
            recorded = recorded,
            exiled = top.first(),
            voteCount = max,
            round = thisRound.toRound(
                outcome = VoteOutcome.EXILED,
                exiledSeat = top.first(),
                exileVotes = max,
            ),
        )
    }

    /** 把这一轮的票封成一条历史。票型是纯数据，不留对当时局面的引用。 */
    private fun List<Ballot>.toRound(
        outcome: VoteOutcome,
        exiledSeat: Int? = null,
        exileVotes: Int = 0,
    ): VoteRound = VoteRound(
        day = day,
        round = voteRound,
        ballots = this,
        outcome = outcome,
        exiledSeat = exiledSeat,
        exileVotes = exileVotes,
    )

    /** 猎人决定是否开枪。[targetSeat] 为 null 表示放弃。 */
    fun hunterShoot(targetSeat: Int?): WerewolfGame {
        require(phase == Phase.HUNTER_SHOOT) { "当前不是猎人开枪阶段：$phase" }
        val hunter = pendingHunterSeat ?: throw IllegalStateException("没有待开枪的猎人")
        if (targetSeat != null) {
            require(targetSeat in aliveSeats) { "只能带走存活玩家，$targetSeat 号不在存活列表" }
        }

        var next = copy(history = history + GameEvent.HunterShot(day, hunter, targetSeat))
        if (targetSeat != null) {
            next = next.applyDeaths(listOf(targetSeat to DeathCause.HUNTER_SHOOT))
            val winnerFaction = next.evaluateWinner()
            if (winnerFaction != null) {
                return next.copy(
                    phase = Phase.FINISHED,
                    winner = winnerFaction,
                    pendingHunterSeat = null,
                    history = next.history + GameEvent.GameOver(next.day, winnerFaction),
                )
            }
        }
        return next.afterHunter()
    }

    // ---------------------------------------------------------------------
    // 内部流转
    // ---------------------------------------------------------------------

    private fun advanceAfterWolves(votes: Map<Int, Int>, victim: Int?): WerewolfGame {
        val next = copy(wolfVotes = votes, tonightVictim = victim)
        return if (next.hasAliveRole(WerewolfRole.SEER)) {
            next.copy(phase = Phase.NIGHT_SEER)
        } else {
            next.advanceAfterSeer()
        }
    }

    private fun advanceAfterSeer(): WerewolfGame =
        if (hasAliveRole(WerewolfRole.WITCH)) {
            copy(phase = Phase.NIGHT_WITCH)
        } else {
            // 没有女巫：没人能用药，狼刀（扣掉守卫挡下的那一刀）直接生效。
            settleNight(listOfNotNull(wolfKillDeath(healed = false)), witch)
        }

    /**
     * 这一夜的狼刀是否真的造成了死亡。
     *
     * 两种情况会让它落空：女巫用了解药、守卫守中了同一个人。
     *
     * **同守同救按「活下来」处理**，这是本引擎选择的简化规则：两种保护叠加时如果反而死，
     * 玩家就得同时记住「谁能救」「谁和谁叠加会失效」两件事，AI 更难推理，而且这种死法
     * 在播报里看起来和「守错了」一模一样，复盘也解释不清。所以宁可让它按直觉走。
     */
    private fun wolfKillDeath(healed: Boolean): Pair<Int, DeathCause>? {
        val victim = tonightVictim ?: return null
        if (healed) return null
        if (victim == tonightGuardedSeat) return null
        return victim to DeathCause.WOLF_KILL
    }

    /**
     * 结算天亮。
     *
     * [healed] 必须由调用方传入，不能靠「刀口没死」反推：万一女巫毒的人和狼刀的人是同一个，
     * 那个人照样会死，反推就会把「用过解药」记成「没用」，复盘里那一夜就解释不通了。
     * 同样的道理，[NightRecord.guarded] 也照抄本夜的守护，而不是「谁没死所以守对了」。
     */
    private fun settleNight(
        deaths: List<Pair<Int, DeathCause>>,
        stock: WitchStock,
        healed: Boolean = false,
    ): WerewolfGame {
        var next = copy(witch = stock).applyDeaths(deaths)
        val deadSeats = deaths.map { it.first }.sorted()
        next = next.copy(
            history = next.history + GameEvent.Dawn(next.day, deadSeats),
            nights = nights + NightRecord(
                day = day,
                wolfTarget = tonightVictim,
                guarded = tonightGuardedSeat,
                healed = healed,
                poisonTarget = deaths.firstOrNull { it.second == DeathCause.WITCH_POISON }?.first,
                deaths = deadSeats,
            ),
        )

        val winnerFaction = next.evaluateWinner()
        if (winnerFaction != null) {
            return next.copy(
                phase = Phase.FINISHED,
                winner = winnerFaction,
                history = next.history + GameEvent.GameOver(next.day, winnerFaction),
            )
        }

        // 只有被狼刀死的猎人可以开枪，被毒的不能。
        val fallenHunter = deaths.firstOrNull { (seat, cause) ->
            cause == DeathCause.WOLF_KILL && next.playerAt(seat).role == WerewolfRole.HUNTER
        }?.first

        return if (fallenHunter != null) {
            next.copy(
                phase = Phase.HUNTER_SHOOT,
                pendingHunterSeat = fallenHunter,
                afterHunterPhase = Phase.DAY_DISCUSSION,
            )
        } else {
            next.copy(phase = Phase.DAY_DISCUSSION)
        }
    }

    private fun finishVote(
        recorded: List<Ballot>,
        exiled: Int?,
        voteCount: Int,
        round: VoteRound,
    ): WerewolfGame {
        if (exiled == null) {
            return copy(
                ballots = recorded,
                voteRounds = voteRounds + round,
                history = history + GameEvent.NoExile(day),
            ).startNextNight()
        }

        // 白痴第一次被票：翻开身份，不死，但从这一刻起失去投票权。
        // 这不是「免死一次」而是「票不死」——夜里被刀、被毒照样会死。
        if (playerAt(exiled).role == WerewolfRole.IDIOT && !idiotRevealed) {
            return copy(
                ballots = recorded,
                voteRounds = voteRounds + round.copy(outcome = VoteOutcome.IDIOT_REVEALED),
                idiotRevealed = true,
                history = history + GameEvent.IdiotRevealed(day, exiled, voteCount),
            ).startNextNight()
        }

        var next = copy(ballots = recorded, voteRounds = voteRounds + round)
            .applyDeaths(listOf(exiled to DeathCause.EXILE))
        next = next.copy(history = next.history + GameEvent.Exiled(next.day, exiled, voteCount))

        val winnerFaction = next.evaluateWinner()
        if (winnerFaction != null) {
            return next.copy(
                phase = Phase.FINISHED,
                winner = winnerFaction,
                history = next.history + GameEvent.GameOver(next.day, winnerFaction),
            )
        }

        return if (next.playerAt(exiled).role == WerewolfRole.HUNTER) {
            next.copy(
                phase = Phase.HUNTER_SHOOT,
                pendingHunterSeat = exiled,
                afterHunterPhase = Phase.NIGHT_WOLF,
            )
        } else {
            next.startNextNight()
        }
    }

    private fun afterHunter(): WerewolfGame =
        if (afterHunterPhase == Phase.NIGHT_WOLF) {
            copy(pendingHunterSeat = null).startNextNight()
        } else {
            copy(pendingHunterSeat = null, phase = Phase.DAY_DISCUSSION)
        }

    private fun startNextNight(): WerewolfGame = copy(
        day = day + 1,
        // 有活着的守卫就先走守护阶段。守卫死了就不该再由他行动，
        // 这个判断每夜现算，不能只看开局配置——否则守卫出局后全队会卡在一个永远不动的阶段
        phase = if (hasAliveRole(WerewolfRole.GUARD)) Phase.NIGHT_GUARD else Phase.NIGHT_WOLF_CHAT,
        // 本夜的守护变成「昨夜」，用来限制守卫不能连守同一人；空守就等于没有限制
        lastGuardedSeat = tonightGuardedSeat,
        tonightGuardedSeat = null,
        wolfVotes = emptyMap(),
        wolfVoteRound = 1,
        tonightVictim = null,
        voteRound = 1,
        voteCandidates = null,
        ballots = emptyList(),
        pendingHunterSeat = null,
    )

    private fun applyDeaths(deaths: List<Pair<Int, DeathCause>>): WerewolfGame {
        if (deaths.isEmpty()) return this
        val bySeat = deaths.toMap()
        return copy(
            players = players.map { player ->
                val cause = bySeat[player.seat]
                if (cause == null) {
                    player
                } else {
                    player.copy(alive = false, deathCause = cause, deathDay = day)
                }
            },
        )
    }

    /**
     * 判胜负。狼人全灭优先于一切——哪怕同一刻神职也全死了，也应该判好人赢。
     */
    private fun evaluateWinner(): Faction? {
        val aliveWolfCount = players.count { it.alive && it.role == WerewolfRole.WEREWOLF }
        if (aliveWolfCount == 0) return Faction.GOOD

        return when (config.winRule) {
            WinRule.SLAY_ALL -> {
                val aliveGoodCount = players.count { it.alive && it.faction == Faction.GOOD }
                if (aliveWolfCount >= aliveGoodCount) Faction.WEREWOLF else null
            }

            WinRule.SLAY_SIDE -> {
                // 只看「开局存在过的那一类」，避免 8 人局里没有平民时误判村民全灭。
                val hadGods = players.any { it.role.isGod }
                val hadVillagers = players.any { it.role == WerewolfRole.VILLAGER }
                val godsAlive = players.any { it.alive && it.role.isGod }
                val villagersAlive = players.any { it.alive && it.role == WerewolfRole.VILLAGER }
                if ((hadGods && !godsAlive) || (hadVillagers && !villagersAlive)) Faction.WEREWOLF else null
            }
        }
    }

    private fun hasAliveRole(role: WerewolfRole): Boolean = alivePlayers.any { it.role == role }

    companion object {
        /** 狼人平票后最多商议几轮。第二轮再平票就空刀。 */
        const val MAX_WOLF_VOTE_ROUNDS = 2

        /**
         * 洗牌发身份，开局。有守卫的板子从守卫阶段开始，否则从狼人行动开始。
         *
         * 洗牌用的 [Random] 由外部传入，测试里传 `Random(seed)` 就能复现同一副牌。
         */
        fun deal(
            config: WerewolfConfig = WerewolfConfig(),
            roster: List<RosterEntry>,
            random: Random = Random.Default,
        ): WerewolfGame = build(config, roster) { roles -> roles.shuffled(random) }

        /**
         * 按给定顺序分配身份，不洗牌。
         *
         * 用于复盘重放和单测——测试需要精确控制谁是什么身份，随机发牌没法写断言。
         */
        fun withRoles(
            config: WerewolfConfig,
            roster: List<RosterEntry>,
            roles: List<WerewolfRole>,
        ): WerewolfGame = build(config, roster) { _ -> roles }

        private inline fun build(
            config: WerewolfConfig,
            roster: List<RosterEntry>,
            arrange: (List<WerewolfRole>) -> List<WerewolfRole>,
        ): WerewolfGame {
            require(roster.size == config.playerCount) {
                "座位数 ${roster.size} 与配置的玩家数 ${config.playerCount} 不一致"
            }
            require(roster.map { it.id }.distinct().size == roster.size) { "玩家 id 不能重复" }

            val pool = config.composition.flatMap { (role, count) -> List(count) { role } }
            val arranged = arrange(pool)
            require(arranged.size == roster.size) {
                "身份数量 ${arranged.size} 与座位数 ${roster.size} 不一致"
            }
            require(arranged.groupingBy { it }.eachCount() == config.composition.filterValues { it > 0 }) {
                "身份构成与配置不符：${arranged.groupingBy { it }.eachCount()} vs ${config.composition}"
            }

            val players = roster.mapIndexed { index, entry ->
                WerewolfPlayer(seat = index + 1, id = entry.id, name = entry.name, role = arranged[index])
            }
            // 有守卫的板子从守护阶段入夜，否则狼人先动——守卫必须盲守，这是规则不是实现细节
            val opening = if (config.has(WerewolfRole.GUARD)) {
                Phase.NIGHT_GUARD
            } else {
                Phase.NIGHT_WOLF_CHAT
            }
            return WerewolfGame(config = config, players = players, phase = opening)
        }
    }
}
