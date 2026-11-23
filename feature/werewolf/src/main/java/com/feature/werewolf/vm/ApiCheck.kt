package com.feature.werewolf.vm

import com.core.ai.model.Friend

/** 一次连通性检查的状态。 */
enum class ApiCheckState {
    CHECKING,
    OK,
    FAILED,
}

/**
 * 某个好友背后那个模型此刻能不能调通。
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
)

/** 结果多久之后不算数。 */
const val API_CHECK_TTL_MS = 10 * 60 * 1000L

/** 探活的最小单位：一个好友 + 它用的厂商和模型。 */
internal data class ProbeTarget(
    val friendId: String,
    val brand: String,
    val model: String,
)

/** 一次真正的请求要覆盖的一组座位。 */
internal data class ProbeTask(
    val brand: String,
    val model: String,
    /** 这一组里所有好友的 id，结果要广播给每一个。 */
    val friendIds: List<String>,
)

/**
 * 算出这一轮要发几次请求。
 *
 * **按「厂商 + 模型」去重**是这里唯一有分量的逻辑：一局 12 人往往只有两三个厂商，
 * 按座位各测一次等于拿同一把 Key 验十一遍——多花的钱不多，但用户要干等十一倍的时间，
 * 而且十一次里有十次的结果必然一样。写错模型名是最常见的配置错误，所以模型名也进 key。
 *
 * 新鲜度只认可 [ApiCheckState.OK]：失败的结果不复用。
 * 用户再点一次「检测连接」多半就是因为刚才红了，直接返回旧结论等于按钮点不动。
 */
internal fun planProbes(
    targets: List<ProbeTarget>,
    known: Map<String, ApiCheck>,
    force: Boolean,
    now: Long,
    ttlMs: Long = API_CHECK_TTL_MS,
    /** 这一轮是不是「搜索式」的检查，见 [ApiCheck.webSearch]。 */
    webSearch: Boolean = false,
): List<ProbeTask> {
    val groups = LinkedHashMap<Pair<String, String>, MutableList<String>>()
    targets.forEach { target ->
        groups.getOrPut(target.brand to target.model) { mutableListOf() }.add(target.friendId)
    }

    return groups.mapNotNull { (key, ids) ->
        // 必须整组都有新鲜的通过结果才算数。同组共享一个结果，正常不会出现半绿半空；
        // 真出现了（比如中途往同模型的座位里加了一个新人）就重测一次，
        // 总比让某个座位带着「不知道什么状态」上桌要好。
        // 前提标记必须一致：拿着「普通探活绿」去放过「搜索式检查」，正是开局后才发现
        // 不支持联网的那条老路
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

/** 把好友列表摊成探活目标，顺序跟传入的一致（配置页的选人顺序）。 */
internal fun List<Friend>.toProbeTargets(): List<ProbeTarget> =
    map { ProbeTarget(friendId = it.id, brand = it.brand, model = it.model) }
