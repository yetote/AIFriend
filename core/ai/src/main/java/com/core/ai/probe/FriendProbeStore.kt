package com.core.ai.probe

import com.core.ai.entity.IAI
import com.core.ai.model.Friend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** 一次连通性检查的状态。 */
enum class ApiCheckState {
    CHECKING,
    OK,
    FAILED,
}

/**
 * 某个模型此刻能不能调通的探活结论。
 *
 * [at] 是检查完成的时间戳，用来判断结果还新不新鲜：用户可能中途去设置页换了 Key，
 * 十分钟前的「已连通」不能当成现在也通。
 */
data class ApiCheck(
    val state: ApiCheckState,
    val message: String? = null,
    val elapsedMs: Long? = null,
    val at: Long = 0,
    /**
     * 这条结论是在「要求联网检索」的前提下测出来的（搜索式）。
     *
     * 两种前提对同一个座位可能给出相反结论：厂商没有内置搜索时，普通探活是绿的，
     * 搜索式探活必须是红的。所以复用缓存之前要比这个标记，否则「自由式测绿了」
     * 会让搜索式直接跳过检查、带着一个不会联网的座位开局。
     */
    val webSearch: Boolean = false,
    /**
     * 这次失败是不是「这家没有联网搜索」。
     *
     * 和「连不上」分开记，是因为界面给的下一步不一样：连不上让他查 Key，
     * 没有搜索只能换厂商或者别选搜索式。混在一起，用户会去反复重试一把没问题的 Key。
     */
    val searchUnsupported: Boolean = false,
)

/** 结果多久之后在「自动复用」场景（如开局前跳过重测）下不算数。 */
const val API_CHECK_TTL_MS = 10 * 60 * 1000L

/** 探活的最小单位：一个好友 + 它用的厂商和模型。 */
data class ProbeTarget(
    val friendId: String,
    val brand: String,
    val model: String,
)

/** 一次真正的请求要覆盖的一组好友。 */
data class ProbeTask(
    val brand: String,
    val model: String,
    /** 这一组里所有好友的 id，结果要广播给每一个。 */
    val friendIds: List<String>,
)

/**
 * 进程内共享探活缓存的键：**按「厂商 + 模型 + 是否联网搜索」存**，不按好友 id 存。
 *
 * 这样做有三个直接好处：
 * 1. 同厂商同模型的多个好友天然共享结论，新建同模型好友也立刻带出状态；
 * 2. 编辑好友换了模型后，旧模型的绿灯不会错挂到新模型上；
 * 3. 普通探活（`webSearch = false`）和狼人杀搜索式探活（`webSearch = true`）互不污染。
 */
data class ProbeModelKey(
    val brand: String,
    val model: String,
    val webSearch: Boolean = false,
)

/** 正在进行的这一轮批量探活的进度。 */
data class ProbeProgress(
    val completed: Int = 0,
    val total: Int = 0,
) {
    val active: Boolean get() = total > 0 && completed < total
}

/**
 * 算出这一轮要发几次请求。
 *
 * **按「厂商 + 模型」去重**：十几个好友往往只有两三个厂商的几个模型，
 * 按好友各测一次等于拿同一把 Key 验十几遍；写错模型名是最常见的配置错误，所以模型名也进 key。
 *
 * 新鲜度只认可 [ApiCheckState.OK]：失败的结果不复用。
 */
fun planProbes(
    targets: List<ProbeTarget>,
    known: Map<String, ApiCheck>,
    force: Boolean,
    now: Long,
    ttlMs: Long = API_CHECK_TTL_MS,
    webSearch: Boolean = false,
): List<ProbeTask> {
    val groups = LinkedHashMap<Pair<String, String>, MutableList<String>>()
    targets.forEach { target ->
        groups.getOrPut(target.brand to target.model) { mutableListOf() }.add(target.friendId)
    }

    return groups.mapNotNull { (key, ids) ->
        val stillFresh = !force && ids.all { id ->
            val check = known[id]
            check != null &&
                check.state == ApiCheckState.OK &&
                check.webSearch == webSearch &&
                now - check.at <= ttlMs
        }
        if (stillFresh) {
            null
        } else {
            ProbeTask(brand = key.first, model = key.second, friendIds = ids)
        }
    }
}

/** 把好友列表摊成探活目标，顺序跟传入的一致。 */
fun List<Friend>.toProbeTargets(): List<ProbeTarget> =
    map { ProbeTarget(friendId = it.id, brand = it.brand, model = it.model) }

/** 把探活耗时格式化成短标签（如 `480ms`、`1.4s`）。 */
fun formatProbeElapsed(elapsedMs: Long): String {
    val safe = elapsedMs.coerceAtLeast(0L)
    return if (safe < 1_000L) {
        "${safe}ms"
    } else {
        String.format(Locale.US, "%.1fs", safe / 1000.0)
    }
}

/**
 * 可单测的探活协调器：串行化批次、按 `(brand, model)` 去重、限制并发并**逐条实时回写**。
 *
 * 逐条回写很关键：如果 8 个模型里 7 个 0.6 秒就通了、1 个死卡到 20 秒超时，
 * 等全部 `awaitAll` 再更新会让用户对着整屏转圈干等 20 秒；
 * 每通一个立刻点亮绿灯，用户一眼就能挑出活着的去聊或开局。
 */
