package com.feature.werewolf.engine

import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPlayer
import com.feature.werewolf.game.WerewolfPromptStyle
import com.feature.werewolf.game.WerewolfSpeechLength
import com.feature.werewolf.game.WerewolfTurnLimit
import com.feature.werewolf.store.WerewolfGameStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/**
 * 界面需要的一份状态。
 *
 * [progress] 和 [table] 是两个时间粒度：前者是「这一阶段做到第几步」，
 * 后者是「阶段结束后的局面」。界面同时用：进度条看前者，牌桌看后者。
 */
data class WerewolfUiState(
    /**
     * 当前这局的存档 id。null 表示手里没有局。
     *
     * 放在状态里而不是只留一个私有字段，是因为「手上这局是不是我要删的那局」
     * 这个判断发生在界面层（列表里删档时）。让它去问控制器要 id，
     * 就得再加一个访问器；而它本来就是状态的一部分。
     */
    val gameId: String? = null,
    val table: WerewolfTable? = null,
    /**
     * 这一局生效的玩家设定（全局那段 + 板子那段拼好的）。
     *
     * 放在状态里是给复盘用的：复盘页要能说清「这一局是按什么设定打的」，
     * 而设置页只保留最新那份——改过之后旧局就再也查不到了。
     * 它不参与任何规则判定，续档也不会用它覆盖设置页。
     */
    val customPrompt: String? = null,
    val progress: WerewolfProgress? = null,
    /**
     * 有位 AI 此刻正在逐字说话。说完了就回到 null。
     *
     * 单独一个字段而不是往 [table] 里塞半句话：table 是要落档的权威局面，
     * 半句话一旦进去，存档里就会留一句没说完的话，续档之后所有人都看得见。
     */
    val live: WerewolfLive? = null,
    /**
     * 刚刚有一步超时了。界面拿它提示一句，看完就过。
     *
     * 和 [progress] 分开：进度是「现在做到第几步」的常态信息，
     * 这个是偶发事件，混在一起会让进度条闪烁。
     */
    val lastTimeout: TurnTimeout? = null,
    val running: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null,
)

/**
 * 一局游戏的生命周期管理：开局、每阶段落档、出错保留局面、断点续玩。
 *
 * 刻意不继承 `AndroidViewModel`，也不碰 Room 的具体实现——两边都通过接口注入。
 * 这样「开局有没有落档」「出错局面还在不在」这些行为可以写成纯 JVM 单测，
 * 否则要靠仪器测试跑真库，慢到没人愿意跑。
 *
 * 不负责**驱动**协程：`play()` 是挂起函数，调用方在自己的 scope 里 launch 它，
 * 用户退出页面时直接取消 scope 即可，`WerewolfDirector` 里的请求会跟着断掉。
 */
