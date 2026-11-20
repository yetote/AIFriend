package com.feature.werewolf.engine.persistence

import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.game.Ballot
import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.Faction
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.NightRecord
import com.feature.werewolf.game.PendingDawn
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.SeerCheck
import com.feature.werewolf.game.SpeechDirection
import com.feature.werewolf.game.VoteOutcome
import com.feature.werewolf.game.VoteRound
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPlayer
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WerewolfSpeechLength
import com.feature.werewolf.game.WinRule
import com.feature.werewolf.game.WerewolfTurnLimit
import com.feature.werewolf.game.WitchStock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 一局的存档格式。
 *
 * **为什么不直接序列化领域对象**：领域类会重构（加字段、改类型、拆类），
 * 一旦 `@Serializable` 贴在领域类上，每次重构都会连带改掉存档格式，
 * 老存档要么读不出来、要么读出来是错的。中间隔一层 DTO，领域随便改，
 * 只需要在 [TableSnapshot.from] / [toTable] 里补映射。
 *
 * **为什么要版本号**：格式变了、又不打算兼容时，`version` 不匹配就当作没有存档，
 * 让玩家重开一局。这比强行反序列化一个半懂的旧档安全——那种情况会读出一个
 * 内部矛盾的局面（比如死了的人还在投票），排查起来极难。
 *
 * 枚举一律存名字而不是序号：序号会随枚举重排而变化，名字不会。
 */
object WerewolfSnapshot {

    /**
     * 当前存档格式版本。改动 DTO 结构时必须加一。
     *
     * v12：狼人票型允许 null（空刀），`wolfVotes` 从 `Map<Int, Int>` 变成 `Map<Int, Int?>`。
     * 形状没变（还是 `{"3": 5}`），多出来的只是 `{"3": null}`；不兼容的是**旧版本读新档**，
     * 所以照例加版本号，让老版本老老实实当它没有存档。
     *
     * v13：多出 `turnLimit` / `thinkingOff`（这一局的思考时限与本局被关思考的座位）。
     * 都是带默认值的**新增**字段，语义没变，旧档照样能读。
     *
     * v14：玩家多出 `model`（每个座位背后跑的模型名，牌桌/回放要显示）。
     * 同样是带默认值的新增字段，旧档读到空串就不显示，语义没变。
     *
     * v15：多出 `spokenPhaseKey` / `spokenSeats`（逐座位发言阶段的续档进度）。
     * 带默认值的新增字段，旧档读出来是空进度——那种存档本来就是整阶段落档的，
     * 续档最多重说当前阶段一遍，不会有语义错乱。
     *
     * v16：多出 `speechLength`（这一局发言的目标长度档位）。
     * 带默认值的新增字段，旧档读出来按标准档处理，语义不变。
     *
     * v17：多出 `alphaWolfSeat`（导演钉死的头狼）。
     * 带默认值的新增字段，旧档读出来是 null——每晚随机，就是旧行为。
     *
     * v18：事件多出 `SPEECH_DIRECTION`（警长每天选的发言方向）。
     * 新事件走 EventSnapshot 的 type 分派，旧档没有这个类型自然读不到，
     * speechDirection 落到默认的顺时针——就是旧行为。
     */
    const val VERSION = 18

    private val json = Json {
        ignoreUnknownKeys = true   // 旧档多出来的字段不该让新版本读不出来
        encodeDefaults = true      // 保证 round-trip 一致，缺省值也要写出来
    }

    fun encode(
        table: WerewolfTable,
        customPrompt: String? = null,
        turnLimit: WerewolfTurnLimit = WerewolfTurnLimit.STANDARD,
        thinkingOff: Set<Int> = emptySet(),
        speechLength: WerewolfSpeechLength = WerewolfSpeechLength.STANDARD,
    ): String = json.encodeToString(
        table.toSnapshot(customPrompt, turnLimit, thinkingOff, speechLength),
    )

