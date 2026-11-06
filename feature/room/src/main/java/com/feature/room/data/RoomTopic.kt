package com.feature.room.data

import com.feature.room.model.RoomMessage
import com.feature.room.model.RoomSender

/**
 * 话题锚点：把「这场讨论到底在聊什么」固定下来。
 *
 * 自由对话最容易散的原因不是模型不聪明，而是**它看不到话题本身**：
 * 上下文按 token 预算从最新往回裁，几轮之后用户最初那句需求就被裁掉了，
 * 再加上成员只能看到最近一两条别人的发言，于是每一轮都在根据上一句猜主题，
 * 猜着猜着就沿着各自的联想走远了。
 *
 * 这里把「最近一条用户发言」抽出来，作为独立的一行提示放在最后，
 * 不受上下文裁剪影响，让每一轮接话都还有一条共同的线可以抓。
 */
object RoomTopic {

    /** 锚点最长保留的字数。太长会挤占本就紧张的接话预算。 */
    const val ANCHOR_MAX_CHARS = 160

    /**
     * 取最近一条该成员能看到、且非空的用户发言作为话题锚点。
     *
     * - 只看 [RoomSender.User]：AI 自己的发言是讨论过程，不是话题本身
     * - 尊重 `targetIds`：被 @ 限定的那一轮，非目标的成员不该拿它当话题
     * - 超长时截断，保留开头，开头通常才是真正的要求
     */
    fun anchor(messages: List<RoomMessage>, friendId: String? = null): String? {
        val visible = messages
            .sortedWith(compareBy({ it.createdAt }, { it.seq }))
            .asReversed()
            .firstOrNull { message ->
                message.sender == RoomSender.User &&
                    message.content.isNotBlank() &&
                    (friendId == null || message.targetIds == null || friendId in message.targetIds)
            }
            ?: return null
        return truncate(visible.content)
    }

    /** 房间没有用户发言时，用房间设定兜底，避免锚点为空。 */
    fun anchorOrRoomPrompt(
        messages: List<RoomMessage>,
        roomPrompt: String,
        friendId: String? = null
    ): String? {
        anchor(messages, friendId)?.let { return it }
        val prompt = roomPrompt.trim()
        return if (prompt.isEmpty()) null else truncate(prompt)
    }

    /**
     * 拼成注入给模型的一行提示。
     *
     * 措辞刻意强调「不要另起新话题」，因为散场几乎都是模型主动引入
     * 一个相关但不同的话题开始的。
     *
     * @param closing 是否是自由对话的最后一轮。为 true 时不要求继续补充，
     *   而是要求收成结论并带上结束标记，避免「该结束了还在往外扩」。
     */
    fun hint(anchor: String, speakerName: String? = null, closing: Boolean = false): String =
        buildString {
            append("（当前讨论的话题：")
            append(anchor)
            append("）\n")
            if (speakerName != null) {
                append("你（")
                append(speakerName)
                append("）")
            }
            if (closing) {
                append("正在群里接话，这是本次对话的最后一轮：请围绕这个话题给出你的结论或结论性意见，")
                append("不要再提出新的问题、新方向或需要继续讨论的点；")
                append("说完在末尾带上【结束】。")
            } else {
                append("正在群里接话：请紧扣这个话题补充，")
                append("不要另起新话题、不要扩展到相邻主题，也不要只是复述上一位说过的话。")
            }
        }

    /** 截断到 [ANCHOR_MAX_CHARS]，并在结尾补省略号表明被截过。 */
    fun truncate(text: String): String {
        val cleaned = text.trim().replace(Regex("\\s+"), " ")
        if (cleaned.length <= ANCHOR_MAX_CHARS) return cleaned
        return cleaned.take(ANCHOR_MAX_CHARS).trimEnd() + "…"
    }
}
