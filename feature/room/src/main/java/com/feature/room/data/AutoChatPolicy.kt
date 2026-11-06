package com.feature.room.data

import com.feature.room.model.MAX_ROOM_MEMBERS
import kotlin.random.Random

/**
 * 自由对话 / 发言顺序的纯逻辑，不涉及网络与持久化，便于单测。
 *
 * 设计约定：
 * 1. 发言顺序一律由客户端随机决定，**不按谁先返回**，也不按成员加入顺序；
 * 2. 同一批次内每人轮到一次，避免连续由同一个人说两句；
 * 3. 自由对话的提问方通过回复里的 `@名字` 把话语权交给下一位，解析失败时随机兜底；
 * 4. 停止由外部取消 job 控制，这里只提供「是否该停」的判断。
 */
object AutoChatPolicy {

    /** 自由对话默认接话次数。默认给少一点，聊得快也不至于刷屏。 */
    const val DEFAULT_TURNS = 3

    /** 接话次数可选范围。 */
    val TURN_RANGE = 1..20

    /** 收到这些标记时，认为讨论已经收尾。 */
    private val END_MARKERS = listOf("[END]", "[结束]", "【结束】")

    /**
     * 一批回复的发言顺序：把 [memberIds] 打乱。
     * 只打乱顺序，不增删成员。
     */
    fun planSpeakers(memberIds: Collection<String>, random: Random = Random.Default): List<String> {
        val shuffled = memberIds.distinct().toMutableList()
        if (shuffled.size <= 1) return shuffled
        shuffled.shuffle(random)
        return shuffled
    }

    /**
     * 自由对话里挑下一位发言人：
     * - 优先使用被 @ 的成员（[mentionedFriendId]）
     * - 没有 @ 或者 @ 的是自己时，从其他成员里随机挑一个
     * - 只有一位成员时返回他本人（由调用方决定是否继续）
     */
    fun pickNextSpeaker(
        candidates: Collection<String>,
        lastSpeakerId: String?,
        mentionedFriendId: String? = null,
        random: Random = Random.Default
    ): String? {
        val pool = candidates.distinct()
        if (pool.isEmpty()) return null
        val others = pool.filter { it != lastSpeakerId }
        if (others.isEmpty()) return pool.first()
        if (mentionedFriendId != null && mentionedFriendId in others) return mentionedFriendId
        return others[random.nextInt(others.size)]
    }

    /**
     * 从回复文本里解析被 @ 的成员。
     *
     * 取**最后一个** @ 作为话语权交接对象（前面的 @ 多半是在正文里提到某人）。
     * 名字按最长匹配，兼容名字是另一个名字前缀的情况。
     */
    fun findMentionedMember(text: String, memberNames: Map<String, String>): String? {
        if (text.isEmpty() || memberNames.isEmpty()) return null

        var result: String? = null
        text.forEachIndexed { index, char ->
            if (char != '@' && char != '＠') return@forEachIndexed
            var bestId: String? = null
            var bestLength = 0
            memberNames.forEach { (friendId, name) ->
                if (name.isNotBlank() && name.length > bestLength &&
                    text.startsWith(name, index + 1)
                ) {
                    bestId = friendId
                    bestLength = name.length
                }
            }
            if (bestId != null) result = bestId
        }
        return result
    }

    /** 回复里带了收尾标记，说明这一轮讨论可以结束了。 */
    fun isEndingReply(text: String): Boolean =
        END_MARKERS.any { text.contains(it, ignoreCase = true) }

    /** 把收尾标记从正文里去掉，避免 `【结束】` 出现在聊天气泡里。 */
    fun stripEndMarker(text: String): String {
        var result = text
        END_MARKERS.forEach { marker ->
            result = result.replace(marker, "", ignoreCase = true)
        }
        return result.trim()
    }

    /**
     * 自由对话是否还能继续。
     *
     * @param completedTurns 已经完成的接话次数
     * @param maxTurns 用户设置的接话上限
     * @param speakerCount 当前房间成员数，少于 2 人无法互相接话
     * @param lastReply 上一条回复，用于识别收尾标记
     */
    fun shouldContinue(
        completedTurns: Int,
        maxTurns: Int,
        speakerCount: Int,
        lastReply: String? = null
    ): Boolean {
        if (speakerCount < 2) return false
        if (completedTurns >= maxTurns.coerceIn(TURN_RANGE)) return false
        if (lastReply != null && isEndingReply(lastReply)) return false
        return true
    }

    /** 接话次数收敛到合法区间。 */
    fun sanitizeTurns(turns: Int): Int = turns.coerceIn(TURN_RANGE)

    /** 成员上限，供测试断言用。 */
    val maxMembers: Int = MAX_ROOM_MEMBERS
}