    /**
     * 只取「这一局用的玩家设定」，不还原局面。
     *
     * 复盘要知道当时是按什么设定打的：改过提示词之后，旧局的发言风格
     * 在设置页里已经查不到了。而提示词是**请求参数**、不是局面状态，
     * `toTable()` 不该把它带进 [WerewolfTable]——所以单独开一个入口读。
     *
     * 旧档没有这一段，返回 null。
     */
    fun decodePrompt(text: String): String? =
        runCatching { json.decodeFromString<TableSnapshot>(text) }.getOrNull()
            ?.customPrompt?.takeIf { it.isNotBlank() }

    /**
     * 只取这一局的节奏设置（时限档位 + 本局关掉思考的座位），不还原局面。
     *
     * 和 [decodePrompt] 同一个理由：这是**请求参数**，不是局面的一部分，
     * 不该混进 [WerewolfTable] 让复盘、导出都被迫背上它。
     * 旧档（v12 及以前）没有这一段，返回 null，调用方按默认值处理。
     */
    fun decodePacing(text: String): PacingSnapshot? {
        val dto = runCatching { json.decodeFromString<TableSnapshot>(text) }.getOrNull() ?: return null
        return PacingSnapshot(
            limit = WerewolfTurnLimit.of(dto.turnLimit),
            thinkingOff = dto.thinkingOff.toSet(),
            speechLength = WerewolfSpeechLength.of(dto.speechLength),
        )
    }

    /**
     * 反序列化。失败返回 null，不抛异常——存档损坏只是丢一局游戏，
     * 不该把整个界面拖崩。调用方看到 null 就当没有存档处理。
     */
    fun decode(text: String): WerewolfTable? {
        val dto = runCatching { json.decodeFromString<TableSnapshot>(text) }.getOrNull() ?: return null
        return decode(dto)
    }

    /**
     * 已经解析出来的快照 → 局面。
     *
     * 单独开一个入口是给「对局文件」用的：那个格式把快照包在自己的信封里，
     * 解出来的还是同一个 [TableSnapshot]，不该为了取一个局面再把 JSON 拼回去重解一遍。
     */
    internal fun decode(dto: TableSnapshot): WerewolfTable? {
        // v2 相对 v1、v3 相对 v2，都只是**新增**带默认值的字段，语义一个字都没改，
        // 所以旧档可以直接读：v1 缺复盘那两段，v2 缺守卫/白痴的状态（那时也没有这两个身份）。
        // 这比「一声不响把玩家没打完的局丢掉」要好。真正的语义变更才该拒绝旧档。
        if (dto.version !in MIN_SUPPORTED_VERSION..VERSION) return null
        return runCatching { dto.toTable() }.getOrNull()
    }

    /** 这份快照是不是**太新**了（来自更高版本）。用来区分「坏了」和「请你升级」。 */
    internal fun isFromFuture(dto: TableSnapshot): Boolean = dto.version > VERSION

    /** 最低可读版本。低于它的档语义已经对不上，宁可当作没有存档。 */
    private const val MIN_SUPPORTED_VERSION = 1
}

private const val SNAPSHOT_VERSION = WerewolfSnapshot.VERSION

// ---------------------------------------------------------------------------
// DTO
// ---------------------------------------------------------------------------

/**
 * 从存档里取出来的「这一局怎么跑」——时限档位 + 被关掉思考的座位。
 *
 * 单独一个类型而不是直接返回 [WerewolfGameStore] 需要的两个值：
 * 续档那一步要把它们一起恢复，多一个解构对象比多两个查询清楚。
 */
data class PacingSnapshot(
    val limit: WerewolfTurnLimit,
    val thinkingOff: Set<Int>,
    /** v16 起：发言长度档位。旧档按标准处理。 */
    val speechLength: WerewolfSpeechLength = WerewolfSpeechLength.STANDARD,
)

