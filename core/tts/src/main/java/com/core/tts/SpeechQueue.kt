package com.core.tts

/**
 * 一条待朗读的发言。
 *
 * [index] 是它在消息流里的稳定下标，朗读去重全靠它——不能用文本，
 * 因为「我投 3 号」这类话在同一个人嘴里会原样出现很多次。
 */
data class SpeechLine(val index: Int, val text: String)

/**
 * 自动朗读的**纯逻辑**：从一段消息流里挑出该读的话、去重、排队。
 *
 * 刻意不依赖 Android，也不依赖狼人杀的 [MessageKind]——那会让这个模块
 * 反过来依赖 feature 层，破坏单向依赖。调用方把消息先映射成 [SpeechCandidate]
 * （在这里就把「私有消息不读、自己的话不读」筛掉），队列只负责顺序和去重。
 *
 * 为什么需要它：消息流是**整表重传**的（每次新增一条就 `transcript + message`
 * 得到新列表），界面拿到的永远是全量。如果每来一次状态就从头读一遍，
 * 第三句话会被读三次。所以必须记住「已经处理到第几条」，从游标往后取。
 */
class SpeechQueue {
    private val pending = ArrayDeque<SpeechLine>()
    private var cursor = 0

    /**
     * 喂入当前整段消息流，返回这次**新增**的、该读的那些话。
     *
     * [candidates] 的下标必须和消息流一致（调用方用 `mapIndexed` 保留原下标），
     * 否则游标会错位。被过滤掉的消息仍然占下标——它们只是不产生朗读项。
     *
     * 返回的同时也入队；调用方拿返回值决定「要不要启动播放循环」。
     */
    fun offer(candidates: List<SpeechCandidate>): List<SpeechLine> {
        if (candidates.size < cursor) {
            // 消息流比上次短：换局或读档了。调用方本应显式 reset，
            // 这里兜底把游标收回来，总比抛越界异常、让整局朗读卡死好。
            cursor = candidates.size
        }
        val added = mutableListOf<SpeechLine>()
        while (cursor < candidates.size) {
            val candidate = candidates[cursor]
            cursor++
            if (candidate.speakable) {
                val line = SpeechLine(index = cursor - 1, text = candidate.text)
                pending.addLast(line)
                added.addLast(line)
            }
        }
        return added
    }

    fun poll(): SpeechLine? = pending.removeFirstOrNull()

    val size: Int get() = pending.size

    val isEmpty: Boolean get() = pending.isEmpty()

    /**
     * 换局时重置。
     *
     * [fromIndex] 一律传当前消息流长度：续档时历史发言**不补读**——
     * 用户刚点开一局，扬声器突然念出昨天二十句话，谁都会被吓一跳。
     * 从 0 开始只适用于全新开局（那时消息流本来就是空的）。
     */
    fun reset(fromIndex: Int) {
        pending.clear()
        cursor = fromIndex
    }

    /** 暂停/放弃时丢掉还没读完的：人已经离开牌桌，再念下去只剩吵。 */
    fun clear() {
        pending.clear()
    }

    /**
     * 把游标跳到当前长度，不朗读、不入队。
     *
     * 朗读开关关掉的那段时间里新来的发言不采集；重新打开时调用这个，
     * 避免那些已经过时的发言在几秒后被突然补念一遍。
     */
    fun skipTo(index: Int) {
        pending.clear()
        cursor = maxOf(cursor, index)
    }
}

/** 调用方筛过一遍之后的候选。[speakable=false] 的仍占下标，但不排队。 */
data class SpeechCandidate(val text: String, val speakable: Boolean)
