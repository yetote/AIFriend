package com.feature.werewolf.engine

import com.feature.werewolf.game.WerewolfTurnLimit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 一步是「想一个结果」还是「说一段话」。两者的时限不同，超时后的处理也不同。 */
enum class TurnKind {
    /** 投票、查验、用药、下单，要的是一个 JSON。超时走兜底动作。 */
    DECISION,

    /** 白天发言、狼人夜聊。超时保留已经说出的半句。 */
    SPEECH,
}

/**
 * 一个座位到目前为止花了多少时间。
 *
 * [timeouts] 单独记而不是从耗时里反推：超时的那一次也计入了 [totalMs]，
 * 而用户真正想看的恰恰是「这个座位超了几次」——那才是要不要把它换掉/关思考的依据。
 */
data class SeatTiming(
    val seat: Int,
    val lastMs: Long = 0L,
    val totalMs: Long = 0L,
    val turns: Int = 0,
    val timeouts: Int = 0,
) {
    val averageMs: Long get() = if (turns == 0) 0L else totalMs / turns
}

/** 某一步超时了。给界面留一条可以显示的记录，而不是悄悄降级。 */
data class TurnTimeout(
    val seat: Int,
    val kind: TurnKind,
    /** 这一档限定的毫秒数。 */
    val limitMs: Long,
    /** 实际等了多久。比 [limitMs] 略大是正常的：计时和取消不在同一刻。 */
    val elapsedMs: Long,
    /**
     * 发生时刻。
     *
     * 同一个座位连着超时两次时，除了这个字段其余全一样——界面靠它区分
     * 「又超了一次」和「还是上一次那条」，否则第二条提示不会弹出来。
     */
    val at: Long = System.currentTimeMillis(),
) {
    val limitSeconds: Int get() = (limitMs / 1000L).toInt()
}

/**
 * 一局的节奏控制：时限档位、本局被关掉深度思考的座位、每个座位的时间账本。
 *
 * **为什么不放进 `WerewolfTable`**：这些全是运行时的观测量，一条规则都不影响。
 * 塞进局面里会让存档、复盘、`.wwg` 全部跟着多出几个和胜负无关的字段；
 * 而它们真正需要「跟着这一局走」的部分——档位和关思考名单——由快照单独带一份。
 *
 * 对象是**可变**的且贯穿整局：用户可以在牌局中途改时限、手动关掉某个座位的思考，
 * 而导演协程是开局就起、一直跑到结束的，只能共享同一个实例才改得动。
 */
class WerewolfPacing(
    limit: WerewolfTurnLimit = WerewolfTurnLimit.STANDARD,
    thinkingOff: Set<Int> = emptySet(),
) {
    private val _limit = MutableStateFlow(limit)
    val limit: StateFlow<WerewolfTurnLimit> = _limit.asStateFlow()

    private val _thinkingOff = MutableStateFlow(thinkingOff)
    /**
     * 本局被关掉深度思考的座位。
     *
     * 三种来源：超时自动关（主路径，见导演的 `ask`）、玩家在设置面板手动关、
     * 以及存档里带回来的（续档之后不该把已经关掉的那个座位又打开）。
     */
    val thinkingOff: StateFlow<Set<Int>> = _thinkingOff.asStateFlow()

    private val _timings = MutableStateFlow<Map<Int, SeatTiming>>(emptyMap())
    val timings: StateFlow<Map<Int, SeatTiming>> = _timings.asStateFlow()

    val currentLimit: WerewolfTurnLimit get() = _limit.value

    /**
     * 把时限压到毫秒级，**只给测试用**。null 表示按档位来。
     *
     * 档位最小也是 30 秒，超时路径没法在单测里真等半分钟；
     * 而这条路径一旦写错，表现是「牌桌永久停住」，比任何规则错误都严重。
     * 和导演的 `streamIntervalMs` 同一个思路：留一个可注入的旋钮，而不是为测试加一档。
     */
    internal var overrideMs: Long? = null

    /** 只压思考段（决策档）。测「思考段和发言段分开计时」用。 */
    internal var overrideThinkingMs: Long? = null

    /** 只压发言段（发言档）。同上。 */
    internal var overrideSpeechMs: Long? = null

    /** 这一步的时限。0 表示不设限。 */
    fun limitMs(kind: TurnKind): Long = when (kind) {
        TurnKind.DECISION -> overrideThinkingMs ?: overrideMs ?: currentLimit.decisionMs
        TurnKind.SPEECH -> overrideSpeechMs ?: overrideMs ?: currentLimit.speechMs
    }

    /** 改档位。只影响**之后**的请求，正在跑的那一步仍按发出时的时限。 */
    fun setLimit(next: WerewolfTurnLimit) {
        _limit.value = next
    }

    /** 某个座位这一步到底要不要开深度思考。角色本身关着的，这里开不回来。 */
    fun thinkingAllowed(seat: Int, roleDefault: Boolean): Boolean =
        roleDefault && seat !in _thinkingOff.value

    /** 本局给这个座位关掉深度思考。超时降级和玩家手动操作都走这里。 */
    fun disableThinking(seat: Int) {
        _thinkingOff.update { it + seat }
    }

    private fun enableThinking(seat: Int) {
        _thinkingOff.update { it - seat }
    }

    /**
     * 手动切换某个座位的深度思考。
     *
     * 只能「关」，不能把角色本来就没开的打开——那是角色属性，在编辑好友那里改。
     * 传 `on = true` 只是把它从关闭名单里拿掉，恢复到角色自己的设定。
     */
    fun setThinking(seat: Int, on: Boolean) {
        if (on) enableThinking(seat) else disableThinking(seat)
    }

    fun record(seat: Int, elapsedMs: Long, kind: TurnKind, timedOut: Boolean) {
        val spent = elapsedMs.coerceAtLeast(0L)
        _timings.update { current ->
            val before = current[seat] ?: SeatTiming(seat)
            current + (seat to before.copy(
                lastMs = spent,
                totalMs = before.totalMs + spent,
                turns = before.turns + 1,
                timeouts = before.timeouts + if (timedOut) 1 else 0,
            ))
        }
    }

    /** 开新局时清账。续档不清——账本不该跨局，但同一局的续档要保留计时。 */
    fun reset(limit: WerewolfTurnLimit, thinkingOff: Set<Int> = emptySet()) {
        _limit.value = limit
        _thinkingOff.value = thinkingOff
        _timings.value = emptyMap()
    }

    /** 续档时把存档里的档位与关思考名单接回来，账本从零开始（它本来就没落档）。 */
    fun restore(limit: WerewolfTurnLimit, thinkingOff: Set<Int>) {
        _limit.value = limit
        _thinkingOff.value = thinkingOff
    }
}