@Serializable
internal data class TableSnapshot(
    val version: Int = SNAPSHOT_VERSION,
    val config: ConfigSnapshot,
    /**
     * 落档当时生效的玩家设定（全局那段 + 板子那段拼好的）。
     *
     * **只给复盘看，不参与局面还原**：提示词是请求参数，不是状态。
     * 续档也仍然用设置页里的当前值——玩家改完设定希望立刻生效，
     * 而不是被一局三天前的存档按回旧设定。
     *
     * 每次落档都会覆盖它，所以值是「最后一次落档时生效的那份」。
     * 旧档（v10 及以前）没有这一段，读出来是 null。
     */
    val customPrompt: String? = null,
    /**
     * 这一局选的思考时限档位（名字，不是序号）。旧档（v12 及以前）没有这一项，按标准处理。
     */
    val turnLimit: String = WerewolfTurnLimit.STANDARD.name,
    /**
     * 这一局被关掉深度思考的座位。
     *
     * 必须落档：超时自动关思考是**本局**的降级，续档之后又打开的话，
     * 那个慢模型会在下一阶段原样再超时一次，用户看到的是一局永远打不完的牌。
     */
    val thinkingOff: List<Int> = emptyList(),
    /** v16 起：这一局发言的目标长度档位（名字）。旧档没有这一项，按标准处理。 */
    val speechLength: String = WerewolfSpeechLength.STANDARD.name,
    val players: List<PlayerSnapshot>,
    val day: Int,
    val phase: String,
    val witch: WitchSnapshot = WitchSnapshot(),
    /** 本夜各狼投给谁。null 是主张空刀（v12 起）。 */
    val wolfVotes: Map<Int, Int?> = emptyMap(),
    val wolfVoteRound: Int = 1,
    val tonightVictim: Int? = null,
    /** v3 起：本夜守卫守的人，以及上一夜守的人（用于「不能连守」）。 */
    val tonightGuardedSeat: Int? = null,
    val lastGuardedSeat: Int? = null,
    /** v8 起：本夜的头狼（狼队指挥）。旧档没有这一项。 */
    val nightHead: Int? = null,
    /** v8 起：头狼定的战术原文。旧档没有这一项。 */
    val wolfPlan: String? = null,
    /** v17 起：导演钉死的头狼（座位号）。旧档没有这一项。 */
    val alphaWolfSeat: Int? = null,
    /** v3 起：白痴是否已翻牌。 */
    val idiotRevealed: Boolean = false,
    /** v10 起：还没公布的天亮播报（第一天先竞选、后公布死讯）。旧档没有这一项。 */
    val pendingDawn: PendingDawnSnapshot? = null,
    val seerChecks: List<CheckSnapshot> = emptyList(),
    val voteRound: Int = 1,
    val voteCandidates: List<Int>? = null,
    val ballots: List<BallotSnapshot> = emptyList(),
    val pendingHunterSeat: Int? = null,
    /** v4 起：警长与竞选状态。旧档没有这一段，读出来就是「没有警长」。 */
    val sheriffSeat: Int? = null,
    val sheriffCandidates: List<Int> = emptyList(),
    val sheriffRunners: List<Int> = emptyList(),
    val sheriffRound: Int = 1,
    val sheriffElectionDone: Boolean = false,
    /** v4 起：待说遗言的座位，以及遗言结束后去哪。 */
    val pendingLastWords: List<Int> = emptyList(),
    val afterLastWordsPhase: String = Phase.DAY_DISCUSSION.name,
    val afterHunterPhase: String = Phase.NIGHT_WOLF.name,
    val history: List<EventSnapshot> = emptyList(),
    val winner: String? = null,
    /** v2 起：每夜结算记录，复盘用。旧档没有这一段，读出来是空列表。 */
    val nights: List<NightSnapshot> = emptyList(),
    /** v2 起：逐轮投票历史，复盘用。 */
    val voteRounds: List<VoteRoundSnapshot> = emptyList(),
    /** v15 起：逐座位发言的续档进度。见 [WerewolfGame.spokenPhaseKey]。 */
    val spokenPhaseKey: String = "",
    val spokenSeats: List<Int> = emptyList(),
    val transcript: List<MessageSnapshot> = emptyList(),
)

@Serializable
internal data class ConfigSnapshot(
    /** 身份构成。键是 [WerewolfRole] 的名字。 */
    val composition: Map<String, Int>,
    val winRule: String,
    val maxVoteRounds: Int,
    val witchCanSelfHealOnFirstNight: Boolean,
    /** v4 起：是否竞选警长。旧档没有这一项，按 false 读。 */
    val hasSheriff: Boolean = false,
)