class FriendProbeCoordinator(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY,
) {
    private val _checks = MutableStateFlow<Map<ProbeModelKey, ApiCheck>>(emptyMap())
    val checks: StateFlow<Map<ProbeModelKey, ApiCheck>> = _checks.asStateFlow()

    private val _progress = MutableStateFlow(ProbeProgress())
    val progress: StateFlow<ProbeProgress> = _progress.asStateFlow()

    private val probeLock = Mutex()

    /** 把共享的模型级缓存映射成 `friendId → ApiCheck`，供界面与开局逻辑使用。 */
    fun resolveForFriends(
        friends: List<Friend>,
        webSearch: Boolean = false,
        snapshot: Map<ProbeModelKey, ApiCheck> = _checks.value,
    ): Map<String, ApiCheck> = buildMap {
        friends.forEach { friend ->
            snapshot[ProbeModelKey(friend.brand, friend.model, webSearch)]?.let { check ->
                put(friend.id, check)
            }
        }
    }

    /** 某个服务商的 Key / 地址变更或被清除后，清掉该厂商（含别名）的旧结论。 */
    fun invalidateBrands(brands: Collection<String>) {
        if (brands.isEmpty()) return
        val targets = brands.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (targets.isEmpty()) return
        _checks.update { current ->
            current.filterKeys { key -> key.brand !in targets }
        }
    }

    /** 清空全部内存探活缓存（用于测试复位或全局清理）。 */
    fun clearAll() {
        _checks.value = emptyMap()
        _progress.value = ProbeProgress()
    }

    /**
     * 对 [friends] 执行一轮探活并把结果实时写入 [checks]，返回本轮待测项是否全部通过。
     *
     * - [force] 为 false 时，[API_CHECK_TTL_MS] 内同前提已绿的模型直接复用，不重复花钱；
     * - 被取消时只擦除仍停在 [ApiCheckState.CHECKING] 的条目，已经跑出的绿/红结果完整保留。
     */
    suspend fun probe(
        friends: List<Friend>,
        force: Boolean = false,
        webSearch: Boolean = false,
        loadCredentials: suspend () -> Unit = {},
        aiFor: (String) -> IAI?,
    ): Boolean = probeLock.withLock {
        if (friends.isEmpty()) return@withLock true

        loadCredentials()

        val known = resolveForFriends(friends, webSearch = webSearch)
        val tasks = planProbes(
            targets = friends.toProbeTargets(),
            known = known,
            force = force,
            now = clock(),
            webSearch = webSearch,
        )
        if (tasks.isEmpty()) return@withLock true

        val keys = tasks.map { ProbeModelKey(it.brand, it.model, webSearch) }
        _progress.value = ProbeProgress(completed = 0, total = tasks.size)
        _checks.update { current ->
            current + keys.associateWith {
                ApiCheck(state = ApiCheckState.CHECKING, webSearch = webSearch)
            }
        }

        // 先在锁内建好 AI 实例：调用方可能传非线程安全的缓存 Map
        val ais = tasks.associateWith { task ->
            runCatching { aiFor(task.brand) }.getOrNull()
        }

        val gate = Semaphore(maxConcurrency.coerceAtLeast(1))
        val doneCounter = AtomicInteger(0)

        try {
            val results = coroutineScope {
                tasks.map { task ->
                    async {
                        gate.withPermit {
                            val ai = ais[task]
                            val result = if (ai == null) {
                                ApiProbeResult(
                                    ok = false,
                                    elapsedMs = 0,
                                    message = "暂不支持的厂商：${task.brand}",
                                )
                            } else {
                                ai.probe(task.model, webSearch = webSearch)
                            }
                            val key = ProbeModelKey(task.brand, task.model, webSearch)
                            val check = ApiCheck(
                                state = if (result.ok) ApiCheckState.OK else ApiCheckState.FAILED,
                                message = result.message,
                                elapsedMs = result.elapsedMs,
                                at = clock(),
                                webSearch = webSearch,
                                searchUnsupported = result.searchUnsupported,
                            )
                            _checks.update { it + (key to check) }
                            val done = doneCounter.incrementAndGet()
                            _progress.value = ProbeProgress(completed = done, total = tasks.size)
                            result.ok
                        }
                    }
                }.awaitAll()
            }
            results.all { it }
        } catch (cancelled: CancellationException) {
            // 只擦除还没回来的 CHECKING，已经测出的绿/红灯留给用户看
            _checks.update { current ->
                current.filterValues { it.state != ApiCheckState.CHECKING }
            }
            throw cancelled
        } finally {
            _progress.value = ProbeProgress()
        }
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENCY = 6
    }
}

/**
 * 全 App 进程内共享的探活中心。
 *
 * 首页好友列表和狼人杀开局页共用这一份状态：
 * - 在首页一键探活后，进狼人杀选人时直接看得到谁活着、谁挂了；
 * - 十分钟内已探活通过的模型在开局时自动跳过重测，不必每次开局干等。
 */
object FriendProbeStore {
    private val coordinator = FriendProbeCoordinator()

    val checks: StateFlow<Map<ProbeModelKey, ApiCheck>> get() = coordinator.checks
    val progress: StateFlow<ProbeProgress> get() = coordinator.progress

    fun resolveForFriends(
        friends: List<Friend>,
        webSearch: Boolean = false,
        snapshot: Map<ProbeModelKey, ApiCheck> = checks.value,
    ): Map<String, ApiCheck> = coordinator.resolveForFriends(friends, webSearch, snapshot)

    fun invalidateBrands(brands: Collection<String>) = coordinator.invalidateBrands(brands)

    fun clearAll() = coordinator.clearAll()

    suspend fun probe(
        friends: List<Friend>,
        force: Boolean = false,
        webSearch: Boolean = false,
        loadCredentials: suspend () -> Unit = {},
        aiFor: (String) -> IAI?,
    ): Boolean = coordinator.probe(
        friends = friends,
        force = force,
        webSearch = webSearch,
        loadCredentials = loadCredentials,
        aiFor = aiFor,
    )
}
