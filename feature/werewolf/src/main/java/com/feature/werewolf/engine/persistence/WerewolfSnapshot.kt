package com.feature.werewolf.engine.persistence

import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.game.Ballot
import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.Faction
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.NightRecord
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.SeerCheck
import com.feature.werewolf.game.VoteOutcome
import com.feature.werewolf.game.VoteRound
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPlayer
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WinRule
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

    /** 当前存档格式版本。改动 DTO 结构时必须加一。 */
    const val VERSION = 3

    private val json = Json {
        ignoreUnknownKeys = true   // 旧档多出来的字段不该让新版本读不出来
        encodeDefaults = true      // 保证 round-trip 一致，缺省值也要写出来
    }

    fun encode(table: WerewolfTable): String =
        json.encodeToString(table.toSnapshot())

    /**
     * 反序列化。失败返回 null，不抛异常——存档损坏只是丢一局游戏，
     * 不该把整个界面拖崩。调用方看到 null 就当没有存档处理。
     */
    fun decode(text: String): WerewolfTable? {
        val dto = runCatching { json.decodeFromString<TableSnapshot>(text) }.getOrNull() ?: return null
        // v2 相对 v1、v3 相对 v2，都只是**新增**带默认值的字段，语义一个字都没改，
        // 所以旧档可以直接读：v1 缺复盘那两段，v2 缺守卫/白痴的状态（那时也没有这两个身份）。
        // 这比「一声不响把玩家没打完的局丢掉」要好。真正的语义变更才该拒绝旧档。
        if (dto.version !in MIN_SUPPORTED_VERSION..VERSION) return null
        return runCatching { dto.toTable() }.getOrNull()
    }

    /** 最低可读版本。低于它的档语义已经对不上，宁可当作没有存档。 */
    private const val MIN_SUPPORTED_VERSION = 1
}

private const val SNAPSHOT_VERSION = WerewolfSnapshot.VERSION

// ---------------------------------------------------------------------------
// DTO
// ---------------------------------------------------------------------------

@Serializable
internal data class TableSnapshot(
    val version: Int = SNAPSHOT_VERSION,
    val config: ConfigSnapshot,
    val players: List<PlayerSnapshot>,
    val day: Int,
    val phase: String,
    val witch: WitchSnapshot = WitchSnapshot(),
    val wolfVotes: Map<Int, Int> = emptyMap(),
    val wolfVoteRound: Int = 1,
    val tonightVictim: Int? = null,
    /** v3 起：本夜守卫守的人，以及上一夜守的人（用于「不能连守」）。 */
    val tonightGuardedSeat: Int? = null,
    val lastGuardedSeat: Int? = null,
    /** v3 起：白痴是否已翻牌。 */
    val idiotRevealed: Boolean = false,
    val seerChecks: List<CheckSnapshot> = emptyList(),
    val voteRound: Int = 1,
    val voteCandidates: List<Int>? = null,
    val ballots: List<BallotSnapshot> = emptyList(),
    val pendingHunterSeat: Int? = null,
    val afterHunterPhase: String = Phase.NIGHT_WOLF.name,
    val history: List<EventSnapshot> = emptyList(),
    val winner: String? = null,
    /** v2 起：每夜结算记录，复盘用。旧档没有这一段，读出来是空列表。 */
    val nights: List<NightSnapshot> = emptyList(),
    /** v2 起：逐轮投票历史，复盘用。 */
    val voteRounds: List<VoteRoundSnapshot> = emptyList(),
    val transcript: List<MessageSnapshot> = emptyList(),
)

@Serializable
internal data class ConfigSnapshot(
    /** 身份构成。键是 [WerewolfRole] 的名字。 */
    val composition: Map<String, Int>,
    val winRule: String,
    val maxVoteRounds: Int,
    val witchCanSelfHealOnFirstNight: Boolean,
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
internal data class NightSnapshot(
    val day: Int,
    val wolfTarget: Int? = null,
    /** v3 起：这一夜守的人。旧档没有这一段，读出来是 null。 */
    val guarded: Int? = null,
    val healed: Boolean = false,
    val poisonTarget: Int? = null,
    val deaths: List<Int> = emptyList(),
)

@Serializable
internal data class VoteRoundSnapshot(
    val day: Int,
    val round: Int,
    val ballots: List<BallotSnapshot> = emptyList(),
    /** [com.feature.werewolf.game.VoteOutcome] 的名字。 */
    val outcome: String,
    val exiledSeat: Int? = null,
    val exileVotes: Int = 0,
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
    val voteCount: Int? = null,
    val hunterSeat: Int? = null,
    val targetSeat: Int? = null,
    val winner: String? = null,
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
)

// ---------------------------------------------------------------------------
// 领域 → DTO
// ---------------------------------------------------------------------------

internal fun WerewolfTable.toSnapshot(): TableSnapshot {
    val game = this.game
    return TableSnapshot(
        config = ConfigSnapshot(
            composition = game.config.composition.entries.associate { it.key.name to it.value },
            winRule = game.config.winRule.name,
            maxVoteRounds = game.config.maxVoteRounds,
            witchCanSelfHealOnFirstNight = game.config.witchCanSelfHealOnFirstNight,
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
        idiotRevealed = game.idiotRevealed,
        seerChecks = game.seerChecks.map { CheckSnapshot(it.day, it.targetSeat, it.isWerewolf) },
        voteRound = game.voteRound,
        voteCandidates = game.voteCandidates,
        ballots = game.ballots.map { BallotSnapshot(it.voterSeat, it.targetSeat) },
        pendingHunterSeat = game.pendingHunterSeat,
        afterHunterPhase = game.afterHunterPhase.name,
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
    is GameEvent.HunterShot ->
        EventSnapshot(type = "HUNTER_SHOT", day = day, hunterSeat = hunterSeat, targetSeat = targetSeat)
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
        idiotRevealed = idiotRevealed,
        seerChecks = seerChecks.map { SeerCheck(it.day, it.targetSeat, it.isWerewolf) },
        voteRound = voteRound,
        voteCandidates = voteCandidates,
        ballots = ballots.map { Ballot(it.voterSeat, it.targetSeat) },
        pendingHunterSeat = pendingHunterSeat,
        afterHunterPhase = enumOf<Phase>(afterHunterPhase),
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
        voteCount = requireNotNull(voteCount) { "EXILED 缺少 voteCount" },
    )
    "NO_EXILE" -> GameEvent.NoExile(day = day)
    "IDIOT_REVEALED" -> GameEvent.IdiotRevealed(
        day = day,
        seat = requireNotNull(seat) { "IDIOT_REVEALED 缺少 seat" },
        voteCount = requireNotNull(voteCount) { "IDIOT_REVEALED 缺少 voteCount" },
    )
    "HUNTER_SHOT" -> GameEvent.HunterShot(
        day = day,
        hunterSeat = requireNotNull(hunterSeat) { "HUNTER_SHOT 缺少 hunterSeat" },
        targetSeat = targetSeat,
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