@Serializable
internal data class PlayerSnapshot(
    val seat: Int,
    val id: String,
    val name: String,
    val role: String,
    val alive: Boolean,
    val deathCause: String? = null,
    val deathDay: Int? = null,
    /** 这个座位背后跑的模型名。老档没有这一段，默认空串，界面据此不显示。 */
    val model: String = "",
)

@Serializable
internal data class WitchSnapshot(
    val antidoteAvailable: Boolean = true,
    val poisonAvailable: Boolean = true,
)

@Serializable
internal data class CheckSnapshot(
    val day: Int,
    val targetSeat: Int,
    val isWerewolf: Boolean,
)

@Serializable
internal data class BallotSnapshot(
    val voterSeat: Int,
    val targetSeat: Int? = null,
)

@Serializable
internal data class PendingDawnSnapshot(
    val day: Int,
    val deaths: List<Int> = emptyList(),
    val bearRoarSeat: Int? = null,
)

@Serializable
internal data class NightSnapshot(
    val day: Int,
    val wolfTarget: Int? = null,
    /** v3 起：这一夜守的人。旧档没有这一段，读出来是 null。 */
    val guarded: Int? = null,
    val healed: Boolean = false,
    val poisonTarget: Int? = null,
    val deaths: List<Int> = emptyList(),
    /** v9 起：守救同归（守卫守了刀口，女巫也救了，按规则同归于尽）。旧档没有。 */
    val mutualProtection: Boolean = false,
)

@Serializable
internal data class VoteRoundSnapshot(
    val day: Int,
    val round: Int,
    val ballots: List<BallotSnapshot> = emptyList(),
    /** [com.feature.werewolf.game.VoteOutcome] 的名字。 */
    val outcome: String,
    val exiledSeat: Int? = null,
    /** v4 起改成浮点：警长一票算 1.5，票数会出现 3.5。旧档里的整数照样读得出来。 */
    val exileVotes: Double = 0.0,
)

/**
 * 公开事件的扁平表示。
 *
 * 这里刻意没用 sealed 多态序列化：事件种类少、字段少，一个 `type` 加几个可空字段
 * 更好维护，也不会因为新增一个事件子类就改变已存数据的分派方式。
 */
@Serializable
internal data class EventSnapshot(
    val type: String,
    val day: Int,
    val deadSeats: List<Int>? = null,
    val seat: Int? = null,
    /** v4 起改成浮点：警长票权 1.5，票数会出现 3.5。 */
    val voteCount: Double? = null,
    val hunterSeat: Int? = null,
    val targetSeat: Int? = null,
    val winner: String? = null,
    /** v4 起：警长 PK 的平票座位。 */
    val seats: List<Int>? = null,
    /** v4 起：没有警长的原因（只有 [SheriffVacant] 会用到）。 */
    val reason: String? = null,
    /** v4 起：警徽是谁交出来的。 */
    val fromSeat: Int? = null,
    /** v5 起：开枪者的称呼（猎人 / 狼王）。老档没有这一项，按猎人读。 */
    val shooterLabel: String? = null,
    /** v18 起：警长选的发言方向（SpeechDirection 的名字，只有 SPEECH_DIRECTION 用到）。 */
    val direction: String? = null,
)

@Serializable
internal data class MessageSnapshot(
    val day: Int,
    val phase: String,
    val seat: Int? = null,
    val kind: String,
    val text: String,
    /** null 表示全员可见。 */
    val audience: List<Int>? = null,
    /** v6 起：发言时的思考过程，只在复盘展示。旧档没有，默认空。 */
    val reasoning: String = "",
)

// ---------------------------------------------------------------------------
// 领域 → DTO
// ---------------------------------------------------------------------------

