package com.feature.werewolf.vm

import com.core.ai.model.Friend
import com.feature.werewolf.game.WerewolfPlayer

/**
 * 「换人续档」的纯逻辑：谁可以被换、换完之后座位表长什么样。
 *
 * 抽成纯函数是为了能脱离 Android / Room 做 JVM 单测——这块一旦写错，
 * 后果是续档把身份、死活、历史全打乱，属于必须在测试里钉死的地方。
 */

/**
 * 每个失败座位能换成谁。
 *
 * 规则：
 * - 排除本局**已经在座**的所有好友（含连不上那几位）：同一个好友换到别人的座位上，
 *   牌桌会出现两个同名同模型的人，AI 互相称呼时就乱了。
 * - 排除人类玩家自己的座位（通过 [occupiedIds] 传入的 SELF_PLAYER_ID）。
 * - 探活 OK 的排前面，没测过的次之，FAILED 的排最后——优先换上去就能打通的人。
 *
 * @param failedSeats 连不上的座位号
 * @param players 本局当前的座位表
 * @param friends 全部好友
 * @param checks 好友 id → 探活结果
 */
internal fun recastCandidates(
    failedSeats: List<Int>,
    players: List<WerewolfPlayer>,
    friends: List<Friend>,
    checks: Map<String, ApiCheck>,
): Map<Int, List<WerewolfRecast.RecastCandidate>> {
    if (failedSeats.isEmpty()) return emptyMap()
    // 本局在座的都占用：换人不能把 A 座的人搬到 B 座
    val occupied = players.map { it.id }.toSet()
    val pool = friends.filter { it.id !in occupied }

    fun rank(id: String): Int = when (checks[id]?.state) {
        ApiCheckState.OK -> 0
        null, ApiCheckState.CHECKING -> 1
        ApiCheckState.FAILED -> 2
    }

    val sorted = pool.sortedWith(
        compareBy<Friend> { rank(it.id) }.thenBy { it.name }
    ).map { friend ->
        WerewolfRecast.RecastCandidate(
            id = friend.id,
            name = friend.name,
            model = friend.model,
            reachable = checks[friend.id]?.state == ApiCheckState.OK,
        )
    }
    return failedSeats.associateWith { sorted }
}

/**
 * 存档里引用了、但好友列表里已经查无此人的座位。
 *
 * 存档的座位表存的是**好友 id**。好友被删了、换了账号、或者换设备没同步，
 * 那个 id 就查无此人——这个座位没有模型、没有音色，连探活都做不了。
 *
 * 以前续档时是 `mapNotNull` 悄悄把它们丢掉的，于是流程一路走到建座位才炸：
 * 「找不到小咪（abc）的资料」。那句话没有出口——用户想换人都没地方换，
 * 这一局就成了打不开的死档。
 *
 * 人类座位不参与：他本来就不在好友列表里（他的 id 是 human 标记），
 * 但他也不是能换的对象，所以按座位号排除。
 */
internal fun missingFriendSeats(
    players: List<WerewolfPlayer>,
    friendsById: Map<String, Friend>,
    humanSeat: Int?,
): List<WerewolfPlayer> =
    players.filter { it.seat != humanSeat && it.id !in friendsById }

/**
 * 「为什么让你换人」的一句话。
 *
 * 两种失败必须分开说：
 *
 * - **好友已经不在了**（被删了 / 没同步）：只能换人，换 Key 也没用；
 * - **连不上**（Key 过期、模型下线）：换个 Key 可能就好，但换人也能立刻开打。
 *
 * 混成一句「席位有问题」的话，用户会先去折腾 Key，折腾完发现还是不行。
 */
internal fun recastReason(
    missing: List<WerewolfPlayer>,
    failedNames: List<String>,
    failedDetail: String,
    searchStyle: Boolean,
): String {
    val parts = mutableListOf<String>()
    if (missing.isNotEmpty()) {
        parts += "${missing.size} 个座位的好友已经不在了：" +
            missing.joinToString("、") { "${it.seat} 号${it.name}" }
    }
    if (failedNames.isNotEmpty()) {
        val names = failedNames.joinToString("、")
        val head = if (searchStyle) {
            "搜索式要求真的联网检索，${failedNames.size} 个座位做不到：$names"
        } else {
            "${failedNames.size} 个座位连不上：$names"
        }
        parts += if (failedDetail.isNotBlank()) "$head（$failedDetail）" else head
    }
    return parts.joinToString("；")
}

/**
 * 把换人决定落到座位表上。
 *
 * **只改 id / name / model 三个字段**：座位号、身份、死活、死亡原因、天数都不动。
 * 这些是「这局打到哪了」的事实，换一个好友来接手不该把它们重写——否则换人等于重开。
 * 历史发言里的说话人按座位现查 players 的名字（见 WerewolfLiveMapping），
 * 所以旧的发言会显示成新名字，符合「这个人接手了这个座位」的直觉。
 *
 * @param swaps 座位号 → 新好友 id。人类座位不该出现在这里（调用方保证）。
 * @return 换人的座位表；新好友找不到时原样返回该座位（不塞假人，界面会报错）。
 */
internal fun applyRecast(
    players: List<WerewolfPlayer>,
    friendsById: Map<String, Friend>,
    swaps: Map<Int, String>,
): List<WerewolfPlayer> = players.map { player ->
    val newId = swaps[player.seat] ?: return@map player
    val friend = friendsById[newId] ?: return@map player
    player.copy(id = friend.id, name = friend.name, model = friend.model)
}
