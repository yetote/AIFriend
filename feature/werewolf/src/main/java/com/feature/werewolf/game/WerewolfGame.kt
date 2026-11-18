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
    /** 待开枪的座位（猎人或狼王）。非 null 时 [phase] 必为 [Phase.HUNTER_SHOOT]。 */
    val pendingHunterSeat: Int? = null,
    /**
     * 警长座位。null 表示此刻没有警长：没开警长、还没选出来，或者警徽已经流失。
     *
     * 警长死了之后这个字段**不会立刻清空**——如果他有遗言，警徽要等他在遗言里
     * 指定继任者；指定的那一刻才改。没指定（或根本没遗言）才作废，
     * 由 `dropDeadBadge()` 统一处理。
     */
    val sheriffSeat: Int? = null,
    /**
     * 上警的座位，按座位号排序。第二轮 PK 时会收窄成平票的那几个。
     *
     * 它决定「谁发言」和「谁不能投票」——警上的人不参与警长投票，这是规则。
     */
    val sheriffCandidates: List<Int> = emptyList(),
    /**
     * 上过警的全部座位，PK 收窄 [sheriffCandidates] 时它不变。
     *
     * 单独留一份，是因为「谁上过警」和「这轮谁在竞选」是两件事：
     * PK 只在平票的两人之间进行，但**没进 PK 的其他人依然是警上的人，依然不能投票**。
     * 只留一份的话，第一轮被淘汰的候选人会莫名其妙拿回选票。
     */
    val sheriffRunners: List<Int> = emptyList(),
    /** 警长选举轮次：1 首次投票，2 平票后的 PK。 */
    val sheriffRound: Int = 1,
    /** 警长选举是否已经办过。第 1 天办过一次就不再办，无人上警也算办过。 */
    val sheriffElectionDone: Boolean = false,
    /**
     * 待发表遗言的座位，按顺序处理。
     *
     * 这是**调度队列**，不是规则状态：遗言说什么都不改变局面，
     * 但轮到谁、还要说几个，编排层必须知道，否则天亮之后谁先开口就只能靠猜。
     */
    val pendingLastWords: List<Int> = emptyList(),
    /**
     * 遗言结束后去哪。
     *
     * 三种去处：天亮死者的遗言之后是白天讨论；被放逐者的遗言之后是黑夜；
     * 被放逐的猎人 / 狼王先遗言、再开枪，所以也可能是 [Phase.HUNTER_SHOOT]。
     * 用一个字段而不是按阶段现推，是因为「遗言说完了」这件事本身不携带信息。
     */
    val afterLastWordsPhase: Phase = Phase.DAY_DISCUSSION,
    /**
     * 开枪结算完之后去哪。天亮时被刀，后面接着白天讨论；
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

    fun isWerewolf(seat: Int): Boolean = playerAt(seat).role.isWerewolf

    /** 全部狼人，含已出局的。判胜负时用 [aliveWolves]。狼王也算狼。 */
    fun wolves(): List<WerewolfPlayer> = players.filter { it.role.isWerewolf }

    fun aliveWolves(): List<WerewolfPlayer> = alivePlayers.filter { it.role.isWerewolf }

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
     * 翻过牌的白痴不在里面：他已经亮明身份、不能再影响票数。
     *
     * 要注意「不能投票」和「不能被投」是同一件事的两种说法，但**不是同一处代码**：
     * 这里只负责前者，后者靠 [castVotes] 用 `voteCandidates ?: votingSeats` 收窄候选。
     * 两处都问同一个集合，所以「投他→翻牌→再投他」的循环才闭合——白痴一旦翻开，
     * 既投不了别人，也不再是候选。只改其中一处会重新打开那个死循环。
     */
    val votingSeats: List<Int>
        get() = alivePlayers
            .filter { !(it.role == WerewolfRole.IDIOT && idiotRevealed) }
            .map { it.seat }

    // ---------------------------------------------------------------------
    // 警长与发言顺序
    // ---------------------------------------------------------------------

    /** 还活着的警长。警长死了但警徽还没移交时，这里给 null。 */
    val aliveSheriffSeat: Int?
        get() = sheriffSeat?.takeIf { seat -> playerAt(seat).alive }

    /**
     * 这个座位的一票值几个半点：平民 2 点（1 票），警长 3 点（1.5 票）。
     *
     * 用半点做单位而不是 Double：票数会出现 1.5，如果直接浮点累加，
     * 「3 票 vs 3.5 票」这种比较迟早会栽在 0.30000000000000004 上。
     */
    fun voteWeight(seat: Int): Int = if (seat == sheriffSeat) 3 else 2

    /**
     * 白天发言顺序。
     *
     * 两条线下惯例，按优先级：
     * 1. **有警长从警长下位起**——警长最后一个发言。他是归票位，先说完就等于
     *    把自己的判断摊在桌上，后面的人全在顺着他说。
     * 2. 没警长从**昨夜死者的下位**起。让紧挨着死者的人先开口，是线下最自然的起手，
     *    因为他们最先看到尸体、也最容易被怀疑。
     * 3. 都没有就从 1 号起。
     *
     * 返回的是**从起点绕一圈**的完整顺序（越过最大座位号后回到最小的），
     * 不是「排好序的活人」——差一个绕圈，7 号之后的 1 号就会被漏掉。
     */
    val discussionOrder: List<Int>
        get() {
            val alive = aliveSeats.sorted()
            if (alive.isEmpty()) return emptyList()
            val anchor = aliveSheriffSeat ?: lastDawnDeadSeats.firstOrNull()
            if (anchor == null) return alive
            val start = alive.firstOrNull { it > anchor } ?: alive.first()
            return alive.filter { it >= start } + alive.filter { it < start }
        }

    /** 今天天亮时死的人。只用来定发言起点和遗言，不参与判定。 */
    private val lastDawnDeadSeats: List<Int>
        get() = history.lastOrNull { it is GameEvent.Dawn && it.day == day }
            ?.let { it as GameEvent.Dawn }
            ?.deadSeats
            .orEmpty()

    /** 此刻还有没有遗言没说完。 */
    val hasPendingLastWords: Boolean get() = pendingLastWords.isNotEmpty()

    /** 这个座位此刻能不能自爆：白天的发言环节、活着、且是狼（狼王同样可以）。 */
    fun canExplode(seat: Int): Boolean =
        (phase == Phase.DAY_DISCUSSION || phase == Phase.DAY_SHERIFF_SPEECH) &&
            playerAt(seat).alive &&
            playerAt(seat).role.isWerewolf

    /**
     * 警长此刻能不能移交警徽：必须正在办遗言，并且说话的人就是那位已出局的警长。
     *
     * 只判断「是不是警长的遗言」，不判断他是不是刚死——警徽只在遗言里移交，
     * 这是线下流程，也是唯一能拿到他本人意愿的时机。
     */
    fun canTransferBadge(seat: Int): Boolean =
        phase == Phase.DAY_LAST_WORDS && sheriffSeat == seat && !playerAt(seat).alive

    /** 有资格投警长票的人：活着、且没上过警。 */
    val sheriffVoters: List<Int>
        get() = aliveSeats.filter { it !in sheriffRunners }

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
     * 平票即**无人出局**，当天到此为止：本板子没有警长，没人能追加一轮 PK。
     * 加投只在 [WerewolfConfig.maxVoteRounds] > 1 的变体板子上才会发生——那时这里
     * 把候选人收窄到平票的那几个人并留在 [Phase.DAY_VOTE]，调用方看到 `voteRound`
     * 增加就知道要再收一轮票。
     *
     * 加投时 [ballots] **只装这一轮的票**，不叠加上一轮：上一轮的票已经被「再投一次」
     * 作废了，叠进来的话牌桌上的票数角标会翻倍，而复盘页只摆最后一轮，两边对不上。
     * 逐轮票型由 [voteRounds] 完整保留，复盘要的就是那份。
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

        val thisRound = votes.map { Ballot(it.key, it.value) }
        // 按半点累加：警长的一票算 3 点，普通人 2 点。
        val tally = mutableMapOf<Int, Int>()
        votes.forEach { (voter, target) ->
            if (target != null) tally.merge(target, voteWeight(voter), Int::plus)
        }
        if (tally.isEmpty()) {
            // 全员弃票：直接无人出局，没有加投的意义
            return finishVote(
                thisRound = thisRound,
                exiled = null,
                voteCount = 0.0,
                round = thisRound.toRound(VoteOutcome.NO_EXILE),
            )
        }

        val max = tally.values.max()
        val top = tally.filterValues { it == max }.keys.sorted()
        val maxVotes = max / 2.0
        if (top.size > 1) {
            return if (voteRound < config.maxVoteRounds) {
                copy(
                    ballots = thisRound,
                    voteRound = voteRound + 1,
                    voteCandidates = top,
                    // 平票只是「这一天还没结束」，单独记一轮 TIE，复盘才能看出投了两次
                    voteRounds = voteRounds + thisRound.toRound(VoteOutcome.TIE),
                )
            } else {
                finishVote(
                    thisRound = thisRound,
                    exiled = null,
                    voteCount = 0.0,
                    round = thisRound.toRound(VoteOutcome.NO_EXILE),
                )
            }
        }
        return finishVote(
            thisRound = thisRound,
            exiled = top.first(),
            voteCount = maxVotes,
            round = thisRound.toRound(
                outcome = VoteOutcome.EXILED,
                exiledSeat = top.first(),
                exileVotes = maxVotes,
            ),
        )
    }

    /** 把这一轮的票封成一条历史。票型是纯数据，不留对当时局面的引用。 */
    private fun List<Ballot>.toRound(
        outcome: VoteOutcome,
        exiledSeat: Int? = null,
        exileVotes: Double = 0.0,
    ): VoteRound = VoteRound(
        day = day,
        round = voteRound,
        ballots = this,
        outcome = outcome,
        exiledSeat = exiledSeat,
        exileVotes = exileVotes,
    )

    /** 猎人 / 狼王决定是否开枪。[targetSeat] 为 null 表示放弃。 */
    fun hunterShoot(targetSeat: Int?): WerewolfGame {
        require(phase == Phase.HUNTER_SHOOT) { "当前不是开枪阶段：$phase" }
        val shooter = pendingHunterSeat ?: throw IllegalStateException("没有待开枪的玩家")
        check(playerAt(shooter).role.canShootOnDeath) { "待开枪的座位不是猎人 / 狼王" }
        if (targetSeat != null) {
            require(targetSeat in aliveSeats) { "只能带走存活玩家，$targetSeat 号不在存活列表" }
        }

        var next = copy(
            history = history + GameEvent.HunterShot(
                day = day,
                hunterSeat = shooter,
                targetSeat = targetSeat,
                shooterLabel = playerAt(shooter).role.displayName,
            )
        )
        if (targetSeat != null) {
            next = next.applyDeaths(listOf(targetSeat to DeathCause.HUNTER_SHOOT))
            // 枪可能带走的是警长：只清**被枪打死的那位**的警徽。
            // 不能调用全局 dropDeadBadge()——开枪的猎人自己如果就是警长，
            // 那样会在他还没说遗言之前先把徽吞掉，他想交也交不出去了。
            if (next.sheriffSeat == targetSeat) next = next.dropDeadBadge()
        }
        // 开枪（或放弃）之后才判胜负：猎人那一枪可能打断绑票，
        // 狼王那一枪则可能在狼队全灭后靠屠边翻盘。
        // 放弃开枪同样要判——否则已绑票的局会照常回到白天，白演一天。
        val shooterIsWolfKing = playerAt(shooter).role == WerewolfRole.WEREWOLF_KING
        val winnerFaction = next.evaluateWinner(allowWolfWinWithNoWolves = shooterIsWolfKing)
        if (winnerFaction != null) {
            return next.copy(
                phase = Phase.FINISHED,
                winner = winnerFaction,
                pendingHunterSeat = null,
                history = next.history + GameEvent.GameOver(next.day, winnerFaction),
            )
        }
        return next.afterHunter()
    }

    // ---------------------------------------------------------------------
    // 警长
    // ---------------------------------------------------------------------

    /**
     * 上警报名。所有存活玩家都要表态，[runners] 是决定竞选的那几个人。
     *
     * 三种结局在这里一次分开，编排层不用自己判断：
     * - 没人上警 → 本局没有警长，直接进入遗言/讨论；
     * - 只有一个人 → 直接当选，不必投票（投也只有他一个）；
     * - 两个以上 → 进竞选发言，之后由警下投票。
     */
    fun signUpSheriff(runners: Set<Int>): WerewolfGame {
        require(phase == Phase.DAY_SHERIFF_SIGNUP) { "当前不是上警阶段：$phase" }
        require(runners.all { it in aliveSeats }) { "只有存活玩家能上警：$runners" }
        val candidates = runners.sorted()
        val base = copy(
            sheriffRunners = candidates,
            sheriffCandidates = candidates,
            sheriffRound = 1,
        )
        // 两人以上才单独播报名单：单人上警时 SheriffElected 的文案已经说了
        // 「只有 X 号上警，自动当选」，再补一条「上警的是 X 号」只是重复占屏。
        val signed = if (candidates.size >= 2) {
            base.copy(history = base.history + GameEvent.SheriffSignup(day, candidates))
        } else {
            base
        }
        return when (candidates.size) {
            0 -> signed.copy(
                sheriffElectionDone = true,
                history = signed.history + GameEvent.SheriffVacant(day, "无人上警"),
            ).afterSheriffElection()

            1 -> signed.electSheriff(candidates.first(), votes = 0)

            else -> signed.copy(phase = Phase.DAY_SHERIFF_SPEECH)
        }
    }

    /** 竞选发言结束，开始警下投票。 */
    fun beginSheriffVote(): WerewolfGame {
        require(phase == Phase.DAY_SHERIFF_SPEECH) { "当前不是竞选发言阶段：$phase" }
        return copy(phase = Phase.DAY_SHERIFF_VOTE)
    }

    /**
     * 警长投票。**只有警下的人投**（上过警的都不投），弃票传 null。
     *
     * 平票进入 PK：平票的那几位再各说一轮，然后由警下重投。第二轮再平票就
     * 警徽流失——本局没有警长。这是线下最常见的处理，也让「上警」有真实代价：
     * 争不到警徽，还把自己排除在投票之外。
     *
     * 注意这里**没有 1.5 票**：警长还没选出来，这个权重只属于「已经当上警长的人」。
     */
    fun castSheriffVotes(votes: Map<Int, Int?>): WerewolfGame {
        require(phase == Phase.DAY_SHERIFF_VOTE) { "当前不是警长投票阶段：$phase" }
        val voters = sheriffVoters.toSet()
        if (voters.isEmpty()) {
            // 全员上警，没人有资格投。这不是异常局面，是「大家都想当警长」的必然结果
            return copy(
                sheriffElectionDone = true,
                history = history + GameEvent.SheriffVacant(day, "全员上警，无人可投"),
            ).afterSheriffElection()
        }
        require(votes.keys == voters) {
            "所有警下玩家都要投票（上警的人不投），期望 $voters，实际 ${votes.keys}"
        }
        val candidates = sheriffCandidates.toSet()
        votes.forEach { (voter, target) ->
            if (target != null) {
                require(target in candidates) { "$voter 号投给了不在候选范围的 $target 号" }
            }
        }

        val tally = votes.values.filterNotNull().groupingBy { it }.eachCount()
        if (tally.isEmpty()) {
            return copy(
                sheriffElectionDone = true,
                history = history + GameEvent.SheriffVacant(day, "警下全员弃票"),
            ).afterSheriffElection()
        }

        val max = tally.values.max()
        val top = tally.filterValues { it == max }.keys.sorted()
        if (top.size > 1) {
            return if (sheriffRound < SHERIFF_MAX_ROUNDS) {
                copy(
                    sheriffRound = sheriffRound + 1,
                    // 收窄到平票的人，但 sheriffRunners 保持不变：他们仍然没有投票权
                    sheriffCandidates = top,
                    phase = Phase.DAY_SHERIFF_SPEECH,
                    history = history + GameEvent.SheriffPk(day, top),
                )
            } else {
                copy(
                    sheriffElectionDone = true,
                    history = history + GameEvent.SheriffVacant(day, "两轮平票，警徽流失"),
                ).afterSheriffElection()
            }
        }
        return electSheriff(top.first(), max)
    }

    /**
     * 警徽移交。只能在警长的遗言里做。
     *
     * [toSeat] 为 null 表示撕掉警徽。允许撕是规则的一部分：有时候交给自己不信任的人
     * 比没有警长更糟。
     */
    fun transferBadge(fromSeat: Int, toSeat: Int?): WerewolfGame {
        require(phase == Phase.DAY_LAST_WORDS) { "只能在遗言里移交警徽：$phase" }
        require(sheriffSeat == fromSeat) { "$fromSeat 号不是警长，不能移交警徽" }
        if (toSeat != null) {
            require(toSeat in aliveSeats) { "警徽只能交给存活玩家，$toSeat 号不在存活列表" }
        }
        return copy(
            sheriffSeat = toSeat,
            history = history + GameEvent.SheriffBadgePassed(day, fromSeat, toSeat),
        )
    }

    /**
     * 遗言说完了，进入下一段。
     *
     * 这里顺带处理「警长死了但没有移交」的情况：警徽随他一起作废。
     * 放在这里而不是死亡那一刻，是因为遗言正是他最后一次能改变主意的机会。
     */
    fun finishLastWords(): WerewolfGame {
        require(phase == Phase.DAY_LAST_WORDS) { "当前不是遗言阶段：$phase" }
        val cleared = copy(pendingLastWords = emptyList()).dropDeadBadge()
        return when (afterLastWordsPhase) {
            Phase.NIGHT_WOLF -> cleared.startNextNight()
            // 被票出的猎人：先遗言，再开枪。枪开完才轮到入夜
            Phase.HUNTER_SHOOT -> cleared.copy(phase = Phase.HUNTER_SHOOT)
            else -> cleared.copy(phase = afterLastWordsPhase)
        }
    }

    // ---------------------------------------------------------------------
    // 自爆
    // ---------------------------------------------------------------------

    /**
     * 狼人自爆：当场出局，**白天立刻结束**，跳过投票直接入夜。
     *
     * 只允许在白天的发言环节（讨论、竞选发言）自爆——投票已经开始了再爆没有意义，
     * 那时票型已经收上来了。
     *
     * 规则上自爆者不留遗言：他要说的那句「我自爆」本身就是交代。唯一的例外是他正好
     * 是警长——警徽得交出去，所以走一遍遗言。
     *
     * 两个容易漏的收尾：
     * - **竞选发言里自爆**：选举当场中止，本局没有警长。必须按「无人上警」那样清掉
     *   竞选名单、补一条 [GameEvent.SheriffVacant]，并置 [sheriffElectionDone]；
     *   否则 `sheriffElectionDone` 还是 false，下一次 `dayOpening()` 会把游戏拽回上警。
     * - **别人的遗言还在排队**：首夜死者（或警长）可能正在等遗言。自爆不能覆盖
     *   那个队列，只能把自己的座位并进去——并完还要走完遗言再入夜。
     */
    fun wolfExplode(seat: Int): WerewolfGame {
        require(phase == Phase.DAY_DISCUSSION || phase == Phase.DAY_SHERIFF_SPEECH) {
            "只有白天的发言环节可以自爆：$phase"
        }
        val player = playerAt(seat)
        require(player.alive) { "$seat 号已经出局" }
        require(player.role.isWerewolf) { "只有狼人可以自爆" }

        val wasCampaigning = phase == Phase.DAY_SHERIFF_SPEECH
        var next = applyDeaths(listOf(seat to DeathCause.SELF_EXPLODE))
        next = next.copy(history = next.history + GameEvent.WolfExploded(next.day, seat))

        if (wasCampaigning) {
            next = next.copy(
                sheriffCandidates = emptyList(),
                sheriffRunners = emptyList(),
                sheriffRound = 1,
                sheriffElectionDone = true,
                history = next.history + GameEvent.SheriffVacant(next.day, "竞选发言时狼人自爆，选举中止"),
            )
        }

        val winnerFaction = next.evaluateWinner()
        if (winnerFaction != null) {
            return next.copy(
                phase = Phase.FINISHED,
                winner = winnerFaction,
                history = next.history + GameEvent.GameOver(next.day, winnerFaction),
            )
        }

        // 只把「需要交代警徽」的爆狼警长加进来，不动队列里原有的人。
        // 入夜必须走完遗言，不能图省事直接 startNextNight()——那里会清空队列。
        val pending = buildList {
            addAll(next.pendingLastWords)
            if (next.sheriffSeat == seat && seat !in this) add(seat)
        }.sorted()
        return if (pending.isEmpty()) {
            next.startNextNight()
        } else {
            next.copy(
                phase = Phase.DAY_LAST_WORDS,
                pendingLastWords = pending,
                afterLastWordsPhase = Phase.NIGHT_WOLF,
            )
        }
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
        // 同一夜可能有两个来源指向同一个人（狼刀 + 女巫毒）。播报、复盘记录、玩家死因
        // 三处必须只算一次，否则会出现「6 号、6 号出局」和「记为毒死却照常开枪」。
        val merged = mergeDeaths(deaths)
        var next = copy(witch = stock).applyDeaths(merged)
        val deadSeats = merged.map { it.first }.sorted()
        // 熊在结算完之后判断：他可能刚死，左右邻居也可能刚死。只看天亮那一刻的存活状态，
        // 所以「今天没咆哮」并不等于「两边都是好人」——这条区别要写进播报文案。
        val bearRoar = next.bearRoarSeat()?.let { GameEvent.BearRoar(next.day, it) }
        next = next.copy(
            history = buildList {
                addAll(next.history)
                add(GameEvent.Dawn(next.day, deadSeats))
                if (bearRoar != null) add(bearRoar)
            },
            nights = nights + NightRecord(
                day = day,
                wolfTarget = tonightVictim,
                guarded = tonightGuardedSeat,
                healed = healed,
                poisonTarget = merged.firstOrNull { it.second == DeathCause.WITCH_POISON }?.first,
                deaths = deadSeats,
            ),
        )

        // 胜负分两步判。狼人全灭是即时终局，好人不需要再等谁开枪；
        // 但「绑票 / 屠边」这类狼方赢必须等天亮被刀的猎人开完枪——
        // 他一枪可能带走最后一狼，把 2v2 的必败翻成 1v1 甚至好人赢。
        // 在这里先判就把他那张枪牌吞了，和「猎人被狼刀可以开枪」直接冲突。
        val winnerFaction = next.evaluateWinner()
        if (winnerFaction == Faction.GOOD) {
            return next.copy(
                phase = Phase.FINISHED,
                winner = winnerFaction,
                history = next.history + GameEvent.GameOver(next.day, winnerFaction),
            )
        }

        // 只有被狼刀死、且能开枪的身份（目前只有猎人）可以开枪，被毒的不能。
        // 狼王是狼人阵营，不会被自己人刀，所以这里实际只会命中猎人。
        // 这里必须读合并后的死因，不能读入参：入参里他可能同时有刀和毒两条记录，
        // 那样会和 applyDeaths 写进 player.deathCause 的结论打架。
        val fallenHunter = merged.firstOrNull { (seat, cause) ->
            cause == DeathCause.WOLF_KILL && next.playerAt(seat).role.canShootOnDeath
        }?.first

        // 首夜死者留遗言（线下惯例：第二夜起，夜里死的人不再发言）。
        // 警长是例外——不管第几夜，他都要在遗言里把警徽交出去，否则好人白丢 1.5 票。
        val lastWords = buildList {
            if (next.day == 1) addAll(deadSeats)
            val sheriff = next.sheriffSeat
            if (sheriff != null && sheriff in deadSeats && sheriff !in this) add(sheriff)
        }.sorted()

        return if (fallenHunter != null) {
            next.copy(
                phase = Phase.HUNTER_SHOOT,
                pendingHunterSeat = fallenHunter,
                afterHunterPhase = Phase.DAY_DISCUSSION,
                // 遗言排在天亮开枪之后：先看枪打到谁，再让死者开口
                pendingLastWords = lastWords,
            )
        } else {
            // 没有猎人要开枪，狼方的胜负此刻就定了（狼全灭已在上面判过）。
            // 不在这里判的话，绑票局面会照常走进白天，白演一轮注定投不出人的投票。
            if (winnerFaction != null) {
                return next.copy(
                    phase = Phase.FINISHED,
                    winner = winnerFaction,
                    history = next.history + GameEvent.GameOver(next.day, winnerFaction),
                )
            }
            next.copy(pendingLastWords = lastWords).dayOpening()
        }
    }

    private fun finishVote(
        thisRound: List<Ballot>,
        exiled: Int?,
        voteCount: Double,
        round: VoteRound,
    ): WerewolfGame {
        if (exiled == null) {
            return copy(
                ballots = thisRound,
                voteRounds = voteRounds + round,
                history = history + GameEvent.NoExile(day),
            ).startNextNight()
        }

        // 白痴第一次被票：翻开身份，不死，但从这一刻起失去投票权。
        // 这不是「免死一次」而是「票不死」——夜里被刀、被毒照样会死。
        if (playerAt(exiled).role == WerewolfRole.IDIOT && !idiotRevealed) {
            return copy(
                ballots = thisRound,
                voteRounds = voteRounds + round.copy(outcome = VoteOutcome.IDIOT_REVEALED),
                idiotRevealed = true,
                history = history + GameEvent.IdiotRevealed(day, exiled, voteCount),
            ).startNextNight()
        }

        var next = copy(ballots = thisRound, voteRounds = voteRounds + round)
            .applyDeaths(listOf(exiled to DeathCause.EXILE))
        next = next.copy(history = next.history + GameEvent.Exiled(next.day, exiled, voteCount))

        // 和 settleNight 同一条时序，但要多等一种情况：
        // 好人赢（狼全灭）通常立即终局，可如果出局的是狼王，他那最后一枪可能带走
        // 最后一神 / 最后一民，当场把胜负翻过来——所以开枪者存在时无论哪边赢都要等枪响。
        val winnerFaction = next.evaluateWinner()
        val shooterRole = next.playerAt(exiled).role
        val canShoot = shooterRole.canShootOnDeath
        if (winnerFaction != null && !canShoot) {
            return next.copy(
                phase = Phase.FINISHED,
                winner = winnerFaction,
                history = next.history + GameEvent.GameOver(next.day, winnerFaction),
            )
        }

        // 被放逐的人一定留遗言（这是他被冤枉后唯一能说话的机会）。
        // 如果他能开枪，顺序是**先遗言、再开枪**——枪是死前最后一张牌，
        // 先说两句再放，才解释得清他为什么带走这个人。
        // 上面特意没在有开枪者时终局，就是为了走到这里。
        return next.copy(
            phase = Phase.DAY_LAST_WORDS,
            pendingLastWords = listOf(exiled),
            afterLastWordsPhase = if (canShoot) Phase.HUNTER_SHOOT else Phase.NIGHT_WOLF,
            pendingHunterSeat = if (canShoot) exiled else null,
            afterHunterPhase = Phase.NIGHT_WOLF,
        )
    }

    /**
     * 天亮时开枪结束之后。
     *
     * [afterHunterPhase] 只有两种值：天亮被刀（回到白天开场，可能还要竞选、遗言）
     * 和白天被票出（直接入夜）。这里用 [dayOpening] 重新算一遍开场，
     * 而不是把阶段写死，是因为「天亮之后先干什么」取决于当天有没有竞选、有没有遗言。
     */
    private fun afterHunter(): WerewolfGame {
        val cleared = copy(pendingHunterSeat = null)
        return if (afterHunterPhase == Phase.NIGHT_WOLF) {
            cleared.startNextNight()
        } else {
            cleared.dayOpening()
        }
    }

    private fun startNextNight(): WerewolfGame = dropDeadBadge().copy(
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
        // 竞选只在第 1 天办一次，跨夜时把选举过程的中间状态收干净；
        // sheriffElectionDone 留着，它记录的是「办过了」
        sheriffCandidates = emptyList(),
        sheriffRunners = emptyList(),
        sheriffRound = 1,
        pendingLastWords = emptyList(),
        afterLastWordsPhase = Phase.DAY_DISCUSSION,
    )

    /**
     * 天亮之后先做什么。
     *
     * 顺序是有讲究的：**第 1 天先选警长，再让死者留遗言**。反过来先遗言的话，
     * 死者的指控会直接左右警长投票——等于给死人一张选票，而且这张票还没有成本。
     *
     * 警长死了、但遗言还没说的时候，不能在这里把警徽清掉：他得先在遗言里指定继任者。
     */
    private fun dayOpening(): WerewolfGame {
        val cleaned = if (pendingLastWords.contains(sheriffSeat)) this else dropDeadBadge()
        if (cleaned.day == 1 && cleaned.config.hasSheriff && !cleaned.sheriffElectionDone) {
            return cleaned.copy(phase = Phase.DAY_SHERIFF_SIGNUP)
        }
        return if (cleaned.pendingLastWords.isNotEmpty()) {
            cleaned.copy(
                phase = Phase.DAY_LAST_WORDS,
                afterLastWordsPhase = Phase.DAY_DISCUSSION,
            )
        } else {
            cleaned.copy(phase = Phase.DAY_DISCUSSION)
        }
    }

    /**
     * 竞选结束（选出来了，或者确定没有警长）之后去哪。
     *
     * 顺带把竞选名单清干净：选举一旦定下来，这两个列表就没有读者了。
     * 留着它们不只是内存问题——`sheriffRunners` 会被当成「上过警的人」继续影响
     * `sheriffVoters`，而第二天再问「谁上过警」已经没有任何意义；
     * 更糟的是名单里会留下当天被票出的人。
     */
    private fun afterSheriffElection(): WerewolfGame {
        val closed = copy(
            sheriffCandidates = emptyList(),
            sheriffRunners = emptyList(),
            sheriffElectionDone = true,
        )
        return if (closed.pendingLastWords.isNotEmpty()) {
            closed.copy(phase = Phase.DAY_LAST_WORDS, afterLastWordsPhase = Phase.DAY_DISCUSSION)
        } else {
            closed.copy(phase = Phase.DAY_DISCUSSION)
        }
    }

    private fun electSheriff(seat: Int, votes: Int): WerewolfGame = copy(
        sheriffSeat = seat,
        sheriffCandidates = emptyList(),
        sheriffRunners = emptyList(),
        sheriffElectionDone = true,
        history = history + GameEvent.SheriffElected(day, seat, votes),
    ).afterSheriffElection()

    /**
     * 死掉的警长如果没把警徽交出去，警徽就作废。
     *
     * 三个调用点（天亮开场、入夜、被枪带走）都不是「死亡那一刻」——因为死亡那一刻
     * 他可能还有遗言，还有机会改主意。等遗言说完再清，是唯一不会抢在他前面的时机。
     */
    private fun dropDeadBadge(): WerewolfGame {
        val sheriff = sheriffSeat ?: return this
        if (playerAt(sheriff).alive) return this
        return copy(
            sheriffSeat = null,
            history = history + GameEvent.SheriffBadgePassed(day, sheriff, toSeat = null),
        )
    }

    /**
     * 把同一夜指向同一个座位的多条死因合并成一条。
     *
     * 实际只会有一种撞车：**狼刀和女巫毒落在同一个人身上**（守卫和解药都是让人活下来，
     * 不会产生第二条死亡记录）。合并时**毒覆盖刀**，两个理由：
     *
     * 1. 毒既不能被守卫挡、也不能被解药救，是这一夜里更「硬」的那个来源；
     * 2. 规则上「被毒不能开枪」。按毒记，[WerewolfPlayer.deathCause] 和
     *    「猎人能不能开枪」这两处才会给出同一个答案。
     *
     * 顺带按座位排序，让播报和复盘的顺序永远稳定。
     */
    private fun mergeDeaths(deaths: List<Pair<Int, DeathCause>>): List<Pair<Int, DeathCause>> =
        deaths.groupBy { it.first }
            .map { (seat, causes) ->
                val cause = if (causes.any { it.second == DeathCause.WITCH_POISON }) {
                    DeathCause.WITCH_POISON
                } else {
                    causes.first().second
                }
                seat to cause
            }
            .sortedBy { it.first }

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
     * 唯一例外是狼王的最后一枪：[allowWolfWinWithNoWolves] 时允许狼队零存活仍按屠边翻盘。
     *
     * 第二条是**绑票**：存活狼人数 >= 存活好人数就判狼人胜，不分屠边屠城。
     *
     * 这不是屠城规则的复读，而是「票已经投不出去」这个事实本身：每人一票，狼人抱团
     * 指同一个好人，好人凑不出比他更多的票，两边对投下去必然平票；而本板子没有警长
     * （[WerewolfConfig.maxVoteRounds] 默认 1），平票就是当天无人出局，夜里狼人再刀一个，
     * 好人只会更少。所以 2 狼 2 好人这一刻胜负已经定了，再演一整轮白天也只能演到同一个结局。
     */
    private fun evaluateWinner(allowWolfWinWithNoWolves: Boolean = false): Faction? {
        val aliveWolfCount = players.count { it.alive && it.role.isWerewolf }
        if (aliveWolfCount == 0 && !allowWolfWinWithNoWolves) return Faction.GOOD

        val aliveGoodCount = players.count { it.alive && it.faction == Faction.GOOD }

        // 绑票比的是**票权**，不是人头。警长一票算 1.5（半点单位里是 3 而不是 2），
        // 活着的警长属于哪边，哪边就多半票：2 狼 2 好且警长是好人时是 4 点对 5 点，
        // 好人能把狼投出去。按人头比会在这里提前判负，那张 1.5 票白白浪费。
        // 死警长的警徽归属未定，不算在任何一边——他要先在遗言里决定交给谁。
        val sheriff = aliveSheriffSeat
        val sheriffIsWolf = sheriff != null && playerAt(sheriff).faction == Faction.WEREWOLF
        val sheriffIsGood = sheriff != null && playerAt(sheriff).faction == Faction.GOOD
        val wolfPoints = aliveWolfCount * 2 + if (sheriffIsWolf) 1 else 0
        val goodPoints = aliveGoodCount * 2 + if (sheriffIsGood) 1 else 0
        if (wolfPoints >= goodPoints) return Faction.WEREWOLF

        if (config.winRule == WinRule.SLAY_SIDE) {
            // 只看「开局存在过的那一类」，避免 8 人局里没有平民时误判村民全灭。
            val hadGods = players.any { it.role.isGod }
            val hadVillagers = players.any { it.role == WerewolfRole.VILLAGER }
            val godsAlive = players.any { it.alive && it.role.isGod }
            val villagersAlive = players.any { it.alive && it.role == WerewolfRole.VILLAGER }
            if ((hadGods && !godsAlive) || (hadVillagers && !villagersAlive)) return Faction.WEREWOLF
        }
        // 狼王那一枪没打穿屠边，狼队又已经全灭——好人照常获胜。
        // 普通判定时 aliveWolfCount 一定是正数，这里直接返回 null 保持原行为。
        return if (aliveWolfCount == 0) Faction.GOOD else null
    }

    private fun hasAliveRole(role: WerewolfRole): Boolean = alivePlayers.any { it.role == role }

    /**
     * 天亮时熊是否该咆哮。邻居按座位号**绕圈**取：1 号的左邻是最大号，
     * 不能把他当成没有左邻——12 人局里 1 号和 12 号是坐在一起的。
     *
     * 熊死了就不咆哮；邻居死了也不算——夜里有人出局时咆哮可能突然停止，
     * 这本身就是白天可以拿来盘的信息。
     */
    private fun bearRoarSeat(): Int? {
        val bear = alivePlayers.firstOrNull { it.role == WerewolfRole.BEAR } ?: return null
        val seats = players.map { it.seat }.sorted()
        val index = seats.indexOf(bear.seat)
        val neighbors = listOf(
            seats[(index - 1 + seats.size) % seats.size],
            seats[(index + 1) % seats.size],
        )
        return bear.seat.takeIf { neighbors.any { seat -> playerAt(seat).alive && isWerewolf(seat) } }
    }

    companion object {
        /** 狼人平票后最多商议几轮。第二轮再平票就空刀。 */
        const val MAX_WOLF_VOTE_ROUNDS = 2

        /**
         * 警长投票最多几轮：首轮 + 一次 PK。
         *
         * 和 [WerewolfConfig.maxVoteRounds] 分开，是因为这两个数字管的是两件事：
         * 那个是「放逐投票能重投几次」，这个是「选警长能投几次」。都叫 maxVoteRounds
         * 会让「12 人局改一次配置」变成一次改两处规则的赌博。
         */
        const val SHERIFF_MAX_ROUNDS = 2

        /**
         * 洗牌发身份，开局。有守卫的板子从守卫阶段开始，否则从狼人行动开始。
         *
         * **座位也一起洗**，不只是身份。座位的唯一用途是发言次序和互相指代，
         * 而名单是配置页按选人顺序排出来的：不洗的话第一个被勾选的人永远是 1 号、
         * 永远先手发言，坐哪个位子完全可预测。座位和身份用同一个 [random] 连洗两次，
         * 传 `Random(seed)` 依然能复现整局。
         */
        fun deal(
            config: WerewolfConfig = WerewolfConfig(),
            roster: List<RosterEntry>,
            random: Random = Random.Default,
        ): WerewolfGame = build(config, roster.shuffled(random)) { roles -> roles.shuffled(random) }

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
