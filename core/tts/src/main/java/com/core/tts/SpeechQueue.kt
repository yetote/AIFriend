package com.core.tts

/**
 * 一条待处理的发言。
 *
 * [index] 是它在消息流里的稳定下标，朗读去重全靠它——不能用文本，
 * 因为「我投 3 号」这类话在同一个人嘴里会原样出现很多次。
 *
 * [speakable] 决定它出不出声，但**不影响它入队**：所有消息都要经过队列，
 * 因为队列同时是「显示闸门」——消费到哪，界面才显示到哪（见 [revealedIndex]）。
 */
data class SpeechLine(
    val index: Int,
    val text: String,
    val speakable: Boolean,
    /** 该句所属的云端音色，由角色配置带进来；null 表示用引擎默认音色。 */
    val voiceId: String? = null,
    /** 该句用哪套 TTS 凭证（TTSProvider.id）；null 表示默认配置。 */
    val ttsConfigId: String? = null,
)

/**
 * 自动朗读 + 显示节流的**纯逻辑**：从一段消息流里挑出该读的话、去重、排队，
 * 并用消费进度驱动界面显示。
 *
 * 刻意不依赖 Android，也不依赖狼人杀的 [MessageKind]——那会让这个模块
 * 反过来依赖 feature 层，破坏单向依赖。调用方把消息先映射成 [SpeechCandidate]
 * （在这里就把「私有消息不读、自己的话不读」筛掉），队列只负责顺序和去重。
 *
 * 为什么需要它：消息流是**整表重传**的（每次新增一条就 `transcript + message`
 * 得到新列表），界面拿到的永远是全量。如果每来一次状态就从头读一遍，
 * 第三句话会被读三次。所以必须记住「已经处理到第几条」，从游标往后取。
 *
 * 为什么它同时管显示：生成（API）比朗读快得多，若生成完就立刻显示，
 * 整轮文字会瞬间刷屏，然后留着用户干等语音一条条念。让显示跟着队列消费
 * 走，一轮朗读结束文字也正好放完，读屏和听音不会再有时间差。
 */
class SpeechQueue {
    private val pending = ArrayDeque<SpeechLine>()
    private var cursor = 0

    /**
     * 已经消费掉（即界面可以显示到）的最大下标；-1 表示一条都没放出来。
     *
     * 只在 [poll] 时推进——**朗读出声前后都算消费**，否则用户得等念完才看到字，
     * 那又变成「听完了才给看」。非朗读消息（投票、播报）poll 后瞬间过，
     * 让显示进度跟上，它们的文字才会出现。
     */
    var revealedIndex: Int = -1
        private set

    /**
     * 喂入当前整段消息流，返回这次**新增**的行。
     *
     * [candidates] 的下标必须和消息流一致（调用方用 `mapIndexed` 保留原下标），
     * 否则游标会错位。所有消息都入队，[SpeechCandidate.speakable] 只决定出不出声。
     *
     * 返回的同时也入队；调用方拿返回值决定「要不要启动播放循环」——即使新增的
     * 全是不出声的消息，也要启动循环把它们消费掉，否则它们会永远卡在闸门后。
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
            val line = SpeechLine(
                index = cursor,
                text = candidate.text,
                speakable = candidate.speakable,
                voiceId = candidate.voiceId,
                ttsConfigId = candidate.ttsConfigId,
            )
            cursor++
            pending.addLast(line)
            added.addLast(line)
        }
        return added
    }

    /**
     * 弹出下一条，并把显示进度推进到它。
     *
     * 调用方对 [SpeechLine.speakable=false] 的行直接跳过朗读——这里已经把进度
     * 记上了，文字能正常显示，不会被永久留在闸门后。
     */
    fun poll(): SpeechLine? {
        val line = pending.removeFirstOrNull()
        if (line != null) revealedIndex = maxOf(revealedIndex, line.index)
        return line
    }

    val size: Int get() = pending.size

    val isEmpty: Boolean get() = pending.isEmpty()

    /**
     * 换局时重置。
     *
     * [fromIndex] 一律传当前消息流长度：续档时历史发言**不补读**——
     * 用户刚点开一局，扬声器突然念出昨天二十句话，谁都会被吓一跳。
     * 同理，历史文字也全部算「已揭示」，界面照常显示整段历史。
     * 从 0 开始只适用于全新开局（那时消息流本来就是空的）。
     */
    fun reset(fromIndex: Int) {
        pending.clear()
        cursor = fromIndex
        revealedIndex = fromIndex - 1
    }

    /** 暂停/放弃时丢掉还没处理的：人已经离开牌桌，再念下去只剩吵。 */
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
        revealedIndex = maxOf(revealedIndex, index - 1)
    }
}

/** 调用方筛过一遍之后的候选。[speakable=false] 的仍占下标、仍入队，只是不出声。 */
data class SpeechCandidate(
    val text: String,
    val speakable: Boolean,
    val voiceId: String? = null,
    val ttsConfigId: String? = null,
)