internal fun WerewolfTable.toSnapshot(
    customPrompt: String? = null,
    turnLimit: WerewolfTurnLimit = WerewolfTurnLimit.STANDARD,
    thinkingOff: Set<Int> = emptySet(),
    speechLength: WerewolfSpeechLength = WerewolfSpeechLength.STANDARD,
): TableSnapshot {
    val game = this.game
    return TableSnapshot(
        customPrompt = customPrompt?.takeIf { it.isNotBlank() },
        turnLimit = turnLimit.name,
        thinkingOff = thinkingOff.sorted(),
        speechLength = speechLength.name,
        config = ConfigSnapshot(
            composition = game.config.composition.entries.associate { it.key.name to it.value },
            winRule = game.config.winRule.name,
            maxVoteRounds = game.config.maxVoteRounds,
            witchCanSelfHealOnFirstNight = game.config.witchCanSelfHealOnFirstNight,
            hasSheriff = game.config.hasSheriff,
        ),
        players = game.players.map {
            PlayerSnapshot(
                seat = it.seat,
                id = it.id,
                name = it.name,
                role = it.role.name,
                alive = it.alive,
                deathCause = it.deathCause?.name,
                deathDay = it.deathDay,
                model = it.model,
            )
        },
        day = game.day,
        phase = game.phase.name,
        witch = WitchSnapshot(game.witch.antidoteAvailable, game.witch.poisonAvailable),
        wolfVotes = game.wolfVotes,
        wolfVoteRound = game.wolfVoteRound,
        tonightVictim = game.tonightVictim,
        tonightGuardedSeat = game.tonightGuardedSeat,
        lastGuardedSeat = game.lastGuardedSeat,
        nightHead = game.nightHead,
        wolfPlan = game.wolfPlan,
        alphaWolfSeat = game.alphaWolfSeat,
        idiotRevealed = game.idiotRevealed,
        pendingDawn = game.pendingDawn?.let {
            PendingDawnSnapshot(
                day = it.day,
                deaths = it.deaths,
                bearRoarSeat = it.bearRoarSeat,
            )
        },
        seerChecks = game.seerChecks.map { CheckSnapshot(it.day, it.targetSeat, it.isWerewolf) },
        voteRound = game.voteRound,
        voteCandidates = game.voteCandidates,
        ballots = game.ballots.map { BallotSnapshot(it.voterSeat, it.targetSeat) },
        pendingHunterSeat = game.pendingHunterSeat,
        sheriffSeat = game.sheriffSeat,
        sheriffCandidates = game.sheriffCandidates,
        sheriffRunners = game.sheriffRunners,
        sheriffRound = game.sheriffRound,
        sheriffElectionDone = game.sheriffElectionDone,
        pendingLastWords = game.pendingLastWords,
        afterLastWordsPhase = game.afterLastWordsPhase.name,
        afterHunterPhase = game.afterHunterPhase.name,
        spokenPhaseKey = game.spokenPhaseKey,
        spokenSeats = game.spokenSeats.sorted(),
        history = game.history.map { it.toSnapshot() },
        winner = game.winner?.name,
        nights = game.nights.map {
            NightSnapshot(
                day = it.day,
                wolfTarget = it.wolfTarget,
                guarded = it.guarded,
                healed = it.healed,
                poisonTarget = it.poisonTarget,
                deaths = it.deaths,
                mutualProtection = it.mutualProtection,
            )
        },
        voteRounds = game.voteRounds.map { it.toSnapshot() },
        transcript = this.transcript.map {
            MessageSnapshot(
                day = it.day,
                phase = it.phase.name,
                seat = it.seat,
                kind = it.kind.name,
                text = it.text,
                audience = it.audience?.sorted(),
                reasoning = it.reasoning,
            )
        },
    )
}

private fun VoteRound.toSnapshot(): VoteRoundSnapshot = VoteRoundSnapshot(
    day = day,
    round = round,
    ballots = ballots.map { BallotSnapshot(it.voterSeat, it.targetSeat) },
    outcome = outcome.name,
    exiledSeat = exiledSeat,
    exileVotes = exileVotes,
)

