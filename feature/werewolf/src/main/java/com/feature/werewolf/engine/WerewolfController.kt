package com.feature.werewolf.engine

import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPlayer
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
    val progress: WerewolfProgress? = null,
    /**
     * 有位 AI 此刻正在逐字说话。说完了就回到 null。
     *
     * 单独一个字段而不是往 [table] 里塞半句话：table 是要落档的权威局面，
     * 半句话一旦进去，存档里就会留一句没说完的话，续档之后所有人都看得见。
     */
    val live: WerewolfLive? = null,
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
) {

    private val _state = MutableStateFlow(WerewolfUiState())
    val state: StateFlow<WerewolfUiState> = _state.asStateFlow()

    private var gameId: String? = null
    private var createdAt: Long = 0L

    /** 开一局新的（还没有任何消息时用这个）。 */
    suspend fun start(id: String, game: WerewolfGame) = start(id, WerewolfTable(game))

    /** 开一局新的，立刻落档——先存再打，中途被杀进程也不会丢。 */
    suspend fun start(id: String, table: WerewolfTable) {
        gameId = id
        createdAt = clock()
        _state.value = WerewolfUiState(gameId = id, table = table, finished = table.game.isFinished)
        store.save(id, table, createdAt)
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
        _state.value = WerewolfUiState(gameId = id, table = table, finished = table.game.isFinished)
        return true
    }

    /**
     * 把这一局打完。
     *
     * 每推进一个阶段就落一次档。粒度选「阶段」而不是「每次请求」：
     * 一个阶段最多十来次请求，落档太频繁纯属浪费；而阶段之间的局面是自洽的，
     * 从这里续上不会出现「话说到一半」的状态。
     */
    suspend fun play() {
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
        )

        _state.update { it.copy(running = true, error = null) }
        try {
            val result = director.play(initial) { snapshot ->
                _state.update { it.copy(table = snapshot) }
                store.save(id, snapshot, createdAt)
            }
            _state.update {
                it.copy(table = result, running = false, finished = true, progress = null, live = null)
            }
            store.save(id, result, createdAt)
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
     * 把界面上当前的局面落一次档。
     *
     * 暂停时用：存档的粒度是「阶段」，而牌桌上可能已经又说了几句。
     * 不落这一次，用户从列表点「接着打」会退回到阶段开头，看着像丢了几句话。
     */
    suspend fun flush() {
        val id = gameId ?: return
        val table = _state.value.table ?: return
        if (table.game.isFinished) return
        store.save(id, table, createdAt)
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
