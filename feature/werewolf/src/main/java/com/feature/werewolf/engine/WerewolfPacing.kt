package com.feature.werewolf.engine

import com.feature.werewolf.game.Phase
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
 *
 * **白天和夜里分开记**，因为这两个数字在参与局里是**身份信号**：
 * 夜里超时会被顶栏提示一句（脱敏版：「刚才有一步超出时限」），
 * 面板上如果再按座位显示「谁超时了、谁的思考被系统关了」，
 * 参与者两处一对，就把那个夜间角色锁死了。
 *
 * 所以 [dayTimeouts] / [dayLastMs] 是给参与局看的干净账本，
 * [nightTimeouts] / [nightLastMs] 只给上帝视角（观战、已结束）看。
 */
data class SeatTiming(
    val seat: Int,
    val lastMs: Long = 0L,
    val totalMs: Long = 0L,
    val turns: Int = 0,
    val timeouts: Int = 0,
    /** 白天那一步的耗时。 */
    val dayLastMs: Long = 0L,
    /** 白天累计耗时。 */
    val dayTotalMs: Long = 0L,
    /** 白天走过的步数。 */
    val dayTurns: Int = 0,
    /** 白天超时的次数。 */
    val dayTimeouts: Int = 0,
) {
    val averageMs: Long get() = if (turns == 0) 0L else totalMs / turns

    /** 白天的平均耗时。 */
    val dayAverageMs: Long get() = if (dayTurns == 0) 0L else dayTotalMs / dayTurns
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
     * 这一步发生在哪个阶段。
     *
     * 界面要靠它判断能不能点名：夜里的超时提示写成「3 号的决定超出 60 秒」，
     * 等于把「3 号是守卫 / 预言家 / 女巫 / 狼人」念给全场听——夜里的决定
     * 全是这几个角色做的，点名就是报身份。
     */
    val phase: Phase = Phase.FINISHED,
    /**
     * 发生时刻。
     *
     * 同一个座位连着超时两次时，除了这个字段其余全一样——界面靠它区分
     * 「又超了一次」和「还是上一次那条」，否则第二条提示不会弹出来。
     */
    val at: Long = System.currentTimeMillis(),
    /** 本次超时是否触发了「关闭本局深度思考」（连续超时达阈值时为 true）。 */
    val thinkingDisabled: Boolean = false,
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
    /** 同一座位连续超时达到这次数后，才自动关闭该座位本局的深度思考（默认 2 次，避免偶发抖动一棍子打死）。 */
    val disableAfterConsecutiveTimeouts: Int = DEFAULT_DISABLE_AFTER_CONSECUTIVE_TIMEOUTS,
) {
    private val consecutiveTimeouts = mutableMapOf<Int, Int>()
    private val _limit = MutableStateFlow(limit)
    val limit: StateFlow<WerewolfTurnLimit> = _limit.asStateFlow()

    private val _thinkingOff = MutableStateFlow(thinkingOff)
    /**
     * 本局被关掉深度思考的座位。
     *
     * 三种来源：玩家在设置面板手动关、白天超时自动关（见导演的 `ask`）、
     * 以及存档里带回来的（续档之后不该把已经关掉的那个座位又打开）。
     *
     * **夜里超时的不在这里**——它进 [nightThinkingOff]。存档落档时用
     * [allThinkingOff]（两个集合的并集），所以续档后思考照样是关着的。
     */
    val thinkingOff: StateFlow<Set<Int>> = _thinkingOff.asStateFlow()

    /**
     * 本局被关掉深度思考的**全部**座位（[thinkingOff] + [nightThinkingOff]）。
     *
     * 存档落档用这个：不然续档之后，夜里被超时关掉思考的座位会重新打开，
     * 那个卡死的模型又开始卡。
     */
    val allThinkingOff: Set<Int> get() = _thinkingOff.value + _nightThinkingOff.value

    /**
     * 玩家**手动**关掉深度思考的座位（以及白天超时自动关的）。
     *
     * 和 [nightThinkingOff] 分开记，是因为这两个来源在参与局里性质完全不同：
     * - 玩家手动关的、白天超时关的 → 白天的事，谁在说话大家都知道，照常显示；
     * - 夜里超时关的 → 夜里的超时提示会让参与者知道「刚才有人超时」，
     *   显示出来等于点名那个夜间角色。
     *
     * 引擎判断要不要开思考时两个集合都算；界面在参与局里只认这一份。
     */
    private val _playerThinkingOff = MutableStateFlow<Set<Int>>(emptySet())
    val playerThinkingOff: StateFlow<Set<Int>> = _playerThinkingOff.asStateFlow()

    /**
     * **夜里**超时被自动关掉深度思考的座位。
     *
     * 引擎照样要真的关——不然那个卡住的座位会一直卡下去；但参与局的界面上
     * 不能出现它的痕迹（见 [_playerThinkingOff] 的说明）。
     *
     * 构造时带进来的名单来源分不清（玩家手动关的和夜里超时关的混在一起），
     * 所以一律进这一份不外显的：参与局的界面上不出现它们，就不泄露。
     */
    private val _nightThinkingOff = MutableStateFlow<Set<Int>>(thinkingOff)
    val nightThinkingOff: StateFlow<Set<Int>> = _nightThinkingOff.asStateFlow()

    private val _timings = MutableStateFlow<Map<Int, SeatTiming>>(emptyMap())
    val timings: StateFlow<Map<Int, SeatTiming>> = _timings.asStateFlow()

    private val _nudgeEpoch = MutableStateFlow(0L)
    /**
     * 玩家手动「催一下」的广播版本号。
     *
     * 只增不减：每一步 AI 开始时记下当时的值，一旦变大，所有此刻正在思考的 AI
     * 都会立刻收尾、带着已想出的内容关思考给结论。广播给全体而不是点名具体座位，
     * 避免参与局在夜里暴露谁正在行动。
     */
    val nudgeEpoch: StateFlow<Long> = _nudgeEpoch.asStateFlow()

    /** 催一下当前所有正在思考的 AI，让它们立刻给结论。 */
    fun nudge() {
        _nudgeEpoch.update { it + 1L }
    }

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

    /** 只压收尾请求（「说结论」）的时限，测试用。null 用默认值。 */
    internal var overrideConcludeMs: Long? = null

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
        roleDefault && seat !in _thinkingOff.value && seat !in _nightThinkingOff.value

    /**
     * 给这个座位关掉深度思考（超时降级和玩家手动操作都走这里）。
     *
     * [night] 决定进哪个集合：
     * - 白天 → [thinkingOff]，参与局照常显示（白天谁在说话是公开的）；
     * - 夜里 → [nightThinkingOff]，参与局**看不到**（夜里超时会把夜间角色点出来）。
     */
    fun disableThinking(seat: Int, night: Boolean = false) {
        if (night) {
            _nightThinkingOff.update { it + seat }
        } else {
            _playerThinkingOff.update { it + seat }
        }
        _thinkingOff.update { it + seat }
    }

    private fun enableThinking(seat: Int) {
        synchronized(consecutiveTimeouts) { consecutiveTimeouts.remove(seat) }
        _playerThinkingOff.update { it - seat }
        _nightThinkingOff.update { it - seat }
        _thinkingOff.update { it - seat }
    }

    /**
     * 局中换人后重置该座位的节奏账本与思考状态：新模型不背旧模型的超时记录。
     */
    fun resetSeat(seat: Int) {
        synchronized(consecutiveTimeouts) { consecutiveTimeouts.remove(seat) }
        _playerThinkingOff.update { it - seat }
        _nightThinkingOff.update { it - seat }
        _thinkingOff.update { it - seat }
        _timings.update { it - seat }
    }

    /**
     * 记录一次超时并按连续次数决定是否关闭该座位的深度思考。
     *
     * 偶发一次网络/推理抖动不直接把整个后半局的深度思考一棍子关掉，
     * 连续超时达到 [disableAfterConsecutiveTimeouts] 次（默认 2 次）时自动关掉；
     * 中间只要正常完成一步，连续计数清零。
     *
     * @return true 表示本次超时触发了自动关闭深度思考。
     */
    fun recordTimeout(
        seat: Int,
        elapsedMs: Long,
        kind: TurnKind,
        night: Boolean = false,
    ): Boolean {
        record(seat, elapsedMs, kind, timedOut = true, night = night)
        val streak = synchronized(consecutiveTimeouts) {
            val next = (consecutiveTimeouts[seat] ?: 0) + 1
            consecutiveTimeouts[seat] = next
            next
        }
        val shouldDisable = streak >= disableAfterConsecutiveTimeouts.coerceAtLeast(1)
        if (shouldDisable) {
            disableThinking(seat, night = night)
        }
        return shouldDisable
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

    /**
     * 记一步的耗时。
     *
     * [night] 决定这一笔记到白天还是夜里的账上——参与局的界面在夜里只看白天那本，
     * 免得「谁刚超时」变成夜间角色的定位器（见 [SeatTiming]）。
     */
    fun record(seat: Int, elapsedMs: Long, kind: TurnKind, timedOut: Boolean, night: Boolean = false) {
        if (!timedOut) {
            synchronized(consecutiveTimeouts) { consecutiveTimeouts.remove(seat) }
        }
        val spent = elapsedMs.coerceAtLeast(0L)
        _timings.update { current ->
            val before = current[seat] ?: SeatTiming(seat)
            current + (seat to before.copy(
                lastMs = spent,
                totalMs = before.totalMs + spent,
                turns = before.turns + 1,
                timeouts = before.timeouts + if (timedOut) 1 else 0,
                dayLastMs = if (night) before.dayLastMs else spent,
                dayTotalMs = if (night) before.dayTotalMs else before.dayTotalMs + spent,
                dayTurns = before.dayTurns + if (night) 0 else 1,
                dayTimeouts = before.dayTimeouts + if (!night && timedOut) 1 else 0,
            ))
        }
    }

    /** 开新局时清账。续档不清——账本不该跨局，但同一局的续档要保留计时。 */
    fun reset(limit: WerewolfTurnLimit, thinkingOff: Set<Int> = emptySet()) {
        synchronized(consecutiveTimeouts) { consecutiveTimeouts.clear() }
        _limit.value = limit
        // 存档带回来的名单来源分不清（玩家手动关的和夜里超时关的混在一起），
        // 一律进不外显的那份：参与局的界面上不出现它们，就不泄露。
        _playerThinkingOff.value = emptySet()
        _nightThinkingOff.value = thinkingOff
        _thinkingOff.value = thinkingOff
        _timings.value = emptyMap()
    }

    /** 续档时把存档里的档位与关思考名单接回来，账本从零开始（它本来就没落档）。 */
    fun restore(limit: WerewolfTurnLimit, thinkingOff: Set<Int>) {
        synchronized(consecutiveTimeouts) { consecutiveTimeouts.clear() }
        _limit.value = limit
        _playerThinkingOff.value = emptySet()
        _nightThinkingOff.value = thinkingOff
        _thinkingOff.value = thinkingOff
    }

    companion object {
        const val DEFAULT_DISABLE_AFTER_CONSECUTIVE_TIMEOUTS = 2
    }
}