private fun GameEvent.toSnapshot(): EventSnapshot = when (this) {
    is GameEvent.Dawn -> EventSnapshot(type = "DAWN", day = day, deadSeats = deadSeats)
    is GameEvent.Exiled -> EventSnapshot(type = "EXILED", day = day, seat = seat, voteCount = voteCount)
    is GameEvent.NoExile -> EventSnapshot(type = "NO_EXILE", day = day)
    is GameEvent.IdiotRevealed ->
        EventSnapshot(type = "IDIOT_REVEALED", day = day, seat = seat, voteCount = voteCount)
    is GameEvent.HunterShot -> EventSnapshot(
        type = "HUNTER_SHOT",
        day = day,
        hunterSeat = hunterSeat,
        targetSeat = targetSeat,
        shooterLabel = shooterLabel,
    )
    is GameEvent.BearRoar -> EventSnapshot(type = "BEAR_ROAR", day = day, seat = seat)
    is GameEvent.WolfExploded -> EventSnapshot(type = "WOLF_EXPLODED", day = day, seat = seat)
    is GameEvent.SheriffSignup ->
        EventSnapshot(type = "SHERIFF_SIGNUP", day = day, seats = runners)
    is GameEvent.SheriffElected ->
        EventSnapshot(type = "SHERIFF_ELECTED", day = day, seat = seat, voteCount = votes.toDouble())
    is GameEvent.SheriffVacant -> EventSnapshot(type = "SHERIFF_VACANT", day = day, reason = reason)
    is GameEvent.SheriffPk -> EventSnapshot(type = "SHERIFF_PK", day = day, seats = seats)
    is GameEvent.SheriffBadgePassed ->
        EventSnapshot(type = "SHERIFF_BADGE_PASSED", day = day, fromSeat = fromSeat, targetSeat = toSeat)
    is GameEvent.SpeechDirectionChosen ->
        EventSnapshot(type = "SPEECH_DIRECTION", day = day, seat = seat, direction = direction.name)
    is GameEvent.GameOver -> EventSnapshot(type = "GAME_OVER", day = day, winner = winner.name)
}

// ---------------------------------------------------------------------------
// DTO → 领域
// ---------------------------------------------------------------------------

internal fun TableSnapshot.toTable(): WerewolfTable = WerewolfTable(
    game = WerewolfGame(
        config = WerewolfConfig(
            composition = config.composition.entries.associate { roleOf(it.key) to it.value },
            winRule = enumOf<WinRule>(config.winRule),
            maxVoteRounds = config.maxVoteRounds,
            witchCanSelfHealOnFirstNight = config.witchCanSelfHealOnFirstNight,
            hasSheriff = config.hasSheriff,
        ),
        players = players.map {
            WerewolfPlayer(
                seat = it.seat,
                id = it.id,
                name = it.name,
                role = enumOf<WerewolfRole>(it.role),
                alive = it.alive,
                deathCause = it.deathCause?.let { name -> enumOf<DeathCause>(name) },
                deathDay = it.deathDay,
                model = it.model,
            )
        },
        day = day,
        phase = enumOf<Phase>(phase),
        witch = WitchStock(witch.antidoteAvailable, witch.poisonAvailable),
        wolfVotes = wolfVotes,
        wolfVoteRound = wolfVoteRound,
        tonightVictim = tonightVictim,
        tonightGuardedSeat = tonightGuardedSeat,
        lastGuardedSeat = lastGuardedSeat,
        nightHead = nightHead,
        wolfPlan = wolfPlan,
        alphaWolfSeat = alphaWolfSeat,
        idiotRevealed = idiotRevealed,
        pendingDawn = pendingDawn?.let {
            PendingDawn(
                day = it.day,
                deaths = it.deaths,
                bearRoarSeat = it.bearRoarSeat,
            )
        },
        seerChecks = seerChecks.map { SeerCheck(it.day, it.targetSeat, it.isWerewolf) },
        voteRound = voteRound,
        voteCandidates = voteCandidates,
        ballots = ballots.map { Ballot(it.voterSeat, it.targetSeat) },
        pendingHunterSeat = pendingHunterSeat,
        sheriffSeat = sheriffSeat,
        sheriffCandidates = sheriffCandidates,
        sheriffRunners = sheriffRunners,
        sheriffRound = sheriffRound,
        sheriffElectionDone = sheriffElectionDone,
        pendingLastWords = pendingLastWords,
        afterLastWordsPhase = enumOf<Phase>(afterLastWordsPhase),
        afterHunterPhase = enumOf<Phase>(afterHunterPhase),
        spokenPhaseKey = spokenPhaseKey,
        spokenSeats = spokenSeats.toSet(),
        history = history.map { it.toEvent() },
        winner = winner?.let { enumOf<Faction>(it) },
        nights = nights.map {
            NightRecord(
                day = it.day,
                wolfTarget = it.wolfTarget,
                guarded = it.guarded,
                healed = it.healed,
                poisonTarget = it.poisonTarget,
                deaths = it.deaths,
                mutualProtection = it.mutualProtection,
            )
        },
        voteRounds = voteRounds.map { it.toRound() },
    ),
    transcript = transcript.map {
        WerewolfMessage(
            day = it.day,
            phase = enumOf<Phase>(it.phase),
            seat = it.seat,
            kind = enumOf<MessageKind>(it.kind),
            text = it.text,
            audience = it.audience?.toSet(),
            reasoning = it.reasoning,
        )
    },
)