class WerewolfController(
    private val store: WerewolfGameStore,
    /** 座位 → 背后的模型。人类座位也在里面（包着 [HumanSeatAI]），对控制器没有区别。 */
    private val agentsFor: (WerewolfPlayer) -> SeatAgent,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * 玩家为这一局写的额外设定。
     *
     * 可以在开局前后改（[applyCustomPrompt]）：设置页改完直接生效，
     * 不用重开一局。续档也用它——**续档读的是设置页里的当前值**，
     * 不会被存档里那份按回旧设定（玩家改完设定就是希望立刻生效）。
     *
     * 但落档时会把它一起写进快照，只给复盘看：复盘要回答
     * 「这一局按什么设定打的」，而设置页只保留最新的那份。
     */
    private var customPrompt: String? = null,
    /**
     * 提示词风格。和 customPrompt 同样在开局前后可改、续档读当前值、不进存档。
     * 用 var 而不是构造 val：它和时限一样可能在设置面板里被切换。
     */
    private var promptStyle: WerewolfPromptStyle = WerewolfPromptStyle.GUIDED,
    /**
     * 这一局发言的目标长度。和 promptStyle 同样在开局前后可改、续档读存档里的值。
     */
    private var speechLength: WerewolfSpeechLength = WerewolfSpeechLength.STANDARD,
    /**
     * 这一局的节奏：时限档位、被关掉深度思考的座位、每个座位的时间账本。
     *
     * 放在控制器外面传进来，是因为它要**活过一整局**：用户在牌局中途打开设置改时限、
     * 关掉某个座位的思考，改的都是这个对象；而导演是每次 [play] 新建的。
     */
    val pacing: WerewolfPacing = WerewolfPacing(),
) {

    private val _state = MutableStateFlow(WerewolfUiState())
    val state: StateFlow<WerewolfUiState> = _state.asStateFlow()

    private var gameId: String? = null
    private var createdAt: Long = 0L

    /** 开一局新的（还没有任何消息时用这个）。 */
    suspend fun start(id: String, game: WerewolfGame) = start(id, WerewolfTable(game))

    /**
     * 换掉这一局的额外设定。
     *
     * 必须在 [play] 之前调：导演是在 play 里创建的，它拿的是当时的值。
     */
    fun applyCustomPrompt(text: String?) {
        // 两端空白没有意义，只会白占请求里的位置
        customPrompt = text?.trim()?.takeIf { it.isNotBlank() }
        // 一起放进状态：复盘页要显示「这一局按什么设定打的」
        _state.update { it.copy(customPrompt = customPrompt) }
    }

    /** 换提示词风格。必须在 [play] 之前调：导演在 play 里创建，拿的是当时的值。 */
    fun applyPromptStyle(style: WerewolfPromptStyle) {
        promptStyle = style
    }

    /** 换发言长度档。必须在 [play] 之前调：导演在 play 里创建，拿的是当时的值。 */
    fun applySpeechLength(length: WerewolfSpeechLength) {
        speechLength = length
    }

    /** 开一局新的，立刻落档——先存再打，中途被杀进程也不会丢。 */
    suspend fun start(id: String, table: WerewolfTable) {
        gameId = id
        createdAt = clock()
        _state.value = WerewolfUiState(
            gameId = id,
            table = table,
            finished = table.game.isFinished,
            customPrompt = customPrompt,
        )
        store.save(
            id, table, createdAt, customPrompt,
            pacing.currentLimit, pacing.allThinkingOff, speechLength,
        )
    }

    /**
     * 换这一局的思考时限。
     *
     * 只影响之后发出的请求：正在等的那一步仍按发出时的时限算。
     * 立刻落一次档不是必须的（每阶段都会存），但档位是用户刚做的选择，
     * 下次进来该看到他设的那个值。
     */
    fun setTurnLimit(limit: WerewolfTurnLimit) {
        pacing.setLimit(limit)
    }

    /** 手动关/开某个座位的深度思考。见 [WerewolfPacing.setThinking]。 */
    fun setSeatThinking(seat: Int, on: Boolean) {
        pacing.setThinking(seat, on)
    }

    /**
     * 续上某一局。
     *
     * 开局的 createdAt 从存档里读，不要拿当前时间顶替——那会让「按开局时间排序」失效。
     *
     * 返回 false 表示这条存档读不出来（损坏、版本太旧，或已经被删）。
     * 这时**不能**动当前状态：用户可能正看着另一局，读档失败不该把那一局也清掉。
     */
    suspend fun resume(id: String): Boolean {
        val table = store.load(id) ?: return false
        gameId = id
        createdAt = store.summaries().firstOrNull { it.id == id }?.createdAt ?: clock()
        // 档位与本局被关掉思考的座位跟着存档回来：玩家为这一局选的是「宽松」，
        // 续档之后不该悄悄回到标准的 1 分钟，更不该把已经关掉的那个座位又打开
        store.pacing(id)?.let {
            pacing.restore(it.limit, it.thinkingOff)
            // 发言长度跟着存档回来：为这一局选的「简短」，续档不该悄悄回到标准
            speechLength = it.speechLength
        }
        // customPrompt 用**调用方刚设的当前值**（续档不用存档里那份，见字段注释）
        _state.value = WerewolfUiState(
            gameId = id,
            table = table,
            finished = table.game.isFinished,
            customPrompt = customPrompt,
        )
        return true
    }

    /**
     * 把这一局打完。
     *
     * 每推进一个阶段就落一次档。粒度选「阶段」而不是「每次请求」：
     * 一个阶段最多十来次请求，落档太频繁纯属浪费；而阶段之间的局面是自洽的，
     * 从这里续上不会出现「话说到一半」的状态。
     */
    /**
     * @param awaitStage 每推完一个阶段调用一次，由上层决定要不要等（比如等朗读念完）。
     *   默认不等——引擎自己跑得快本来就是对的，同步是界面层的需求，不是规则的。
     */
    suspend fun play(awaitStage: suspend () -> Unit = {}) {
        val initial = _state.value.table ?: return
        val id = gameId ?: return
        if (initial.game.isFinished) return

        val director = WerewolfDirector(
            agents = initial.game.players.associate { it.seat to agentsFor(it) },
            random = random,
            onProgress = { progress -> _state.update { it.copy(progress = progress) } },
            // 只刷界面，不落档。落档还是每阶段一次：一个阶段十来条消息，
            // 每条都写库既慢又没必要——真正需要「不丢」的边界是阶段
            onTranscript = { snapshot -> _state.update { it.copy(table = snapshot) } },
            // 流式发言同样只刷界面。它比 onTranscript 更细：一条消息还没成型就先显示半句
            onLive = { live -> _state.update { it.copy(live = live) } },
            customPrompt = customPrompt,
            promptStyle = promptStyle,
            speechLength = speechLength,
            pacing = pacing,
            onTimedOut = { timeout -> _state.update { it.copy(lastTimeout = timeout) } },
        )

        _state.update { it.copy(running = true, error = null) }
        try {
            val result = director.play(initial) { snapshot ->
                _state.update { it.copy(table = snapshot) }
                store.save(
                    id, snapshot, createdAt, customPrompt,
                    pacing.currentLimit, pacing.allThinkingOff, speechLength,
                )
                // 落档之后停一下：这一阶段产生的话要先念给人听完，再往下走。
                // 放在落档之后而不是之前——等的时候进程被杀，存档已经是最新的。
                awaitStage()
            }
            _state.update {
                it.copy(table = result, running = false, finished = true, progress = null, live = null)
            }
            store.save(
                id, result, createdAt, customPrompt,
                pacing.currentLimit, pacing.allThinkingOff, speechLength,
            )
        } catch (cancelled: CancellationException) {
            // 用户退出页面：局面已经按阶段存过了，这里只需要收起 running。
            // live 必须一起清掉——半句话不属于任何存档，留着会让下次进来先看到一个鬼影
            _state.update { it.copy(running = false, live = null) }
            throw cancelled
        } catch (t: Throwable) {
            // 网络/Key 这类传输层问题会走到这里。局面原样保留，
            // 用户可以稍后点「继续」从当前阶段接着打
            _state.update {
                it.copy(
                    running = false,
                    live = null,
                    error = t.message?.take(200) ?: "对局中断，请重试",
                )
            }
        }
    }

    /**
     * 人类座位在牌桌上点「自爆」。
     *
     * 这是唯一一条**不经过导演**的推进：人可以在别人说话中途爆，而那时导演正挂在
     * 那个人的请求上，没法从外面叫它「先停一下，插一句自爆」。所以上层
     * （ViewModel）的用法是：取消正在跑的这一局 → 调这里落一步 → 重新 [play] 接着打。
     *
     * 落档粒度仍是阶段，但这一步特殊：自爆本身就是**阶段终点**，不存在
     * 「一个阶段说了一半」的问题，所以立刻存一次是安全的。
     *
     * 返回 false 表示这一步不合法（不在白天发言环节、已出局、不是狼），
     * 或者手里根本没有局——上层据此决定要不要重启对局。
     */
    suspend fun explodeNow(seat: Int): Boolean {
        val id = gameId ?: return false
        val table = _state.value.table ?: return false
        if (!table.game.canExplode(seat)) return false
        val next = table.recordExplode(seat)
        _state.update {
            it.copy(
                table = next,
                finished = next.game.isFinished,
                live = null,
                progress = null,
                error = null,
            )
        }
        store.save(
            id, next, createdAt, customPrompt,
            pacing.currentLimit, pacing.allThinkingOff, speechLength,
        )
        return true
    }

    /**
     * 续档探活失败后换人：用新的座位表替换当前局面并立刻落档。
     *
     * 必须在 [resume] 之后调——gameId 和 createdAt 由 resume 从存档恢复，
     * 这里不能自己拿当前时间当开局时间，否则列表排序会乱。
     * 换人只动座位背后的好友，身份、死活、历史全是 [table] 里带过来的原样事实；
     * agents 的重建在调用方（上层持有按座位取 agent 的映射）。
     */
    suspend fun recast(table: WerewolfTable) {
        val id = gameId ?: return
        _state.update {
            it.copy(
                table = table,
                finished = table.game.isFinished,
                error = null,
                live = null,
                progress = null,
            )
        }
        store.save(
            id, table, createdAt, customPrompt,
            pacing.currentLimit, pacing.allThinkingOff, speechLength,
        )
    }

    /**
     * 把界面上当前的局面落一次档。
     *
     * 暂停时用：存档的粒度是「阶段」，而牌桌上可能已经又说了几句。
     * 不落这一次，用户从列表点「接着打」会退回到阶段开头，看着像丢了几句话。
     */
    suspend fun flush() {
        val id = gameId ?: return
        val table = _state.value.table ?: return
        if (table.game.isFinished) return
        store.save(
            id, table, createdAt, customPrompt,
            pacing.currentLimit, pacing.allThinkingOff, speechLength,
        )
    }

    /** 清掉当前这局（比如玩家主动放弃）。 */
    suspend fun abandon() {
        val id = gameId
        if (id == null) {
            _state.value = WerewolfUiState()
            return
        }
        discard(id)
    }

    /**
     * 丢掉某一局存档。
     *
     * 列表页可以删任意一局，不一定是当前这局——所以「清状态」必须按 id 判断：
     * 用户在列表里删掉一条三周前的旧档，不该把手上正打着的局也从屏幕上抹掉。
     */
    suspend fun discard(id: String) {
        if (id == gameId) {
            gameId = null
            createdAt = 0L
            _state.value = WerewolfUiState()
        }
        store.delete(id)
    }
}
