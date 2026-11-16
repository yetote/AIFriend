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
    val table: WerewolfTable? = null,
    val progress: WerewolfProgress? = null,
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
        _state.value = WerewolfUiState(table = table, finished = table.game.isFinished)
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
        _state.value = WerewolfUiState(table = table, finished = table.game.isFinished)
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
        )

        _state.update { it.copy(running = true, error = null) }
        try {
            val result = director.play(initial) { snapshot ->
                _state.update { it.copy(table = snapshot) }
                store.save(id, snapshot, createdAt)
            }
            _state.update {
                it.copy(table = result, running = false, finished = true, progress = null)
            }
            store.save(id, result, createdAt)
        } catch (cancelled: CancellationException) {
            // 用户退出页面：局面已经按阶段存过了，这里只需要收起 running
            _state.update { it.copy(running = false) }
            throw cancelled
        } catch (t: Throwable) {
            // 网络/Key 这类传输层问题会走到这里。局面原样保留，
            // 用户可以稍后点「继续」从当前阶段接着打
            _state.update {
                it.copy(running = false, error = t.message?.take(200) ?: "对局中断，请重试")
            }
        }
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