private fun VoteRoundSnapshot.toRound(): VoteRound = VoteRound(
    day = day,
    round = round,
    ballots = ballots.map { Ballot(it.voterSeat, it.targetSeat) },
    outcome = enumOf<VoteOutcome>(outcome),
    exiledSeat = exiledSeat,
    exileVotes = exileVotes,
)

private fun EventSnapshot.toEvent(): GameEvent = when (type) {
    "DAWN" -> GameEvent.Dawn(day = day, deadSeats = deadSeats.orEmpty())
    "EXILED" -> GameEvent.Exiled(
        day = day,
        seat = requireNotNull(seat) { "EXILED 缺少 seat" },
        voteCount = voteCount ?: 0.0,
    )
    "NO_EXILE" -> GameEvent.NoExile(day = day)
    "IDIOT_REVEALED" -> GameEvent.IdiotRevealed(
        day = day,
        seat = requireNotNull(seat) { "IDIOT_REVEALED 缺少 seat" },
        voteCount = voteCount ?: 0.0,
    )
    "HUNTER_SHOT" -> GameEvent.HunterShot(
        day = day,
        hunterSeat = requireNotNull(hunterSeat) { "HUNTER_SHOT 缺少 hunterSeat" },
        targetSeat = targetSeat,
        shooterLabel = shooterLabel ?: "猎人",
    )
    "BEAR_ROAR" -> GameEvent.BearRoar(
        day = day,
        seat = requireNotNull(seat) { "BEAR_ROAR 缺少 seat" },
    )
    "WOLF_EXPLODED" -> GameEvent.WolfExploded(
        day = day,
        seat = requireNotNull(seat) { "WOLF_EXPLODED 缺少 seat" },
    )
    "SHERIFF_SIGNUP" -> GameEvent.SheriffSignup(day = day, runners = seats.orEmpty())
    "SHERIFF_ELECTED" -> GameEvent.SheriffElected(
        day = day,
        seat = requireNotNull(seat) { "SHERIFF_ELECTED 缺少 seat" },
        votes = voteCount?.toInt() ?: 0,
    )
    "SHERIFF_VACANT" -> GameEvent.SheriffVacant(
        day = day,
        reason = reason ?: "警徽流失",
    )
    "SHERIFF_PK" -> GameEvent.SheriffPk(day = day, seats = seats.orEmpty())
    "SHERIFF_BADGE_PASSED" -> GameEvent.SheriffBadgePassed(
        day = day,
        fromSeat = requireNotNull(fromSeat) { "SHERIFF_BADGE_PASSED 缺少 fromSeat" },
        toSeat = targetSeat,
    )
    "SPEECH_DIRECTION" -> GameEvent.SpeechDirectionChosen(
        day = day,
        seat = requireNotNull(seat) { "SPEECH_DIRECTION 缺少 seat" },
        direction = enumOf<SpeechDirection>(requireNotNull(direction) { "SPEECH_DIRECTION 缺少 direction" }),
    )
    "GAME_OVER" -> GameEvent.GameOver(
        day = day,
        winner = enumOf<Faction>(requireNotNull(winner) { "GAME_OVER 缺少 winner" }),
    )
    else -> error("未知事件类型 $type")
}

private fun roleOf(name: String): WerewolfRole = enumOf(name)

private inline fun <reified T : Enum<T>> enumOf(name: String): T =
    enumValues<T>().firstOrNull { it.name == name } ?: error("未知枚举值 $name")
